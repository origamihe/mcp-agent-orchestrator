package com.mcp.tools.model;

import java.util.List;

/**
 * 一个 patch hunk，描述"将文件的 startLine 到 endLine 行替换为 newLines"。
 * agent 产出 patch，系统原子应用，预览后再提交。
 */
public record PatchHunk(
        int startLine,          // 1-based, inclusive
        int endLine,            // 1-based, inclusive, -1 表示到文件末尾
        List<String> newLines   // 替换后的新行内容（可为空列表即删除）
) {

    public PatchHunk {
        if (startLine < 1) {
            throw new IllegalArgumentException("startLine must be >= 1, got: " + startLine);
        }
        if (newLines == null) {
            throw new IllegalArgumentException("newLines must not be null");
        }
    }

    public static PatchHunk replace(int startLine, int endLine, List<String> newLines) {
        return new PatchHunk(startLine, endLine, newLines);
    }

    public static PatchHunk insert(int afterLine, List<String> newLines) {
        return new PatchHunk(afterLine + 1, afterLine, newLines);
    }

    public static PatchHunk delete(int startLine, int endLine) {
        return new PatchHunk(startLine, endLine, List.of());
    }
}