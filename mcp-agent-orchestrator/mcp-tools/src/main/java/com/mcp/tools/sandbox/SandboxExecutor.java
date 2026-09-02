package com.mcp.tools.sandbox;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 沙箱执行器接口 — 根据 ToolRiskLevel 选择不同的隔离策略。
 *
 * 隔离策略：
 * <pre>
 * L0-L1 → 不需要沙箱（直接执行或只读）
 * L2    → Workspace 隔离（限制文件写入路径）
 * L3    → Process 沙箱（受限进程 + 超时 + 资源限制）
 * L4    → Container 沙箱（Docker 容器隔离）
 * L5    → 默认禁止
 * </pre>
 *
 * 设计原则：
 * - Sandbox 的优先级取决于 Agent 能力边界，而非对标 DeepSeek Harness
 * - 当前工具体系（web_search, file_read, file_write, document_generation, memory, LLM）主要处于 L0-L2
 * - 如果未来 Agent 需要执行 shell/Python/Java/编译等，L3-L4 的 Sandbox 才会变成 P0
 */
public interface SandboxExecutor {

    /**
     * 执行结果 — 包含退出码、标准输出、标准错误和耗时。
     */
    record SandboxResult(
            int exitCode,
            String stdout,
            String stderr,
            long durationMs,
            boolean timedOut,
            boolean wasKilled
    ) {
        public static SandboxResult success(String stdout, long durationMs) {
            return new SandboxResult(0, stdout, "", durationMs, false, false);
        }

        public static SandboxResult timeout(String partialStdout, long durationMs) {
            return new SandboxResult(-1, partialStdout, "Execution timed out", durationMs, true, false);
        }

        public static SandboxResult killed(String partialStdout, String reason, long durationMs) {
            return new SandboxResult(-1, partialStdout, reason, durationMs, false, true);
        }

        public static SandboxResult error(int exitCode, String stderr, long durationMs) {
            return new SandboxResult(exitCode, "", stderr, durationMs, false, false);
        }
    }

    /**
     * 沙箱配置 — 定义执行环境约束。
     */
    record SandboxConfig(
            Duration timeout,
            long maxMemoryBytes,
            List<String> allowedCommands,
            List<String> writeablePaths,
            boolean allowNetwork,
            boolean allowSubprocesses
    ) {
        public static SandboxConfig defaultConfig() {
            return new SandboxConfig(
                    Duration.ofSeconds(30),
                    256 * 1024 * 1024L, // 256MB
                    List.of(),
                    List.of("."),
                    false,
                    false
            );
        }

        public static SandboxConfig permissive() {
            return new SandboxConfig(
                    Duration.ofMinutes(2),
                    512 * 1024 * 1024L,
                    List.of(),
                    List.of("."),
                    false,
                    false
            );
        }

        public static SandboxConfig none() {
            return new SandboxConfig(
                    Duration.ZERO,
                    0,
                    List.of(),
                    List.of(),
                    false,
                    false
            );
        }
    }

    /**
     * 在沙箱中执行命令。
     *
     * @param command 要执行的命令（如 "python script.py"）
     * @param workDir 工作目录
     * @param env     环境变量
     * @param config  沙箱配置（超时、内存限制、网络权限等）
     * @return 执行结果
     */
    SandboxResult execute(String command, String workDir, Map<String, String> env, SandboxConfig config);

    /**
     * 检查此执行器是否支持指定的风险等级。
     */
    boolean supports(String riskLevel);
}