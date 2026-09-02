package com.mcp.gateway.host.capability;

import com.mcp.common.tool.ToolRiskLevel;
import com.mcp.tools.sandbox.SandboxPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Capability 审计日志 — 结构化记录所有 capability 调用。
 *
 * 记录字段：
 * - timestamp: 调用时间
 * - sessionId: 调用者会话
 * - capability: 能力名称
 * - riskLevel: 风险等级
 * - decision: 沙箱决策
 * - verdict: ALLOWED / BLOCKED / REJECTED
 * - params: 调用参数（脱敏）
 */
@Slf4j
@Component
public class CapabilityAuditLog {

    public void record(String sessionId, String capability, ToolRiskLevel riskLevel,
                       SandboxPolicy.Decision decision, String verdict, Map<String, Object> params) {
        Map<String, Object> auditEntry = new LinkedHashMap<>();
        auditEntry.put("timestamp", Instant.now().toString());
        auditEntry.put("sessionId", sessionId);
        auditEntry.put("capability", capability);
        auditEntry.put("riskLevel", riskLevel != null ? riskLevel.name() : "UNKNOWN");
        auditEntry.put("decision", decision != null ? decision.name() : "NONE");
        auditEntry.put("verdict", verdict);
        auditEntry.put("paramKeys", params != null ? params.keySet() : "none");

        log.info("[AUDIT] capability_call | {}", auditEntry);
    }

    public void recordBlocked(String sessionId, String capability, String reason) {
        Map<String, Object> auditEntry = new LinkedHashMap<>();
        auditEntry.put("timestamp", Instant.now().toString());
        auditEntry.put("sessionId", sessionId);
        auditEntry.put("capability", capability);
        auditEntry.put("verdict", "BLOCKED");
        auditEntry.put("reason", reason);

        log.warn("[AUDIT] capability_blocked | {}", auditEntry);
    }
}