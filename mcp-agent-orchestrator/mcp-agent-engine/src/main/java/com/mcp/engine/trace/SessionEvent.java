package com.mcp.engine.trace;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only 会话事件 — 记录 Agent 执行过程中模型看到的每一步。
 *
 * 与 {@link TraceRecord}（最终快照）不同，SessionEvent 是事件流，
 * 每个事件记录了执行管线中某个特定节点的输入/输出/决策。
 *
 * 事件类型覆盖完整执行链路：
 * <pre>
 * Session
 *   ├── USER_MESSAGE          — 用户原始输入
 *   ├── CONTEXT_CLASSIFICATION — ContextRequirement 判定结果
 *   ├── AGENT_SELECTION       — 选中的 Agent 类型
 *   ├── CONTEXT_INJECTION     — 注入的上下文（Memory/Artifact/Workspace/GroupConversation）
 *   ├── SYSTEM_PROMPT         — 最终 System Prompt（含所有分层）
 *   ├── TOOL_DECISION         — LLM 决定调用工具
 *   ├── TOOL_CALL             — 工具调用请求
 *   ├── TOOL_RESULT           — 工具调用结果
 *   ├── MEMORY_INJECTION      — 记忆注入
 *   ├── SUBAGENT_SCHEDULE     — 子 Agent 调度
 *   ├── LLM_RESPONSE          — LLM 最终响应
 *   ├── COMPRESSION           — 上下文压缩事件
 *   ├── CONTRACT_VIOLATION    — 执行契约违规
 *   └── FINAL_RESPONSE        — 最终返回给用户的响应
 * </pre>
 *
 * 设计原则：
 * - 只追加，不修改，不删除
 * - 每个事件自包含（含足够上下文用于回放）
 * - payload 使用 Map 保持灵活性，同时记录关键结构化字段
 */
public record SessionEvent(
        String eventId,
        String sessionId,
        String traceId,
        String parentEventId,
        SessionEventType eventType,
        Instant timestamp,
        int sequence,
        Map<String, Object> payload,
        long elapsedMsSinceStart
) {

    public static SessionEventBuilder builder() {
        return new SessionEventBuilder();
    }

    public static final class SessionEventBuilder {
        private String eventId = UUID.randomUUID().toString();
        private String sessionId;
        private String traceId;
        private String parentEventId;
        private SessionEventType eventType;
        private Instant timestamp = Instant.now();
        private int sequence;
        private Map<String, Object> payload = Map.of();
        private long elapsedMsSinceStart;

        public SessionEventBuilder eventId(String eventId) { this.eventId = eventId; return this; }
        public SessionEventBuilder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public SessionEventBuilder traceId(String traceId) { this.traceId = traceId; return this; }
        public SessionEventBuilder parentEventId(String parentEventId) { this.parentEventId = parentEventId; return this; }
        public SessionEventBuilder eventType(SessionEventType eventType) { this.eventType = eventType; return this; }
        public SessionEventBuilder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public SessionEventBuilder sequence(int sequence) { this.sequence = sequence; return this; }
        public SessionEventBuilder payload(Map<String, Object> payload) { this.payload = payload; return this; }
        public SessionEventBuilder elapsedMsSinceStart(long elapsedMsSinceStart) { this.elapsedMsSinceStart = elapsedMsSinceStart; return this; }

        public SessionEvent build() {
            if (sessionId == null) throw new IllegalStateException("sessionId is required");
            if (traceId == null) throw new IllegalStateException("traceId is required");
            if (eventType == null) throw new IllegalStateException("eventType is required");
            return new SessionEvent(eventId, sessionId, traceId, parentEventId, eventType, timestamp, sequence, payload, elapsedMsSinceStart);
        }
    }
}