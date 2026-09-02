package com.mcp.gateway.host.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.common.tool.ToolRiskLevel;
import com.mcp.gateway.ws.WebSocketSessionManager;
import com.mcp.tools.sandbox.SandboxPolicy;
import com.mcp.tools.sandbox.WorkspaceSandbox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 能力路由器 — Gateway 调用插件能力的中枢，也是安全策略汇聚点。
 *
 * 安全控制面（每次调用必经）：
 * <pre>
 * Agent / Tool Call
 *      ↓
 * [0] CapabilityAuthorization — 验证会话授权
 *      ↓
 * [1] CapabilityRiskRegistry — 查询风险等级 (L0-L5)
 *      ↓
 * [2] SandboxPolicy.decide()  — 决定沙箱策略
 *      ↓
 * [3] 策略执行:
 *     BLOCKED            → 直接拒绝
 *     WORKSPACE_ISOLATION → WorkspaceSandbox 路径校验
 *     PROCESS_SANDBOX     → 强制超时/输出限制约束
 *      ↓
 * [4] CapabilityAuditLog  — 结构化审计
 *      ↓
 * [5] sendTo(sessionId)   — 定向路由（非 broadcast）
 *      ↓
 * WebSocket → Plugin → IDE/OS
 * </pre>
 */
@Slf4j
@Component
public class CapabilityRouter {

    private final WebSocketSessionManager sessionManager;
    private final CapabilityAuthorization capabilityAuth;
    private final CapabilityRiskRegistry riskRegistry;
    private final SandboxPolicy sandboxPolicy;
    private final WorkspaceSandbox workspaceSandbox;
    private final CapabilityAuditLog auditLog;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, CompletableFuture<Map<String, Object>>> pendingCalls =
            new ConcurrentHashMap<>();

    public CapabilityRouter(WebSocketSessionManager sessionManager,
                            CapabilityAuthorization capabilityAuth,
                            CapabilityRiskRegistry riskRegistry,
                            SandboxPolicy sandboxPolicy,
                            WorkspaceSandbox workspaceSandbox,
                            CapabilityAuditLog auditLog) {
        this.sessionManager = sessionManager;
        this.capabilityAuth = capabilityAuth;
        this.riskRegistry = riskRegistry;
        this.sandboxPolicy = sandboxPolicy;
        this.workspaceSandbox = workspaceSandbox;
        this.auditLog = auditLog;
    }

    /**
     * 调用插件能力并等待结果。
     * 执行前会经过完整的安全控制面校验。
     */
    public Mono<Map<String, Object>> call(String sessionId, String capability, Map<String, Object> params) {
        ToolRiskLevel risk = riskRegistry.getRiskLevel(capability);

        // [P1-1] 授权检查 — 在风险等级判断之前验证会话授权
        CapabilityAuthorization.AuthDecision authDecision = capabilityAuth.check(sessionId, risk);
        if (!authDecision.isAllowed()) {
            auditLog.record(sessionId, capability, risk, SandboxPolicy.Decision.BLOCKED,
                    "UNAUTHORIZED: " + authDecision.message(), params);
            log.warn("[CapabilityRouter] UNAUTHORIZED: sessionId={}, capability={}, reason={}",
                    sessionId, capability, authDecision.message());
            return Mono.just(Map.of(
                    "error", "Capability not authorized",
                    "capability", capability,
                    "riskLevel", risk.name(),
                    "reason", authDecision.message()
            ));
        }

        SandboxPolicy.Decision decision = sandboxPolicy.decide(risk);

        log.info("[CapabilityRouter] Request: capability={}, risk={}, decision={}, sessionId={}",
                capability, risk, decision, sessionId);

        // [P0-3] 策略 1: BLOCKED — 直接拒绝
        if (decision == SandboxPolicy.Decision.BLOCKED) {
            auditLog.record(sessionId, capability, risk, decision, "BLOCKED", params);
            log.warn("[CapabilityRouter] BLOCKED: capability={}, risk={}", capability, risk);
            return Mono.just(Map.of(
                    "error", "Capability blocked by security policy",
                    "capability", capability,
                    "riskLevel", risk.name()
            ));
        }

        // [P0-2] 策略 2: WORKSPACE_ISOLATION — 路径校验
        if (decision == SandboxPolicy.Decision.WORKSPACE_ISOLATION) {
            String filePath = extractFilePath(params);
            if (filePath != null) {
                Path resolved = workspaceSandbox.resolve(Path.of(filePath));
                if (resolved == null || !workspaceSandbox.isWriteAllowed(Path.of(filePath))) {
                    auditLog.record(sessionId, capability, risk, decision, "REJECTED: path outside workspace", params);
                    log.warn("[CapabilityRouter] REJECTED: path outside workspace, capability={}, filePath={}",
                            capability, filePath);
                    return Mono.just(Map.of(
                            "error", "Path outside workspace",
                            "filePath", filePath,
                            "workspaceRoot", workspaceSandbox.getWorkspaceRoot().toString()
                    ));
                }
            }
        }

        // [P0-1] 策略 3: PROCESS_SANDBOX — 强制沙箱约束
        if (decision == SandboxPolicy.Decision.PROCESS_SANDBOX) {
            params = new LinkedHashMap<>(params);
            params.putIfAbsent("_sandbox", "process");
            params.putIfAbsent("_timeout", "30");
            params.putIfAbsent("_outputLimit", "1048576");
        }

        // [审计] 记录通过安全校验的调用
        auditLog.record(sessionId, capability, risk, decision, "ALLOWED", params);

        // 构造消息
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
            sessionManager.sendTo(sessionId, json);
            log.info("[CapabilityRouter] Calling {} | callId={} | sessionId={}", capability, callId, sessionId);
        } catch (Exception e) {
            pendingCalls.remove(callId);
            future.completeExceptionally(e);
            log.error("[CapabilityRouter] Failed to serialize message: {}", e.getMessage());
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

    private String extractFilePath(Map<String, Object> params) {
        Object path = params.get("filePath");
        if (path != null) {
            return path.toString();
        }
        path = params.get("path");
        return path != null ? path.toString() : null;
    }
}