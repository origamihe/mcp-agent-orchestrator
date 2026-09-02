package com.mcp.gateway.channel.delivery;

import com.mcp.common.delivery.DeliveryChannel;
import com.mcp.common.delivery.DeliveryMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Email 投递渠道 — 将 Agent 响应通过邮件投递。
 */
@Slf4j
@Component
public class EmailDeliveryChannel implements DeliveryChannel {

    private volatile boolean available = true;

    @Override
    public String getChannelType() {
        return "email";
    }

    @Override
    public String getDisplayName() {
        return "Email";
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
        log.info("[EmailDelivery] Sending email to {}: subject={}",
                message.getTargetId(),
                message.getContent() != null && message.getContent().length() > 50
                        ? message.getContent().substring(0, 50) + "..."
                        : message.getContent());

        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(100);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        });
    }

    @Override
    public Map<String, Object> getInfo() {
        return Map.of("type", "email", "available", available, "protocol", "smtp");
    }

    @Override
    public DeliveryMessage.ContentType[] getSupportedContentTypes() {
        return new DeliveryMessage.ContentType[]{
                DeliveryMessage.ContentType.TEXT,
                DeliveryMessage.ContentType.MARKDOWN,
                DeliveryMessage.ContentType.HTML,
                DeliveryMessage.ContentType.FILE,
                DeliveryMessage.ContentType.NOTIFICATION
        };
    }

    @Override
    public int getRateLimitPerSecond() {
        return 2;
    }
}