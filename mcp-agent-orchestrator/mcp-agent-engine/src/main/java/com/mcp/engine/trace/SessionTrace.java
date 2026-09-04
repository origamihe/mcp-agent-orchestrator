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

    public void recordRequestReceived(String requestSummary, int requestLength) {
        record(SessionEventType.REQUEST_RECEIVED, Map.of(
                "requestSummary", requestSummary,
                "requestLength", requestLength
        ));
    }

    public void recordPlanCreated(String planType, int stepCount, String planSummary) {
        record(SessionEventType.PLAN_CREATED, Map.of(
                "planType", planType,
                "stepCount", stepCount,
                "planSummary", planSummary != null ? planSummary : ""
        ));
    }

    public void recordContextBuilt(int fileContextCount, int totalTokens, String budgetInfo) {
        record(SessionEventType.CONTEXT_BUILT, Map.of(
                "fileContextCount", fileContextCount,
                "totalTokens", totalTokens,
                "budgetInfo", budgetInfo != null ? budgetInfo : ""
        ));
    }

    public void recordAgentStarted(String agentName, String agentType) {
        record(SessionEventType.AGENT_STARTED, Map.of(
                "agentName", agentName,
                "agentType", agentType
        ));
    }

    public void recordAgentIteration(int iteration, int toolCallCount, String summary) {
        record(SessionEventType.AGENT_ITERATION, Map.of(
                "iteration", iteration,
                "toolCallCount", toolCallCount,
                "summary", summary != null ? summary : ""
        ));
    }

    public void recordPipelineStep(String pipelineId, String stepName, int stepIndex, boolean success) {
        record(SessionEventType.PIPELINE_STEP, Map.of(
                "pipelineId", pipelineId,
                "stepName", stepName,
                "stepIndex", stepIndex,
                "success", success
        ));
    }

    public void recordMemoryRead(String memoryType, int itemsRead) {
        record(SessionEventType.MEMORY_READ, Map.of(
                "memoryType", memoryType,
                "itemsRead", itemsRead
        ));
    }

    public void recordMemoryWrite(String memoryType, int itemsWritten) {
        record(SessionEventType.MEMORY_WRITE, Map.of(
                "memoryType", memoryType,
                "itemsWritten", itemsWritten
        ));
    }

    public void recordPolicyDecision(String capability, String decision, String reason) {
        record(SessionEventType.POLICY_DECISION, Map.of(
                "capability", capability,
                "decision", decision,
                "reason", reason != null ? reason : ""
        ));
    }

    public void recordLlmCall(String modelName, int promptTokens, int responseTokens) {
        record(SessionEventType.LLM_CALL, Map.of(
                "modelName", modelName != null ? modelName : "unknown",
                "promptTokens", promptTokens,
                "responseTokens", responseTokens
        ));
    }

    public void recordArtifactCreated(String artifactType, String path, int size) {
        record(SessionEventType.ARTIFACT_CREATED, Map.of(
                "artifactType", artifactType,
                "path", path != null ? path : "",
                "size", size
        ));
    }

    public void recordExecutionCompleted(String status, long totalElapsedMs, int totalEvents) {
        record(SessionEventType.EXECUTION_COMPLETED, Map.of(
                "status", status,
                "totalElapsedMs", totalElapsedMs,
                "totalEvents", totalEvents
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
        recordExecutionCompleted("COMPLETED",
                java.time.Duration.between(startTime, Instant.now()).toMillis(),
                getEventCount());
        recordFinalResponse(-1, java.time.Duration.between(startTime, Instant.now()).toMillis());
    }
}