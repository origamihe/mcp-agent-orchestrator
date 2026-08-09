package com.mcp.engine.reflection;

import com.mcp.core.domain.memory.FailureEntity;
import com.mcp.core.domain.memory.SkillEntity;
import com.mcp.tools.model.ToolCapability;
import com.mcp.tools.model.ToolDefinition;
import com.mcp.tools.model.ToolQuery;
import com.mcp.tools.model.ToolScore;
import com.mcp.tools.model.ToolStats;
import com.mcp.tools.registry.CapabilityResolver;
import com.mcp.tools.registry.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 增强版 CapabilityResolver —— 综合 Skill 成功率、Failure 历史、Priority、Latency 进行评分排序。
 * <p>
 * 评分公式：
 * compositeScore = 100 + skillBonus(0~30) - failurePenalty(0~40) + priorityBonus(0~10) - latencyPenalty(0~20)
 * <p>
 * Planner 应使用 resolveRanked() 获取按评分排序的工具列表，优先使用高分工具。
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class ScoringCapabilityResolver implements CapabilityResolver {

    private final ToolRegistry registry;
    private final SkillLibraryService skillLibraryService;
    private final FailureLibraryService failureLibraryService;

    private static final double SKILL_BONUS_MAX = 30.0;
    private static final double FAILURE_PENALTY_MAX = 40.0;
    private static final double PRIORITY_BONUS_MAX = 10.0;
    private static final double LATENCY_PENALTY_MAX = 20.0;

    private static final long LATENCY_WARN_MS = 5000L;
    private static final long LATENCY_SEVERE_MS = 15000L;

    @Override
    public List<ToolDefinition> resolve(ToolQuery query) {
        return registry.query(query);
    }

    @Override
    public List<ToolScore> resolveRanked(ToolQuery query) {
        List<ToolDefinition> tools = resolve(query);
        if (tools.isEmpty()) {
            return List.of();
        }

        List<SkillEntity> highSuccessSkills = skillLibraryService.getHighSuccessSkills();
        List<FailureEntity> unresolvedFailures = failureLibraryService.getUnresolvedFailures();

        List<ToolScore> scores = tools.stream()
                .map(tool -> scoreTool(tool, highSuccessSkills, unresolvedFailures))
                .sorted(Comparator.comparingDouble(ToolScore::getCompositeScore).reversed())
                .collect(Collectors.toList());

        if (log.isDebugEnabled()) {
            log.debug("[ScoringCapabilityResolver] Ranked {} tools for query: owner={}, capability={}, category={}",
                    scores.size(), query.getOwner(), query.getCapability(), query.getCategory());
            for (int i = 0; i < scores.size(); i++) {
                log.debug("  [{}] {}", i + 1, scores.get(i));
            }
        }

        return scores;
    }

    private ToolScore scoreTool(ToolDefinition tool,
                                List<SkillEntity> highSuccessSkills,
                                List<FailureEntity> unresolvedFailures) {
        ToolScore.ToolScoreBuilder builder = ToolScore.builder().tool(tool);

        double skillBonus = calculateSkillBonus(tool, highSuccessSkills);
        double failurePenalty = calculateFailurePenalty(tool, unresolvedFailures);
        double priorityBonus = Math.min(tool.getPriority() * 2.0, PRIORITY_BONUS_MAX);
        double latencyPenalty = calculateLatencyPenalty(tool);

        builder.skillBonus(skillBonus)
                .failurePenalty(failurePenalty)
                .priorityBonus(priorityBonus)
                .latencyPenalty(latencyPenalty);

        return builder.build();
    }

    private double calculateSkillBonus(ToolDefinition tool, List<SkillEntity> highSuccessSkills) {
        if (highSuccessSkills.isEmpty() || tool.getName() == null) {
            return 0.0;
        }

        for (SkillEntity skill : highSuccessSkills) {
            if (skill.getSteps() != null && skill.getSteps().toLowerCase().contains(tool.getName().toLowerCase())) {
                double rate = skill.getSuccessRate();
                if (rate >= 90.0) {
                    log.debug("[Scoring] {} ← Skill '{}' bonus: +{} (success={}%)",
                            tool.getName(), skill.getName(), SKILL_BONUS_MAX, String.format("%.0f", rate));
                    return SKILL_BONUS_MAX;
                } else if (rate >= 80.0) {
                    double bonus = SKILL_BONUS_MAX * 0.7;
                    log.debug("[Scoring] {} ← Skill '{}' bonus: +{} (success={}%)",
                            tool.getName(), skill.getName(), String.format("%.0f", bonus), String.format("%.0f", rate));
                    return bonus;
                } else if (rate >= 70.0) {
                    double bonus = SKILL_BONUS_MAX * 0.4;
                    log.debug("[Scoring] {} ← Skill '{}' bonus: +{} (success={}%)",
                            tool.getName(), skill.getName(), String.format("%.0f", bonus), String.format("%.0f", rate));
                    return bonus;
                }
            }
        }

        return 0.0;
    }

    private double calculateFailurePenalty(ToolDefinition tool, List<FailureEntity> unresolvedFailures) {
        if (unresolvedFailures.isEmpty() || tool.getName() == null) {
            return 0.0;
        }

        for (FailureEntity failure : unresolvedFailures) {
            if (failure.getCorrectApproach() != null
                    && failure.getCorrectApproach().toLowerCase().contains(tool.getName().toLowerCase())) {
                int occurrences = failure.getOccurrenceCount();
                double penalty = Math.min(occurrences * 10.0, FAILURE_PENALTY_MAX);
                log.debug("[Scoring] {} ← Failure '{}' penalty: -{} (occurrences={})",
                        tool.getName(), failure.getTaskPattern(), String.format("%.0f", penalty), occurrences);
                return penalty;
            }

            if (failure.getErrorSignature() != null
                    && failure.getErrorSignature().toLowerCase().contains(tool.getName().toLowerCase())) {
                int occurrences = failure.getOccurrenceCount();
                double penalty = Math.min(occurrences * 8.0, FAILURE_PENALTY_MAX * 0.8);
                log.debug("[Scoring] {} ← Failure error-match penalty: -{} (occurrences={})",
                        tool.getName(), String.format("%.0f", penalty), occurrences);
                return penalty;
            }
        }

        return 0.0;
    }

    private double calculateLatencyPenalty(ToolDefinition tool) {
        ToolStats stats = registry.getToolStats(tool.getName());
        if (stats == null || stats.avgDurationMs() <= 0) {
            return 0.0;
        }

        long avgMs = stats.avgDurationMs();
        if (avgMs >= LATENCY_SEVERE_MS) {
            return LATENCY_PENALTY_MAX;
        } else if (avgMs >= LATENCY_WARN_MS) {
            double ratio = (double) (avgMs - LATENCY_WARN_MS) / (LATENCY_SEVERE_MS - LATENCY_WARN_MS);
            return ratio * LATENCY_PENALTY_MAX;
        }
        return 0.0;
    }
}