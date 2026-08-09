package com.mcp.tools.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具评分 —— CapabilityResolver 的排序依据。
 * 综合 Skill 成功率、Failure 历史、Priority、Latency 等多维度。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolScore {

    private ToolDefinition tool;

    @Builder.Default
    private double baseScore = 100.0;

    @Builder.Default
    private double skillBonus = 0.0;

    @Builder.Default
    private double failurePenalty = 0.0;

    @Builder.Default
    private double priorityBonus = 0.0;

    @Builder.Default
    private double latencyPenalty = 0.0;

    private String skillName;
    private String failureWarning;

    /**
     * 综合得分 = baseScore + skillBonus - failurePenalty + priorityBonus - latencyPenalty
     */
    public double getCompositeScore() {
        return baseScore + skillBonus - failurePenalty + priorityBonus - latencyPenalty;
    }

    public String getToolName() {
        return tool != null ? tool.getName() : "unknown";
    }

    @Override
    public String toString() {
        return String.format("%s: %.1f (base=%.0f, skill=+%.0f, failure=-%.0f, priority=+%.0f, latency=-%.0f)",
                getToolName(), getCompositeScore(),
                baseScore, skillBonus, failurePenalty, priorityBonus, latencyPenalty);
    }
}