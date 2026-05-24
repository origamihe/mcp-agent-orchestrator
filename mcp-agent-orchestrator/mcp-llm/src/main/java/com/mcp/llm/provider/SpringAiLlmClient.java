package com.mcp.llm.provider;

import com.mcp.llm.client.ChatMessage;
import com.mcp.llm.client.LlmClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class SpringAiLlmClient implements LlmClient {

    private final ChatClient chatClient;

    public SpringAiLlmClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Mono<String> generate(String prompt) {
        return Mono.fromCallable(() ->
                chatClient.prompt(prompt)
                        .call()
                        .content()
        );
    }

    @Override
    public Mono<String> generateWithSystemPrompt(String systemPrompt, String userPrompt) {
        return Mono.fromCallable(() ->
                chatClient.prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .call()
                        .content()
        );
    }

    @Override
    public Mono<String> chat(String message) {
        return generate(message);
    }

    @Override
    public Mono<String> chatWithHistory(List<ChatMessage> history, String newMessage) {
        // TODO: 实现带历史对话的调用
        return generate(newMessage);
    }
}