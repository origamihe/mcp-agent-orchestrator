package com.mcp.engine.retry;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class RetryManager {

    private static final long INITIAL_BACKOFF_MS = 2_000;
    private static final long MAX_BACKOFF_MS = 60_000;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2,
            r -> {
                Thread t = new Thread(r, "retry-scheduler");
                t.setDaemon(true);
                return t;
            });
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("[RetryManager] 初始化完成，initialBackoff={}ms, maxBackoff={}ms",
                INITIAL_BACKOFF_MS, MAX_BACKOFF_MS);
    }

    @PreDestroy
    public void destroy() {
        log.info("[RetryManager] 正在关闭，取消 {} 个待处理任务", futures.size());
        futures.values().forEach(f -> f.cancel(false));
        futures.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void submit(RetryTask task) {
        submit(task, calculateBackoff(task.getRetryCount()));
    }

    private void submit(RetryTask task, long delayMs) {
        if (task.getRetryCount() >= task.getMaxRetries()) {
            log.warn("[RetryManager] 已达最大重试次数，丢弃任务: taskId={}, type={}, sessionId={}, retryCount={}/{}",
                    task.getTaskId(), task.getTaskType(), task.getSessionId(),
                    task.getRetryCount(), task.getMaxRetries());
            return;
        }

        try {
            ScheduledFuture<?> future = scheduler.schedule(
                    () -> execute(task), delayMs, TimeUnit.MILLISECONDS);
            futures.put(task.getTaskId(), future);

            log.info("[RetryManager] 已调度重试: taskId={}, type={}, sessionId={}, retryCount={}/{}, delayMs={}",
                    task.getTaskId(), task.getTaskType(), task.getSessionId(),
                    task.getRetryCount(), task.getMaxRetries(), delayMs);
        } catch (RejectedExecutionException e) {
            log.warn("[RetryManager] 调度器已关闭，丢弃任务: taskId={}, type={}, sessionId={}",
                    task.getTaskId(), task.getTaskType(), task.getSessionId());
        }
    }

    private void execute(RetryTask task) {
        futures.remove(task.getTaskId());

        log.info("[RetryManager] 开始执行重试: taskId={}, type={}, sessionId={}, retryCount={}",
                task.getTaskId(), task.getTaskType(), task.getSessionId(), task.getRetryCount());

        try {
            task.getAction().get()
                    .subscribe(
                            v -> log.info("[RetryManager] 重试成功: taskId={}, type={}, sessionId={}",
                                    task.getTaskId(), task.getTaskType(), task.getSessionId()),
                            error -> handleRetryError(task, error)
                    );
        } catch (Exception e) {
            handleRetryError(task, e);
        }
    }

    private void handleRetryError(RetryTask task, Throwable error) {
        if (isRetryable(error) && task.getRetryCount() < task.getMaxRetries()) {
            task.incrementRetry();
            long delay = calculateBackoff(task.getRetryCount());
            log.warn("[RetryManager] 重试失败，将再次调度: taskId={}, type={}, sessionId={}, retryCount={}/{}, delayMs={}, error={}",
                    task.getTaskId(), task.getTaskType(), task.getSessionId(),
                    task.getRetryCount(), task.getMaxRetries(), delay, error.getMessage());
            submit(task, delay);
        } else {
            log.warn("[RetryManager] 重试耗尽: taskId={}, type={}, sessionId={}, retryCount={}/{}, error={}",
                    task.getTaskId(), task.getTaskType(), task.getSessionId(),
                    task.getRetryCount(), task.getMaxRetries(), error.getMessage());
        }
    }

    public void cancelBySession(String sessionId) {
        futures.entrySet().removeIf(entry -> {
            ScheduledFuture<?> future = entry.getValue();
            boolean cancelled = future.cancel(false);
            if (cancelled) {
                log.info("[RetryManager] 取消任务: taskId={}, sessionId={}", entry.getKey(), sessionId);
            }
            return cancelled;
        });
    }

    public int getPendingCount() {
        return futures.size();
    }

    private long calculateBackoff(int retryCount) {
        long delay = INITIAL_BACKOFF_MS * (1L << Math.min(retryCount, 5));
        return Math.min(delay, MAX_BACKOFF_MS);
    }

    public static boolean isRetryable(Throwable error) {
        if (error == null) {
            return false;
        }

        String msg = error.getMessage() != null ? error.getMessage() : "";

        if (msg.contains("400") || msg.contains("INVALID_ARGUMENT")
                || msg.contains("Bad Request") || msg.contains("401")
                || msg.contains("403") || msg.contains("404")) {
            return false;
        }

        if (msg.contains("429") || msg.contains("Too Many Requests")) {
            return true;
        }
        if (msg.contains("503") || msg.contains("Service Unavailable")) {
            return true;
        }
        if (msg.contains("rate") && (msg.contains("limit") || msg.contains("exceeded"))) {
            return true;
        }
        if (msg.contains("quota") || msg.contains("RESOURCE_EXHAUSTED")) {
            return true;
        }

        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof ConnectException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof TimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }

        String lowerMsg = msg.toLowerCase();
        if (lowerMsg.contains("connection") && (lowerMsg.contains("refused") || lowerMsg.contains("reset")
                || lowerMsg.contains("timeout") || lowerMsg.contains("timed out"))) {
            return true;
        }
        if (lowerMsg.contains("unavailable") || lowerMsg.contains("provider")) {
            return true;
        }

        return false;
    }
}