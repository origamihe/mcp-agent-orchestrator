package com.mcp.gateway.host.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.gateway.channel.ChannelOrchestrator;
import com.mcp.gateway.host.capability.CapabilityRouter;
import com.mcp.gateway.host.event.HostEvent;
import com.mcp.gateway.host.event.HostEventBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Host 桥接层 — 统一处理所有来自 Host 插件的消息。
 * 根据消息类型分发到：事件总线、能力路由、聊天链路。
 * 这一层是 Gateway 与插件的唯一接口，不依赖任何具体 IDE。
 */
@Slf4j
@Component
public class HostBridge {

    private final HostEventBus eventBus;
    private final CapabilityRouter capabilityRouter;
    private final ChannelOrchestrator channelOrchestrator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HostBridge(HostEventBus eventBus,
                      CapabilityRouter capabilityRouter,
                      ChannelOrchestrator channelOrchestrator) {
        this.eventBus = eventBus;
        this.capabilityRouter = capabilityRouter;
        this.channelOrchestrator = channelOrchestrator;
    }

    /**
     * 处理来自插件的原始 JSON 消息。
     * 根据 type 字段分发：
     * - "event"   → 发布到 HostEventBus
     * - "chat"    → 走聊天链路（ChannelOrchestrator）
     * - "capability_result" → 通知 CapabilityRouter
     * - "hello"   → 插件注册，返回可用能力列表
     */
    public Mono<Void> handleMessage(JsonNode payload) {
        String type = payload.has("type") ? payload.get("type").asText() : "chat";

        return switch (type) {
            case "event" -> handleEvent(payload);
            case "chat" -> handleChat(payload);
            case "capability_result" -> handleCapabilityResult(payload);
            case "hello" -> handleHello(payload);
            default -> {
                log.warn("[HostBridge] Unknown message type: {}", type);
                yield Mono.empty();
            }
        };
    }

    private Mono<Void> handleEvent(JsonNode payload) {
        HostEvent event = new HostEvent();
        event.setHostType(payload.has("hostType") ? payload.get("hostType").asText() : "ide");
        event.setIdeType(payload.has("ideType") ? payload.get("ideType").asText() : null);
        event.setWorkspaceId(payload.has("workspaceId") ? payload.get("workspaceId").asText() : null);
        event.setSessionId(payload.has("sessionId") ? payload.get("sessionId").asText() : null);

        if (payload.has("event") && payload.get("event").has("type")) {
            try {
                event.setType(HostEvent.EventType.valueOf(
                        payload.get("event").get("type").asText().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("[HostBridge] Unknown event type: {}", payload.get("event").get("type").asText());
                return Mono.empty();
            }
        } else {
            return Mono.empty();
        }

        if (payload.has("event") && payload.get("event").has("payload")) {
            Map<String, Object> eventPayload = new LinkedHashMap<>();
            payload.get("event").get("payload").fields().forEachRemaining(f ->
                    eventPayload.put(f.getKey(), objectMapper.convertValue(f.getValue(), Object.class)));
            event.setPayload(eventPayload);
        }

        log.info("[HostBridge] Event: {} from {} workspace={}",
                event.getType(), event.getHostType(), event.getWorkspaceId());
        eventBus.publish(event);
        return Mono.empty();
    }

    private Mono<Void> handleChat(JsonNode payload) {
        String hostType = payload.has("hostType") ? payload.get("hostType").asText() : "ide";
        return channelOrchestrator.handleMessage(hostType, payload);
    }

    private Mono<Void> handleCapabilityResult(JsonNode payload) {
        String callId = payload.has("callId") ? payload.get("callId").asText() : null;
        if (callId == null) return Mono.empty();

        Map<String, Object> result = new LinkedHashMap<>();
        if (payload.has("result")) {
            payload.get("result").fields().forEachRemaining(f ->
                    result.put(f.getKey(), objectMapper.convertValue(f.getValue(), Object.class)));
        }

        capabilityRouter.resolveResult(callId, result);
        return Mono.empty();
    }

    private Mono<Void> handleHello(JsonNode payload) {
        String hostType = payload.has("hostType") ? payload.get("hostType").asText() : "unknown";
        String ideType = payload.has("ideType") ? payload.get("ideType").asText() : "unknown";
        log.info("[HostBridge] Host registered: {} ({})", hostType, ideType);
        return Mono.empty();
    }
}