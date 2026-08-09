package com.mcp.llm.factory;

import com.mcp.core.domain.llm.LlmModelConfig;
import com.mcp.core.domain.llm.LlmProviderType;
import com.mcp.llm.context.ProviderContext;

import java.util.Set;

/**
 * Provider 工厂接口 - 策略模式
 * 每个 Factory 负责创建特定 Provider 的完整上下文
 */
public interface ProviderFactory {

    /**
     * 获取支持的 Provider 类型集合
     */
    Set<LlmProviderType> supportedProviders();

    /**
     * 创建 Provider 上下文
     *
     * @param modelConfig LLM 模型配置（来自数据库）
     * @return ProviderContext
     */
    ProviderContext createContext(LlmModelConfig modelConfig);
}