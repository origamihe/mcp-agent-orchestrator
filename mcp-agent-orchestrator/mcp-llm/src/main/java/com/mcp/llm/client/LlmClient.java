package com.mcp.llm.client;

import com.mcp.core.domain.chat.CoreChatMessage;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * LLM 客户端统一抽象接口
 */
public interface LlmClient {

    Mono<String> generate(String prompt);

    Mono<String> generateWithSystemPrompt(String systemPrompt, String userPrompt);

    Mono<String> chat(String message);

    /**
     * 带历史对话的聊天（推荐使用）
     */
    Mono<String> chatWithHistory(List<ChatMessage> history, String newMessage);

    /**
     * 使用 Core Domain 的历史（内部推荐）
     */
    Mono<String> chatWithCoreHistory(List<CoreChatMessage> history, String newMessage);

    /**
     * 支持带 SessionId 的完整对话（未来扩展性更好）
     */
    Mono<String> chatWithSession(String sessionId, String newMessage);

    /**
     * 使用指定模型配置生成回复
     */
    Mono<String> generateWithConfig(String configId, String prompt);

    /**
     * 使用指定模型配置 + 系统提示生成回复
     */
    Mono<String> generateWithConfigAndSystem(String configId, String systemPrompt, String userPrompt);
}