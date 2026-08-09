package com.mcp.engine.delivery;

import com.mcp.common.delivery.DeliveryChannel;
import com.mcp.common.delivery.DeliveryMessage;
import com.mcp.common.delivery.DeliveryResult;
import com.mcp.common.delivery.DeliveryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3 验证 — DeliveryManager 测试。
 * 验证：
 * 1. 渠道注册/注销
 * 2. 消息投递（成功/失败）
 * 3. 多渠道路由（broadcast）
 * 4. 文件投递
 * 5. Markdown 投递
 * 6. 重试机制
 * 7. 延迟投递
 * 8. 投递历史
 * 9. 监听器
 * 10. DeliveryMessage 模型
 * 11. DeliveryResult 模型
 */
@DisplayName("DeliveryManager — P3 投递管理器测试")
class DeliveryManagerTest {

    private DeliveryManager deliveryManager;

    @BeforeEach
    void setUp() {
        deliveryManager = new DeliveryManager();
    }

    /**
     * 快速成功的 Mock 渠道
     */
    private static class SuccessChannel implements DeliveryChannel {
        final String type;
        final List<DeliveryMessage> sentMessages = new ArrayList<>();

        SuccessChannel(String type) { this.type = type; }

        @Override public String getChannelType() { return type; }
        @Override public String getDisplayName() { return "Mock " + type; }
        @Override public boolean isAvailable() { return true; }

        @Override
        public CompletableFuture<Boolean> send(DeliveryMessage message) {
            sentMessages.add(message);
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public Map<String, Object> getInfo() {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("type", type);
            info.put("available", true);
            info.put("sent", sentMessages.size());
            return info;
        }
    }

    /**
     * 快速失败的 Mock 渠道
     */
    private static class FailingChannel implements DeliveryChannel {
        final String type;
        final String errorMsg;

        FailingChannel(String type, String errorMsg) {
            this.type = type;
            this.errorMsg = errorMsg;
        }

        @Override public String getChannelType() { return type; }
        @Override public String getDisplayName() { return "Failing " + type; }
        @Override public boolean isAvailable() { return true; }

        @Override
        public CompletableFuture<Boolean> send(DeliveryMessage message) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException(errorMsg));
            return future;
        }

