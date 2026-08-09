package com.mcp.engine.retry;

import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Supplier;

public class RetryTask {

    public enum TaskType {
        MEMORY_LIFECYCLE,
        REFLECTION
    }

    private final String taskId;
    private final String sessionId;
    private final String userId;
    private final TaskType taskType;
    private final Supplier<Mono<Void>> action;
    private final int maxRetries;
    private final long createTime;
    private volatile int retryCount;

    private RetryTask(Builder builder) {
        this.taskId = UUID.randomUUID().toString();
        this.sessionId = builder.sessionId;
        this.userId = builder.userId;
        this.taskType = builder.taskType;
        this.action = builder.action;
        this.maxRetries = builder.maxRetries;
        this.createTime = System.currentTimeMillis();
        this.retryCount = 0;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public Supplier<Mono<Void>> getAction() {
        return action;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sessionId;
        private String userId;
        private TaskType taskType;
        private Supplier<Mono<Void>> action;
        private int maxRetries = 3;

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder taskType(TaskType taskType) {
            this.taskType = taskType;
            return this;
        }

        public Builder action(Supplier<Mono<Void>> action) {
            this.action = action;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public RetryTask build() {
            if (sessionId == null) {
                throw new IllegalArgumentException("sessionId is required");
            }
            if (taskType == null) {
                throw new IllegalArgumentException("taskType is required");
            }
            if (action == null) {
                throw new IllegalArgumentException("action is required");
            }
            return new RetryTask(this);
        }
    }
}