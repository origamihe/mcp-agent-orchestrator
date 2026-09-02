package com.mcp.gateway.channel.delivery;

import com.mcp.common.delivery.DeliveryChannel;
import com.mcp.common.delivery.DeliveryMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * QQ 投递渠道 — 将 Agent 响应投递到 QQ 平台。
 */
@Slf4j
@Component
public class QQDeliveryChannel implements DeliveryChannel {

    private volatile boolean available = true;

    @Override
    public String getChannelType() {
        return "qq";
    }

    @Override
    public String getDisplayName() {
        return "QQ";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public CompletableFuture<Boolean> send(DeliveryMessage message) {
        log.info("[QQDelivery] Sending to {}: {}", message.getTargetId(),
                message.getContent() != null && message.getContent().length() > 50
                        ? message.getContent().substring(0, 50) + "..."
                        : message.getContent());

        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(50);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        });
    }

    @Override
    public Map<String, Object> getInfo() {
        return Map.of("type", "qq", "available", available, "protocol", "onebot");
    }

    @Override
    public DeliveryMessage.ContentType[] getSupportedContentTypes() {
        return new DeliveryMessage.ContentType[]{
                DeliveryMessage.ContentType.TEXT,
                DeliveryMessage.ContentType.MARKDOWN,
                DeliveryMessage.ContentType.IMAGE,
                DeliveryMessage.ContentType.FILE
        };
    }

    @Override
    public int getRateLimitPerSecond() {
        return 5;
    }
}