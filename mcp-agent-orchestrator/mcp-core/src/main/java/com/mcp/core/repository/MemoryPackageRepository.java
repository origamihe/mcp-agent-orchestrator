package com.mcp.core.repository;

import com.mcp.core.domain.memory.MemoryCategory;
import com.mcp.core.entity.MemoryPackageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 记忆包 Repository
 */
@Repository
public interface MemoryPackageRepository extends JpaRepository<MemoryPackageEntity, Long> {

    /**
     * 根据会话查找所有记忆包，按权重降序排列
     */
    List<MemoryPackageEntity> findBySessionIdOrderByWeightDesc(String sessionId);

    /**
     * 根据会话和分类查找记忆包
     */
    List<MemoryPackageEntity> findBySessionIdAndCategoryOrderByVersionDesc(String sessionId, MemoryCategory category);

    /**
     * 查找会话指定分类的最新版本记忆
     */
    Optional<MemoryPackageEntity> findFirstBySessionIdAndCategoryOrderByVersionDesc(String sessionId, MemoryCategory category);

    /**
     * 统计会话记忆包数量
     */
    long countBySessionId(String sessionId);

    /**
     * 增加访问计数
     */
    @Modifying
    @Transactional
    @Query("UPDATE MemoryPackageEntity m SET m.accessCount = m.accessCount + 1, m.lastAccessedAt = :now WHERE m.id = :id")
    void incrementAccess(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 删除会话所有记忆
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM MemoryPackageEntity m WHERE m.sessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查找过期记忆（衰减机制：很久没访问的记忆降低权重）
     */
    List<MemoryPackageEntity> findByLastAccessedAtBefore(LocalDateTime cutoffTime);
}