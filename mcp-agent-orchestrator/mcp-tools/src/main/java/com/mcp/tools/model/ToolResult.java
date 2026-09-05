package com.mcp.tools.model;

import java.util.List;

@Deprecated
public record ToolResult(
        boolean success,
        String message,
        String path,
        String operation,
        Integer affectedLines,
        String backupPath,
        Object data,
        String error,
        List<String> warnings,
        String toolCallId
) {

    public static ToolResult success(String message) {
        return new ToolResult(true, message, null, null, null, null, null, null, null, null);
    }

    public static ToolResult success(String message, String path, String operation) {
        return new ToolResult(true, message, path, operation, null, null, null, null, null, null);
    }

    public static ToolResult failure(String error) {
        return new ToolResult(false, error, null, null, null, null, null, error, null, null);
    }

    public static ToolResult failure(String error, String path, String operation) {
        return new ToolResult(false, error, path, operation, null, null, null, error, null, null);
    }

    public ToolResult withData(Object data) {
        return new ToolResult(success, message, path, operation, affectedLines, backupPath, data, error, warnings, toolCallId);
    }

    public ToolResult withAffectedLines(int lines) {
        return new ToolResult(success, message, path, operation, lines, backupPath, data, error, warnings, toolCallId);
    }

    public ToolResult withBackupPath(String backup) {
        return new ToolResult(success, message, path, operation, affectedLines, backup, data, error, warnings, toolCallId);
    }

    public ToolResult withToolCallId(String toolCallId) {
        return new ToolResult(success, message, path, operation, affectedLines, backupPath, data, error, warnings, toolCallId);
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"success\":").append(success);
        if (message != null) sb.append(",\"message\":\"").append(escapeJson(message)).append('"');
        if (path != null) sb.append(",\"path\":\"").append(escapeJson(path)).append('"');
        if (operation != null) sb.append(",\"operation\":\"").append(escapeJson(operation)).append('"');
        if (affectedLines != null) sb.append(",\"affectedLines\":").append(affectedLines);
        if (backupPath != null) sb.append(",\"backupPath\":\"").append(escapeJson(backupPath)).append('"');
        if (data != null) sb.append(",\"data\":\"").append(escapeJson(String.valueOf(data))).append('"');
        if (error != null) sb.append(",\"error\":\"").append(escapeJson(error)).append('"');
        if (toolCallId != null) sb.append(",\"toolCallId\":\"").append(escapeJson(toolCallId)).append('"');
        if (warnings != null && !warnings.isEmpty()) {
            sb.append(",\"warnings\":[");
            for (int i = 0; i < warnings.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(escapeJson(warnings.get(i))).append('"');
            }
            sb.append(']');
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