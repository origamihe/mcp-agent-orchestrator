package com.mcp.engine.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.llm.client.LlmClient;
import com.mcp.tools.executor.ToolExecutor;
import com.mcp.tools.model.ToolDefinition;
import com.mcp.tools.model.ToolExecutionRequest;
import com.mcp.tools.model.ToolResult;
import com.mcp.tools.registry.ToolRegistry;
import jakarta.annotation.PostConstruct;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.*;

@Slf4j
@Component
@SuppressWarnings("unchecked")
public class SimpleReActAgent implements Agent {

    @Setter
    private LlmClient llmClient;
    @Setter
    private ToolRegistry toolRegistry;
    @Setter
    private ToolExecutor toolExecutor;

    @Value("${spring.ai.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.options.model}")
    private String modelName;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ThreadLocal<ExecutionTracker> currentTracker = new ThreadLocal<>();

    private static final Duration OLLAMA_TIMEOUT = Duration.ofSeconds(120);

    private WebClient ollamaWebClient;

    @PostConstruct
    public void initWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(OLLAMA_TIMEOUT);
        this.ollamaWebClient = WebClient.builder()
                .baseUrl(ollamaBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        log.info("[SimpleReActAgent] WebClient initialized with baseUrl: {}", ollamaBaseUrl);
    }

    @Override
    public String getId() {
        return "simple-react-agent";
    }

    @Override
    public String getName() {
        return "SimpleReActAgent";
    }

    @Override
    public Mono<String> execute(String task) {
        return executeWithSystemPrompt(task, buildDefaultToolSystemPrompt());
    }

    @Override
    public Mono<String> executeWithContext(String task, AgentContext context) {
        ExecutionTracker tracker = context != null ? context.getExecutionTracker() : null;
        if (tracker == null) {
            tracker = new ExecutionTracker();
        }
        currentTracker.set(tracker);

        String customPrompt = (context != null && context.getSystemPrompt() != null && !context.getSystemPrompt().isEmpty())
                ? context.getSystemPrompt()
                : null;
        String toolInstructions = buildToolInstructions();
        String sessionHint = (context != null && context.getSessionId() != null)
                ? "\n\n【当前会话ID】" + context.getSessionId() + "\n调用 read_conversation_history 或 read_conversation_summary 时，请使用此 sessionId。"
                : "";
        String systemPrompt = (customPrompt != null)
                ? customPrompt + "\n\n" + toolInstructions + sessionHint
                : "你是一个专业、友好的智能助手。\n\n" + toolInstructions + sessionHint;
        return executeWithSystemPrompt(task, systemPrompt)
                .doFinally(signal -> currentTracker.remove());
    }

    private static final int DEFAULT_MAX_TOOL_ROUNDS = 5;

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

    private Mono<String> executeWithSystemPrompt(String task, String systemPrompt) {
        List<Map<String, Object>> toolDefs = buildOllamaToolDefinitions();
        List<Map<String, Object>> conversationHistory = new ArrayList<>();
        Map<String, Object> sysMsg = new LinkedHashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        conversationHistory.add(sysMsg);
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", task);
        conversationHistory.add(userMsg);

        return reactLoop(conversationHistory, toolDefs, 0);
    }

    private Mono<String> reactLoop(List<Map<String, Object>> messages,
                                    List<Map<String, Object>> toolDefs, int round) {
        return reactLoop(messages, toolDefs, round, new LinkedHashSet<>());
    }

