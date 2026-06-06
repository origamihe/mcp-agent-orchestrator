package com.mcp.engine.agent;

import com.mcp.llm.client.LlmClient;
import com.mcp.tools.executor.ToolExecutor;
import com.mcp.tools.model.ToolDefinition;
import com.mcp.tools.model.ToolExecutionRequest;
import com.mcp.tools.registry.ToolRegistry;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@Slf4j
@Component
public class SimpleReActAgent implements Agent {

    @Setter
    private LlmClient llmClient;
    @Setter
    private ToolRegistry toolRegistry;
    @Setter
    private ToolExecutor toolExecutor;

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
        String toolsPrompt = buildToolsPrompt();
        String systemPrompt = buildReActSystemPrompt(toolsPrompt);

        return llmClient.generateWithSystemPrompt(systemPrompt, "任务: " + task)
                .flatMap(response -> {
                    if (response.contains("TOOL_CALL:")) {
                        return executeToolAndContinue(task, response, systemPrompt);
                    }
                    return Mono.just(response);
                });
    }

    @Override
    public Mono<String> executeWithContext(String task, AgentContext context) {
        return execute(task);
    }

    private String buildToolsPrompt() {
        var tools = toolRegistry.getAllTools();
        if (tools.isEmpty()) {
            return "（当前没有可用工具）";
        }
        return tools.stream()
                .map(t -> String.format("- %s: %s (参数: %s)", t.getName(), t.getDescription(), t.getInputSchema()))
                .collect(Collectors.joining("\n"));
    }

    private String buildReActSystemPrompt(String toolsPrompt) {
        return """
                你是一个智能助手，可以调用工具来完成任务。

                可用工具：
                %s

                当需要使用工具时，请按以下格式输出：
                TOOL_CALL:
                工具名称: <工具名>
                参数: {"<参数名>": "<参数值>"}

                注意：<参数名> 必须使用上面可用工具中列出的实际参数名（如 path），不要使用 "参数名" 这几个字。
                示例：如果调用 read_file 工具读取 /tmp/test.txt，应输出：
                参数: {"path": "/tmp/test.txt"}

                收到工具结果后，请基于结果给出最终回答。
                如果不需要工具，直接给出回答即可。
                """.formatted(toolsPrompt);
    }

    private Mono<String> executeToolAndContinue(String task, String response, String systemPrompt) {
        String toolName = extractValue(response, "工具名称:");
        String argsJson = extractValue(response, "参数:");

        if (toolName == null) {
            return Mono.just(response);
        }

        ToolExecutionRequest request = new ToolExecutionRequest();
        request.setToolName(toolName.trim());
        request.setArguments(parseSimpleArgs(argsJson));

        return toolExecutor.execute(request)
                .map(result -> "工具 [" + toolName + "] 返回结果:\n" + result)
                .flatMap(toolResult ->
                        llmClient.generateWithSystemPrompt(
                                systemPrompt + "\n\n原始任务: " + task + "\n工具执行结果: " + toolResult,
                                "请基于工具结果给出最终回答"
                        )
                )
                .defaultIfEmpty("无法获取工具结果。");
    }

    private String extractValue(String response, String key) {
        for (String line : response.lines().toList()) {
            if (line.trim().startsWith(key)) {
                return line.substring(line.indexOf(key) + key.length()).trim();
            }
        }
        return null;
    }

    private java.util.Map<String, Object> parseSimpleArgs(String json) {
        java.util.Map<String, Object> args = new java.util.HashMap<>();
        if (json != null && !json.isBlank()) {
            json = json.trim().replaceAll("[{}]", "");
            for (String pair : json.split(",")) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replaceAll("\"", "");
                    String value = kv[1].trim().replaceAll("\"", "");
                    args.put(key, value);
                }
            }
        }
        return args;
    }
}