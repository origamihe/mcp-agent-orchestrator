package com.mcp.engine.conversation;

import com.mcp.engine.conversation.ConversationTracker.ConversationThread;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConversationTracker - 对话线程追踪器")
class ConversationTrackerTest {

    private ConversationTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ConversationTracker();
    }

    // ==================== 线程创建 ====================

    @Nested
    @DisplayName("线程创建")
    class ThreadCreation {

        @Test
        @DisplayName("首次消息创建新线程")
        void createsNewThreadOnFirstMessage() {
            ConversationThread thread = tracker.getOrCreateThread("group1", "user1", "msg1");
            assertThat(thread).isNotNull();
            assertThat(thread.getGroupId()).isEqualTo("group1");
            assertThat(thread.getCreatorUserId()).isEqualTo("user1");
            assertThat(thread.isActive()).isTrue();
            assertThat(thread.getMessageCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("同一用户连续消息追加到同一线程")
        void sameThreadForSameUser() {
            ConversationThread t1 = tracker.getOrCreateThread("group1", "user1", "msg1");
            ConversationThread t2 = tracker.getOrCreateThread("group1", "user1", "msg2");

            assertThat(t2.getThreadId()).isEqualTo(t1.getThreadId());
            assertThat(t2.getMessageCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("不同用户消息追加到同一活跃线程")
        void sameThreadForDifferentUserInSameGroup() {
            ConversationThread t1 = tracker.getOrCreateThread("group1", "user1", "msg1");
            ConversationThread t2 = tracker.getOrCreateThread("group1", "user2", "msg2");

            assertThat(t2.getThreadId()).isEqualTo(t1.getThreadId());
            assertThat(t2.getMessageCount()).isEqualTo(2);
            assertThat(t2.getLastUserId()).isEqualTo("user2");
        }
    }

    // ==================== 线程归属 ====================

    @Nested
    @DisplayName("线程归属判断")
    class ThreadBelonging {

        @Test
        @DisplayName("belongsToThread 同一用户返回 true")
        void belongsToSameUser() {
            ConversationThread thread = tracker.getOrCreateThread("group1", "user1", "msg1");
            boolean belongs = tracker.belongsToThread(thread.getThreadId(), "user1");
            assertThat(belongs).isTrue();
        }

        @Test
        @DisplayName("belongsToThread 不同用户返回 false")
        void notBelongsToDifferentUser() {
            ConversationThread thread = tracker.getOrCreateThread("group1", "user1", "msg1");
            boolean belongs = tracker.belongsToThread(thread.getThreadId(), "user2");
            assertThat(belongs).isFalse();
        }

        @Test
        @DisplayName("belongsToThread 不存在的线程返回 false")
        void notBelongsToNonexistentThread() {
            boolean belongs = tracker.belongsToThread("nonexistent", "user1");
            assertThat(belongs).isFalse();
        }
    }

    // ==================== 线程生命周期 ====================

    @Nested
    @DisplayName("线程生命周期")
    class ThreadLifecycle {

        @Test
        @DisplayName("关闭线程后 isActive 为 false")
        void closeThread() {
            ConversationThread thread = tracker.getOrCreateThread("group1", "user1", "msg1");
            tracker.closeThread(thread.getThreadId());
            assertThat(thread.isActive()).isFalse();
        }

        @Test
        @DisplayName("活跃线程数统计")
        void activeThreadCount() {
            tracker.getOrCreateThread("group1", "user1", "msg1");
            assertThat(tracker.activeThreadCount("group1")).isEqualTo(1);

            tracker.getOrCreateThread("group1", "user2", "msg2");
            assertThat(tracker.activeThreadCount("group1")).isEqualTo(1);

            tracker.getOrCreateThread("group2", "user1", "msg1");
            assertThat(tracker.activeThreadCount("group1")).isEqualTo(1);
            assertThat(tracker.activeThreadCount("group2")).isEqualTo(1);
        }

        @Test
        @DisplayName("getActiveThreads 按最后活跃时间倒序")
        void activeThreadsSortedByLastActive() {
            ConversationThread t1 = tracker.getOrCreateThread("group1", "user1", "msg1");
            ConversationThread t2 = tracker.getOrCreateThread("group2", "user1", "msg1");

            List<ConversationThread> active = tracker.getActiveThreads("group1");
            assertThat(active).hasSize(1);
            assertThat(active.get(0).getThreadId()).isEqualTo(t1.getThreadId());
        }
    }

    // ==================== 线程数据 ====================

    @Nested
    @DisplayName("线程数据")
    class ThreadData {

        @Test
        @DisplayName("messageIds 记录所有消息 ID")
        void recordsAllMessageIds() {
            ConversationThread thread = tracker.getOrCreateThread("group1", "user1", "msg1");
            ConversationThread updated = tracker.getOrCreateThread("group1", "user1", "msg2");
            tracker.getOrCreateThread("group1", "user1", "msg3");

            List<String> messageIds = updated.getMessageIds();
            assertThat(messageIds).containsExactly("msg1", "msg2", "msg3");
        }

        @Test
        @DisplayName("getMessageIds 返回副本")
        void messageIdsCopy() {
            ConversationThread thread = tracker.getOrCreateThread("group1", "user1", "msg1");
            List<String> copy = thread.getMessageIds();

            copy.add("modified");
            assertThat(thread.getMessageIds()).hasSize(1);
        }
    }
}