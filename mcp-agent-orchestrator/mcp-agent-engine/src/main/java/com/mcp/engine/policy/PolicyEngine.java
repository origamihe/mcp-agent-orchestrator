package com.mcp.engine.policy;

import com.mcp.common.identity.MemoryIdentity;
import com.mcp.engine.execution.ExecutionPlan;
import com.mcp.engine.trace.SessionTrace;
import com.mcp.engine.trace.SessionTraceHolder;
import com.mcp.tools.model.ToolCategory;
import com.mcp.tools.model.ToolDefinition;
import com.mcp.tools.pipeline.ToolPolicyChecker;
import com.mcp.tools.registry.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 策略引擎 — P1 核心组件，统一 Tool 调用前的安全评估。
 *
 * 职责：
 * 1. 评估 Tool 调用是否允许（ALLOW / DENY / REQUIRE_CONFIRMATION）
 * 2. 基于 identity、agent、capability、resource、context 五要素做决策
 * 3. 替代分散在 CapabilityRouter / SandboxPolicy / PermissionAspect 中的安全逻辑
 *
 * 设计原则：
 * - 策略决策从"Tool Router 的一部分"升级为"Runtime 的控制平面"
 * - 支持可插拔的 PolicyChecker 扩展
 * - 决策结果可审计（通过 SessionTrace 记录 POLICY_DECISION 事件）
 */
@Slf4j
@Component
public class PolicyEngine implements ToolPolicyChecker {

    private final Map<String, PolicyChecker> checkers = new ConcurrentHashMap<>();
    private final ToolRegistry toolRegistry;

    public PolicyEngine(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        registerChecker("sandbox", new SandboxPolicyChecker());
        registerChecker("permission", new PermissionPolicyChecker());
    }

    public void registerChecker(String name, PolicyChecker checker) {
        checkers.put(name, checker);
    }

    public PolicyDecision evaluate(MemoryIdentity identity, String agentId, ToolDefinition capability,
                                    String resource, ExecutionPlan context) {
        PolicyContext ctx = new PolicyContext(identity, agentId, capability, resource, context);

        for (PolicyChecker checker : checkers.values()) {
            PolicyDecision decision = checker.check(ctx);
            if (decision == PolicyDecision.DENY) {
                log.warn("[PolicyEngine] DENY: checker={}, capability={}, identity={}",
                        checker.getClass().getSimpleName(), capability != null ? capability.getName() : "null", identity.userId());
                recordPolicyTrace(capability, decision, checker.getClass().getSimpleName());
                return PolicyDecision.DENY;
            }
            if (decision == PolicyDecision.REQUIRE_CONFIRMATION) {
                log.info("[PolicyEngine] REQUIRE_CONFIRMATION: checker={}, capability={}",
                        checker.getClass().getSimpleName(), capability != null ? capability.getName() : "null");
                recordPolicyTrace(capability, decision, checker.getClass().getSimpleName());
                return PolicyDecision.REQUIRE_CONFIRMATION;
            }
        }

        recordPolicyTrace(capability, PolicyDecision.ALLOW, "all_checkers");
        return PolicyDecision.ALLOW;
    }

    private void recordPolicyTrace(ToolDefinition capability, PolicyDecision decision, String checker) {
        SessionTrace trace = SessionTraceHolder.currentOrNull();
        if (trace != null) {
            trace.recordPolicyDecision(
                    capability != null ? capability.getName() : "unknown",
                    decision.name(),
                    "checker=" + checker
            );
        }
    }

    public enum PolicyDecision {
        ALLOW,
        DENY,
        REQUIRE_CONFIRMATION
    }

    public interface PolicyChecker {
        PolicyDecision check(PolicyContext ctx);
    }

    public record PolicyContext(
            MemoryIdentity identity,
            String agentId,
            ToolDefinition capability,
            String resource,
            ExecutionPlan executionPlan
    ) {}

    static class SandboxPolicyChecker implements PolicyChecker {
        private static final Set<ToolCategory> SANDBOX_REQUIRED = EnumSet.of(
                ToolCategory.WRITE, ToolCategory.SYSTEM, ToolCategory.CODE, ToolCategory.FILE
        );

        @Override
        public PolicyDecision check(PolicyContext ctx) {
            if (ctx.capability == null) return PolicyDecision.ALLOW;
            if (ctx.capability.getCategory() != null
                    && SANDBOX_REQUIRED.contains(ctx.capability.getCategory())) {
                if (ctx.executionPlan == null) {
                    return PolicyDecision.DENY;
                }
                ExecutionPlan.ToolPolicy tp = ctx.executionPlan.toolPolicy();
                if (!tp.allowFileWrite() && !tp.allowCodeExecution()) {
                    return PolicyDecision.DENY;
                }
                if (tp.allowFileWrite() || tp.allowCodeExecution()) {
                    return PolicyDecision.REQUIRE_CONFIRMATION;
                }
            }
            return PolicyDecision.ALLOW;
        }
    }

