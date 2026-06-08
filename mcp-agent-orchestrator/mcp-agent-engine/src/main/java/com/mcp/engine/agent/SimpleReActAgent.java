package com.mcp.engine.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.llm.client.LlmClient;
import com.mcp.tools.executor.ToolExecutor;
import com.mcp.tools.model.ToolDefinition;
import com.mcp.tools.model.ToolExecutionRequest;
import com.mcp.tools.registry.ToolRegistry;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

@Slf4j
@Component
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

    private static final Duration OLLAMA_TIMEOUT = Duration.ofSeconds(120);

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
        String systemPrompt = (context != null && context.getSystemPrompt() != null && !context.getSystemPrompt().isEmpty())
                ? context.getSystemPrompt()
                : buildDefaultToolSystemPrompt();
        return executeWithSystemPrompt(task, systemPrompt);
    }

    private String buildDefaultToolSystemPrompt() {
        return """
                你是一个专业、友好的智能助手。

                【工具调用规则 - 必须严格遵守】
                1. 对于问候、闲聊、常识问答、观点交流等不需要外部操作的对话，你必须直接回答，绝对不要调用任何工具。
                2. 只有在用户明确要求以下操作时，才可以调用工具：
                   - 读取或写入本地文件（用户明确提到了文件路径）
                   - 搜索网络获取最新信息（用户明确要求搜索或查询实时数据）
                3. 如果你不确定是否需要调用工具，就不要调用工具，直接回答即可。
                4. 滥用工具会导致糟糕的用户体验，是严重的错误。
                5. 如果不调用工具，确保你的回答完整、有帮助。
                """;
    }

    private Mono<String> executeWithSystemPrompt(String task, String systemPrompt) {
        List<Map<String, Object>> toolDefs = buildOllamaToolDefinitions();

        return callOllamaWithTools(systemPrompt, task, toolDefs, null)
                .flatMap(response -> {
                    if (response.toolCalls != null && !response.toolCalls.isEmpty()) {
                        OllamaToolCall tc = response.toolCalls.get(0);
                        log.info("[Ollama ToolCall] model requested tool: {} with args: {}", tc.name, tc.arguments);
                        return executeToolAndContinue(systemPrompt, task, tc, toolDefs);
                    }
                    return Mono.just(response.content != null ? response.content : "无响应");
                });
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
                        required.add(entry.getKey());
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

        return WebClient.builder()
                .baseUrl(ollamaBaseUrl)
                .build()
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
                Map<String, Object> arguments = (Map<String, Object>) function.get("arguments");
                toolCalls.add(new OllamaToolCall(name, arguments != null ? arguments : Map.of()));
            }
        }
        return new OllamaChatResponse(content, toolCalls);
    }

    private Mono<String> executeToolAndContinue(
            String systemPrompt, String task,
            OllamaToolCall toolCall, List<Map<String, Object>> toolDefs) {

        ToolExecutionRequest request = new ToolExecutionRequest();
        request.setToolName(toolCall.name);
        request.setArguments(new HashMap<>(toolCall.arguments));

        return toolExecutor.execute(request)
                .flatMap(toolResult -> {
                    String toolResultStr = toolResult != null ? toolResult.toString() : "空结果";
                    log.info("[Ollama ToolCall] tool {} result: {}", toolCall.name,
                            toolResultStr.length() > 200 ? toolResultStr.substring(0, 200) + "..." : toolResultStr);

                    List<Map<String, Object>> messages = new ArrayList<>();
                    messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", task));
                    messages.add(Map.of(
                            "role", "assistant", "content", "",
                            "tool_calls", List.of(Map.of(
                                    "function", Map.of(
                                            "name", toolCall.name,
                                            "arguments", toolCall.arguments
                                    )
                            ))
                    ));
                    messages.add(Map.of("role", "tool", "content", toolResultStr));

                    return callOllamaWithTools(null, null, toolDefs, messages)
                            .map(resp -> resp.content != null ? resp.content : "工具执行完成但未获取到最终回答");
                })
                .defaultIfEmpty("工具执行失败，未获取到结果。");
    }

    private record OllamaChatResponse(String content, List<OllamaToolCall> toolCalls) {}
    private record OllamaToolCall(String name, Map<String, Object> arguments) {}
}