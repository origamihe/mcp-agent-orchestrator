package com.mcp.engine.loop;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 定时任务调度器 — 在 Agent Loop 稳定后，支持定时自动执行。
 *
 * 适用场景：
 * - "每 30 分钟搜索最新 AI 新闻并总结"
 * - "每天上午 9 点生成日报"
 * - "每小时检查系统状态"
 *
 * 设计原则：
 * - Scheduling 是 Loops 之上的能力，不应在 Loops 稳定前投入过多。
 * - 使用 ScheduledExecutorService 实现，不依赖外部调度框架。
 * - 每个任务可独立启停。
 */
public class AgentTaskScheduler {

    private final ScheduledExecutorService executor;

    public AgentTaskScheduler() {
        this.executor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "agent-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    public AgentTaskScheduler(int threadPoolSize) {
        this.executor = Executors.newScheduledThreadPool(threadPoolSize, r -> {
            Thread t = new Thread(r, "agent-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 按固定频率调度任务。
     *
     * @param taskId      任务标识
     * @param initialDelay 初始延迟
     * @param period      执行间隔
     * @param task        任务逻辑
     * @return ScheduledTaskHandle（可取消）
     */
    public ScheduledTaskHandle scheduleAtFixedRate(
            String taskId,
            Duration initialDelay,
            Duration period,
            Supplier<String> task) {

        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();

        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
                () -> {
                    try {
                        String result = task.get();
                        if (result != null && !result.isEmpty()) {
                            System.out.printf("[Scheduler] Task '%s' completed: %d chars%n",
                                    taskId, result.length());
                        }
                    } catch (Exception e) {
                        System.err.printf("[Scheduler] Task '%s' failed: %s%n",
                                taskId, e.getMessage());
                    }
                },
                initialDelay.toMillis(),
                period.toMillis(),
                TimeUnit.MILLISECONDS
        );

        futureRef.set(future);

        return new ScheduledTaskHandle(taskId, futureRef, Instant.now());
    }

    /**
     * 延迟执行一次性任务。
     */
    public ScheduledTaskHandle scheduleOnce(
            String taskId,
            Duration delay,
            Runnable task) {

        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();

        ScheduledFuture<?> future = executor.schedule(
                () -> {
                    try {
                        task.run();
                    } catch (Exception e) {
                        System.err.printf("[Scheduler] Task '%s' failed: %s%n",
                                taskId, e.getMessage());
                    }
                },
                delay.toMillis(),
                TimeUnit.MILLISECONDS
        );

        futureRef.set(future);

        return new ScheduledTaskHandle(taskId, futureRef, Instant.now());
    }

    /**
     * 关闭调度器。
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 调度任务句柄 — 可取消、查询状态。
     */
    public static class ScheduledTaskHandle {
        private final String taskId;
        private final AtomicReference<ScheduledFuture<?>> futureRef;
        private final Instant createdAt;

        ScheduledTaskHandle(String taskId, AtomicReference<ScheduledFuture<?>> futureRef, Instant createdAt) {
            this.taskId = taskId;
            this.futureRef = futureRef;
            this.createdAt = createdAt;
        }

        public void cancel() {
            ScheduledFuture<?> future = futureRef.get();
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
            }
        }

        public boolean isDone() {
            ScheduledFuture<?> future = futureRef.get();
            return future == null || future.isDone();
        }

        public boolean isCancelled() {
            ScheduledFuture<?> future = futureRef.get();
            return future != null && future.isCancelled();
        }

        public String getTaskId() {
            return taskId;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }
    }
}