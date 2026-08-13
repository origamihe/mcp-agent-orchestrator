package com.mcp.gateway.decision;

import com.mcp.common.channel.ChannelMessage;
import com.mcp.gateway.decision.ResponsePlanner.ResponsePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResponsePlanner - 回复策略规划")
class ResponsePlannerTest {

    private ResponsePlanner planner;

    @BeforeEach
    void setUp() {
        planner = new ResponsePlanner();
    }

    @Test
    @DisplayName("群聊 @Agent 消息 → 回复时 @mention 发送者")
    void groupMentionedAgentShouldMentionSender() {
        ChannelMessage msg = ChannelMessage.builder()
                .messageId("m1")
                .senderId("user123")
                .senderName("张三")
                .chatId("group1")
                .chatType(ChannelMessage.ChatType.GROUP)
                .content("帮忙查一下")
                .mentionedAgent(true)
                .build();

        ResponsePlan plan = planner.plan(msg);
        assertThat(plan.shouldMention()).isTrue();
        assertThat(plan.mentionTargetId()).isEqualTo("user123");
    }

    @Test
    @DisplayName("群聊未 @Agent 消息 → 不需要 @mention")
    void groupNotMentionedAgentNoMention() {
        ChannelMessage msg = ChannelMessage.builder()
                .messageId("m1")
                .senderId("user123")
                .chatId("group1")
                .chatType(ChannelMessage.ChatType.GROUP)
                .content("大家好")
                .mentionedAgent(false)
                .build();

        ResponsePlan plan = planner.plan(msg);
        assertThat(plan.shouldMention()).isFalse();
        assertThat(plan.mentionTargetId()).isNull();
    }

    @Test
    @DisplayName("私聊消息 → 不需要 @mention")
    void privateChatNoMention() {
        ChannelMessage msg = ChannelMessage.builder()
                .messageId("m1")
                .senderId("user123")
                .chatId("user123")
                .chatType(ChannelMessage.ChatType.PRIVATE)
                .content("你好")
                .mentionedAgent(true)
                .build();

        ResponsePlan plan = planner.plan(msg);
        assertThat(plan.shouldMention()).isFalse();
        assertThat(plan.mentionTargetId()).isNull();
    }

    @Test
    @DisplayName("ResponsePlan.NONE 常量")
    void nonePlan() {
        ResponsePlan none = ResponsePlan.NONE;
        assertThat(none.shouldMention()).isFalse();
        assertThat(none.mentionTargetId()).isNull();
        assertThat(none.threadId()).isNull();
    }
}