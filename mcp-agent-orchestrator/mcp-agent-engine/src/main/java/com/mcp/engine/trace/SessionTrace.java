package com.mcp.engine.trace;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 会话追踪管理器 — 管理单次 Agent 执行的完整事件流。
 *
 * 职责：
 * 1. 维护 traceId、sequence 计数器
 * 2. 提供便捷的事件记录方法（每个方法对应一个 SessionEventType）
 * 3. 自动计算 elapsedMsSinceStart
 * 4. 将事件追加到 SessionEventStore
 *
 * 生命周期：一次 Agent 执行（从 internalProcess 入口到返回响应）。
 */
public class SessionTrace implements AutoCloseable {

    private final String sessionId;
    private final String traceId;
    private final Instant startTime;
    private final AtomicInteger sequence;
    private final SessionEventStore store;

    public SessionTrace(String sessionId, SessionEventStore store) {
        this.sessionId = sessionId;
        this.traceId = UUID.randomUUID().toString();
        this.startTime = Instant.now();
        this.sequence = new AtomicInteger(0);
        this.store = store;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSessionId() {
        return sessionId;
    }

    private void record(SessionEventType type, Map<String, Object> payload) {
        SessionEvent event = SessionEvent.builder()
                .sessionId(sessionId)
                .traceId(traceId)
                .eventType(type)
                .timestamp(Instant.now())
                .sequence(sequence.getAndIncrement())
                .payload(payload)
                .elapsedMsSinceStart(java.time.Duration.between(startTime, Instant.now()).toMillis())
                .build();
        store.append(event);
    }

    public void recordUserMessage(String userMessage, int messageLength) {
        record(SessionEventType.USER_MESSAGE, Map.of(
                "message", userMessage.length() > 500 ? userMessage.substring(0, 500) + "..." : userMessage,
                "length", messageLength
        ));
    }

    public void recordContextClassification(String requirement, String reason, boolean hasActiveDoc,
                                             boolean isGame, String activeContextSource) {
        record(SessionEventType.CONTEXT_CLASSIFICATION, Map.of(
                "requirement", requirement,
                "reason", reason,
                "hasActiveDoc", hasActiveDoc,
                "isGame", isGame,
                "activeContextSource", activeContextSource
        ));
    }

    public void recordAgentSelection(String agentName, String agentType) {
        record(SessionEventType.AGENT_SELECTION, Map.of(
                "agentName", agentName,
                "agentType", agentType
        ));
    }

    public void recordContextInjection(String contextType, int chars, String summary) {
        record(SessionEventType.CONTEXT_INJECTION, Map.of(
                "contextType", contextType,
                "chars", chars,
                "summary", summary
        ));
    }

    public void recordSystemPrompt(String promptPolicy, int totalChars, int layerCount) {
        record(SessionEventType.SYSTEM_PROMPT, Map.of(
                "promptPolicy", promptPolicy,
                "totalChars", totalChars,
                "layerCount", layerCount
        ));
    }

    public void recordToolDecision(String toolName, boolean hasToolCalls, int toolCount) {
        record(SessionEventType.TOOL_DECISION, Map.of(
                "hasToolCalls", hasToolCalls,
                "toolCount", toolCount,
                "toolName", toolName
        ));
    }

    public void recordToolCall(String toolName, String arguments, int round) {
        record(SessionEventType.TOOL_CALL, Map.of(
                "toolName", toolName,
                "arguments", arguments,
                "round", round
        ));
    }

    public void recordToolResult(String toolName, boolean success, int resultChars, int round, String error) {
        record(SessionEventType.TOOL_RESULT, Map.of(
                "toolName", toolName,
                "success", success,
                "resultChars", resultChars,
                "round", round,
                "error", error != null ? error : ""
        ));
    }

    public void recordMemoryInjection(String memoryType, int chars) {
        record(SessionEventType.MEMORY_INJECTION, Map.of(
                "memoryType", memoryType,
                "chars", chars
        ));
    }

    public void recordSubAgentSchedule(String fromAgent, String toAgent, String reason) {
        record(SessionEventType.SUBAGENT_SCHEDULE, Map.of(
                "fromAgent", fromAgent,
                "toAgent", toAgent,
                "reason", reason
        ));
    }

    public void recordLlmResponse(int responseChars, String modelName, long elapsedMs) {
        record(SessionEventType.LLM_RESPONSE, Map.of(
                "responseChars", responseChars,
                "modelName", modelName,
                "elapsedMs", elapsedMs
        ));
    }

    public void recordCompression(String strategy, int beforeChars, int afterChars) {
        record(SessionEventType.COMPRESSION, Map.of(
                "strategy", strategy,
                "beforeChars", beforeChars,
                "afterChars", afterChars
        ));
    }

    public void recordContractViolation(String contractName, String expected, String actual, String detail) {
        record(SessionEventType.CONTRACT_VIOLATION, Map.of(
                "contractName", contractName,
                "expected", expected,
                "actual", actual,
                "detail", detail
        ));
    }

    public void recordFinalResponse(int responseChars, long totalElapsedMs) {
        record(SessionEventType.FINAL_RESPONSE, Map.of(
                "responseChars", responseChars,
                "totalElapsedMs", totalElapsedMs
        ));
    }

    public List<SessionEvent> getEvents() {
        return store.getByTraceId(traceId);
    }

    public int getEventCount() {
        return store.getByTraceId(traceId).size();
    }

    @Override
    public void close() {
        recordFinalResponse(-1, java.time.Duration.between(startTime, Instant.now()).toMillis());
    }
}