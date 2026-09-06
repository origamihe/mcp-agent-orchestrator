package com.mcp.core.service;

import com.mcp.core.domain.llm.LlmModelConfig;
import com.mcp.core.domain.llm.LlmProviderType;
import com.mcp.core.domain.llm.ProviderAvailability;
import com.mcp.core.entity.LlmConfigEntity;
import com.mcp.core.mapper.LlmConfigMapper;
import com.mcp.core.repository.LlmConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmConfigService {

    private final LlmConfigRepository repository;
    private final LlmConfigMapper mapper;
    private final ProviderAvailability providerAvailability;

    private final Map<String, LlmModelConfig> configCache = new ConcurrentHashMap<>();
    private volatile LlmModelConfig defaultConfig;
    private volatile boolean cacheLoaded = false;

    @PostConstruct
    public void initCache() {
        refreshCache();
    }

    public void refreshCache() {
        try {
            List<LlmConfigEntity> allConfigs = repository.findAll();
            configCache.clear();
            LlmModelConfig fallbackDefault = null;

            for (LlmConfigEntity entity : allConfigs) {
                LlmModelConfig config = mapper.toDomain(entity);
                configCache.put(entity.getConfigId(), config);

                String providerModelKey = entity.getProvider().name() + "_" + entity.getModelName();
                configCache.put(providerModelKey, config);

                if (entity.getEnabled() && fallbackDefault == null) {
                    if (providerAvailability.isProviderAvailable(config.getProvider())) {
                        fallbackDefault = config;
                    } else {
                        log.info("[LlmConfig] DB config provider {} is not available, skipping",
                                config.getProvider());
                    }
                }
            }

            defaultConfig = fallbackDefault != null ? fallbackDefault : getDefaultOllamaConfig();
            cacheLoaded = true;
            log.info("[LlmConfig] Cache loaded: {} configs cached, default={}",
                    allConfigs.size(), defaultConfig.getProvider() + "/" + defaultConfig.getModelName());
        } catch (Exception e) {
            log.warn("[LlmConfig] Failed to load configs from DB, using fallback default", e);
            defaultConfig = getDefaultOllamaConfig();
            cacheLoaded = true;
        }
    }

    /**
     * 获取默认配置（优先从缓存，缓存未命中时刷新缓存后返回）
     */
    public Mono<LlmModelConfig> getDefaultConfig() {
        if (cacheLoaded && defaultConfig != null) {
            return Mono.just(defaultConfig);
        }
        return Mono.fromCallable(() -> {
            refreshCache();
            return defaultConfig;
        });
    }

    /**
     * 根据 provider 和 model 获取配置（优先从缓存）
     */
    public Mono<LlmModelConfig> getConfig(LlmProviderType provider, String modelName) {
        String key = provider.name() + "_" + modelName;
        LlmModelConfig cached = configCache.get(key);
        if (cached != null) {
            return Mono.just(cached);
        }
        return Mono.fromCallable(() -> {
            LlmConfigEntity entity = repository.findByProviderAndModelName(provider, modelName)
                    .orElseThrow(() -> new RuntimeException("LLM Config not found: " + provider + "/" + modelName));
            LlmModelConfig config = mapper.toDomain(entity);
            configCache.put(key, config);
            configCache.put(entity.getConfigId(), config);
            return config;
        });
    }

    /**
     * 获取所有可用配置（优先从缓存）
     */
    public Mono<List<LlmModelConfig>> getAllEnabledConfigs() {
        if (cacheLoaded) {
            List<LlmModelConfig> enabled = configCache.values().stream()
                    .filter(LlmModelConfig::isEnabled)
                    .distinct()
                    .toList();
            if (!enabled.isEmpty()) {
                return Mono.just(enabled);
            }
        }
        return Mono.fromCallable(() -> repository.findByEnabledTrue().stream()
                .map(mapper::toDomain)
                .toList());
    }

    /**
     * 根据 configId 获取配置（优先从缓存）
     */
    public Mono<LlmModelConfig> getConfigById(String configId) {
        LlmModelConfig cached = configCache.get(configId);
        if (cached != null) {
            return Mono.just(cached);
        }
        return Mono.fromCallable(() -> {
            LlmConfigEntity entity = repository.findById(configId)
                    .orElseThrow(() -> new RuntimeException("LLM Config not found: " + configId));
            LlmModelConfig config = mapper.toDomain(entity);
            configCache.put(configId, config);
            return config;
        });
    }

    /**
     * 同步获取模型上下文窗口大小（优先从缓存）。
     * 用于 ContextBudget.forModel() 等非响应式上下文。
     *
     * @param configId 模型配置 ID，为 null 时使用默认配置
     * @return 模型上下文窗口大小（token 数）
     */
    public int getContextWindow(String configId) {
        if (configId != null) {
            LlmModelConfig cached = configCache.get(configId);
            if (cached != null) {
                return cached.getContextWindow();
            }
        }
        if (defaultConfig != null) {
            return defaultConfig.getContextWindow();
        }
        return 128000;
    }

    /**
     * 兜底默认配置（数据库无配置时使用）
     * 当前默认：Ollama 本地 qwen3:8b
     */
    private LlmModelConfig getDefaultOllamaConfig() {
        return LlmModelConfig.builder()
                .configId("default-ollama-qwen3")
                .provider(LlmProviderType.LOCAL_OLLAMA)
                .modelName("qwen3:8b")
                .temperature(0.7)
                .maxTokens(4096)
                .contextWindow(128000)
                .enabled(true)
                .build();
    }
}