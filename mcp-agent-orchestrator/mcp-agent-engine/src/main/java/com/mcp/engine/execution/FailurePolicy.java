package com.mcp.engine.execution;

/**
 * 失败策略 — P1 核心组件，统一决定 Pipeline/Agent 失败后的恢复行为。
 *
 * 设计原则：
 * - 失败策略不能由各执行路径自行决定
 * - 必须由统一的 FailurePolicy 评估后给出决策
 * - 决策结果可审计
 */
public final class FailurePolicy {

    private FailurePolicy() {}

    public enum Decision {
        RETRY,
        FALLBACK,
        ABORT,
        ASK_USER
    }

    public enum FailureType {
        POLICY_DENIED,
        TOOL_NOT_FOUND,
        TOOL_INVALID_ARGUMENT,
        TOOL_EXECUTION_ERROR,
        TIMEOUT,
        MODEL_ERROR,
        CONTEXT_ERROR,
        PIPELINE_ERROR,
        CANCELLATION,
        INTERNAL_ERROR
    }

    /**
     * 评估失败，决定恢复策略。
     *
     * @param failureType   失败类型
     * @param errorMessage  错误信息
     * @param plan          执行计划（用于上下文判断）
     * @return 恢复决策
     */
    public static Decision evaluate(FailureType failureType, String errorMessage, ExecutionPlan plan) {
        return switch (failureType) {
            case POLICY_DENIED -> Decision.ABORT;
            case TOOL_NOT_FOUND -> Decision.ABORT;
            case TOOL_INVALID_ARGUMENT -> Decision.ABORT;
            case TOOL_EXECUTION_ERROR -> {
                if (plan.toolPolicy().allowSearch() || plan.mode() == ExecutionPlan.ExecutionMode.PIPELINE) {
                    yield Decision.FALLBACK;
                }
                yield Decision.ABORT;
            }
            case TIMEOUT -> Decision.RETRY;
            case MODEL_ERROR -> Decision.RETRY;
            case CONTEXT_ERROR -> Decision.ABORT;
            case PIPELINE_ERROR -> Decision.FALLBACK;
            case CANCELLATION -> Decision.ABORT;
            case INTERNAL_ERROR -> Decision.ABORT;
        };
    }

    /**
     * 从错误信息中推断失败类型。
     */
    public static FailureType classifyFailure(Throwable error) {
        if (error == null) return FailureType.INTERNAL_ERROR;
        String msg = error.getMessage() != null ? error.getMessage().toLowerCase() : "";

        if (msg.contains("denied") || msg.contains("policy")) return FailureType.POLICY_DENIED;
        if (msg.contains("timeout") || msg.contains("timed out")) return FailureType.TIMEOUT;
        if (msg.contains("not found") || msg.contains("404")) return FailureType.TOOL_NOT_FOUND;
        if (msg.contains("invalid") || msg.contains("argument")) return FailureType.TOOL_INVALID_ARGUMENT;
        if (msg.contains("model") || msg.contains("llm") || msg.contains("ai")) return FailureType.MODEL_ERROR;
        if (msg.contains("context") || msg.contains("token")) return FailureType.CONTEXT_ERROR;
        if (msg.contains("pipeline")) return FailureType.PIPELINE_ERROR;
        if (msg.contains("cancel")) return FailureType.CANCELLATION;

        return FailureType.TOOL_EXECUTION_ERROR;
    }
}