package com.mcp.engine.evolution;

import com.mcp.common.evolution.StrategyEvolutionContext;
import com.mcp.common.evolution.StrategyEvolutionContext.EvolutionRecommendation;
import com.mcp.common.evolution.StrategyEvolutionContext.PerformanceSnapshot;
import com.mcp.common.evolution.StrategyEvolutionContext.StrategyChange;
import com.mcp.common.evolution.StrategyEvolutionContext.StrategyDimension;
import com.mcp.common.evolution.StrategyEvolutionContext.StrategyPhase;
import com.mcp.core.domain.memory.FailureEntity;
import com.mcp.core.domain.memory.SkillEntity;
import com.mcp.engine.reflection.FailureLibraryService;
import com.mcp.engine.reflection.SkillLibraryService;
import com.mcp.llm.client.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 策略进化引擎 — 追踪执行指标、分析趋势、自动进化 Agent 策略。
 *
 * <p>核心职责：
 * <ol>
 *   <li>收集每次执行的性能快照</li>
 *   <li>分析趋势，检测性能退化</li>
 *   <li>生成策略进化建议</li>
 *   <li>管理策略变更历史</li>
 *   <li>触发策略回滚</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyEvolutionManager {

    private final LlmClient llmClient;
    private final SkillLibraryService skillLibraryService;
    private final FailureLibraryService failureLibraryService;

    private final Map<String, StrategyEvolutionContext> evolutionMap = new ConcurrentHashMap<>();

    private static final int MIN_EXECUTIONS_FOR_EVOLUTION = 10;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final double DEGRADATION_THRESHOLD = -0.05;
    private static final double IMPROVEMENT_THRESHOLD = 0.05;
    private static final int MAX_SNAPSHOT_HISTORY = 50;

    private static final String EVOLUTION_ANALYSIS_PROMPT = """
            你是一个策略进化分析师。你的任务是分析 Agent 的执行数据，给出策略进化建议。

            【当前性能数据】
            成功率: %.1f%%
            平均评分: %.1f/100
            平均延迟: %.0fms
            工具调用成功率: %.1f%%
            学习效果: %.1f%%
            技能匹配率: %.1f%%
            失败规避率: %.1f%%

            【趋势分析】
            趋势斜率: %.4f（正值=改善，负值=退化）
            连续成功: %d 次
            连续失败: %d 次

            【当前策略维度】
            %s

            【分析要求】
            1. 判断当前策略阶段（OBSERVING/STABILIZING/EVOLVING/REGRESSING/CONVERGING）
            2. 对每个策略维度给出改进建议（如有必要）
            3. 建议格式：维度 + 建议 + 置信度(0-1) + 证据

            输出 JSON：
            {
                "phase": "当前阶段",
                "recommendations": [
                    {
                        "dimension": "AGENT_ROUTING|TOOL_SELECTION|PROMPT_STRUCTURE|CONTEXT_LOADING|REFLECTION_TRIGGER",
                        "recommendation": "具体建议",
                        "confidence": 0.8,
                        "evidence": "支撑证据",
                        "priority": "HIGH|MEDIUM|LOW"
                    }
                ],
                "summary": "总体分析摘要"
            }
            """;

    public StrategyEvolutionContext getOrCreate(String agentId) {
        return evolutionMap.computeIfAbsent(agentId, id -> StrategyEvolutionContext.builder()
                .agentId(id)
                .phase(StrategyPhase.OBSERVING)
                .currentStrategyVersion(1)
                .build());
    }

    public StrategyEvolutionContext getEvolution(String agentId) {
        return evolutionMap.get(agentId);
    }

    public Map<String, StrategyEvolutionContext> getAllEvolutions() {
        return new HashMap<>(evolutionMap);
    }

    /**
     * 记录一次执行结果。
     */
    public void recordExecution(String agentId, boolean success, double score,
                                 double latencyMs, boolean toolCallSuccess,
                                 boolean skillMatched, boolean failureAvoided) {
        StrategyEvolutionContext ctx = getOrCreate(agentId);
        ctx.setExecutionCount(ctx.getExecutionCount() + 1);

        if (success) {
            ctx.setConsecutiveSuccesses(ctx.getConsecutiveSuccesses() + 1);
            ctx.setConsecutiveFailures(0);
        } else {
            ctx.setConsecutiveFailures(ctx.getConsecutiveFailures() + 1);
            ctx.setConsecutiveSuccesses(0);
        }

        PerformanceSnapshot snapshot = buildSnapshot(ctx, success, score, latencyMs,
                toolCallSuccess, skillMatched, failureAvoided);

        List<PerformanceSnapshot> history = ctx.getPerformanceHistory();
        history.add(snapshot);
        if (history.size() > MAX_SNAPSHOT_HISTORY) {
            history.remove(0);
        }

        updatePhase(ctx);
        computeTrend(ctx);

        log.debug("[StrategyEvolution] agent={}, execution={}, success={}, score={}, phase={}, trend={}",
                agentId, ctx.getExecutionCount(), success, score, ctx.getPhase(),
                String.format("%.4f", ctx.getTrendSlope()));
    }

    /**
     * 批量记录执行结果。
     */
    public void recordExecution(String agentId, ExecutionRecord record) {
        recordExecution(agentId, record.success(), record.score(), record.latencyMs(),
                record.toolCallSuccess(), record.skillMatched(), record.failureAvoided());
    }

    /**
     * 分析并生成进化建议。
     */
    public Mono<List<EvolutionRecommendation>> analyzeAndRecommend(String agentId) {
        StrategyEvolutionContext ctx = getOrCreate(agentId);

        if (!ctx.isReadyForEvolution()) {
            log.debug("[StrategyEvolution] agent={} 数据不足，跳过进化分析 (executions={})",
                    agentId, ctx.getExecutionCount());
            return Mono.just(List.of());
        }

        return llmClient.generate(buildAnalysisPrompt(ctx))
                .map(this::parseRecommendations)
                .doOnNext(recommendations -> {
                    ctx.setPendingRecommendations(recommendations);
                    log.info("[StrategyEvolution] agent={} 生成 {} 条进化建议", agentId, recommendations.size());
                })
                .onErrorReturn(List.of());
    }

    /**
     * 应用一条进化建议。
     */
    public StrategyChange applyRecommendation(String agentId, EvolutionRecommendation recommendation) {
        StrategyEvolutionContext ctx = getOrCreate(agentId);

        StrategyChange change = StrategyChange.builder()
                .timestamp(Instant.now())
                .dimension(recommendation.getDimension())
                .changeDescription(recommendation.getRecommendation())
                .reason(recommendation.getEvidence())
                .expectedImprovement(recommendation.getConfidence())
                .build();

        ctx.getStrategyChanges().add(change);
        ctx.setCurrentStrategyVersion(ctx.getCurrentStrategyVersion() + 1);
        ctx.getPendingRecommendations().remove(recommendation);

        log.info("[StrategyEvolution] agent={} 应用进化建议: dimension={}, version=v{}",
                agentId, recommendation.getDimension(), ctx.getCurrentStrategyVersion());

        return change;
    }

    /**
     * 回滚到上一个策略版本。
     */
    public boolean rollback(String agentId) {
        StrategyEvolutionContext ctx = getOrCreate(agentId);

        if (ctx.getStrategyChanges().isEmpty()) {
            log.warn("[StrategyEvolution] agent={} 无历史变更可回滚", agentId);
            return false;
        }

        StrategyChange lastChange = ctx.getStrategyChanges().remove(ctx.getStrategyChanges().size() - 1);
        lastChange.setEffective(false);
        ctx.setCurrentStrategyVersion(ctx.getCurrentStrategyVersion() - 1);
        ctx.setPhase(StrategyPhase.OBSERVING);

        log.info("[StrategyEvolution] agent={} 回滚策略: dimension={}, newVersion=v{}",
                agentId, lastChange.getDimension(), ctx.getCurrentStrategyVersion());

        return true;
    }

    /**
     * 生成进化上下文 Prompt 片段。
     */
    public String buildEvolutionPrompt(String agentId) {
        StrategyEvolutionContext ctx = getOrCreate(agentId);
        return ctx.toPromptFragment();
    }

    /**
     * 计算进化健康度评分。
     */
    public double computeHealthScore(String agentId) {
        StrategyEvolutionContext ctx = getEvolution(agentId);
        if (ctx == null || ctx.getPerformanceHistory().isEmpty()) {
            return 0.5;
        }

        PerformanceSnapshot latest = ctx.getPerformanceHistory().get(ctx.getPerformanceHistory().size() - 1);
        double score = 0.0;

        score += latest.successRate() * 40;
        score += (latest.getAvgScore() / 100.0) * 20;
        score += latest.getToolCallSuccessRate() * 15;
        score += latest.getLearningEffectiveness() * 15;
        score += latest.getFailureAvoidanceRate() * 10;

        if (ctx.getTrendSlope() > 0) {
            score += 5;
        } else if (ctx.getTrendSlope() < DEGRADATION_THRESHOLD) {
            score -= 10;
        }

        return Math.max(0, Math.min(100, score));
    }

    public record ExecutionRecord(
            boolean success,
            double score,
            double latencyMs,
            boolean toolCallSuccess,
            boolean skillMatched,
            boolean failureAvoided
    ) {
        public static ExecutionRecord of(boolean success, double score, double latencyMs,
                                          boolean toolCallSuccess, boolean skillMatched,
                                          boolean failureAvoided) {
            return new ExecutionRecord(success, score, latencyMs, toolCallSuccess, skillMatched, failureAvoided);
        }
    }

    private PerformanceSnapshot buildSnapshot(StrategyEvolutionContext ctx, boolean success,
                                               double score, double latencyMs,
                                               boolean toolCallSuccess, boolean skillMatched,
                                               boolean failureAvoided) {
        List<PerformanceSnapshot> history = ctx.getPerformanceHistory();
        PerformanceSnapshot prev = history.isEmpty() ? null : history.get(history.size() - 1);

        double learningEffectiveness = computeLearningEffectiveness(ctx);
        double skillMatchRate = prev != null
                ? (prev.getSkillMatchRate() * 0.7 + (skillMatched ? 0.3 : 0.0))
                : (skillMatched ? 1.0 : 0.0);
        double failureAvoidanceRate = prev != null
                ? (prev.getFailureAvoidanceRate() * 0.7 + (failureAvoided ? 0.3 : 0.0))
                : (failureAvoided ? 1.0 : 0.0);

        return PerformanceSnapshot.builder()
                .timestamp(Instant.now())
                .totalExecutions(ctx.getExecutionCount())
                .successCount(success
                        ? (prev != null ? prev.getSuccessCount() + 1 : 1)
                        : (prev != null ? prev.getSuccessCount() : 0))
                .failureCount(!success
                        ? (prev != null ? prev.getFailureCount() + 1 : 1)
                        : (prev != null ? prev.getFailureCount() : 0))
                .avgScore(prev != null
                        ? (prev.getAvgScore() * 0.8 + score * 0.2)
                        : score)
                .avgLatencyMs(prev != null
                        ? (prev.getAvgLatencyMs() * 0.8 + latencyMs * 0.2)
                        : latencyMs)
                .toolCallSuccessRate(prev != null
                        ? (prev.getToolCallSuccessRate() * 0.8 + (toolCallSuccess ? 0.2 : 0.0))
                        : (toolCallSuccess ? 1.0 : 0.0))
                .learningEffectiveness(learningEffectiveness)
                .skillMatchRate(skillMatchRate)
                .failureAvoidanceRate(failureAvoidanceRate)
                .build();
    }

    private void updatePhase(StrategyEvolutionContext ctx) {
        if (ctx.getExecutionCount() < MIN_EXECUTIONS_FOR_EVOLUTION) {
            ctx.setPhase(StrategyPhase.OBSERVING);
            return;
        }

        if (ctx.getConsecutiveFailures() >= MAX_CONSECUTIVE_FAILURES) {
            ctx.setPhase(StrategyPhase.REGRESSING);
            return;
        }

        if (ctx.getTrendSlope() > IMPROVEMENT_THRESHOLD) {
            ctx.setPhase(StrategyPhase.EVOLVING);
        } else if (ctx.getTrendSlope() < DEGRADATION_THRESHOLD) {
            ctx.setPhase(StrategyPhase.REGRESSING);
        } else if (Math.abs(ctx.getTrendSlope()) <= 0.01
                && ctx.getConsecutiveSuccesses() >= 20) {
            ctx.setPhase(StrategyPhase.CONVERGING);
        } else {
            ctx.setPhase(StrategyPhase.STABILIZING);
        }
    }

    private void computeTrend(StrategyEvolutionContext ctx) {
        List<PerformanceSnapshot> history = ctx.getPerformanceHistory();
        if (history.size() < 3) {
            ctx.setTrendSlope(0.0);
            return;
        }

        int n = Math.min(history.size(), 10);
        List<PerformanceSnapshot> recent = history.subList(history.size() - n, history.size());

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < recent.size(); i++) {
            double x = i;
            double y = recent.get(i).successRate();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double denominator = n * sumX2 - sumX * sumX;
        ctx.setTrendSlope(denominator != 0 ? (n * sumXY - sumX * sumY) / denominator : 0.0);
    }

    private double computeLearningEffectiveness(StrategyEvolutionContext ctx) {
        List<SkillEntity> activeSkills = skillLibraryService.getHighSuccessSkills();
        List<FailureEntity> unresolvedFailures = failureLibraryService.getUnresolvedFailures();

        if (activeSkills.isEmpty() && unresolvedFailures.isEmpty()) {
            return 0.5;
        }

        double skillScore = activeSkills.isEmpty() ? 0.5
                : activeSkills.stream().mapToDouble(SkillEntity::getSuccessRate).average().orElse(0.5);

        double failureScore = unresolvedFailures.isEmpty() ? 1.0
                : 1.0 - Math.min(1.0, unresolvedFailures.size() / 10.0);

        return skillScore * 0.6 + failureScore * 0.4;
    }

    private String buildAnalysisPrompt(StrategyEvolutionContext ctx) {
        PerformanceSnapshot latest = ctx.getPerformanceHistory().get(
                ctx.getPerformanceHistory().size() - 1);

        StringBuilder dimensionWeights = new StringBuilder();
        ctx.getDimensionWeights().forEach((dim, weight) ->
                dimensionWeights.append("  - ").append(dim.name())
                        .append(": ").append(String.format("%.2f", weight)).append("\n"));

        return EVOLUTION_ANALYSIS_PROMPT.formatted(
                latest.successRate() * 100,
                latest.getAvgScore(),
                latest.getAvgLatencyMs(),
                latest.getToolCallSuccessRate() * 100,
                latest.getLearningEffectiveness() * 100,
                latest.getSkillMatchRate() * 100,
                latest.getFailureAvoidanceRate() * 100,
                ctx.getTrendSlope(),
                ctx.getConsecutiveSuccesses(),
                ctx.getConsecutiveFailures(),
                dimensionWeights.toString()
        );
    }

    @SuppressWarnings("unchecked")
    private List<EvolutionRecommendation> parseRecommendations(String raw) {
        try {
            String json = raw;
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);

            String phase = root.has("phase") ? root.get("phase").asText() : "OBSERVING";

            List<EvolutionRecommendation> recommendations = new ArrayList<>();
            if (root.has("recommendations")) {
                for (com.fasterxml.jackson.databind.JsonNode node : root.get("recommendations")) {
                    StrategyDimension dim = StrategyDimension.valueOf(
                            node.get("dimension").asText());
                    String rec = node.get("recommendation").asText();
                    double conf = node.has("confidence") ? node.get("confidence").asDouble() : 0.5;
                    String evidence = node.has("evidence") ? node.get("evidence").asText() : "";
                    EvolutionRecommendation.Priority priority = EvolutionRecommendation.Priority.valueOf(
                            node.has("priority") ? node.get("priority").asText() : "MEDIUM");

                    recommendations.add(EvolutionRecommendation.builder()
                            .dimension(dim)
                            .recommendation(rec)
                            .confidence(conf)
                            .evidence(evidence)
                            .priority(priority)
                            .build());
                }
            }
            return recommendations;
        } catch (Exception e) {
            log.warn("[StrategyEvolution] 解析进化建议失败: {}", e.getMessage());
            return List.of();
        }
    }
}