package com.mcp.engine.agent.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.common.channel.SearchRequirement;
import com.mcp.common.identity.MemoryIdentity;
import com.mcp.engine.agent.Agent;
import com.mcp.engine.agent.LLMRequest;
import com.mcp.engine.agent.card.AgentCard;
import com.mcp.engine.execution.ExecutionState;
import com.mcp.engine.policy.PolicyEngine;
import com.mcp.engine.runtime.AgentRuntime;
import com.mcp.engine.trace.SessionTrace;
import com.mcp.engine.trace.SessionTraceHolder;
import com.mcp.llm.client.ChatMessage;
import com.mcp.llm.client.LlmClient;
import com.mcp.llm.client.LlmToolResponse;
import com.mcp.tools.executor.ToolExecutor;
import com.mcp.tools.model.SearchDocument;
import com.mcp.tools.model.SearchResult;
import com.mcp.tools.model.ToolDefinition;
import com.mcp.tools.model.ToolExecutionRequest;
import com.mcp.tools.model.ToolExecutionResult;
import com.mcp.tools.registry.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;

@Slf4j
@Component
@SuppressWarnings({"unchecked"})
public class SearchAgent implements Agent {

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final AgentRuntime agentRuntime;
    private final ToolExecutor toolExecutor;
    private final PolicyEngine policyEngine;
    private final ResearchSynthesizer researchSynthesizer;

    public SearchAgent(LlmClient llmClient,
                       ToolRegistry toolRegistry,
                       AgentRuntime agentRuntime,
                       ToolExecutor toolExecutor,
                       PolicyEngine policyEngine,
                       ResearchSynthesizer researchSynthesizer) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.agentRuntime = agentRuntime;
        this.toolExecutor = toolExecutor;
        this.policyEngine = policyEngine;
        this.researchSynthesizer = researchSynthesizer;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int DEFAULT_MAX_TOOL_ROUNDS = 5;
    private static final int REQUIRED_SEARCH_MAX_RETRY_ROUNDS = 2;

    @Override
    public String getId() {
        return "search-agent";
    }

    @Override
    public String getName() {
        return "SearchAgent";
    }

    @Override
    public AgentCard getAgentCard() {
        return AgentCard.builder()
                .agentId(getId())
                .agentName(getName())
                .agentType(AgentCard.AgentType.SEARCH)
                .description("联网搜索、深度研究、信息聚合与综合分析专用 Agent")
                .skills(List.of("web-search", "deep-research", "information-retrieval",
                        "data-aggregation", "fact-checking", "cross-analysis"))
                .toolNames(List.of("deep_research", "multi_search", "fetch_webpage", "web_search"))
                .version("3.0.0")
                .promptName("search-agent")
                .build();
    }

    @Override
    public void setLlmClient(LlmClient llmClient) {
        // 构造器注入，此方法为兼容 Agent 接口保留
    }

    @Override
    public void setToolRegistry(ToolRegistry toolRegistry) {
        // 构造器注入，此方法为兼容 Agent 接口保留
    }

    @Override
    public Mono<String> execute(LLMRequest request) {
        String sessionId = request.getSessionId();
        String userMessage = request.getUserMessage();
        SearchRequirement searchRequirement = request.getSearchRequirement();
        ExecutionState execState = request.getExecutionState();

        log.info("[SearchDecision] requestId={} sessionId={} requirement={} toolAuthorized={}",
                execState != null ? execState.getExecutionId() : "N/A",
                sessionId,
                searchRequirement,
                request.getExecutionPlan() != null && request.getExecutionPlan().toolPolicy().allowSearch());

        log.debug("[SearchAgent] Executing: session={}, userMessage={}",
                sessionId,
                userMessage != null ? userMessage.substring(0, Math.min(50, userMessage.length())) : "(empty)");

        if (toolExecutor == null) {
            log.warn("[SearchAgent] ToolExecutor 未配置，回退到纯文本模式（无搜索能力）");
            if (agentRuntime == null) {
                return Mono.just("[SearchAgent] AgentRuntime 未配置，无法执行搜索任务。");
            }

            if (searchRequirement == SearchRequirement.REQUIRED) {
                log.warn("[ToolFallback] requestId={} reason=REQUIRED_SEARCH_NO_TOOL_EXECUTOR toolExecutor=null",
                        execState != null ? execState.getExecutionId() : "N/A");
                return Mono.just("抱歉，当前环境未配置搜索工具，无法完成所需的搜索任务。请检查系统配置。");
            }

            String systemPrompt = request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()
                    ? request.getSystemPrompt()
                    : "你是一个专业的搜索助手。请用中文回答，提供信息来源。";
            String fallbackSystemPrompt = systemPrompt + "\n\n"
                    + "【重要提示 - 搜索工具不可用】\n"
                    + "当前环境未配置搜索工具，你无法进行实时联网搜索。\n"
                    + "请基于你已有的知识尽力回答用户问题，并明确告知用户：\n"
                    + "1. 你无法进行实时搜索，回答基于已有知识\n"
                    + "2. 建议用户自行验证关键信息\n"
                    + "3. 如果问题涉及最新信息（如新闻、实时数据），请诚实说明你无法提供\n";
            return agentRuntime.run(fallbackSystemPrompt, userMessage);
        }

        String systemPrompt = request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()
                ? request.getSystemPrompt()
                : "你是一个专业的研究助手，具备深度搜索、证据分析和综合研判能力。";

        String toolInstructions = buildMinimalToolInstructions();
        String fullSystemPrompt = systemPrompt + "\n\n" + toolInstructions;

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder().role("system").content(fullSystemPrompt).build());
        messages.add(ChatMessage.builder().role("user").content(userMessage).build());

