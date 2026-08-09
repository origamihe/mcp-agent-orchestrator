package com.mcp.tools.model;

import java.time.Instant;

public record HealthCheckResult(
        String toolName,
        boolean healthy,
        String message,
        long responseTimeMs,
        Instant checkedAt
) {
    public static HealthCheckResult healthy(String toolName, long responseTimeMs) {
        return new HealthCheckResult(toolName, true, "OK", responseTimeMs, Instant.now());
    }

    public static HealthCheckResult unhealthy(String toolName, String reason) {
        return new HealthCheckResult(toolName, false, reason, -1, Instant.now());
    }

    public static HealthCheckResult unknown(String toolName) {
        return new HealthCheckResult(toolName, false, "Health check not implemented", -1, Instant.now());
    }
}