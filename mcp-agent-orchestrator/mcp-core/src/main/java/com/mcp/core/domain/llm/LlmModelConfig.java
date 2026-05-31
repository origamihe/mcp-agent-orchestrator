package com.mcp.core.domain.llm;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * LLM 模型配置 - 领域模型
 */
public class LlmModelConfig {

    private final String configId;
    private final LlmProviderType provider;
    private final String modelName;
    private final Double temperature;
    private final Integer maxTokens;
    private final Map<String, Object> parameters;   // 扩展参数
    private final boolean enabled;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public LlmModelConfig(String configId, LlmProviderType provider, String modelName,
                          Double temperature, Integer maxTokens, Map<String, Object> parameters,
                          boolean enabled, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.configId = configId;
        this.provider = provider;
        this.modelName = modelName;
        this.temperature = temperature != null ? temperature : 0.7;
        this.maxTokens = maxTokens != null ? maxTokens : 2048;
        this.parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters
    public String getConfigId() { return configId; }
    public LlmProviderType getProvider() { return provider; }
    public String getModelName() { return modelName; }
    public Double getTemperature() { return temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public Map<String, Object> getParameters() { return parameters; }
    public boolean isEnabled() { return enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String configId;
        private LlmProviderType provider;
        private String modelName;
        private Double temperature;
        private Integer maxTokens;
        private Map<String, Object> parameters;
        private boolean enabled = true;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder configId(String configId) {
            this.configId = configId;
            return this;
        }

        public Builder provider(LlmProviderType provider) {
            this.provider = provider;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public LlmModelConfig build() {
            return new LlmModelConfig(configId, provider, modelName, temperature,
                    maxTokens, parameters, enabled, createdAt, updatedAt);
        }
    }
}