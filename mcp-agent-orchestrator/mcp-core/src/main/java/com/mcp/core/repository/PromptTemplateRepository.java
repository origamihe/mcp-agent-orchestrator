package com.mcp.core.repository;

import com.mcp.core.domain.prompt.PromptType;
import com.mcp.core.entity.PromptTemplateEntity;
import com.mcp.core.entity.PromptTemplateId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Prompt 模板 Repository — 支持版本管理与 A/B 变体。
 */
@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplateEntity, PromptTemplateId> {

    /**
     * 根据名称查找（返回默认变体最新版本）
     */
    @Query("SELECT p FROM PromptTemplateEntity p WHERE p.name = :name AND p.variant = 'default' ORDER BY p.version DESC")
    List<PromptTemplateEntity> findByName(@Param("name") String name);

    /**
     * 根据名称和变体查找所有版本
     */
    @Query("SELECT p FROM PromptTemplateEntity p WHERE p.name = :name AND p.variant = :variant ORDER BY p.version DESC")
    List<PromptTemplateEntity> findByNameAndVariant(@Param("name") String name, @Param("variant") String variant);

    /**
     * 根据类型查找所有启用的模板
     */
    @Query("SELECT p FROM PromptTemplateEntity p WHERE p.type = :type AND p.enabled = true")
    List<PromptTemplateEntity> findByType(@Param("type") PromptType type);

    /**
     * 根据类型查找所有变体（用于 A/B 测试，取每个变体的最新版本）
     */
    @Query("SELECT p FROM PromptTemplateEntity p WHERE p.type = :type AND p.enabled = true " +
            "AND p.version = (SELECT MAX(p2.version) FROM PromptTemplateEntity p2 " +
            "WHERE p2.name = p.name AND p2.variant = p.variant AND p2.enabled = true) " +
            "ORDER BY p.name, p.weight DESC")
    List<PromptTemplateEntity> findLatestEnabledVariantsByType(@Param("type") PromptType type);

    /**
     * 根据类型查找最新版本（仅默认变体，兼容旧接口）
     */
    @Query("SELECT p FROM PromptTemplateEntity p " +
            "WHERE p.type = :type AND p.variant = 'default' AND p.enabled = true " +
            "ORDER BY p.version DESC")
    List<PromptTemplateEntity> findLatestByType(@Param("type") PromptType type);

    /**
     * 查找特定名称和类型的最新模板（仅默认变体）
     */
    @Query("SELECT p FROM PromptTemplateEntity p WHERE p.name = :name AND p.type = :type " +
            "AND p.variant = 'default' AND p.enabled = true ORDER BY p.version DESC")
    List<PromptTemplateEntity> findLatestByNameAndType(@Param("name") String name, @Param("type") PromptType type);

    /**
     * 查找所有活跃模板
     */
    @Query("SELECT p FROM PromptTemplateEntity p WHERE p.version > 0")
    List<PromptTemplateEntity> findAllActiveTemplates();
}