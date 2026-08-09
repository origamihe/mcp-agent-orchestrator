package com.mcp.tools.model;

import java.time.Instant;

public record ToolStats(
        String toolName,
        long callCount,
        long successCount,
        long failCount,
        long avgDurationMs,
        long maxDurationMs,
        long minDurationMs,
        Instant lastUsed,
        Instant lastSuccess,
        String lastError
) {
    public double successRate() {
        return callCount > 0 ? (double) successCount / callCount * 100 : 0;
    }

    public static ToolStats empty(String toolName) {
        return new ToolStats(toolName, 0, 0, 0, 0, 0, 0, null, null, null);
    }

    public ToolStats withExecution(boolean success, long durationMs, String error) {
        long newCallCount = callCount + 1;
        long newSuccessCount = successCount + (success ? 1 : 0);
        long newFailCount = failCount + (success ? 0 : 1);
        long newAvgDuration = callCount > 0
                ? (avgDurationMs * callCount + durationMs) / newCallCount
                : durationMs;
        long newMaxDuration = Math.max(maxDurationMs, durationMs);
        long newMinDuration = callCount > 0 ? Math.min(minDurationMs, durationMs) : durationMs;
        Instant now = Instant.now();

        return new ToolStats(
                toolName,
                newCallCount,
                newSuccessCount,
                newFailCount,
                newAvgDuration,
                newMaxDuration,
                newMinDuration,
                now,
                success ? now : lastSuccess,
                success ? lastError : error
        );
    }
}