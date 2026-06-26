package com.mcp.tools.tool.edit;

import com.mcp.tools.annotation.McpTool;
import com.mcp.tools.model.FileVersion;
import com.mcp.tools.model.PatchHunk;
import com.mcp.tools.service.WorkspaceFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 编辑工具集：统一 patch、行范围替换、插入、删除、写入、预览。
 * 所有修改操作支持 expectedHash/expectedVersion 版本锁。
 *
 * 编辑流程：agent 先读 → 拿到 fileHash → 构造 patch → 预览 → 带 expectedHash 提交。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EditToolSet {

    private final WorkspaceFileService fs;

    // ==================== apply_patch（核心统一入口） ====================

    @McpTool(
            name = "apply_patch",
            description = "Apply a list of hunks to a file atomically. Each hunk replaces lines [startLine, endLine] with newLines. Supports version locking via expectedHash or expectedVersion. Parameters: path (file path), hunks (JSON array of {startLine, endLine, newLines}), expectedHash (optional, SHA-256), expectedVersion (optional, hash prefix).",
            tags = {"file", "edit", "patch", "atomic"}
    )
    public String applyPatch(String path, String hunks, String expectedHash, String expectedVersion) {
        try {
            Path filePath = fs.resolve(path);
            if (!Files.exists(filePath)) {
                return fail("File does not exist: " + path, path, "apply_patch");
            }

            // 解析 hunks JSON
            List<PatchHunk> hunkList = parseHunks(hunks);
            if (hunkList.isEmpty()) {
                return fail("No hunks provided", path, "apply_patch");
            }

            // 版本锁校验
            try {
                fs.validateVersion(filePath, expectedHash, expectedVersion);
            } catch (WorkspaceFileService.VersionMismatchException e) {
                FileVersion current = fs.getVersion(filePath);
                return fail("Version mismatch: " + e.getMessage()
                        + ". Current version: " + current.version()
                        + ". Please re-read the file and retry.", path, "apply_patch");
            }

            // 记录操作前版本
            FileVersion versionBefore = fs.getVersion(filePath);

            // 备份
            String backupPath = fs.createBackup(filePath);

            // 读取原始行
            List<String> allLines = new ArrayList<>(fs.readAllLines(filePath));
            int totalLines = allLines.size();

            // 校验并排序 hunks（从后往前应用，避免行号偏移）
            List<PatchHunk> sorted = hunkList.stream()
                    .sorted((a, b) -> Integer.compare(b.startLine(), a.startLine()))
                    .toList();

            for (PatchHunk hunk : sorted) {
                int actualEnd = (hunk.endLine() < 0 || hunk.endLine() > totalLines)
                        ? totalLines : hunk.endLine();
                if (hunk.startLine() > totalLines) {
                    // 回滚
                    fs.rollback(filePath, backupPath);
                    return fail("Hunk startLine " + hunk.startLine()
                            + " exceeds total lines " + totalLines, path, "apply_patch");
                }
                // 应用 hunk
                allLines.subList(hunk.startLine() - 1, actualEnd).clear();
                allLines.addAll(hunk.startLine() - 1, hunk.newLines());
                totalLines = allLines.size();
            }

            // 原子写
            String newContent = String.join("\n", allLines);
            fs.atomicWrite(filePath, newContent);

            // 写后校验
            if (!fs.verifyWrite(filePath, newContent)) {
                fs.rollback(filePath, backupPath);
                return fail("Write verification failed. File restored from backup.", path, "apply_patch");
            }

            FileVersion versionAfter = fs.getVersion(filePath);
            int affectedLines = countAffected(hunkList);

            log.info("apply_patch: {} {} hunks, version {} -> {}",
                    path, hunkList.size(), versionBefore.version(), versionAfter.version());

            StringBuilder result = new StringBuilder();
            result.append("{\"ok\":true,\"tool\":\"apply_patch\",\"path\":\"")
                    .append(escapeJson(path)).append("\"");
            result.append(",\"versionBefore\":\"").append(versionBefore.version()).append("\"");
            result.append(",\"versionAfter\":\"").append(versionAfter.version()).append("\"");
            result.append(",\"backupPath\":\"").append(escapeJson(backupPath)).append("\"");
            result.append(",\"affectedLines\":").append(affectedLines);
            result.append(",\"hunkCount\":").append(hunkList.size());
            result.append(",\"message\":\"Patch applied: " + hunkList.size() + " hunks\"}");
            return result.toString();

        } catch (Exception e) {
            log.error("apply_patch failed: {}", path, e);
            return fail("Failed to apply patch: " + e.getMessage(), path, "apply_patch");
        }
    }

    // ==================== preview_edit（patch 预览） ====================

    @McpTool(
            name = "preview_edit",
            description = "Preview what applying a list of hunks would change, without modifying the file. Returns a unified-diff-like preview. Parameters: path (file path), hunks (JSON array of {startLine, endLine, newLines}).",
            tags = {"file", "edit", "preview", "diff"}
    )
    public String previewEdit(String path, String hunks) {
        try {
            Path filePath = fs.resolve(path);
            if (!Files.exists(filePath)) {
                return fail("File does not exist: " + path, path, "preview_edit");
            }

            List<PatchHunk> hunkList = parseHunks(hunks);
            if (hunkList.isEmpty()) {
                return fail("No hunks provided", path, "preview_edit");
            }

            List<String> allLines = fs.readAllLines(filePath);
            int totalLines = allLines.size();
            FileVersion ver = FileVersion.of(
                    String.join("\n", allLines),
                    Files.getLastModifiedTime(filePath).toMillis()
            );

            int contextLines = 3;
            StringBuilder diff = new StringBuilder();
            diff.append("--- ").append(path).append(" (version: ").append(ver.version()).append(")\n");
            diff.append("+++ ").append(path).append(" (preview)\n");

            List<PatchHunk> sorted = hunkList.stream()
                    .sorted((a, b) -> Integer.compare(a.startLine(), b.startLine()))
                    .toList();

            for (PatchHunk hunk : sorted) {
                int actualEnd = (hunk.endLine() < 0 || hunk.endLine() > totalLines)
                        ? totalLines : hunk.endLine();
                int ctxStart = Math.max(0, hunk.startLine() - 1 - contextLines);
                int ctxEnd = Math.min(totalLines - 1, actualEnd - 1 + contextLines);

                diff.append("@@ -").append(hunk.startLine()).append(",")
                        .append(actualEnd - hunk.startLine() + 1)
                        .append(" +").append(hunk.startLine()).append(",")
                        .append(hunk.newLines().size()).append(" @@\n");

                // 上下文（before）
                for (int i = ctxStart; i < hunk.startLine() - 1; i++) {
                    diff.append("  L").append(i + 1).append(": ").append(allLines.get(i)).append("\n");
                }
                // 删除的行
                for (int i = hunk.startLine() - 1; i < actualEnd; i++) {
                    diff.append("- L").append(i + 1).append(": ").append(allLines.get(i)).append("\n");
                }
                // 新增的行
                for (int i = 0; i < hunk.newLines().size(); i++) {
                    diff.append("+ L").append(hunk.startLine() + i).append(": ")
                            .append(hunk.newLines().get(i)).append("\n");
                }
                // 上下文（after）
                for (int i = actualEnd; i <= ctxEnd; i++) {
                    diff.append("  L").append(i + 1).append(": ").append(allLines.get(i)).append("\n");
                }
            }

            StringBuilder result = new StringBuilder();
            result.append("{\"ok\":true,\"tool\":\"preview_edit\",\"path\":\"")
                    .append(escapeJson(path)).append("\"");
            result.append(",\"version\":\"").append(ver.version()).append("\"");
            result.append(",\"hunkCount\":").append(hunkList.size());
            result.append(",\"preview\":\"").append(escapeJson(diff.toString())).append("\"");
            result.append(",\"message\":\"Preview generated for " + hunkList.size() + " hunks\"}");
            return result.toString();

        } catch (Exception e) {
            log.error("preview_edit failed: {}", path, e);
            return fail("Failed to preview edit: " + e.getMessage(), path, "preview_edit");
        }
    }

    // ==================== replace_file_range ====================

    @McpTool(
            name = "replace_file_range",
            description = "Replace a range of lines with new content. Supports version locking. Parameters: path (file path), startLine (1-based), endLine (1-based, -1 for EOF), content (new content), expectedHash (optional), expectedVersion (optional).",
            tags = {"file", "edit", "replace", "range"}
    )
    public String replaceFileRange(String path, int startLine, int endLine,
                                   String content, String expectedHash, String expectedVersion) {
        if (startLine < 1) {
            return fail("startLine must be >= 1", path, "replace_file_range");
        }
        try {
            Path filePath = fs.resolve(path);
            if (!Files.exists(filePath)) {
                return fail("File does not exist: " + path, path, "replace_file_range");
            }

            try {
                fs.validateVersion(filePath, expectedHash, expectedVersion);
            } catch (WorkspaceFileService.VersionMismatchException e) {
                FileVersion current = fs.getVersion(filePath);
                return fail("Version mismatch. Current: " + current.version()
                        + ". Re-read and retry.", path, "replace_file_range");
            }

            FileVersion versionBefore = fs.getVersion(filePath);
            String backupPath = fs.createBackup(filePath);

            List<String> allLines = new ArrayList<>(fs.readAllLines(filePath));
            int totalLines = allLines.size();

            if (startLine > totalLines) {
                return fail("startLine " + startLine + " exceeds total lines " + totalLines,
                        path, "replace_file_range");
            }
            int actualEnd = (endLine < 0 || endLine > totalLines) ? totalLines : endLine;
            if (actualEnd < startLine) {
                return fail("endLine " + endLine + " < startLine " + startLine,
                        path, "replace_file_range");
            }

            List<String> newLines = content == null || content.isEmpty()
                    ? List.of()
                    : List.of(content.split("\n", -1));

            allLines.subList(startLine - 1, actualEnd).clear();
            allLines.addAll(startLine - 1, newLines);

            String newContent = String.join("\n", allLines);
            fs.atomicWrite(filePath, newContent);

            if (!fs.verifyWrite(filePath, newContent)) {
                fs.rollback(filePath, backupPath);
                return fail("Write verification failed. Restored from backup.", path, "replace_file_range");
            }

            FileVersion versionAfter = fs.getVersion(filePath);
            log.info("replace_file_range: {} lines {}-{} -> {} lines, version {} -> {}",
                    path, startLine, actualEnd, newLines.size(),
                    versionBefore.version(), versionAfter.version());

            StringBuilder result = new StringBuilder();
            result.append("{\"ok\":true,\"tool\":\"replace_file_range\",\"path\":\"")
                    .append(escapeJson(path)).append("\"");
            result.append(",\"versionBefore\":\"").append(versionBefore.version()).append("\"");
            result.append(",\"versionAfter\":\"").append(versionAfter.version()).append("\"");
            result.append(",\"backupPath\":\"").append(escapeJson(backupPath)).append("\"");
            result.append(",\"affectedLines\":").append(actualEnd - startLine + 1);
            result.append(",\"message\":\"Range replaced: lines ")
                    .append(startLine).append("-").append(actualEnd)
                    .append(" replaced with ").append(newLines.size()).append(" lines\"}");
            return result.toString();

        } catch (Exception e) {
            log.error("replace_file_range failed: {}", path, e);
            return fail("Failed: " + e.getMessage(), path, "replace_file_range");
        }
    }

    // ==================== insert_at_line ====================

    @McpTool(
            name = "insert_at_line",
            description = "Insert content at a specific line. Parameters: path (file path), lineNumber (1-based, insert BEFORE this line; -1 to append), content (content to insert), after (if true, insert AFTER line), expectedHash (optional), expectedVersion (optional).",
            tags = {"file", "edit", "insert"}
    )
    public String insertAtLine(String path, int lineNumber, String content, boolean after,
                               String expectedHash, String expectedVersion) {
        if (content == null || content.isEmpty()) {
            return fail("content must not be empty", path, "insert_at_line");
        }
        try {
            Path filePath = fs.resolve(path);
            if (!Files.exists(filePath)) {
                return fail("File does not exist: " + path, path, "insert_at_line");
            }

            try {
                fs.validateVersion(filePath, expectedHash, expectedVersion);
            } catch (WorkspaceFileService.VersionMismatchException e) {
                FileVersion current = fs.getVersion(filePath);
                return fail("Version mismatch. Current: " + current.version(), path, "insert_at_line");
            }

            FileVersion versionBefore = fs.getVersion(filePath);
            String backupPath = fs.createBackup(filePath);

            List<String> allLines = new ArrayList<>(fs.readAllLines(filePath));
            int totalLines = allLines.size();
            List<String> insertLines = List.of(content.split("\n", -1));

            int insertPos;
            if (lineNumber < 0) {
                insertPos = totalLines;
            } else if (lineNumber == 0 || lineNumber > totalLines + 1) {
                return fail("lineNumber must be 1.." + (totalLines + 1) + " or -1 for append",
                        path, "insert_at_line");
            } else {
                insertPos = after ? lineNumber : lineNumber - 1;
            }

            allLines.addAll(insertPos, insertLines);

            String newContent = String.join("\n", allLines);
            fs.atomicWrite(filePath, newContent);
            fs.verifyWrite(filePath, newContent);

            FileVersion versionAfter = fs.getVersion(filePath);
            log.info("insert_at_line: {} {} line(s) at position {}, version {} -> {}",
                    path, insertLines.size(), insertPos + 1,
                    versionBefore.version(), versionAfter.version());

            StringBuilder result = new StringBuilder();
            result.append("{\"ok\":true,\"tool\":\"insert_at_line\",\"path\":\"")
                    .append(escapeJson(path)).append("\"");
            result.append(",\"versionBefore\":\"").append(versionBefore.version()).append("\"");
            result.append(",\"versionAfter\":\"").append(versionAfter.version()).append("\"");
            result.append(",\"backupPath\":\"").append(escapeJson(backupPath)).append("\"");
            result.append(",\"affectedLines\":").append(insertLines.size());
            result.append(",\"message\":\"Inserted " + insertLines.size()
                    + " lines at position " + (insertPos + 1) + "\"}");
            return result.toString();

        } catch (Exception e) {
            log.error("insert_at_line failed: {}", path, e);
            return fail("Failed: " + e.getMessage(), path, "insert_at_line");
        }
    }

    // ==================== delete_range ====================

    @McpTool(
            name = "delete_range",
            description = "Delete a range of lines. Parameters: path (file path), startLine (1-based), endLine (1-based, -1 for EOF), expectedHash (optional), expectedVersion (optional).",
            tags = {"file", "edit", "delete", "range"}
    )
    public String deleteRange(String path, int startLine, int endLine,
                              String expectedHash, String expectedVersion) {
        if (startLine < 1) {
            return fail("startLine must be >= 1", path, "delete_range");
        }
        try {
            Path filePath = fs.resolve(path);
            if (!Files.exists(filePath)) {
                return fail("File does not exist: " + path, path, "delete_range");
            }

            try {
                fs.validateVersion(filePath, expectedHash, expectedVersion);
            } catch (WorkspaceFileService.VersionMismatchException e) {
                FileVersion current = fs.getVersion(filePath);
                return fail("Version mismatch. Current: " + current.version(), path, "delete_range");
            }

            FileVersion versionBefore = fs.getVersion(filePath);
            String backupPath = fs.createBackup(filePath);

            List<String> allLines = new ArrayList<>(fs.readAllLines(filePath));
            int totalLines = allLines.size();

            if (startLine > totalLines) {
                return fail("startLine " + startLine + " exceeds total lines " + totalLines,
                        path, "delete_range");
            }
            int actualEnd = (endLine < 0 || endLine > totalLines) ? totalLines : endLine;
            if (actualEnd < startLine) {
                return fail("endLine " + endLine + " < startLine " + startLine, path, "delete_range");
            }

            int deletedCount = actualEnd - startLine + 1;
            allLines.subList(startLine - 1, actualEnd).clear();

            String newContent = String.join("\n", allLines);
            fs.atomicWrite(filePath, newContent);
            fs.verifyWrite(filePath, newContent);

            FileVersion versionAfter = fs.getVersion(filePath);
            log.info("delete_range: {} deleted lines {}-{}, version {} -> {}",
                    path, startLine, actualEnd, versionBefore.version(), versionAfter.version());

            StringBuilder result = new StringBuilder();
            result.append("{\"ok\":true,\"tool\":\"delete_range\",\"path\":\"")
                    .append(escapeJson(path)).append("\"");
            result.append(",\"versionBefore\":\"").append(versionBefore.version()).append("\"");
            result.append(",\"versionAfter\":\"").append(versionAfter.version()).append("\"");
            result.append(",\"backupPath\":\"").append(escapeJson(backupPath)).append("\"");
            result.append(",\"affectedLines\":").append(deletedCount);
            result.append(",\"message\":\"Deleted lines " + startLine + "-" + actualEnd
                    + " (" + deletedCount + " lines)\"}");
            return result.toString();

        } catch (Exception e) {
            log.error("delete_range failed: {}", path, e);
            return fail("Failed: " + e.getMessage(), path, "delete_range");
        }
    }

    // ==================== write_file ====================

    @McpTool(
            name = "write_file",
            description = "Create or overwrite a file with atomic write. Parameters: path (file path), content (file content), expectedHash (optional, for existing files).",
            tags = {"file", "write", "create"}
    )
    public String writeFile(String path, String content, String expectedHash) {
        if (content == null) {
            return fail("content must not be null", path, "write_file");
        }
        if (content.length() > WorkspaceFileService.MAX_CONTENT_SIZE) {
            return fail("Content size exceeds limit", path, "write_file");
        }
        try {
            Path filePath = fs.resolve(path);
            boolean existed = Files.exists(filePath);

            FileVersion versionBefore = existed ? fs.getVersion(filePath) : FileVersion.empty();
            String backupPath = existed ? fs.createBackup(filePath) : null;

            if (existed && expectedHash != null) {
                try {
                    fs.validateVersion(filePath, expectedHash, null);
                } catch (WorkspaceFileService.VersionMismatchException e) {
                    return fail("Version mismatch: " + e.getMessage(), path, "write_file");
                }
            }

            fs.atomicWrite(filePath, content);

            if (!fs.verifyWrite(filePath, content)) {
                fs.rollback(filePath, backupPath);
                return fail("Write verification failed. Restored from backup.", path, "write_file");
            }

            FileVersion versionAfter = fs.getVersion(filePath);
            log.info("write_file: {} ({} chars), version {} -> {}",
                    path, content.length(), versionBefore.version(), versionAfter.version());

            StringBuilder result = new StringBuilder();
            result.append("{\"ok\":true,\"tool\":\"write_file\",\"path\":\"")
                    .append(escapeJson(path)).append("\"");
            result.append(",\"versionBefore\":\"").append(versionBefore.version()).append("\"");
            result.append(",\"versionAfter\":\"").append(versionAfter.version()).append("\"");
            if (backupPath != null) {
                result.append(",\"backupPath\":\"").append(escapeJson(backupPath)).append("\"");
            }
            result.append(",\"affectedLines\":").append(content.split("\n", -1).length);
            result.append(",\"message\":\"").append(existed ? "File overwritten" : "File created")
                    .append(" successfully\"}");
            return result.toString();

        } catch (Exception e) {
            log.error("write_file failed: {}", path, e);
            return fail("Failed: " + e.getMessage(), path, "write_file");
        }
    }

    // ==================== append_file ====================

    @McpTool(
            name = "append_file",
            description = "Append content to the end of a file. Creates the file if it does not exist. Parameters: path (file path), content (content to append), expectedHash (optional).",
            tags = {"file", "edit", "append"}
    )
    public String appendFile(String path, String content, String expectedHash) {
        if (content == null || content.isEmpty()) {
            return fail("content must not be empty", path, "append_file");
        }
        try {
            Path filePath = fs.resolve(path);
            boolean existed = Files.exists(filePath);

            FileVersion versionBefore = existed ? fs.getVersion(filePath) : FileVersion.empty();
            String backupPath = existed ? fs.createBackup(filePath) : null;

            if (existed && expectedHash != null) {
                try {
                    fs.validateVersion(filePath, expectedHash, null);
                } catch (WorkspaceFileService.VersionMismatchException e) {
                    return fail("Version mismatch: " + e.getMessage(), path, "append_file");
                }
            }

            Path parentDir = filePath.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }

            if (!existed) {
                fs.atomicWrite(filePath, content);
            } else {
                Files.writeString(filePath, content, StandardOpenOption.APPEND);
            }

            FileVersion versionAfter = fs.getVersion(filePath);

            log.info("append_file: {} ({} chars appended), version {} -> {}",
                    path, content.length(), versionBefore.version(), versionAfter.version());

            StringBuilder result = new StringBuilder();
            result.append("{\"ok\":true,\"tool\":\"append_file\",\"path\":\"")
                    .append(escapeJson(path)).append("\"");
            result.append(",\"versionBefore\":\"").append(versionBefore.version()).append("\"");
            result.append(",\"versionAfter\":\"").append(versionAfter.version()).append("\"");
            if (backupPath != null) {
                result.append(",\"backupPath\":\"").append(escapeJson(backupPath)).append("\"");
            }
            result.append(",\"affectedLines\":").append(content.split("\n", -1).length);
            result.append(",\"message\":\"Content appended successfully\"}");
            return result.toString();

        } catch (Exception e) {
            log.error("append_file failed: {}", path, e);
            return fail("Failed: " + e.getMessage(), path, "append_file");
        }
    }

    // ==================== upsert_file ====================

    @McpTool(
            name = "upsert_file",
            description = "Create a file if it does not exist, or update it if it does. Parameters: path (file path), content (file content), mode ('overwrite' or 'append', default 'overwrite'), expectedHash (optional).",
            tags = {"file", "edit", "upsert"}
    )
    public String upsertFile(String path, String content, String mode, String expectedHash) {
        if (content == null) {
            return fail("content must not be null", path, "upsert_file");
        }
        if (content.length() > WorkspaceFileService.MAX_CONTENT_SIZE) {
            return fail("Content size exceeds limit", path, "upsert_file");
        }
        String effectiveMode = (mode != null && !mode.isBlank()) ? mode.toLowerCase() : "overwrite";
        if (!effectiveMode.equals("overwrite") && !effectiveMode.equals("append")) {
            return fail("mode must be 'overwrite' or 'append', got: " + mode, path, "upsert_file");
        }
        try {
            Path filePath = fs.resolve(path);
            boolean existed = Files.exists(filePath);

            FileVersion versionBefore = existed ? fs.getVersion(filePath) : FileVersion.empty();
            String backupPath = existed ? fs.createBackup(filePath) : null;

            if (existed && expectedHash != null && "overwrite".equals(effectiveMode)) {
                try {
                    fs.validateVersion(filePath, expectedHash, null);
                } catch (WorkspaceFileService.VersionMismatchException e) {
                    return fail("Version mismatch: " + e.getMessage(), path, "upsert_file");
                }
            }

            if (!existed || "overwrite".equals(effectiveMode)) {
                fs.atomicWrite(filePath, content);
            } else {
                Files.writeString(filePath, content, StandardOpenOption.APPEND);
            }

            if (!fs.verifyWrite(filePath, fs.readAll(filePath))) {
                fs.rollback(filePath, backupPath);
                return fail("Write verification failed", path, "upsert_file");
            }

            FileVersion versionAfter = fs.getVersion(filePath);
            String action = existed ? ("append".equals(effectiveMode) ? "appended to" : "overwritten") : "created";

            log.info("upsert_file: {} {} ({} chars), version {} -> {}",
                    path, action, content.length(), versionBefore.version(), versionAfter.version());

            StringBuilder result = new StringBuilder();
            result.append("{\"ok\":true,\"tool\":\"upsert_file\",\"path\":\"")
                    .append(escapeJson(path)).append("\"");
            result.append(",\"versionBefore\":\"").append(versionBefore.version()).append("\"");
            result.append(",\"versionAfter\":\"").append(versionAfter.version()).append("\"");
            if (backupPath != null) {
                result.append(",\"backupPath\":\"").append(escapeJson(backupPath)).append("\"");
            }
            result.append(",\"affectedLines\":").append(content.split("\n", -1).length);
            result.append(",\"message\":\"File ").append(action).append(" successfully\"}");
            return result.toString();

        } catch (Exception e) {
            log.error("upsert_file failed: {}", path, e);
            return fail("Failed: " + e.getMessage(), path, "upsert_file");
        }
    }

    // ==================== rename_file ====================

    @McpTool(
            name = "rename_file",
            description = "Rename a file within the same directory. Parameters: path (current file path), newName (new file name, not full path).",
            tags = {"file", "rename", "structure"}
    )
    public String renameFile(String path, String newName) {
        if (newName == null || newName.isBlank()) {
            return fail("newName must not be empty", path, "rename_file");
        }
        try {
            Path filePath = fs.resolve(path);
            if (!Files.exists(filePath)) {
                return fail("File does not exist: " + path, path, "rename_file");
            }
            Path targetPath = filePath.resolveSibling(newName);
            if (Files.exists(targetPath)) {
                return fail("Target already exists: " + newName, path, "rename_file");
            }
            if (!targetPath.toAbsolutePath().normalize().startsWith(fs.getWorkspaceRoot())) {
                return fail("Target path is outside workspace", path, "rename_file");
            }

            FileVersion versionBefore = fs.getVersion(filePath);
            Files.move(filePath, targetPath, StandardCopyOption.ATOMIC_MOVE);

            log.info("rename_file: {} -> {}", path, newName);

            StringBuilder result = new StringBuilder();
            result.append("{\"ok\":true,\"tool\":\"rename_file\",\"path\":\"")
                    .append(escapeJson(path)).append("\"");
            result.append(",\"newPath\":\"").append(escapeJson(targetPath.toString())).append("\"");
            result.append(",\"versionBefore\":\"").append(versionBefore.version()).append("\"");
            result.append(",\"message\":\"File renamed to ").append(newName).append("\"}");
            return result.toString();

        } catch (Exception e) {
            log.error("rename_file failed: {}", path, e);
            return fail("Failed: " + e.getMessage(), path, "rename_file");
        }
    }

    // ==================== move_file ====================

    @McpTool(
            name = "move_file",
            description = "Move a file to a different directory. Parameters: path (source file path), targetDir (target directory path), keepName (if true, keep original file name; if false, targetDir is the full new path).",
            tags = {"file", "move", "structure"}
    )
    public String moveFile(String path, String targetDir, boolean keepName) {
        if (targetDir == null || targetDir.isBlank()) {
            return fail("targetDir must not be empty", path, "move_file");
        }
        try {
            Path filePath = fs.resolve(path);
            if (!Files.exists(filePath)) {
                return fail("File does not exist: " + path, path, "move_file");
            }

            Path targetPath;
            if (keepName) {
                Path dir = fs.resolve(targetDir);
                Files.createDirectories(dir);
                targetPath = dir.resolve(filePath.getFileName());
            } else {
                targetPath = fs.resolve(targetDir);
            }

            if (Files.exists(targetPath)) {
                return fail("Target already exists: " + targetPath, path, "move_file");
            }

            FileVersion versionBefore = fs.getVersion(filePath);
            Files.createDirectories(targetPath.getParent());
            Files.move(filePath, targetPath, StandardCopyOption.ATOMIC_MOVE);

            log.info("move_file: {} -> {}", path, targetPath);

            StringBuilder result = new StringBuilder();
            result.append("{\"ok\":true,\"tool\":\"move_file\",\"path\":\"")
                    .append(escapeJson(path)).append("\"");
            result.append(",\"newPath\":\"").append(escapeJson(targetPath.toString())).append("\"");
            result.append(",\"versionBefore\":\"").append(versionBefore.version()).append("\"");
            result.append(",\"message\":\"File moved to ").append(targetDir).append("\"}");
            return result.toString();

        } catch (Exception e) {
            log.error("move_file failed: {}", path, e);
            return fail("Failed: " + e.getMessage(), path, "move_file");
        }
    }

    // ==================== ensure_dir ====================

    @McpTool(
            name = "ensure_dir",
            description = "Ensure a directory exists, creating parent directories as needed. Parameters: path (directory path).",
            tags = {"file", "directory", "structure"}
    )
    public String ensureDir(String path) {
        try {
            Path dirPath = fs.resolve(path);
            if (Files.exists(dirPath)) {
                if (!Files.isDirectory(dirPath)) {
                    return fail("Path exists but is not a directory: " + path, path, "ensure_dir");
                }
                StringBuilder result = new StringBuilder();
                result.append("{\"ok\":true,\"tool\":\"ensure_dir\",\"path\":\"")
                        .append(escapeJson(path)).append("\"");
                result.append(",\"created\":false");
                result.append(",\"message\":\"Directory already exists\"}");
                return result.toString();
            }
            Files.createDirectories(dirPath);
            log.info("ensure_dir: {} created", path);

            StringBuilder result = new StringBuilder();
            result.append("{\"ok\":true,\"tool\":\"ensure_dir\",\"path\":\"")
                    .append(escapeJson(path)).append("\"");
            result.append(",\"created\":true");
            result.append(",\"message\":\"Directory created\"}");
            return result.toString();

        } catch (Exception e) {
            log.error("ensure_dir failed: {}", path, e);
            return fail("Failed: " + e.getMessage(), path, "ensure_dir");
        }
    }

    // ==================== create_file ====================

    @McpTool(
            name = "create_file",
            description = "Create a new file. Fails if the file already exists. Parameters: path (file path), content (initial content, empty string for empty file).",
            tags = {"file", "create", "structure"}
    )
    public String createFile(String path, String content) {
        if (content == null) content = "";
        if (content.length() > WorkspaceFileService.MAX_CONTENT_SIZE) {
            return fail("Content size exceeds limit", path, "create_file");
        }
        try {
            Path filePath = fs.resolve(path);
            if (Files.exists(filePath)) {
                return fail("File already exists: " + path + ". Use write_file or upsert_file to overwrite.",
                        path, "create_file");
            }
            fs.atomicWrite(filePath, content);

            if (!fs.verifyWrite(filePath, content)) {
                return fail("Write verification failed", path, "create_file");
            }

            FileVersion versionAfter = fs.getVersion(filePath);
            log.info("create_file: {} ({} chars), version {}", path, content.length(), versionAfter.version());

            StringBuilder result = new StringBuilder();
            result.append("{\"ok\":true,\"tool\":\"create_file\",\"path\":\"")
                    .append(escapeJson(path)).append("\"");
            result.append(",\"versionAfter\":\"").append(versionAfter.version()).append("\"");
            result.append(",\"affectedLines\":").append(content.isEmpty() ? 0 : content.split("\n", -1).length);
            result.append(",\"message\":\"File created successfully\"}");
            return result.toString();

        } catch (Exception e) {
            log.error("create_file failed: {}", path, e);
            return fail("Failed: " + e.getMessage(), path, "create_file");
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 解析 hunks JSON 数组。
     * 格式: [{"startLine":10,"endLine":15,"newLines":["line1","line2"]}, ...]
     */
    private List<PatchHunk> parseHunks(String hunksJson) {
        List<PatchHunk> result = new ArrayList<>();
        if (hunksJson == null || hunksJson.isBlank()) return result;

        String trimmed = hunksJson.trim();
        if (!trimmed.startsWith("[")) return result;

        int i = 1;
        while (i < trimmed.length()) {
            char c = trimmed.charAt(i);
            if (c == ']') break;
            if (c == '{') {
                int objEnd = findMatchingBrace(trimmed, i);
                if (objEnd < 0) break;
                String obj = trimmed.substring(i, objEnd + 1);
                PatchHunk hunk = parseSingleHunk(obj);
                if (hunk != null) {
                    result.add(hunk);
                }
                i = objEnd + 1;
            } else {
                i++;
            }
        }
        return result;
    }

    private PatchHunk parseSingleHunk(String json) {
        try {
            int startLine = extractInt(json, "startLine");
            int endLine = extractInt(json, "endLine");
            List<String> newLines = extractStringArray(json, "newLines");
            if (startLine < 1) return null;
            return new PatchHunk(startLine, endLine, newLines != null ? newLines : List.of());
        } catch (Exception e) {
            log.warn("Failed to parse hunk: {}", json, e);
            return null;
        }
    }

    private int extractInt(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return 0;
        idx += search.length();
        while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) idx++;
        int end = idx;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        if (end == idx) return 0;
        return Integer.parseInt(json.substring(idx, end));
    }

    private List<String> extractStringArray(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return List.of();
        idx += search.length();
        while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) idx++;
        if (idx >= json.length() || json.charAt(idx) != '[') return List.of();

        int arrEnd = findMatchingBracket(json, idx);
        if (arrEnd < 0) return List.of();

        List<String> result = new ArrayList<>();
        String arrContent = json.substring(idx + 1, arrEnd);
        int pos = 0;
        while (pos < arrContent.length()) {
            char c = arrContent.charAt(pos);
            if (c == '"') {
                int strEnd = arrContent.indexOf('"', pos + 1);
                if (strEnd < 0) break;
                result.add(arrContent.substring(pos + 1, strEnd));
                pos = strEnd + 1;
            } else {
                pos++;
            }
        }
        return result;
    }

    private int findMatchingBrace(String s, int start) {
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private int findMatchingBracket(String s, int start) {
        char open = s.charAt(start);
        char close = (open == '[') ? ']' : '}';
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private int countAffected(List<PatchHunk> hunks) {
        return hunks.stream().mapToInt(h -> h.endLine() - h.startLine() + 1).sum();
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