package com.mcp.gateway.host.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.gateway.ws.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 能力路由器 — Gateway 调用插件能力的中枢。
 * Agent 通过 Tool 调用 read_file、list_directory 等能力时，
 * 路由器将请求转发到对应插件的 WebSocket 会话，等待结果返回。
 */
@Slf4j
@Component
public class CapabilityRouter {

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, CompletableFuture<Map<String, Object>>> pendingCalls =
            new ConcurrentHashMap<>();

    public CapabilityRouter(WebSocketSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * 调用插件能力并等待结果。
     * Agent 通过 Tool 调用此方法，例如 tool.call("read_file", {"filePath": "..."})
     */
    public Mono<Map<String, Object>> call(String sessionId, String capability, Map<String, Object> params) {
        String callId = UUID.randomUUID().toString();

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "capability_call");
        message.put("callId", callId);
        message.put("capability", capability);
        message.put("params", params);

        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pendingCalls.put(callId, future);

        try {
            String json = objectMapper.writeValueAsString(message);
            sessionManager.broadcast(json);
            log.info("[CapabilityRouter] Calling {} | callId={} | params={}", capability, callId, params);
        } catch (Exception e) {
            pendingCalls.remove(callId);
            future.completeExceptionally(e);
        }

        return Mono.fromFuture(future)
                .timeout(java.time.Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.warn("[CapabilityRouter] Capability {} failed: {}", capability, e.getMessage());
                    return Mono.just(Map.of("error", e.getMessage()));
                });
    }

    /**
     * 插件返回能力调用结果时调用此方法。
     */
    public void resolveResult(String callId, Map<String, Object> result) {
        CompletableFuture<Map<String, Object>> future = pendingCalls.remove(callId);
        if (future != null) {
            future.complete(result);
            log.debug("[CapabilityRouter] Resolved {} -> {}", callId, result.keySet());
        } else {
            log.warn("[CapabilityRouter] No pending call for callId={}", callId);
        }
    }
}