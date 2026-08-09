package com.mcp.llm.context;

import com.mcp.core.domain.llm.LlmProviderType;
import com.mcp.llm.config.ProviderConfig;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.Map;

/**
 * Provider 上下文 - 封装完整的 LLM Provider 信息
 * SpringAiLlmClient 通过此上下文进行聊天，无需关心具体 Provider
 */
public record ProviderContext(
        ChatModel chatModel,
        ChatOptions chatOptions,
        LlmProviderType providerType,
        ProviderConfig providerConfig,
        Map<String, Object> metadata
) {
}