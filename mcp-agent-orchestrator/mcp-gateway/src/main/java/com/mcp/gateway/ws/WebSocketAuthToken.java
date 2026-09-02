package com.mcp.gateway.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 认证 Token 管理器。
 *
 * 用于验证 WebSocket 连接是否来自受信任的 Plugin 客户端。
 * 每个 Host 连接时需携带预共享的 token，Gateway 验证通过后才允许建立会话。
 */
@Slf4j
@Component
public class WebSocketAuthToken {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String sharedToken;

    private final Map<String, String> sessionTokens = new ConcurrentHashMap<>();

    public WebSocketAuthToken() {
        this.sharedToken = generateToken();
        log.info("[WebSocketAuth] Shared token generated: {}", sharedToken.substring(0, 8) + "...");
    }

    public WebSocketAuthToken(String presetToken) {
        this.sharedToken = presetToken;
        log.info("[WebSocketAuth] Using preset token: {}", presetToken.substring(0, Math.min(8, presetToken.length())) + "...");
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String getSharedToken() {
        return sharedToken;
    }

    public boolean validate(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        return sharedToken.equals(token);
    }

    public void registerSession(String sessionId, String token) {
        sessionTokens.put(sessionId, token);
    }

    public void unregisterSession(String sessionId) {
        sessionTokens.remove(sessionId);
    }

    public boolean isSessionValid(String sessionId) {
        return sessionTokens.containsKey(sessionId);
    }
}