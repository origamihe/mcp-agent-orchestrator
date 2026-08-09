package com.mcp.engine.runtime;

import com.mcp.core.context.BuildContext;
import com.mcp.core.context.ContextAssembler;
import com.mcp.core.context.PromptContextBuilder;
import com.mcp.core.context.PromptLayer;
import com.mcp.core.context.PromptPolicy;
import com.mcp.engine.agent.Agent;
import com.mcp.engine.agent.LLMRequest;
import com.mcp.engine.trace.TraceCollector;
import com.mcp.engine.trace.TraceRecord;
import com.mcp.llm.client.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent Runtime — 统一 Prompt 组装与 LLM 执行管线。
 *
 * 职责：
 * 1. 构建 PromptLayer 列表（PromptContextBuilder）
 * 2. 按 Policy 过滤（PromptPolicy）
 * 3. 渲染为 System Prompt 字符串（ContextAssembler）
 * 4. 统一 LLM 执行入口（Agent 执行 + 直接 LLM 调用）
 *
 * 设计原则：
 * - Runtime 是 Prompt 组装和 LLM 执行的事实标准
 * - Orchestrator 不再直接调用 Agent.execute()，全部通过 AgentRuntime 统一调度
 * - 未来扩展：ToolRegistry、Planner、Reflection、Learning 全部接入 Runtime
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRuntime {

    private final PromptContextBuilder promptContextBuilder;
    private final ContextAssembler contextAssembler;
    private final LlmClient llmClient;

    private TraceCollector traceCollector = TraceCollector.NOOP;

    /**
     * 组装完整的 System Prompt。
     *
     * 接受 BuildContext 统一上下文对象，避免参数管道膨胀。
     * 未来新增上下文字段（language、device、permission 等）只需在 BuildContext 中增加，
     * 无需修改此方法签名。
     *
     * @param ctx 构建上下文（包含 baseSystemPrompt、userProfile、groupContext 等所有原始输入）
     * @return PromptAssemblyResult
     */
    public PromptAssemblyResult assemble(BuildContext ctx) {
        return assemble(ctx, PromptPolicy.forRequest(ctx.userMessage()));
    }

    /**
     * 使用指定 PromptPolicy 组装 System Prompt。
     * 允许 Orchestrator 根据意图显式指定 Policy，而非依赖 forRequest() 推断。
     */
    public PromptAssemblyResult assemble(BuildContext ctx, PromptPolicy policy) {
        LocalDateTime startTime = LocalDateTime.now();
        long startNanos = System.nanoTime();

        List<PromptLayer> allLayers = promptContextBuilder.buildLayers(ctx);

        List<PromptLayer> filteredLayers = policy.filter(allLayers);
        String assembledPrompt = contextAssembler.render(filteredLayers);

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        log.info("[AgentRuntime] Policy: {}, layers: {} → {}",
                policy, allLayers.size(), filteredLayers.size());

        String promptPreview = assembledPrompt.length() > 500
                ? assembledPrompt.substring(0, 500) + "..."
                : assembledPrompt;
        log.info("[AgentRuntime] ========== 最终 System Prompt (前500字符) ==========\n{}",
                promptPreview);

        TraceRecord trace = TraceRecord.builder()
                .sessionId(ctx.state() != null ? ctx.state().getLanguage() : null)
                .userId(ctx.userProfile() != null ? ctx.userProfile().getUserId() : null)
                .startTime(startTime)
                .elapsedMs(elapsedMs)
                .systemPrompt(truncate(ctx.baseSystemPrompt(), 500))
                .userMessage(ctx.userMessage())
                .renderedPrompt(assembledPrompt)
                .workspaceState(ctx.workspacePrompt())
                .layerCount(filteredLayers.size())
                .build();
        traceCollector.record(trace);

        return PromptAssemblyResult.of(assembledPrompt, policy, filteredLayers.size());
    }

    /**
     * 使用默认模型配置执行 LLM 调用。
     *
     * @param systemPrompt 组装好的 System Prompt
     * @param userPrompt   用户消息
     * @return LLM 响应
     */
    public Mono<String> run(String systemPrompt, String userPrompt) {
        return llmClient.generateWithSystemPrompt(systemPrompt, userPrompt);
    }

    /**
     * 使用指定模型配置执行 LLM 调用。
     *
     * @param modelConfigId 模型配置 ID
     * @param systemPrompt  组装好的 System Prompt
     * @param userPrompt    用户消息
     * @return LLM 响应
     */
    public Mono<String> runWithConfig(String modelConfigId, String systemPrompt, String userPrompt) {
        return llmClient.generateWithConfigAndSystem(modelConfigId, systemPrompt, userPrompt);
    }

    /**
     * 通过 AgentRuntime 统一执行 Agent。
     *
     * 所有 Agent 执行必须通过此方法，禁止 Orchestrator 直接调用 Agent.execute()。
     * 未来可在此处统一注入监控、日志、限流、熔断等横切关注点。
     *
     * @param agent   要执行的 Agent
     * @param request LLM 请求（包含完整的 systemPrompt、userMessage、tools 等）
     * @return LLM 响应
     */
    public Mono<String> execute(Agent agent, LLMRequest request) {
        return agent.execute(request);
    }

    /**
     * 设置 TraceCollector — 用于运行时追踪。
     * 默认 NOOP（不收集），测试环境可注入 InMemory 实现。
     */
    public void setTraceCollector(TraceCollector traceCollector) {
        this.traceCollector = traceCollector != null ? traceCollector : TraceCollector.NOOP;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}