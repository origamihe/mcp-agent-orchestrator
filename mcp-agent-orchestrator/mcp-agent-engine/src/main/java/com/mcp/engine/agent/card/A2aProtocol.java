package com.mcp.engine.agent.card;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A2A 协议 - Agent 间消息传递
 */
public interface A2aProtocol {

    /** Agent 间任务请求 */
    @Data
    @Builder
    class AgentTaskRequest {
        @Builder.Default
        private String taskId = UUID.randomUUID().toString();
        private String fromAgentId;
        private String toAgentId;
        private String task;
        private String context;
        private Map<String, Object> params;
        @Builder.Default
        private long timestamp = Instant.now().toEpochMilli();
    }

    /** Agent 间任务响应 */
    @Data
    @Builder
    class AgentTaskResponse {
        private String taskId;
        private String fromAgentId;
        private String toAgentId;
        private String result;
        private boolean success;
        private String errorMessage;
        @Builder.Default
        private long timestamp = Instant.now().toEpochMilli();
    }

    /** Agent 间广播消息 */
    @Data
    @Builder
    class AgentBroadcast {
        @Builder.Default
        private String messageId = UUID.randomUUID().toString();
        private String fromAgentId;
        private String topic;
        private String payload;
        @Builder.Default
        private long timestamp = Instant.now().toEpochMilli();
    }
}