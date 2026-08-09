package com.mcp.llm.factory.impl;

import com.mcp.core.domain.llm.LlmModelConfig;
import com.mcp.core.domain.llm.LlmProviderType;
import com.mcp.llm.config.ProviderConfig;
import com.mcp.llm.context.ProviderContext;
import com.mcp.llm.factory.ProviderFactory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * OpenAI Compatible API 统一 Provider 工厂实现
 * 支持 Google AI Studio、OpenAI、DeepSeek 等使用 OpenAI Compatible API 的 Provider
 * 仅在 OpenAiChatModel Bean 存在时注册（当前默认使用 Ollama，不注册）
 */
@Component
@ConditionalOnBean(OpenAiChatModel.class)
public class OpenAiCompatibleProviderFactory implements ProviderFactory {

    private final ObjectProvider<OpenAiChatModel> chatModelProvider;

    private static final Map<LlmProviderType, String> PROVIDER_LABELS = Map.of(
            LlmProviderType.GOOGLE_GENAI, "google",
            LlmProviderType.OPENAI, "openai",
            LlmProviderType.DEEPSEEK, "deepseek"
    );

    public OpenAiCompatibleProviderFactory(ObjectProvider<OpenAiChatModel> chatModelProvider) {
        this.chatModelProvider = chatModelProvider;
    }

    @Override
    public Set<LlmProviderType> supportedProviders() {
        return Set.of(LlmProviderType.GOOGLE_GENAI, LlmProviderType.OPENAI, LlmProviderType.DEEPSEEK);
    }

    @Override
    public ProviderContext createContext(LlmModelConfig modelConfig) {
        LlmProviderType providerType = modelConfig.getProvider();

        ChatOptions options = OpenAiChatOptions.builder()
                .model(modelConfig.getModelName())
                .temperature(modelConfig.getTemperature())
                .maxTokens(modelConfig.getMaxTokens())
                .build();

        ProviderConfig providerConfig = ProviderConfig.builder()
                .streamEnabled(true)
                .toolEnabled(true)
                .visionEnabled(true)
                .embeddingEnabled(true)
                .build();

        String label = PROVIDER_LABELS.getOrDefault(providerType, providerType.name().toLowerCase());

        return new ProviderContext(
                getChatModel(),
                options,
                providerType,
                providerConfig,
                Map.of("model", modelConfig.getModelName(), "provider", label)
        );
    }

    private OpenAiChatModel getChatModel() {
        OpenAiChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException(
                    "OpenAiChatModel 不可用，请检查 spring.ai.openai 配置");
        }
        return model;
    }
}