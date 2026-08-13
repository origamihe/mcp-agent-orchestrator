package com.mcp.gateway.decision;

import com.mcp.common.channel.ChannelMessage;
import com.mcp.common.identity.UserProfile;
import com.mcp.common.identity.UserProfileService;
import com.mcp.common.identity.UserRole;
import com.mcp.engine.conversation.ConversationTracker;
import com.mcp.engine.task.AgentTask;
import com.mcp.engine.task.AgentTaskScheduler;
import com.mcp.gateway.decision.InteractionDecisionEngine.Decision;
import com.mcp.gateway.decision.InteractionDecisionEngine.DecisionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InteractionDecisionEngine - 交互决策引擎")
class InteractionDecisionEngineTest {

    private ConversationTracker conversationTracker;
    @Mock
    private AgentTaskScheduler taskScheduler;
    @Mock
    private UserProfileService userProfileService;

    private InteractionDecisionEngine engine;

    @BeforeEach
    void setUp() {
        conversationTracker = new ConversationTracker();
        engine = new InteractionDecisionEngine(conversationTracker, taskScheduler, userProfileService);
    }

    // ==================== IGNORE 决策 ====================

    @Nested
    @DisplayName("IGNORE 决策")
    class IgnoreDecision {

        @Test
        @DisplayName("群聊未 @Agent 消息 → IGNORE")
        void groupWithoutMentionIgnored() {
            ChannelMessage msg = ChannelMessage.builder()
                    .messageId("m1")
                    .senderId("user1")
                    .chatId("group1")
                    .chatType(ChannelMessage.ChatType.GROUP)
                    .content("大家好")
                    .mentionedAgent(false)
                    .build();

            Decision decision = engine.decide(msg);
            assertThat(decision.type()).isEqualTo(DecisionType.IGNORE);
        }

        @Test
        @DisplayName("非群聊消息 → IGNORE")
        void nonGroupChatIgnored() {
            ChannelMessage msg = ChannelMessage.builder()
                    .messageId("m1")
                    .senderId("user1")
                    .chatId("private1")
                    .chatType(ChannelMessage.ChatType.PRIVATE)
                    .content("你好")
                    .mentionedAgent(false)
                    .build();

            Decision decision = engine.decide(msg);
            assertThat(decision.type()).isEqualTo(DecisionType.IGNORE);
        }
    }

    // ==================== REPLY 决策 ====================

    @Nested
    @DisplayName("REPLY 决策")
    class ReplyDecision {

        @Test
        @DisplayName("无运行任务时直接回复")
        void replyWhenNoRunningTask() {
            ChannelMessage msg = buildGroupMentionMessage("user1", "group1", "帮忙");
            UserProfile profile = buildProfile("user1", UserRole.MEMBER);

            when(userProfileService.getUserProfile("user1")).thenReturn(profile);
            when(taskScheduler.getRunningTask("group1")).thenReturn(Optional.empty());
            when(taskScheduler.calculatePriority(eq("user1"), eq(UserRole.MEMBER), eq("帮忙"), anyBoolean(), anyBoolean()))
                    .thenReturn(50);

            Decision decision = engine.decide(msg);
            assertThat(decision.type()).isEqualTo(DecisionType.REPLY);
            assertThat(decision.priority()).isEqualTo(50);
        }
    }

    // ==================== MERGE 决策 ====================

    @Nested
    @DisplayName("MERGE 决策")
    class MergeDecision {

        @Test
        @DisplayName("同一用户连续消息在去抖窗口内合并")
        void mergeSameUserWithinDebounce() {
            ChannelMessage msg = buildGroupMentionMessage("user1", "group1", "补充内容");
            UserProfile profile = buildProfile("user1", UserRole.MEMBER);
            AgentTask running = buildRunningTask("t1", "group1", "user1", Instant.now());

            when(userProfileService.getUserProfile("user1")).thenReturn(profile);
            when(taskScheduler.getRunningTask("group1")).thenReturn(Optional.of(running));

            Decision decision = engine.decide(msg);
            assertThat(decision.type()).isEqualTo(DecisionType.MERGE);
        }
    }

    // ==================== QUEUE 决策 ====================

    @Nested
    @DisplayName("QUEUE 决策")
    class QueueDecision {

        @Test
        @DisplayName("不同用户消息入队")
        void queueDifferentUserMessage() {
            ChannelMessage msg = buildGroupMentionMessage("user2", "group1", "我也要问");
            UserProfile profile = buildProfile("user2", UserRole.MEMBER);
            AgentTask running = buildRunningTask("t1", "group1", "user1", "thread1");

            when(userProfileService.getUserProfile("user2")).thenReturn(profile);
            when(taskScheduler.getRunningTask("group1")).thenReturn(Optional.of(running));
            when(taskScheduler.calculatePriority(eq("user2"), eq(UserRole.MEMBER), eq("我也要问"), anyBoolean(), anyBoolean()))
                    .thenReturn(50);
            when(taskScheduler.getQueueSize("group1")).thenReturn(0);

            Decision decision = engine.decide(msg);
            assertThat(decision.type()).isEqualTo(DecisionType.QUEUE);
        }
    }

    // ==================== INTERRUPT 决策 ====================

    @Nested
    @DisplayName("INTERRUPT 决策")
    class InterruptDecision {

        @Test
        @DisplayName("高优先级消息可打断")
        void interruptHighPriority() {
            ChannelMessage msg = buildGroupMentionMessage("owner1", "group1", "紧急任务");
            UserProfile profile = buildProfile("owner1", UserRole.OWNER);
            AgentTask running = buildRunningTask("t1", "group1", "user1", "thread1");
            running.setPriority(50);

            when(userProfileService.getUserProfile("owner1")).thenReturn(profile);
            when(taskScheduler.getRunningTask("group1")).thenReturn(Optional.of(running));
            when(taskScheduler.calculatePriority(eq("owner1"), eq(UserRole.OWNER), eq("紧急任务"), anyBoolean(), anyBoolean()))
                    .thenReturn(125);

            Decision decision = engine.decide(msg);
            assertThat(decision.type()).isEqualTo(DecisionType.INTERRUPT);
        }
    }

    // ==================== 辅助方法 ====================

    private ChannelMessage buildGroupMentionMessage(String senderId, String chatId, String content) {
        return ChannelMessage.builder()
                .messageId("m1")
                .senderId(senderId)
                .senderName("User-" + senderId)
                .chatId(chatId)
                .chatType(ChannelMessage.ChatType.GROUP)
                .content(content)
                .mentionedAgent(true)
                .build();
    }

    private UserProfile buildProfile(String userId, UserRole role) {
        return UserProfile.builder()
                .userId(userId)
                .nickname("User-" + userId)
                .role(role)
                .build();
    }

    private AgentTask buildRunningTask(String taskId, String groupId, String userId, String threadId) {
        return AgentTask.builder()
                .taskId(taskId)
                .groupId(groupId)
                .userId(userId)
                .threadId(threadId)
                .priority(50)
                .status(AgentTask.TaskStatus.RUNNING)
                .createdAt(Instant.now().minusSeconds(5))
                .build();
    }

    private AgentTask buildRunningTask(String taskId, String groupId, String userId, Instant createdAt) {
        return AgentTask.builder()
                .taskId(taskId)
                .groupId(groupId)
                .userId(userId)
                .priority(50)
                .status(AgentTask.TaskStatus.RUNNING)
                .createdAt(createdAt)
                .build();
    }
}