package com.mcp.tools.sandbox;

import java.nio.file.Path;
import java.util.List;

/**
 * Workspace 隔离策略 — 对应 ToolRiskLevel L2。
 *
 * 限制文件读写路径到指定的 Workspace 目录下，
 * 防止 Agent 工具写入系统敏感目录或通过符号链接越界。
 *
 * 这是一个轻量级隔离，不需要进程隔离或容器。
 * 适用于 file_read、file_write、document_generation 等工具。
 *
 * <h3>P0-Runtime 不变量：Real-Path Boundary Validation</h3>
 * <pre>
 *   requested path
 *       ↓
 *   Path.normalize()          ← 消除 ../ 和冗余分隔符
 *       ↓
 *   Path.toRealPath()         ← 解析符号链接，得到真实物理路径
 *       ↓
 *   startsWith(workspaceRealPath) ← 与 workspace 的真实路径比较
 *       ↓
 *   ├─ true → ALLOW
 *   └─ false → DENY
 * </pre>
 */
public class WorkspaceSandbox {

    private final Path workspaceRoot;
    private final Path workspaceRealPath;
    private final List<String> writeableSubPaths;

    public WorkspaceSandbox(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.workspaceRealPath = resolveRealPath(this.workspaceRoot);
        this.writeableSubPaths = List.of();
    }

    public WorkspaceSandbox(Path workspaceRoot, List<String> writeableSubPaths) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.workspaceRealPath = resolveRealPath(this.workspaceRoot);
        this.writeableSubPaths = writeableSubPaths;
    }

    /**
     * 解析路径的真实路径，处理 workspace 目录可能不存在的情况。
     */
    private Path resolveRealPath(Path path) {
        try {
            if (java.nio.file.Files.exists(path)) {
                return path.toRealPath();
            }
            Path parent = path.getParent();
            if (parent != null) {
                return parent.toRealPath().resolve(path.getFileName());
            }
            return path.toAbsolutePath();
        } catch (java.io.IOException e) {
            return path.toAbsolutePath().normalize();
        }
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

        if (isWithinWorkspace(resolved)) {
            return true;
        }

        if (writeableSubPaths.isEmpty()) {
            return false;
        }

        return writeableSubPaths.stream().anyMatch(sub ->
                isWithinWorkspace(workspaceRoot.resolve(sub).normalize()));
    }

    /**
     * 验证给定路径是否在允许的读取范围内。
     * 读取范围比写入范围更宽松：允许读取 Workspace 内的任何文件。
     */
    public boolean isReadAllowed(Path targetPath) {
        Path resolved = resolve(targetPath);
        if (resolved == null) return false;
        return isWithinWorkspace(resolved);
    }

    /**
     * 解析路径 — 将相对路径解析为 Workspace 下的绝对路径。
     * 防止路径遍历攻击（如 ../../etc/passwd）和符号链接绕过。
     */
    public Path resolve(Path targetPath) {
        if (targetPath.isAbsolute()) {
            Path normalized = targetPath.normalize();
            if (isWithinWorkspace(normalized)) {
                return normalized;
            }
            return null;
        }

        Path resolved = workspaceRoot.resolve(targetPath).normalize();
        if (isWithinWorkspace(resolved)) {
            return resolved;
        }
        return null;
    }

    /**
     * 检查路径是否为符号链接，防止符号链接绕过工作区边界。
     * 应在任何文件操作之前调用，作为 TOCTOU 防护的额外层级。
     *
     * @throws SecurityException 如果路径是符号链接
     */
    public void checkNoSymlink(Path path) {
        try {
            if (java.nio.file.Files.isSymbolicLink(path)) {
                throw new SecurityException(
                        "Symlinks are not allowed in workspace: '" + path
                                + "' -> '" + java.nio.file.Files.readSymbolicLink(path) + "'");
            }
        } catch (java.io.IOException e) {
            throw new SecurityException(
                    "Failed to check symlink for path: '" + path + "': " + e.getMessage());
        }
    }

    /**
     * 核心边界验证：normalize → toRealPath → startsWith(workspaceRealPath)。
     *
     * <p>处理三种情况：
     * <ol>
     *   <li>文件存在 →直接 toRealPath() 后比较</li>
     *   <li>文件不存在但父目录存在 → 父目录 toRealPath() 后比较</li>
     *   <li>父目录也不存在 → 逐级向上查找最近存在的祖先目录</li>
     * </ol>
     */
    private boolean isWithinWorkspace(Path normalizedPath) {
        try {
            Path realPath = toRealPathOrBestEffort(normalizedPath);
            boolean within = realPath.startsWith(workspaceRealPath);
            if (!within) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取路径的真实路径（解析符号链接），
     * 如果路径不存在，则逐级向上查找最近存在的祖先目录。
     */
    private Path toRealPathOrBestEffort(Path path) {
        try {
            if (java.nio.file.Files.exists(path)) {
                return path.toRealPath();
            }
            return resolveNearestExistingAncestor(path);
        } catch (java.io.IOException e) {
            return resolveNearestExistingAncestor(path);
        }
    }

    /**
     * 逐级向上查找最近存在的祖先目录，解析其真实路径后拼接剩余路径。
     *
     * <p>例如：workspace/a/b/c.txt 中只有 workspace/ 存在：
     * <pre>
     *   workspace/a/b/c.txt → 不存在
     *   workspace/a/b/     → 不存在
     *   workspace/a/       → 不存在
     *   workspace/         → 存在，toRealPath() = /tmp/ws
     *   → 返回 /tmp/ws/a/b/c.txt
     * </pre>
     */
    private Path resolveNearestExistingAncestor(Path path) {
        Path current = path.toAbsolutePath().normalize();
        Path remaining = null;

        while (current != null) {
            try {
                if (java.nio.file.Files.exists(current)) {
                    Path realAncestor = current.toRealPath();
                    if (remaining != null) {
                        return realAncestor.resolve(remaining);
                    }
                    return realAncestor;
                }
            } catch (java.io.IOException ignored) {
                // 继续向上查找
            }
            if (remaining == null) {
                remaining = current.getFileName();
            } else {
                remaining = current.getFileName().resolve(remaining);
            }
            current = current.getParent();
        }

        return path.toAbsolutePath().normalize();
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