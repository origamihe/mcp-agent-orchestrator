package com.mcp.tools.sandbox;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 进程级沙箱执行器 — 对应 ToolRiskLevel L3。
 *
 * 限制：
 * - 超时控制（通过 Process.waitFor + timeout）
 * - 工作目录隔离（限制在指定目录下执行）
 * - 输出大小限制（防止 OOM）
 * - 进程强制终止（超时后 destroyForcibly）
 *
 * 注意：这不是真正的安全沙箱（如 Docker/Landlock），仅提供基本的进程隔离。
 * 对于 L4 级别（网络 + 本地执行），应使用 Container 沙箱。
 * 对于 L3 级别（执行本地程序），此实现已足够。
 */
@Slf4j
public class ProcessSandboxExecutor implements SandboxExecutor {

    private static final int MAX_OUTPUT_BYTES = 10 * 1024 * 1024; // 10MB max output

    @Override
    public SandboxResult execute(String command, String workDir, Map<String, String> env, SandboxConfig config) {
        long startTime = System.currentTimeMillis();

        try {
            Path workPath = Path.of(workDir).toAbsolutePath().normalize();
            if (!Files.isDirectory(workPath)) {
                return SandboxResult.error(1, "Work directory does not exist: " + workDir, 0);
            }

            ProcessBuilder pb = new ProcessBuilder();
            if (isWindows()) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            pb.directory(workPath.toFile());

            if (env != null && !env.isEmpty()) {
                pb.environment().putAll(env);
            }

            if (!config.allowNetwork()) {
                pb.environment().put("http_proxy", "");
                pb.environment().put("https_proxy", "");
                pb.environment().put("no_proxy", "*");
            }

            pb.redirectErrorStream(false);

            Process process = pb.start();

            ByteArrayOutputStream stdoutCapture = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrCapture = new ByteArrayOutputStream();

            Thread stdoutThread = new Thread(() -> {
                try {
                    process.getInputStream().transferTo(new LimitedOutputStream(stdoutCapture, MAX_OUTPUT_BYTES));
                } catch (IOException ignored) {}
            }, "sandbox-stdout");
            stdoutThread.setDaemon(true);
            stdoutThread.start();

            Thread stderrThread = new Thread(() -> {
                try {
                    process.getErrorStream().transferTo(new LimitedOutputStream(stderrCapture, MAX_OUTPUT_BYTES));
                } catch (IOException ignored) {}
            }, "sandbox-stderr");
            stderrThread.setDaemon(true);
            stderrThread.start();

            boolean finished = process.waitFor(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
            long elapsed = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                log.warn("[Sandbox] Process timed out after {}ms, command: {}", elapsed, command);
                return SandboxResult.timeout(stdoutCapture.toString(), elapsed);
            }

            stdoutThread.join(1000);
            stderrThread.join(1000);

            int exitCode = process.exitValue();
            String stdout = stdoutCapture.toString(StandardCharsets.UTF_8);
            String stderr = stderrCapture.toString(StandardCharsets.UTF_8);

            if (exitCode != 0) {
                log.warn("[Sandbox] Process exited with code {}: {} | stderr: {}",
                        exitCode, command, stderr.length() > 200 ? stderr.substring(0, 200) : stderr);
                return SandboxResult.error(exitCode, stderr, elapsed);
            }

            log.debug("[Sandbox] Process completed in {}ms: {} | stdout: {} chars",
                    elapsed, command, stdout.length());
            return SandboxResult.success(stdout, elapsed);

        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[Sandbox] IO error executing command: {} | error: {}", command, e.getMessage());
            return SandboxResult.error(1, "IO error: " + e.getMessage(), elapsed);
        } catch (InterruptedException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            Thread.currentThread().interrupt();
            log.error("[Sandbox] Interrupted executing command: {}", command);
            return SandboxResult.error(1, "Interrupted", elapsed);
        }
    }

    @Override
    public boolean supports(String riskLevel) {
        return "L3".equals(riskLevel);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static class LimitedOutputStream extends java.io.FilterOutputStream {
        private final int maxBytes;
        private int written;

        LimitedOutputStream(java.io.OutputStream out, int maxBytes) {
            super(out);
            this.maxBytes = maxBytes;
        }

        @Override
        public void write(int b) throws IOException {
            if (written < maxBytes) {
                super.write(b);
                written++;
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (written >= maxBytes) return;
            int toWrite = Math.min(len, maxBytes - written);
            super.write(b, off, toWrite);
            written += toWrite;
        }
    }
}