        int maxRounds = searchRequirement == SearchRequirement.REQUIRED
                ? REQUIRED_SEARCH_MAX_RETRY_ROUNDS
                : DEFAULT_MAX_TOOL_ROUNDS;

        return reactLoop(messages, 0, new ArrayList<>(), userMessage, maxRounds, searchRequirement, request)
                .timeout(Duration.ofSeconds(300))
                .flatMap(result -> {
                    if (researchSynthesizer != null
                            && (!result.searchResults().isEmpty() || !result.documents().isEmpty())) {
                        log.debug("[SearchAgent] Post-processing: synthesizing {} results and {} documents",
                                result.searchResults().size(), result.documents().size());
                        return researchSynthesizer.synthesize(
                                userMessage, result.searchResults(), result.documents());
                    }
                    if (!result.searchResults().isEmpty() || !result.documents().isEmpty()) {
                        log.warn("[SearchAgent] ResearchSynthesizer not configured, returning raw results");
                    }
                    return Mono.just(result.finalAnswer());
                })
                .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
                    log.warn("[SearchAgent] Search timed out after 300s, returning degraded response");
                    return Mono.just("搜索任务超时（300秒），可能是当前模型推理速度较慢或搜索服务响应缓慢。请尝试使用更精确的搜索词，或稍后重试。");
                })
                .onErrorResume(e -> {
                    log.warn("[SearchAgent] Search failed: session={} | errorType={} | message={} | recovered=true",
                            sessionId, e.getClass().getSimpleName(), e.getMessage());
                    return Mono.just("搜索任务执行失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误") + "。请稍后重试。");
                });
    }

    /**
     * 最小化工具使用指引 — 仅指导 LLM 如何使用工具，不决定何时使用工具。
     * 是否必须搜索由代码层 SearchRequirement 决定，不由本 Prompt 决定。
     */
    private String buildMinimalToolInstructions() {
        return """
                【工具使用指引】

                你是一个搜索助手，可以使用以下工具获取最新信息：
                - deep_research: 深度联网搜索，获取多源搜索结果和网页正文
                - web_search: 基础网页搜索
                - multi_search: 多搜索引擎并行搜索
                - fetch_webpage: 抓取指定网页内容

                工具调用格式（由系统自动处理，无需手动构造JSON）。

                使用规则：
                1. 优先使用 deep_research 获取全面信息
                2. 如果 deep_research 返回空结果，可尝试 web_search 或 multi_search
                3. 收集足够信息后，综合分析并给出最终答案
                4. 不要向用户描述工具调用过程
                5. 不要输出"我会使用..."、"接下来我将..."等预告文字
                6. 使用中文回答，引用具体来源

                最终输出格式：
                ## 核心发现
                （2-3句话概括最重要的发现）

                ## 主要观点
                按主题分类，列出不同来源的共识

                ## 争议分析
                （如存在不同观点）标注分歧

                ## 不确定性说明
                哪些信息尚待证实

                ## 综合判断
                总体趋势和后续关注

                ## 信息来源
                引用所有来源
                """;
    }

    /**
     * 当 deep_research 返回 0 条结果时，要求 LLM 回退到 web_search 工具。
     */
    private String buildEmptyResultFallbackInstruction(String userQuery) {
        return """
                【系统指令 - 回退搜索策略】

                deep_research 未返回任何结果。请立即改用 web_search 工具重新搜索，参数如下：
                - query: "%s"

                调用完成后，根据搜索结果生成最终回答。不要输出任何解释性文字，直接调用工具即可。
                """.formatted(userQuery.replace("\"", "\\\""));
    }

    private Mono<ReactResult> reactLoop(List<ChatMessage> messages, int round,
                                         List<ToolResultEntry> collectedToolResults, String userQuery,
                                         int maxRounds, SearchRequirement searchRequirement,
                                         LLMRequest request) {
        return reactLoop(messages, round, new LinkedHashSet<>(), collectedToolResults, userQuery,
                maxRounds, searchRequirement, request);
    }

    private Mono<ReactResult> reactLoop(List<ChatMessage> messages, int round, Set<String> recentCalls,
                                         List<ToolResultEntry> collectedToolResults, String userQuery,
                                         int maxRounds, SearchRequirement searchRequirement,
                                         LLMRequest request) {
        String executionId = request.getExecutionState() != null
                ? request.getExecutionState().getExecutionId() : "N/A";
        String sessionId = request.getSessionId();

        if (round >= maxRounds) {
            log.info("[SearchAgent] requestId={} sessionId={} round={} maxRounds={} toolResults={} "
                    + "Max rounds reached, checking search requirement",
                    executionId, sessionId, round, maxRounds, collectedToolResults.size());

            if (collectedToolResults.isEmpty()) {
                if (searchRequirement == SearchRequirement.REQUIRED) {
                    log.warn("[ToolFallback] requestId={} reason=REQUIRED_SEARCH_NO_TOOL_RESULTS "
                            + "round={} maxRounds={}",
                            executionId, round, maxRounds);
                    return executeDeterministicFallback(userQuery, collectedToolResults, request);
                }
                log.warn("[SearchAgent] requestId={} Max rounds reached with no tool results, "
                        + "returning degraded response", executionId);
                return Mono.just(buildFinalResult(
                        "抱歉，搜索任务未能完成。可能是搜索服务暂时不可用或网络问题。请稍后重试，或尝试更具体的搜索词。",
                        collectedToolResults));
            }
            return llmClient.chatWithTools(messages, buildToolDefinitions())
                    .map(resp -> {
                        String c = resp.getContent();
                        String answer = (c != null && !c.isBlank()) ? c : "已达最大推理轮次，无法完成搜索。";
                        return buildFinalResult(answer, collectedToolResults);
                    });
        }

        return llmClient.chatWithTools(messages, buildToolDefinitions())
                .flatMap(response -> {
                    List<LlmToolResponse.ToolCall> effectiveCalls = response.getToolCalls();

                    log.info("[ToolDecision] requestId={} round={} toolCallDetected={} toolCallCount={} "
                            + "collectedToolResults={}",
                            executionId, round, !effectiveCalls.isEmpty(), effectiveCalls.size(),
                            collectedToolResults.size());

                    log.debug("[SearchAgent Round {}] LLM response: hasToolCalls={}, contentLength={}, collectedToolResults={}",
                            round, response.hasToolCalls(),
                            response.getContent() != null ? response.getContent().length() : 0,
                            collectedToolResults.size());

                    SessionTrace trace = SessionTraceHolder.currentOrNull();
                    if (trace != null) {
                        trace.recordToolDecision(
                                effectiveCalls.isEmpty() ? "NONE" : effectiveCalls.get(0).getName(),
                                response.hasToolCalls(),
                                effectiveCalls.size());
                    }

                    if (effectiveCalls.isEmpty()) {
                        log.warn("[SearchAgent Round {}] No tool calls detected. "
                                + "contentLen={}, contentPreview={}",
                                round,
                                response.getContent() != null ? response.getContent().length() : 0,
                                response.getContent() != null
                                        ? response.getContent().substring(0, Math.min(200, response.getContent().length()))
                                        : "(null)");
                    }

                    if (!effectiveCalls.isEmpty()) {
                        log.debug("[SearchAgent Round {}] Processing {} tool call(s): {}",
                                round, effectiveCalls.size(),
                                effectiveCalls.stream().map(LlmToolResponse.ToolCall::getName).toList());

                        List<Mono<Map.Entry<LlmToolResponse.ToolCall, ToolResultEntry>>> tasks = effectiveCalls.stream()
                                .map(tc -> {
                                    SessionTrace t = SessionTraceHolder.currentOrNull();
                                    if (t != null) {
                                        t.recordToolCall(tc.getName(),
                                                tc.getArguments() != null ? tc.getArguments().toString() : "",
                                                round);
                                    }
                                    return executeSingleTool(tc, request).map(result -> {
                                        String resultStr = toToolResultJson(tc, result);
                                        Map<String, Object> parsedContent = extractParsedContent(resultStr, tc.getName());
                                        SessionTrace t2 = SessionTraceHolder.currentOrNull();
                                        if (t2 != null) {
                                            t2.recordToolResult(tc.getName(), true, resultStr.length(), round, null);
                                        }
                                        log.info("[ToolExecution] requestId={} tool={} success={} round={}",
                                                executionId, tc.getName(),
                                                result.isSuccess() || result.data() != null,
                                                round);
                                        log.debug("[SearchAgent Round {}] tool response for {}: {}",
                                                round, tc.getName(),
                                                resultStr.length() > 500
                                                        ? resultStr.substring(0, 500) + "..."
                                                        : resultStr);
                                        return (Map.Entry<LlmToolResponse.ToolCall, ToolResultEntry>)
                                                new java.util.AbstractMap.SimpleEntry<>(
                                                        tc, new ToolResultEntry(tc.getName(), resultStr, parsedContent));
                                    });
                                })
                                .toList();

                        return Flux.merge(tasks)
                                .collectList()
                                .flatMap(results -> {
                                    log.debug("[SearchAgent Round {}] tool execution completed: {} results from {} tasks",
                                            round, results.size(), tasks.size());

                                    List<ChatMessage> updatedMessages = new ArrayList<>(messages);
                                    List<Map<String, Object>> assistantToolCalls = new ArrayList<>();
                                    List<ChatMessage> toolMessages = new ArrayList<>();
                                    List<ToolResultEntry> updatedToolResults = new ArrayList<>(collectedToolResults);

                                    for (var entry : results) {
                                        LlmToolResponse.ToolCall tc = entry.getKey();
                                        ToolResultEntry resultEntry = entry.getValue();
                                        String callId = tc.getId();
                                        assistantToolCalls.add(Map.of(
                                                "id", callId,
                                                "type", "function",
                                                "function", Map.of(
                                                        "name", tc.getName(),
                                                        "arguments", tc.getArguments()
                                                )
                                        ));
                                        toolMessages.add(ChatMessage.builder()
                                                .role("tool")
                                                .toolCallId(callId)
                                                .name(tc.getName())
                                                .content(resultEntry.jsonForLlm())
                                                .build());
                                        updatedToolResults.add(resultEntry);
                                    }
                                    updatedMessages.add(ChatMessage.builder()
                                            .role("assistant")
                                            .content("")
                                            .toolCalls(assistantToolCalls)
                                            .build());
                                    updatedMessages.addAll(toolMessages);

                                    log.debug("[SearchAgent Round {}] accumulated tool results: {} → {}",
                                            round, collectedToolResults.size(), updatedToolResults.size());

                                    Set<String> updatedCalls = new LinkedHashSet<>(recentCalls);
                                    for (var entry : results) {
                                        LlmToolResponse.ToolCall tc = entry.getKey();
                                        String callKey = tc.getName() + ":" + canonicalArgs(tc.getArguments());
                                        if (updatedCalls.contains(callKey)) {
                                            log.warn("[SearchAgent] Duplicate tool call detected: {}, breaking loop",
                                                    callKey);
                                            return Mono.just(buildFinalResult(
                                                    "检测到重复工具调用，已停止。",
                                                    updatedToolResults));
                                        }
                                        updatedCalls.add(callKey);
                                    }

                                    boolean deepResearchReturnedEmpty = false;
                                    boolean hasTriedFallback = false;
                                    for (ToolResultEntry toolResult : updatedToolResults) {
                                        if (toolResult.toolName().equals("deep_research")) {
                                            Map<String, Object> parsed = toolResult.parsedContent();
                                            if (parsed != null) {
                                                Object resultCount = parsed.get("resultCount");
                                                Object resList = parsed.get("results");
                                                boolean isEmpty = (resultCount instanceof Number n && n.intValue() == 0)
                                                        || (resList instanceof List<?> l && l.isEmpty());
                                                if (isEmpty) {
                                                    deepResearchReturnedEmpty = true;
                                                }
                                            } else {
                                                deepResearchReturnedEmpty = toolResult.jsonForLlm()
                                                        .contains("\"resultCount\":0");
                                            }
                                        }
                                        if (toolResult.toolName().equals("web_search")
                                                || toolResult.toolName().equals("multi_search")) {
                                            hasTriedFallback = true;
                                        }
                                    }

                                    if (deepResearchReturnedEmpty && !hasTriedFallback && round < maxRounds - 1) {
                                        log.warn("[SearchAgent Round {}] deep_research returned 0 results, "
                                                + "injecting fallback retry with web_search", round);
                                        List<ChatMessage> retryMessages = new ArrayList<>(updatedMessages);
                                        retryMessages.add(ChatMessage.builder()
                                                .role("user")
                                                .content(buildEmptyResultFallbackInstruction(userQuery))
                                                .build());
                                        return reactLoop(retryMessages, round + 1, updatedCalls,
                                                updatedToolResults, userQuery, maxRounds, searchRequirement, request);
                                    }

                                    return reactLoop(updatedMessages, round + 1, updatedCalls,
                                            updatedToolResults, userQuery, maxRounds, searchRequirement, request);
                                });
                    }

                    String finalContent = response.getContent();
                    if (finalContent == null || finalContent.isBlank()) {
                        finalContent = "";
                    }

                    boolean hasNoToolResults = collectedToolResults.isEmpty();

                    if (hasNoToolResults && round < maxRounds) {
                        if (searchRequirement == SearchRequirement.REQUIRED) {
                            if (round == 0) {
                                log.warn("[SearchAgent] requestId={} round={} No tool calls, "
                                        + "REQUIRED search, retrying with Native Tool Calling",
                                        executionId, round);
                                List<ChatMessage> retryMessages = new ArrayList<>(messages);
                                retryMessages.add(ChatMessage.builder()
                                        .role("user")
                                        .content("你还没有调用搜索工具。请立即调用 deep_research 工具来搜索以下问题："
                                                + userQuery)
                                        .build());
                                return reactLoop(retryMessages, round + 1, recentCalls,
                                        collectedToolResults, userQuery, maxRounds, searchRequirement, request);
                            }
                            log.warn("[ToolFallback] requestId={} reason=REQUIRED_SEARCH_WITHOUT_TOOL_CALL "
                                    + "round={} tool=deep_research",
                                    executionId, round);
                            return executeDeterministicFallback(userQuery, collectedToolResults, request);
                        }

                        if (searchRequirement == SearchRequirement.OPTIONAL) {
                            if (round == 0) {
                                log.warn("[SearchAgent Round {}] No tool calls, OPTIONAL search, "
                                        + "retrying once", round);
                                List<ChatMessage> retryMessages = new ArrayList<>(messages);
                                retryMessages.add(ChatMessage.builder()
                                        .role("user")
                                        .content("你还没有调用搜索工具。如果问题涉及最新信息，请调用 deep_research 工具搜索："
                                                + userQuery + "。如果问题不涉及最新信息，可以直接回答。")
                                        .build());
                                return reactLoop(retryMessages, round + 1, recentCalls,
                                        collectedToolResults, userQuery, maxRounds, searchRequirement, request);
                            }
                        }

                        if (searchRequirement == SearchRequirement.NONE) {
                            log.debug("[SearchAgent Round {}] No tool calls, NONE search requirement, "
                                    + "accepting response", round);
                        }
                    }

                    log.debug("[SearchAgent Round {}] No tool calls, building final result with {} collected tool results",
                            round, collectedToolResults.size());
                    return Mono.just(buildFinalResult(finalContent, collectedToolResults));
                });
    }

    /**
     * 确定性回退：当 SearchRequirement.REQUIRED 且 LLM 未调用工具时，
     * 由代码直接调用 ToolExecutor 执行 deep_research。
     */
    private Mono<ReactResult> executeDeterministicFallback(
            String userQuery, List<ToolResultEntry> existingResults, LLMRequest request) {
        String executionId = request.getExecutionState() != null
                ? request.getExecutionState().getExecutionId() : "N/A";
        log.info("[ToolFallback] requestId={} reason=EXECUTING_DETERMINISTIC_FALLBACK tool=deep_research "
                + "existingResults={}",
                executionId, existingResults.size());

        if (toolExecutor == null || toolRegistry == null) {
            log.error("[ToolFallback] requestId={} Cannot execute deterministic fallback: "
                    + "toolExecutor or toolRegistry is null", executionId);
            return Mono.just(buildFinalResult(
                    "抱歉，搜索工具不可用，无法完成所需的搜索任务。",
                    existingResults));
        }

        String toolCallId = "fallback-" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> args = new HashMap<>();
        args.put("query", userQuery);
        args.put("depth", "2");

        ToolExecutionRequest toolRequest = new ToolExecutionRequest();
        toolRequest.setToolName("deep_research");
        toolRequest.setArguments(args);
        toolRequest.setRequestId(toolCallId);

        ExecutionState execState = request.getExecutionState();
        if (execState != null) {
            execState.waitingForTool(toolCallId);
        }

        log.info("[ToolExecution] requestId={} tool=deep_research started=true fallback=true", executionId);

        return toolExecutor.execute(toolRequest)
                .map(result -> {
                    if (execState != null) {
                        execState.toolCompleted(toolCallId);
                    }
                    log.info("[ToolExecution] requestId={} tool=deep_research finished=true fallback=true "
                            + "success={}",
                            executionId, result.isSuccess() || result.data() != null);

                    String resultStr = toToolResultJson(
                            new LlmToolResponse.ToolCall(toolCallId, "deep_research", args),
                            result);
                    Map<String, Object> parsedContent = extractParsedContent(resultStr, "deep_research");

                    List<ToolResultEntry> allResults = new ArrayList<>(existingResults);
                    allResults.add(new ToolResultEntry("deep_research", resultStr, parsedContent));

                    return buildFinalResult(
                            "以下是通过确定性搜索回退机制获取的结果。",
                            allResults);
                })
                .onErrorResume(error -> {
                    log.error("[ToolFallback] requestId={} Deterministic fallback execution failed: {}",
                            executionId, error.getMessage());
                    if (execState != null) {
                        execState.toolCompleted(toolCallId);
                    }
                    return Mono.just(buildFinalResult(
                            "搜索任务执行失败：" + (error.getMessage() != null ? error.getMessage() : "未知错误"),
                            existingResults));
                });
    }

    private Mono<ToolExecutionResult> executeSingleTool(LlmToolResponse.ToolCall toolCall, LLMRequest request) {
        log.debug("[LLM-DIAG] Node5-ToolExecutor: ENTERING tool execution - toolName={}, arguments={}, toolExecutor={}",
                toolCall.getName(), toolCall.getArguments(),
                toolExecutor != null ? toolExecutor.getClass().getSimpleName() : "null");
        if (toolExecutor == null) {
            log.error("[LLM-DIAG] Node5-CRITICAL: toolExecutor is NULL! Cannot execute tool: {}", toolCall.getName());
            return Mono.just(ToolExecutionResult.executionError(toolCall.getId(), toolCall.getName(),
                    com.mcp.tools.model.ToolError.internal("toolExecutor is null"), java.time.Duration.ZERO));
        }

        ExecutionState execState = request.getExecutionState();
        if (execState != null) {
            execState.waitingForTool(toolCall.getId());
        }

        ToolExecutionRequest toolRequest = new ToolExecutionRequest();
        toolRequest.setToolName(toolCall.getName());
        toolRequest.setArguments(new HashMap<>(toolCall.getArguments()));
        toolRequest.setRequestId(toolCall.getId());

        return toolRegistry.getTool(toolCall.getName())
                .map(toolDef -> {
                    MemoryIdentity identity = new MemoryIdentity(null, request.getSessionId(),
                            request.getUserId(), null, null);
                    PolicyEngine.PolicyDecision decision =
                            policyEngine.evaluate(identity, getId(), toolDef, null,
                                    request.getExecutionPlan());
                    return decision;
                })
                .defaultIfEmpty(PolicyEngine.PolicyDecision.DENY)
                .flatMap(decision -> {
                    if (decision == PolicyEngine.PolicyDecision.DENY) {
                        log.warn("[SearchAgent] PolicyEngine DENY: tool={}", toolCall.getName());
                        if (execState != null) {
                            execState.toolCompleted(toolCall.getId());
                        }
                        SessionTrace t = SessionTraceHolder.currentOrNull();
                        if (t != null) {
                            t.recordPolicyDecision(toolCall.getName(), "DENY", "PolicyEngine denied tool execution");
                        }
                        return Mono.just(ToolExecutionResult.denied(
                                toolCall.getId(), toolCall.getName(), "PolicyEngine denied tool execution"));
                    }
                    return toolExecutor.execute(toolRequest)
                            .doOnSuccess(result -> {
                                if (execState != null) {
                                    execState.toolCompleted(toolCall.getId());
                                }
                            })
                            .onErrorResume(error -> {
                                log.warn("[SearchAgent] Tool execution error: {} - {}",
                                        toolCall.getName(), error.getMessage());
                                if (execState != null) {
                                    execState.toolCompleted(toolCall.getId());
                                }
                                return Mono.just(ToolExecutionResult.executionError(
                                        toolCall.getId(), toolCall.getName(),
                                        com.mcp.tools.model.ToolError.internal(
                                                error.getMessage() != null ? error.getMessage() : "unknown error"),
                                        java.time.Duration.ZERO));
                            });
                });
    }

    private String toToolResultJson(LlmToolResponse.ToolCall toolCall, ToolExecutionResult toolResult) {
        if (toolResult == null) {
            return "{\"success\":false,\"tool\":\"" + toolCall.getName()
                    + "\",\"toolCallId\":\"" + toolCall.getId()
                    + "\",\"error\":\"toolResult is null\"}";
        }
        return toolResult.toJson();
    }

    private boolean isToolFailure(String content) {
        if (content == null) return true;
        return content.contains("\"ok\":false")
                || content.contains("\"success\":false")
                || content.contains("\"status\":\"FAILURE\"");
    }

    private String canonicalArgs(Map<String, Object> args) {
        if (args == null || args.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(args);
        } catch (Exception e) {
            return args.toString();
        }
    }

    private List<Map<String, Object>> buildToolDefinitions() {
        if (toolRegistry == null) {
            log.error("[LLM-DIAG] Node2-FATAL: ToolRegistry is NULL! No tools will be available.");
            return List.of();
        }

        List<ToolDefinition> allTools = toolRegistry.getAllTools();
        List<ToolDefinition> enabledTools = toolRegistry.getEnabledTools();
        log.debug("[LLM-DIAG] Node2-ToolRegistry: totalRegisteredTools={}, enabledTools={}, toolNames={}",
                allTools.size(), enabledTools.size(),
                allTools.stream().map(ToolDefinition::getName).toList());
        if (allTools.isEmpty()) {
            log.error("[LLM-DIAG] Node2-CRITICAL: ToolRegistry.getAllTools() returned EMPTY! "
                    + "No tools will be passed to the LLM. "
                    + "Check if tools are registered in McpOrchestratorApplication. "
                    + "toolRegistry={}, toolRegistry class={}",
                    toolRegistry, toolRegistry != null ? toolRegistry.getClass().getName() : "null");
        } else {
            for (ToolDefinition td : allTools) {
                log.debug("[LLM-DIAG] Node2-ToolDetail: name={}, enabled={}, category={}, description={}",
                        td.getName(), td.isEnabled(), td.getCategory(),
                        td.getDescription() != null ? td.getDescription().substring(0, Math.min(80, td.getDescription().length())) : "null");
            }
        }

        List<String> allowedToolNames = getAgentCard().getToolNames();
        log.debug("[LLM-DIAG] Node2-MultiToolFilter: AgentCard.toolNames={}, totalRegistryTools={}",
                allowedToolNames, allTools.size());

        List<ToolDefinition> filteredTools = new ArrayList<>();
        List<String> skippedTools = new ArrayList<>();
        for (ToolDefinition td : allTools) {
            if (allowedToolNames.contains(td.getName())) {
                filteredTools.add(td);
            } else {
                skippedTools.add(td.getName());
            }
        }
        log.debug("[LLM-DIAG] Node2-MultiToolFilter: filteredTools={}, skippedTools={}",
                filteredTools.stream().map(ToolDefinition::getName).toList(), skippedTools);

        if (filteredTools.isEmpty()) {
            log.error("[LLM-DIAG] Node2-CRITICAL: After filtering by AgentCard.toolNames={}, "
                    + "NO tools remain! Check if tool names in AgentCard match registry. "
                    + "Registry has: {}. "
                    + "SearchAgent will run without tools — no fallback to all/similar tools.",
                    allowedToolNames,
                    allTools.stream().map(ToolDefinition::getName).toList());
            return List.of();
        }

        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolDefinition td : filteredTools) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", td.getName());
            function.put("description", td.getDescription());

            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("type", "object");

            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();
            try {
                Map<String, Object> schema = objectMapper.readValue(
                        td.getInputSchema(), new TypeReference<Map<String, Object>>() {});
                if (schema.containsKey("properties")) {
                    Map<String, Object> props = (Map<String, Object>) schema.get("properties");
                    for (Map.Entry<String, Object> entry : props.entrySet()) {
                        Map<String, Object> propDef = new LinkedHashMap<>((Map<String, Object>) entry.getValue());
                        properties.put(entry.getKey(), propDef);
                    }
                }
                if (schema.containsKey("required") && schema.get("required") instanceof List<?> reqList) {
                    for (Object item : reqList) {
                        if (item instanceof String s) {
                            required.add(s);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[SearchAgent] Failed to parse inputSchema for tool {}: {}",
                        td.getName(), e.getMessage());
            }
            parameters.put("properties", properties);
            parameters.put("required", required);
            function.put("parameters", parameters);

            Map<String, Object> toolDef = new LinkedHashMap<>();
            toolDef.put("type", "function");
            toolDef.put("function", function);
            tools.add(toolDef);
        }
        return tools;
    }

    /**
     * 从 reactLoop 返回的最终答案和工具调用结果中提取结构化数据。
     */
    private ReactResult buildFinalResult(String finalAnswer, List<ToolResultEntry> toolResults) {
        List<SearchResult> searchResults = new ArrayList<>();
        List<SearchDocument> documents = new ArrayList<>();

        log.debug("[SearchAgent] buildFinalResult: parsing {} tool result entries", toolResults.size());

        SessionTrace trace = SessionTraceHolder.currentOrNull();
        if (trace != null) {
            trace.recordLlmResponse(finalAnswer != null ? finalAnswer.length() : 0, "search-agent", 0);
        }

        for (ToolResultEntry entry : toolResults) {
            try {
                Map<String, Object> innerMap = entry.parsedContent();
                if (innerMap == null) {
                    log.debug("[SearchAgent] buildFinalResult: no parsedContent for tool={}, skipping",
                            entry.toolName());
                    continue;
                }

                if (entry.toolName().equals("deep_research")) {
                    parseDeepResearchResult(innerMap, searchResults, documents);
                } else if (entry.toolName().equals("fetch_webpage")) {
                    SearchDocument doc = parseFetchWebpageResult(innerMap, entry.jsonForLlm());
                    if (doc != null) documents.add(doc);
                } else if (entry.toolName().equals("multi_search") || entry.toolName().equals("web_search")) {
                    parseSearchToolResult(innerMap, searchResults);
                }
            } catch (Exception e) {
                log.warn("[SearchAgent] Failed to parse tool result for {}: {}",
                        entry.toolName(), e.getMessage());
            }
        }

        log.debug("[SearchAgent] Extracted {} search results and {} documents for synthesis",
                searchResults.size(), documents.size());
        return new ReactResult(finalAnswer, searchResults, documents);
    }

    private Map<String, Object> extractParsedContent(String jsonStr, String toolName) {
        try {
            if (!jsonStr.startsWith("{")) return null;

            Map<String, Object> wrapper = objectMapper.readValue(jsonStr,
                    new TypeReference<Map<String, Object>>() {});

            Object contentObj = wrapper.get("content");
            if (contentObj == null) {
                log.debug("[SearchAgent] extractParsedContent: no 'content' field for {}, keys={}",
                        toolName, wrapper.keySet());
                return wrapper;
            }

            if (contentObj instanceof String contentStr) {
                if (contentStr.startsWith("{")) {
                    return objectMapper.readValue(contentStr,
                            new TypeReference<Map<String, Object>>() {});
                }
                if (contentStr.startsWith("[")) {
                    Map<String, Object> wrapped = new LinkedHashMap<>();
                    wrapped.put("results", objectMapper.readValue(contentStr,
                            new TypeReference<List<Map<String, Object>>>() {}));
                    return wrapped;
                }
                Map<String, Object> plainResult = new LinkedHashMap<>();
                plainResult.put("content", contentStr);
                plainResult.put("success", wrapper.get("success"));
                plainResult.put("tool", wrapper.get("tool"));
                return plainResult;
            }

            if (contentObj instanceof Map) {
                return (Map<String, Object>) contentObj;
            }

            return wrapper;
        } catch (Exception e) {
            log.debug("[SearchAgent] extractParsedContent failed for {}: {}", toolName, e.getMessage());
            return null;
        }
    }

    private void parseDeepResearchResult(Map<String, Object> map, List<SearchResult> searchResults,
                                          List<SearchDocument> documents) {
        log.debug("[SearchAgent] parseDeepResearchResult: map keys={}", map.keySet());

        if (map.containsKey("results")) {
            Object resultsObj = map.get("results");
            if (resultsObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        Map<String, Object> r = (Map<String, Object>) m;
                        searchResults.add(new SearchResult(
                                (String) r.getOrDefault("title", ""),
                                (String) r.getOrDefault("snippet", ""),
                                (String) r.getOrDefault("url", ""),
                                (String) r.getOrDefault("source", "Unknown"),
                                r.get("score") instanceof Number n ? n.doubleValue() : 0.0
                        ));
                    }
                }
                log.debug("[SearchAgent] parseDeepResearchResult: extracted {} results", searchResults.size());
            }
        }
        if (map.containsKey("documents")) {
            Object docsObj = map.get("documents");
            if (docsObj instanceof List<?> docsList) {
                for (Object item : docsList) {
                    if (item instanceof Map<?, ?> m) {
                        Map<String, Object> d = (Map<String, Object>) m;
                        documents.add(new SearchDocument(
                                (String) d.getOrDefault("title", ""),
                                (String) d.getOrDefault("url", ""),
                                (String) d.getOrDefault("content", ""),
                                d.get("contentLength") instanceof Number n ? n.intValue() : 0
                        ));
                    }
                }
                log.debug("[SearchAgent] parseDeepResearchResult: extracted {} documents", documents.size());
            }
        }
        // 递归搜索结果（round2）
        if (map.containsKey("round2")) {
            Object round2Obj = map.get("round2");
            if (round2Obj instanceof Map<?, ?> r2) {
                Map<String, Object> round2 = (Map<String, Object>) r2;
                if (round2.containsKey("results")) {
                    Object r2ResultsObj = round2.get("results");
                    if (r2ResultsObj instanceof List<?> list) {
                        for (Object item : list) {
                            if (item instanceof Map<?, ?> m) {
                                Map<String, Object> r = (Map<String, Object>) m;
                                searchResults.add(new SearchResult(
                                        (String) r.getOrDefault("title", ""),
                                        (String) r.getOrDefault("snippet", ""),
                                        (String) r.getOrDefault("url", ""),
                                        (String) r.getOrDefault("source", "Unknown"),
                                        r.get("score") instanceof Number n ? n.doubleValue() : 0.0
                                ));
                            }
                        }
                    }
                }
                if (round2.containsKey("documents")) {
                    Object r2DocsObj = round2.get("documents");
                    if (r2DocsObj instanceof List<?> docsList) {
                        for (Object item : docsList) {
                            if (item instanceof Map<?, ?> m) {
                                Map<String, Object> d = (Map<String, Object>) m;
                                documents.add(new SearchDocument(
                                        (String) d.getOrDefault("title", ""),
                                        (String) d.getOrDefault("url", ""),
                                        (String) d.getOrDefault("content", ""),
                                        d.get("contentLength") instanceof Number n ? n.intValue() : 0
                                ));
                            }
                        }
                    }
                }
            }
        }
    }

    private SearchDocument parseFetchWebpageResult(Map<String, Object> map, String jsonForLlm) {
        String textContent = (String) map.getOrDefault("content", "");
        String title = (String) map.getOrDefault("title", "");
        String url = (String) map.getOrDefault("url", "");

        if (textContent.contains("网页标题:") || textContent.contains("链接:")) {
            String[] lines = textContent.split("\n");
            for (String line : lines) {
                if (line.startsWith("网页标题:") && title.isEmpty()) {
                    title = line.substring("网页标题:".length()).trim();
                }
                if (line.startsWith("链接:") && url.isEmpty()) {
                    url = line.substring("链接:".length()).trim();
                }
            }
            int dashIdx = textContent.indexOf("---");
            if (dashIdx >= 0) {
                textContent = textContent.substring(dashIdx + 3).trim();
            }
        }

        if (title.isEmpty() && url.isEmpty() && textContent.isEmpty()) {
            return null;
        }
        return new SearchDocument(title, url, textContent, textContent.length());
    }

    private void parseSearchToolResult(Map<String, Object> map, List<SearchResult> searchResults) {
        if (map.containsKey("results")) {
            Object resultsObj = map.get("results");
            if (resultsObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        Map<String, Object> r = (Map<String, Object>) m;
                        searchResults.add(new SearchResult(
                                (String) r.getOrDefault("title", ""),
                                (String) r.getOrDefault("snippet", ""),
                                (String) r.getOrDefault("url", ""),
                                (String) r.getOrDefault("source", "Unknown"),
                                r.get("score") instanceof Number n ? n.doubleValue() : 0.0
                        ));
                    }
                }
            }
        }
    }

    private record ReactResult(String finalAnswer, List<SearchResult> searchResults, List<SearchDocument> documents) {}

    private record ToolResultEntry(String toolName, String jsonForLlm, Map<String, Object> parsedContent) {}
}