    static class PermissionPolicyChecker implements PolicyChecker {
        @Override
        public PolicyDecision check(PolicyContext ctx) {
            if (ctx.capability == null) return PolicyDecision.ALLOW;
            if (ctx.identity == null || ctx.identity.userId() == null) {
                return PolicyDecision.DENY;
            }
            return PolicyDecision.ALLOW;
        }
    }

    @Override
    public ToolPolicyChecker.Decision check(String toolName, String pipelineId, String stepId) {
        log.warn("[PolicyEngine] Policy check called without ExecutionContext - DENY: tool={}, pipeline={}, step={}",
                toolName, pipelineId, stepId);
        return ToolPolicyChecker.Decision.DENY;
    }

    @Override
    public ToolPolicyChecker.Decision check(String toolName, String pipelineId, String stepId,
                                             Map<String, Object> executionContext) {
        log.debug("[PolicyEngine] Pipeline policy check: tool={}, pipeline={}, step={}, contextKeys={}",
                toolName, pipelineId, stepId, executionContext != null ? executionContext.keySet() : "null");

        if (executionContext == null || executionContext.isEmpty()) {
            log.warn("[PolicyEngine] No ExecutionContext provided - DENY: tool={}, pipeline={}, step={}",
                    toolName, pipelineId, stepId);
            return ToolPolicyChecker.Decision.DENY;
        }

        ExecutionPlan plan = (ExecutionPlan) executionContext.get("executionPlan");
        if (plan == null) {
            log.warn("[PolicyEngine] No ExecutionPlan in context - DENY: tool={}, pipeline={}, step={}",
                    toolName, pipelineId, stepId);
            return ToolPolicyChecker.Decision.DENY;
        }

        PolicyDecision decision = evaluateWithPlan(plan, toolName);

        ToolPolicyChecker.Decision result = switch (decision) {
            case DENY -> ToolPolicyChecker.Decision.DENY;
            case REQUIRE_CONFIRMATION -> ToolPolicyChecker.Decision.REQUIRE_CONFIRMATION;
            default -> ToolPolicyChecker.Decision.ALLOW;
        };

        SessionTrace trace = SessionTraceHolder.currentOrNull();
        if (trace != null) {
            trace.recordPolicyDecision(toolName, result.name(),
                    "pipeline=" + pipelineId + ", step=" + stepId);
        }

        return result;
    }

    private PolicyDecision evaluateWithPlan(ExecutionPlan plan, String toolName) {
        ToolDefinition toolDef = toolRegistry.getTool(toolName).block();
        return evaluateWithPlan(plan, toolDef);
    }

    private PolicyDecision evaluateWithPlan(ExecutionPlan plan, ToolDefinition toolDef) {
        ExecutionPlan.ToolPolicy tp = plan.toolPolicy();
        if (tp == null) {
            log.warn("[PolicyEngine] No ToolPolicy defined - DENY: tool={}", toolDef != null ? toolDef.getName() : "null");
            return PolicyDecision.DENY;
        }

        ToolCategory category = toolDef != null ? toolDef.getCategory() : null;
        if (category == null) {
            log.warn("[PolicyEngine] No ToolCategory defined for tool '{}' - DENY",
                    toolDef != null ? toolDef.getName() : "null");
            return PolicyDecision.DENY;
        }

        return switch (category) {
            case SEARCH, WEB -> {
                if (!tp.allowSearch()) {
                    log.warn("[PolicyEngine] DENY: {} tool '{}' blocked by ToolPolicy.allowSearch=false",
                            category, toolDef.getName());
                    yield PolicyDecision.DENY;
                }
                yield PolicyDecision.ALLOW;
            }
            case WRITE, CODE, SYSTEM, FILE -> {
                if (!tp.allowFileWrite()) {
                    log.warn("[PolicyEngine] DENY: {} tool '{}' blocked by ToolPolicy.allowFileWrite=false",
                            category, toolDef.getName());
                    yield PolicyDecision.DENY;
                }
                if (!tp.allowCodeExecution()) {
                    log.warn("[PolicyEngine] DENY: {} tool '{}' blocked by ToolPolicy.allowCodeExecution=false",
                            category, toolDef.getName());
                    yield PolicyDecision.DENY;
                }
                yield PolicyDecision.ALLOW;
            }
            case READ -> {
                if (!tp.allowFileRead()) {
                    log.warn("[PolicyEngine] DENY: {} tool '{}' blocked by ToolPolicy.allowFileRead=false",
                            category, toolDef.getName());
                    yield PolicyDecision.DENY;
                }
                yield PolicyDecision.ALLOW;
            }
            default -> PolicyDecision.ALLOW;
        };
    }
}