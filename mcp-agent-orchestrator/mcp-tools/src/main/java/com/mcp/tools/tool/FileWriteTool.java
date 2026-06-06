package com.mcp.tools.tool;

import com.mcp.tools.annotation.McpTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Slf4j
@Component
public class FileWriteTool {

    private static final int MAX_CONTENT_SIZE = 10 * 1024 * 1024; // 10MB 大小限制

    @McpTool(
            name = "write_file",
            description = "创建新文件或覆盖已有文件，写入指定内容。参数 path：文件的绝对路径或相对路径，content：要写入的文件内容",
            tags = {"file", "write", "io"}
    )
    public String writeFile(String path, String content) {
        if (path == null || path.isBlank()) {
            return "错误：未指定文件路径，请提供有效的 path 参数。";
        }
        if (content == null) {
            return "错误：未提供文件内容，请提供有效的 content 参数。";
        }

        if (content.length() > MAX_CONTENT_SIZE) {
            return String.format("错误：内容大小 %d KB 超过限制（最大 %d MB），拒绝写入。",
                    content.length() / 1024, MAX_CONTENT_SIZE / (1024 * 1024));
        }

        try {
            Path filePath = Path.of(path);
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                log.info("父目录已创建: {}", parentDir);
            }

            String backupPath = null;
            if (Files.exists(filePath)) {
                backupPath = path + ".bak";
                Files.copy(filePath, Path.of(backupPath));
                log.info("原文件已备份: {}", backupPath);
            }

            Files.writeString(filePath, content);

            String writtenContent = Files.readString(filePath);
            if (!writtenContent.equals(content)) {
                if (backupPath != null && Files.exists(Path.of(backupPath))) {
                    Files.copy(Path.of(backupPath), filePath);
                }
                log.error("写入校验失败: {}", path);
                return "错误：写入校验失败，文件内容不匹配，已恢复原文件。";
            }

            log.info("文件写入成功: {} ({} 字符)", path, content.length());
            String result = "文件写入成功: " + path + "\n写入字符数: " + content.length();
            if (backupPath != null) {
                result += "\n原文件已备份到: " + backupPath;
            }
            return result;
        } catch (IOException e) {
            log.error("文件写入失败: {}", path, e);
            return "文件写入失败: " + path + " - " + e.getMessage();
        }
    }

    @McpTool(
            name = "append_file",
            description = "向已有文件末尾追加内容，如果文件不存在则创建。参数 path：文件路径，content：要追加的内容",
            tags = {"file", "append", "io"}
    )
    public String appendFile(String path, String content) {
        if (path == null || path.isBlank()) {
            return "错误：未指定文件路径，请提供有效的 path 参数。";
        }
        if (content == null) {
            return "错误：未提供追加内容，请提供有效的 content 参数。";
        }

        if (content.length() > MAX_CONTENT_SIZE) {
            return String.format("错误：追加内容大小 %d KB 超过限制（最大 %d MB），拒绝写入。",
                    content.length() / 1024, MAX_CONTENT_SIZE / (1024 * 1024));
        }

        try {
            Path filePath = Path.of(path);
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            String backupPath = null;
            if (Files.exists(filePath) && Files.size(filePath) > 0) {
                backupPath = path + ".bak";
                Files.copy(filePath, Path.of(backupPath));
                log.info("原文件已备份: {}", backupPath);
            }

            Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("文件追加成功: {} ({} 字符)", path, content.length());
            String result = "文件追加成功: " + path + "\n追加字符数: " + content.length();
            if (backupPath != null) {
                result += "\n原文件已备份到: " + backupPath;
            }
            return result;
        } catch (IOException e) {
            log.error("文件追加失败: {}", path, e);
            return "文件追加失败: " + path + " - " + e.getMessage();
        }
    }

    @McpTool(
            name = "edit_file",
            description = "在文件中查找指定文本并将其替换为新文本。参数 path：文件路径，old_str：需要被替换的原文本（必须精确匹配），new_str：替换后的新文本",
            tags = {"file", "edit", "replace", "io"}
    )
    public String editFile(String path, String old_str, String new_str) {
        if (path == null || path.isBlank()) {
            return "错误：未指定文件路径，请提供有效的 path 参数。";
        }
        if (old_str == null || old_str.isEmpty()) {
            return "错误：未提供要查找的原文本，请提供有效的 old_str 参数。";
        }
        if (new_str == null) {
            new_str = "";
        }

        try {
            Path filePath = Path.of(path);
            if (!Files.exists(filePath)) {
                return "错误：文件不存在: " + path;
            }
            if (!Files.isReadable(filePath)) {
                return "错误：文件不可读: " + path;
            }
            if (!Files.isWritable(filePath)) {
                return "错误：文件不可写: " + path;
            }

            String originalContent = Files.readString(filePath);

            if (!originalContent.contains(old_str)) {
                return "错误：在文件中未找到指定的原文本，请确认 old_str 是否与文件内容精确匹配。\n"
                        + "提示：old_str 需要包含完整的行内内容，包括缩进和标点符号。";
            }

            int occurrenceCount = countOccurrences(originalContent, old_str);
            if (occurrenceCount > 1) {
                return "错误：原文本在文件中出现了 " + occurrenceCount + " 次，匹配不唯一。\n"
                        + "请提供更长的、能唯一标识目标位置的 old_str 文本，确保只匹配一处。";
            }

            String backupPath = path + ".bak";
            Files.copy(filePath, Path.of(backupPath));
            log.info("原文件已备份: {}", backupPath);

            String newContent = originalContent.replace(old_str, new_str);
            Files.writeString(filePath, newContent);

            String writtenContent = Files.readString(filePath);
            if (!writtenContent.contains(new_str) && !new_str.isEmpty()) {
                Files.copy(Path.of(backupPath), filePath);
                return "错误：写入校验失败，新文本未找到，已恢复原文件。";
            }

            log.info("文件编辑成功: {} (替换了 {} 处)", path, occurrenceCount);
            return "文件编辑成功: " + path + "\n替换了 1 处文本。\n原文件已备份到: " + backupPath;
        } catch (IOException e) {
            log.error("文件编辑失败: {}", path, e);
            return "文件编辑失败: " + path + " - " + e.getMessage();
        }
    }

    private int countOccurrences(String text, String target) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}