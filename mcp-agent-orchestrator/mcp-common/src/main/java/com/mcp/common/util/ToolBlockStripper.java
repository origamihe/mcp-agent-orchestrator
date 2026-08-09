package com.mcp.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工具块清洗器 — 从文本中移除 [Internal_Memory_Storage] / [Tool_Call] 等内部工具块。
 *
 * <p>位于 mcp-common，供所有模块共享使用，确保清洗逻辑一致。
 *
 * <p>使用方式：
 * <pre>{@code
 * String cleaned = ToolBlockStripper.strip(rawText);
 * }</pre>
 */
public final class ToolBlockStripper {

    private static final Pattern TOOL_TAG_PATTERN = Pattern.compile(
            "\\[(Internal_Memory_Storage|Tool_Call)\\]",
            Pattern.CASE_INSENSITIVE
    );

    private ToolBlockStripper() {
    }

    /**
     * 从文本中移除所有工具块，返回清洗后的文本。
     * 工具块格式：[TAG] + 可选 JSON 体（可能跨行，支持嵌套）。
     */
    public static String strip(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String cleaned = stripToolBlocks(text);
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n").trim();
        return cleaned;
    }

    private static String stripToolBlocks(String text) {
        Matcher matcher = TOOL_TAG_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            int tagStart = matcher.start();
            int tagEnd = matcher.end();

            result.append(text, lastEnd, tagStart);

            int jsonEnd = findJsonEnd(text, tagEnd);
            lastEnd = jsonEnd;
        }

        result.append(text, lastEnd, text.length());
        return result.toString();
    }

    private static int findJsonEnd(String text, int tagEnd) {
        int pos = skipWhitespace(text, tagEnd);

        if (pos >= text.length() || text.charAt(pos) != '{') {
            return tagEnd;
        }

        int braceCount = 0;
        int i = pos;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    return skipWhitespace(text, i + 1);
                }
            }
            i++;
        }

        return tagEnd;
    }

    private static int skipWhitespace(String text, int start) {
        int pos = start;
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
        return pos;
    }
}