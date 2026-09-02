package com.mcp.gateway.host.capability;

import com.mcp.common.tool.ToolRiskLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 能力授权层 — 验证 Agent 是否被授权调用指定 Capability。
 *
 * 在 SandboxPolicy 之前执行，确保只有经过认证的会话才能发起能力调用。
 * 授权模型：
 * - session → authorized risk levels (L0-L5)
 * - 默认：新会话无任何授权，需要显式授权
 * - 原生会话（IDE 直接连接）可授权到 L3
 * - Agent 会话需通过确认流程逐步授权
 */
@Slf4j
@Component
public class CapabilityAuthorization {

    private final Map<String, Set<ToolRiskLevel>> sessionAuthorizations = new ConcurrentHashMap<>();

    private static final Set<ToolRiskLevel> DEFAULT_IDE_AUTHORIZATION = Set.of(
            ToolRiskLevel.L0, ToolRiskLevel.L1, ToolRiskLevel.L2, ToolRiskLevel.L3
    );

    private static final Set<ToolRiskLevel> DEFAULT_AGENT_AUTHORIZATION = Set.of(
            ToolRiskLevel.L0, ToolRiskLevel.L1
    );

    public enum AuthResult {
        ALLOWED,
        DENIED_NO_SESSION,
        DENIED_NOT_AUTHORIZED,
        DENIED_SESSION_CLOSED
    }

    public static class AuthDecision {
        private final AuthResult result;
        private final String message;

        public AuthDecision(AuthResult result, String message) {
            this.result = result;
            this.message = message;
        }

        public AuthResult result() { return result; }
        public String message() { return message; }
        public boolean isAllowed() { return result == AuthResult.ALLOWED; }

        public static AuthDecision allowed() {
            return new AuthDecision(AuthResult.ALLOWED, "Authorized");
        }

        public static AuthDecision denied(String reason) {
            return new AuthDecision(AuthResult.DENIED_NOT_AUTHORIZED, reason);
        }
    }

    public void authorizeSession(String sessionId, Set<ToolRiskLevel> levels) {
        sessionAuthorizations.put(sessionId, levels);
        log.info("[CapabilityAuth] Session {} authorized for levels: {}", sessionId, levels);
    }

    public void authorizeSessionAsIDE(String sessionId) {
        authorizeSession(sessionId, DEFAULT_IDE_AUTHORIZATION);
    }

    public void authorizeSessionAsAgent(String sessionId) {
        authorizeSession(sessionId, DEFAULT_AGENT_AUTHORIZATION);
    }

    public void revokeSession(String sessionId) {
        sessionAuthorizations.remove(sessionId);
        log.info("[CapabilityAuth] Session {} authorization revoked", sessionId);
    }

    public AuthDecision check(String sessionId, ToolRiskLevel requiredLevel) {
        Set<ToolRiskLevel> authorized = sessionAuthorizations.get(sessionId);
        if (authorized == null) {
            return new AuthDecision(AuthResult.DENIED_NOT_AUTHORIZED,
                    "Session not authorized. Session " + sessionId + " requires explicit authorization.");
        }

        if (!authorized.contains(requiredLevel)) {
            return new AuthDecision(AuthResult.DENIED_NOT_AUTHORIZED,
                    String.format("Session %s is authorized for %s, but capability requires %s",
                            sessionId, authorized, requiredLevel));
        }

        return AuthDecision.allowed();
    }

    public boolean isAuthorized(String sessionId, ToolRiskLevel level) {
        return check(sessionId, level).isAllowed();
    }
}