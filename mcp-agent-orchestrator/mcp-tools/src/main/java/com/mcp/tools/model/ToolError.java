package com.mcp.tools.model;

import java.time.Duration;
import java.util.Map;

/**
 * 结构化 Tool 错误 — 替代 String error 字段。
 *
 * 支持：
 * - 错误码分类（NETWORK / TIMEOUT / PERMISSION / VALIDATION / INTERNAL / SANDBOX）
 * - 可恢复性标记
 * - 重试建议
 */
public record ToolError(
        String errorCode,
        String message,
        String detail,
        boolean recoverable,
        int suggestedRetryAfterSeconds
) {
    public static ToolError of(String errorCode, String message) {
        return new ToolError(errorCode, message, null, false, 0);
    }

    public static ToolError of(String errorCode, String message, String detail) {
        return new ToolError(errorCode, message, detail, false, 0);
    }

    public static ToolError network(String message) {
        return new ToolError("NETWORK_ERROR", message, null, true, 3);
    }

    public static ToolError timeout(String message) {
        return new ToolError("TIMEOUT", message, null, true, 5);
    }

    public static ToolError permission(String message) {
        return new ToolError("PERMISSION_DENIED", message, null, false, 0);
    }

    public static ToolError validation(String message) {
        return new ToolError("VALIDATION_ERROR", message, null, false, 0);
    }

    public static ToolError internal(String message) {
        return new ToolError("INTERNAL_ERROR", message, null, false, 0);
    }

    public static ToolError sandbox(String message) {
        return new ToolError("SANDBOX_VIOLATION", message, null, false, 0);
    }

    @Override
    public String toString() {
        return "[" + errorCode + "] " + message + (detail != null ? " | " + detail : "");
    }
}