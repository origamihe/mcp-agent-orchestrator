package com.mcp.engine.delivery;

import com.mcp.common.delivery.DeliveryChannel;
import com.mcp.common.delivery.DeliveryMessage;
import com.mcp.common.delivery.DeliveryResult;
import com.mcp.common.delivery.DeliveryStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * DeliveryManager — Agent Runtime 统一投递管理器。
 *
 * 核心职责：
 * 1. 消息投递 — 将 Agent 响应投递到指定渠道（QQ、NapCat、Telegram、Email 等）
 * 2. 文件投递 — 发送文件（报告、文档、图片）到渠道
 * 3. 多渠道路由 — 一条消息同时投递到多个渠道
 * 4. 重试 & 退避 — 投递失败自动重试，指数退避
 * 5. 投递历史 — 记录所有投递状态，支持查询
 * 6. 定时投递 — 支持延迟/定时发送
 *
 * 设计原则：
 * - 渠道适配器通过注册机制接入，不硬编码
 * - 投递状态全程可追踪
 * - 失败自动重试，最多 3 次
 * - 异步投递，不阻塞主流程
 */
@Slf4j
@Service
public class DeliveryManager {

    private final Map<String, DeliveryChannel> channels = new ConcurrentHashMap<>();
    private final Map<String, DeliveryResult> deliveryHistory = new ConcurrentHashMap<>();
    private final List<DeliveryResult> recentDeliveries = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "delivery-scheduler");
        t.setDaemon(true);
        return t;
    });

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1_000;
    private static final long MAX_BACKOFF_MS = 30_000;
    private static final int MAX_HISTORY = 500;

    private final List<Consumer<DeliveryResult>> listeners = new CopyOnWriteArrayList<>();

    // ==================== 渠道注册 ====================

    /**
     * 注册投递渠道。
     */
    public void registerChannel(DeliveryChannel channel) {
        channels.put(channel.getChannelType(), channel);
        log.info("[DeliveryManager] Registered channel: {}", channel.getChannelType());
    }

    /**
     * 注销投递渠道。
     */
    public void unregisterChannel(String channelType) {
        channels.remove(channelType);
        log.info("[DeliveryManager] Unregistered channel: {}", channelType);
    }

    /**
     * 获取已注册的渠道类型列表。
     */
    public List<String> getRegisteredChannels() {
        return new ArrayList<>(channels.keySet());
    }

    /**
     * 获取渠道信息。
     */
    public Map<String, Object> getChannelInfo(String channelType) {
        DeliveryChannel channel = channels.get(channelType);
        if (channel == null) return Collections.emptyMap();
        return channel.getInfo();
    }

    // ==================== 消息投递 ====================

    /**
     * 投递消息到指定渠道。
     *
     * @param message 投递消息
     * @return 投递结果（异步完成，立即返回 PENDING 状态）
     */
    public DeliveryResult deliver(DeliveryMessage message) {
        if (message == null || message.getChannelType() == null) {
            return DeliveryResult.failure(null, "unknown", "unknown", "Invalid message");
        }

        DeliveryChannel channel = channels.get(message.getChannelType());
        if (channel == null) {
            DeliveryResult result = DeliveryResult.failure(
                    message.getId(), message.getChannelType(), message.getTargetId(),
                    "Channel not registered: " + message.getChannelType());
            recordResult(result);
            return result;
        }

        if (!channel.isAvailable()) {
            DeliveryResult result = DeliveryResult.failure(
                    message.getId(), message.getChannelType(), message.getTargetId(),
                    "Channel unavailable: " + message.getChannelType());
            recordResult(result);
            return result;
        }

        DeliveryResult result = new DeliveryResult();
        result.setMessageId(message.getId());
        result.setChannelType(message.getChannelType());
        result.setTargetId(message.getTargetId());
        result.setStatus(DeliveryStatus.SENDING);
        result.setSentAt(Instant.now());
        result.setMaxRetries(MAX_RETRIES);
        recordResult(result);

        doDeliver(channel, message, result, 0);

        return result;
    }

    /**
     * 投递消息到多个渠道。
     *
     * @param channelTypes 目标渠道类型列表
     * @param content      消息内容
     * @param targetId     目标 ID
     * @return 每个渠道的投递结果
     */
    public List<DeliveryResult> broadcast(List<String> channelTypes, String content, String targetId) {
        if (channelTypes == null || channelTypes.isEmpty()) return Collections.emptyList();

        List<DeliveryResult> results = new ArrayList<>();
        for (String channelType : channelTypes) {
            DeliveryMessage message = DeliveryMessage.text(channelType, targetId, content);
            results.add(deliver(message));
        }
        log.info("[DeliveryManager] Broadcast to {} channels: {}", channelTypes.size(),
                channelTypes);
        return results;
    }

    /**
     * 投递文件到指定渠道。
     */
    public DeliveryResult deliverFile(String channelType, String targetId, String filePath, String caption) {
        DeliveryMessage message = DeliveryMessage.file(channelType, targetId, filePath, caption);
        return deliver(message);
    }

    /**
     * 投递 Markdown 消息到指定渠道。
     */
    public DeliveryResult deliverMarkdown(String channelType, String targetId, String content) {
        DeliveryMessage message = DeliveryMessage.markdown(channelType, targetId, content);
        return deliver(message);
    }

    // ==================== 定时投递 ====================

    /**
     * 延迟投递消息。
     *
     * @param message 投递消息
     * @param delay   延迟时间
     */
    public void deliverLater(DeliveryMessage message, Duration delay) {
        message.setScheduledAt(Instant.now().plus(delay));

        DeliveryResult result = new DeliveryResult();
        result.setMessageId(message.getId());
        result.setChannelType(message.getChannelType());
        result.setTargetId(message.getTargetId());
        result.setStatus(DeliveryStatus.SCHEDULED);
        recordResult(result);

        scheduler.schedule(() -> deliver(message), delay.toMillis(), TimeUnit.MILLISECONDS);
        log.info("[DeliveryManager] Scheduled delivery: {} in {}ms", message.getId(), delay.toMillis());
    }

    // ==================== 投递历史 ====================

    /**
     * 查询投递状态。
     */
    public DeliveryResult getDeliveryStatus(String messageId) {
        return deliveryHistory.get(messageId);
    }

    /**
     * 获取最近的投递记录。
     */
    public List<DeliveryResult> getRecentDeliveries(int limit) {
        int size = recentDeliveries.size();
        int from = Math.max(0, size - limit);
        return recentDeliveries.subList(from, size);
    }

    /**
     * 获取所有投递历史。
     */
    public Map<String, DeliveryResult> getDeliveryHistory() {
        return new LinkedHashMap<>(deliveryHistory);
    }

    // ==================== 监听器 ====================

    /**
     * 注册投递事件监听器。
     */
    public void addListener(Consumer<DeliveryResult> listener) {
        listeners.add(listener);
    }

    /**
     * 移除投递事件监听器。
     */
    public void removeListener(Consumer<DeliveryResult> listener) {
        listeners.remove(listener);
    }

    // ==================== 内部方法 ====================

    private void doDeliver(DeliveryChannel channel, DeliveryMessage message, DeliveryResult result, int attempt) {
        result.setAttemptCount(attempt + 1);

        channel.send(message).whenComplete((success, error) -> {
            if (error != null) {
                log.warn("[DeliveryManager] Delivery failed (attempt {}/{}): {} -> {}: {}",
                        attempt + 1, MAX_RETRIES, message.getChannelType(),
                        message.getTargetId(), error.getMessage());

                if (attempt < MAX_RETRIES - 1) {
                    result.setStatus(DeliveryStatus.RETRYING);
                    result.setErrorMessage(error.getMessage());
                    long backoff = Math.min(INITIAL_BACKOFF_MS * (1L << attempt), MAX_BACKOFF_MS);
                    scheduler.schedule(() -> doDeliver(channel, message, result, attempt + 1),
                            backoff, TimeUnit.MILLISECONDS);
                    log.info("[DeliveryManager] Retrying in {}ms (attempt {})", backoff, attempt + 2);
                } else {
                    result.setStatus(DeliveryStatus.FAILED);
                    result.setErrorMessage(error.getMessage());
                    result.setCompletedAt(Instant.now());
                    notifyListeners(result);
                    log.error("[DeliveryManager] Delivery permanently failed after {} attempts: {}",
                            MAX_RETRIES, message.getId());
                }
            } else {
                result.setStatus(DeliveryStatus.DELIVERED);
                result.setCompletedAt(Instant.now());
                notifyListeners(result);
                log.info("[DeliveryManager] Delivered: {} -> {} ({})",
                        message.getChannelType(), message.getTargetId(), message.getContentType());
            }
        });
    }

    private void recordResult(DeliveryResult result) {
        deliveryHistory.put(result.getMessageId(), result);
        recentDeliveries.add(result);
        while (recentDeliveries.size() > MAX_HISTORY) {
            recentDeliveries.remove(0);
        }
    }

    private void notifyListeners(DeliveryResult result) {
        for (Consumer<DeliveryResult> listener : listeners) {
            try {
                listener.accept(result);
            } catch (Exception e) {
                log.warn("[DeliveryManager] Listener error: {}", e.getMessage());
            }
        }
    }
}