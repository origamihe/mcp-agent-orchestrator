package com.mcp.engine.agent;

import com.mcp.engine.planner.EditPlan;
import com.mcp.engine.planner.PlanStep;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 执行上下文 —— 记录当前任务的完整执行状态。
 * 与 ExecutionTracker 不同，ExecutionContext 关注的是 Planner 决策层面的信息，
 * 而 ExecutionTracker 关注的是工具调用层面的统计。
 * <p>
 * 核心价值：
 * 1. 防止 Planner 重复犯同一个错误（hasAlreadyFailed）
 * 2. 为 Reflection 提供 Planner 决策的完整上下文
 * 3. 支持重规划（replan）时保留历史信息
 */
@Data
public class ExecutionContext {

    private final String taskId;
    private final String goal;
    private final Instant startTime;

    private EditPlan currentPlan;
    private int currentStepIndex;
    private int maxRetries;

    private final List<StepResult> completedSteps = new CopyOnWriteArrayList<>();
    private final List<StepResult> failedSteps = new CopyOnWriteArrayList<>();
    private final List<String> observations = new CopyOnWriteArrayList<>();
    private final List<ToolCallRecord> toolHistory = new CopyOnWriteArrayList<>();

    private String currentHypothesis;
    private String workspaceSnapshot;

    private ExecutionContext(String taskId, String goal, EditPlan currentPlan,
                             int maxRetries, String currentHypothesis, String workspaceSnapshot) {
        this.taskId = taskId;
        this.goal = goal;
        this.startTime = Instant.now();
        this.currentPlan = currentPlan;
        this.currentStepIndex = 0;
        this.maxRetries = maxRetries;
        this.currentHypothesis = currentHypothesis;
        this.workspaceSnapshot = workspaceSnapshot;
    }

    public static ExecutionContext create(String taskId, String goal, EditPlan plan) {
        return new ExecutionContext(taskId, goal, plan, 3, null, null);
    }

    public static ExecutionContext create(String taskId, String goal, EditPlan plan,
                                          int maxRetries) {
        return new ExecutionContext(taskId, goal, plan, maxRetries, null, null);
    }

    // ==================== 失败检测 ====================

    /**
     * 检查是否已经用相同的工具名和关键参数失败过。
     * 用于防止 Planner 在重规划后再次尝试相同的失败路径。
     */
    public boolean hasAlreadyFailed(String toolName, String keyArgument) {
        return failedSteps.stream().anyMatch(f ->
                f.step != null
                        && f.step.getToolName() != null
                        && f.step.getToolName().equals(toolName)
                        && (keyArgument == null || containsKeyArgument(f.step.getArguments(), keyArgument))
        ) || toolHistory.stream().anyMatch(r ->
                !r.success
                        && r.toolName != null
                        && r.toolName.equals(toolName)
                        && (keyArgument == null || containsKeyArgument(r.arguments, keyArgument))
        );
    }

    /**
     * 检查某个工具是否已经失败过（按工具名）。
     */
    public boolean hasAlreadyFailed(String toolName) {
        return hasAlreadyFailed(toolName, null);
    }

    private boolean containsKeyArgument(Map<String, Object> arguments, String keyArgument) {
        if (arguments == null || keyArgument == null) {
            return false;
        }
        return arguments.values().stream()
                .anyMatch(v -> v != null && v.toString().contains(keyArgument));
    }

    // ==================== 步骤记录 ====================

    public void recordStepSuccess(PlanStep step, String result, long durationMs) {
        completedSteps.add(new StepResult(step, true, result, null, durationMs, 1));
        currentStepIndex++;
    }

    public void recordStepFailure(PlanStep step, String error, long durationMs, int attemptNumber) {
        failedSteps.add(new StepResult(step, false, null, error, durationMs, attemptNumber));
    }

