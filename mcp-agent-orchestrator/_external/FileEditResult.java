package com.mcp.tools.model;

import java.util.List;

/**
 * 纯结构化文件编辑结果，机器友好，不混入自然语言。
 * 前端/agent 可以稳定解析 JSON，需要时可自行渲染人类可读格式。
 */
public record FileEditResult(
        boolean ok,
        String tool,
        String path,
        String versionBefore,       // 操作前文件 hash 前8位
        String versionAfter,        // 操作后文件 hash 前8位
        String backupPath,          // 备份路径（如果有）
        int affectedLines,          // 受影响行数
        String preview,             // diff 预览（可选）
        String message,             // 简短描述
        String error,               // 错误详情（如果失败）
        List<String> warnings       // 警告信息
) {

    public String toJson() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"ok\":").append(ok);
        appendStr(sb, "tool", tool);
        appendStr(sb, "path", path);
        appendStr(sb, "versionBefore", versionBefore);
        appendStr(sb, "versionAfter", versionAfter);
        appendStr(sb, "backupPath", backupPath);
        sb.append(",\"affectedLines\":").append(affectedLines);
        appendStr(sb, "preview", preview);
        appendStr(sb, "message", message);
        appendStr(sb, "error", error);
        if (warnings != null && !warnings.isEmpty()) {
            sb.append(",\"warnings\":[");
            for (int i = 0; i < warnings.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(escape(warnings.get(i))).append('"');
            }
            sb.append(']');
        }
        sb.append('}');
        return sb.toString();
    }

    private static void appendStr(StringBuilder sb, String key, String value) {
        if (value != null) {
            sb.append(",\"").append(key).append("\":\"").append(escape(value)).append('"');
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> sb.append(c);
            }
        }
        return sb.toString();
    }

    // ---- Builder 模式 ----

    public static FileEditResult success(String tool, String path, String versionBefore,
                                         String versionAfter, int affectedLines, String message) {
        return new FileEditResult(true, tool, path, versionBefore, versionAfter,
                null, affectedLines, null, message, null, null);
    }

    public static FileEditResult failure(String tool, String path, String error) {
        return new FileEditResult(false, tool, path, null, null,
                null, 0, null, null, error, null);
    }

    public FileEditResult withBackupPath(String backupPath) {
        return new FileEditResult(ok, tool, path, versionBefore, versionAfter,
                backupPath, affectedLines, preview, message, error, warnings);
    }

    public FileEditResult withPreview(String preview) {
        return new FileEditResult(ok, tool, path, versionBefore, versionAfter,
                backupPath, affectedLines, preview, message, error, warnings);
    }

    public FileEditResult withWarnings(List<String> warnings) {
        return new FileEditResult(ok, tool, path, versionBefore, versionAfter,
                backupPath, affectedLines, preview, message, error, warnings);
    }
}