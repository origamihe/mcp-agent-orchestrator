package com.mcp.tools.service;

import com.mcp.tools.model.FileVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 统一文件服务：路径校验、工作区限制、备份、原子写、版本/哈希校验。
 * 所有 ReadToolSet / EditToolSet / CodeAwareEditTool 都通过此服务访问文件系统。
 */
@Slf4j
@Service
public class WorkspaceFileService {

    private final Path workspaceRoot;
    public static final int MAX_CONTENT_SIZE = 10 * 1024 * 1024; // 10 MB

    public WorkspaceFileService(@Value("${mcp.workspace.root:}") String workspaceRootPath) {
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
        log.info("WorkspaceFileService initialized with root: {}", workspaceRoot);
    }

    // ==================== 路径解析与校验 ====================

    /**
     * 将路径解析为工作区内的绝对路径。
     * 支持相对路径（相对于 workspace root）和外部绝对路径。
     * 外部绝对路径会自动复制到 workspace/_external/ 目录下，返回副本路径。
     */
    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Path must not be null or blank");
        }

        Path inputPath = Path.of(relativePath);

        if (inputPath.isAbsolute()) {
            return resolveExternalPath(inputPath);
        }

        Path resolved = workspaceRoot.resolve(relativePath).toAbsolutePath().normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            throw new SecurityException(
                    "Access denied: path '" + relativePath + "' is outside workspace root");
        }
        return resolved;
    }

    /**
     * 处理外部绝对路径：将文件复制到 workspace/_external/ 目录下，返回副本路径。
     * 如果文件已存在且未修改，则复用已有副本。
     */
    private Path resolveExternalPath(Path externalPath) {
        Path normalized = externalPath.toAbsolutePath().normalize();

        if (!Files.exists(normalized)) {
            throw new SecurityException(
                    "External file not found: '" + normalized + "'");
        }

        try {
            Path externalDir = workspaceRoot.resolve("_external");
            Files.createDirectories(externalDir);

            String originalName = normalized.getFileName().toString();
            String safeName = sanitizeFilename(originalName);
            Path targetPath = externalDir.resolve(safeName);

            if (Files.exists(targetPath)) {
                if (Files.getLastModifiedTime(normalized).equals(Files.getLastModifiedTime(targetPath))
                        && Files.size(normalized) == Files.size(targetPath)) {
                    log.info("External file already synced: {} -> {}", normalized, targetPath);
                    return targetPath;
                }
                String uniqueName = UUID.randomUUID().toString().substring(0, 8) + "_" + safeName;
                targetPath = externalDir.resolve(uniqueName);
            }

            Files.copy(normalized, targetPath, StandardCopyOption.COPY_ATTRIBUTES);
            Files.setLastModifiedTime(targetPath, Files.getLastModifiedTime(normalized));
            log.info("External file copied into workspace: {} -> {}", normalized, targetPath);
            return targetPath;
        } catch (IOException e) {
            throw new SecurityException(
                    "Failed to copy external file '" + normalized + "' into workspace: " + e.getMessage());
        }
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[<>:\"/\\\\|?*]", "_");
    }

    // ==================== 读取 ====================

    /**
     * 读取文件全部内容。
     */
    public String readAll(Path filePath) throws IOException {
        if (Files.size(filePath) > MAX_CONTENT_SIZE) {
            throw new IOException("File size exceeds limit: " + filePath);
        }
        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    /**
     * 读取文件全部行（用于行级操作）。
     */
    public List<String> readAllLines(Path filePath) throws IOException {
        return Files.readAllLines(filePath, StandardCharsets.UTF_8);
    }

    /**
     * 获取文件版本信息（hash、行数、修改时间等）。
     */
    public FileVersion getVersion(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            return FileVersion.empty();
        }
        String content = readAll(filePath);
        long lastModified = Files.getLastModifiedTime(filePath).toMillis();
        return FileVersion.of(content, lastModified);
    }

    // ==================== 版本校验 ====================

    /**
     * 校验文件当前版本是否与 agent 读取时一致。
     * 如果 agent 传了 expectedHash 或 expectedVersion，校验不通过则抛出异常。
     */
    public void validateVersion(Path filePath, String expectedHash, String expectedVersion)
            throws IOException, VersionMismatchException {
        if (expectedHash == null && expectedVersion == null) {
            return; // 不校验
        }
        FileVersion current = getVersion(filePath);
        if (expectedHash != null && !current.fileHash().equals(expectedHash)) {
            throw new VersionMismatchException(
                    "File has been modified since last read. " +
                            "Expected hash: " + expectedHash.substring(0, Math.min(8, expectedHash.length())) +
                            ", current: " + current.version());
        }
        if (expectedVersion != null && !current.version().equals(expectedVersion)) {
            throw new VersionMismatchException(
                    "File version mismatch. Expected: " + expectedVersion +
                            ", current: " + current.version());
        }
    }

    // ==================== 备份 ====================

    /**
     * 为文件创建时间戳备份，返回备份路径。
     */
    public String createBackup(Path filePath) throws IOException {
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

    // ==================== 原子写 ====================

    /**
     * 原子写入：先写临时文件，再 rename 到目标路径。
     */
    public void atomicWrite(Path filePath, String content) throws IOException {
        Path parentDir = filePath.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }
        Path tempFile = Files.createTempFile(parentDir, "mcp-tmp-", ".tmp");
        try {
            Files.writeString(tempFile, content, StandardCharsets.UTF_8);
            Files.move(tempFile, filePath,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.debug("Atomic write completed: {}", filePath);
        } catch (Exception e) {
            try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            throw e;
        }
    }

    /**
     * 写后校验：重新读取文件，确认写入内容正确。
     */
    public boolean verifyWrite(Path filePath, String expectedContent) throws IOException {
        String actual = readAll(filePath);
        return actual.equals(expectedContent);
    }

    /**
     * 回滚：从备份恢复文件。
     */
    public void rollback(Path filePath, String backupPath) throws IOException {
        if (backupPath == null) return;
        Path backup = Path.of(backupPath);
        if (Files.exists(backup)) {
            Files.copy(backup, filePath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Rolled back from backup: {}", backupPath);
        }
    }

    // ==================== 辅助 ====================

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    /**
     * 版本不匹配异常。
     */
    public static class VersionMismatchException extends IOException {
        public VersionMismatchException(String message) {
            super(message);
        }
    }
}