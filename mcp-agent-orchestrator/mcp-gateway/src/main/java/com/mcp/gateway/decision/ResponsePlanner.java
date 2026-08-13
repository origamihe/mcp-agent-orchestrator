package com.mcp.gateway.decision;

import com.mcp.common.channel.ChannelMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 回复计划器 — 决定回复的目标用户和 @mention 策略。
 *
 * 规则：
 * - 群聊消息：回复时 @mention 原始发送者
 * - 私聊消息：不需要 @mention
 * - @all 消息：回复时 @mention 原始发送者
 */
@Slf4j
@Component
public class ResponsePlanner {

    /**
     * 生成回复计划。
     */
    public ResponsePlan plan(ChannelMessage msg) {
        if (msg.getChatType() != ChannelMessage.ChatType.GROUP) {
            return new ResponsePlan(null, false, null);
        }

        String mentionTargetId = null;
        boolean shouldMention = false;

        if (msg.isMentionedAgent()) {
            mentionTargetId = msg.getSenderId();
            shouldMention = true;
        }

        return new ResponsePlan(mentionTargetId, shouldMention, null);
    }

    /**
     * 回复计划。
     */
    public record ResponsePlan(
            String mentionTargetId,
            boolean shouldMention,
            String threadId) {

        public static final ResponsePlan NONE = new ResponsePlan(null, false, null);
    }
}