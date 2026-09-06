package com.mcp.llm.provider;

import com.mcp.core.domain.chat.CoreChatMessage;
import com.mcp.core.domain.llm.LlmModelConfig;
import com.mcp.core.service.LlmConfigService;
import com.mcp.core.service.PromptService;
import com.mcp.llm.client.ChatMessage;
import com.mcp.llm.client.LlmClient;
import com.mcp.llm.client.LlmToolResponse;
import com.mcp.llm.context.ProviderContext;
import com.mcp.llm.factory.ProviderRegistry;
import com.mcp.llm.metrics.LlmMetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * 多 Provider LLM 客户端实现（支持 Ollama / Google AI Studio / OpenRouter / DeepSeek / Claude）
 * 通过 ProviderContext 与具体 Provider 解耦
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringAiLlmClient implements LlmClient {

    private final LlmConfigService llmConfigService;
    private final PromptService promptService;
    private final ProviderRegistry providerRegistry;
    private final LlmMetricsCollector metricsCollector;

    @Override
    public Mono<String> generate(String prompt) {
        return promptService.getCoreSystemPrompt()
                .flatMap(systemPrompt ->
                        llmConfigService.getDefaultConfig()
                                .flatMap(config -> {
                                    ProviderContext context = providerRegistry.getContext(config);
                                    return Mono.fromCallable(() -> createChatClient(context).prompt()
                                            .system(systemPrompt)
                                            .user(prompt)
                                            .options(context.chatOptions())
                                            .call()
                                            .content())
                                            .subscribeOn(Schedulers.boundedElastic())
                                            .timeout(Duration.ofSeconds(120));
                                })
                )
                .defaultIfEmpty("No response generated.");
    }

    @Override
    public Mono<String> generateWithSystemPrompt(String systemPrompt, String userPrompt) {
        long startTime = System.currentTimeMillis();
        int sysLen = systemPrompt != null ? systemPrompt.length() : 0;
        int usrLen = userPrompt != null ? userPrompt.length() : 0;
        log.debug("[LLM] generate START | sysPromptLen={} | usrPromptLen={} | totalLen={}",
                sysLen, usrLen, sysLen + usrLen);

        return llmConfigService.getDefaultConfig()
                .flatMap(config -> {
                    log.debug("[LLM] Using model: {}, provider: {}",
                            config.getModelName(), config.getProvider());
                    ProviderContext context = providerRegistry.getContext(config);
                    return Mono.fromCallable(() -> {
                        long callStart = System.currentTimeMillis();
                        ChatResponse response = createChatClient(context).prompt()
                                .system(systemPrompt)
                                .user(userPrompt)
                                .options(context.chatOptions())
                                .call()
                                .chatResponse();
                        long callEnd = System.currentTimeMillis();
                        log.debug("[LLM] call completed in {}ms", callEnd - callStart);
                        logTokenUsage(response);
                        return response.getResult().getOutput().getText();
                    }).subscribeOn(Schedulers.boundedElastic())
                    .timeout(Duration.ofSeconds(120));
                })
                .doOnSuccess(result -> {
                    long totalElapsed = System.currentTimeMillis() - startTime;
                    metricsCollector.recordCallDuration(totalElapsed);
                    metricsCollector.recordSuccess();
                    log.debug("[LLM] generate SUCCESS | totalElapsed={}ms | responseLen={}",
                            totalElapsed, result != null ? result.length() : 0);
                })
                .doOnError(error -> {
                    long totalElapsed = System.currentTimeMillis() - startTime;
                    metricsCollector.recordCallDuration(totalElapsed);
                    metricsCollector.recordFailure();
                    log.error("[LLM] generate FAILED | totalElapsed={}ms | error={}",
                            totalElapsed, error.getMessage());
                })
                .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
                    long totalElapsed = System.currentTimeMillis() - startTime;
                    log.warn("[LLM] generate TIMED OUT after {}ms", totalElapsed);
                    return Mono.just("AI 模型响应超时（120秒），可能是当前模型推理速度较慢。请稍后重试，或缩短问题长度。");
                })
                .defaultIfEmpty("No response from AI model.");
    }

    @Override
    public Mono<String> chat(String message) {
        return generate(message);
    }

    @Override
    public Mono<String> chatWithHistory(List<ChatMessage> history, String newMessage) {
        return llmConfigService.getDefaultConfig()
                .flatMap(config -> {
                    ProviderContext context = providerRegistry.getContext(config);
                    List<Message> messages = convertToSpringAiMessages(history, newMessage);
                    return Mono.fromCallable(() -> createChatClient(context).prompt()
                            .messages(messages)
                            .options(context.chatOptions())
                            .call()
                            .content())
                            .subscribeOn(Schedulers.boundedElastic())
                            .timeout(Duration.ofSeconds(120));
                })
                .defaultIfEmpty("No response from AI model.");
    }

    @Override
    public Mono<String> chatWithCoreHistory(List<CoreChatMessage> history, String newMessage) {
        return llmConfigService.getDefaultConfig()
                .flatMap(config -> {
                    ProviderContext context = providerRegistry.getContext(config);
                    List<Message> messages = convertCoreToSpringAiMessages(history, newMessage);
                    return Mono.fromCallable(() -> createChatClient(context).prompt()
                            .messages(messages)
                            .options(context.chatOptions())
                            .call()
                            .content())
                            .subscribeOn(Schedulers.boundedElastic())
                            .timeout(Duration.ofSeconds(120));
                })
                .defaultIfEmpty("No response from AI model.");
    }

    @Override
    public Mono<String> generateWithConfig(String configId, String prompt) {
        return llmConfigService.getConfigById(configId)
                .flatMap(config -> {
                    ProviderContext context = providerRegistry.getContext(config);
                    return Mono.fromCallable(() -> createChatClient(context).prompt()
                            .user(prompt)
                            .options(context.chatOptions())
                            .call()
                            .content())
                            .subscribeOn(Schedulers.boundedElastic())
                            .timeout(Duration.ofSeconds(120));
                })
                .defaultIfEmpty("No response generated.");
    }

    @Override
    public Mono<String> generateWithConfigAndSystem(String configId, String systemPrompt, String userPrompt) {
        return llmConfigService.getConfigById(configId)
                .flatMap(config -> {
                    ProviderContext context = providerRegistry.getContext(config);
                    return Mono.fromCallable(() -> {
                        ChatResponse response = createChatClient(context).prompt()
                                .system(systemPrompt)
                                .user(userPrompt)
                                .options(context.chatOptions())
                                .call()
                                .chatResponse();
                        logTokenUsage(response);
                        return response.getResult().getOutput().getText();
                    }).subscribeOn(Schedulers.boundedElastic())
                    .timeout(Duration.ofSeconds(120));
                })
                .defaultIfEmpty("No response from AI model.");
    }

    @Override
    public Mono<LlmToolResponse> chatWithTools(List<ChatMessage> messages, List<Map<String, Object>> toolDefinitions) {
        return llmConfigService.getDefaultConfig()
                .flatMap(config -> {
                    ProviderContext context = providerRegistry.getContext(config);
                    return Mono.fromCallable(() -> {
                        List<Message> springMessages = convertChatMessages(messages);

                        // ===== 诊断节点1：发送给 LLM 前 =====
                        List<ToolCallback> toolCallbacks = List.of();
                        int toolCount = toolDefinitions != null ? toolDefinitions.size() : 0;
                        if (toolCount > 0) {
                            toolCallbacks = createToolCallbacks(toolDefinitions);
                        }
                        List<String> toolNames = toolCallbacks.stream()
                                .map(tc -> tc.getToolDefinition().name())
                                .collect(Collectors.toList());
                        String optionsType = context.chatOptions() != null
                                ? context.chatOptions().getClass().getSimpleName()
                                : "null";
                        boolean isToolCallingOptions = context.chatOptions() instanceof ToolCallingChatOptions;
                        log.debug("[LLM] Node1-BeforeLLM: tools.size()={}, toolNames={}, chatOptionsType={}, isToolCallingChatOptions={}",
                                toolCount, toolNames, optionsType, isToolCallingOptions);

                        ChatOptions effectiveOptions = context.chatOptions();
                        if (toolCount > 0 && effectiveOptions instanceof ToolCallingChatOptions toolOpts) {
                            ChatOptions cloned = toolOpts.copy();
                            if (cloned instanceof ToolCallingChatOptions clonedToolOpts) {
                                clonedToolOpts.setToolCallbacks(toolCallbacks);
                                clonedToolOpts.setInternalToolExecutionEnabled(false);
                                effectiveOptions = cloned;
                                log.debug("[LLM] Node1-Options: cloned {}, set internalToolExecutionEnabled=false, toolCallbacks={}",
                                        effectiveOptions.getClass().getSimpleName(), toolNames);
                            }
                        } else if (toolCount > 0) {
                            log.warn("[LLM] Node1-WARN: chatOptions is NOT ToolCallingChatOptions (type={}), "
                                    + "cannot disable internal tool execution. Tools may be auto-executed by Spring AI.",
                                    optionsType);
                        }

                        if (toolCount > 0) {
                            String toolText = buildToolTextDescriptions(toolDefinitions);
                            List<Message> augmentedMessages = new ArrayList<>();
                            for (Message msg : springMessages) {
                                if (msg instanceof SystemMessage) {
                                    String augmentedContent = ((SystemMessage) msg).getText() + toolText;
                                    augmentedMessages.add(new SystemMessage(augmentedContent));
                                } else {
                                    augmentedMessages.add(msg);
                                }
                            }
                            springMessages = augmentedMessages;
                            log.debug("[LLM] Node1-TextTools: appended {} tool definitions to system prompt (~{} chars)",
                                    toolCount, toolText.length());
                        }

                        ChatClient client = createChatClient(context);
                        ChatClient.ChatClientRequestSpec promptSpec = client.prompt()
                                .messages(springMessages)
                                .options(effectiveOptions);
                        if (toolCount > 0) {
                            log.debug("[LLM] Node1-Registered {} tool(s): internalExec=false + text-based tool descriptions in system prompt",
                                    toolCount);
                        } else {
                            log.warn("[LLM] Node1-WARN: toolDefinitions is EMPTY! Model will NOT know about any tools. "
                                    + "Check ToolRegistry registration and buildToolDefinitions().");
                        }
                        ChatResponse response = promptSpec.call().chatResponse();

                        return toLlmToolResponse(response, toolDefinitions);
                    }).subscribeOn(Schedulers.boundedElastic())
                    .timeout(Duration.ofSeconds(120));
                });
    }

    // ====================== 流式方法 ======================

    @Override
    public Flux<String> generateStream(String prompt) {
        return promptService.getCoreSystemPrompt()
                .flatMapMany(systemPrompt ->
                        llmConfigService.getDefaultConfig()
                                .flatMapMany(config -> {
                                    ProviderContext context = providerRegistry.getContext(config);
                                    return createChatClient(context).prompt()
                                            .system(systemPrompt)
                                            .user(prompt)
                                            .options(context.chatOptions())
                                            .stream()
                                            .chatResponse()
                                            .mapNotNull(r -> extractTextFromStreamChunk(r))
                                            .publishOn(Schedulers.boundedElastic());
                                })
                )
                .switchIfEmpty(Flux.just("No response generated."));
    }

    @Override
    public Flux<String> generateStreamWithSystemPrompt(String systemPrompt, String userPrompt) {
        long startTime = System.currentTimeMillis();
        int sysLen = systemPrompt != null ? systemPrompt.length() : 0;
        int usrLen = userPrompt != null ? userPrompt.length() : 0;
        log.debug("[LLM] generateStream START | sysPromptLen={} | usrPromptLen={}",
                sysLen, usrLen);

        return llmConfigService.getDefaultConfig()
                .flatMapMany(config -> {
                    log.debug("[LLM] Streaming with model: {}, provider: {}",
                            config.getModelName(), config.getProvider());
                    ProviderContext context = providerRegistry.getContext(config);
                    return createChatClient(context).prompt()
                            .system(systemPrompt)
                            .user(userPrompt)
                            .options(context.chatOptions())
                            .stream()
                            .chatResponse()
                            .doOnComplete(() -> {
                                long totalElapsed = System.currentTimeMillis() - startTime;
                                metricsCollector.recordCallDuration(totalElapsed);
                                metricsCollector.recordSuccess();
                                metricsCollector.recordStreamCall();
                                log.debug("[LLM] generateStream COMPLETE | totalElapsed={}ms",
                                        totalElapsed);
                            })
                            .doOnError(error -> {
                                long totalElapsed = System.currentTimeMillis() - startTime;
                                metricsCollector.recordCallDuration(totalElapsed);
                                metricsCollector.recordFailure();
                                log.error("[DIAG-LLM] generateStreamWithSystemPrompt FAILED | totalElapsed={}ms | error={}",
                                        totalElapsed, error.getMessage());
                            })
                            .mapNotNull(r -> {
                                logTokenUsage(r);
                                return extractTextFromStreamChunk(r);
                            })
                            .publishOn(Schedulers.boundedElastic());
                })
                .switchIfEmpty(Flux.just("No response from AI model."));
    }

    @Override
    public Flux<String> generateStreamWithConfigAndSystem(String configId, String systemPrompt, String userPrompt) {
        long startTime = System.currentTimeMillis();
        log.debug("[LLM] generateStreamWithConfig START | configId={} | sysPromptLen={} | usrPromptLen={}",
                configId, systemPrompt != null ? systemPrompt.length() : 0,
                userPrompt != null ? userPrompt.length() : 0);

        return llmConfigService.getConfigById(configId)
                .flatMapMany(config -> {
                    log.debug("[LLM] Streaming with config: {}, model: {}, provider: {}",
                            configId, config.getModelName(), config.getProvider());
                    ProviderContext context = providerRegistry.getContext(config);
                    return createChatClient(context).prompt()
                            .system(systemPrompt)
                            .user(userPrompt)
                            .options(context.chatOptions())
                            .stream()
                            .chatResponse()
                            .doOnComplete(() -> {
                                long totalElapsed = System.currentTimeMillis() - startTime;
                                metricsCollector.recordCallDuration(totalElapsed);
                                metricsCollector.recordSuccess();
                                metricsCollector.recordStreamCall();
                                log.debug("[LLM] generateStreamWithConfig COMPLETE | totalElapsed={}ms",
                                        totalElapsed);
                            })
                            .doOnError(error -> {
                                long totalElapsed = System.currentTimeMillis() - startTime;
                                metricsCollector.recordCallDuration(totalElapsed);
                                metricsCollector.recordFailure();
                                log.error("[DIAG-LLM] generateStreamWithConfigAndSystem FAILED | totalElapsed={}ms | error={}",
                                        totalElapsed, error.getMessage());
                            })
                            .mapNotNull(r -> {
                                logTokenUsage(r);
                                return extractTextFromStreamChunk(r);
                            })
                            .publishOn(Schedulers.boundedElastic());
                })
                .switchIfEmpty(Flux.just("No response from AI model."));
    }

    private String extractTextFromStreamChunk(ChatResponse response) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return null;
        }
        return response.getResults().get(0).getOutput().getText();
    }

    // ====================== 辅助方法 ======================

    private ChatClient createChatClient(ProviderContext context) {
        return ChatClient.builder(context.chatModel()).build();
    }

    private void logTokenUsage(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return;
        }
        try {
            var usage = response.getMetadata().getUsage();
            if (usage != null) {
                log.debug("[LLMTokens] Prompt={} | Completion={} | Total={} | Ratio={}",
                        usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens(),
                        usage.getCompletionTokens() != null && usage.getCompletionTokens() > 0
                                ? String.format("%.1f", (double) usage.getPromptTokens()
                                        / usage.getCompletionTokens())
                                : "N/A");
            } else {
                log.debug("[LLMTokens] No usage info in response metadata");
            }
        } catch (Exception e) {
            log.debug("[LLMTokens] Failed to extract token usage: {}", e.getMessage());
        }
    }

    private String buildToolTextDescriptions(List<Map<String, Object>> toolDefinitions) {
        if (toolDefinitions == null || toolDefinitions.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## Available Tools\n\n");
        sb.append("You have access to the following tools. ");
        sb.append("To use a tool, output its parameters in a JSON code block.\n\n");

        for (Map<String, Object> toolDef : toolDefinitions) {
            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) toolDef.get("function");
            if (function == null) continue;
            String name = (String) function.get("name");
            String description = (String) function.get("description");
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");

            sb.append("### ").append(name).append("\n");
            if (description != null && !description.isBlank()) {
                sb.append(description).append("\n");
            }
            if (parameters != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");
                @SuppressWarnings("unchecked")
                List<String> required = (List<String>) parameters.get("required");
                if (properties != null && !properties.isEmpty()) {
                    sb.append("Parameters:\n");
                    for (Map.Entry<String, Object> prop : properties.entrySet()) {
                        String propName = prop.getKey();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> propDef = (Map<String, Object>) prop.getValue();
                        String propType = propDef != null ? (String) propDef.get("type") : "string";
                        String propDesc = propDef != null ? (String) propDef.get("description") : "";
                        boolean isRequired = required != null && required.contains(propName);
                        sb.append("  - ").append(propName)
                                .append(" (").append(propType != null ? propType : "string").append(")");
                        if (isRequired) sb.append(" [REQUIRED]");
                        if (propDesc != null && !propDesc.isBlank()) sb.append(": ").append(propDesc);
                        sb.append("\n");
                    }
                }
            }
            sb.append("\n");
        }

        sb.append("To call a tool, output:\n```json\n{\"param1\": \"value1\"}\n```\n");
        return sb.toString();
    }

    private List<Message> convertToSpringAiMessages(List<ChatMessage> history, String newMessage) {
        List<Message> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            String role = msg.getRole().toLowerCase();
            messages.add(switch (role) {
                case "system" -> new SystemMessage(msg.getContent());
                case "assistant" -> new AssistantMessage(msg.getContent());
                case "tool" -> new ToolResponseMessage(List.of(
                        new ToolResponseMessage.ToolResponse(
                                msg.getToolCallId() != null && !msg.getToolCallId().isBlank()
                                        ? msg.getToolCallId() : "unknown",
                                msg.getName() != null && !msg.getName().isBlank()
                                        ? msg.getName() : "unknown",
                                msg.getContent() != null ? msg.getContent() : "")));
                default -> new UserMessage(msg.getContent());
            });
        }
        messages.add(new UserMessage(newMessage));
        return messages;
    }

    private List<Message> convertCoreToSpringAiMessages(List<CoreChatMessage> history, String newMessage) {
        List<Message> messages = new ArrayList<>();
        for (CoreChatMessage msg : history) {
            String roleCode = msg.getRole().getCode().toLowerCase();
            messages.add(switch (roleCode) {
                case "system" -> new SystemMessage(msg.getContent());
                case "assistant" -> new AssistantMessage(msg.getContent());
                case "tool" -> new ToolResponseMessage(List.of(
                        new ToolResponseMessage.ToolResponse(
                                "unknown",
                                "unknown",
                                msg.getContent() != null ? msg.getContent() : "")));
                default -> new UserMessage(msg.getContent());
            });
        }
        messages.add(new UserMessage(newMessage));
        return messages;
    }

    private List<Message> convertChatMessages(List<ChatMessage> chatMessages) {
        List<Message> messages = new ArrayList<>();
        for (ChatMessage cm : chatMessages) {
            String role = cm.getRole().toLowerCase();
            messages.add(switch (role) {
                case "system" -> new SystemMessage(cm.getContent() != null ? cm.getContent() : "");
                case "user" -> new UserMessage(cm.getContent() != null ? cm.getContent() : "");
                case "assistant" -> {
                    if (cm.getToolCalls() != null && !cm.getToolCalls().isEmpty()) {
                        yield new AssistantMessage(
                                cm.getContent() != null ? cm.getContent() : "",
                                Map.of(),
                                cm.getToolCalls().stream()
                                        .map(tc -> new AssistantMessage.ToolCall(
                                                (String) tc.getOrDefault("id", ""),
                                                "function",
                                                (String) ((Map<String, Object>) tc.getOrDefault("function", Map.of())).get("name"),
                                                toJsonString(((Map<String, Object>) tc.getOrDefault("function", Map.of())).get("arguments"))))
                                        .toList());
                    }
                    yield new AssistantMessage(cm.getContent() != null ? cm.getContent() : "");
                }
                case "tool" -> {
                    String toolCallId = cm.getToolCallId() != null && !cm.getToolCallId().isBlank()
                            ? cm.getToolCallId() : "unknown";
                    String name = cm.getName() != null && !cm.getName().isBlank()
                            ? cm.getName() : "unknown";
                    yield new ToolResponseMessage(List.of(
                            new ToolResponseMessage.ToolResponse(
                                    toolCallId, name, cm.getContent() != null ? cm.getContent() : "")));
                }
                default -> new UserMessage(cm.getContent() != null ? cm.getContent() : "");
            });
        }
        return messages;
    }

    private List<ToolCallback> createToolCallbacks(List<Map<String, Object>> toolDefinitions) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (Map<String, Object> def : toolDefinitions) {
            Map<String, Object> function = (Map<String, Object>) def.get("function");
            if (function == null) continue;
            String name = (String) function.get("name");
            String description = (String) function.get("description");
            Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");
            String inputSchema = toJsonString(parameters != null ? parameters : Map.of());

            ToolDefinition toolDef = ToolDefinition.builder()
                    .name(name)
                    .description(description != null ? description : "")
                    .inputSchema(inputSchema)
                    .build();

            callbacks.add(new ToolCallback() {
                @Override
                public ToolDefinition getToolDefinition() {
                    return toolDef;
                }

                @Override
                public String call(String input) {
                    throw new UnsupportedOperationException("Tool callback should not be auto-executed");
                }
            });
        }
        return callbacks;
    }

    private LlmToolResponse toLlmToolResponse(ChatResponse response,
                                                 List<Map<String, Object>> toolDefinitions) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            log.warn("[LLM-DIAG] Node3-LLMResponse: response is null or empty");
            return new LlmToolResponse("", List.of());
        }
        var output = response.getResults().get(0).getOutput();
        String content = output.getText();
        List<LlmToolResponse.ToolCall> toolCalls = new ArrayList<>();

        // ===== 诊断节点3：LLM 原始响应 =====
        log.debug("[LLM] Node3-LLMRawResponse: response.toString()={}", response.toString());
        log.debug("[LLM] Node3-LLMRawResponse: output.getText() length={}, output.getToolCalls() size={}",
                content != null ? content.length() : 0,
                output.getToolCalls() != null ? output.getToolCalls().size() : 0);

        if (output.getToolCalls() != null) {
            for (AssistantMessage.ToolCall tc : output.getToolCalls()) {
                log.debug("[LLM] Node3-LLMRawResponse: toolCall name={}, id={}, arguments={}",
                        tc.name(), tc.id(), tc.arguments());
                Map<String, Object> arguments = parseArguments(tc.arguments());
                toolCalls.add(new LlmToolResponse.ToolCall(tc.id(), tc.name(), arguments));
            }
        }

        // ===== 文本回退：Ollama 等不支持原生 tool calling 的模型 =====
        if (toolCalls.isEmpty() && content != null && !content.isBlank()
                && toolDefinitions != null && !toolDefinitions.isEmpty()) {
            List<LlmToolResponse.ToolCall> textToolCalls = tryParseTextToolCalls(content, toolDefinitions);
            if (!textToolCalls.isEmpty()) {
                toolCalls.addAll(textToolCalls);
                log.debug("[LLM] Node4-TextFallback: parsed {} tool call(s) from text content (Ollama fallback)",
                        textToolCalls.size());
            }
        }

        // ===== 诊断节点4：Tool Parser 解析结果 =====
        log.debug("[LLM] Node4-ToolParser: parsedToolCallCount={}, hasToolCalls={}, contentLength={}",
                toolCalls.size(), !toolCalls.isEmpty(),
                content != null ? content.length() : 0);
        if (toolCalls.isEmpty() && (content == null || content.isBlank())) {
            log.warn("[LLM-DIAG] Node4-WARN: No tool calls AND empty content! Model returned nothing useful.");
        } else if (toolCalls.isEmpty()) {
            log.warn("[LLM-DIAG] Node4-WARN: No tool calls parsed. Model returned text-only response ({} chars). "
                    + "This means the model did NOT attempt to call any tool.", content != null ? content.length() : 0);
        }

        return new LlmToolResponse(content, toolCalls);
    }

    /**
     * 文本回退解析：当模型不支持原生 tool calling（如部分 Ollama 模型）时，
     * 从文本输出中提取 JSON 代码块，匹配工具定义，生成 ToolCall。
     * <p>
     * 这是 Ollama 模型常见问题的解决方案：模型用文本输出"看起来像"工具调用的 JSON，
     * 但从不通过 tool_calls 字段返回。此方法在 hasToolCalls=false 时作为兜底。
     * <p>
     * 支持三种 JSON 格式：
     * 1. 直接参数格式：{"query": "xxx", "depth": "2"} — 通过 findBestToolMatch 匹配
     * 2. 嵌套调用格式：{"name": "tool_name", "arguments": {...}} — 通过 name 精确匹配 + arguments 提取
     * 3. 工具名映射格式：{"tool_name": {params}} — 单 key 匹配工具名 + value 作为参数
     */
    public static List<LlmToolResponse.ToolCall> tryParseTextToolCalls(
            String content, List<Map<String, Object>> toolDefinitions) {
        if (content == null || content.isBlank() || toolDefinitions == null || toolDefinitions.isEmpty()) {
            return List.of();
        }

        List<LlmToolResponse.ToolCall> parsed = new java.util.ArrayList<>();

        // 策略1：匹配 ```json ... ``` 代码块
        java.util.regex.Pattern jsonBlockPattern = java.util.regex.Pattern.compile(
                "```(?:json)?\\s*\\n?\\s*(\\{[^`]+\\})\\s*\\n?\\s*```",
                java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = jsonBlockPattern.matcher(content);

        java.util.Set<String> seenToolCallKeys = new java.util.HashSet<>();
        while (matcher.find()) {
            String jsonStr = matcher.group(1).trim();
            tryParseSingleJsonBlock(jsonStr, toolDefinitions, parsed, seenToolCallKeys);
        }

        // 策略2：如果代码块未匹配到，尝试从原始文本中提取裸 JSON 对象
        if (parsed.isEmpty()) {
            java.util.regex.Pattern rawJsonPattern = java.util.regex.Pattern.compile(
                    "\\{(?:[^{}]|\\{[^{}]*\\})*\\}",
                    java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher rawMatcher = rawJsonPattern.matcher(content);
            while (rawMatcher.find()) {
                String jsonStr = rawMatcher.group().trim();
                if (jsonStr.length() > 10) {
                    tryParseSingleJsonBlock(jsonStr, toolDefinitions, parsed, seenToolCallKeys);
                }
            }
        }

        // 策略3：如果仍未匹配到，检查内容是否包含工具名称，尝试构造默认工具调用
        if (parsed.isEmpty() && content.length() > 0 && content.length() < 500) {
            String bestFallbackTool = findToolByNameInText(content, toolDefinitions);
            if (bestFallbackTool != null) {
                java.util.Map<String, Object> defaultArgs = extractQueryFromText(content);
                if (defaultArgs != null) {
                    parsed.add(new LlmToolResponse.ToolCall("text-"+java.util.UUID.randomUUID().toString(), bestFallbackTool, defaultArgs));
                    log.debug("[LLM] Node4-TextFallback: forced tool call from text mention: tool={}, args={}",
                            bestFallbackTool, defaultArgs.keySet());
                }
            }
        }

        if (!parsed.isEmpty()) {
            log.debug("[LLM] Node4-TextFallback: extracted {} tool call(s) from text content", parsed.size());
        }
        return parsed;
    }

    /**
     * 尝试从单个 JSON 字符串中解析工具调用，避免重复代码。
     */
    @SuppressWarnings("unchecked")
    private static void tryParseSingleJsonBlock(String jsonStr, List<Map<String, Object>> toolDefinitions,
                                                 List<LlmToolResponse.ToolCall> parsed,
                                                 java.util.Set<String> seenKeys) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> jsonMap = mapper.readValue(jsonStr, Map.class);

            String explicitName = jsonMap.get("name") instanceof String n ? n : null;
            Object argumentsObj = jsonMap.get("arguments");

            if (explicitName != null && argumentsObj instanceof Map) {
                Map<String, Object> argsMap = (Map<String, Object>) argumentsObj;
                if (toolNameExists(explicitName, toolDefinitions)) {
                    String key = explicitName + ":" + argsMap.keySet().toString();
                    if (seenKeys.add(key)) {
                        parsed.add(new LlmToolResponse.ToolCall("text-"+java.util.UUID.randomUUID().toString(), explicitName, argsMap));
                        log.debug("[LLM] Node4-TextFallback: parsed via explicit name: tool={}, params={}",
                                explicitName, argsMap.keySet());
                    }
                    return;
                }
            }

            // 策略3：{tool_name: {params}} 格式（Ollama 常见输出格式）
            if (explicitName == null && jsonMap.size() == 1) {
                String singleKey = jsonMap.keySet().iterator().next();
                Object singleValue = jsonMap.get(singleKey);
                if (singleValue instanceof Map && toolNameExists(singleKey, toolDefinitions)) {
                    Map<String, Object> argsMap = (Map<String, Object>) singleValue;
                    String key = singleKey + ":" + argsMap.keySet().toString();
                    if (seenKeys.add(key)) {
                        parsed.add(new LlmToolResponse.ToolCall("text-"+java.util.UUID.randomUUID().toString(), singleKey, argsMap));
                        log.debug("[LLM] Node4-TextFallback: parsed via tool-name mapping: tool={}, params={}",
                                singleKey, argsMap.keySet());
                    }
                    return;
                }
            }

            String bestMatchTool = findBestToolMatch(jsonMap, toolDefinitions);
            if (bestMatchTool != null) {
                String key = bestMatchTool + ":" + jsonMap.keySet().toString();
                if (seenKeys.add(key)) {
                    parsed.add(new LlmToolResponse.ToolCall("text-"+java.util.UUID.randomUUID().toString(), bestMatchTool, jsonMap));
                    log.debug("[LLM] Node4-TextFallback: parsed text-based tool call: tool={}, params={}",
                            bestMatchTool, jsonMap.keySet());
                }
            }
        } catch (Exception e) {
            log.debug("[LLM-DIAG] Node4-TextFallback: failed to parse JSON block: {}", e.getMessage());
        }
    }

    /**
     * 从文本内容中查找工具名称的提及，返回最匹配的工具名。
     */
    @SuppressWarnings("unchecked")
    private static String findToolByNameInText(String content, List<Map<String, Object>> toolDefinitions) {
        String lower = content.toLowerCase();
        String bestMatch = null;
        int bestPos = Integer.MAX_VALUE;
        for (Map<String, Object> toolDef : toolDefinitions) {
            Map<String, Object> function = (Map<String, Object>) toolDef.get("function");
            if (function == null) continue;
            String name = (String) function.get("name");
            int pos = lower.indexOf(name.toLowerCase());
            if (pos >= 0 && pos < bestPos) {
                bestPos = pos;
                bestMatch = name;
            }
        }
        return bestMatch;
    }

    /**
     * 从简短文本中提取查询参数（用于构造默认工具调用）。
     */
    private static Map<String, Object> extractQueryFromText(String content) {
        if (content == null || content.isBlank()) return null;
        // 提取引号内的内容作为查询
        java.util.regex.Pattern quotePattern = java.util.regex.Pattern.compile("[\"\"\"]([^\"\"\"]+)[\"\"\"]");
        java.util.regex.Matcher m = quotePattern.matcher(content);
        if (m.find()) {
            return Map.of("query", m.group(1).trim(), "depth", "2");
        }
        // 提取"搜索"、"查询"等关键词后的内容
        java.util.regex.Pattern keywordPattern = java.util.regex.Pattern.compile(
                "(?:搜索|查询|搜|查找|检索|研究)\\s*[:：]?\\s*(.+?)(?:[。！？\\n]|$)");
        m = keywordPattern.matcher(content);
        if (m.find()) {
            return Map.of("query", m.group(1).trim(), "depth", "2");
        }
        return null;
    }

    /**
     * 检查工具名称是否存在于工具定义列表中。
     */
    @SuppressWarnings("unchecked")
    private static boolean toolNameExists(String name, List<Map<String, Object>> toolDefinitions) {
        for (Map<String, Object> toolDef : toolDefinitions) {
            Map<String, Object> function = (Map<String, Object>) toolDef.get("function");
            if (function != null && name.equals(function.get("name"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据 JSON 参数匹配最合适的工具定义。
     * 匹配策略：计算 JSON 参数与工具 required 参数的重叠度，选择重叠度最高的工具。
     */
    @SuppressWarnings("unchecked")
    private static String findBestToolMatch(Map<String, Object> jsonParams,
                                             List<Map<String, Object>> toolDefinitions) {
        String bestTool = null;
        int bestScore = 0;

        for (Map<String, Object> toolDef : toolDefinitions) {
            Map<String, Object> function = (Map<String, Object>) toolDef.get("function");
            if (function == null) continue;
            String name = (String) function.get("name");
            Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");
            if (parameters == null) continue;

            Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");
            List<String> required = (List<String>) parameters.get("required");
            if (properties == null) continue;

            int score = 0;
            for (String key : jsonParams.keySet()) {
                if (properties.containsKey(key)) {
                    score += 2; // exact property match
                }
            }
            if (required != null) {
                for (String req : required) {
                    if (jsonParams.containsKey(req)) {
                        score += 3; // required param match bonus
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestTool = name;
            }
        }
        return bestTool;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String args) {
        if (args == null || args.isBlank()) return Map.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(args, Map.class);
        } catch (Exception e) {
            return Map.of("raw", args);
        }
    }

    private String toJsonString(Object obj) {
        if (obj == null) return "{}";
        if (obj instanceof String s) return s;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }
}