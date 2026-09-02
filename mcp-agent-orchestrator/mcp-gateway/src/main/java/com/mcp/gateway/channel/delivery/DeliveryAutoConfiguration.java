package com.mcp.gateway.channel.delivery;

import com.mcp.engine.delivery.DeliveryManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

/**
 * 投递渠道自动注册 — 在 Spring 启动时将 DeliveryChannel 实现注册到 DeliveryManager。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(DeliveryManager.class)
public class DeliveryAutoConfiguration {

    private final DeliveryManager deliveryManager;
    private final QQDeliveryChannel qqChannel;
    private final WebhookDeliveryChannel webhookChannel;
    private final EmailDeliveryChannel emailChannel;

    @PostConstruct
    public void registerChannels() {
        deliveryManager.registerChannel(qqChannel);
        deliveryManager.registerChannel(webhookChannel);
        deliveryManager.registerChannel(emailChannel);
        log.info("[DeliveryAutoConfiguration] Registered {} delivery channels",
                deliveryManager.getRegisteredChannels().size());
    }
}