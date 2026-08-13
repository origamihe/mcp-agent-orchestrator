package com.mcp.gateway.channel.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcp.common.channel.ChannelMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OneBotChannelAdapter CQ Code 统一解析测试。
 * 覆盖 message 为 string / array 两种格式，以及 @Agent、@all、reply、私聊等场景。
 */
@DisplayName("OneBotChannelAdapter 统一 CQ Code 解析")
class OneBotChannelAdapterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BOT_QQ = "3559443515";
    private static final String OTHER_QQ = "999888777";
    private static final String GROUP_ID = "123456789";
    private static final String SENDER_QQ = "987654321";
    private static final String SENDER_NAME = "TestUser";

    private OneBotChannelAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OneBotChannelAdapter();
        adapter.setQqNumber(BOT_QQ);
    }

    // ==================== 辅助方法 ====================

    private ObjectNode groupPayload(String messageType) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("post_type", "message");
        payload.put("message_type", messageType);
        payload.put("message_id", 123456789L);
        payload.put("user_id", Long.parseLong(SENDER_QQ));
        payload.put("self_id", Long.parseLong(BOT_QQ));
        if ("group".equals(messageType)) {
            payload.put("group_id", Long.parseLong(GROUP_ID));
        }
        ObjectNode sender = MAPPER.createObjectNode();
        sender.put("user_id", Long.parseLong(SENDER_QQ));
        sender.put("nickname", SENDER_NAME);
        payload.set("sender", sender);
        return payload;
    }

    private ObjectNode groupPayloadWithArray(ArrayNode messageArray) {
        ObjectNode payload = groupPayload("group");
        payload.set("message", messageArray);
        String rawCq = buildRawCqFromArray(messageArray);
        payload.put("raw_message", rawCq);
        return payload;
    }

    private ObjectNode groupPayloadWithString(String cqString) {
        ObjectNode payload = groupPayload("group");
        payload.put("message", cqString);
        payload.put("raw_message", cqString);
        return payload;
    }

    private ObjectNode groupPayloadWithRawOnly(String rawMessage, String messageText) {
        ObjectNode payload = groupPayload("group");
        payload.put("message", messageText);
        payload.put("raw_message", rawMessage);
        return payload;
    }

    private ObjectNode privatePayload() {
        ObjectNode payload = groupPayload("private");
        payload.remove("group_id");
        return payload;
    }

    private String buildRawCqFromArray(ArrayNode segments) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode seg : segments) {
            String type = seg.get("type").asText();
            JsonNode data = seg.get("data");
            StringBuilder params = new StringBuilder();
            if (data != null) {
                data.fields().forEachRemaining(e ->
                        params.append(params.length() > 0 ? "," : "")
                                .append(e.getKey()).append("=").append(e.getValue().asText()));
            }
            sb.append("[CQ:").append(type);
            if (params.length() > 0) sb.append(",").append(params);
            sb.append("]");
        }
        return sb.toString();
    }

    private ArrayNode atAgentSegment() {
        ArrayNode arr = MAPPER.createArrayNode();
        ObjectNode at = MAPPER.createObjectNode();
        at.put("type", "at");
        ObjectNode atData = MAPPER.createObjectNode();
        atData.put("qq", BOT_QQ);
        at.set("data", atData);
        arr.add(at);
        return arr;
    }

    private ArrayNode textSegment(String text) {
        ArrayNode arr = MAPPER.createArrayNode();
        ObjectNode t = MAPPER.createObjectNode();
        t.put("type", "text");
        ObjectNode tData = MAPPER.createObjectNode();
        tData.put("text", text);
        t.set("data", tData);
        arr.add(t);
        return arr;
    }

    private ArrayNode atOtherSegment() {
        ArrayNode arr = MAPPER.createArrayNode();
        ObjectNode at = MAPPER.createObjectNode();
        at.put("type", "at");
        ObjectNode atData = MAPPER.createObjectNode();
        atData.put("qq", OTHER_QQ);
        at.set("data", atData);
        arr.add(at);
        return arr;
    }

    private ArrayNode replySegment(String msgId) {
        ArrayNode arr = MAPPER.createArrayNode();
        ObjectNode r = MAPPER.createObjectNode();
        r.put("type", "reply");
        ObjectNode rData = MAPPER.createObjectNode();
        rData.put("id", msgId);
        r.set("data", rData);
        arr.add(r);
        return arr;
    }

    private ArrayNode atAllSegment() {
        ArrayNode arr = MAPPER.createArrayNode();
        ObjectNode at = MAPPER.createObjectNode();
        at.put("type", "at");
        ObjectNode atData = MAPPER.createObjectNode();
        atData.put("qq", "all");
        at.set("data", atData);
        arr.add(at);
        return arr;
    }

    private ArrayNode mergeArrays(ArrayNode... arrays) {
        ArrayNode result = MAPPER.createArrayNode();
        for (ArrayNode arr : arrays) {
            result.addAll(arr);
        }
        return result;
    }

    // ==================== 场景 1：array @Agent ====================

    @Test
    @DisplayName("array @Agent → mentionedAgent=true, text=正确提取")
    void arrayAtAgent() {
        ArrayNode msg = mergeArrays(atAgentSegment(), textSegment(" 你好"));
        ObjectNode payload = groupPayloadWithArray(msg);

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.isMentionedAgent()).isTrue();
        assertThat(result.getMentionedUsers()).contains(BOT_QQ);
        assertThat(result.getContent()).isEqualTo("你好");
        assertThat(result.getChatType()).isEqualTo(ChannelMessage.ChatType.GROUP);
        assertThat(result.getChatId()).isEqualTo(GROUP_ID);
        assertThat(result.getMessageId()).isEqualTo("123456789");
    }

    // ==================== 场景 2：string CQ @Agent ====================

    @Test
    @DisplayName("string CQ @Agent → mentionedAgent=true, text=正确提取")
    void stringCqAtAgent() {
        ObjectNode payload = groupPayloadWithString("[CQ:at,qq=" + BOT_QQ + "] 你好");

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.isMentionedAgent()).isTrue();
        assertThat(result.getMentionedUsers()).contains(BOT_QQ);
        assertThat(result.getContent()).isEqualTo("你好");
    }

    // ==================== 场景 3：raw_message @Agent（message 为纯文本回退）====================

    @Test
    @DisplayName("raw_message @Agent → mentionedAgent=true, 从 raw_message 解析")
    void rawMessageAtAgent() {
        ObjectNode payload = groupPayloadWithRawOnly(
                "[CQ:at,qq=" + BOT_QQ + "] 帮我查一下",
                "帮我查一下");

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.isMentionedAgent()).isTrue();
        assertThat(result.getMentionedUsers()).contains(BOT_QQ);
        assertThat(result.getContent()).isEqualTo("帮我查一下");
    }

    // ==================== 场景 4：普通消息 ====================

    @Test
    @DisplayName("普通文本消息 → mentionedAgent=false")
    void normalTextMessage() {
        ObjectNode payload = groupPayloadWithString("大家好，今天天气不错");

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.isMentionedAgent()).isFalse();
        assertThat(result.getMentionedUsers()).isEmpty();
        assertThat(result.getContent()).isEqualTo("大家好，今天天气不错");
    }

    // ==================== 场景 5：@all ====================

    @Test
    @DisplayName("@all → mentionedAgent=false, mentionedUsers 包含 'all'")
    void atAll() {
        ObjectNode payload = groupPayloadWithString("[CQ:at,qq=all] 注意一下");

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.isMentionedAgent()).isFalse();
        assertThat(result.getMentionedUsers()).contains("all");
        assertThat(result.getContent()).isEqualTo("注意一下");
    }

    @Test
    @DisplayName("array @all → mentionedAgent=false")
    void arrayAtAll() {
        ArrayNode msg = mergeArrays(atAllSegment(), textSegment(" 注意一下"));
        ObjectNode payload = groupPayloadWithArray(msg);

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.isMentionedAgent()).isFalse();
        assertThat(result.getMentionedUsers()).contains("all");
    }

    // ==================== 场景 6：多个 @ ====================

    @Test
    @DisplayName("多个 @（含 Agent）→ mentionedAgent=true, 所有用户都在 mentionedUsers 中")
    void multipleAtWithAgent() {
        ObjectNode payload = groupPayloadWithString(
                "[CQ:at,qq=" + OTHER_QQ + "][CQ:at,qq=" + BOT_QQ + "] 你们好");

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.isMentionedAgent()).isTrue();
        assertThat(result.getMentionedUsers()).containsExactly(OTHER_QQ, BOT_QQ);
        assertThat(result.getContent()).isEqualTo("你们好");
    }

    @Test
    @DisplayName("array 多个 @（含 Agent）→ mentionedAgent=true")
    void arrayMultipleAtWithAgent() {
        ArrayNode msg = mergeArrays(atOtherSegment(), atAgentSegment(), textSegment(" 你们好"));
        ObjectNode payload = groupPayloadWithArray(msg);

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.isMentionedAgent()).isTrue();
        assertThat(result.getMentionedUsers()).containsExactly(OTHER_QQ, BOT_QQ);
    }

    // ==================== 场景 7：@其他用户 ====================

    @Test
    @DisplayName("@其他用户 → mentionedAgent=false")
    void atOtherUser() {
        ObjectNode payload = groupPayloadWithString("[CQ:at,qq=" + OTHER_QQ + "] 你好");

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.isMentionedAgent()).isFalse();
        assertThat(result.getMentionedUsers()).containsExactly(OTHER_QQ);
    }

    @Test
    @DisplayName("array @其他用户 → mentionedAgent=false")
    void arrayAtOtherUser() {
        ArrayNode msg = mergeArrays(atOtherSegment(), textSegment(" 你好"));
        ObjectNode payload = groupPayloadWithArray(msg);

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.isMentionedAgent()).isFalse();
        assertThat(result.getMentionedUsers()).containsExactly(OTHER_QQ);
    }

    // ==================== 场景 8：私聊 ====================

    @Test
    @DisplayName("私聊消息 → chatType=PRIVATE, mentionedAgent 不依赖 @")
    void privateMessage() {
        ObjectNode payload = privatePayload();
        payload.put("message", "你好 Agent");
        payload.put("raw_message", "你好 Agent");

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.getChatType()).isEqualTo(ChannelMessage.ChatType.PRIVATE);
        assertThat(result.getChatId()).isEqualTo(SENDER_QQ);
        assertThat(result.isMentionedAgent()).isFalse();
        assertThat(result.getContent()).isEqualTo("你好 Agent");
    }

    // ==================== 场景 9：reply Agent ====================

    @Test
    @DisplayName("reply + @Agent → replyToMessageId 正确, mentionedAgent=true")
    void replyToAgent() {
        ObjectNode payload = groupPayloadWithString(
                "[CQ:reply,id=789][CQ:at,qq=" + BOT_QQ + "] 好的");

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.getReplyToMessageId()).isEqualTo("789");
        assertThat(result.isMentionedAgent()).isTrue();
        assertThat(result.getContent()).isEqualTo("好的");
    }

    @Test
    @DisplayName("array reply + @Agent → replyToMessageId 正确")
    void arrayReplyToAgent() {
        ArrayNode msg = mergeArrays(replySegment("789"), atAgentSegment(), textSegment(" 好的"));
        ObjectNode payload = groupPayloadWithArray(msg);

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.getReplyToMessageId()).isEqualTo("789");
        assertThat(result.isMentionedAgent()).isTrue();
    }

    // ==================== 场景 10：message_id 正确传递 ====================

    @Test
    @DisplayName("message_id 为 number → 正确传递为 string")
    void messageIdNumber() {
        ObjectNode payload = groupPayload("group");
        payload.put("message_id", 123456789L);
        payload.put("message", "测试");
        payload.put("raw_message", "测试");

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.getMessageId()).isEqualTo("123456789");
    }

    @Test
    @DisplayName("message_id 为 string → 正确传递")
    void messageIdString() {
        ObjectNode payload = groupPayload("group");
        payload.put("message_id", "abc123");
        payload.put("message", "测试");
        payload.put("raw_message", "测试");

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.getMessageId()).isEqualTo("abc123");
    }

    @Test
    @DisplayName("message_id 缺失 → messageId 为 null")
    void messageIdMissing() {
        ObjectNode payload = groupPayload("group");
        payload.remove("message_id");
        payload.put("message", "测试");
        payload.put("raw_message", "测试");

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.getMessageId()).isNull();
    }

    // ==================== 场景 11：sender 信息正确传递 ====================

    @Test
    @DisplayName("sender 信息 → senderId 和 hostContext 正确")
    void senderInfo() {
        ObjectNode payload = groupPayloadWithString("测试消息");

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.getSenderId()).isEqualTo(SENDER_QQ);
        assertThat(result.getHostContext()).isNotNull();
        assertThat(result.getHostContext().getUserId()).isEqualTo(SENDER_QQ);
        assertThat(result.getHostContext().getUserName()).isEqualTo(SENDER_NAME);
    }

    // ==================== 场景 12：纯 CQ 码消息（无文本）====================

    @Test
    @DisplayName("纯 @Agent 无文本 → text 为空字符串")
    void pureAtAgentNoText() {
        ObjectNode payload = groupPayloadWithString("[CQ:at,qq=" + BOT_QQ + "]");

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.isMentionedAgent()).isTrue();
        assertThat(result.getContent()).isEqualTo("");
    }

    // ==================== 场景 13：self_id 缺失时回退到 qqNumber ====================

    @Test
    @DisplayName("无 self_id → 回退到配置的 qqNumber 做匹配")
    void fallbackToQqNumberWhenSelfIdMissing() {
        ObjectNode payload = groupPayloadWithString("[CQ:at,qq=" + BOT_QQ + "] 你好");
        payload.remove("self_id");

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.isMentionedAgent()).isTrue();
    }

    // ==================== 场景 14：CQ:image 等非关键 CQ 码被正确剥离 ====================

    @Test
    @DisplayName("CQ:image 和 CQ:record 被正确剥离，不影响文本提取")
    void nonAtCqCodesStripped() {
        ObjectNode payload = groupPayloadWithString(
                "[CQ:image,file=abc.jpg][CQ:at,qq=" + BOT_QQ + "] 看看这张图");

        ChannelMessage result = adapter.normalize(payload);

        assertThat(result.isMentionedAgent()).isTrue();
        assertThat(result.getContent()).isEqualTo("看看这张图");
    }
}