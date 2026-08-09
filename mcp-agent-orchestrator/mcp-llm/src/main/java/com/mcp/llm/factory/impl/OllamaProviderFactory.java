package com.mcp.llm.factory.impl;

import com.mcp.core.domain.llm.LlmModelConfig;
import com.mcp.core.domain.llm.LlmProviderType;
import com.mcp.llm.config.ProviderConfig;
import com.mcp.llm.context.ProviderContext;
import com.mcp.llm.factory.ProviderFactory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ollama Provider 工厂实现
 * ChatModel 延迟加载：仅在首次 createContext 时从容器获取
 */
@Component
public class OllamaProviderFactory implements ProviderFactory {

    private final ObjectProvider<OllamaChatModel> chatModelProvider;
    private final AtomicReference<OllamaChatModel> cachedModel = new AtomicReference<>();

    public OllamaProviderFactory(ObjectProvider<OllamaChatModel> chatModelProvider) {
        this.chatModelProvider = chatModelProvider;
    }

    @Override
    public Set<LlmProviderType> supportedProviders() {
        return Set.of(LlmProviderType.LOCAL_OLLAMA);
    }

    private OllamaChatModel getChatModel() {
        OllamaChatModel model = cachedModel.get();
        if (model == null) {
            model = chatModelProvider.getIfAvailable();
            if (model == null) {
                throw new IllegalStateException(
                        "OllamaChatModel 不可用，请检查 spring.ai.ollama.base-url 配置");
            }
            cachedModel.compareAndSet(null, model);
        }
        return model;
    }

    @Override
    public ProviderContext createContext(LlmModelConfig modelConfig) {
        ChatOptions options = OllamaChatOptions.builder()
                .model(modelConfig.getModelName())
                .temperature(modelConfig.getTemperature())
                .numPredict(modelConfig.getMaxTokens())
                .build();

        ProviderConfig providerConfig = ProviderConfig.builder()
                .streamEnabled(true)
                .toolEnabled(true)
                .build();

        return new ProviderContext(
                getChatModel(),
                options,
                LlmProviderType.LOCAL_OLLAMA,
                providerConfig,
                Map.of("model", modelConfig.getModelName())
        );
    }
}