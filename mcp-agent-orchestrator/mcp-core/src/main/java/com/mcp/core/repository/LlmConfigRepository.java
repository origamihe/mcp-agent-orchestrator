package com.mcp.core.repository;

import com.mcp.core.domain.llm.LlmProviderType;
import com.mcp.core.entity.LlmConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * LLM 配置 Repository
 */
@Repository
public interface LlmConfigRepository extends JpaRepository<LlmConfigEntity, String> {

    /**
     * 根据提供商和模型名称查找
     */
    Optional<LlmConfigEntity> findByProviderAndModelName(LlmProviderType provider, String modelName);

    /**
     * 查找所有启用的配置
     */
    List<LlmConfigEntity> findByEnabledTrue();

    /**
     * 根据提供商查找所有配置
     */
    List<LlmConfigEntity> findByProvider(LlmProviderType provider);

    /**
     * 查找默认配置（可根据业务规则调整）
     */
    Optional<LlmConfigEntity> findFirstByEnabledTrueOrderByUpdatedAtDesc();
}