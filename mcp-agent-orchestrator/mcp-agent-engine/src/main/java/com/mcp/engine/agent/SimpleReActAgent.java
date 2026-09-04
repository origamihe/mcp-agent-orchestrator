package com.mcp.engine.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.engine.agent.card.AgentCard;
import com.mcp.engine.policy.PolicyEngine;
import com.mcp.llm.client.ChatMessage;
import com.mcp.llm.client.LlmClient;
import com.mcp.llm.client.LlmToolResponse;
import com.mcp.tools.executor.ToolExecutor;
import com.mcp.tools.model.ToolDefinition;
import com.mcp.tools.model.ToolExecutionRequest;
import com.mcp.tools.model.ToolExecutionResult;
import com.mcp.tools.model.ToolResult;
import com.mcp.tools.registry.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

@Slf4j
@Component
@SuppressWarnings({"unchecked"})
public class SimpleReActAgent implements Agent {

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final PolicyEngine policyEngine;

    public SimpleReActAgent(LlmClient llmClient,
                            ToolRegistry toolRegistry,
                            ToolExecutor toolExecutor,
                            PolicyEngine policyEngine) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.policyEngine = policyEngine;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ThreadLocal<ExecutionTracker> currentTracker = new ThreadLocal<>();
    private volatile LLMRequest currentRequest;

    private static final int DEFAULT_MAX_TOOL_ROUNDS = 5;

    @Override
    public String getId() {
        return "simple-react-agent";
    }

    @Override
    public String getName() {
        return "SimpleReActAgent";
    }

    @Override
    public AgentCard getAgentCard() {
        return AgentCard.builder()
                .agentId(getId())
                .agentName(getName())
                .agentType(AgentCard.AgentType.EXECUTOR)
                .description("通用 ReAct Agent — 支持工具调用、多轮推理、联网搜索、文件操作、文档处理")
                .skills(List.of(
                        "web-search", "information-retrieval", "data-aggregation", "fact-checking",
                        "code-generation", "code-review", "code-analysis",
                        "file-reading", "file-writing", "code-editing",
                        "document-reading", "document-search",
                        "chat", "qa", "roleplay", "translation", "summarization"
                ))
                .toolNames(List.of(
                        "multi_search", "fetch_webpage", "web_search", "deep_research",
                        "read_file", "write_file", "edit_file",
                        "search_file", "search_content",
                        "read_document_meta", "read_document_range",
                        "read_conversation_history", "read_conversation_summary"
                ))
                .version("1.0.0")
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
        ExecutionTracker tracker = request.getVariables() != null
                ? (ExecutionTracker) request.getVariables().get("executionTracker")
                : null;
        if (tracker == null) {
            tracker = new ExecutionTracker();
        }
        currentTracker.set(tracker);

        String systemPrompt = request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()
                ? request.getSystemPrompt()
                : buildDefaultToolSystemPrompt();
        String toolInstructions = buildToolInstructions();
        String sessionHint = request.getSessionId() != null
                ? "\n\n【当前会话ID】" + request.getSessionId()
                  + "\n调用 read_conversation_history 或 read_conversation_summary 时，请使用此 sessionId。"
                : "";
        String fullSystemPrompt = systemPrompt + "\n\n" + toolInstructions + sessionHint;

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder().role("system").content(fullSystemPrompt).build());
        messages.add(ChatMessage.builder().role("user").content(request.getUserMessage()).build());

