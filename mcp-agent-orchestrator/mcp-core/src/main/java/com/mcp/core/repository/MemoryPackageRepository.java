package com.mcp.core.repository;

import com.mcp.core.domain.memory.MemoryCategory;
import com.mcp.core.domain.memory.MemoryScope;
import com.mcp.core.domain.memory.MemoryType;
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
     * 增加访问计数（单条）
     */
    @Modifying
    @Transactional
    @Query("UPDATE MemoryPackageEntity m SET m.accessCount = m.accessCount + 1, m.lastAccessedAt = :now WHERE m.id = :id")
    void incrementAccess(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 批量增加访问计数（一次 SQL 完成多条，消除 N+1 问题）
     */
    @Modifying
    @Transactional
    @Query("UPDATE MemoryPackageEntity m SET m.accessCount = m.accessCount + 1, m.lastAccessedAt = :now WHERE m.id IN :ids")
    void batchIncrementAccess(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);

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

    /**
     * 根据用户ID查找记忆包
     */
    List<MemoryPackageEntity> findByUserIdOrderByWeightDesc(String userId);

    /**
     * 根据群ID查找记忆包
     */
    List<MemoryPackageEntity> findByGroupIdOrderByWeightDesc(String groupId);

    /**
     * 根据会话和用户查找活跃记忆包（按类型过滤）
     */
    List<MemoryPackageEntity> findBySessionIdAndUserIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(
            String sessionId, String userId, MemoryType memoryType);

    /**
     * 根据会话查找活跃记忆包（按类型过滤，userId 未知时使用）
     */
    List<MemoryPackageEntity> findBySessionIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(
            String sessionId, MemoryType memoryType);

    /**
     * 根据会话和群查找记忆包
     */
    List<MemoryPackageEntity> findBySessionIdAndGroupIdOrderByWeightDesc(String sessionId, String groupId);

    /**
     * 根据会话和作用域查找记忆包
     */
    List<MemoryPackageEntity> findBySessionIdAndScopeOrderByWeightDesc(String sessionId, MemoryScope scope);

    /**
     * 根据会话、用户ID和作用域查找记忆包
     */
    List<MemoryPackageEntity> findBySessionIdAndUserIdAndScopeOrderByWeightDesc(
            String sessionId, String userId, MemoryScope scope);

    /**
     * 根据会话、群ID和作用域查找记忆包
     */
    List<MemoryPackageEntity> findBySessionIdAndGroupIdAndScopeOrderByWeightDesc(
            String sessionId, String groupId, MemoryScope scope);

    /**
     * 统计会话非 PERSONA 记忆包数量
     */
    @Query("SELECT COUNT(m) FROM MemoryPackageEntity m WHERE m.sessionId = :sessionId AND m.scope <> 'PERSONA'")
    long countBySessionIdExcludingPersona(@Param("sessionId") String sessionId);

    /**
     * 根据用户ID和记忆类型查找活跃记忆
     */
    @Query("SELECT m FROM MemoryPackageEntity m WHERE m.userId = :userId AND m.memoryType = :memoryType AND m.isActive = true ORDER BY m.weight DESC")
    List<MemoryPackageEntity> findByUserIdAndMemoryType(
            @Param("userId") String userId,
            @Param("memoryType") MemoryType memoryType);

    /**
     * 根据用户ID和factKey查找活跃记忆（用于精确去重）
     */
    Optional<MemoryPackageEntity> findByUserIdAndFactKeyAndIsActiveTrue(String userId, String factKey);

    /**
     * 根据群ID和factKey查找活跃记忆（用于群聊精确去重）
     */
    Optional<MemoryPackageEntity> findByGroupIdAndFactKeyAndIsActiveTrue(String groupId, String factKey);

    /**
     * 查找用户所有Always-Inject类型的活跃记忆（PREFERENCE/IDENTITY/RELATION）
     */
    @Query("SELECT m FROM MemoryPackageEntity m WHERE m.userId = :userId AND m.memoryType IN :types AND m.isActive = true ORDER BY m.weight DESC")
    List<MemoryPackageEntity> findByUserIdAndMemoryTypeIn(
            @Param("userId") String userId,
            @Param("types") List<MemoryType> types);

    /**
     * 查找群所有Always-Inject类型的活跃记忆
     */
    @Query("SELECT m FROM MemoryPackageEntity m WHERE m.groupId = :groupId AND m.memoryType IN :types AND m.isActive = true ORDER BY m.weight DESC")
    List<MemoryPackageEntity> findByGroupIdAndMemoryTypeIn(
            @Param("groupId") String groupId,
            @Param("types") List<MemoryType> types);

    /**
     * 合并查询：一次 SQL 获取用户和群的所有活跃记忆（排除 PERSONA 作用域）。
     * 替代原来的 4 次独立查询（userId+AlwaysInject, groupId+AlwaysInject, userId+Episode, groupId+Episode）。
     */
    @Query("SELECT m FROM MemoryPackageEntity m WHERE (m.userId = :userId OR m.groupId = :groupId) AND m.isActive = true AND m.scope <> 'PERSONA' ORDER BY m.weight DESC")
    List<MemoryPackageEntity> findAllActiveByUserIdOrGroupId(
            @Param("userId") String userId,
            @Param("groupId") String groupId);

    /**
     * 按群ID和时间倒序查找群聊记忆（专用于 Recent Group Context）。
     */
    @Query("SELECT m FROM MemoryPackageEntity m WHERE m.groupId = :groupId AND m.scope = 'GROUP' AND m.isActive = true ORDER BY m.createdAt DESC")
    List<MemoryPackageEntity> findGroupMessagesByCreatedAtDesc(@Param("groupId") String groupId);

    /**
     * 按群ID和 messageId 列表批量查找群聊记忆（用于 Thread 消息内容回填）。
     */
    @Query("SELECT m FROM MemoryPackageEntity m WHERE m.groupId = :groupId AND m.messageId IN :messageIds ORDER BY m.createdAt ASC")
    List<MemoryPackageEntity> findByGroupIdAndMessageIdIn(
            @Param("groupId") String groupId,
            @Param("messageIds") List<String> messageIds);

}