package com.mcp.engine.task;

import com.mcp.common.identity.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 任务调度器 — 管理 Agent 任务队列、优先级计算和调度。
 *
 * 多因素优先级：
 * - Authority: OWNER=100, ADMIN=75, MEMBER=50
 * - Urgency: 紧急关键词 +10
 * - Continuity: 同一 Thread 延续 +15
 * - Age: 每等待 1 秒 +1（防饥饿，上限 30）
 */
@Slf4j
@Component
public class TaskScheduler {

    private static final int MAX_QUEUE_SIZE = 20;
    private static final long DEBOUNCE_WINDOW_MS = 800;  // 消息聚合窗口
    private static final long INTERRUPT_COOLDOWN_MS = 5000; // 打断冷却时间

    private final Map<String, ConcurrentLinkedDeque<AgentTask>> groupQueues = new ConcurrentHashMap<>();
    private final Map<String, AgentTask> runningTasks = new ConcurrentHashMap<>();
    private Instant lastInterruptTime = Instant.EPOCH;

    /**
     * 计算任务优先级。
     */
    public int calculatePriority(String userId, UserRole userRole, String messageContent,
                                  boolean isSameThread, boolean isSameUser) {
        int priority = 0;

        // Authority
        priority += switch (userRole) {
            case OWNER -> 100;
            case ADMIN -> 75;
            default -> 50;
        };

        // Urgency（紧急关键词）
        String lower = messageContent.toLowerCase();
        if (lower.contains("紧急") || lower.contains("马上") || lower.contains("立即")
                || lower.contains("urgent") || lower.contains("asap")) {
            priority += 10;
        }

        // Continuity（同一 Thread 延续）
        if (isSameThread) {
            priority += 15;
        }

        return Math.min(priority, 150);
    }

    /**
     * 提交任务到队列。
     * 返回 true 表示立即执行，false 表示已入队等待。
     */
    public ScheduleResult submit(AgentTask task) {
        String groupId = task.getGroupId();
        ConcurrentLinkedDeque<AgentTask> queue = groupQueues.computeIfAbsent(
                groupId, k -> new ConcurrentLinkedDeque<>());

        AgentTask running = runningTasks.get(groupId);

        // 无运行任务 → 立即执行
        if (running == null || running.isCompleted()) {
            runningTasks.put(groupId, task);
            task.setStatus(AgentTask.TaskStatus.RUNNING);
            task.setStartedAt(Instant.now());
            log.info("[TaskScheduler] 立即执行任务 {} group={} user={} priority={} reason={}",
                    task.getTaskId(), groupId, task.getUserId(),
                    task.getPriority(), task.getPriorityReason());
            return ScheduleResult.NOW;
        }

        // 同一用户在同一 Thread 的连续消息 → 合并
        if (task.getUserId().equals(running.getUserId())
                && running.getAgeMillis() < DEBOUNCE_WINDOW_MS) {
            log.info("[TaskScheduler] 合并消息到任务 {} group={} user={} (debounce)",
                    running.getTaskId(), groupId, task.getUserId());
            return ScheduleResult.MERGE;
        }

        // 高优先级可以打断 → 但需要冷却时间
        if (task.getPriority() >= 120
                && Instant.now().isAfter(lastInterruptTime.plusMillis(INTERRUPT_COOLDOWN_MS))) {
            running.setStatus(AgentTask.TaskStatus.INTERRUPTED);
            running.setCompletedAt(Instant.now());
            runningTasks.put(groupId, task);
            task.setStatus(AgentTask.TaskStatus.RUNNING);
            task.setStartedAt(Instant.now());
            lastInterruptTime = Instant.now();
            log.info("[TaskScheduler] 打断任务 {} (priority={}) → 新任务 {} (priority={}) group={}",
                    running.getTaskId(), running.getPriority(),
                    task.getTaskId(), task.getPriority(), groupId);
            return ScheduleResult.INTERRUPT;
        }

        // 入队
        if (queue.size() >= MAX_QUEUE_SIZE) {
            queue.pollFirst(); // 丢弃最旧的任务
            log.warn("[TaskScheduler] 队列已满，丢弃最旧任务 group={}", groupId);
        }
        queue.offerLast(task);
        log.info("[TaskScheduler] 任务入队 {} group={} user={} priority={} queueSize={}",
                task.getTaskId(), groupId, task.getUserId(),
                task.getPriority(), queue.size());
        return ScheduleResult.QUEUED;
    }

    /**
     * 完成当前任务，从队列中取出下一个。
     */
    public Optional<AgentTask> completeTask(String groupId) {
        AgentTask completed = runningTasks.remove(groupId);
        if (completed != null) {
            completed.setStatus(AgentTask.TaskStatus.COMPLETED);
            completed.setCompletedAt(Instant.now());
            log.info("[TaskScheduler] 任务完成 {} group={} user={} duration={}ms",
                    completed.getTaskId(), groupId, completed.getUserId(),
                    completed.getDurationMillis());
        }
        return dequeueNext(groupId);
    }

    /**
     * 标记当前任务失败，从队列中取出下一个。
     */
    public Optional<AgentTask> failTask(String groupId, String error) {
        AgentTask failed = runningTasks.remove(groupId);
        if (failed != null) {
            failed.setStatus(AgentTask.TaskStatus.FAILED);
            failed.setCompletedAt(Instant.now());
            failed.setErrorMessage(error);
            log.warn("[TaskScheduler] 任务失败 {} group={} user={} error={}",
                    failed.getTaskId(), groupId, failed.getUserId(), error);
        }
        return dequeueNext(groupId);
    }

    private Optional<AgentTask> dequeueNext(String groupId) {
        ConcurrentLinkedDeque<AgentTask> queue = groupQueues.get(groupId);
        if (queue != null && !queue.isEmpty()) {
            AgentTask next = queue.stream()
                    .max(Comparator.comparingInt(AgentTask::getPriority))
                    .orElse(null);
            if (next != null) {
                queue.remove(next);
                next.setStatus(AgentTask.TaskStatus.RUNNING);
                next.setStartedAt(Instant.now());
                runningTasks.put(groupId, next);
                log.info("[TaskScheduler] 出队执行任务 {} group={} user={} priority={}",
                        next.getTaskId(), groupId, next.getUserId(), next.getPriority());
                return Optional.of(next);
            }
        }
        return Optional.empty();
    }

    /**
     * 获取当前运行中的任务。
     */
    public Optional<AgentTask> getRunningTask(String groupId) {
        return Optional.ofNullable(runningTasks.get(groupId));
    }

    /**
     * 获取队列大小。
     */
    public int getQueueSize(String groupId) {
        ConcurrentLinkedDeque<AgentTask> queue = groupQueues.get(groupId);
        return queue != null ? queue.size() : 0;
    }

    /**
     * 获取队列中所有任务。
     */
    public List<AgentTask> getQueuedTasks(String groupId) {
        ConcurrentLinkedDeque<AgentTask> queue = groupQueues.get(groupId);
        return queue != null ? List.copyOf(queue) : List.of();
    }

    public enum ScheduleResult {
        NOW, MERGE, QUEUED, INTERRUPT
    }
}