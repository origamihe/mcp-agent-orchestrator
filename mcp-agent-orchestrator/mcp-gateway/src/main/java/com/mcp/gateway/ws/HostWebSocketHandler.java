package com.mcp.gateway.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.gateway.host.bridge.HostBridge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

/**
 * 统一的 Host WebSocket Handler。
 * 所有 Host 客户端（IDE、Desktop、Terminal）通过此端点建立长连接，
 * 以 JSON 消息进行双向通信。
 *
 * 消息类型：
 * - "event"            → HostEventBus   → Memory / Planner / Skill
 * - "chat"             → ChannelOrchestrator → Agent 对话链路
 * - "capability_result" → CapabilityRouter → Agent Tool 调用返回
 * - "hello"            → HostBridge    → 插件注册
 */
@Slf4j
@Component
public class HostWebSocketHandler implements WebSocketHandler {

    private final HostBridge hostBridge;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HostWebSocketHandler(HostBridge hostBridge,
                                WebSocketSessionManager sessionManager) {
        this.hostBridge = hostBridge;
        this.sessionManager = sessionManager;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        log.info("[HostWS] Host connected: {}", sessionId);
        sessionManager.register(sessionId, session);

        return session.receive()
                .map(msg -> msg.getPayloadAsText())
                .doOnNext(msg -> log.debug("[HostWS] Received: {}", msg))
                .flatMap(rawMessage -> {
                    try {
                        JsonNode payload = objectMapper.readTree(rawMessage);
                        return hostBridge.handleMessage(payload)
                                .doOnError(e -> log.error("[HostWS] Error: {}", e.getMessage()))
                                .onErrorResume(e -> Mono.empty());
                    } catch (Exception e) {
                        log.error("[HostWS] Failed to parse message: {}", e.getMessage());
                        return Mono.empty();
                    }
                })
                .onErrorContinue((err, obj) ->
                        log.error("[HostWS] Stream error: {}", err.getMessage()))
                .doFinally(signal -> {
                    log.info("[HostWS] Host disconnected: {}", sessionId);
                    sessionManager.unregister(sessionId);
                })
                .then();
    }
}