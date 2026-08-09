package com.mcp.common.pipeline;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流水线执行结果 — 汇总所有步骤的执行结果。
 */
public class PipelineResult {
    private String pipelineId;
    private String pipelineName;
    private PipelineStatus status;
    private List<StepResult> stepResults;
    private Map<String, Object> outputs;
    private Instant startedAt;
    private Instant completedAt;
    private long totalDurationMs;
    private int totalSteps;
    private int successSteps;
    private int failedSteps;

    public PipelineResult() {
        this.stepResults = new ArrayList<>();
        this.outputs = new LinkedHashMap<>();
        this.startedAt = Instant.now();
    }

    public static PipelineResult success(String pipelineId, String pipelineName, List<StepResult> steps) {
        PipelineResult r = new PipelineResult();
        r.pipelineId = pipelineId;
        r.pipelineName = pipelineName;
        r.status = PipelineStatus.COMPLETED;
        r.stepResults = steps;
        r.completedAt = Instant.now();
        r.computeStats();
        return r;
    }

    public static PipelineResult failure(String pipelineId, String pipelineName, List<StepResult> steps) {
        PipelineResult r = new PipelineResult();
        r.pipelineId = pipelineId;
        r.pipelineName = pipelineName;
        r.status = PipelineStatus.FAILED;
        r.stepResults = steps;
        r.completedAt = Instant.now();
        r.computeStats();
        return r;
    }

    private void computeStats() {
        this.totalSteps = stepResults.size();
        this.successSteps = (int) stepResults.stream().filter(StepResult::isSuccess).count();
        this.failedSteps = totalSteps - successSteps;
        this.totalDurationMs = Duration.between(startedAt, completedAt).toMillis();

        for (StepResult step : stepResults) {
            if (step.isSuccess() && step.getOutput() != null) {
                outputs.put(step.getStepId(), step.getOutput());
            }
        }
    }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public String getPipelineName() { return pipelineName; }
    public void setPipelineName(String pipelineName) { this.pipelineName = pipelineName; }

    public PipelineStatus getStatus() { return status; }
    public void setStatus(PipelineStatus status) { this.status = status; }

    public List<StepResult> getStepResults() { return stepResults; }
    public void setStepResults(List<StepResult> stepResults) { this.stepResults = stepResults; }

    public Map<String, Object> getOutputs() { return outputs; }
    public void setOutputs(Map<String, Object> outputs) { this.outputs = outputs; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }

    public int getTotalSteps() { return totalSteps; }
    public void setTotalSteps(int totalSteps) { this.totalSteps = totalSteps; }

    public int getSuccessSteps() { return successSteps; }
    public void setSuccessSteps(int successSteps) { this.successSteps = successSteps; }

    public int getFailedSteps() { return failedSteps; }
    public void setFailedSteps(int failedSteps) { this.failedSteps = failedSteps; }

    @Override
    public String toString() {
        return "PipelineResult{" +
                "pipeline='" + pipelineName + '\'' +
                ", status=" + status +
                ", steps=" + successSteps + "/" + totalSteps + " success" +
                (failedSteps > 0 ? ", " + failedSteps + " failed" : "") +
                ", duration=" + totalDurationMs + "ms" +
                '}';
    }
}