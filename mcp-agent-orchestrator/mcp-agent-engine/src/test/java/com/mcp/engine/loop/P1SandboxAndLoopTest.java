package com.mcp.engine.loop;

import com.mcp.engine.trace.SessionEventStore;
import com.mcp.engine.trace.SessionTraceHolder;
import com.mcp.common.tool.ToolRiskLevel;
import com.mcp.tools.sandbox.ProcessSandboxExecutor;
import com.mcp.tools.sandbox.SandboxExecutor;
import com.mcp.tools.sandbox.SandboxPolicy;
import com.mcp.tools.sandbox.WorkspaceSandbox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1: Sandbox + Loops + Scheduling 验证测试。
 */
class P1SandboxAndLoopTest {

    private SessionEventStore.InMemory store;

    @BeforeEach
    void setUp() {
        store = new SessionEventStore.InMemory();
        store.clear();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    // ==================== Sandbox Tests ====================

    @Test
    @DisplayName("ProcessSandboxExecutor — 执行 echo 命令")
    void shouldExecuteEchoCommand() {
        ProcessSandboxExecutor executor = new ProcessSandboxExecutor();
        SandboxExecutor.SandboxConfig config = SandboxExecutor.SandboxConfig.defaultConfig();

        SandboxExecutor.SandboxResult result = executor.execute(
                "echo hello world",
                System.getProperty("java.io.tmpdir"),
                java.util.Map.of(),
                config);

        assertTrue(result.exitCode() == 0, "Expected exit code 0, got " + result.exitCode());
        assertTrue(result.stdout().contains("hello world"),
                "Expected 'hello world' in stdout, got: " + result.stdout());
        assertFalse(result.timedOut());
        assertFalse(result.wasKilled());
        assertTrue(result.durationMs() >= 0);
    }

    @Test
    @DisplayName("ProcessSandboxExecutor — 超时终止")
    void shouldTimeoutLongRunningCommand() {
        ProcessSandboxExecutor executor = new ProcessSandboxExecutor();
        SandboxExecutor.SandboxConfig config = new SandboxExecutor.SandboxConfig(
                Duration.ofMillis(200),
                256 * 1024 * 1024L,
                java.util.List.of(),
                java.util.List.of("."),
                false,
                false);

        SandboxExecutor.SandboxResult result = executor.execute(
                isWindows() ? "ping -n 10 127.0.0.1 >nul" : "sleep 5",
                System.getProperty("java.io.tmpdir"),
                java.util.Map.of(),
                config);

        assertTrue(result.timedOut(), "Expected timeout, got: exitCode=" + result.exitCode());
    }

    @Test
    @DisplayName("ProcessSandboxExecutor — 支持 L3 风险等级")
    void shouldSupportL3RiskLevel() {
        ProcessSandboxExecutor executor = new ProcessSandboxExecutor();
        assertTrue(executor.supports("L3"));
        assertFalse(executor.supports("L2"));
        assertFalse(executor.supports("L4"));
    }

    @Test
    @DisplayName("ProcessSandboxExecutor — 不存在的命令返回错误")
    void shouldReturnErrorForNonExistentCommand() {
        ProcessSandboxExecutor executor = new ProcessSandboxExecutor();
        SandboxExecutor.SandboxConfig config = SandboxExecutor.SandboxConfig.defaultConfig();

        SandboxExecutor.SandboxResult result = executor.execute(
                "nonexistent_command_xyz_123",
                System.getProperty("java.io.tmpdir"),
                java.util.Map.of(),
                config);

        assertNotEquals(0, result.exitCode(), "Expected non-zero exit code for non-existent command");
    }

    @Test
    @DisplayName("WorkspaceSandbox — 允许写入 workspace 内路径")
    void shouldAllowWriteWithinWorkspace() throws Exception {
        Path workspaceRoot = Path.of(System.getProperty("java.io.tmpdir"), "sandbox-test-" + System.currentTimeMillis());
        java.nio.file.Files.createDirectories(workspaceRoot);

        try {
            WorkspaceSandbox sandbox = new WorkspaceSandbox(workspaceRoot);

            assertTrue(sandbox.isWriteAllowed(workspaceRoot.resolve("test.txt")));
            assertTrue(sandbox.isWriteAllowed(workspaceRoot.resolve("subdir/output.json")));
            assertTrue(sandbox.isReadAllowed(workspaceRoot.resolve("data.csv")));
        } finally {
            java.nio.file.Files.deleteIfExists(workspaceRoot);
        }
    }

    @Test
    @DisplayName("WorkspaceSandbox — 阻止路径遍历攻击")
    void shouldPreventPathTraversal() {
        Path workspaceRoot = Path.of(System.getProperty("java.io.tmpdir"), "sandbox-" + System.currentTimeMillis());
        WorkspaceSandbox sandbox = new WorkspaceSandbox(workspaceRoot);

        assertFalse(sandbox.isWriteAllowed(Path.of("../../etc/passwd")));
        assertFalse(sandbox.isWriteAllowed(Path.of("C:\\Windows\\System32\\config")));
    }

    @Test
    @DisplayName("WorkspaceSandbox — 阻止写入 workspace 外路径")
    void shouldPreventWriteOutsideWorkspace() {
        Path workspaceRoot = Path.of(System.getProperty("java.io.tmpdir"), "sandbox-" + System.currentTimeMillis());
        WorkspaceSandbox sandbox = new WorkspaceSandbox(workspaceRoot);

        assertFalse(sandbox.isWriteAllowed(Path.of("/etc/passwd")));
        assertFalse(sandbox.isWriteAllowed(Path.of("/home/user/secret.txt")));
    }

    @Test
    @DisplayName("SandboxPolicy — 各风险等级的正确决策")
    void shouldDecideCorrectlyForEachRiskLevel() {
        Path workspaceRoot = Path.of(System.getProperty("java.io.tmpdir"));
        WorkspaceSandbox ws = new WorkspaceSandbox(workspaceRoot);
        ProcessSandboxExecutor ps = new ProcessSandboxExecutor();
        SandboxPolicy policy = new SandboxPolicy(ws, ps);

        assertEquals(SandboxPolicy.Decision.NONE, policy.decide(ToolRiskLevel.L0));
        assertEquals(SandboxPolicy.Decision.NONE, policy.decide(ToolRiskLevel.L1));
        assertEquals(SandboxPolicy.Decision.WORKSPACE_ISOLATION, policy.decide(ToolRiskLevel.L2));
        assertEquals(SandboxPolicy.Decision.PROCESS_SANDBOX, policy.decide(ToolRiskLevel.L3));
        assertEquals(SandboxPolicy.Decision.CONTAINER_SANDBOX, policy.decide(ToolRiskLevel.L4));
        assertEquals(SandboxPolicy.Decision.BLOCKED, policy.decide(ToolRiskLevel.L5));
    }

    // ==================== Loops Tests ====================

    @Test
    @DisplayName("LoopContext — 达到最大轮次后停止")
    void shouldStopAtMaxRounds() {
        LoopContext ctx = LoopContext.builder()
                .maxRounds(3)
                .timeout(Duration.ofSeconds(30))
                .maxConsecutiveErrors(3)
                .build();

        assertTrue(ctx.shouldContinue());
        ctx.recordSuccess();
        assertTrue(ctx.shouldContinue());
        ctx.recordSuccess();
        assertTrue(ctx.shouldContinue());
        ctx.recordSuccess();
        assertFalse(ctx.shouldContinue());
    }

    @Test
    @DisplayName("LoopContext — 连续错误达到上限后停止")
    void shouldStopAtMaxConsecutiveErrors() {
        LoopContext ctx = LoopContext.builder()
                .maxRounds(10)
                .timeout(Duration.ofSeconds(30))
                .maxConsecutiveErrors(2)
                .build();

        assertTrue(ctx.shouldContinue());
        ctx.recordError();
        assertTrue(ctx.shouldContinue());
        ctx.recordError();
        assertFalse(ctx.shouldContinue());
    }

    @Test
    @DisplayName("LoopContext — 成功重置连续错误计数")
    void shouldResetConsecutiveErrorsOnSuccess() {
        LoopContext ctx = LoopContext.builder()
                .maxRounds(10)
                .timeout(Duration.ofSeconds(30))
                .maxConsecutiveErrors(3)
                .build();

        ctx.recordError();
        ctx.recordError();
        assertEquals(2, ctx.getConsecutiveErrors());

        ctx.recordSuccess();
        assertEquals(0, ctx.getConsecutiveErrors());
        assertTrue(ctx.shouldContinue());
    }

    @Test
    @DisplayName("LoopContext — 自定义终止条件")
    void shouldUseCustomTerminationCondition() {
        LoopContext ctx = LoopContext.builder()
                .maxRounds(10)
                .timeout(Duration.ofSeconds(30))
                .maxConsecutiveErrors(3)
                .terminationCondition(result -> result.output() != null && result.output().contains("DONE"))
                .build();

        assertTrue(ctx.shouldContinue());

        LoopContext.LoopIterationResult result = LoopContext.LoopIterationResult.success("Task DONE", false, 0);
        assertTrue(ctx.checkTermination(result));
        assertFalse(ctx.shouldContinue());
    }

    @Test
    @DisplayName("AgentLoop — 正常执行完成")
    void shouldExecuteSimpleLoop() {
        SessionTraceHolder.start("loop-test", store);
        try {
            AtomicInteger counter = new AtomicInteger(0);
            AgentLoop loop = AgentLoop.create();

            LoopContext ctx = LoopContext.builder()
                    .maxRounds(3)
                    .timeout(Duration.ofSeconds(5))
                    .maxConsecutiveErrors(3)
                    .terminationCondition(result -> counter.get() >= 3)
                    .build();

            AgentLoop.LoopResult result = loop.executeLoop(ctx, c -> {
                int count = counter.incrementAndGet();
                return Mono.just(LoopContext.LoopIterationResult.success(
                        "iteration " + count, false, 0));
            }).block();

            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals(3, result.totalRounds());
        } finally {
            SessionTraceHolder.end();
        }
    }

    @Test
    @DisplayName("AgentLoop — 错误后终止")
    void shouldStopOnError() {
        SessionTraceHolder.start("loop-error-test", store);
        try {
            AtomicInteger counter = new AtomicInteger(0);
            AgentLoop loop = AgentLoop.create();

            LoopContext ctx = LoopContext.builder()
                    .maxRounds(5)
                    .timeout(Duration.ofSeconds(5))
                    .maxConsecutiveErrors(1)
                    .build();

            AgentLoop.LoopResult result = loop.executeLoop(ctx, c -> {
                int count = counter.incrementAndGet();
                if (count == 1) {
                    return Mono.just(LoopContext.LoopIterationResult.error("simulated failure"));
                }
                return Mono.just(LoopContext.LoopIterationResult.success("ok", false, 0));
            }).block();

            assertNotNull(result);
            assertTrue(result.hasError());
            assertEquals(1, result.totalRounds());
        } finally {
            SessionTraceHolder.end();
        }
    }

    @Test
    @DisplayName("LoopStateMachine — 正常 ReAct 循环")
    void shouldFollowReActStateMachine() {
        assertEquals(LoopStateMachine.initial(), LoopStateMachine.THINK);

        assertEquals(LoopStateMachine.ACT,
                LoopStateMachine.next(LoopStateMachine.THINK, true, false));

        assertEquals(LoopStateMachine.OBSERVE,
                LoopStateMachine.next(LoopStateMachine.ACT, true, false));

        assertEquals(LoopStateMachine.ACT,
                LoopStateMachine.next(LoopStateMachine.DECIDE, true, false));

        assertEquals(LoopStateMachine.DONE,
                LoopStateMachine.next(LoopStateMachine.DECIDE, false, false));

        assertEquals(LoopStateMachine.DONE,
                LoopStateMachine.next(LoopStateMachine.THINK, false, false));

        assertEquals(LoopStateMachine.ERROR,
                LoopStateMachine.next(LoopStateMachine.THINK, true, true));
    }

    @Test
    @DisplayName("LoopStateMachine — 终止状态检查")
    void shouldCheckTerminalStates() {
        assertTrue(LoopStateMachine.DONE.isTerminal());
        assertTrue(LoopStateMachine.ERROR.isTerminal());
        assertFalse(LoopStateMachine.THINK.isTerminal());
        assertFalse(LoopStateMachine.ACT.isTerminal());
        assertFalse(LoopStateMachine.OBSERVE.isTerminal());
        assertFalse(LoopStateMachine.DECIDE.isTerminal());
    }

    // ==================== Scheduling Tests ====================

    @Test
    @DisplayName("AgentTaskScheduler — 延迟执行一次性任务")
    void shouldScheduleOnce() throws Exception {
        AgentTaskScheduler scheduler = new AgentTaskScheduler(1);
        AtomicInteger counter = new AtomicInteger(0);

        AgentTaskScheduler.ScheduledTaskHandle handle = scheduler.scheduleOnce(
                "test-once", Duration.ofMillis(100), counter::incrementAndGet);

        Thread.sleep(300);
        assertEquals(1, counter.get());
        assertTrue(handle.isDone());

        scheduler.shutdown();
    }

    @Test
    @DisplayName("AgentTaskScheduler — 取消任务")
    void shouldCancelTask() throws Exception {
        AgentTaskScheduler scheduler = new AgentTaskScheduler(1);
        AtomicInteger counter = new AtomicInteger(0);

        AgentTaskScheduler.ScheduledTaskHandle handle = scheduler.scheduleOnce(
                "test-cancel", Duration.ofSeconds(5), counter::incrementAndGet);

        handle.cancel();
        assertTrue(handle.isCancelled());

        Thread.sleep(100);
        assertEquals(0, counter.get());

        scheduler.shutdown();
    }
}