package com.mcp.core.repository;

import com.mcp.core.domain.chat.MessageRole;
import com.mcp.core.entity.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 聊天消息 Repository
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    /**
     * 根据会话ID查找所有消息（按时间排序）
     */
    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * 查找最近 N 条消息
     */
    List<ChatMessageEntity> findTopNBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);

    /**
     * 根据角色过滤消息
     */
    List<ChatMessageEntity> findBySessionIdAndRoleOrderByCreatedAtAsc(String sessionId, MessageRole role);

    /**
     * 统计会话消息数量
     */
    long countBySessionId(String sessionId);

    /**
     * 获取会话的第一条消息
     */
    ChatMessageEntity findFirstBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * 分页查询会话消息（推荐用于大消息量场景）
     */
    @Query("SELECT m FROM ChatMessageEntity m WHERE m.sessionId = :sessionId ORDER BY m.createdAt ASC")
    List<ChatMessageEntity> findBySessionIdWithPage(@Param("sessionId") String sessionId, Pageable pageable);

    /**
     * 查找最近一条 Assistant 回复（用于上下文构建）
     */
    @Query("SELECT m FROM ChatMessageEntity m " +
            "WHERE m.sessionId = :sessionId AND m.role = 'ASSISTANT' " +
            "ORDER BY m.createdAt DESC LIMIT 1")
    ChatMessageEntity findLatestAssistantMessage(@Param("sessionId") String sessionId);

    /**
     * 删除会话的所有消息
     */
    @Modifying
    @Transactional
    void deleteBySessionId(@Param("sessionId") String sessionId);
}