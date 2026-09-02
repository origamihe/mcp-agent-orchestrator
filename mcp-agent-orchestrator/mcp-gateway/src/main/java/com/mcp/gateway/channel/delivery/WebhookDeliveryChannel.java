package com.mcp.gateway.channel.delivery;

import com.mcp.common.delivery.DeliveryChannel;
import com.mcp.common.delivery.DeliveryMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Webhook 投递渠道 — 将 Agent 响应投递到外部 HTTP 端点。
 */
@Slf4j
@Component
public class WebhookDeliveryChannel implements DeliveryChannel {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private volatile String webhookUrl;
    private volatile boolean available = true;

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public String getChannelType() {
        return "webhook";
    }

    @Override
    public String getDisplayName() {
        return "Webhook";
    }

    @Override
    public boolean isAvailable() {
        return available && webhookUrl != null && !webhookUrl.isBlank();
    }

    @Override
    public CompletableFuture<Boolean> send(DeliveryMessage message) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        String payload = String.format(
                "{\"type\":\"%s\",\"target\":\"%s\",\"content\":\"%s\"}",
                message.getContentType(), message.getTargetId(),
                escapeJson(message.getContent()));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
                    log.info("[WebhookDelivery] POST {} -> {} (status={})",
                            webhookUrl, message.getTargetId(), response.statusCode());
                    return success;
                })
                .exceptionally(e -> {
                    log.warn("[WebhookDelivery] Failed: {}", e.getMessage());
                    return false;
                });
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public Map<String, Object> getInfo() {
        return Map.of("type", "webhook", "available", available, "url", webhookUrl != null ? webhookUrl : "not set");
    }

    @Override
    public DeliveryMessage.ContentType[] getSupportedContentTypes() {
        return new DeliveryMessage.ContentType[]{
                DeliveryMessage.ContentType.TEXT,
                DeliveryMessage.ContentType.MARKDOWN,
                DeliveryMessage.ContentType.HTML,
                DeliveryMessage.ContentType.NOTIFICATION
        };
    }

    @Override
    public int getRateLimitPerSecond() {
        return 20;
    }
}