        return reactLoop(messages, 0)
                .doFinally(signal -> currentTracker.remove());
    }

    private String buildDefaultToolSystemPrompt() {
        return "你是一个专业、友好的智能助手。\n\n" + buildToolInstructions();
    }

    private String buildToolInstructions() {
        return """
                【工具调用规则 - 必须严格遵守】
                1. 如果用户要求最新信息、文件操作、联网检索，优先使用工具。
                2. 如果信息不足以直接回答，先调用最小必要工具。
                3. 如果一次工具结果不够，再继续补充调用。
                4. 对于多个可并行工具调用（如同时搜索多个来源、同时读取多个文件），应一次性发起多个 tool_calls。
                5. 对于问候、闲聊、常识问答、观点交流等不需要外部操作的对话，直接回答，不要调用工具。
                6. 如果不确定是否需要调用工具，直接回答即可。
                7. 每次回答时，直接给出最终答案，不要展示"第一步"、"第二步"等思考过程。

                【文件路径规则 - 极其重要】
                8. 调用 read_file 或 write_file 时，必须使用用户提供的完整绝对路径。
                   例如用户说"读取 C:\\Users\\xxx\\Desktop\\数据标注 的文件，分析 bolt.txt"，
                   你应调用 read_file(path="C:\\Users\\xxx\\Desktop\\数据标注\\bolt.txt")，
                   而不是 read_file(path="bolt.txt")。
                9. 如果用户提到了目录和文件名，请将它们拼接成完整的绝对路径后再调用工具。

                【文档文件规则 - 重要】
                10. 对于 .docx（Word文档）和 .pdf（PDF文档）文件，不要使用 read_file 读取，
                    应使用 read_document_meta 获取文档元信息，或使用 read_document_range 读取指定页面。
                    例如：read_document_meta(path="C:\\Users\\xxx\\Desktop\\报告.docx")。
                11. 对于 .txt、.md、.java 等纯文本文件，使用 read_file 正常读取。

                【对话历史回放规则 - 极其重要】
                12. 当用户要求"复述聊天记录"、"列出我说过的话"、"回顾对话"、"把聊天内容列出来"、
                    "还记得我说了什么吗"、"总结刚才聊了什么"等需要回顾历史对话的请求时，
                    必须调用 read_conversation_history 或 read_conversation_summary 工具获取真实数据，
                    不要凭记忆猜测或编造。
                13. 如果用户要求逐条列出所有发言，调用 read_conversation_history(role="user")，
                    只获取用户发言。
                14. 如果用户问"我们聊了什么"或"总结对话"，调用 read_conversation_summary 获取摘要。
                15. 如果历史记录为空或工具返回空结果，必须明确告知用户"当前会话没有保存的历史记录"，
                    不要假装记得。
                """;
    }

    private Mono<String> reactLoop(List<ChatMessage> messages, int round) {
        return reactLoop(messages, round, new LinkedHashSet<>());
    }

    private Mono<String> reactLoop(List<ChatMessage> messages, int round, Set<String> recentCalls) {
        int maxRounds = DEFAULT_MAX_TOOL_ROUNDS;
        if (round >= maxRounds) {
            log.info("[ReAct] Max rounds ({}) reached, forcing final answer", maxRounds);
            return llmClient.chatWithTools(messages, buildToolDefinitions())
                    .map(resp -> {
                        String c = resp.getContent();
                        return (c != null && !c.isBlank()) ? c : "已达最大推理轮次，无法完成分析。";
                    });
        }

        return llmClient.chatWithTools(messages, buildToolDefinitions())
                .flatMap(response -> {
                    if (response.hasToolCalls()) {
                        List<LlmToolResponse.ToolCall> calls = response.getToolCalls();
                        log.info("[ReAct Round {}] model requested {} tool(s): {}",
                                round, calls.size(),
                                calls.stream().map(LlmToolResponse.ToolCall::getName).toList());

                        List<Mono<AbstractMap.SimpleEntry<LlmToolResponse.ToolCall, String>>> tasks = calls.stream()
                                .map(tc -> executeSingleTool(tc).map(result -> {
                                    String resultStr = toToolResultJson(tc, result);
                                    return new AbstractMap.SimpleEntry<>(tc, resultStr);
                                }))
                                .toList();

                        return Flux.merge(tasks)
                                .collectList()
                                .flatMap(results -> {
                                    List<ChatMessage> updatedMessages = new ArrayList<>(messages);
                                    List<Map<String, Object>> assistantToolCalls = new ArrayList<>();
                                    List<ChatMessage> toolMessages = new ArrayList<>();
                                    for (var entry : results) {
                                        LlmToolResponse.ToolCall tc = entry.getKey();
                                        String resultStr = entry.getValue();
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
                                                .content(resultStr)
                                                .build());
                                        log.info("[ReAct Round {}] tool {} result: {}", round, tc.getName(),
                                                resultStr.length() > 200 ? resultStr.substring(0, 200) + "..." : resultStr);
                                    }
                                    updatedMessages.add(ChatMessage.builder()
                                            .role("assistant")
                                            .content("")
                                            .toolCalls(assistantToolCalls)
                                            .build());
                                    updatedMessages.addAll(toolMessages);

                                    Set<String> updatedCalls = new LinkedHashSet<>(recentCalls);
                                    for (var entry : results) {
                                        LlmToolResponse.ToolCall tc = entry.getKey();
                                        String callKey = tc.getName() + ":" + canonicalArgs(tc.getArguments());
                                        if (updatedCalls.contains(callKey)) {
                                            log.warn("[ReAct] Duplicate tool call detected: {}, breaking loop", callKey);
                                            return Mono.just("检测到重复工具调用，已停止：" + tc.getName() + "(" + tc.getArguments() + ")");
                                        }
                                        updatedCalls.add(callKey);
                                    }

                                    return reactLoop(updatedMessages, round + 1, updatedCalls);
                                });
                    }
                    String finalContent = response.getContent();
                    if (finalContent == null || finalContent.isBlank()) {
                        log.warn("[ReAct Round {}] LLM returned empty content, using fallback", round);
                        finalContent = buildSmartFallback(messages, round);
                    }
                    return Mono.just(finalContent);
                });
    }

    private Mono<ToolExecutionResult> executeSingleTool(LlmToolResponse.ToolCall toolCall) {
        long startTime = System.currentTimeMillis();
        ToolExecutionRequest request = new ToolExecutionRequest();
        request.setToolName(toolCall.getName());
        request.setArguments(new HashMap<>(toolCall.getArguments()));
        request.setRequestId(toolCall.getId());

        return toolRegistry.getTool(toolCall.getName())
                .map(toolDef -> {
                    PolicyEngine.PolicyDecision decision =
                            policyEngine.evaluate(null, getId(), toolDef, null,
                                    currentRequest != null ? currentRequest.getExecutionPlan() : null);
                    return decision;
                })
                .defaultIfEmpty(PolicyEngine.PolicyDecision.ALLOW)
                .flatMap(decision -> {
                    if (decision == PolicyEngine.PolicyDecision.DENY) {
                        log.warn("[SimpleReActAgent] PolicyEngine DENY: tool={}", toolCall.getName());
                        return Mono.just(ToolExecutionResult.denied(
                                toolCall.getId(), toolCall.getName(), "PolicyEngine denied tool execution"));
                    }
                    return toolExecutor.execute(request)
                            .doOnSuccess(result -> recordObservation(toolCall, result, startTime))
                            .doOnError(error -> recordObservationError(toolCall, error, startTime));
                });
    }

    private void recordObservation(LlmToolResponse.ToolCall toolCall, ToolExecutionResult result, long startTime) {
        ExecutionTracker tracker = currentTracker.get();
        if (tracker == null) return;
        long duration = System.currentTimeMillis() - startTime;
        boolean success = !isToolFailure(result != null ? result.toString() : "");
        String summary = result != null ? truncateResult(result.toString()) : "";
        String error = success ? null : extractError(result);
        String args = canonicalArgs(toolCall.getArguments());
        tracker.recordToolCall(toolCall.getName(), args, success, summary, error, duration, toolCall.getId());
    }

    private void recordObservationError(LlmToolResponse.ToolCall toolCall, Throwable error, long startTime) {
        ExecutionTracker tracker = currentTracker.get();
        if (tracker == null) return;
        long duration = System.currentTimeMillis() - startTime;
        String args = canonicalArgs(toolCall.getArguments());
        tracker.recordToolCall(toolCall.getName(), args, false, "",
                error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName(), duration,
                toolCall.getId());
    }

    private String truncateResult(String result) {
        if (result == null) return "";
        return result.length() <= 300 ? result : result.substring(0, 297) + "...";
    }

    private String extractError(Object result) {
        if (result == null) return null;
        String str = result.toString();
        if (str.contains("\"error\":\"")) {
            int start = str.indexOf("\"error\":\"") + 9;
            int end = str.indexOf("\"", start);
            if (end > start) {
                return str.substring(start, end);
            }
        }
        return null;
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

    private String buildSmartFallback(List<ChatMessage> messages, int round) {
        boolean hadToolFailure = false;
        boolean hadSearchFailure = false;
        String lastToolError = null;

        for (ChatMessage msg : messages) {
            if ("tool".equals(msg.getRole())) {
                String content = msg.getContent();
                if (content != null) {
                    if (content.contains("\"status\":\"FAILURE\"") || content.contains("\"status\":\"PARTIAL_SUCCESS\"")) {
                        hadSearchFailure = true;
                    }
                    if (content.contains("\"ok\":false") || content.contains("搜索失败") || content.contains("FAILURE")) {
                        hadToolFailure = true;
                        if (content.contains("\"error\":")) {
                            int start = content.indexOf("\"error\":\"");
                            if (start >= 0) {
                                int end = content.indexOf("\"", start + 10);
                                if (end > start) {
                                    lastToolError = content.substring(start + 10, end);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (hadSearchFailure) {
            return "抱歉，搜索服务暂时不可用，网络连接可能存在问题。" +
                   "你可以稍后再试，或者尝试换一个更具体的关键词重新搜索。";
        }
        if (hadToolFailure) {
            String detail = lastToolError != null ? "（" + lastToolError + "）" : "";
            return "抱歉，工具执行遇到了问题" + detail + "。请稍后再试。";
        }
        return "抱歉，我暂时无法回答这个问题，请稍后再试。";
    }

    private List<Map<String, Object>> buildToolDefinitions() {
        List<ToolDefinition> allTools = toolRegistry.getAllTools();
        List<String> allowedToolNames = getAgentCard().getToolNames();

        List<ToolDefinition> filteredTools = new ArrayList<>();
        for (ToolDefinition td : allTools) {
            if (allowedToolNames.contains(td.getName())) {
                filteredTools.add(td);
            }
        }
        log.info("[MultiTool] SimpleReActAgent: AgentCard.toolNames={}, registry={} tools, filtered={} tools",
                allowedToolNames.size(), allTools.size(), filteredTools.size());

        if (filteredTools.isEmpty()) {
            log.warn("[MultiTool] SimpleReActAgent: No authorized tools available for agent '{}'. "
                    + "AgentCard.toolNames={} did not match any tool in registry ({} tools). "
                    + "Agent will run without tools.",
                    getId(), allowedToolNames, allTools.size());
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
                log.warn("Failed to parse inputSchema for tool {}: {}", td.getName(), e.getMessage());
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
}