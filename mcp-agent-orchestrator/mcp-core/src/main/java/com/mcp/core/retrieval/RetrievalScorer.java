package com.mcp.core.retrieval;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 多维召回打分器 — 综合 BM25、关键词、时效性、重要性、类型权重。
 *
 * 打分维度：
 * <pre>
 * 1. BM25 语义相关性 (weight: 0.35)  — 基于 BM25 算法的文本匹配
 * 2. 关键词命中率    (weight: 0.25)  — 查询词在文档中的命中比例
 * 3. 时效性衰减      (weight: 0.15)  — 越新的记忆权重越高
 * 4. 重要性/热度     (weight: 0.15)  — 记忆本身的重要性评分
 * 5. 类型权重        (weight: 0.10)  — 偏好记忆 > 事实记忆 > 临时记忆
 * </pre>
 *
 * 总分数 = Σ (dimension_score × weight)，范围 [0, 1]。
 *
 * 设计原则：
 * - 多维打分比单一维度更鲁棒，避免"关键词匹配但完全不相关"的情况
 * - 权重可配置，不同场景可调整（如搜索场景提高 BM25 权重，偏好场景提高重要性权重）
 * - 时效性衰减使用指数衰减，而非线性衰减（更符合记忆遗忘曲线）
 */
public class RetrievalScorer {

    private final Bm25Scorer bm25Scorer;
    private final ScorerWeights weights;

    public RetrievalScorer(Bm25Scorer bm25Scorer) {
        this(bm25Scorer, ScorerWeights.defaults());
    }

    public RetrievalScorer(Bm25Scorer bm25Scorer, ScorerWeights weights) {
        this.bm25Scorer = bm25Scorer;
        this.weights = weights;
    }

    /**
     * 计算综合得分。
     */
    public ScoredMemory score(ScoredMemory candidate, String query) {
        double bm25 = normalizeBm25(bm25Scorer.score(candidate.content(), query));
        double keyword = computeKeywordHitRate(candidate.content(), query);
        double recency = computeRecencyDecay(candidate.createdAt());
        double importance = normalizeImportance(candidate.importance());
        double typeWeight = computeTypeWeight(candidate.memoryType());

        double composite = bm25 * weights.bm25()
                + keyword * weights.keyword()
                + recency * weights.recency()
                + importance * weights.importance()
                + typeWeight * weights.typeWeight();

        return candidate.withScore(Math.min(composite, 1.0))
                .withBreakdown(new ScoreBreakdown(bm25, keyword, recency, importance, typeWeight));
    }

    private double normalizeBm25(double rawScore) {
        return Math.tanh(rawScore / 5.0);
    }

    private double computeKeywordHitRate(String content, String query) {
        if (content == null || query == null || query.isBlank()) return 0;

        List<String> queryTokens = Bm25Scorer.tokenize(query);
        if (queryTokens.isEmpty()) return 0;

        String lowerContent = content.toLowerCase();
        long hits = queryTokens.stream()
                .filter(token -> lowerContent.contains(token))
                .count();

        return (double) hits / queryTokens.size();
    }

    private double computeRecencyDecay(Instant createdAt) {
        if (createdAt == null) return 0.1;

        long hoursSinceCreation = Duration.between(createdAt, Instant.now()).toHours();
        if (hoursSinceCreation < 0) hoursSinceCreation = 0;

        double halfLifeHours = 24 * 7; // 7 天半衰期
        return Math.exp(-Math.log(2) * hoursSinceCreation / halfLifeHours);
    }

    private double normalizeImportance(double importance) {
        return Math.min(importance / 100.0, 1.0);
    }

    private double computeTypeWeight(String memoryType) {
        if (memoryType == null) return 0.3;
        return switch (memoryType.toUpperCase()) {
            case "PREFERENCE", "PROFILE" -> 1.0;
            case "RELATION", "HABIT" -> 0.9;
            case "GOAL", "PROJECT" -> 0.7;
            case "SKILL", "FACT" -> 0.5;
            case "EVENT", "SCHEDULE" -> 0.4;
            case "TEMPORARY" -> 0.2;
            default -> 0.3;
        };
    }

    /**
     * 打分权重配置。
     */
    public record ScorerWeights(
            double bm25,
            double keyword,
            double recency,
            double importance,
            double typeWeight
    ) {
        public static ScorerWeights defaults() {
            return new ScorerWeights(0.35, 0.25, 0.15, 0.15, 0.10);
        }

        public static ScorerWeights searchOptimized() {
            return new ScorerWeights(0.45, 0.30, 0.05, 0.10, 0.10);
        }

        public static ScorerWeights preferenceOptimized() {
            return new ScorerWeights(0.15, 0.10, 0.20, 0.35, 0.20);
        }
    }

    /**
     * 打分分解 — 展示各维度得分，用于调试和可视化。
     */
    public record ScoreBreakdown(
            double bm25,
            double keyword,
            double recency,
            double importance,
            double typeWeight
    ) {
        public String toDebugString() {
            return String.format(
                    "BM25=%.3f, Keyword=%.3f, Recency=%.3f, Importance=%.3f, Type=%.3f",
                    bm25, keyword, recency, importance, typeWeight);
        }
    }

    /**
     * 带评分的记忆条目。
     */
    public static class ScoredMemory {
        private final String content;
        private final String memoryType;
        private final double importance;
        private final Instant createdAt;
        private double score;
        private ScoreBreakdown breakdown;

        public ScoredMemory(String content, String memoryType, double importance, Instant createdAt) {
            this.content = content;
            this.memoryType = memoryType;
            this.importance = importance;
            this.createdAt = createdAt;
        }

        public String content() { return content; }
        public String memoryType() { return memoryType; }
        public double importance() { return importance; }
        public Instant createdAt() { return createdAt; }
        public double score() { return score; }
        public ScoreBreakdown breakdown() { return breakdown; }

        ScoredMemory withScore(double score) {
            this.score = score;
            return this;
        }

        ScoredMemory withBreakdown(ScoreBreakdown breakdown) {
            this.breakdown = breakdown;
            return this;
        }
    }
}