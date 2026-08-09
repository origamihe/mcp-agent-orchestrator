package com.mcp.llm.config;

import lombok.Builder;
import lombok.Data;

/**
 * Provider 能力配置 - 标记 Provider 支持的能力
 * baseUrl/apiKey 等由 Spring Boot 自动配置管理，不需要在这里重复构建
 */
@Data
@Builder
public class ProviderConfig {

    /** 是否启用流式响应 */
    private Boolean streamEnabled;

    /** 是否启用工具调用 */
    private Boolean toolEnabled;

    /** 是否启用视觉能力 */
    private Boolean visionEnabled;

    /** 是否启用嵌入能力 */
    private Boolean embeddingEnabled;
}