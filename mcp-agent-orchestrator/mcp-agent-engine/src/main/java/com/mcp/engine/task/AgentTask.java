package com.mcp.engine.task;

import com.mcp.common.identity.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Agent 任务 — 代表一个待处理或正在处理的 Agent 请求。
 *
 * 状态机：QUEUED → CLASSIFYING → RUNNING → COMPLETED / INTERRUPTED / FAILED
 */
@Data
@Builder
public class AgentTask {

    private String taskId;
    private String sessionId;
    private String groupId;
    private String userId;
    private String userName;
    private UserRole userRole;
    private String messageContent;
    private String threadId;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private TaskStatus status;
    private int priority;
    private String priorityReason;
    private int retryCount;
    private String errorMessage;

    public enum TaskStatus {
        QUEUED, CLASSIFYING, RUNNING, COMPLETED, INTERRUPTED, FAILED
    }

    public static AgentTask create(String sessionId, String groupId, String userId,
                                    String userName, UserRole userRole,
                                    String messageContent, String threadId,
                                    int priority, String priorityReason) {
        return AgentTask.builder()
                .taskId("task-" + groupId + "-" + System.currentTimeMillis())
                .sessionId(sessionId)
                .groupId(groupId)
                .userId(userId)
                .userName(userName)
                .userRole(userRole)
                .messageContent(messageContent)
                .threadId(threadId)
                .createdAt(Instant.now())
                .status(TaskStatus.QUEUED)
                .priority(priority)
                .priorityReason(priorityReason)
                .retryCount(0)
                .build();
    }

    public boolean isRunning() {
        return status == TaskStatus.RUNNING;
    }

    public boolean isCompleted() {
        return status == TaskStatus.COMPLETED || status == TaskStatus.FAILED;
    }

    public long getAgeMillis() {
        return Instant.now().toEpochMilli() - createdAt.toEpochMilli();
    }

    public long getDurationMillis() {
        if (startedAt == null) return 0;
        Instant end = completedAt != null ? completedAt : Instant.now();
        return end.toEpochMilli() - startedAt.toEpochMilli();
    }
}