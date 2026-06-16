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
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class FileWriteTool {

    private final Path workspaceRoot;
    private static final int MAX_CONTENT_SIZE = 10 * 1024 * 1024;

    public FileWriteTool(@Value("${mcp.workspace.root:}") String workspaceRootPath) {
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
        log.info("FileWriteTool initialized with workspace root: {}", workspaceRoot);
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

    private String createBackup(Path filePath) throws IOException {
        if (!Files.exists(filePath)) return null;
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());
        String backupName = filePath.getFileName().toString() + ".bak_" + timestamp;
        Path backupPath = filePath.resolveSibling(backupName);
        Files.copy(filePath, backupPath);
        log.info("Backup created: {}", backupPath);
        return backupPath.toString();
    }

    private void atomicWrite(Path filePath, String content) throws IOException {
        Path parentDir = filePath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        Path tempFile = Files.createTempFile(parentDir, "mcp-tmp-", ".tmp");
        try {
            Files.writeString(tempFile, content, StandardCharsets.UTF_8);
            Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.debug("Atomic write completed: {}", filePath);
        } catch (Exception e) {
            try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            throw e;
        }
    }

    @McpTool(
            name = "write_file",
            description = "Create or overwrite a file with specified content using atomic write. Parameters: path (file path), content (file content).",
            tags = {"file", "write", "io"}
    )
    public String writeFile(String path, String content) {
        if (content == null) {
            return ToolResult.failure("content must not be null", path, "write_file").toJson();
        }
        if (content.length() > MAX_CONTENT_SIZE) {
            return ToolResult.failure("Content size " + (content.length() / 1024) + " KB exceeds limit (" + (MAX_CONTENT_SIZE / (1024 * 1024)) + " MB)", path, "write_file").toJson();
        }
        try {
            Path filePath = resolveAndValidate(path);
            String backupPath = createBackup(filePath);
            atomicWrite(filePath, content);
            String writtenContent = Files.readString(filePath);
            if (!writtenContent.equals(content)) {
                if (backupPath != null) {
                    Files.copy(Path.of(backupPath), filePath, StandardCopyOption.REPLACE_EXISTING);
                }
                log.error("Write verification failed: {}", path);
                return ToolResult.failure("Write verification failed, content mismatch. Original file restored.", path, "write_file")
                        .withBackupPath(backupPath).toJson();
            }
            log.info("write_file: {} ({} chars)", path, content.length());
            ToolResult result = ToolResult.success("File written successfully", path, "write_file")
                    .withAffectedLines(content.split("\n", -1).length);
            if (backupPath != null) {
                result = result.withBackupPath(backupPath);
            }
            return result.toJson();
        } catch (SecurityException e) {
            return ToolResult.failure(e.getMessage(), path, "write_file").toJson();
        } catch (IOException e) {
            log.error("Failed to write file: {}", path, e);
            return ToolResult.failure("Failed to write file: " + e.getMessage(), path, "write_file").toJson();
        }
    }

    @McpTool(
            name = "append_file",
            description = "Append content to the end of a file. Creates the file if it does not exist. Parameters: path (file path), content (content to append).",
            tags = {"file", "append", "io"}
    )
    public String appendFile(String path, String content) {
        if (content == null) {
            return ToolResult.failure("content must not be null", path, "append_file").toJson();
        }
        if (content.length() > MAX_CONTENT_SIZE) {
            return ToolResult.failure("Content size exceeds limit", path, "append_file").toJson();
        }
        try {
            Path filePath = resolveAndValidate(path);
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            String backupPath = null;
            if (Files.exists(filePath) && Files.size(filePath) > 0) {
                backupPath = createBackup(filePath);
            }
            if (!Files.exists(filePath)) {
                atomicWrite(filePath, content);
            } else {
                Files.writeString(filePath, content, StandardOpenOption.APPEND);
            }
            log.info("append_file: {} ({} chars appended)", path, content.length());
            ToolResult result = ToolResult.success("Content appended successfully", path, "append_file")
                    .withAffectedLines(content.split("\n", -1).length);
            if (backupPath != null) {
                result = result.withBackupPath(backupPath);
            }
            return result.toJson();
        } catch (SecurityException e) {
            return ToolResult.failure(e.getMessage(), path, "append_file").toJson();
        } catch (IOException e) {
            log.error("Failed to append file: {}", path, e);
            return ToolResult.failure("Failed to append file: " + e.getMessage(), path, "append_file").toJson();
        }
    }

    @McpTool(
            name = "edit_file",
            description = "Find and replace exact text in a file. The old_str must match exactly and uniquely. Parameters: path (file path), old_str (text to find, must be exact and unique), new_str (replacement text).",
            tags = {"file", "edit", "replace", "io"}
    )
    public String editFile(String path, String old_str, String new_str) {
        if (old_str == null || old_str.isEmpty()) {
            return ToolResult.failure("old_str must not be empty", path, "edit_file").toJson();
        }
        if (new_str == null) new_str = "";
        try {
            Path filePath = resolveAndValidate(path);
            if (!Files.exists(filePath)) {
                return ToolResult.failure("File does not exist: " + path, path, "edit_file").toJson();
            }
            if (!Files.isReadable(filePath)) {
                return ToolResult.failure("File not readable: " + path, path, "edit_file").toJson();
            }
            String originalContent = Files.readString(filePath);
            if (!originalContent.contains(old_str)) {
                return ToolResult.failure("old_str not found in file. Ensure exact match including indentation and whitespace.", path, "edit_file").toJson();
            }
            int occurrenceCount = countOccurrences(originalContent, old_str);
            if (occurrenceCount > 1) {
                return ToolResult.failure("old_str found " + occurrenceCount + " times (not unique). Provide a longer, unique old_str.", path, "edit_file").toJson();
            }
            String backupPath = createBackup(filePath);
            String newContent = originalContent.replace(old_str, new_str);
            atomicWrite(filePath, newContent);
            int affectedLines = countAffectedLines(originalContent, newContent);
            log.info("edit_file: {} (1 replacement, {} lines affected)", path, affectedLines);
            ToolResult result = ToolResult.success("File edited successfully: 1 replacement", path, "edit_file")
                    .withAffectedLines(affectedLines);
            if (backupPath != null) {
                result = result.withBackupPath(backupPath);
            }
            return result.toJson();
        } catch (SecurityException e) {
            return ToolResult.failure(e.getMessage(), path, "edit_file").toJson();
        } catch (IOException e) {
            log.error("Failed to edit file: {}", path, e);
            return ToolResult.failure("Failed to edit file: " + e.getMessage(), path, "edit_file").toJson();
        }
    }

    @McpTool(
            name = "replace_file_range",
            description = "Replace a range of lines in a file with new content. Parameters: path (file path), startLine (1-based, inclusive), endLine (1-based, inclusive, -1 for end of file), content (new content for the range).",
            tags = {"file", "edit", "range", "replace", "io"}
    )
    public String replaceFileRange(String path, int startLine, int endLine, String content) {
        if (content == null) content = "";
        if (startLine < 1) {
            return ToolResult.failure("startLine must be >= 1, got: " + startLine, path, "replace_file_range").toJson();
        }
        try {
            Path filePath = resolveAndValidate(path);
            if (!Files.exists(filePath)) {
                return ToolResult.failure("File does not exist: " + path, path, "replace_file_range").toJson();
            }
            List<String> allLines = new ArrayList<>(Files.readAllLines(filePath));
            int totalLines = allLines.size();
            if (startLine > totalLines) {
                return ToolResult.failure("startLine " + startLine + " exceeds total lines " + totalLines, path, "replace_file_range").toJson();
            }
            int actualEnd = (endLine < 0 || endLine > totalLines) ? totalLines : endLine;
            if (actualEnd < startLine) {
                return ToolResult.failure("endLine " + endLine + " is less than startLine " + startLine, path, "replace_file_range").toJson();
            }
            int replacedCount = actualEnd - startLine + 1;
            String backupPath = createBackup(filePath);
            List<String> newContentLines = content.isEmpty() ? new ArrayList<>() :
                    new ArrayList<>(List.of(content.split("\n", -1)));
            allLines.subList(startLine - 1, actualEnd).clear();
            allLines.addAll(startLine - 1, newContentLines);
            String newFileContent = String.join("\n", allLines);
            atomicWrite(filePath, newFileContent);
            log.info("replace_file_range: {} lines {}-{} replaced with {} lines", path, startLine, actualEnd, newContentLines.size());
            ToolResult result = ToolResult.success("Range replaced: lines " + startLine + "-" + actualEnd + " replaced with " + newContentLines.size() + " lines", path, "replace_file_range")
                    .withAffectedLines(replacedCount);
            if (backupPath != null) {
                result = result.withBackupPath(backupPath);
            }
            return result.toJson();
        } catch (SecurityException e) {
            return ToolResult.failure(e.getMessage(), path, "replace_file_range").toJson();
        } catch (IOException e) {
            log.error("Failed to replace file range: {}", path, e);
            return ToolResult.failure("Failed to replace file range: " + e.getMessage(), path, "replace_file_range").toJson();
        }
    }

    @McpTool(
            name = "insert_file",
            description = "Insert content at a specific line in a file. Parameters: path (file path), lineNumber (1-based line number to insert BEFORE, -1 to append at end), content (content to insert), after (if true, insert AFTER the line number instead of before, default false).",
            tags = {"file", "edit", "insert", "io"}
    )
    public String insertFile(String path, int lineNumber, String content, boolean after) {
        if (content == null || content.isEmpty()) {
            return ToolResult.failure("content must not be empty", path, "insert_file").toJson();
        }
        try {
            Path filePath = resolveAndValidate(path);
            if (!Files.exists(filePath)) {
                return ToolResult.failure("File does not exist: " + path, path, "insert_file").toJson();
            }
            List<String> allLines = new ArrayList<>(Files.readAllLines(filePath));
            int totalLines = allLines.size();
            String backupPath = createBackup(filePath);
            List<String> insertLines = new ArrayList<>(List.of(content.split("\n", -1)));
            int insertPosition;
            if (lineNumber < 0) {
                insertPosition = allLines.size();
            } else if (lineNumber == 0 || lineNumber > totalLines + 1) {
                return ToolResult.failure("lineNumber must be between 1 and " + (totalLines + 1) + ", or -1 for append", path, "insert_file").toJson();
            } else {
                insertPosition = after ? lineNumber : lineNumber - 1;
            }
            allLines.addAll(insertPosition, insertLines);
            String newFileContent = String.join("\n", allLines);
            atomicWrite(filePath, newFileContent);
            log.info("insert_file: {} {} {} line(s) at position {}", path, after ? "after" : "before", insertLines.size(), insertPosition);
            ToolResult result = ToolResult.success("Content inserted: " + insertLines.size() + " lines at position " + (insertPosition + 1), path, "insert_file")
                    .withAffectedLines(insertLines.size());
            if (backupPath != null) {
                result = result.withBackupPath(backupPath);
            }
            return result.toJson();
        } catch (SecurityException e) {
            return ToolResult.failure(e.getMessage(), path, "insert_file").toJson();
        } catch (IOException e) {
            log.error("Failed to insert into file: {}", path, e);
            return ToolResult.failure("Failed to insert into file: " + e.getMessage(), path, "insert_file").toJson();
        }
    }

    @McpTool(
            name = "delete_file_range",
            description = "Delete a range of lines from a file. Parameters: path (file path), startLine (1-based, inclusive), endLine (1-based, inclusive, -1 for end of file).",
            tags = {"file", "edit", "delete", "range", "io"}
    )
    public String deleteFileRange(String path, int startLine, int endLine) {
        if (startLine < 1) {
            return ToolResult.failure("startLine must be >= 1, got: " + startLine, path, "delete_file_range").toJson();
        }
        try {
            Path filePath = resolveAndValidate(path);
            if (!Files.exists(filePath)) {
                return ToolResult.failure("File does not exist: " + path, path, "delete_file_range").toJson();
            }
            List<String> allLines = new ArrayList<>(Files.readAllLines(filePath));
            int totalLines = allLines.size();
            if (startLine > totalLines) {
                return ToolResult.failure("startLine " + startLine + " exceeds total lines " + totalLines, path, "delete_file_range").toJson();
            }
            int actualEnd = (endLine < 0 || endLine > totalLines) ? totalLines : endLine;
            if (actualEnd < startLine) {
                return ToolResult.failure("endLine " + endLine + " is less than startLine " + startLine, path, "delete_file_range").toJson();
            }
            int deletedCount = actualEnd - startLine + 1;
            String backupPath = createBackup(filePath);
            allLines.subList(startLine - 1, actualEnd).clear();
            String newFileContent = String.join("\n", allLines);
            atomicWrite(filePath, newFileContent);
            log.info("delete_file_range: {} deleted lines {}-{}", path, startLine, actualEnd);
            ToolResult result = ToolResult.success("Deleted lines " + startLine + "-" + actualEnd + " (" + deletedCount + " lines)", path, "delete_file_range")
                    .withAffectedLines(deletedCount);
            if (backupPath != null) {
                result = result.withBackupPath(backupPath);
            }
            return result.toJson();
        } catch (SecurityException e) {
            return ToolResult.failure(e.getMessage(), path, "delete_file_range").toJson();
        } catch (IOException e) {
            log.error("Failed to delete file range: {}", path, e);
            return ToolResult.failure("Failed to delete file range: " + e.getMessage(), path, "delete_file_range").toJson();
        }
    }

    @McpTool(
            name = "upsert_file",
            description = "Create a file if it does not exist, or update it if it does. Parameters: path (file path), content (file content), mode (update strategy: 'overwrite' to replace entirely, 'append' to append, default 'overwrite').",
            tags = {"file", "write", "upsert", "io"}
    )
    public String upsertFile(String path, String content, String mode) {
        if (content == null) {
            return ToolResult.failure("content must not be null", path, "upsert_file").toJson();
        }
        if (content.length() > MAX_CONTENT_SIZE) {
            return ToolResult.failure("Content size exceeds limit", path, "upsert_file").toJson();
        }
        String effectiveMode = (mode != null && !mode.isBlank()) ? mode.toLowerCase() : "overwrite";
        if (!effectiveMode.equals("overwrite") && !effectiveMode.equals("append")) {
            return ToolResult.failure("mode must be 'overwrite' or 'append', got: " + mode, path, "upsert_file").toJson();
        }
        try {
            Path filePath = resolveAndValidate(path);
            String backupPath = null;
            boolean existed = Files.exists(filePath);
            if (existed) {
                backupPath = createBackup(filePath);
            }
            if (!existed || "overwrite".equals(effectiveMode)) {
                atomicWrite(filePath, content);
            } else {
                Files.writeString(filePath, content, StandardOpenOption.APPEND);
            }
            String action = existed ? ("append".equals(effectiveMode) ? "appended to" : "overwritten") : "created";
            log.info("upsert_file: {} {} ({} chars)", path, action, content.length());
            ToolResult result = ToolResult.success("File " + action + " successfully", path, "upsert_file")
                    .withAffectedLines(content.split("\n", -1).length);
            if (backupPath != null) {
                result = result.withBackupPath(backupPath);
            }
            return result.toJson();
        } catch (SecurityException e) {
            return ToolResult.failure(e.getMessage(), path, "upsert_file").toJson();
        } catch (IOException e) {
            log.error("Failed to upsert file: {}", path, e);
            return ToolResult.failure("Failed to upsert file: " + e.getMessage(), path, "upsert_file").toJson();
        }
    }

    @McpTool(
            name = "preview_edit",
            description = "Preview what an edit_file operation would change without actually modifying the file. Parameters: path (file path), old_str (text to find), new_str (replacement text).",
            tags = {"file", "edit", "preview", "io"}
    )
    public String previewEdit(String path, String old_str, String new_str) {
        if (old_str == null || old_str.isEmpty()) {
            return ToolResult.failure("old_str must not be empty", path, "preview_edit").toJson();
        }
        if (new_str == null) new_str = "";
        try {
            Path filePath = resolveAndValidate(path);
            if (!Files.exists(filePath)) {
                return ToolResult.failure("File does not exist: " + path, path, "preview_edit").toJson();
            }
            String originalContent = Files.readString(filePath);
            if (!originalContent.contains(old_str)) {
                return ToolResult.failure("old_str not found in file.", path, "preview_edit").toJson();
            }
            int occurrenceCount = countOccurrences(originalContent, old_str);
            if (occurrenceCount > 1) {
                return ToolResult.failure("old_str found " + occurrenceCount + " times (not unique). Preview not available for ambiguous matches.", path, "preview_edit").toJson();
            }
            int matchStart = originalContent.indexOf(old_str);
            int matchLineStart = originalContent.substring(0, matchStart).split("\n", -1).length;
            int matchLineEnd = matchLineStart + old_str.split("\n", -1).length - 1;
            List<String> allLines = Files.readAllLines(filePath);
            int contextLines = 3;
            int ctxStart = Math.max(0, matchLineStart - 1 - contextLines);
            int ctxEnd = Math.min(allLines.size() - 1, matchLineEnd - 1 + contextLines);
            StringBuilder preview = new StringBuilder();
            preview.append("=== Preview of edit_file ===\n");
            preview.append("File: ").append(path).append("\n");
            preview.append("Match at lines ").append(matchLineStart).append("-").append(matchLineEnd).append("\n");
            preview.append("Occurrences: ").append(occurrenceCount).append(" (unique, safe to edit)\n\n");
            preview.append("--- Context (before) ---\n");
            for (int i = ctxStart; i < matchLineStart - 1; i++) {
                preview.append("  L").append(i + 1).append(": ").append(allLines.get(i)).append("\n");
            }
            preview.append("--- Will be replaced ---\n");
            for (int i = matchLineStart - 1; i < matchLineEnd; i++) {
                preview.append("- L").append(i + 1).append(": ").append(allLines.get(i)).append("\n");
            }
            preview.append("--- Will become ---\n");
            String[] newLines = new_str.split("\n", -1);
            for (int i = 0; i < newLines.length; i++) {
                preview.append("+ L").append(matchLineStart + i).append(": ").append(newLines[i]).append("\n");
            }
            preview.append("--- Context (after) ---\n");
            for (int i = matchLineEnd; i <= ctxEnd; i++) {
                preview.append("  L").append(i + 1).append(": ").append(allLines.get(i)).append("\n");
            }
            return ToolResult.success("Preview generated", path, "preview_edit")
                    .withAffectedLines(matchLineEnd - matchLineStart + 1)
                    .toJson()
                    + "\n" + preview;
        } catch (SecurityException e) {
            return ToolResult.failure(e.getMessage(), path, "preview_edit").toJson();
        } catch (IOException e) {
            log.error("Failed to preview edit: {}", path, e);
            return ToolResult.failure("Failed to preview edit: " + e.getMessage(), path, "preview_edit").toJson();
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

    private int countAffectedLines(String original, String modified) {
        String[] origLines = original.split("\n", -1);
        String[] modLines = modified.split("\n", -1);
        int minLen = Math.min(origLines.length, modLines.length);
        int affected = Math.abs(origLines.length - modLines.length);
        for (int i = 0; i < minLen; i++) {
            if (!origLines[i].equals(modLines[i])) affected++;
        }
        return affected;
    }
}