package com.mcp.engine.execution;

import com.mcp.common.execution.ExecutionStatus;
import com.mcp.common.execution.ToolExecutionCallback;
import com.mcp.engine.trace.SessionTrace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 执行状态 — P1 核心类，统一管理 Agent 执行的完整生命周期状态。
 *
 * ExecutionPlan 解决"我要怎么执行？"，ExecutionState 解决"我现在执行到哪里？"
 *
 * 状态机：
 * <pre>
 * CREATED → PLANNING → RUNNING → WAITING_TOOL → RUNNING → COMPLETED
 *                                      ↓
 *                                  FAILED / CANCELLED
 * </pre>
 *
 * 设计原则：
 * - 只有一份权威状态，Agent/Pipeline/Trace 不各自维护状态
 * - 支持 pendingToolCalls / completedToolResults 的完整追踪
 * - 支持 AgentState 收纳 ExecutionContext 的 Agent 层推理状态
 * - 支持 ContextSnapshot 快照
 * - 支持 FailureState 结构化失败信息
 */
public class ExecutionState implements ToolExecutionCallback {

    private final String executionId;
    private volatile ExecutionStatus status;
    private volatile int iteration;
    private final List<String> pendingToolCalls;
    private final List<String> completedToolResults;
    private volatile String contextSnapshot;
    private volatile AgentState agentState;
    private volatile FailureState failure;
    private final Instant startedAt;
    private volatile Instant updatedAt;
    private volatile SessionTrace trace;

