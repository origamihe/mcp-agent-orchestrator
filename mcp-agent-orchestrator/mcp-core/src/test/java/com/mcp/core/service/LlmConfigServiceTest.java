package com.mcp.core.service;

import com.mcp.core.domain.llm.LlmModelConfig;
import com.mcp.core.domain.llm.LlmProviderType;
import com.mcp.core.domain.llm.ProviderAvailability;
import com.mcp.core.entity.LlmConfigEntity;
import com.mcp.core.mapper.LlmConfigMapper;
import com.mcp.core.repository.LlmConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmConfigService — 多 Provider 降级策略")
class LlmConfigServiceTest {

    @Mock
    private LlmConfigRepository repository;

    @Mock
    private LlmConfigMapper mapper;

    @Mock
    private ProviderAvailability providerAvailability;

    private LlmConfigService service;

    @BeforeEach
    void setUp() {
        service = new LlmConfigService(repository, mapper, providerAvailability);
    }

    private static LlmConfigEntity entity(String id, LlmProviderType provider, String model) {
        LlmConfigEntity e = new LlmConfigEntity();
        e.setConfigId(id);
        e.setProvider(provider);
        e.setModelName(model);
        e.setEnabled(true);
        return e;
    }

    private static LlmModelConfig domain(LlmProviderType provider, String model) {
        return LlmModelConfig.builder()
                .provider(provider)
                .modelName(model)
                .build();
    }

    @Nested
    @DisplayName("getDefaultConfig — 遍历所有启用配置，跳过不可用 Provider")
    class DefaultConfigWithMultipleProviders {

        @Test
        @DisplayName("GOOGLE_GENAI 不可用 + LOCAL_OLLAMA 可用 → 应选择 LOCAL_OLLAMA 而非回退到硬编码兜底")
        void shouldSkipUnavailableAndUseNextAvailable() {
            LlmConfigEntity googleConfig = entity("c1", LlmProviderType.GOOGLE_GENAI, "gemini-pro");
            LlmConfigEntity ollamaConfig = entity("c2", LlmProviderType.LOCAL_OLLAMA, "qwen3:8b");

            LlmModelConfig googleDomain = domain(LlmProviderType.GOOGLE_GENAI, "gemini-pro");
            LlmModelConfig ollamaDomain = domain(LlmProviderType.LOCAL_OLLAMA, "qwen3:8b");

            when(repository.findAll()).thenReturn(List.of(googleConfig, ollamaConfig));
            when(mapper.toDomain(googleConfig)).thenReturn(googleDomain);
            when(mapper.toDomain(ollamaConfig)).thenReturn(ollamaDomain);
            when(providerAvailability.isProviderAvailable(LlmProviderType.GOOGLE_GENAI)).thenReturn(false);
            when(providerAvailability.isProviderAvailable(LlmProviderType.LOCAL_OLLAMA)).thenReturn(true);

            StepVerifier.create(service.getDefaultConfig())
                    .expectNextMatches(config ->
                            config.getProvider() == LlmProviderType.LOCAL_OLLAMA
                                    && "qwen3:8b".equals(config.getModelName()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("所有 DB Provider 都不可用 → 回退到硬编码 Ollama 兜底配置")
        void shouldFallbackToHardcodedOllamaWhenAllUnavailable() {
            LlmConfigEntity googleConfig = entity("c1", LlmProviderType.GOOGLE_GENAI, "gemini-pro");
            LlmModelConfig googleDomain = domain(LlmProviderType.GOOGLE_GENAI, "gemini-pro");

            when(repository.findAll()).thenReturn(List.of(googleConfig));
            when(mapper.toDomain(googleConfig)).thenReturn(googleDomain);
            when(providerAvailability.isProviderAvailable(LlmProviderType.GOOGLE_GENAI)).thenReturn(false);

            StepVerifier.create(service.getDefaultConfig())
                    .expectNextMatches(config ->
                            config.getProvider() == LlmProviderType.LOCAL_OLLAMA
                                    && "qwen3:8b".equals(config.getModelName()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("DB 无启用配置 → 回退到硬编码 Ollama 兜底配置")
        void shouldFallbackWhenNoEnabledConfigs() {
            when(repository.findAll()).thenReturn(List.of());

            StepVerifier.create(service.getDefaultConfig())
                    .expectNextMatches(config ->
                            config.getProvider() == LlmProviderType.LOCAL_OLLAMA)
                    .verifyComplete();
        }
    }
}