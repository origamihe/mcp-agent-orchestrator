package com.mcp.core.service;

import com.mcp.core.domain.llm.LlmModelConfig;
import com.mcp.core.domain.llm.LlmProviderType;
import com.mcp.core.entity.LlmConfigEntity;
import com.mcp.core.mapper.LlmConfigMapper;
import com.mcp.core.repository.LlmConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LlmConfigService {

    private final LlmConfigRepository repository;
    private final LlmConfigMapper mapper;

    /**
     * 获取默认配置（推荐）
     */
    public Mono<LlmModelConfig> getDefaultConfig() {
        return Mono.fromCallable(() -> repository.findFirstByEnabledTrueOrderByUpdatedAtDesc()
                .map(mapper::toDomain)
                .orElseGet(this::getDefaultGoogleConfig));
    }

    /**
     * 根据 provider 和 model 获取配置
     */
    public Mono<LlmModelConfig> getConfig(LlmProviderType provider, String modelName) {
        return Mono.fromCallable(() -> repository.findByProviderAndModelName(provider, modelName)
                .map(mapper::toDomain)
                .orElseThrow(() -> new RuntimeException("LLM Config not found: " + provider + "/" + modelName)));
    }

    /**
     * 获取所有可用配置
     */
    public Mono<List<LlmModelConfig>> getAllEnabledConfigs() {
        return Mono.fromCallable(() -> repository.findByEnabledTrue().stream()
                .map(mapper::toDomain)
                .toList());
    }

    /**
     * 根据 configId 获取配置
     */
    public Mono<LlmModelConfig> getConfigById(String configId) {
        return Mono.fromCallable(() -> repository.findById(configId)
                .map(mapper::toDomain)
                .orElseGet(() -> {
                    if (configId != null && configId.startsWith("ollama-")) {
                        String modelName = configId.substring("ollama-".length()).replace("-", ":");
                        return LlmModelConfig.builder()
                                .configId(configId)
                                .provider(LlmProviderType.LOCAL_OLLAMA)
                                .modelName(modelName)
                                .temperature(0.7)
                                .maxTokens(2048)
                                .enabled(true)
                                .build();
                    }
                    throw new RuntimeException("LLM Config not found: " + configId);
                }));
    }

    /**
     * 兜底默认 Ollama 配置
     */
    private LlmModelConfig getDefaultGoogleConfig() {
        return LlmModelConfig.builder()
                .configId("default-ollama")
                .provider(LlmProviderType.LOCAL_OLLAMA)
                .modelName("gemma4")
                .temperature(0.7)
                .maxTokens(2048)
                .enabled(true)
                .build();
    }
}