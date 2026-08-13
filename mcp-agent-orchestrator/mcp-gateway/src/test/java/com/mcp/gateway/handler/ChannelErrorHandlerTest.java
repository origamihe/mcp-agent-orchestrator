package com.mcp.gateway.handler;

import com.mcp.common.channel.ChannelMessage;
import com.mcp.common.channel.ChannelReply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChannelErrorHandler - 渠道错误降级处理")
class ChannelErrorHandlerTest {

    private ChannelErrorHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ChannelErrorHandler();
    }

    // ==================== 降级消息映射 ====================

    @Nested
    @DisplayName("错误消息映射")
    class ErrorMapping {

        private final ChannelMessage privateMsg = ChannelMessage.builder()
                .messageId("m1")
                .senderId("user1")
                .chatId("private1")
                .chatType(ChannelMessage.ChatType.PRIVATE)
                .content("test")
                .build();

        @Test
        @DisplayName("超时异常 → 超时提示")
        void timeoutToGracefulMessage() {
            ChannelReply reply = handler.buildFallbackReply(privateMsg, new TimeoutException("timed out"));
            assertThat(reply.getContent()).isEqualTo("处理超时，请稍候再试。");
        }

        @Test
        @DisplayName("429 错误 → 限流提示")
        void rateLimitToGracefulMessage() {
            ChannelReply reply = handler.buildFallbackReply(privateMsg,
                    new RuntimeException("HTTP 429 Too Many Requests"));
            assertThat(reply.getContent()).isEqualTo("当前请求过多，请稍等片刻再试。");
        }

        @Test
        @DisplayName("连接拒绝 → 不可用提示")
        void connectionRefusedToGracefulMessage() {
            ChannelReply reply = handler.buildFallbackReply(privateMsg,
                    new RuntimeException("Connection refused"));
            assertThat(reply.getContent()).isEqualTo("服务暂时不可用，请稍后重试。");
        }

        @Test
        @DisplayName("502 → 上游异常提示")
        void badGatewayToGracefulMessage() {
            ChannelReply reply = handler.buildFallbackReply(privateMsg,
                    new RuntimeException("502 Bad Gateway"));
            assertThat(reply.getContent()).isEqualTo("上游服务异常，请稍后重试。");
        }

        @Test
        @DisplayName("503 → 过载提示")
        void serviceUnavailableToGracefulMessage() {
            ChannelReply reply = handler.buildFallbackReply(privateMsg,
                    new RuntimeException("503 Service Unavailable"));
            assertThat(reply.getContent()).isEqualTo("服务暂时过载，请稍后重试。");
        }

        @Test
        @DisplayName("504 → 超时提示")
        void gatewayTimeoutToGracefulMessage() {
            ChannelReply reply = handler.buildFallbackReply(privateMsg,
                    new RuntimeException("504 Gateway Timeout"));
            assertThat(reply.getContent()).isEqualTo("处理超时，请稍候再试。");
        }

        @Test
        @DisplayName("未知错误 → 通用降级提示")
        void unknownErrorToGenericMessage() {
            ChannelReply reply = handler.buildFallbackReply(privateMsg,
                    new RuntimeException("some unknown error"));
            assertThat(reply.getContent()).isEqualTo("处理请求时遇到问题，请稍后再试。");
        }

        @Test
        @DisplayName("null 错误消息 → 通用降级提示")
        void nullMessageToGenericMessage() {
            ChannelReply reply = handler.buildFallbackReply(privateMsg,
                    new RuntimeException());
            assertThat(reply.getContent()).isEqualTo("处理请求时遇到问题，请稍后再试。");
        }
    }

    // ==================== 回复构建 ====================

    @Nested
    @DisplayName("回复构建")
    class ReplyBuilding {

        @Test
        @DisplayName("群聊降级回复携带 mentionTargetId")
        void groupReplyHasMention() {
            ChannelMessage groupMsg = ChannelMessage.builder()
                    .messageId("m1")
                    .senderId("user1")
                    .chatId("group1")
                    .chatType(ChannelMessage.ChatType.GROUP)
                    .content("test")
                    .mentionedAgent(true)
                    .build();

            ChannelReply reply = handler.buildFallbackReply(groupMsg,
                    new RuntimeException("timeout"));
            assertThat(reply.getMentionTargetId()).isEqualTo("user1");
            assertThat(reply.getTargetId()).isEqualTo("group1");
        }

        @Test
        @DisplayName("私聊降级回复不携带 mentionTargetId")
        void privateReplyNoMention() {
            ChannelMessage privateMsg = ChannelMessage.builder()
                    .messageId("m1")
                    .senderId("user1")
                    .chatId("private1")
                    .chatType(ChannelMessage.ChatType.PRIVATE)
                    .content("test")
                    .build();

            ChannelReply reply = handler.buildFallbackReply(privateMsg,
                    new RuntimeException("error"));
            // 私聊中 mentionTargetId 就是 senderId
            assertThat(reply.getTargetId()).isEqualTo("user1");
        }
    }

    // ==================== 可恢复性判断 ====================

    @Nested
    @DisplayName("可恢复性判断")
    class Recoverability {

        @Test
        @DisplayName("TimeoutException 可恢复")
        void timeoutIsRecoverable() {
            assertThat(handler.isRecoverable(new TimeoutException())).isTrue();
        }

        @Test
        @DisplayName("包含 429 可恢复")
        void rateLimitIsRecoverable() {
            assertThat(handler.isRecoverable(new RuntimeException("HTTP 429"))).isTrue();
        }

        @Test
        @DisplayName("包含 503 可恢复")
        void serviceUnavailableIsRecoverable() {
            assertThat(handler.isRecoverable(new RuntimeException("503 error"))).isTrue();
        }

        @Test
        @DisplayName("包含 timeout 可恢复")
        void timeoutStringIsRecoverable() {
            assertThat(handler.isRecoverable(new RuntimeException("timeout error"))).isTrue();
        }

        @Test
        @DisplayName("连接错误可恢复")
        void connectionErrorIsRecoverable() {
            assertThat(handler.isRecoverable(new RuntimeException("connection reset"))).isTrue();
        }

        @Test
        @DisplayName("未知错误不可恢复")
        void unknownErrorIsNotRecoverable() {
            assertThat(handler.isRecoverable(new RuntimeException("unknown fatal error"))).isFalse();
        }
    }

    @Test
    @DisplayName("默认超时时间 120 秒")
    void defaultTimeout() {
        assertThat(handler.getDefaultTimeout().getSeconds()).isEqualTo(120);
    }
}