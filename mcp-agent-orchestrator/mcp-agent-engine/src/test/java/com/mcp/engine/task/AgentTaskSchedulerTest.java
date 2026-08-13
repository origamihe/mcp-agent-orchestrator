package com.mcp.engine.task;

import com.mcp.common.identity.UserRole;
import com.mcp.engine.task.AgentTaskScheduler.ScheduleResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentTaskScheduler - 任务调度器")
class AgentTaskSchedulerTest {

    private AgentTaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AgentTaskScheduler();
    }

    // ==================== 优先级计算 ====================

    @Nested
    @DisplayName("优先级计算")
    class PriorityCalculation {

        @Test
        @DisplayName("OWNER 基础优先级 100")
        void ownerBasePriority() {
            int p = scheduler.calculatePriority("user1", UserRole.OWNER, "你好", false, false);
            assertThat(p).isEqualTo(100);
        }

        @Test
        @DisplayName("ADMIN 基础优先级 75")
        void adminBasePriority() {
            int p = scheduler.calculatePriority("user1", UserRole.ADMIN, "你好", false, false);
            assertThat(p).isEqualTo(75);
        }

        @Test
        @DisplayName("MEMBER 基础优先级 50")
        void memberBasePriority() {
            int p = scheduler.calculatePriority("user1", UserRole.MEMBER, "你好", false, false);
            assertThat(p).isEqualTo(50);
        }

        @Test
        @DisplayName("紧急关键词 +10")
        void urgentKeywordBonus() {
            int p = scheduler.calculatePriority("user1", UserRole.MEMBER, "紧急求助", false, false);
            assertThat(p).isEqualTo(60);
        }

        @Test
        @DisplayName("同一 Thread 延续 +15")
        void sameThreadBonus() {
            int p = scheduler.calculatePriority("user1", UserRole.MEMBER, "你好", true, false);
            assertThat(p).isEqualTo(65);
        }

        @Test
        @DisplayName("紧急 + 同 Thread 叠加")
        void urgentAndSameThread() {
            int p = scheduler.calculatePriority("user1", UserRole.MEMBER, "紧急任务", true, false);
            assertThat(p).isEqualTo(75);
        }

        @Test
        @DisplayName("OWNER + 紧急 + 同 Thread 不超过 150")
        void maxPriorityCapped() {
            int p = scheduler.calculatePriority("user1", UserRole.OWNER, "紧急求助", true, false);
            assertThat(p).isEqualTo(125);
        }
    }

    // ==================== 任务调度 ====================

    @Nested
    @DisplayName("任务调度")
    class TaskScheduling {

        @Test
        @DisplayName("无运行任务时立即执行")
        void submitWhenNoRunningTask() {
            AgentTask task = buildTask("t1", "group1", "user1", 50);
            ScheduleResult result = scheduler.submit(task);
            assertThat(result).isEqualTo(ScheduleResult.NOW);
            assertThat(task.getStatus()).isEqualTo(AgentTask.TaskStatus.RUNNING);
            assertThat(task.getStartedAt()).isNotNull();
        }

        @Test
        @DisplayName("同一用户短时间连续消息合并")
        void mergeSameUserWithinDebounce() {
            AgentTask t1 = buildTask("t1", "group1", "user1", 50);
            scheduler.submit(t1); // NOW

            AgentTask t2 = buildTask("t2", "group1", "user1", 50);
            ScheduleResult result = scheduler.submit(t2);
            assertThat(result).isEqualTo(ScheduleResult.MERGE);
        }

        @Test
        @DisplayName("高优先级任务可打断当前任务")
        void interruptHighPriority() {
            AgentTask t1 = buildTask("t1", "group1", "user1", 50);
            scheduler.submit(t1); // NOW

            // 模拟 t1 已经运行了一段时间（超过 INTERRUPT_COOLDOWN）
            // 实际上 INTERRUPT_COOLDOWN 是 5 秒，这里无法绕过
            // 但 submit 中 priority >= 120 且 priority > current + 20 才打断
            // 默认 member 50 无法打断，需要 OWNER
            AgentTask t2 = buildTask("t2", "group1", "owner1", 125);
            t2.setUserId("owner1");
            ScheduleResult result = scheduler.submit(t2);
            // 由于 INTERRUPT_COOLDOWN 为 5 秒且 lastInterruptTime 为 EPOCH，
            // 所以第一次打断是允许的
            assertThat(result).isEqualTo(ScheduleResult.INTERRUPT);
            assertThat(t1.getStatus()).isEqualTo(AgentTask.TaskStatus.INTERRUPTED);
        }

        @Test
        @DisplayName("普通优先级任务入队")
        void queueNormalPriority() {
            AgentTask t1 = buildTask("t1", "group1", "user1", 50);
            scheduler.submit(t1); // NOW

            AgentTask t2 = buildTask("t2", "group1", "user2", 50);
            // 手动设置 t2 的用户 ID 为不同用户，避免 merge
            t2.setUserId("user2");
            ScheduleResult result = scheduler.submit(t2);
            assertThat(result).isEqualTo(ScheduleResult.QUEUED);
            assertThat(scheduler.getQueueSize("group1")).isEqualTo(1);
        }
    }

    // ==================== 任务生命周期 ====================

    @Nested
    @DisplayName("任务生命周期")
    class TaskLifecycle {

        @Test
        @DisplayName("completeTask 标记完成并出队下一个")
        void completeTaskAndDequeue() {
            AgentTask t1 = buildTask("t1", "group1", "user1", 50);
            scheduler.submit(t1);

            AgentTask t2 = buildTask("t2", "group1", "user2", 60);
            t2.setUserId("user2");
            scheduler.submit(t2); // queue

            Optional<AgentTask> next = scheduler.completeTask("group1");
            assertThat(t1.getStatus()).isEqualTo(AgentTask.TaskStatus.COMPLETED);
            assertThat(t1.getCompletedAt()).isNotNull();
            assertThat(next).isPresent();
            assertThat(next.get().getTaskId()).isEqualTo("t2");
            assertThat(next.get().getStatus()).isEqualTo(AgentTask.TaskStatus.RUNNING);
        }

        @Test
        @DisplayName("failTask 标记失败并出队下一个")
        void failTaskAndDequeue() {
            AgentTask t1 = buildTask("t1", "group1", "user1", 50);
            scheduler.submit(t1);

            AgentTask t2 = buildTask("t2", "group1", "user2", 60);
            t2.setUserId("user2");
            scheduler.submit(t2); // queue

            Optional<AgentTask> next = scheduler.failTask("group1", "LLM timeout");
            assertThat(t1.getStatus()).isEqualTo(AgentTask.TaskStatus.FAILED);
            assertThat(t1.getErrorMessage()).isEqualTo("LLM timeout");
            assertThat(next).isPresent();
            assertThat(next.get().getTaskId()).isEqualTo("t2");
        }

        @Test
        @DisplayName("队列为空时 completeTask 返回空")
        void completeTaskEmptyQueue() {
            AgentTask t1 = buildTask("t1", "group1", "user1", 50);
            scheduler.submit(t1);

            Optional<AgentTask> next = scheduler.completeTask("group1");
            assertThat(next).isEmpty();
        }

        @Test
        @DisplayName("出队时按优先级取最高")
        void dequeueHighestPriority() {
            AgentTask t1 = buildTask("t1", "group1", "user1", 50);
            scheduler.submit(t1);

            AgentTask t2 = buildTask("t2", "group1", "user2", 30);
            t2.setUserId("user2");
            scheduler.submit(t2);

            AgentTask t3 = buildTask("t3", "group1", "user3", 80);
            t3.setUserId("user3");
            scheduler.submit(t3);

            // 完成 t1，应出队优先级最高的 t3
            Optional<AgentTask> next = scheduler.completeTask("group1");
            assertThat(next).isPresent();
            assertThat(next.get().getTaskId()).isEqualTo("t3");
            assertThat(scheduler.getQueueSize("group1")).isEqualTo(1);
        }

        @Test
        @DisplayName("getDurationMillis 计算任务时长")
        void taskDuration() {
            AgentTask t1 = buildTask("t1", "group1", "user1", 50);
            scheduler.submit(t1);
            scheduler.completeTask("group1");

            assertThat(t1.getDurationMillis()).isGreaterThanOrEqualTo(0);
        }
    }

    // ==================== 队列管理 ====================

    @Nested
    @DisplayName("队列管理")
    class QueueManagement {

        @Test
        @DisplayName("获取运行中任务")
        void getRunningTask() {
            AgentTask t1 = buildTask("t1", "group1", "user1", 50);
            scheduler.submit(t1);

            Optional<AgentTask> running = scheduler.getRunningTask("group1");
            assertThat(running).isPresent();
            assertThat(running.get().getTaskId()).isEqualTo("t1");
        }

        @Test
        @DisplayName("获取队列任务列表")
        void getQueuedTasks() {
            AgentTask t1 = buildTask("t1", "group1", "user1", 50);
            scheduler.submit(t1);

            AgentTask t2 = buildTask("t2", "group1", "user2", 60);
            t2.setUserId("user2");
            scheduler.submit(t2);

            List<AgentTask> queued = scheduler.getQueuedTasks("group1");
            assertThat(queued).hasSize(1);
            assertThat(queued.get(0).getTaskId()).isEqualTo("t2");
        }

        @Test
        @DisplayName("不存在的群组队列为空")
        void noGroupQueue() {
            assertThat(scheduler.getQueueSize("nonexistent")).isEqualTo(0);
            assertThat(scheduler.getQueuedTasks("nonexistent")).isEmpty();
            assertThat(scheduler.getRunningTask("nonexistent")).isEmpty();
        }
    }

    private AgentTask buildTask(String taskId, String groupId, String userId, int priority) {
        return AgentTask.builder()
                .taskId(taskId)
                .groupId(groupId)
                .userId(userId)
                .priority(priority)
                .status(AgentTask.TaskStatus.QUEUED)
                .createdAt(Instant.now())
                .build();
    }
}