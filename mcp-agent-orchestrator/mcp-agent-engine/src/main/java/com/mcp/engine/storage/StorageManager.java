package com.mcp.engine.storage;

import com.mcp.common.artifact.Artifact;
import com.mcp.common.artifact.ArtifactType;
import com.mcp.common.storage.FileInfo;
import com.mcp.common.storage.StorageQuota;
import com.mcp.common.storage.StorageStats;
import com.mcp.common.workspace.Workspace;
import com.mcp.engine.artifact.ArtifactService;
import com.mcp.engine.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * StorageManager — Agent Runtime 统一存储管理器。
 *
 * 核心职责：
 * 1. 文件系统操作（读/写/列/删）— 所有文件 I/O 的唯一入口
 * 2. 存储配额管理 — 防止磁盘/DB 溢出
 * 3. 清理协调 — 过期文件、Artifact、Workspace 的定期清理
 * 4. Artifact ↔ 文件系统同步
 *
 * 设计原则：
 * - 所有文件操作必须通过 StorageManager，不允许绕过
 * - 写入操作自动检查配额
 * - 大文件操作自动记录日志
 * - 错误统一处理，不抛异常到调用方
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageManager {

    private final ArtifactService artifactService;
    private final WorkspaceService workspaceService;

    private final StorageQuota quota = new StorageQuota();

    // ==================== 文件系统操作 ====================

    /**
     * 读取文件内容（UTF-8）。
     *
     * @param filePath 文件路径（绝对路径或相对项目根目录）
     * @return 文件内容，若文件不存在或不可读则返回 empty
     */
    public Optional<String> readFile(String filePath) {
        return readFile(Path.of(filePath));
    }

    public Optional<String> readFile(Path path) {
        try {
            if (!Files.exists(path)) {
                log.warn("[StorageManager] File not found: {}", path);
                return Optional.empty();
            }
            if (!Files.isReadable(path)) {
                log.warn("[StorageManager] File not readable: {}", path);
                return Optional.empty();
            }
            if (Files.size(path) > quota.getMaxFileSizeBytes()) {
                log.warn("[StorageManager] File exceeds size limit: {} ({} bytes, max={})",
                        path, Files.size(path), quota.getMaxFileSizeBytes());
                return Optional.empty();
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            log.debug("[StorageManager] Read file: {} ({} chars)", path, content.length());
            return Optional.of(content);
        } catch (IOException e) {
            log.error("[StorageManager] Failed to read file: {}", path, e);
            return Optional.empty();
        }
    }

    /**
     * 写入文件内容到磁盘。
     * 自动创建父目录，检查配额，记录操作。
     *
     * @param filePath 目标文件路径
     * @param content  文件内容
     * @return 是否写入成功
     */
    public boolean writeFile(String filePath, String content) {
        return writeFile(Path.of(filePath), content);
    }

    public boolean writeFile(Path path, String content) {
        try {
            if (content == null) {
                log.warn("[StorageManager] Cannot write null content to: {}", path);
                return false;
            }
            long contentSize = content.getBytes(StandardCharsets.UTF_8).length;
            if (contentSize > quota.getMaxFileSizeBytes()) {
                log.warn("[StorageManager] Content exceeds size limit: {} ({} bytes, max={})",
                        path, contentSize, quota.getMaxFileSizeBytes());
                return false;
            }

            checkQuotaBeforeWrite(contentSize);

            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
                log.debug("[StorageManager] Created parent directories: {}", parent);
            }

            Files.writeString(path, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("[StorageManager] Wrote file: {} ({} bytes)", path, contentSize);
            return true;
        } catch (IOException e) {
            log.error("[StorageManager] Failed to write file: {}", path, e);
            return false;
        }
    }

    /**
     * 追加内容到文件末尾。
     */
    public boolean appendToFile(String filePath, String content) {
        return appendToFile(Path.of(filePath), content);
    }

    public boolean appendToFile(Path path, String content) {
        try {
            if (content == null) return false;

            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Files.writeString(path, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("[StorageManager] Appended to file: {} ({} chars)", path, content.length());
            return true;
        } catch (IOException e) {
            log.error("[StorageManager] Failed to append to file: {}", path, e);
            return false;
        }
    }

    /**
     * 列出目录内容。
     *
     * @param dirPath   目录路径
     * @param recursive 是否递归
     * @return 文件信息列表
     */
    public List<FileInfo> listDirectory(String dirPath, boolean recursive) {
        return listDirectory(Path.of(dirPath), recursive);
    }

    public List<FileInfo> listDirectory(Path dir, boolean recursive) {
        List<FileInfo> result = new ArrayList<>();
        try {
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                log.warn("[StorageManager] Not a directory: {}", dir);
                return result;
            }
            try (Stream<Path> stream = recursive ? Files.walk(dir) : Files.list(dir)) {
                stream.filter(p -> !p.equals(dir))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(p -> {
                            FileInfo info = new FileInfo(
                                    p.toString(),
                                    p.getFileName().toString(),
                                    Files.isDirectory(p),
                                    getFileSize(p),
                                    getLastModifiedTime(p)
                            );
                            info.setReadable(Files.isReadable(p));
                            info.setWritable(Files.isWritable(p));
                            info.setLanguage(detectLanguage(p.getFileName().toString()));
                            result.add(info);
                        });
            }
            log.debug("[StorageManager] Listed directory: {} ({} entries)", dir, result.size());
        } catch (IOException e) {
            log.error("[StorageManager] Failed to list directory: {}", dir, e);
        }
        return result;
    }

    /**
     * 删除文件。
     */
    public boolean deleteFile(String filePath) {
        return deleteFile(Path.of(filePath));
    }

    public boolean deleteFile(Path path) {
        try {
            if (!Files.exists(path)) {
                log.warn("[StorageManager] File not found for deletion: {}", path);
                return false;
            }
            Files.delete(path);
            log.info("[StorageManager] Deleted file: {}", path);
            return true;
        } catch (IOException e) {
            log.error("[StorageManager] Failed to delete file: {}", path, e);
            return false;
        }
    }

    /**
     * 检查文件是否存在。
     */
    public boolean fileExists(String filePath) {
        return Files.exists(Path.of(filePath));
    }

    // ==================== Artifact ↔ 文件同步 ====================

    /**
     * 将 Artifact 内容写入文件系统。
     */
    public boolean saveArtifactToFile(Artifact artifact, String filePath) {
        if (artifact == null || artifact.getContent() == null) {
            log.warn("[StorageManager] Cannot save null artifact to file");
            return false;
        }
        boolean success = writeFile(filePath, artifact.getContent());
        if (success) {
            log.info("[StorageManager] Saved artifact to file: {} -> {}",
                    artifact.getTitle(), filePath);
        }
        return success;
    }

    /**
     * 从文件系统加载文件为 Artifact。
     */
    public Optional<Artifact> loadFileAsArtifact(String filePath, String sessionId) {
        return readFile(filePath).map(content -> {
            ArtifactType type = detectArtifactType(filePath);
            Artifact artifact = new Artifact(filePath, type, content, "UTF-8", content.length());
            artifact.setTitle(extractTitle(filePath));
            artifact.setCreatedBy("storage-manager");
            artifact.addMetadata("source", "filesystem");
            artifact.addMetadata("filePath", filePath);
            artifactService.saveArtifact(sessionId, artifact);
            log.info("[StorageManager] Loaded file as artifact: {} -> type={}, session={}",
                    filePath, type, sessionId);
            return artifact;
        });
    }

    // ==================== Workspace 文件同步 ====================

    /**
     * 将 Workspace 中已打开的文件同步到文件系统。
     * 用于 "保存所有修改" 场景。
     */
    public int syncWorkspaceToDisk(Workspace workspace) {
        if (workspace == null || workspace.getOpenedFiles().isEmpty()) return 0;

        int count = 0;
        for (var entry : workspace.getOpenedFiles().entrySet()) {
            String path = entry.getKey();
            Workspace.OpenedFile file = entry.getValue();
            if (file.getContent() != null && writeFile(path, file.getContent())) {
                workspace.getOpenedFile(path).ifPresent(f -> f.setMtime(Instant.now()));
                count++;
            }
        }
        if (count > 0) {
            workspace.markDirty();
            workspaceService.save(workspace);
            log.info("[StorageManager] Synced {} files from workspace {} to disk",
                    count, workspace.getWorkspaceId());
        }
        return count;
    }

    /**
     * 将 Workspace 中的文件重新加载到内存（刷新缓存）。
     */
    public int refreshWorkspaceFromDisk(Workspace workspace) {
        if (workspace == null || workspace.getOpenedFiles().isEmpty()) return 0;

        AtomicInteger count = new AtomicInteger(0);
        for (var entry : workspace.getOpenedFiles().entrySet()) {
            String path = entry.getKey();
            readFile(path).ifPresent(content -> {
                workspace.getOpenedFile(path).ifPresent(f -> {
                    f.setContent(content);
                    f.setMtime(getLastModifiedTime(Path.of(path)));
                    f.setReadAt(Instant.now());
                });
                count.incrementAndGet();
            });
        }
        int refreshed = count.get();
        if (refreshed > 0) {
            workspace.markDirty();
            workspaceService.save(workspace);
            log.info("[StorageManager] Refreshed {} files in workspace {} from disk",
                    refreshed, workspace.getWorkspaceId());
        }
        return refreshed;
    }

    // ==================== 存储统计 & 配额 ====================

    /**
     * 收集存储统计信息。
     */
    public StorageStats collectStats() {
        StorageStats stats = new StorageStats();

        try {
            List<Artifact> artifacts = artifactService.findBySession("global");
            stats.setTotalArtifacts(artifacts.size());
        } catch (Exception e) {
            log.warn("[StorageManager] Failed to count artifacts: {}", e.getMessage());
        }

        stats.setTotalDiskUsageBytes(estimateDiskUsage());

        log.info("[StorageManager] Stats collected: {}", stats);
        return stats;
    }

    private long estimateDiskUsage() {
        long total = 0;
        try {
            String userDir = System.getProperty("user.dir");
            Path storageDir = Path.of(userDir, "storage");
            if (Files.exists(storageDir)) {
                try (Stream<Path> stream = Files.walk(storageDir)) {
                    total = stream.filter(Files::isRegularFile)
                            .mapToLong(this::getFileSize)
                            .sum();
                }
            }
        } catch (IOException e) {
            log.warn("[StorageManager] Failed to estimate disk usage: {}", e.getMessage());
        }
        return total;
    }

    private void checkQuotaBeforeWrite(long additionalBytes) {
        if (!quota.isAutoCleanup()) return;

        StorageStats stats = collectStats();
        long currentUsage = stats.getTotalDiskUsageBytes();
        long maxUsage = quota.getMaxDiskUsageBytes();
        double ratio = (double) currentUsage / maxUsage;

        if (ratio > quota.getCleanupThreshold()) {
            log.warn("[StorageManager] Storage usage {}% exceeds threshold {}%, triggering cleanup",
                    String.format("%.1f", ratio * 100), String.format("%.0f", quota.getCleanupThreshold() * 100));
            cleanup();
        }
    }

    /**
     * 执行存储清理。
     * 清理过期 Artifact、临时文件等。
     *
     * @return 清理的项目数
     */
    public int cleanup() {
        int cleaned = 0;
        Instant cutoff = Instant.now().minusSeconds(quota.getRetentionDays() * 86400L);

        try {
            String userDir = System.getProperty("user.dir");
            Path tempDir = Path.of(userDir, "temp");
            if (Files.exists(tempDir)) {
                try (Stream<Path> stream = Files.list(tempDir)) {
                    for (Path p : stream.toList()) {
                        try {
                            if (Files.getLastModifiedTime(p).toInstant().isBefore(cutoff)) {
                                Files.deleteIfExists(p);
                                cleaned++;
                                log.debug("[StorageManager] Cleaned temp file: {}", p);
                            }
                        } catch (IOException e) {
                            log.warn("[StorageManager] Failed to clean temp file: {} | reason={}", p, e.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("[StorageManager] Cleanup error: {}", e.getMessage());
        }

        log.info("[StorageManager] Cleanup completed: {} items removed", cleaned);
        return cleaned;
    }

    /**
     * 获取存储配额配置。
     */
    public StorageQuota getQuota() {
        return quota;
    }

    // ==================== 工具方法 ====================

    private long getFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    private Instant getLastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    private static String detectLanguage(String path) {
        if (path == null) return null;
        String lower = path.toLowerCase();
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".js")) return "javascript";
        if (lower.endsWith(".ts")) return "typescript";
        if (lower.endsWith(".go")) return "go";
        if (lower.endsWith(".rs")) return "rust";
        if (lower.endsWith(".sql")) return "sql";
        if (lower.endsWith(".md")) return "markdown";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "yaml";
        if (lower.endsWith(".html")) return "html";
        if (lower.endsWith(".css")) return "css";
        if (lower.endsWith(".sh") || lower.endsWith(".bat")) return "shell";
        return null;
    }

    private static ArtifactType detectArtifactType(String filePath) {
        if (filePath == null) return ArtifactType.TEXT;
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".ts")
                || lower.endsWith(".go") || lower.endsWith(".rs") || lower.endsWith(".cpp") || lower.endsWith(".c")) {
            return ArtifactType.CODE;
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return ArtifactType.MARKDOWN;
        if (lower.endsWith(".sql")) return ArtifactType.SQL;
        if (lower.endsWith(".txt")) return ArtifactType.TEXT;
        if (lower.endsWith(".prompt")) return ArtifactType.PROMPT;
        if (lower.endsWith(".diff") || lower.endsWith(".patch")) return ArtifactType.DIFF;
        if (lower.endsWith(".log")) return ArtifactType.LOG;
        if (lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".properties")
                || lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".toml")) {
            return ArtifactType.CONFIG;
        }
        return ArtifactType.FILE;
    }

    private static String extractTitle(String filePath) {
        if (filePath == null) return "Untitled";
        String name = filePath;
        if (name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1);
        if (name.contains("\\")) name = name.substring(name.lastIndexOf('\\') + 1);
        return name;
    }
}