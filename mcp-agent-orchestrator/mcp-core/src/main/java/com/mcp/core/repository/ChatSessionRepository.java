package com.mcp.core.repository;

import com.mcp.core.entity.ChatSessionEntity;
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
 * 聊天会话 Repository
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, String> {

    /**
     * 根据用户ID查找所有会话
     */
    List<ChatSessionEntity> findByUserId(String userId);

    /**
     * 查找最近活跃的会话
     */
    List<ChatSessionEntity> findByUserIdOrderByLastActiveAtDesc(String userId);

    /**
     * 查找指定时间内活跃的会话
     */
    List<ChatSessionEntity> findByLastActiveAtAfter(LocalDateTime time);

    /**
     * 更新会话最后活跃时间
     */
    @Modifying
    @Transactional
    @Query("UPDATE ChatSessionEntity s SET s.lastActiveAt = :now WHERE s.sessionId = :sessionId")
    int updateLastActiveTime(@Param("sessionId") String sessionId, @Param("now") LocalDateTime now);

    /**
     * 查找包含特定消息的会话（关联查询）
     */
    @Query("SELECT DISTINCT s FROM ChatSessionEntity s JOIN s.messages m WHERE m.sessionId = :sessionId")
    Optional<ChatSessionEntity> findWithMessages(@Param("sessionId") String sessionId);

    /**
     * 根据 Agent ID 查找所有会话
     */
    List<ChatSessionEntity> findByAgentIdOrderByLastActiveAtDesc(String agentId);

    /**
     * 根据 Agent ID 和状态查找会话
     */
    List<ChatSessionEntity> findByAgentIdAndStatusOrderByLastActiveAtDesc(String agentId, String status);
}