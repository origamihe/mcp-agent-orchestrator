package com.mcp.common.evolution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 策略进化上下文 — 追踪 Agent 执行策略的进化状态。
 *
 * <p>核心设计：
 * <ul>
 *   <li>追踪执行指标的趋势（成功/失败/延迟/评分）</li>
 *   <li>记录策略变更历史</li>
 *   <li>生成进化建议</li>
 *   <li>支持策略版本回滚</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyEvolutionContext {

    public enum StrategyPhase {
        OBSERVING,      // 观察期：收集数据
        STABILIZING,    // 稳定期：策略稳定
        EVOLVING,       // 进化中：调整策略
        REGRESSING,     // 退化中：性能下降
        CONVERGING      // 收敛期：策略已收敛
    }

    public enum StrategyDimension {
        AGENT_ROUTING,          // Agent 路由策略
        TOOL_SELECTION,         // 工具选择策略
        PROMPT_STRUCTURE,       // Prompt 结构策略
        CONTEXT_LOADING,        // 上下文加载策略
        REFLECTION_TRIGGER      // 反思触发策略
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceSnapshot {
        @Builder.Default
        private Instant timestamp = Instant.now();
        private int totalExecutions;
        private int successCount;
        private int failureCount;
        private double avgScore;
        private double avgLatencyMs;
        private double toolCallSuccessRate;
        private double learningEffectiveness;
        private double skillMatchRate;
        private double failureAvoidanceRate;

        public double successRate() {
            return totalExecutions > 0 ? (double) successCount / totalExecutions : 0.0;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StrategyChange {
        @Builder.Default
        private Instant timestamp = Instant.now();
        private StrategyDimension dimension;
        private String changeDescription;
        private String previousValue;
        private String newValue;
        private String reason;
        private double expectedImprovement;
        private double actualImprovement;
        private boolean effective;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvolutionRecommendation {
        private StrategyDimension dimension;
        private String recommendation;
        private double confidence;
        private String evidence;
        private Priority priority;

        public enum Priority { HIGH, MEDIUM, LOW }
    }

    private String agentId;
    @Builder.Default
    private StrategyPhase phase = StrategyPhase.OBSERVING;
    @Builder.Default
    private List<PerformanceSnapshot> performanceHistory = new ArrayList<>();
    @Builder.Default
    private List<StrategyChange> strategyChanges = new ArrayList<>();
    @Builder.Default
    private List<EvolutionRecommendation> pendingRecommendations = new ArrayList<>();
    private int currentStrategyVersion;
    private int executionCount;
    private int consecutiveSuccesses;
    private int consecutiveFailures;
    private double trendSlope;

    @Builder.Default
    private Map<StrategyDimension, Double> dimensionWeights = Map.of(
            StrategyDimension.AGENT_ROUTING, 0.3,
            StrategyDimension.TOOL_SELECTION, 0.25,
            StrategyDimension.PROMPT_STRUCTURE, 0.2,
            StrategyDimension.CONTEXT_LOADING, 0.15,
            StrategyDimension.REFLECTION_TRIGGER, 0.1
    );

    public boolean isReadyForEvolution() {
        return executionCount >= 10 && phase != StrategyPhase.CONVERGING;
    }

    public boolean isDegrading() {
        return phase == StrategyPhase.REGRESSING || consecutiveFailures >= 3;
    }

    public String toPromptFragment() {
        StringBuilder sb = new StringBuilder();
        sb.append("【策略进化状态】\n");
        sb.append("当前阶段: ").append(phase.name()).append("\n");
        sb.append("策略版本: v").append(currentStrategyVersion).append("\n");
        sb.append("总执行次数: ").append(executionCount).append("\n");

        if (!performanceHistory.isEmpty()) {
            PerformanceSnapshot latest = performanceHistory.get(performanceHistory.size() - 1);
            sb.append("最近成功率: ").append(String.format("%.1f%%", latest.successRate() * 100)).append("\n");
            sb.append("平均评分: ").append(String.format("%.1f", latest.avgScore)).append("\n");
            sb.append("工具调用成功率: ").append(String.format("%.1f%%", latest.toolCallSuccessRate * 100)).append("\n");
        }

        if (!pendingRecommendations.isEmpty()) {
            sb.append("待应用建议: ").append(pendingRecommendations.size()).append(" 条\n");
            for (EvolutionRecommendation rec : pendingRecommendations) {
                sb.append("  - [").append(rec.priority).append("] ").append(rec.recommendation).append("\n");
            }
        }

        return sb.toString();
    }
}