    private Mono<String> reactLoop(List<Map<String, Object>> messages,
                                    List<Map<String, Object>> toolDefs, int round,
                                    Set<String> recentCalls) {
        int maxRounds = DEFAULT_MAX_TOOL_ROUNDS;
        if (round >= maxRounds) {
            log.info("[ReAct] Max rounds ({}) reached, forcing final answer", maxRounds);
            return callOllamaWithTools(null, null, toolDefs, messages)
                    .map(resp -> {
                        String c = resp.content;
                        return (c != null && !c.isBlank()) ? c : "已达最大推理轮次，无法完成分析。";
                    });
        }

        return callOllamaWithTools(null, null, toolDefs, messages)
                .flatMap(response -> {
                    if (response.toolCalls != null && !response.toolCalls.isEmpty()) {
                        List<OllamaToolCall> calls = response.toolCalls;
                        log.info("[ReAct Round {}] model requested {} tool(s): {}",
                                round, calls.size(),
                                calls.stream().map(tc -> tc.name).toList());

                        List<Mono<AbstractMap.SimpleEntry<OllamaToolCall, String>>> tasks = calls.stream()
                                .map(tc -> executeSingleTool(tc).map(result -> {
                                    String resultStr = toToolResultJson(tc, result);
                                    return new AbstractMap.SimpleEntry<>(tc, resultStr);
                                }))
                                .toList();

                        return Flux.merge(tasks)
                                .collectList()
                                .flatMap(results -> {
                                    List<Map<String, Object>> updatedMessages = new ArrayList<>(messages);
                                    List<Map<String, Object>> assistantToolCalls = new ArrayList<>();
                                    for (var entry : results) {
                                        OllamaToolCall tc = entry.getKey();
                                        String resultStr = entry.getValue();
                                        assistantToolCalls.add(Map.of(
                                                "id", UUID.randomUUID().toString().substring(0, 8),
                                                "type", "function",
                                                "function", Map.of(
                                                        "name", tc.name,
                                                        "arguments", tc.arguments
                                                )
                                        ));
                                        updatedMessages.add(Map.of(
                                                "role", "tool",
                                                "tool_call_id", assistantToolCalls.get(assistantToolCalls.size() - 1).get("id"),
                                                "name", tc.name,
                                                "content", resultStr
                                        ));
                                        log.info("[ReAct Round {}] tool {} result: {}", round, tc.name,
                                                resultStr.length() > 200 ? resultStr.substring(0, 200) + "..." : resultStr);
                                    }
                                    updatedMessages.add(Map.of(
                                            "role", "assistant", "content", "",
                                            "tool_calls", assistantToolCalls
                                    ));

                                    Set<String> updatedCalls = new LinkedHashSet<>(recentCalls);
                                    for (var entry : results) {
                                        OllamaToolCall tc = entry.getKey();
                                        String callKey = tc.name + ":" + canonicalArgs(tc.arguments);
                                        if (updatedCalls.contains(callKey)) {
                                            log.warn("[ReAct] Duplicate tool call detected: {}, breaking loop", callKey);
                                            return Mono.just("检测到重复工具调用，已停止：" + tc.name + "(" + tc.arguments + ")");
                                        }
                                        updatedCalls.add(callKey);
                                    }

                                    return reactLoop(updatedMessages, toolDefs, round + 1, updatedCalls);
                                });
                    }
                    String finalContent = response.content;
                    if (finalContent == null || finalContent.isBlank()) {
                        log.warn("[ReAct Round {}] LLM returned empty content, using fallback", round);
                        finalContent = buildSmartFallback(messages, round);
                    }
                    return Mono.just(finalContent);
                });
    }

    private Mono<Object> executeSingleTool(OllamaToolCall toolCall) {
        long startTime = System.currentTimeMillis();
        ToolExecutionRequest request = new ToolExecutionRequest();
        request.setToolName(toolCall.name);
        request.setArguments(new HashMap<>(toolCall.arguments));
        return toolExecutor.execute(request).defaultIfEmpty("工具执行失败，未获取到结果。")
                .doOnSuccess(result -> recordObservation(toolCall, result, startTime))
                .doOnError(error -> recordObservationError(toolCall, error, startTime));
    }

    private void recordObservation(OllamaToolCall toolCall, Object result, long startTime) {
        ExecutionTracker tracker = currentTracker.get();
        if (tracker == null) return;
        long duration = System.currentTimeMillis() - startTime;
        boolean success = !isToolFailure(result != null ? result.toString() : "");
        String summary = result != null ? truncateResult(result.toString()) : "";
        String error = success ? null : extractError(result);
        String args = canonicalArgs(toolCall.arguments);
        tracker.recordToolCall(toolCall.name, args, success, summary, error, duration);
    }

