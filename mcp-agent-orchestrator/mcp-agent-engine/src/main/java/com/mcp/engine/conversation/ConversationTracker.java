package com.mcp.engine.conversation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话线程追踪器 — 管理群聊中的对话线程（Thread）生命周期。
 *
 * 每个 Thread 代表群聊中一段独立的讨论主题。
 * 支持创建、更新、关闭、超时自动清理。
 */
@Slf4j
@Component
public class ConversationTracker {

    private static final long THREAD_TIMEOUT_SECONDS = 300; // 5分钟无活动自动关闭
    private static final int MAX_THREADS_PER_GROUP = 10;

    private final Map<String, ConversationThread> threads = new ConcurrentHashMap<>();

    /**
     * 获取或创建群聊中的活跃线程。
     * 如果当前没有活跃线程，创建新线程。
     * 如果存在活跃线程且最后一条消息在时效窗口内，返回该线程。
     */
    public ConversationThread getOrCreateThread(String groupId, String userId, String messageId) {
        cleanupExpiredThreads(groupId);

        List<ConversationThread> active = getActiveThreads(groupId);
        if (!active.isEmpty()) {
            ConversationThread latest = active.get(0);
            if (!latest.isExpired(THREAD_TIMEOUT_SECONDS)) {
                latest.addMessage(userId, messageId);
                return latest;
            }
        }

        ConversationThread thread = new ConversationThread(groupId, userId, messageId);
        threads.put(thread.getThreadId(), thread);
        log.debug("[ConversationTracker] 创建新线程 {} group={} user={}",
                thread.getThreadId(), groupId, userId);
        return thread;
    }

    /**
     * 获取群聊中所有活跃线程（按最后活跃时间倒序）。
     */
    public List<ConversationThread> getActiveThreads(String groupId) {
        return threads.values().stream()
                .filter(t -> t.getGroupId().equals(groupId))
                .filter(t -> t.isActive() && !t.isExpired(THREAD_TIMEOUT_SECONDS))
                .sorted(Comparator.comparing(ConversationThread::getLastActiveAt).reversed())
                .toList();
    }

    /**
     * 关闭指定线程。
     */
    public void closeThread(String threadId) {
        ConversationThread thread = threads.get(threadId);
        if (thread != null) {
            thread.close();
            log.debug("[ConversationTracker] 关闭线程 {} group={}", threadId, thread.getGroupId());
        }
    }

    /**
     * 判断消息是否属于指定线程（同一用户的连续补充消息）。
     */
    public boolean belongsToThread(String threadId, String userId) {
        ConversationThread thread = threads.get(threadId);
        if (thread == null || !thread.isActive()) return false;
        return thread.getLastUserId().equals(userId)
                && !thread.isExpired(THREAD_TIMEOUT_SECONDS);
    }

    /**
     * 清理超时线程和超出数量限制的旧线程。
     */
    private void cleanupExpiredThreads(String groupId) {
        List<ConversationThread> groupThreads = threads.values().stream()
                .filter(t -> t.getGroupId().equals(groupId))
                .toList();

        for (ConversationThread t : groupThreads) {
            if (t.isExpired(THREAD_TIMEOUT_SECONDS) || !t.isActive()) {
                threads.remove(t.getThreadId());
                log.debug("[ConversationTracker] 清理过期线程 {} group={}", t.getThreadId(), groupId);
            }
        }

        List<ConversationThread> remaining = groupThreads.stream()
                .filter(t -> threads.containsKey(t.getThreadId()))
                .sorted(Comparator.comparing(ConversationThread::getLastActiveAt))
                .toList();

        while (remaining.size() > MAX_THREADS_PER_GROUP) {
            ConversationThread oldest = remaining.remove(0);
            threads.remove(oldest.getThreadId());
            log.debug("[ConversationTracker] 清理旧线程 {} group={} (超出限制)", oldest.getThreadId(), groupId);
        }
    }

    /**
     * 获取活跃线程数。
     */
    public int activeThreadCount(String groupId) {
        return (int) threads.values().stream()
                .filter(t -> t.getGroupId().equals(groupId) && t.isActive())
                .count();
    }

    /**
     * 获取指定线程的 messageId 列表。
     * 返回副本以避免外部修改内部状态。
     */
    public List<String> getThreadMessageIds(String threadId) {
        ConversationThread thread = threads.get(threadId);
        if (thread == null) return List.of();
        return thread.getMessageIds();
    }

    /**
     * 获取指定线程的创建者 userId。
     */
    public String getThreadCreatorUserId(String threadId) {
        ConversationThread thread = threads.get(threadId);
        if (thread == null) return null;
        return thread.getCreatorUserId();
    }

    /**
     * 对话线程。
     */
    public static class ConversationThread {
        private final String threadId;
        private final String groupId;
        private final String creatorUserId;
        private final Instant createdAt;
        private Instant lastActiveAt;
        private String lastUserId;
        private boolean active;
        private int messageCount;
        private final List<String> messageIds;

        ConversationThread(String groupId, String userId, String messageId) {
            this.threadId = "thread-" + groupId + "-" + System.currentTimeMillis();
            this.groupId = groupId;
            this.creatorUserId = userId;
            this.createdAt = Instant.now();
            this.lastActiveAt = Instant.now();
            this.lastUserId = userId;
            this.active = true;
            this.messageCount = 1;
            this.messageIds = new ArrayList<>();
            this.messageIds.add(messageId);
        }

        void addMessage(String userId, String messageId) {
            this.lastUserId = userId;
            this.lastActiveAt = Instant.now();
            this.messageCount++;
            this.messageIds.add(messageId);
        }

        void close() {
            this.active = false;
        }

        boolean isExpired(long timeoutSeconds) {
            return Instant.now().isAfter(lastActiveAt.plusSeconds(timeoutSeconds));
        }

        public String getThreadId() { return threadId; }
        public String getGroupId() { return groupId; }
        public String getCreatorUserId() { return creatorUserId; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getLastActiveAt() { return lastActiveAt; }
        public String getLastUserId() { return lastUserId; }
        public boolean isActive() { return active; }
        public int getMessageCount() { return messageCount; }
        public List<String> getMessageIds() { return new ArrayList<>(messageIds); }
    }
}