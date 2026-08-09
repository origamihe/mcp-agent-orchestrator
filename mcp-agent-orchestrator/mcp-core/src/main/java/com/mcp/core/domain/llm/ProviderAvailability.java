package com.mcp.core.domain.llm;

/**
 * Provider 可用性检查接口
 * 由 mcp-llm 模块的 ProviderRegistry 实现
 * 用于在 mcp-core 中判断数据库配置的 Provider 是否实际可用
 */
public interface ProviderAvailability {

    /**
     * 判断指定 Provider 是否有已注册的 Factory
     */
    boolean isProviderAvailable(LlmProviderType providerType);
}