    public ExecutionState(String executionId) {
        this.executionId = executionId;
        this.status = ExecutionStatus.PENDING;
        this.iteration = 0;
        this.pendingToolCalls = Collections.synchronizedList(new ArrayList<>());
        this.completedToolResults = Collections.synchronizedList(new ArrayList<>());
        this.agentState = new AgentState();
        this.startedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void setTrace(SessionTrace trace) {
        this.trace = trace;
    }

    public synchronized void transitionTo(ExecutionStatus newStatus) {
        ExecutionStatus oldStatus = this.status;
        this.status = newStatus;
        this.updatedAt = Instant.now();
        if (trace != null) {
            trace.recordStateTransition(executionId, oldStatus.name(), newStatus.name(),
                    iteration, pendingToolCalls.size(), completedToolResults.size());
        }
    }

    public synchronized void startPlanning() {
        transitionTo(ExecutionStatus.PENDING);
    }

    public synchronized void startRunning() {
        transitionTo(ExecutionStatus.RUNNING);
    }

    public synchronized void waitingForTool(String toolCallId) {
        pendingToolCalls.add(toolCallId);
        transitionTo(ExecutionStatus.WAITING_TOOL);
    }

    public synchronized void toolCompleted(String toolCallId) {
        pendingToolCalls.remove(toolCallId);
        completedToolResults.add(toolCallId);
        this.updatedAt = Instant.now();
    }

    public synchronized void incrementIteration() {
        this.iteration++;
        this.updatedAt = Instant.now();
    }

    public synchronized void markCompleted() {
        transitionTo(ExecutionStatus.SUCCESS);
    }

    public synchronized void markFailed(String reason, String errorCode) {
        this.failure = new FailureState(reason, errorCode, Instant.now());
        transitionTo(ExecutionStatus.EXECUTION_ERROR);
    }

    public synchronized void markCancelled() {
        transitionTo(ExecutionStatus.CANCELLED);
    }

    public synchronized void markTimeout() {
        this.failure = new FailureState("Execution timeout", "TIMEOUT", Instant.now());
        transitionTo(ExecutionStatus.TIMEOUT);
    }

    @Override
    public void onToolStart(String toolCallId) {
        waitingForTool(toolCallId);
    }

    @Override
    public void onToolComplete(String toolCallId, boolean success) {
        toolCompleted(toolCallId);
    }

    public synchronized void setContextSnapshot(String snapshot) {
        this.contextSnapshot = snapshot;
        this.updatedAt = Instant.now();
    }

    public synchronized void setAgentState(AgentState agentState) {
        this.agentState = agentState;
        this.updatedAt = Instant.now();
    }

    public String getExecutionId() { return executionId; }
    public ExecutionStatus getStatus() { return status; }
    public int getIteration() { return iteration; }
    public List<String> getPendingToolCalls() { return Collections.unmodifiableList(pendingToolCalls); }
    public List<String> getCompletedToolResults() { return Collections.unmodifiableList(completedToolResults); }
    public String getContextSnapshot() { return contextSnapshot; }
    public AgentState getAgentState() { return agentState; }
    public FailureState getFailure() { return failure; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    public boolean isRunning() {
        return status == ExecutionStatus.RUNNING || status == ExecutionStatus.PENDING;
    }

    public record FailureState(
            String reason,
            String errorCode,
            Instant occurredAt
    ) {}

    public static class AgentState {
        private volatile String goal;
        private volatile String currentHypothesis;
        private final List<String> completedSteps = new CopyOnWriteArrayList<>();
        private final List<String> failedSteps = new CopyOnWriteArrayList<>();
        private final List<String> agentLocalHistory = new CopyOnWriteArrayList<>();
        private final List<ToolCallRecord> toolHistory = new CopyOnWriteArrayList<>();
        private volatile int currentStepIndex;

        public void setGoal(String goal) { this.goal = goal; }
        public String getGoal() { return goal; }

        public void setCurrentHypothesis(String hypothesis) { this.currentHypothesis = hypothesis; }
        public String getCurrentHypothesis() { return currentHypothesis; }

        public void recordStepSuccess(String step) { completedSteps.add(step); currentStepIndex++; }
        public void recordStepFailure(String step) { failedSteps.add(step); }
        public void addToHistory(String entry) { agentLocalHistory.add(entry); }

        public void recordToolCall(String toolName, Map<String, Object> arguments,
                                    boolean success, String result, String error, long durationMs) {
            toolHistory.add(new ToolCallRecord(toolName, arguments, success, result, error, durationMs));
        }

        public boolean hasAlreadyFailed(String toolName, String keyArgument) {
            return failedSteps.stream().anyMatch(f -> f.contains(toolName))
                    || toolHistory.stream().anyMatch(r ->
                    !r.success && r.toolName != null && r.toolName.equals(toolName)
                            && (keyArgument == null || containsKeyArgument(r.arguments, keyArgument)));
        }

        private boolean containsKeyArgument(Map<String, Object> arguments, String keyArgument) {
            if (arguments == null || keyArgument == null) return false;
            return arguments.values().stream()
                    .anyMatch(v -> v != null && v.toString().contains(keyArgument));
        }

        public List<String> getCompletedSteps() { return Collections.unmodifiableList(completedSteps); }
        public List<String> getFailedSteps() { return Collections.unmodifiableList(failedSteps); }
        public List<String> getAgentLocalHistory() { return Collections.unmodifiableList(agentLocalHistory); }
        public int getCurrentStepIndex() { return currentStepIndex; }
        public int totalCompletedCount() { return completedSteps.size(); }
        public int totalFailedCount() { return failedSteps.size(); }

        public String buildReflectionSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Goal: ").append(goal != null ? goal : "N/A").append("\n");
            sb.append("Completed: ").append(completedSteps.size()).append(", Failed: ").append(failedSteps.size()).append("\n");
            for (String step : completedSteps) {
                sb.append("  [OK] ").append(step).append("\n");
            }
            for (String step : failedSteps) {
                sb.append("  [FAIL] ").append(step).append("\n");
            }
            return sb.toString();
        }
    }

    public record ToolCallRecord(
            String toolName,
            Map<String, Object> arguments,
            boolean success,
            String result,
            String error,
            long durationMs
    ) {}
}