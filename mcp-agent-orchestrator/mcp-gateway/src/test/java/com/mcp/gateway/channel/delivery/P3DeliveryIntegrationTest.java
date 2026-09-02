package com.mcp.gateway.channel.delivery;

import com.mcp.common.delivery.DeliveryChannel;
import com.mcp.common.delivery.DeliveryMessage;
import com.mcp.common.delivery.DeliveryResult;
import com.mcp.common.delivery.DeliveryStatus;
import com.mcp.engine.delivery.DeliveryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * P3: Delivery 渠道实现 + 集成验证。
 */
@DisplayName("P3 — Delivery 渠道实现与集成测试")
class P3DeliveryIntegrationTest {

    // ==================== QQ 渠道 ====================

    @Nested
    @DisplayName("QQDeliveryChannel")
    class QQChannelTests {

        @Test
        @DisplayName("基本属性")
        void shouldHaveCorrectProperties() {
            QQDeliveryChannel channel = new QQDeliveryChannel();
            assertEquals("qq", channel.getChannelType());
            assertEquals("QQ", channel.getDisplayName());
            assertTrue(channel.isAvailable());
        }

        @Test
        @DisplayName("发送文本消息")
        void shouldSendTextMessage() {
            QQDeliveryChannel channel = new QQDeliveryChannel();
            DeliveryMessage msg = DeliveryMessage.text("qq", "user123", "Hello");
            CompletableFuture<Boolean> result = channel.send(msg);
            assertTrue(result.join());
        }

        @Test
        @DisplayName("支持的内容类型")
        void shouldSupportMultipleContentTypes() {
            QQDeliveryChannel channel = new QQDeliveryChannel();
            DeliveryMessage.ContentType[] types = channel.getSupportedContentTypes();
            assertThat(types).contains(
                    DeliveryMessage.ContentType.TEXT,
                    DeliveryMessage.ContentType.MARKDOWN,
                    DeliveryMessage.ContentType.IMAGE,
                    DeliveryMessage.ContentType.FILE);
        }

        @Test
        @DisplayName("速率限制")
        void shouldHaveRateLimit() {
            QQDeliveryChannel channel = new QQDeliveryChannel();
            assertEquals(5, channel.getRateLimitPerSecond());
        }

        @Test
        @DisplayName("渠道信息")
        void shouldReturnChannelInfo() {
            QQDeliveryChannel channel = new QQDeliveryChannel();
            Map<String, Object> info = channel.getInfo();
            assertEquals("qq", info.get("type"));
            assertEquals("onebot", info.get("protocol"));
        }
    }

    // ==================== Webhook 渠道 ====================

    @Nested
    @DisplayName("WebhookDeliveryChannel")
    class WebhookChannelTests {

        @Test
        @DisplayName("未设置 URL 时不可用")
        void shouldBeUnavailableWithoutUrl() {
            WebhookDeliveryChannel channel = new WebhookDeliveryChannel();
            assertFalse(channel.isAvailable());
        }

        @Test
        @DisplayName("设置 URL 后可用")
        void shouldBeAvailableWithUrl() {
            WebhookDeliveryChannel channel = new WebhookDeliveryChannel();
            channel.setWebhookUrl("https://example.com/webhook");
            assertTrue(channel.isAvailable());
        }

        @Test
        @DisplayName("未设置 URL 时发送失败")
        void shouldFailWithoutUrl() {
            WebhookDeliveryChannel channel = new WebhookDeliveryChannel();
            DeliveryMessage msg = DeliveryMessage.text("webhook", "target", "test");
            CompletableFuture<Boolean> result = channel.send(msg);
            assertFalse(result.join());
        }

        @Test
        @DisplayName("支持的内容类型")
        void shouldSupportContentTypes() {
            WebhookDeliveryChannel channel = new WebhookDeliveryChannel();
            DeliveryMessage.ContentType[] types = channel.getSupportedContentTypes();
            assertThat(types).contains(
                    DeliveryMessage.ContentType.TEXT,
                    DeliveryMessage.ContentType.MARKDOWN,
                    DeliveryMessage.ContentType.HTML,
                    DeliveryMessage.ContentType.NOTIFICATION);
        }
    }