    public void recordToolCall(String toolName, Map<String, Object> arguments,
                               boolean success, String resultSummary,
                               String errorMessage, long durationMs) {
        toolHistory.add(new ToolCallRecord(
                toolName, arguments, success, resultSummary, errorMessage, durationMs));
    }

    public void addObservation(String observation) {
        observations.add(observation);
    }

    // ==================== 状态查询 ====================

    public boolean shouldRetry() {
        return failedSteps.size() < maxRetries;
    }

    public int totalRetriesUsed() {
        return (int) failedSteps.stream()
                .filter(f -> f.attemptNumber > 1)
                .count();
    }

    public int totalFailedCount() {
        return failedSteps.size();
    }

    public int totalCompletedCount() {
        return completedSteps.size();
    }

    public long elapsedMs() {
        return Duration.between(startTime, Instant.now()).toMillis();
    }

    // ==================== Planner 上下文构建 ====================

    /**
     * 构建供 Planner 使用的上下文摘要。
     * 包含已完成步骤、失败步骤、当前假设等，帮助 Planner 做出更好的决策。
     */
    public String buildContextForPlanner() {
        StringBuilder sb = new StringBuilder();
        sb.append("任务目标: ").append(goal).append("\n");
        sb.append("已执行: ").append(elapsedMs()).append("ms\n");

        if (!completedSteps.isEmpty()) {
            sb.append("已完成步骤:\n");
            for (int i = 0; i < completedSteps.size(); i++) {
                StepResult r = completedSteps.get(i);
                sb.append("  [").append(i + 1).append("] ")
                        .append(r.step.getDisplayName()).append(" → 成功 (")
                        .append(r.durationMs).append("ms)\n");
            }
        }

        if (!failedSteps.isEmpty()) {
            sb.append("失败步骤 (请勿重复尝试):\n");
            for (StepResult r : failedSteps) {
                sb.append("  ✗ ").append(r.step.getDisplayName());
                if (r.step.getArguments() != null && !r.step.getArguments().isEmpty()) {
                    sb.append("(").append(truncate(r.step.getArguments().toString(), 80)).append(")");
                }
                sb.append(" → ").append(truncate(r.errorMessage, 100)).append("\n");
            }
        }

        if (currentHypothesis != null) {
            sb.append("当前假设: ").append(currentHypothesis).append("\n");
        }

        return sb.toString();
    }

    /**
     * 构建供 Reflection 使用的完整执行摘要。
     */
    public String buildReflectionSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 执行摘要 ===\n");
        sb.append("任务: ").append(goal).append("\n");
        sb.append("耗时: ").append(elapsedMs()).append("ms\n");
        sb.append("计划类型: ").append(currentPlan != null ? currentPlan.getPlanType() : "N/A").append("\n");
        sb.append("Planner 推理: ").append(currentPlan != null ? currentPlan.getReasoning() : "N/A").append("\n");
        sb.append("完成: ").append(totalCompletedCount()).append(" 步\n");
        sb.append("失败: ").append(totalFailedCount()).append(" 步\n");
        sb.append("重试: ").append(totalRetriesUsed()).append(" 次\n");

        if (currentHypothesis != null) {
            sb.append("假设: ").append(currentHypothesis).append("\n");
        }

        sb.append("\n工具调用历史:\n");
        for (int i = 0; i < toolHistory.size(); i++) {
            ToolCallRecord r = toolHistory.get(i);
            sb.append("  [").append(i + 1).append("] ")
                    .append(r.toolName).append(" → ")
                    .append(r.success ? "成功" : "失败")
                    .append(" (").append(r.durationMs).append("ms)\n");
        }

        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }

    // ==================== 内部记录类型 ====================

    public record StepResult(
            PlanStep step,
            boolean success,
            String resultSummary,
            String errorMessage,
            long durationMs,
            int attemptNumber
    ) {}

    public record ToolCallRecord(
            String toolName,
            Map<String, Object> arguments,
            boolean success,
            String resultSummary,
            String errorMessage,
            long durationMs
    ) {}
}