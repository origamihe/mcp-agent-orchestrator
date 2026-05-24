package com.mcp.llm.client;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * LLM 客户端统一抽象
 */
public interface LlmClient {

    Mono<String> generate(String prompt);

    Mono<String> generateWithSystemPrompt(String systemPrompt, String userPrompt);

    Mono<String> chat(String message);

    Mono<String> chatWithHistory(List<ChatMessage> history, String newMessage);
}