    // ==================== Email 渠道 ====================

    @Nested
    @DisplayName("EmailDeliveryChannel")
    class EmailChannelTests {

        @Test
        @DisplayName("基本属性")
        void shouldHaveCorrectProperties() {
            EmailDeliveryChannel channel = new EmailDeliveryChannel();
            assertEquals("email", channel.getChannelType());
            assertEquals("Email", channel.getDisplayName());
            assertTrue(channel.isAvailable());
        }

        @Test
        @DisplayName("发送邮件")
        void shouldSendEmail() {
            EmailDeliveryChannel channel = new EmailDeliveryChannel();
            DeliveryMessage msg = DeliveryMessage.text("email", "user@example.com", "Subject: Test");
            CompletableFuture<Boolean> result = channel.send(msg);
            assertTrue(result.join());
        }

        @Test
        @DisplayName("速率限制较低")
        void shouldHaveLowRateLimit() {
            EmailDeliveryChannel channel = new EmailDeliveryChannel();
            assertEquals(2, channel.getRateLimitPerSecond());
        }
    }

    // ==================== DeliveryManager 集成 ====================

    @Nested
    @DisplayName("DeliveryManager 多渠道路由")
    class DeliveryManagerIntegration {

        private DeliveryManager deliveryManager;
        private QQDeliveryChannel qqChannel;
        private EmailDeliveryChannel emailChannel;

        @BeforeEach
        void setUp() {
            deliveryManager = new DeliveryManager();
            qqChannel = new QQDeliveryChannel();
            emailChannel = new EmailDeliveryChannel();
            deliveryManager.registerChannel(qqChannel);
            deliveryManager.registerChannel(emailChannel);
        }

        @Test
        @DisplayName("注册多个渠道")
        void shouldRegisterMultipleChannels() {
            List<String> registered = deliveryManager.getRegisteredChannels();
            assertThat(registered).contains("qq", "email");
        }

        @Test
        @DisplayName("注销渠道")
        void shouldUnregisterChannel() {
            deliveryManager.unregisterChannel("qq");
            List<String> registered = deliveryManager.getRegisteredChannels();
            assertThat(registered).doesNotContain("qq");
            assertThat(registered).contains("email");
        }

        @Test
        @DisplayName("投递到已注册渠道")
        void shouldDeliverToRegisteredChannel() {
            DeliveryMessage msg = DeliveryMessage.text("qq", "user1", "test message");
            DeliveryResult result = deliveryManager.deliver(msg);
            assertNotNull(result);
            assertThat(result.getStatus()).isIn(DeliveryStatus.SENDING, DeliveryStatus.DELIVERED);
        }

        @Test
        @DisplayName("投递到未注册渠道返回失败")
        void shouldFailForUnregisteredChannel() {
            DeliveryMessage msg = DeliveryMessage.text("unknown", "user1", "test");
            DeliveryResult result = deliveryManager.deliver(msg);
            assertEquals(DeliveryStatus.FAILED, result.getStatus());
        }

        @Test
        @DisplayName("Broadcast 多渠道路由")
        void shouldBroadcastToMultipleChannels() {
            List<DeliveryResult> results = deliveryManager.broadcast(
                    List.of("qq", "email"), "Hello World", "user1");
            assertEquals(2, results.size());
        }

        @Test
        @DisplayName("投递文件")
        void shouldDeliverFile() {
            DeliveryResult result = deliveryManager.deliverFile(
                    "qq", "user1", "/tmp/report.pdf", "Report");
            assertNotNull(result);
        }

        @Test
        @DisplayName("投递 Markdown")
        void shouldDeliverMarkdown() {
            DeliveryResult result = deliveryManager.deliverMarkdown(
                    "qq", "user1", "**Bold** text");
            assertNotNull(result);
        }
    }
}