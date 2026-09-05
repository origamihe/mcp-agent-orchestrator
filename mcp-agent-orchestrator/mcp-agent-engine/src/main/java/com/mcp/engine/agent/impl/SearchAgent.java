package com.mcp.engine.agent.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private volatile LLMRequest currentRequest;

    private static final int DEFAULT_MAX_TOOL_ROUNDS = 5;

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
                .version("2.1.0")
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
        this.currentRequest = request;
        log.info("[SearchAgent] Executing: session={}, userMessage={}",
                request.getSessionId(),
                request.getUserMessage() != null
                        ? request.getUserMessage().substring(0, Math.min(50, request.getUserMessage().length()))
                        : "(empty)");

        if (toolExecutor == null) {
            log.warn("[SearchAgent] ToolExecutor 未配置，回退到纯文本模式（无搜索能力）");
            if (agentRuntime == null) {
                return Mono.just("[SearchAgent] AgentRuntime 未配置，无法执行搜索任务。");
            }
            String systemPrompt = request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()
                    ? request.getSystemPrompt()
                    : "你是一个专业的搜索助手。请用中文回答，提供信息来源。";
            String fallbackSystemPrompt = systemPrompt + """


                    【重要提示 - 搜索工具不可用】
                    当前环境未配置搜索工具，你无法进行实时联网搜索。
                    请基于你已有的知识尽力回答用户问题，并明确告知用户：
                    1. 你无法进行实时搜索，回答基于已有知识
                    2. 建议用户自行验证关键信息
                    3. 如果问题涉及最新信息（如新闻、实时数据），请诚实说明你无法提供
                    """;
            return agentRuntime.run(fallbackSystemPrompt, request.getUserMessage());
        }

        String systemPrompt = request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()
                ? request.getSystemPrompt()
                : "你是一个专业的研究助手，具备深度搜索、证据分析和综合研判能力。";

        String toolInstructions = buildToolInstructions();
        String fullSystemPrompt = systemPrompt + "\n\n" + toolInstructions;

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder().role("system").content(fullSystemPrompt).build());
        messages.add(ChatMessage.builder().role("user").content(request.getUserMessage()).build());

        return reactLoop(messages, 0, new ArrayList<>(), request.getUserMessage())
                .timeout(Duration.ofSeconds(300))
                .flatMap(result -> {
                    if (researchSynthesizer != null
                            && (!result.searchResults().isEmpty() || !result.documents().isEmpty())) {
                        log.info("[SearchAgent] Post-processing: synthesizing {} results and {} documents",
                                result.searchResults().size(), result.documents().size());
                        return researchSynthesizer.synthesize(
                                request.getUserMessage(), result.searchResults(), result.documents());
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
                    log.error("[SearchAgent] Search failed with error: {}", e.getMessage(), e);
                    return Mono.just("搜索任务执行失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误") + "。请稍后重试。");
                });
    }

    private String buildToolInstructions() {
        return """
                【内部规则 - 仅用于指导你的行为，严禁向用户输出以下任何内容】
                
                ⚠️ 【最高优先级 - 强制工具调用规则】
                你是一个搜索助手，职责是搜索最新信息并综合分析。以下规则必须严格遵守：
                
                1. 对于任何涉及以下关键词或意图的请求，你必须先调用搜索工具，严禁在未调用工具的情况下直接回答：
                   - 搜索、查找、检索、查询、最新、新闻、动态、资料、信息、热点、事件、报道
                   - 今天、最近、近期、当前、现在、实时
                   - 地缘政治、经济、科技、天气、股市、汇率、体育、娱乐
                   - 任何需要外部信息才能准确回答的问题
                
                2. 如果你没有调用任何工具，则不得生成任何正文内容。
                   唯一允许的非工具输出是：CALL_SEARCH_TOOL
                
                3. 工具调用格式（必须严格使用以下JSON格式，放在代码块中）：
                   ```
                   ```json
                   {"tool_name": "deep_research", "query": "用户问题", "depth": "2"}
                   ```
                   ```
                   或使用完整格式：
                   ```
                   ```json
                   {"name": "deep_research", "arguments": {"query": "用户问题", "depth": "2"}}
                   ```
                   ```
                   ⚠️ 关键：必须将JSON放在 ```json 和 ``` 之间，否则系统无法识别！
                
                4. 工具调用顺序（在内部执行，不要向用户描述）：
                   a) 首先调用 deep_research(query="{用户问题}", depth="2") 获取多源搜索结果和网页正文
                   b) 如果 deep_research 返回的 documents 不足，调用 fetch_webpage(url="...") 补充抓取
                   c) 收集足够信息后，开始综合分析
                
                5. 严禁向用户输出的内容：
                   - "我会使用...工具"、"接下来我将..."、"请稍等..."——直接调用工具，不要预告
                   - "第1步...第2步..."——不要暴露执行计划
                   - "以下是搜索结果预览结构"——不要输出模板框架
                   - 任何对工具调用过程的描述——用户只需要最终答案
                
                6. 最终输出格式（仅在所有工具调用完成后输出一次）：
                   按主题整合信息，而非按搜索引擎分类。使用以下结构：
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
                
                7. 关键规则：
                   - 不要输出任何"我将要..."或"以下是..."的预告文字
                   - 工具调用之间不要插入任何解释性文字
                   - 使用中文
                   - 引用具体来源
                   - 每一轮最多调用 2 个工具
                """;
    }

    /**
     * 当 LLM 在首轮没有调用工具时，强制要求其调用搜索工具。
     * 通过追加一条 user 消息来引导 LLM 重新规划，确保搜索任务真正执行。
     */
    private String buildToolUseRetryInstruction(String llmContent, String userQuery) {
        return """
                【系统指令 - 你必须执行以下操作，不要输出任何解释性文字】
                
                你刚才没有调用任何搜索工具。对于搜索任务，你必须先调用工具获取信息，然后再整理答案。
                
                请立即调用 deep_research 工具，参数如下：
                - query: "%s"
                - depth: "2"
                
                ⚠️ 工具调用格式（必须严格使用以下JSON格式，放在代码块中）：
                ```json
                {"name": "deep_research", "arguments": {"query": "%s", "depth": "2"}}
                ```
                
                不要输出任何其他文字，直接输出上面的JSON代码块即可！
                """.formatted(userQuery.replace("\"", "\\\""), userQuery.replace("\"", "\\\""));
    }

    /**
     * 第二次重试时的强力指令：当第一次重试仍然失败时使用。
     */
    private String buildForceToolCallInstruction(String userQuery) {
        return """
                【系统指令 - 最后一次机会，必须调用工具！】
                
                你连续两次没有调用搜索工具。现在你必须调用 deep_research 工具。
                
                请在回复中仅输出以下JSON代码块，不要添加任何其他文字：
                
                ```json
                {"name": "deep_research", "arguments": {"query": "%s", "depth": "2"}}
                ```
                
                ⚠️ 这是系统级强制指令，不要输出任何其他内容！
                """.formatted(userQuery.replace("\"", "\\\""));
    }

    /**
     * 当 deep_research 返回 0 条结果时，要求 LLM 回退到 web_search 工具。
     * 通过追加一条 user 消息来引导 LLM 使用备用搜索策略。
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
                                         List<ToolResultEntry> collectedToolResults, String userQuery) {
        return reactLoop(messages, round, new LinkedHashSet<>(), collectedToolResults, userQuery);
    }

    private Mono<ReactResult> reactLoop(List<ChatMessage> messages, int round, Set<String> recentCalls,
                                         List<ToolResultEntry> collectedToolResults, String userQuery) {
        int maxRounds = DEFAULT_MAX_TOOL_ROUNDS;
        if (round >= maxRounds) {
            log.info("[SearchAgent] Max rounds ({}) reached, forcing final answer with {} collected results",
                    maxRounds, collectedToolResults.size());
            if (collectedToolResults.isEmpty()) {
                log.warn("[SearchAgent] Max rounds reached with no tool results, returning degraded response");
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

                    log.info("[SearchAgent Round {}] LLM response: hasToolCalls={}, contentLength={}, collectedToolResults={}",
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

                    // 文本回退已在 SpringAiLlmClient.toLlmToolResponse() 中统一处理，
                    // 此处仅做防御性日志：如果仍然为空，说明 LLM 完全未尝试调用工具
                    if (effectiveCalls.isEmpty()) {
                        log.warn("[SearchAgent Round {}] No tool calls detected (SpringAiLlmClient text fallback also failed). "
                                + "contentLen={}, contentPreview={}",
                                round,
                                response.getContent() != null ? response.getContent().length() : 0,
                                response.getContent() != null
                                        ? response.getContent().substring(0, Math.min(200, response.getContent().length()))
                                        : "(null)");
                    }

                    if (!effectiveCalls.isEmpty()) {
                        log.info("[SearchAgent Round {}] Processing {} tool call(s): {}",
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
                                    return executeSingleTool(tc).map(result -> {
                                        String resultStr = toToolResultJson(tc, result);
                                        Map<String, Object> parsedContent = extractParsedContent(resultStr, tc.getName());
                                        SessionTrace t2 = SessionTraceHolder.currentOrNull();
                                        if (t2 != null) {
                                            t2.recordToolResult(tc.getName(), true, resultStr.length(), round, null);
                                        }
                                        log.info("[SearchAgent Round {}] RAW tool response for {}: {}",
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
                                    log.info("[SearchAgent Round {}] tool execution completed: {} results from {} tasks",
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
                                        log.info("[SearchAgent Round {}] added tool result entry: {} ({} chars)",
                                                round, tc.getName(), resultEntry.jsonForLlm().length());
                                    }
                                    updatedMessages.add(ChatMessage.builder()
                                            .role("assistant")
                                            .content("")
                                            .toolCalls(assistantToolCalls)
                                            .build());
                                    updatedMessages.addAll(toolMessages);

                                    log.info("[SearchAgent Round {}] accumulated tool results: {} → {}",
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
                                            // P1-3 改进：使用 parsedContent 检测，而非字符串匹配
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
                                                // 回退到字符串匹配
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
                                                updatedToolResults, userQuery);
                                    }

                                    return reactLoop(updatedMessages, round + 1, updatedCalls,
                                            updatedToolResults, userQuery);
                                });
                    }

                    String finalContent = response.getContent();
                    if (finalContent == null || finalContent.isBlank()) {
                        finalContent = "";
                    }

                    boolean hasNoToolResults = collectedToolResults.isEmpty();
                    boolean isEarlyRound = round <= 1;

                    if (hasNoToolResults && isEarlyRound) {
                        // 第一次重试：使用常规工具调用指令
                        if (round == 0) {
                            log.warn("[SearchAgent Round {}] No tool calls and no collected results yet, "
                                    + "forcing retry with tool-use instruction", round);
                            List<ChatMessage> retryMessages = new ArrayList<>(messages);
                            retryMessages.add(ChatMessage.builder()
                                    .role("user")
                                    .content(buildToolUseRetryInstruction(response.getContent(), userQuery))
                                    .build());
                            return reactLoop(retryMessages, round + 1, recentCalls,
                                    collectedToolResults, userQuery);
                        }
                        // 第二次重试：使用强力指令，直接给出JSON格式
                        log.warn("[SearchAgent Round {}] First retry also failed, "
                                + "forcing second retry with explicit JSON format", round);
                        List<ChatMessage> retryMessages = new ArrayList<>(messages);
                        retryMessages.add(ChatMessage.builder()
                                .role("user")
                                .content(buildForceToolCallInstruction(userQuery))
                                .build());
                        return reactLoop(retryMessages, round + 1, recentCalls,
                                collectedToolResults, userQuery);
                    }

                    if (hasNoToolResults) {
                        log.warn("[SearchAgent Round {}] No tool calls and no collected results, "
                                + "but retry limit reached, using fallback", round);
                        finalContent = "抱歉，搜索未能返回有效结果，请稍后再试。";
                    }

                    log.info("[SearchAgent Round {}] No tool calls, building final result with {} collected tool results",
                            round, collectedToolResults.size());
                    return Mono.just(buildFinalResult(finalContent, collectedToolResults));
                });
    }

    /**
     * 从 reactLoop 返回的最终答案和工具调用结果中提取结构化数据。
     * 直接使用 ToolResultEntry.parsedContent() 避免 unwrapToolResult 的二次解析开销。
     */
    private ReactResult buildFinalResult(String finalAnswer, List<ToolResultEntry> toolResults) {
        List<SearchResult> searchResults = new ArrayList<>();
        List<SearchDocument> documents = new ArrayList<>();

        log.info("[SearchAgent] buildFinalResult: parsing {} tool result entries", toolResults.size());

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

        log.info("[SearchAgent] Extracted {} search results and {} documents for synthesis",
                searchResults.size(), documents.size());
        return new ReactResult(finalAnswer, searchResults, documents);
    }

    /**
     * 从 toToolResultJson 的 JSON 字符串中提取解析后的 content 内容。
     * 输入格式：{"success":true,"tool":"deep_research","content":"{\"query\":...,\"results\":[...]}"}
     * 返回：content 字段解析后的 Map（即工具实际输出的结构化数据）
     */
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
        // fetch_webpage 返回的可能是 wrapper 中的 content 字段（纯文本）
        String textContent = (String) map.getOrDefault("content", "");
        String title = (String) map.getOrDefault("title", "");
        String url = (String) map.getOrDefault("url", "");

        // 如果 content 是纯文本格式（"网页标题: xxx\n链接: xxx\n---\nxxx"），尝试解析
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
            // 提取 --- 之后的内容作为正文
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

    private Mono<ToolExecutionResult> executeSingleTool(LlmToolResponse.ToolCall toolCall) {
        // ===== 诊断节点5：ToolExecutor 是否真正进入执行 =====
        log.info("[LLM-DIAG] Node5-ToolExecutor: ENTERING tool execution - toolName={}, arguments={}, toolExecutor={}",
                toolCall.getName(), toolCall.getArguments(),
                toolExecutor != null ? toolExecutor.getClass().getSimpleName() : "null");
        if (toolExecutor == null) {
            log.error("[LLM-DIAG] Node5-CRITICAL: toolExecutor is NULL! Cannot execute tool: {}", toolCall.getName());
            return Mono.just(ToolExecutionResult.executionError(toolCall.getId(), toolCall.getName(),
                    com.mcp.tools.model.ToolError.internal("toolExecutor is null"), java.time.Duration.ZERO));
        }

        ExecutionState execState = currentRequest != null ? currentRequest.getExecutionState() : null;
        if (execState != null) {
            execState.waitingForTool(toolCall.getId());
        }

        ToolExecutionRequest request = new ToolExecutionRequest();
        request.setToolName(toolCall.getName());
        request.setArguments(new HashMap<>(toolCall.getArguments()));
        request.setRequestId(toolCall.getId());

        return toolRegistry.getTool(toolCall.getName())
                .map(toolDef -> {
                    MemoryIdentity identity = currentRequest != null
                            ? new MemoryIdentity(null, currentRequest.getSessionId(),
                                    currentRequest.getUserId(), null, null)
                            : null;
                    PolicyEngine.PolicyDecision decision =
                            policyEngine.evaluate(identity, getId(), toolDef, null,
                                    currentRequest != null ? currentRequest.getExecutionPlan() : null);
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
                    return toolExecutor.execute(request)
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
        // P3-1 防御：toolRegistry 为空时的处理
        if (toolRegistry == null) {
            log.error("[LLM-DIAG] Node2-FATAL: ToolRegistry is NULL! No tools will be available.");
            return List.of();
        }

        // ===== 诊断节点2：ToolRegistry 当前注册的工具 =====
        List<ToolDefinition> allTools = toolRegistry.getAllTools();
        List<ToolDefinition> enabledTools = toolRegistry.getEnabledTools();
        log.info("[LLM-DIAG] Node2-ToolRegistry: totalRegisteredTools={}, enabledTools={}, toolNames={}",
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
                log.info("[LLM-DIAG] Node2-ToolDetail: name={}, enabled={}, category={}, description={}",
                        td.getName(), td.isEnabled(), td.getCategory(),
                        td.getDescription() != null ? td.getDescription().substring(0, Math.min(80, td.getDescription().length())) : "null");
            }
        }

        // ===== 多工具协同：按 AgentCard.toolNames 过滤，只暴露本 Agent 需要的工具 =====
        List<String> allowedToolNames = getAgentCard().getToolNames();
        log.info("[LLM-DIAG] Node2-MultiToolFilter: AgentCard.toolNames={}, totalRegistryTools={}",
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
        log.info("[LLM-DIAG] Node2-MultiToolFilter: filteredTools={}, skippedTools={}",
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

    private record ReactResult(String finalAnswer, List<SearchResult> searchResults, List<SearchDocument> documents) {}

    /**
     * 工具执行结果入口：同时持有 JSON 字符串（给 LLM 的 tool message）和解析后的 Map（给 buildFinalResult 直接使用）。
     * 消除原先 toToolResultJson → unwrapToolResult 的双层序列化/反序列化开销。
     */
    private record ToolResultEntry(String toolName, String jsonForLlm, Map<String, Object> parsedContent) {}
}