        @Override
        public Map<String, Object> getInfo() {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("type", type);
            info.put("available", true);
            return info;
        }
    }

    /**
     * 不可用的渠道
     */
    private static class UnavailableChannel implements DeliveryChannel {
        @Override public String getChannelType() { return "offline"; }
        @Override public String getDisplayName() { return "Offline Channel"; }
        @Override public boolean isAvailable() { return false; }
        @Override public CompletableFuture<Boolean> send(DeliveryMessage message) {
            return CompletableFuture.completedFuture(false);
        }
        @Override public Map<String, Object> getInfo() {
            return Map.of("available", false);
        }
    }

    /**
     * 延迟响应的渠道（用于测试重试）
     */
    private static class DelayedSuccessChannel implements DeliveryChannel {
        final AtomicInteger callCount = new AtomicInteger(0);
        final int failCount;

        DelayedSuccessChannel(int failCount) { this.failCount = failCount; }

        @Override public String getChannelType() { return "delayed"; }
        @Override public String getDisplayName() { return "Delayed Channel"; }
        @Override public boolean isAvailable() { return true; }

        @Override
        public CompletableFuture<Boolean> send(DeliveryMessage message) {
            int count = callCount.incrementAndGet();
            if (count <= failCount) {
                CompletableFuture<Boolean> future = new CompletableFuture<>();
                future.completeExceptionally(new RuntimeException("Simulated failure #" + count));
                return future;
            }
            return CompletableFuture.completedFuture(true);
        }

        @Override public Map<String, Object> getInfo() { return Map.of("available", true); }
    }

    // ==================== 渠道注册 ====================

    @Nested
    @DisplayName("渠道注册/注销")
    class ChannelRegistration {

        @Test
        @DisplayName("注册渠道后应出现在注册列表中")
        void shouldRegisterChannel() {
            deliveryManager.registerChannel(new SuccessChannel("qq"));

            assertThat(deliveryManager.getRegisteredChannels()).contains("qq");
        }

        @Test
        @DisplayName("注销渠道后应从列表中移除")
        void shouldUnregisterChannel() {
            deliveryManager.registerChannel(new SuccessChannel("qq"));
            deliveryManager.unregisterChannel("qq");

            assertThat(deliveryManager.getRegisteredChannels()).doesNotContain("qq");
        }

        @Test
        @DisplayName("注册多个渠道应全部列出")
        void shouldListAllChannels() {
            deliveryManager.registerChannel(new SuccessChannel("qq"));
            deliveryManager.registerChannel(new SuccessChannel("telegram"));
            deliveryManager.registerChannel(new SuccessChannel("email"));

            assertThat(deliveryManager.getRegisteredChannels())
                    .containsExactlyInAnyOrder("qq", "telegram", "email");
        }

        @Test
        @DisplayName("getChannelInfo 应返回渠道信息")
        void shouldReturnChannelInfo() {
            deliveryManager.registerChannel(new SuccessChannel("qq"));

            Map<String, Object> info = deliveryManager.getChannelInfo("qq");
            assertThat(info).containsEntry("type", "qq");
            assertThat(info).containsEntry("available", true);
        }

        @Test
        @DisplayName("未注册的渠道应返回空 map")
        void shouldReturnEmptyMapForUnknownChannel() {
            assertThat(deliveryManager.getChannelInfo("unknown")).isEmpty();
        }
    }

    // ==================== 消息投递 ====================

    @Nested
    @DisplayName("消息投递")
    class MessageDelivery {

        @Test
        @DisplayName("投递到未注册的渠道应返回 FAILED")
        void shouldFailForUnregisteredChannel() {
            DeliveryMessage msg = DeliveryMessage.text("unknown", "target", "hello");

            DeliveryResult result = deliveryManager.deliver(msg);
            assertThat(result.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(result.getErrorMessage()).contains("not registered");
        }

        @Test
        @DisplayName("投递到不可用的渠道应返回 FAILED")
        void shouldFailForUnavailableChannel() {
            deliveryManager.registerChannel(new UnavailableChannel());
            DeliveryMessage msg = DeliveryMessage.text("offline", "target", "hello");

            DeliveryResult result = deliveryManager.deliver(msg);
            assertThat(result.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(result.getErrorMessage()).contains("unavailable");
        }

        @Test
        @DisplayName("投递到有效渠道应返回 SENDING 状态")
        void shouldReturnSendingForValidChannel() {
            deliveryManager.registerChannel(new SuccessChannel("qq"));
            DeliveryMessage msg = DeliveryMessage.text("qq", "user123", "Hello!");

            DeliveryResult result = deliveryManager.deliver(msg);
            assertThat(result.getStatus()).isIn(DeliveryStatus.SENDING, DeliveryStatus.DELIVERED);
            assertThat(result.getChannelType()).isEqualTo("qq");
            assertThat(result.getTargetId()).isEqualTo("user123");
        }

        @Test
        @DisplayName("null 消息应返回 FAILED")
        void shouldFailForNullMessage() {
            DeliveryResult result = deliveryManager.deliver(null);
            assertThat(result.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        }
    }

    // ==================== 多渠道路由 ====================

    @Nested
    @DisplayName("broadcast — 多渠道路由")
    class Broadcast {

        @Test
        @DisplayName("broadcast 应投递到所有指定渠道")
        void shouldBroadcastToAllChannels() {
            SuccessChannel qq = new SuccessChannel("qq");
            SuccessChannel telegram = new SuccessChannel("telegram");
            deliveryManager.registerChannel(qq);
            deliveryManager.registerChannel(telegram);

            List<DeliveryResult> results = deliveryManager.broadcast(
                    List.of("qq", "telegram"), "Hello all!", "group-1");

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(r -> r.getChannelType() != null);
        }

        @Test
        @DisplayName("broadcast 空列表应返回空结果")
        void shouldReturnEmptyForEmptyChannelList() {
            List<DeliveryResult> results = deliveryManager.broadcast(
                    List.of(), "Hello", "target");
            assertThat(results).isEmpty();
        }
    }

    // ==================== 文件投递 ====================

    @Nested
    @DisplayName("deliverFile — 文件投递")
    class FileDelivery {

        @Test
        @DisplayName("deliverFile 应设置 contentType=FILE")
        void shouldSetFileContentType() {
            SuccessChannel qq = new SuccessChannel("qq");
            deliveryManager.registerChannel(qq);

            deliveryManager.deliverFile("qq", "user123", "/path/to/report.pdf", "Here is the report");

            assertThat(qq.sentMessages).hasSize(1);
            DeliveryMessage sent = qq.sentMessages.get(0);
            assertThat(sent.getContentType()).isEqualTo(DeliveryMessage.ContentType.FILE);
            assertThat(sent.getFilePath()).isEqualTo("/path/to/report.pdf");
        }
    }

    // ==================== Markdown 投递 ====================

    @Nested
    @DisplayName("deliverMarkdown — Markdown 投递")
    class MarkdownDelivery {

        @Test
        @DisplayName("deliverMarkdown 应设置 contentType=MARKDOWN")
        void shouldSetMarkdownContentType() {
            SuccessChannel qq = new SuccessChannel("qq");
            deliveryManager.registerChannel(qq);

            deliveryManager.deliverMarkdown("qq", "user123", "# Title\n**bold**");

            assertThat(qq.sentMessages).hasSize(1);
            DeliveryMessage sent = qq.sentMessages.get(0);
            assertThat(sent.getContentType()).isEqualTo(DeliveryMessage.ContentType.MARKDOWN);
            assertThat(sent.getContent()).isEqualTo("# Title\n**bold**");
        }
    }

    // ==================== 重试机制 ====================

    @Nested
    @DisplayName("重试机制")
    class Retry {

        @Test
        @DisplayName("失败后应自动重试")
        void shouldRetryOnFailure() throws InterruptedException {
            DelayedSuccessChannel channel = new DelayedSuccessChannel(2); // fail 2 times, then succeed
            deliveryManager.registerChannel(channel);

            DeliveryMessage msg = DeliveryMessage.text("delayed", "user", "test");
            DeliveryResult result = deliveryManager.deliver(msg);

            Thread.sleep(5000);

            assertThat(channel.callCount.get()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("超过最大重试次数应标记 FAILED")
        void shouldFailAfterMaxRetries() throws InterruptedException {
            FailingChannel channel = new FailingChannel("fail", "always fails");
            deliveryManager.registerChannel(channel);

            DeliveryMessage msg = DeliveryMessage.text("fail", "user", "test");
            DeliveryResult result = deliveryManager.deliver(msg);

            Thread.sleep(5000);

            DeliveryResult finalResult = deliveryManager.getDeliveryStatus(msg.getId());
            assertThat(finalResult).isNotNull();
            assertThat(finalResult.getAttemptCount()).isGreaterThanOrEqualTo(1);
        }
    }

    // ==================== 延迟投递 ====================

    @Nested
    @DisplayName("deliverLater — 延迟投递")
    class DelayedDelivery {

        @Test
        @DisplayName("deliverLater 应设置 SCHEDULED 状态")
        void shouldSetScheduledStatus() {
            deliveryManager.registerChannel(new SuccessChannel("qq"));
            DeliveryMessage msg = DeliveryMessage.text("qq", "user", "scheduled");

            deliveryManager.deliverLater(msg, Duration.ofSeconds(10));

            DeliveryResult status = deliveryManager.getDeliveryStatus(msg.getId());
            assertThat(status).isNotNull();
            assertThat(status.getStatus()).isEqualTo(DeliveryStatus.SCHEDULED);
        }

        @Test
        @DisplayName("延迟消息应在指定时间后投递")
        void shouldDeliverAfterDelay() throws InterruptedException {
            SuccessChannel qq = new SuccessChannel("qq");
            deliveryManager.registerChannel(qq);
            DeliveryMessage msg = DeliveryMessage.text("qq", "user", "delayed msg");

            deliveryManager.deliverLater(msg, Duration.ofMillis(100));

            Thread.sleep(500);

            DeliveryResult status = deliveryManager.getDeliveryStatus(msg.getId());
            assertThat(status).isNotNull();
        }
    }

    // ==================== 投递历史 ====================

    @Nested
    @DisplayName("投递历史")
    class DeliveryHistory {

        @Test
        @DisplayName("getDeliveryStatus 应返回投递结果")
        void shouldReturnDeliveryStatus() {
            deliveryManager.registerChannel(new SuccessChannel("qq"));
            DeliveryMessage msg = DeliveryMessage.text("qq", "user", "hello");

            deliveryManager.deliver(msg);

            DeliveryResult status = deliveryManager.getDeliveryStatus(msg.getId());
            assertThat(status).isNotNull();
            assertThat(status.getMessageId()).isEqualTo(msg.getId());
        }

        @Test
        @DisplayName("getRecentDeliveries 应返回最近记录")
        void shouldReturnRecentDeliveries() {
            deliveryManager.registerChannel(new SuccessChannel("qq"));
            deliveryManager.deliver(DeliveryMessage.text("qq", "user1", "msg1"));
            deliveryManager.deliver(DeliveryMessage.text("qq", "user2", "msg2"));
            deliveryManager.deliver(DeliveryMessage.text("qq", "user3", "msg3"));

            List<DeliveryResult> recent = deliveryManager.getRecentDeliveries(2);
            assertThat(recent).hasSize(2);
        }

        @Test
        @DisplayName("getDeliveryHistory 应返回所有记录")
        void shouldReturnAllHistory() {
            deliveryManager.registerChannel(new SuccessChannel("qq"));
            deliveryManager.deliver(DeliveryMessage.text("qq", "user1", "msg1"));

            Map<String, DeliveryResult> history = deliveryManager.getDeliveryHistory();
            assertThat(history).isNotEmpty();
        }
    }

    // ==================== 监听器 ====================

    @Nested
    @DisplayName("事件监听器")
    class EventListeners {

        @Test
        @DisplayName("监听器应在投递完成时收到通知")
        void shouldNotifyListener() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            deliveryManager.registerChannel(new SuccessChannel("qq"));
            deliveryManager.addListener(result -> latch.countDown());

            deliveryManager.deliver(DeliveryMessage.text("qq", "user", "hello"));

            boolean notified = latch.await(3, TimeUnit.SECONDS);
            assertThat(notified).isTrue();
        }

        @Test
        @DisplayName("移除监听器后不应收到通知")
        void shouldNotNotifyRemovedListener() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            Consumer<DeliveryResult> listener = result -> latch.countDown();
            deliveryManager.addListener(listener);
            deliveryManager.removeListener(listener);

            deliveryManager.registerChannel(new SuccessChannel("qq"));
            deliveryManager.deliver(DeliveryMessage.text("qq", "user", "hello"));

            boolean notified = latch.await(1, TimeUnit.SECONDS);
            assertThat(notified).isFalse();
        }
    }

    // ==================== DeliveryMessage 模型 ====================

    @Nested
    @DisplayName("DeliveryMessage — 投递消息模型")
    class DeliveryMessageModel {

        @Test
        @DisplayName("text() 工厂方法应正确创建文本消息")
        void shouldCreateTextMessage() {
            DeliveryMessage msg = DeliveryMessage.text("qq", "user123", "Hello!");

            assertThat(msg.getChannelType()).isEqualTo("qq");
            assertThat(msg.getTargetId()).isEqualTo("user123");
            assertThat(msg.getContent()).isEqualTo("Hello!");
            assertThat(msg.getContentType()).isEqualTo(DeliveryMessage.ContentType.TEXT);
            assertThat(msg.getId()).isNotNull();
            assertThat(msg.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("file() 工厂方法应正确创建文件消息")
        void shouldCreateFileMessage() {
            DeliveryMessage msg = DeliveryMessage.file("qq", "user123", "/path/to/doc.pdf", "Report");

            assertThat(msg.getContentType()).isEqualTo(DeliveryMessage.ContentType.FILE);
            assertThat(msg.getFilePath()).isEqualTo("/path/to/doc.pdf");
            assertThat(msg.getContent()).isEqualTo("Report");
        }

        @Test
        @DisplayName("markdown() 工厂方法应正确创建 Markdown 消息")
        void shouldCreateMarkdownMessage() {
            DeliveryMessage msg = DeliveryMessage.markdown("qq", "user123", "# Title");

            assertThat(msg.getContentType()).isEqualTo(DeliveryMessage.ContentType.MARKDOWN);
            assertThat(msg.getContent()).isEqualTo("# Title");
        }

        @Test
        @DisplayName("notification() 工厂方法应正确创建通知消息")
        void shouldCreateNotificationMessage() {
            DeliveryMessage msg = DeliveryMessage.notification("qq", "user123", "Title", "Body");

            assertThat(msg.getContentType()).isEqualTo(DeliveryMessage.ContentType.NOTIFICATION);
            assertThat(msg.getContent()).isEqualTo("Title");
            assertThat(msg.getMetadata()).containsEntry("body", "Body");
        }

        @Test
        @DisplayName("addMetadata 应正确添加元数据")
        void shouldAddMetadata() {
            DeliveryMessage msg = DeliveryMessage.text("qq", "user", "hello");
            msg.addMetadata("key1", "value1");
            msg.addMetadata("key2", 42);

            assertThat(msg.getMetadata()).containsEntry("key1", "value1");
            assertThat(msg.getMetadata()).containsEntry("key2", 42);
        }
    }

    // ==================== DeliveryResult 模型 ====================

    @Nested
    @DisplayName("DeliveryResult — 投递结果模型")
    class DeliveryResultModel {

        @Test
        @DisplayName("success() 工厂方法应创建成功结果")
        void shouldCreateSuccessResult() {
            DeliveryResult result = DeliveryResult.success("msg-1", "qq", "user123");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
            assertThat(result.getMessageId()).isEqualTo("msg-1");
            assertThat(result.getChannelType()).isEqualTo("qq");
            assertThat(result.getTargetId()).isEqualTo("user123");
            assertThat(result.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("failure() 工厂方法应创建失败结果")
        void shouldCreateFailureResult() {
            DeliveryResult result = DeliveryResult.failure("msg-1", "qq", "user123", "Connection refused");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(result.getErrorMessage()).isEqualTo("Connection refused");
        }

        @Test
        @DisplayName("toString 应包含关键信息")
        void shouldHaveDescriptiveToString() {
            DeliveryResult result = DeliveryResult.success("msg-1", "qq", "user123");
            String str = result.toString();

            assertThat(str).contains("msg-1");
            assertThat(str).contains("qq");
            assertThat(str).contains("DELIVERED");
        }
    }
}