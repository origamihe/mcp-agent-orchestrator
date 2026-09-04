package com.mcp.tools.pipeline;

import java.util.Map;

/**
 * 工具策略检查器接口 — 用于 Pipeline 执行前的安全检查。
 *
 * 由 mcp-agent-engine 的 PolicyEngine 实现，通过 mcp-tools 包暴露此接口
 * 避免跨模块循环依赖。
 *
 * 返回值：
 * - ALLOW — 允许执行
 * - DENY — 拒绝执行
 * - REQUIRE_CONFIRMATION — 需要用户确认
 */
public interface ToolPolicyChecker {

    enum Decision {
        ALLOW,
        DENY,
        REQUIRE_CONFIRMATION
    }

    Decision check(String toolName, String pipelineId, String stepId);

    /**
     * 带执行上下文的策略检查 — Pipeline 执行时使用，可传递 ExecutionPlan 等上下文。
     * 默认委托给无上下文版本，子类可覆盖以使用上下文做更细粒度的策略评估。
     */
    default Decision check(String toolName, String pipelineId, String stepId, Map<String, Object> executionContext) {
        return check(toolName, pipelineId, stepId);
    }
}