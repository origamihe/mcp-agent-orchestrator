package com.mcp.common.pipeline;

import java.time.Instant;

/**
 * 步骤执行结果 — 记录单个步骤的执行详情。
 */
public class StepResult {
    private String stepId;
    private String toolName;
    private boolean success;
    private Object output;
    private String errorMessage;
    private String fallbackToolUsed;
    private int attemptCount;
    private Instant startedAt;
    private Instant completedAt;
    private long durationMs;

    public StepResult() {
        this.startedAt = Instant.now();
    }

    public static StepResult success(String stepId, String toolName, Object output, long durationMs) {
        StepResult r = new StepResult();
        r.stepId = stepId;
        r.toolName = toolName;
        r.success = true;
        r.output = output;
        r.durationMs = durationMs;
        r.completedAt = Instant.now();
        return r;
    }

    public static StepResult failure(String stepId, String toolName, String errorMessage, long durationMs) {
        StepResult r = new StepResult();
        r.stepId = stepId;
        r.toolName = toolName;
        r.success = false;
        r.errorMessage = errorMessage;
        r.durationMs = durationMs;
        r.completedAt = Instant.now();
        return r;
    }

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public Object getOutput() { return output; }
    public void setOutput(Object output) { this.output = output; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getFallbackToolUsed() { return fallbackToolUsed; }
    public void setFallbackToolUsed(String fallbackToolUsed) { this.fallbackToolUsed = fallbackToolUsed; }

    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    @Override
    public String toString() {
        return "StepResult{" +
                "step='" + stepId + '\'' +
                ", tool='" + toolName + '\'' +
                ", success=" + success +
                ", duration=" + durationMs + "ms" +
                (fallbackToolUsed != null ? ", fallback='" + fallbackToolUsed + '\'' : "") +
                (errorMessage != null ? ", error='" + errorMessage + '\'' : "") +
                '}';
    }
}