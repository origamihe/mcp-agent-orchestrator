package com.mcp.gateway.decision;

import com.mcp.common.channel.ChannelMessage;
import com.mcp.common.identity.UserProfile;
import com.mcp.common.identity.UserProfileService;
import com.mcp.engine.conversation.ConversationTracker;
import com.mcp.engine.task.AgentTaskScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 交互决策引擎 — 决定群聊消息的处理方式。
 *
 * 决策规则：
 * - IGNORE: 未 @Agent 的消息（已在 Phase 1 处理，此处为兜底）
 * - REPLY:  无运行任务，直接回复
 * - MERGE:  同一用户在同一 Thread 内的连续消息，合并到当前任务
 * - QUEUE:  不同用户/不同 Thread 的消息，入队等待
 * - INTERRUPT: 高优先级（OWNER/ADMIN + 紧急关键词）打断当前任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionDecisionEngine {

    private final ConversationTracker conversationTracker;
    private final AgentTaskScheduler taskScheduler;
    private final UserProfileService userProfileService;

    private static final long DEBOUNCE_WINDOW_MS = 800;

    /**
     * 决策方法 — 根据消息上下文和当前状态决定处理方式。
     */
    public Decision decide(ChannelMessage msg) {
        String groupId = msg.getChatId();
        String userId = msg.getSenderId();

        // 兜底：非群聊或未 @Agent → IGNORE
        if (msg.getChatType() != ChannelMessage.ChatType.GROUP || !msg.isMentionedAgent()) {
            return Decision.IGNORE;
        }

        // 获取或创建对话线程
        ConversationTracker.ConversationThread thread = conversationTracker
                .getOrCreateThread(groupId, userId, msg.getMessageId());

        // 获取用户身份
        UserProfile profile = userProfileService.getUserProfile(userId);

        // 检查是否有运行中的任务
        var runningTask = taskScheduler.getRunningTask(groupId);

        if (runningTask.isEmpty()) {
            // 无运行任务 → 直接回复
            int priority = taskScheduler.calculatePriority(
                    userId, profile.getRole(), msg.getContent(),
                    false, false);
            return new Decision(DecisionType.REPLY, thread.getThreadId(), priority,
                    "无运行任务，直接回复");
        }

        var current = runningTask.get();
        String currentUserId = current.getUserId();
        long currentAge = current.getAgeMillis();

        // 同一用户 + 同一线程 + 在去抖窗口内 → MERGE
        if (userId.equals(currentUserId)
                && conversationTracker.belongsToThread(thread.getThreadId(), userId)
                && currentAge < DEBOUNCE_WINDOW_MS) {
            log.info("[DecisionEngine] MERGE: 同一用户 {} 连续消息 (age={}ms) group={}",
                    userId, currentAge, groupId);
            return new Decision(DecisionType.MERGE, thread.getThreadId(), current.getPriority(),
                    "同一用户连续消息合并");
        }

        // 计算优先级
        boolean isSameThread = thread.getThreadId().equals(current.getThreadId());
        boolean isSameUser = userId.equals(currentUserId);
        int priority = taskScheduler.calculatePriority(
                userId, profile.getRole(), msg.getContent(),
                isSameThread, isSameUser);

        // 高优先级打断
        if (priority >= 120 && priority > current.getPriority() + 20) {
            log.info("[DecisionEngine] INTERRUPT: 优先级 {} > {} group={} user={}",
                    priority, current.getPriority(), groupId, userId);
            return new Decision(DecisionType.INTERRUPT, thread.getThreadId(), priority,
                    "高优先级打断: " + profile.getRole() + " priority=" + priority);
        }

        // 默认入队
        log.info("[DecisionEngine] QUEUE: priority={} group={} user={} queueSize={}",
                priority, groupId, userId, taskScheduler.getQueueSize(groupId));
        return new Decision(DecisionType.QUEUE, thread.getThreadId(), priority,
                "队列等待: priority=" + priority);
    }

    public enum DecisionType {
        IGNORE, REPLY, MERGE, QUEUE, INTERRUPT
    }

    public record Decision(DecisionType type, String threadId, int priority, String reason) {
        public static final Decision IGNORE = new Decision(DecisionType.IGNORE, null, 0, "未@Agent");
    }
}