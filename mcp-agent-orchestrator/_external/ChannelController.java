package com.mcp.gateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcp.gateway.channel.ChannelAdapter;
import com.mcp.gateway.channel.ChannelAdapterRegistry;
import com.mcp.gateway.channel.ChannelOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/channel")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelOrchestrator channelOrchestrator;
    private final ChannelAdapterRegistry adapterRegistry;

    /** 获取所有渠道状态 */
    @GetMapping("/status")
    public Mono<ResponseEntity<List<Map<String, Object>>>> getStatus() {
        List<Map<String, Object>> statuses = new ArrayList<>();
        for (ChannelAdapter adapter : adapterRegistry.getAll()) {
            statuses.add(adapter.getStatus());
        }
        return Mono.just(ResponseEntity.ok(statuses));
    }

    /** OneBot v11 Webhook 回调（QQ） */
    @PostMapping("/qq/webhook")
    public Mono<ResponseEntity<Map<String, String>>> qqWebhook(@RequestBody JsonNode payload) {
        return channelOrchestrator.handleMessage("qq", payload)
                .thenReturn(ResponseEntity.ok(Map.of("status", "ok")))
                .onErrorResume(e -> {
                    log.error("[QQ Webhook] Error processing message: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage())));
                });
    }

    /** Telegram Webhook 回调（预留） */
    @PostMapping("/telegram/webhook")
    public Mono<ResponseEntity<Map<String, String>>> telegramWebhook(@RequestBody JsonNode payload) {
        return channelOrchestrator.handleMessage("telegram", payload)
                .thenReturn(ResponseEntity.ok(Map.of("status", "ok")));
    }

    /** Discord Webhook 回调（预留） */
    @PostMapping("/discord/webhook")
    public Mono<ResponseEntity<Map<String, String>>> discordWebhook(@RequestBody JsonNode payload) {
        return channelOrchestrator.handleMessage("discord", payload)
                .thenReturn(ResponseEntity.ok(Map.of("status", "ok")));
    }
}