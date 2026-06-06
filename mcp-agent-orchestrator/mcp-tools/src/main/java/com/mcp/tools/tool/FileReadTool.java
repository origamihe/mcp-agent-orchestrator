package com.mcp.tools.tool;

import com.mcp.tools.annotation.McpTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class FileReadTool {

    @McpTool(
            name = "read_file",
            description = "读取指定路径的文件内容，如果是目录则列出目录内容。参数 path：文件的绝对路径或相对路径",
            tags = {"file", "io"}
    )
    public String readFile(String path) {
        if (path == null || path.isBlank()) {
            return "错误：未指定文件路径参数，请提供有效的 path 参数。";
        }
        try {
            Path filePath = Path.of(path);
            if (!Files.exists(filePath)) {
                return "路径不存在: " + path;
            }

            if (Files.isDirectory(filePath)) {
                return listDirectory(filePath);
            }

            if (!Files.isReadable(filePath)) {
                return "文件不可读: " + path;
            }
            String content = Files.readString(filePath);
            log.info("File read successfully: {} ({} chars)", path, content.length());
            return content;
        } catch (IOException e) {
            log.error("Failed to read file: {}", path, e);
            return "读取文件失败: " + path + " - " + e.getMessage();
        }
    }

    private String listDirectory(Path dirPath) throws IOException {
        try (Stream<Path> entries = Files.list(dirPath)) {
            String listing = entries
                    .map(p -> (Files.isDirectory(p) ? "[DIR]  " : "[FILE] ") + p.getFileName())
                    .collect(Collectors.joining("\n"));
            if (listing.isEmpty()) {
                return "目录为空: " + dirPath;
            }
            log.info("Directory listed: {} ({} entries)", dirPath, listing.lines().count());
            return "目录 " + dirPath + " 的内容:\n" + listing;
        }
    }
}