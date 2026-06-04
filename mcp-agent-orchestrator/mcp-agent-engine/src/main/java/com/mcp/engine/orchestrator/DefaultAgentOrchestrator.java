package com.mcp.engine.orchestrator;   // 请根据你的实际包路径调整

import com.mcp.core.domain.prompt.PromptType;
import com.mcp.core.service.ChatHistoryService;
import com.mcp.core.service.PromptService;
import com.mcp.engine.agent.Agent;
import com.mcp.engine.orchestrator.AgentOrchestrator;
import com.mcp.llm.client.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认 Agent 编排器 - 完整接入数据库 Prompt + 历史记录
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAgentOrchestrator implements AgentOrchestrator {

    private final LlmClient llmClient;
    private final PromptService promptService;
    private final ChatHistoryService chatHistoryService;

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    @Override
    public Mono<String> processRequest(String request, String sessionId) {
        if (request == null || request.trim().isEmpty()) {
            return Mono.just("请输入有效的问题。");
        }

        long startTime = System.currentTimeMillis();

        log.info("[Orchestrator] Receive request: {} | Session: {}", request, sessionId);

        return Mono.zip(
                        promptService.getCoreSystemPrompt(),                    // 1. 获取系统 Prompt
                        chatHistoryService.getHistorySummary(sessionId, 10)    // 2. 获取历史摘要（最近10条）
                )
                .flatMap(tuple -> {
                    String systemPrompt = tuple.getT1();
                    String historySummary = tuple.getT2();

                    // 构建用户 Prompt（带历史）
                    String userPrompt = buildUserPrompt(request, historySummary);

                    return llmClient.generateWithSystemPrompt(systemPrompt, userPrompt);
                })
                .flatMap(response -> {
                    // 3. 保存对话历史
                    return chatHistoryService.saveUserAndAssistantMessage(sessionId, request, response)
                            .thenReturn(response);
                })
                .doOnSuccess(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("[Orchestrator] Success! Duration: {}ms | Session: {}", duration, sessionId);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("[Orchestrator] Error! Duration: {}ms | Error: {}", duration, error.getMessage(), error);
                })
                .onErrorResume(error -> {
                    String msg = error.getMessage() != null ? error.getMessage() : "Unknown error";
                    if (msg.contains("429")) {
                        return Mono.just("当前 Gemini API 配额已用尽，请稍后再试。");
                    }
                    return Mono.just("处理请求时发生错误: " + msg);
                });
    }

    /**
     * 构建带历史的用户 Prompt
     */
    private String buildUserPrompt(String request, String historySummary) {
        return """
            历史对话摘要：
            %s
            
            用户最新问题：%s
            
            请一步一步思考并给出专业、清晰的回答。
            """.formatted(historySummary.isEmpty() ? "（无历史对话）" : historySummary, request);
    }

    @Override
    public Mono<String> executeTask(String task, String agentName) {
        Agent agent = agents.get(agentName);
        if (agent == null) {
            return Mono.error(new RuntimeException("Agent not found: " + agentName));
        }
        return agent.execute(task);
    }

    @Override
    public void registerAgent(Agent agent) {
        agents.put(agent.getName(), agent);
        log.info("[Orchestrator] Agent registered: {}", agent.getName());
    }

    @Override
    public void registerDefaultTools() {
        // TODO: 后续实现工具自动注册
        log.info("[Orchestrator] Default tools registration placeholder.");
    }
}