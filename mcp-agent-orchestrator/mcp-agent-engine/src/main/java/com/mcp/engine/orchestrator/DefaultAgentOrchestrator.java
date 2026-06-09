package com.mcp.engine.orchestrator;   // 请根据你的实际包路径调整

import com.mcp.core.domain.prompt.PromptType;
import com.mcp.core.service.ChatHistoryService;
import com.mcp.core.service.PromptService;
import com.mcp.engine.agent.Agent;
import com.mcp.engine.agent.AgentContext;
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

                    // 意图分类：判断是否需要工具调用
                    Agent defaultAgent = agents.isEmpty() ? null : agents.values().iterator().next();
                    if (defaultAgent != null && likelyNeedsTools(request)) {
                        log.info("[Orchestrator] Delegating to agent: {}", defaultAgent.getName());
                        return defaultAgent.execute(request);
                    }

                    if (defaultAgent != null) {
                        log.info("[Orchestrator] Simple chat, using direct LLM (no tools)");
                    }

                    // 没有 Agent 时，直接调用 LLM
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
    public Mono<String> processRequestWithModel(String request, String sessionId, String modelConfigId) {
        if (request == null || request.trim().isEmpty()) {
            return Mono.just("请输入有效的问题。");
        }

        long startTime = System.currentTimeMillis();
        log.info("[Orchestrator] Receive request: {} | Session: {} | Model: {}", request, sessionId, modelConfigId);

        return Mono.zip(
                        promptService.getCoreSystemPrompt(),
                        chatHistoryService.getHistorySummary(sessionId, 10)
                )
                .flatMap(tuple -> {
                    String systemPrompt = tuple.getT1();
                    String historySummary = tuple.getT2();
                    String userPrompt = buildUserPrompt(request, historySummary);

                    if (modelConfigId != null && !modelConfigId.isEmpty()) {
                        return llmClient.generateWithConfigAndSystem(modelConfigId, systemPrompt, userPrompt);
                    }
                    return llmClient.generateWithSystemPrompt(systemPrompt, userPrompt);
                })
                .flatMap(response ->
                        chatHistoryService.saveUserAndAssistantMessage(sessionId, request, response)
                                .thenReturn(response)
                )
                .doOnSuccess(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("[Orchestrator] Success! Duration: {}ms | Session: {} | Model: {}", duration, sessionId, modelConfigId);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("[Orchestrator] Error! Duration: {}ms | Error: {}", duration, error.getMessage(), error);
                })
                .onErrorResume(error ->
                        Mono.just("处理请求时发生错误: " + error.getMessage())
                );
    }

    @Override
    public Mono<String> processRequestWithSystemPrompt(String request, String sessionId, String systemPrompt, String modelConfigId) {
        if (request == null || request.trim().isEmpty()) {
            return Mono.just("请输入有效的问题。");
        }

        long startTime = System.currentTimeMillis();
        log.info("[Orchestrator] Receive request: {} | Session: {} | SystemPrompt: {} | Model: {}",
                request, sessionId, systemPrompt.substring(0, Math.min(30, systemPrompt.length())) + "...",
                modelConfigId != null ? modelConfigId : "default");

        Agent defaultAgent = agents.isEmpty() ? null : agents.values().iterator().next();
        if (defaultAgent != null && likelyNeedsTools(request)) {
            log.info("[Orchestrator] Delegating to agent with custom system prompt: {}", defaultAgent.getName());
            return defaultAgent.executeWithContext(request,
                            AgentContext.builder()
                                    .systemPrompt(systemPrompt)
                                    .sessionId(sessionId)
                                    .build())
                    .flatMap(response ->
                            chatHistoryService.saveUserAndAssistantMessage(sessionId, request, response)
                                    .thenReturn(response)
                    )
                    .doOnSuccess(result -> {
                        long duration = System.currentTimeMillis() - startTime;
                        log.info("[Orchestrator] Agent Success! Duration: {}ms | Session: {}", duration, sessionId);
                    })
                    .doOnError(error -> {
                        long duration = System.currentTimeMillis() - startTime;
                        log.error("[Orchestrator] Agent Error! Duration: {}ms | Error: {}", duration, error.getMessage(), error);
                    })
                    .onErrorResume(error ->
                            Mono.just("处理请求时发生错误: " + error.getMessage())
                    );
        }

        return chatHistoryService.getHistorySummary(sessionId, 10)
                .flatMap(historySummary -> {
                    String userPrompt = historySummary.isEmpty()
                            ? request
                            : "历史对话摘要：%s\n\n当前问题：%s".formatted(historySummary, request);
                    if (modelConfigId != null && !modelConfigId.isEmpty()) {
                        return llmClient.generateWithConfigAndSystem(modelConfigId, systemPrompt, userPrompt);
                    }
                    return llmClient.generateWithSystemPrompt(systemPrompt, userPrompt);
                })
                .flatMap(response ->
                        chatHistoryService.saveUserAndAssistantMessage(sessionId, request, response)
                                .thenReturn(response)
                )
                .doOnSuccess(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("[Orchestrator] Success! Duration: {}ms | Session: {}", duration, sessionId);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("[Orchestrator] Error! Duration: {}ms | Error: {}", duration, error.getMessage(), error);
                })
                .onErrorResume(error ->
                        Mono.just("处理请求时发生错误: " + error.getMessage())
                );
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

    /**
     * 简单意图分类：判断用户请求是否可能需要调用工具
     */
    private boolean likelyNeedsTools(String request) {
        if (request == null || request.trim().isEmpty()) {
            return false;
        }
        String lower = request.toLowerCase();
        // 文件操作相关
        boolean fileRelated = lower.contains("文件") || lower.contains("路径") || lower.contains("目录")
                || lower.contains("file") || lower.contains("path") || lower.contains("folder")
                || lower.contains("读取") || lower.contains("写入") || lower.contains("编辑")
                || lower.contains("read") || lower.contains("write") || lower.contains("edit");
        // 搜索相关
        boolean searchRelated = lower.contains("搜索") || lower.contains("查找") || lower.contains("查询")
                || lower.contains("search") || lower.contains("find") || lower.contains("lookup");
        return fileRelated || searchRelated;
    }

    @Override
    public void registerDefaultTools() {
        // TODO: 后续实现工具自动注册
        log.info("[Orchestrator] Default tools registration placeholder.");
    }
}