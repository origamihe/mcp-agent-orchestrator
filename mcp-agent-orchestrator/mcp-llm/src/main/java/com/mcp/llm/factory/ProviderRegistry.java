package com.mcp.llm.factory;

import com.mcp.core.domain.llm.LlmModelConfig;
import com.mcp.core.domain.llm.LlmProviderType;
import com.mcp.core.domain.llm.ProviderAvailability;
import com.mcp.llm.context.ProviderContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provider 注册中心
 * 使用 ConcurrentHashMap 实现 O(1) 查找
 * 同时实现 ProviderAvailability 接口，供 mcp-core 模块查询 Provider 可用性
 */
@Component
@RequiredArgsConstructor
public class ProviderRegistry implements ProviderAvailability {

    private final List<ProviderFactory> factories;

    /** ProviderType -> Factory 的映射 */
    private final Map<LlmProviderType, ProviderFactory> providerFactoryMap = new ConcurrentHashMap<>();

    /**
     * 初始化时构建映射表
     * O(n) 初始化，O(1) 查找
     */
    @PostConstruct
    public void init() {
        for (ProviderFactory factory : factories) {
            for (LlmProviderType providerType : factory.supportedProviders()) {
                providerFactoryMap.put(providerType, factory);
            }
        }
    }

    @Override
    public boolean isProviderAvailable(LlmProviderType providerType) {
        return providerFactoryMap.containsKey(providerType);
    }

    /**
     * 根据配置获取 Provider 上下文
     *
     * @param modelConfig LLM 模型配置
     * @return ProviderContext
     */
    public ProviderContext getContext(LlmModelConfig modelConfig) {
        LlmProviderType providerType = modelConfig.getProvider();
        
        ProviderFactory factory = providerFactoryMap.get(providerType);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "Unsupported LLM provider: " + providerType);
        }
        
        return factory.createContext(modelConfig);
    }
}