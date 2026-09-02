package com.mcp.tools.sandbox;

import java.nio.file.Path;
import java.util.List;

/**
 * Workspace 隔离策略 — 对应 ToolRiskLevel L2。
 *
 * 限制文件写入路径到指定的 Workspace 目录下，
 * 防止 Agent 工具写入系统敏感目录。
 *
 * 这是一个轻量级隔离，不需要进程隔离或容器。
 * 适用于 file_read、file_write、document_generation 等工具。
 */
public class WorkspaceSandbox {

    private final Path workspaceRoot;
    private final List<String> writeableSubPaths;

    public WorkspaceSandbox(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.writeableSubPaths = List.of();
    }

    public WorkspaceSandbox(Path workspaceRoot, List<String> writeableSubPaths) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.writeableSubPaths = writeableSubPaths;
    }

    /**
     * 验证给定路径是否在允许的写入范围内。
     *
     * @param targetPath 要写入的目标路径
     * @return true 如果允许写入
     */
    public boolean isWriteAllowed(Path targetPath) {
        Path resolved = resolve(targetPath);
        if (resolved == null) return false;

        if (resolved.startsWith(workspaceRoot)) {
            return true;
        }

        if (writeableSubPaths.isEmpty()) {
            return false;
        }

        return writeableSubPaths.stream().anyMatch(sub ->
                resolved.startsWith(workspaceRoot.resolve(sub).normalize()));
    }

    /**
     * 验证给定路径是否在允许的读取范围内。
     * 读取范围比写入范围更宽松：允许读取 Workspace 内的任何文件。
     */
    public boolean isReadAllowed(Path targetPath) {
        Path resolved = resolve(targetPath);
        if (resolved == null) return false;
        return resolved.startsWith(workspaceRoot);
    }

    /**
     * 解析路径 — 将相对路径解析为 Workspace 下的绝对路径。
     * 防止路径遍历攻击（如 ../../etc/passwd）。
     */
    public Path resolve(Path targetPath) {
        if (targetPath.isAbsolute()) {
            Path normalized = targetPath.normalize();
            if (normalized.startsWith(workspaceRoot)) {
                return normalized;
            }
            return null;
        }

        Path resolved = workspaceRoot.resolve(targetPath).normalize();
        if (resolved.startsWith(workspaceRoot)) {
            return resolved;
        }
        return null;
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    /**
     * 创建 Workspace 目录结构。
     */
    public void ensureDirectories() {
        try {
            java.nio.file.Files.createDirectories(workspaceRoot);
            for (String sub : writeableSubPaths) {
                java.nio.file.Files.createDirectories(workspaceRoot.resolve(sub));
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to create workspace directories: " + workspaceRoot, e);
        }
    }
}