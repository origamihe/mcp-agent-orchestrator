package com.mcp.tools.tool;

import com.mcp.tools.annotation.McpTool;
import com.mcp.tools.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class FileReadTool {

    private final Path workspaceRoot;
    private static final int MAX_CHUNK_SIZE = 256 * 1024;

    public FileReadTool(@Value("${mcp.workspace.root:}") String workspaceRootPath) {
        String rootPath = workspaceRootPath;
        if (rootPath == null || rootPath.isBlank()) {
            rootPath = System.getProperty("user.dir");
        }
        this.workspaceRoot = Path.of(rootPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(workspaceRoot);
        } catch (IOException e) {
            log.warn("Workspace root directory could not be created: {}", workspaceRoot);
        }
        log.info("FileReadTool initialized with workspace root: {}", workspaceRoot);
    }

    private Path resolveAndValidate(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path must not be null or blank");
        }
        Path resolved = workspaceRoot.resolve(path).toAbsolutePath().normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            throw new SecurityException("Access denied: path '" + path + "' is outside workspace root '" + workspaceRoot + "'");
        }
        return resolved;
    }

    @McpTool(
            name = "read_file",
            description = "Read the full content of a file. Parameter path: relative path within the workspace.",
            tags = {"file", "read", "io"}
    )
    public String readFile(String path) {
        try {
            Path filePath = resolveAndValidate(path);
            if (!Files.exists(filePath)) {
                return ToolResult.failure("Path does not exist: " + path, path, "read_file").toJson();
            }
            if (Files.isDirectory(filePath)) {
                return listDirectoryInternal(filePath, 1);
            }
            if (!Files.isReadable(filePath)) {
                return ToolResult.failure("File not readable: " + path, path, "read_file").toJson();
            }
            String content = Files.readString(filePath);
            log.info("read_file: {} ({} chars)", path, content.length());
            return ToolResult.success("File read successfully", path, "read_file")
                    .withData(content)
                    .withAffectedLines(content.split("\n", -1).length)
                    .toJson();
        } catch (SecurityException e) {
            return ToolResult.failure(e.getMessage(), path, "read_file").toJson();
        } catch (IOException e) {
            log.error("Failed to read file: {}", path, e);
            return ToolResult.failure("Failed to read file: " + e.getMessage(), path, "read_file").toJson();
        }
    }

    @McpTool(
            name = "read_file_range",
            description = "Read a specific line range of a file. Parameters: path (file path), startLine (1-based, inclusive), endLine (1-based, inclusive, -1 for end of file).",
            tags = {"file", "read", "range", "io"}
    )
    public String readFileRange(String path, int startLine, int endLine) {
        try {
            Path filePath = resolveAndValidate(path);
            if (!Files.exists(filePath)) {
                return ToolResult.failure("Path does not exist: " + path, path, "read_file_range").toJson();
            }
            if (Files.isDirectory(filePath)) {
                return ToolResult.failure("Path is a directory, not a file: " + path, path, "read_file_range").toJson();
            }
            List<String> allLines = Files.readAllLines(filePath);
            int totalLines = allLines.size();
            if (startLine < 1) {
                return ToolResult.failure("startLine must be >= 1, got: " + startLine, path, "read_file_range").toJson();
            }
            if (startLine > totalLines) {
                return ToolResult.failure("startLine " + startLine + " exceeds file total lines " + totalLines, path, "read_file_range").toJson();
            }
            int actualEnd = (endLine < 0 || endLine > totalLines) ? totalLines : endLine;
            if (actualEnd < startLine) {
                return ToolResult.failure("endLine " + endLine + " is less than startLine " + startLine, path, "read_file_range").toJson();
            }
            List<String> selectedLines = allLines.subList(startLine - 1, actualEnd);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < selectedLines.size(); i++) {
                if (i > 0) sb.append('\n');
                sb.append(selectedLines.get(i));
            }
            String rangeContent = sb.toString();
            log.info("read_file_range: {} lines {}-{}/{} ({} chars)", path, startLine, actualEnd, totalLines, rangeContent.length());
            return ToolResult.success("Range read successfully", path, "read_file_range")
                    .withData(rangeContent)
                    .withAffectedLines(selectedLines.size())
                    .toJson()
                    + "\n# Total lines: " + totalLines + ", range: " + startLine + "-" + actualEnd;
        } catch (SecurityException e) {
            return ToolResult.failure(e.getMessage(), path, "read_file_range").toJson();
        } catch (IOException e) {
            log.error("Failed to read file range: {}", path, e);
            return ToolResult.failure("Failed to read file range: " + e.getMessage(), path, "read_file_range").toJson();
        }
    }

    @McpTool(
            name = "search_file",
            description = "Search for a keyword in a file and return context lines around each match. Parameters: path (file path), keyword (search term), contextLines (lines before/after match, default 2).",
            tags = {"file", "search", "read", "io"}
    )
    public String searchFile(String path, String keyword, int contextLines) {
        try {
            Path filePath = resolveAndValidate(path);
            if (!Files.exists(filePath)) {
                return ToolResult.failure("Path does not exist: " + path, path, "search_file").toJson();
            }
            if (Files.isDirectory(filePath)) {
                return ToolResult.failure("Path is a directory, not a file: " + path, path, "search_file").toJson();
            }
            if (keyword == null || keyword.isEmpty()) {
                return ToolResult.failure("keyword must not be empty", path, "search_file").toJson();
            }
            if (contextLines < 0) contextLines = 2;
            List<String> allLines = Files.readAllLines(filePath);
            List<MatchResult> matches = new ArrayList<>();
            for (int i = 0; i < allLines.size(); i++) {
                if (allLines.get(i).contains(keyword)) {
                    int contextStart = Math.max(0, i - contextLines);
                    int contextEnd = Math.min(allLines.size() - 1, i + contextLines);
                    List<String> context = new ArrayList<>();
                    for (int j = contextStart; j <= contextEnd; j++) {
                        context.add((j == i ? ">>> L" + (j + 1) + ": " : "    L" + (j + 1) + ": ") + allLines.get(j));
                    }
                    matches.add(new MatchResult(i + 1, context));
                }
            }
            if (matches.isEmpty()) {
                return ToolResult.success("No matches found for '" + keyword + "'", path, "search_file")
                        .withData("No matches found in " + allLines.size() + " lines")
                        .toJson();
            }
            StringBuilder result = new StringBuilder();
            result.append("Found ").append(matches.size()).append(" match(es) for '").append(keyword).append("':\n");
            for (int i = 0; i < matches.size(); i++) {
                if (i > 0) result.append("\n---\n");
                result.append("Match ").append(i + 1).append(":\n");
                result.append(String.join("\n", matches.get(i).context));
            }
            log.info("search_file: {} found {} match(es) for '{}'", path, matches.size(), keyword);
            return ToolResult.success("Search completed", path, "search_file")
                    .withData(matches.size() + " match(es)")
                    .toJson()
                    + "\n" + result;
        } catch (SecurityException e) {
            return ToolResult.failure(e.getMessage(), path, "search_file").toJson();
        } catch (IOException e) {
            log.error("Failed to search file: {}", path, e);
            return ToolResult.failure("Failed to search file: " + e.getMessage(), path, "search_file").toJson();
        }
    }

    @McpTool(
            name = "read_file_chunk",
            description = "Read a chunk of a large file by byte offset. Parameters: path (file path), offset (byte offset, 0-based), chunkSize (bytes per chunk, max 262144).",
            tags = {"file", "read", "chunk", "io"}
    )
    public String readFileChunk(String path, long offset, int chunkSize) {
        try {
            Path filePath = resolveAndValidate(path);
            if (!Files.exists(filePath)) {
                return ToolResult.failure("Path does not exist: " + path, path, "read_file_chunk").toJson();
            }
            if (Files.isDirectory(filePath)) {
                return ToolResult.failure("Path is a directory, not a file: " + path, path, "read_file_chunk").toJson();
            }
            if (offset < 0) {
                return ToolResult.failure("offset must be >= 0, got: " + offset, path, "read_file_chunk").toJson();
            }
            if (chunkSize <= 0 || chunkSize > MAX_CHUNK_SIZE) {
                return ToolResult.failure("chunkSize must be between 1 and " + MAX_CHUNK_SIZE + ", got: " + chunkSize, path, "read_file_chunk").toJson();
            }
            long fileSize = Files.size(filePath);
            if (offset >= fileSize) {
                return ToolResult.success("Offset beyond file size", path, "read_file_chunk")
                        .withData("File size: " + fileSize + " bytes, offset: " + offset + " is beyond EOF")
                        .toJson();
            }
            int actualSize = (int) Math.min(chunkSize, fileSize - offset);
            byte[] buffer = new byte[actualSize];
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath.toFile(), "r")) {
                raf.seek(offset);
                raf.readFully(buffer);
            }
            String chunk = new String(buffer, StandardCharsets.UTF_8);
            log.info("read_file_chunk: {} offset={}, size={}/{}", path, offset, actualSize, fileSize);
            return ToolResult.success("Chunk read successfully", path, "read_file_chunk")
                    .withData(chunk)
                    .toJson()
                    + "\n# File size: " + fileSize + " bytes, offset: " + offset + ", chunk size: " + actualSize;
        } catch (SecurityException e) {
            return ToolResult.failure(e.getMessage(), path, "read_file_chunk").toJson();
        } catch (IOException e) {
            log.error("Failed to read file chunk: {}", path, e);
            return ToolResult.failure("Failed to read file chunk: " + e.getMessage(), path, "read_file_chunk").toJson();
        }
    }

    @McpTool(
            name = "list_directory",
            description = "List directory contents with recursive depth, extension filter, and tree view. Parameters: path (directory path), depth (recursion depth, default 1), extension (file extension filter like '.java', optional), treeMode (tree view, default false).",
            tags = {"file", "directory", "list", "io"}
    )
    public String listDirectory(String path, int depth, String extension, boolean treeMode) {
        try {
            Path dirPath = resolveAndValidate(path);
            if (!Files.exists(dirPath)) {
                return ToolResult.failure("Path does not exist: " + path, path, "list_directory").toJson();
            }
            if (!Files.isDirectory(dirPath)) {
                return ToolResult.failure("Path is not a directory: " + path, path, "list_directory").toJson();
            }
            if (depth < 1) depth = 1;
            if (depth > 10) depth = 10;
            String ext = (extension != null && !extension.isEmpty()) ? extension.toLowerCase() : null;
            StringBuilder result = new StringBuilder();
            if (treeMode) {
                result.append(dirPath.getFileName()).append("\n");
                buildTree(dirPath, "", 1, depth, ext, result);
            } else {
                result.append("Directory: ").append(dirPath).append("\n\n");
                collectEntries(dirPath, depth, ext, result);
            }
            log.info("list_directory: {} depth={}, ext={}, treeMode={}", path, depth, ext, treeMode);
            return ToolResult.success("Directory listed successfully", path, "list_directory")
                    .withData(result.toString())
                    .toJson()
                    + "\n" + result;
        } catch (SecurityException e) {
            return ToolResult.failure(e.getMessage(), path, "list_directory").toJson();
        } catch (IOException e) {
            log.error("Failed to list directory: {}", path, e);
            return ToolResult.failure("Failed to list directory: " + e.getMessage(), path, "list_directory").toJson();
        }
    }

    private void buildTree(Path dir, String prefix, int currentDepth, int maxDepth, String ext, StringBuilder sb) throws IOException {
        if (currentDepth > maxDepth) return;
        try (Stream<Path> entries = Files.list(dir)) {
            List<Path> sorted = entries.sorted((a, b) -> {
                boolean aDir = Files.isDirectory(a);
                boolean bDir = Files.isDirectory(b);
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
                    try {
                        long size = Files.size(p);
                        sb.append("[FILE] ").append(name).append(" (").append(formatSize(size)).append(")\n");
                    } catch (IOException e) {
                        sb.append("[FILE] ").append(name).append("\n");
                    }
                }
            }
        }
    }

    private void collectEntries(Path dir, int depth, String ext, StringBuilder sb) throws IOException {
        try (Stream<Path> walk = Files.walk(dir, depth - 1)) {
            List<Path> sorted = walk
                    .filter(p -> !p.equals(dir))
                    .filter(p -> ext == null || Files.isDirectory(p) || p.getFileName().toString().toLowerCase().endsWith(ext))
                    .sorted((a, b) -> {
                        boolean aDir = Files.isDirectory(a);
                        boolean bDir = Files.isDirectory(b);
                        if (aDir != bDir) return aDir ? -1 : 1;
                        return a.compareTo(b);
                    })
                    .collect(Collectors.toList());
            if (sorted.isEmpty()) {
                sb.append("(empty directory)\n");
            } else {
                for (Path p : sorted) {
                    String prefix = Files.isDirectory(p) ? "[DIR]  " : "[FILE] ";
                    try {
                        String size = Files.isDirectory(p) ? "" : " (" + formatSize(Files.size(p)) + ")";
                        String timeStr = "";
                        try {
                            Instant modified = Files.getLastModifiedTime(p).toInstant();
                            timeStr = "  " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                    .withZone(ZoneId.systemDefault())
                                    .format(modified);
                        } catch (IOException ignored) {}
                        sb.append(prefix).append(dir.relativize(p)).append(size).append(timeStr).append("\n");
                    } catch (IOException e) {
                        sb.append(prefix).append(dir.relativize(p)).append("\n");
                    }
                }
            }
        }
    }

    @McpTool(
            name = "read_files",
            description = "Batch read multiple files with preview. Parameters: paths (comma-separated file paths), previewLines (preview lines per file, default 20).",
            tags = {"file", "read", "batch", "io"}
    )
    public String readFiles(String paths, int previewLines) {
        if (paths == null || paths.isBlank()) {
            return ToolResult.failure("paths must not be empty", null, "read_files").toJson();
        }
        if (previewLines <= 0) previewLines = 20;
        String[] pathArray = paths.split(",");
        StringBuilder result = new StringBuilder();
        int successCount = 0;
        int failCount = 0;
        List<String> warnings = new ArrayList<>();
        for (String p : pathArray) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) continue;
            try {
                Path filePath = resolveAndValidate(trimmed);
                if (!Files.exists(filePath)) {
                    warnings.add(trimmed + ": does not exist");
                    failCount++;
                    continue;
                }
                if (Files.isDirectory(filePath)) {
                    warnings.add(trimmed + ": is a directory, skipped");
                    failCount++;
                    continue;
                }
                List<String> allLines = Files.readAllLines(filePath);
                int preview = Math.min(previewLines, allLines.size());
                List<String> previewContent = allLines.subList(0, preview);
                result.append("=== ").append(trimmed)
                        .append(" (").append(preview).append("/").append(allLines.size()).append(" lines) ===\n");
                for (String line : previewContent) {
                    result.append(line).append("\n");
                }
                if (preview < allLines.size()) {
                    result.append("... (").append(allLines.size() - preview).append(" more lines)\n");
                }
                result.append("\n");
                successCount++;
            } catch (SecurityException e) {
                warnings.add(trimmed + ": " + e.getMessage());
                failCount++;
            } catch (IOException e) {
                warnings.add(trimmed + ": " + e.getMessage());
                failCount++;
            }
        }
        log.info("read_files: {} success, {} failed out of {} paths", successCount, failCount, pathArray.length);
        return ToolResult.success("Batch read completed: " + successCount + " success, " + failCount + " failed", null, "read_files")
                .withData(result.toString())
                .toJson()
                + "\n" + result + "\n# Summary: " + successCount + " success, " + failCount + " failed";
    }

    @McpTool(
            name = "file_info",
            description = "Get file metadata (size, modification time, line count, etc.). Parameter path: file path.",
            tags = {"file", "info", "metadata", "io"}
    )
    public String fileInfo(String path) {
        try {
            Path filePath = resolveAndValidate(path);
            if (!Files.exists(filePath)) {
                return ToolResult.failure("Path does not exist: " + path, path, "file_info").toJson();
            }
            StringBuilder info = new StringBuilder();
            info.append("Path: ").append(filePath).append("\n");
            info.append("Type: ").append(Files.isDirectory(filePath) ? "Directory" : "File").append("\n");
            info.append("Size: ").append(formatSize(Files.size(filePath))).append("\n");
            info.append("Readable: ").append(Files.isReadable(filePath)).append("\n");
            info.append("Writable: ").append(Files.isWritable(filePath)).append("\n");
            info.append("Executable: ").append(Files.isExecutable(filePath)).append("\n");
            try {
                Instant modified = Files.getLastModifiedTime(filePath).toInstant();
                info.append("Last Modified: ").append(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                .withZone(ZoneId.systemDefault())
                                .format(modified)).append("\n");
            } catch (IOException e) {
                info.append("Last Modified: unknown\n");
            }
            if (!Files.isDirectory(filePath)) {
                List<String> lines = Files.readAllLines(filePath);
                info.append("Lines: ").append(lines.size()).append("\n");
                info.append("Chars: ").append(Files.readString(filePath).length()).append("\n");
            }
            return ToolResult.success("File info retrieved", path, "file_info")
                    .withData(info.toString())
                    .toJson()
                    + "\n" + info;
        } catch (SecurityException e) {
            return ToolResult.failure(e.getMessage(), path, "file_info").toJson();
        } catch (IOException e) {
            log.error("Failed to get file info: {}", path, e);
            return ToolResult.failure("Failed to get file info: " + e.getMessage(), path, "file_info").toJson();
        }
    }

    private String listDirectoryInternal(Path dirPath, int depth) {
        try {
            StringBuilder sb = new StringBuilder();
            collectEntries(dirPath, depth, null, sb);
            String listing = sb.toString().trim();
            if (listing.isEmpty()) listing = "Directory is empty: " + dirPath;
            log.info("Directory listed: {} (depth={})", dirPath, depth);
            return ToolResult.success("Directory listed", dirPath.toString(), "read_file")
                    .withData(listing)
                    .toJson()
                    + "\nDirectory " + dirPath + " content:\n" + listing;
        } catch (IOException e) {
            log.error("Failed to list directory: {}", dirPath, e);
            return ToolResult.failure("Failed to list directory: " + e.getMessage(), dirPath.toString(), "read_file").toJson();
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static class MatchResult {
        final int lineNumber;
        final List<String> context;
        MatchResult(int lineNumber, List<String> context) {
            this.lineNumber = lineNumber;
            this.context = context;
        }
    }
}