    private void recordObservationError(OllamaToolCall toolCall, Throwable error, long startTime) {
        ExecutionTracker tracker = currentTracker.get();
        if (tracker == null) return;
        long duration = System.currentTimeMillis() - startTime;
        String args = canonicalArgs(toolCall.arguments);
        tracker.recordToolCall(toolCall.name, args, false, "",
                error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName(), duration);
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

    private String toToolResultJson(OllamaToolCall toolCall, Object toolResult) {
        if (toolResult instanceof ToolResult tr) {
            return tr.toJson();
        }
        try {
            String content = toolResult != null ? toolResult.toString() : "空结果";
            boolean success = !isToolFailure(content);
            Map<String, Object> structured = new LinkedHashMap<>();
            structured.put("success", success);
            structured.put("tool", toolCall.name);
            structured.put("content", content);
            return objectMapper.writeValueAsString(structured);
        } catch (Exception e) {
            return "{\"success\":false,\"tool\":\"" + toolCall.name + "\",\"error\":\"serialization failed\"}";
        }
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

    private String buildSmartFallback(List<Map<String, Object>> messages, int round) {
        boolean hadToolFailure = false;
        boolean hadSearchFailure = false;
        String lastToolError = null;

        for (Map<String, Object> msg : messages) {
            if ("tool".equals(msg.get("role"))) {
                String content = (String) msg.get("content");
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

    private List<Map<String, Object>> buildOllamaToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolDefinition td : toolRegistry.getAllTools()) {
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

    private Mono<OllamaChatResponse> callOllamaWithTools(
            String systemPrompt, String userPrompt,
            List<Map<String, Object>> tools,
            List<Map<String, Object>> previousMessages) {

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("stream", false);
        requestBody.put("options", Map.of("temperature", 0.1, "num_predict", 2048));

        List<Map<String, Object>> messages = new ArrayList<>();
        if (previousMessages != null) {
            messages.addAll(previousMessages);
        } else {
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userPrompt));
        }
        requestBody.put("messages", messages);
        requestBody.put("tools", tools);

        return ollamaWebClient
                .post()
                .uri("/api/chat")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(OLLAMA_TIMEOUT)
                .map(this::parseOllamaResponse);
    }

    @SuppressWarnings("unchecked")
    private OllamaChatResponse parseOllamaResponse(Map<String, Object> raw) {
        Map<String, Object> message = (Map<String, Object>) raw.get("message");
        String content = (String) message.get("content");
        List<Map<String, Object>> rawToolCalls = (List<Map<String, Object>>) message.get("tool_calls");

        List<OllamaToolCall> toolCalls = new ArrayList<>();
        if (rawToolCalls != null) {
            for (Map<String, Object> tc : rawToolCalls) {
                Map<String, Object> function = (Map<String, Object>) tc.get("function");
                String name = (String) function.get("name");
                Map<String, Object> arguments = parseArguments(function.get("arguments"));
                toolCalls.add(new OllamaToolCall(name, arguments));
            }
        }
        return new OllamaChatResponse(content, toolCalls);
    }

    private Map<String, Object> parseArguments(Object argsObj) {
        if (argsObj instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (argsObj instanceof String str && !str.isBlank()) {
            try {
                return objectMapper.readValue(str, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse arguments as JSON string: {}", str);
            }
        }
        return Map.of();
    }

    private Mono<String> executeToolAndContinue(
            String systemPrompt, String task,
            OllamaToolCall toolCall, List<Map<String, Object>> toolDefs) {

        ToolExecutionRequest request = new ToolExecutionRequest();
        request.setToolName(toolCall.name);
        request.setArguments(new HashMap<>(toolCall.arguments));

        return toolExecutor.execute(request)
                .flatMap(toolResult -> {
                    String toolResultStr = toToolResultJson(toolCall, toolResult);
                    log.info("[Ollama ToolCall] tool {} result: {}", toolCall.name,
                            toolResultStr.length() > 200 ? toolResultStr.substring(0, 200) + "..." : toolResultStr);

                    String callId = UUID.randomUUID().toString().substring(0, 8);
                    List<Map<String, Object>> messages = new ArrayList<>();
                    messages.add(Map.of("role", "system", "content", systemPrompt));
                    messages.add(Map.of("role", "user", "content", task));
                    messages.add(Map.of(
                            "role", "assistant", "content", "",
                            "tool_calls", List.of(Map.of(
                                    "id", callId,
                                    "type", "function",
                                    "function", Map.of(
                                            "name", toolCall.name,
                                            "arguments", toolCall.arguments
                                    )
                            ))
                    ));
                    messages.add(Map.of(
                            "role", "tool",
                            "tool_call_id", callId,
                            "name", toolCall.name,
                            "content", toolResultStr
                    ));

                    return callOllamaWithTools(null, null, toolDefs, messages)
                            .map(resp -> resp.content != null ? resp.content : "工具执行完成但未获取到最终回答");
                })
                .defaultIfEmpty("工具执行失败，未获取到结果。");
    }

    private record OllamaChatResponse(String content, List<OllamaToolCall> toolCalls) {}
    private record OllamaToolCall(String name, Map<String, Object> arguments) {}
}