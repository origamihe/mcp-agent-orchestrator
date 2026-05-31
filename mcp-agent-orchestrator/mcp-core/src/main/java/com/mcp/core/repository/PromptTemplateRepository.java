package com.mcp.core.repository;

import com.mcp.core.domain.prompt.PromptType;
import com.mcp.core.entity.PromptTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Prompt 模板 Repository
 */
@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplateEntity, String> {

    /**
     * 根据名称查找（主键）
     */
    Optional<PromptTemplateEntity> findByName(String name);

    /**
     * 根据类型查找所有模板
     */
    List<PromptTemplateEntity> findByType(PromptType type);

    /**
     * 根据类型查找最新版本的模板
     */
    @Query("SELECT p FROM PromptTemplateEntity p " +
            "WHERE p.type = :type " +
            "ORDER BY p.version DESC")
    List<PromptTemplateEntity> findLatestByType(@Param("type") PromptType type);

    /**
     * 查找特定名称和类型的最新模板
     */
    Optional<PromptTemplateEntity> findFirstByNameAndTypeOrderByVersionDesc(String name, PromptType type);

    /**
     * 查找所有已启用的模板（可扩展字段）
     */
    @Query("SELECT p FROM PromptTemplateEntity p WHERE p.version > 0")
    List<PromptTemplateEntity> findAllActiveTemplates();
}