package com.mcp.tools.tool.read;

import com.mcp.tools.annotation.McpTool;
import com.mcp.tools.model.FileVersion;
import com.mcp.tools.service.WorkspaceFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 只读文件工具集：目录列表、文件信息、行范围读取、搜索、批量读取。
 * 所有读取操作返回 FileVersion（含 fileHash），为后续编辑提供版本锁依据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadToolSet {

    private final WorkspaceFileService fs;

    // ==================== list_directory ====================

    @McpTool(
            name = "list_directory",
            description = "List directory contents recursively. Parameters: path (directory path), depth (1-10, default 1), extension (filter like '.java', optional), treeMode (tree view, default false).",
            tags = {"file", "read", "directory"}
    )
    public String listDirectory(String path, int depth, String extension, boolean treeMode) {
        try {
            Path dirPath = fs.resolve(path);
            if (!Files.exists(dirPath)) {
                return fail("Path does not exist: " + path, path, "list_directory");
            }
            if (!Files.isDirectory(dirPath)) {
                return fail("Not a directory: " + path, path, "list_directory");
            }
            if (depth < 1) depth = 1;
            if (depth > 10) depth = 10;
            String ext = (extension != null && !extension.isEmpty()) ? extension.toLowerCase() : null;

            StringBuilder listing = new StringBuilder();
            if (treeMode) {
                listing.append(dirPath.getFileName()).append("\n");
                buildTree(dirPath, "", 1, depth, ext, listing);
            } else {
                collectEntries(dirPath, depth, ext, listing);
            }

            log.info("list_directory: {} depth={}, ext={}, treeMode={}", path, depth, ext, treeMode);
            return success("Directory listed", path, "list_directory", listing.toString());

        } catch (SecurityException e) {
            return fail(e.getMessage(), path, "list_directory");
        } catch (IOException e) {
            log.error("Failed to list directory: {}", path, e);
            return fail("Failed to list directory: " + e.getMessage(), path, "list_directory");
        }
    }

    // ==================== file_info ====================

    @McpTool(
            name = "file_info",
            description = "Get file metadata including hash, size, line count, and version. Returns fileHash for use in subsequent edit operations. Parameter: path (file path).",
            tags = {"file", "read", "metadata", "version"}
    )
    public String fileInfo(String path) {
        try {
            Path filePath = fs.resolve(path);
            if (!Files.exists(filePath)) {
                return fail("Path does not exist: " + path, path, "file_info");
            }

            FileVersion ver = fs.getVersion(filePath);
            StringBuilder sb = new StringBuilder();
            sb.append("{\"type\":\"").append(Files.isDirectory(filePath) ? "directory" : "file").append("\"");
            sb.append(",\"size\":").append(Files.size(filePath));
            sb.append(",\"readable\":").append(Files.isReadable(filePath));
            sb.append(",\"writable\":").append(Files.isWritable(filePath));
            if (!ver.fileHash().isEmpty()) {
                sb.append(",\"fileHash\":\"").append(ver.fileHash()).append("\"");
                sb.append(",\"version\":\"").append(ver.version()).append("\"");
                sb.append(",\"lineCount\":").append(ver.lineCount());
            }
            sb.append(",\"lastModified\":").append(ver.lastModified());
            try {
                String timeStr = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(ver.lastModified()));
                sb.append(",\"lastModifiedStr\":\"").append(timeStr).append("\"");
            } catch (Exception ignored) {}
            sb.append("}");

            log.info("file_info: {} (hash={}, lines={})", path, ver.version(), ver.lineCount());
            return success("File info retrieved", path, "file_info", sb.toString());

        } catch (SecurityException e) {
            return fail(e.getMessage(), path, "file_info");
        } catch (IOException e) {
            log.error("Failed to get file info: {}", path, e);
            return fail("Failed to get file info: " + e.getMessage(), path, "file_info");
        }
    }

    // ==================== read_file ====================

    @McpTool(
            name = "read_file",
            description = "Read the full content of a file. Returns content with fileHash for version locking in subsequent edit operations. This is the primary entry point for agent file reading. Parameter: path (relative file path).",
            tags = {"file", "read", "full"}
    )
    public String readFile(String path) {
        try {
            Path filePath = fs.resolve(path);
            if (!Files.exists(filePath)) {
                return fail("Path does not exist: " + path, path, "read_file");
            }
            if (Files.isDirectory(filePath)) {
                return fail("Path is a directory, not a file: " + path + ". Use list_directory instead.", path, "read_file");
            }
            if (!Files.isReadable(filePath)) {
                return fail("File not readable: " + path, path, "read_file");
            }

            String content = fs.readAll(filePath);
            FileVersion ver = FileVersion.of(content, Files.getLastModifiedTime(filePath).toMillis());

            log.info("read_file: {} ({} chars, {} lines, version={})",
                    path, content.length(), ver.lineCount(), ver.version());

            StringBuilder result = new StringBuilder();
            result.append("{\"ok\":true,\"tool\":\"read_file\",\"path\":\"")
                    .append(escapeJson(path)).append("\"");
            result.append(",\"fileHash\":\"").append(ver.fileHash()).append("\"");
            result.append(",\"version\":\"").append(ver.version()).append("\"");
            result.append(",\"lineCount\":").append(ver.lineCount());
            result.append(",\"fileSize\":").append(ver.fileSize());
            result.append(",\"lastModified\":").append(ver.lastModified());
            result.append(",\"content\":\"").append(escapeJson(content)).append("\"");
            result.append(",\"message\":\"File read successfully\"}");
            return result.toString();

        } catch (SecurityException e) {
            return fail(e.getMessage(), path, "read_file");
        } catch (IOException e) {
            log.error("Failed to read file: {}", path, e);
            return fail("Failed to read file: " + e.getMessage(), path, "read_file");
        }
    }

    // ==================== read_file_range ====================

    @McpTool(
            name = "read_file_range",
            description = "Read a line range of a file. Returns content with fileHash for version locking. Parameters: path (file path), startLine (1-based, inclusive), endLine (1-based, inclusive, -1 for EOF).",
            tags = {"file", "read", "range"}
    )
    public String readFileRange(String path, int startLine, int endLine) {
        try {
            Path filePath = fs.resolve(path);
            if (!Files.exists(filePath)) {
                return fail("Path does not exist: " + path, path, "read_file_range");
            }
            if (Files.isDirectory(filePath)) {
                return fail("Path is a directory: " + path, path, "read_file_range");
            }

            List<String> allLines = fs.readAllLines(filePath);
            int totalLines = allLines.size();

            if (startLine < 1 || startLine > totalLines) {
                return fail("startLine must be 1.." + totalLines + ", got: " + startLine, path, "read_file_range");
            }
            int actualEnd = (endLine < 0 || endLine > totalLines) ? totalLines : endLine;
            if (actualEnd < startLine) {
                return fail("endLine " + endLine + " < startLine " + startLine, path, "read_file_range");
            }

            List<String> selected = allLines.subList(startLine - 1, actualEnd);
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < selected.size(); i++) {
                if (i > 0) content.append('\n');
                content.append(selected.get(i));
            }

            FileVersion ver = FileVersion.of(
                    String.join("\n", allLines),
                    Files.getLastModifiedTime(filePath).toMillis()
            );

            log.info("read_file_range: {} lines {}-{}/{}", path, startLine, actualEnd, totalLines);
            StringBuilder result = new StringBuilder();
            result.append("{\"ok\":true,\"tool\":\"read_file_range\",\"path\":\"")
                    .append(escapeJson(path)).append("\"");
            result.append(",\"fileHash\":\"").append(ver.fileHash()).append("\"");
            result.append(",\"version\":\"").append(ver.version()).append("\"");
            result.append(",\"totalLines\":").append(totalLines);
            result.append(",\"rangeStart\":").append(startLine);
            result.append(",\"rangeEnd\":").append(actualEnd);
            result.append(",\"content\":\"").append(escapeJson(content.toString())).append("\"");
            result.append(",\"message\":\"Range read successfully\"}");
            return result.toString();

        } catch (SecurityException e) {
            return fail(e.getMessage(), path, "read_file_range");
        } catch (IOException e) {
            log.error("Failed to read file range: {}", path, e);
            return fail("Failed to read file range: " + e.getMessage(), path, "read_file_range");
        }
    }

    // ==================== search_file ====================

    @McpTool(
            name = "search_file",
            description = "Search for keyword in a file, return matching lines with context. Parameters: path (file path), keyword (search term), contextLines (before/after, default 2).",
            tags = {"file", "read", "search"}
    )
    public String searchFile(String path, String keyword, int contextLines) {
        try {
            Path filePath = fs.resolve(path);
            if (!Files.exists(filePath)) {
                return fail("Path does not exist: " + path, path, "search_file");
            }
            if (Files.isDirectory(filePath)) {
                return fail("Path is a directory: " + path, path, "search_file");
            }
            if (keyword == null || keyword.isEmpty()) {
                return fail("keyword must not be empty", path, "search_file");
            }
            if (contextLines < 0) contextLines = 2;

            List<String> allLines = fs.readAllLines(filePath);
            List<Map<String, Object>> matches = new ArrayList<>();

            for (int i = 0; i < allLines.size(); i++) {
                if (allLines.get(i).contains(keyword)) {
                    int ctxStart = Math.max(0, i - contextLines);
                    int ctxEnd = Math.min(allLines.size() - 1, i + contextLines);
                    List<String> context = new ArrayList<>();
                    for (int j = ctxStart; j <= ctxEnd; j++) {
                        context.add((j == i ? ">>> L" : "    L") + (j + 1) + ": " + allLines.get(j));
                    }
                    Map<String, Object> match = new LinkedHashMap<>();
                    match.put("line", i + 1);
                    match.put("context", context);
                    matches.add(match);
                }
            }

            FileVersion ver = FileVersion.of(
                    String.join("\n", allLines),
                    Files.getLastModifiedTime(filePath).toMillis()
            );

            StringBuilder json = new StringBuilder();
            json.append("{\"ok\":true,\"tool\":\"search_file\",\"path\":\"").append(escapeJson(path)).append("\"");
            json.append(",\"fileHash\":\"").append(ver.fileHash()).append("\"");
            json.append(",\"version\":\"").append(ver.version()).append("\"");
            json.append(",\"keyword\":\"").append(escapeJson(keyword)).append("\"");
            json.append(",\"matchCount\":").append(matches.size());
            json.append(",\"matches\":[");
            for (int i = 0; i < matches.size(); i++) {
                if (i > 0) json.append(',');
                Map<String, Object> m = matches.get(i);
                json.append("{\"line\":").append(m.get("line")).append(",\"context\":[");
                @SuppressWarnings("unchecked")
                List<String> ctx = (List<String>) m.get("context");
                for (int j = 0; j < ctx.size(); j++) {
                    if (j > 0) json.append(',');
                    json.append('"').append(escapeJson(ctx.get(j))).append('"');
                }
                json.append("]}");
            }
            json.append("],\"message\":\"Search completed\"}");
            return json.toString();

        } catch (SecurityException e) {
            return fail(e.getMessage(), path, "search_file");
        } catch (IOException e) {
            log.error("Failed to search file: {}", path, e);
            return fail("Failed to search file: " + e.getMessage(), path, "search_file");
        }
    }

    // ==================== read_files (batch) ====================

    @McpTool(
            name = "read_files",
            description = "Batch read multiple files with preview. Parameters: paths (comma-separated), previewLines (default 20).",
            tags = {"file", "read", "batch"}
    )
    public String readFiles(String paths, int previewLines) {
        if (paths == null || paths.isBlank()) {
            return fail("paths must not be empty", null, "read_files");
        }
        if (previewLines <= 0) previewLines = 20;

        String[] pathArray = paths.split(",");
        List<Map<String, Object>> files = new ArrayList<>();
        int successCount = 0, failCount = 0;
        List<String> warnings = new ArrayList<>();

        for (String p : pathArray) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) continue;
            try {
                Path filePath = fs.resolve(trimmed);
                if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                    warnings.add(trimmed + ": skipped");
                    failCount++;
                    continue;
                }
                List<String> allLines = fs.readAllLines(filePath);
                FileVersion ver = FileVersion.of(
                        String.join("\n", allLines),
                        Files.getLastModifiedTime(filePath).toMillis()
                );
                int preview = Math.min(previewLines, allLines.size());
                List<String> previewContent = allLines.subList(0, preview);

                Map<String, Object> fileInfo = new LinkedHashMap<>();
                fileInfo.put("path", trimmed);
                fileInfo.put("fileHash", ver.fileHash());
                fileInfo.put("version", ver.version());
                fileInfo.put("totalLines", allLines.size());
                fileInfo.put("previewLines", preview);
                fileInfo.put("preview", previewContent);
                files.add(fileInfo);
                successCount++;
            } catch (Exception e) {
                warnings.add(trimmed + ": " + e.getMessage());
                failCount++;
            }
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"ok\":true,\"tool\":\"read_files\",\"successCount\":").append(successCount);
        json.append(",\"failCount\":").append(failCount);
        json.append(",\"files\":[");
        for (int i = 0; i < files.size(); i++) {
            if (i > 0) json.append(',');
            Map<String, Object> f = files.get(i);
            json.append("{\"path\":\"").append(escapeJson((String) f.get("path"))).append("\"");
            json.append(",\"fileHash\":\"").append(f.get("fileHash")).append("\"");
            json.append(",\"version\":\"").append(f.get("version")).append("\"");
            json.append(",\"totalLines\":").append(f.get("totalLines"));
            json.append(",\"previewLines\":").append(f.get("previewLines"));
            json.append(",\"preview\":[");
            @SuppressWarnings("unchecked")
            List<String> preview = (List<String>) f.get("preview");
            for (int j = 0; j < preview.size(); j++) {
                if (j > 0) json.append(',');
                json.append('"').append(escapeJson(preview.get(j))).append('"');
            }
            json.append("]}");
        }
        json.append("],\"message\":\"Batch read: " + successCount + " success, " + failCount + " failed\"}");
        return json.toString();
    }

    // ==================== 辅助方法 ====================

    private void buildTree(Path dir, String prefix, int currentDepth, int maxDepth,
                           String ext, StringBuilder sb) throws IOException {
        if (currentDepth > maxDepth) return;
        try (Stream<Path> entries = Files.list(dir)) {
            List<Path> sorted = entries.sorted((a, b) -> {
                boolean aDir = Files.isDirectory(a), bDir = Files.isDirectory(b);
                if (aDir != bDir) return aDir ? -1 : 1;
                return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
            }).collect(Collectors.toList());

            for (int i = 0; i < sorted.size(); i++) {
                Path p = sorted.get(i);
                boolean isLast = (i == sorted.size() - 1);
                String connector = isLast ? "└── " : "├── ";
                String childPrefix = isLast ? "    " : "│   ";
                String name = p.getFileName().toString();

                if (ext != null && Files.isRegularFile(p) && !name.toLowerCase().endsWith(ext)) {
                    continue;
                }
                sb.append(prefix).append(connector);
                if (Files.isDirectory(p)) {
                    sb.append("[DIR] ").append(name).append("\n");
                    buildTree(p, prefix + childPrefix, currentDepth + 1, maxDepth, ext, sb);
                } else {
                    sb.append("[FILE] ").append(name)
                            .append(" (").append(formatSize(Files.size(p))).append(")\n");
                }
            }
        }
    }

    private void collectEntries(Path dir, int depth, String ext, StringBuilder sb) throws IOException {
        try (Stream<Path> walk = Files.walk(dir, depth - 1)) {
            List<Path> sorted = walk
                    .filter(p -> !p.equals(dir))
                    .filter(p -> ext == null || Files.isDirectory(p)
                            || p.getFileName().toString().toLowerCase().endsWith(ext))
                    .sorted((a, b) -> {
                        boolean aDir = Files.isDirectory(a), bDir = Files.isDirectory(b);
                        if (aDir != bDir) return aDir ? -1 : 1;
                        return a.compareTo(b);
                    }).collect(Collectors.toList());

            if (sorted.isEmpty()) {
                sb.append("(empty directory)\n");
            } else {
                for (Path p : sorted) {
                    String prefix = Files.isDirectory(p) ? "[DIR]  " : "[FILE] ";
                    String size = Files.isDirectory(p) ? "" : " (" + formatSize(Files.size(p)) + ")";
                    sb.append(prefix).append(dir.relativize(p)).append(size).append("\n");
                }
            }
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String success(String message, String path, String tool, String data) {
        return "{\"ok\":true,\"tool\":\"" + escapeJson(tool)
                + "\",\"path\":\"" + escapeJson(path)
                + "\",\"message\":\"" + escapeJson(message)
                + "\",\"data\":\"" + escapeJson(data) + "\"}";
    }

    private String fail(String error, String path, String tool) {
        return "{\"ok\":false,\"tool\":\"" + escapeJson(tool)
                + "\",\"path\":\"" + (path != null ? escapeJson(path) : "")
                + "\",\"error\":\"" + escapeJson(error) + "\"}";
    }

    private static String escapeJson(String s) {
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
}