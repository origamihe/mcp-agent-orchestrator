package com.mcp.engine.agent;

import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Getter
public class ExecutionTracker {

    private final Instant startTime = Instant.now();
    private final List<ToolObservation> observations = new CopyOnWriteArrayList<>();
    private final List<Long> matchedSkillIds = new ArrayList<>();
    private final List<Long> matchedFailureIds = new ArrayList<>();

    public void recordToolCall(String toolName, String arguments, boolean success,
                               String resultSummary, String errorMessage, long durationMs) {
        recordToolCall(toolName, arguments, success, resultSummary, errorMessage, durationMs, null);
    }

    public void recordToolCall(String toolName, String arguments, boolean success,
                               String resultSummary, String errorMessage, long durationMs,
                               String toolCallId) {
        observations.add(new ToolObservation(
                toolName, arguments, success, resultSummary, errorMessage, durationMs, toolCallId));
    }

    public void addMatchedSkill(Long skillId) {
        if (skillId != null) {
            matchedSkillIds.add(skillId);
        }
    }

    public void addMatchedFailure(Long failureId) {
        if (failureId != null) {
            matchedFailureIds.add(failureId);
        }
    }

    public String buildExecutionSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("执行时长: ").append(Duration.between(startTime, Instant.now()).toMillis())
                .append("ms\n");

        long successCount = observations.stream().filter(ToolObservation::success).count();
        long failureCount = observations.size() - successCount;

        sb.append("工具调用: ").append(observations.size()).append(" 次");
        sb.append(" (成功: ").append(successCount).append(", 失败: ").append(failureCount).append(")\n");

        if (!observations.isEmpty()) {
            sb.append("详细记录:\n");
            for (int i = 0; i < observations.size(); i++) {
                ToolObservation obs = observations.get(i);
                sb.append("  [").append(i + 1).append("] ")
                        .append(obs.toolName).append("(")
                        .append(truncate(obs.arguments, 100)).append(")");
                sb.append(" → ").append(obs.success ? "成功" : "失败");
                sb.append(" (").append(obs.durationMs).append("ms)");
                if (!obs.success && obs.errorMessage != null) {
                    sb.append(" 错误: ").append(truncate(obs.errorMessage, 200));
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public String buildToolsUsedSummary() {
        if (observations.isEmpty()) {
            return "无";
        }
        return observations.stream()
                .map(o -> o.toolName)
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("无");
    }

    public List<String> buildToolsUsedList() {
        if (observations.isEmpty()) {
            return List.of();
        }
        return observations.stream()
                .map(o -> o.toolName)
                .distinct()
                .collect(Collectors.toList());
    }

    public String buildErrorSummary() {
        List<ToolObservation> failures = observations.stream()
                .filter(o -> !o.success)
                .toList();
        if (failures.isEmpty()) {
            return null;
        }
        return failures.stream()
                .map(o -> o.toolName + ": " + (o.errorMessage != null ? o.errorMessage : "未知错误"))
                .reduce((a, b) -> a + "; " + b)
                .orElse(null);
    }

    public boolean hasFailures() {
        return observations.stream().anyMatch(o -> !o.success);
    }

    public long getTotalElapsedMs() {
        return Duration.between(startTime, Instant.now()).toMillis();
    }

    public boolean hasParseFailures() {
        return observations.stream().anyMatch(o -> !o.success
                && o.errorMessage != null
                && (o.errorMessage.contains("解析") || o.errorMessage.contains("parse")
                    || o.errorMessage.contains("extract") || o.errorMessage.contains("unwrap")));
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }

    public record ToolObservation(
            String toolName,
            String arguments,
            boolean success,
            String resultSummary,
            String errorMessage,
            long durationMs,
            String toolCallId
    ) {}
}