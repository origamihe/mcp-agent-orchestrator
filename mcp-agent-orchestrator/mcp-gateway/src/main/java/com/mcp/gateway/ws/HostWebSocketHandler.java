package com.mcp.gateway.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.gateway.host.bridge.HostBridge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * 统一的 Host WebSocket Handler。
 * 所有 Host 客户端（IDE、Desktop、Terminal）通过此端点建立长连接，
 * 以 JSON 消息进行双向通信。
 *
 * 认证：连接时需携带 ?token=xxx 参数，Gateway 验证通过后才允许建立会话。
 *
 * 消息类型：
 * - "hello"            → HostBridge    → 插件注册 + 会话授权
 * - "event"            → HostEventBus   → Memory / Planner / Skill
 * - "chat"             → ChannelOrchestrator → Agent 对话链路
 * - "capability_result" → CapabilityRouter → Agent Tool 调用返回
 */
@Slf4j
@Component
public class HostWebSocketHandler implements WebSocketHandler {

    private final HostBridge hostBridge;
    private final WebSocketSessionManager sessionManager;
    private final WebSocketAuthToken authToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HostWebSocketHandler(HostBridge hostBridge,
                                WebSocketSessionManager sessionManager,
                                WebSocketAuthToken authToken) {
        this.hostBridge = hostBridge;
        this.sessionManager = sessionManager;
        this.authToken = authToken;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        HandshakeInfo handshake = session.getHandshakeInfo();
        URI uri = handshake.getUri();

        // [P1-3] Token 认证
        String token = extractToken(uri);
        if (!authToken.validate(token)) {
            log.warn("[HostWS] Connection rejected: invalid or missing token from {}", uri.getHost());
            return session.close(org.springframework.web.reactive.socket.CloseStatus.POLICY_VIOLATION);
        }

        String sessionId = session.getId();
        log.info("[HostWS] Host connected (authenticated): {}", sessionId);
        sessionManager.register(sessionId, session);
        authToken.registerSession(sessionId, token);

        return session.receive()
                .map(msg -> msg.getPayloadAsText())
                .doOnNext(msg -> log.debug("[HostWS] Received: {}", msg))
                .flatMap(rawMessage -> {
                    try {
                        JsonNode payload = objectMapper.readTree(rawMessage);
                        return hostBridge.handleMessage(payload, sessionId)
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
                    authToken.unregisterSession(sessionId);
                })
                .then();
    }

    private String extractToken(URI uri) {
        String query = uri.getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}