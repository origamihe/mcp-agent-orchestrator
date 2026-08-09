package com.mcp.common.delivery;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 投递渠道接口 — 消息投递的抽象层。
 *
 * 每个渠道（QQ、NapCat、Telegram、Email、HTTP Webhook 等）实现此接口，
 * 通过 DeliveryManager.registerChannel() 注册。
 *
 * 实现要求：
 * - send() 必须异步完成，返回 CompletableFuture
 * - 失败时 CompletableFuture 应 exceptionally complete
 * - isAvailable() 用于健康检查
 */
public interface DeliveryChannel {

    /**
     * 渠道唯一标识，如 "qq", "napcat", "telegram", "email", "webhook"。
     */
    String getChannelType();

    /**
     * 渠道显示名称。
     */
    String getDisplayName();

    /**
     * 渠道是否可用（连接正常、已认证等）。
     */
    boolean isAvailable();

    /**
     * 发送消息到渠道。
     *
     * @param message 投递消息
     * @return 异步结果，true 表示成功
     */
    CompletableFuture<Boolean> send(DeliveryMessage message);

    /**
     * 获取渠道信息（状态、配置等）。
     */
    Map<String, Object> getInfo();

    /**
     * 获取渠道支持的内容类型列表。
     */
    default DeliveryMessage.ContentType[] getSupportedContentTypes() {
        return new DeliveryMessage.ContentType[] { DeliveryMessage.ContentType.TEXT };
    }

    /**
     * 获取渠道的速率限制（每秒最大消息数）。
     */
    default int getRateLimitPerSecond() {
        return 10;
    }
}