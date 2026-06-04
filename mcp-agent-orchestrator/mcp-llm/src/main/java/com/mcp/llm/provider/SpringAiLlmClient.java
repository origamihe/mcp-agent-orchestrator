package com.mcp.llm.provider;

import com.mcp.core.domain.llm.LlmModelConfig;
import com.mcp.core.service.LlmConfigService;
import com.mcp.llm.client.ChatMessage;
import com.mcp.llm.client.LlmClient;
import com.mcp.core.domain.chat.CoreChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

/**
 * Google Gemini LLM 客户端实现（适配 Spring AI 1.1.0-M2）
 */
@Component
@RequiredArgsConstructor
public class SpringAiLlmClient implements LlmClient {

    private final ChatClient chatClient;
    private final LlmConfigService llmConfigService;

    @Override
    public Mono<String> generate(String prompt) {
        return llmConfigService.getDefaultConfig()
                .flatMap(config ->
                        Mono.fromCallable(() -> chatClient.prompt()
                                .user(prompt)
                                .options(buildOptions(config))
                                .call()
                                .content()
                        ).subscribeOn(Schedulers.boundedElastic())
                )
                .defaultIfEmpty("No response generated.");
    }

    @Override
    public Mono<String> generateWithSystemPrompt(String systemPrompt, String userPrompt) {
        return llmConfigService.getDefaultConfig()
                .flatMap(config ->
                        Mono.fromCallable(() -> chatClient.prompt()
                                .system(systemPrompt)
                                .user(userPrompt)
                                .options(buildOptions(config))
                                .call()
                                .content()
                        ).subscribeOn(Schedulers.boundedElastic())
                )
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
                    List<Message> messages = convertToSpringAiMessages(history, newMessage);
                    return Mono.fromCallable(() -> chatClient.prompt()
                            .messages(messages)
                            .options(buildOptions(config))
                            .call()
                            .content())
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .defaultIfEmpty("No response from AI model.");
    }

    @Override
    public Mono<String> chatWithCoreHistory(List<CoreChatMessage> history, String newMessage) {
        return llmConfigService.getDefaultConfig()
                .flatMap(config -> {
                    List<Message> messages = convertCoreToSpringAiMessages(history, newMessage);
                    return Mono.fromCallable(() -> chatClient.prompt()
                            .messages(messages)
                            .options(buildOptions(config))
                            .call()
                            .content())
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .defaultIfEmpty("No response from AI model.");
    }

    @Override
    public Mono<String> chatWithSession(String sessionId, String newMessage) {
        return Mono.error(new UnsupportedOperationException("chatWithSession 方法暂未实现"));
    }

    // ====================== 辅助方法 ======================

    private List<Message> convertToSpringAiMessages(List<ChatMessage> history, String newMessage) {
        List<Message> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            String role = msg.getRole().toLowerCase();
            messages.add(switch (role) {
                case "system" -> new SystemMessage(msg.getContent());
                case "assistant" -> new AssistantMessage(msg.getContent());
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
                default -> new UserMessage(msg.getContent());
            });
        }
        messages.add(new UserMessage(newMessage));
        return messages;
    }

    private OllamaChatOptions buildOptions(LlmModelConfig config) {
        return OllamaChatOptions.builder()
                .model(config.getModelName())
                .temperature(config.getTemperature())
                .build();
    }
}