package com.mcp.tools.model;

import com.mcp.common.execution.ExecutionStatus;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一 Tool 执行结果 — P1 升级版，替代简单的 boolean success + String error。
 *
 * 设计原则：
 * - ExecutionStatus 替换 boolean success，支持 9 种细粒度状态
 * - ToolError 替换 String error，支持结构化错误分类
 * - Duration 记录执行耗时，支撑性能分析
 * - metadata 提供扩展点，不污染核心字段
 * - 保持与旧 ToolResult 的互转（toLegacy / fromLegacy）
 */
public record ToolExecutionResult(
        String toolCallId,
        String toolName,
        ExecutionStatus status,
        Object data,
        ToolError error,
        Duration duration,
        Map<String, Object> metadata
) {

    public static ToolExecutionResult success(String toolCallId, String toolName, Object data, Duration duration) {
        return new ToolExecutionResult(
                toolCallId, toolName, ExecutionStatus.SUCCESS,
                data, null, duration, Map.of()
        );
    }

    public static ToolExecutionResult partialSuccess(String toolCallId, String toolName, Object data,
                                                      String warning, Duration duration) {
        return new ToolExecutionResult(
                toolCallId, toolName, ExecutionStatus.PARTIAL_SUCCESS,
                data, null, duration,
                Map.of("warning", warning != null ? warning : "")
        );
    }

    public static ToolExecutionResult businessError(String toolCallId, String toolName, ToolError error,
                                                     Duration duration) {
        return new ToolExecutionResult(
                toolCallId, toolName, ExecutionStatus.BUSINESS_ERROR,
                null, error, duration, Map.of()
        );
    }

    public static ToolExecutionResult executionError(String toolCallId, String toolName, ToolError error,
                                                      Duration duration) {
        return new ToolExecutionResult(
                toolCallId, toolName, ExecutionStatus.EXECUTION_ERROR,
                null, error, duration, Map.of()
        );
    }

    public static ToolExecutionResult timeout(String toolCallId, String toolName, Duration duration) {
        return new ToolExecutionResult(
                toolCallId, toolName, ExecutionStatus.TIMEOUT,
                null, ToolError.timeout("Tool execution timed out after " + duration.toSeconds() + "s"),
                duration, Map.of()
        );
    }

    public static ToolExecutionResult denied(String toolCallId, String toolName, String reason) {
        return new ToolExecutionResult(
                toolCallId, toolName, ExecutionStatus.DENIED,
                null, ToolError.permission(reason),
                Duration.ZERO, Map.of()
        );
    }

    public static ToolExecutionResult cancelled(String toolCallId, String toolName) {
        return new ToolExecutionResult(
                toolCallId, toolName, ExecutionStatus.CANCELLED,
                null, null, Duration.ZERO, Map.of()
        );
    }

    public ToolExecutionResult withMetadata(String key, Object value) {
        Map<String, Object> newMeta = new HashMap<>(this.metadata);
        newMeta.put(key, value);
        return new ToolExecutionResult(toolCallId, toolName, status, data, error, duration,
                Collections.unmodifiableMap(newMeta));
    }

    public boolean isSuccess() {
        return status.isSuccess();
    }

    public boolean isError() {
        return status.isError();
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    public ToolResult toLegacy() {
        return new ToolResult(
                status.isSuccess(),
                status == ExecutionStatus.SUCCESS ? "OK" : status.name(),
                null,
                null,
                null,
                null,
                data,
                error != null ? error.toString() : null,
                null,
                toolCallId
        );
    }

    public static ToolExecutionResult fromLegacy(ToolResult legacy, String toolName) {
        ExecutionStatus s = legacy.success() ? ExecutionStatus.SUCCESS : ExecutionStatus.EXECUTION_ERROR;
        ToolError err = legacy.error() != null ? ToolError.internal(legacy.error()) : null;
        return new ToolExecutionResult(
                legacy.toolCallId(), toolName, s,
                legacy.data(), err, Duration.ZERO, Map.of()
        );
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"status\":\"").append(status.name()).append('"');
        sb.append(",\"toolCallId\":\"").append(escapeJson(toolCallId)).append('"');
        sb.append(",\"toolName\":\"").append(escapeJson(toolName)).append('"');
        if (data != null) {
            sb.append(",\"data\":\"").append(escapeJson(String.valueOf(data))).append('"');
        }
        if (error != null) {
            sb.append(",\"error\":\"").append(escapeJson(error.toString())).append('"');
        }
        if (duration != null) {
            sb.append(",\"durationMs\":").append(duration.toMillis());
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}