package com.mcp.engine.context;

import com.mcp.engine.execution.ExecutionPlan;

import java.time.Duration;

/**
 * 上下文预算 — P1 升级版，替代仅基于 Token 的 TokenBudget。
 *
 * 新增维度：
 * - maxFiles: 最大文件数
 * - maxBytes: 最大字节数（防止大文件 OOM）
 * - maxLines: 最大行数（防止单文件过大）
 * - timeout: 上下文构建超时
 *
 * 设计原则：
 * - 多维预算，防止单一维度成为瓶颈
 * - 与 TokenBudget 保持兼容（通过 toTokenBudget() 转换）
 * - 支持按 PlanType 和 ModelContextWindow 分配
 */
public record ContextBudget(
        int maxTokens,
        int maxFiles,
        int maxBytes,
        int maxLines,
        Duration timeout
) {
    public static final ContextBudget DEFAULT = new ContextBudget(
            8000, 20, 10 * 1024 * 1024, 10000, Duration.ofSeconds(10)
    );

    public static final ContextBudget SMALL = new ContextBudget(
            4096, 10, 5 * 1024 * 1024, 5000, Duration.ofSeconds(5)
    );

    public static final ContextBudget LARGE = new ContextBudget(
            32768, 50, 50 * 1024 * 1024, 50000, Duration.ofSeconds(30)
    );

    public static ContextBudget forModel(int modelContextWindow) {
        if (modelContextWindow <= 32000) {
            return SMALL;
        }
        if (modelContextWindow <= 64000) {
            return DEFAULT;
        }
        return LARGE;
    }

    /**
     * 从 ExecutionPlan 创建 ContextBudget。
     * 使用 MemoryPolicy.maxMemoryTokens 作为 maxTokens，
     * 使用 TimeoutPolicy.executionTimeout 作为 timeout。
     */
    public static ContextBudget fromExecutionPlan(ExecutionPlan plan) {
        if (plan == null) {
            return DEFAULT;
        }
        int tokens = plan.memoryPolicy().maxMemoryTokens();
        Duration timeout = plan.timeoutPolicy().executionTimeout();
        return new ContextBudget(
                tokens > 0 ? tokens : DEFAULT.maxTokens,
                DEFAULT.maxFiles,
                DEFAULT.maxBytes,
                DEFAULT.maxLines,
                timeout != null ? timeout : DEFAULT.timeout
        );
    }

    public TokenBudget toTokenBudget() {
        return TokenBudget.builder()
                .totalBudget(maxTokens)
                .build();
    }

    public boolean canFit(int additionalTokens, int additionalFiles, long additionalBytes) {
        return additionalTokens <= maxTokens
                && additionalFiles <= maxFiles
                && additionalBytes <= maxBytes;
    }
}