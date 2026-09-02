package com.mcp.core.retrieval;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索器 — 融合 BM25 + 关键词 + 多维打分的检索策略。
 *
 * 检索流程：
 * <pre>
 * 1. 粗筛（BM25）        — 用 BM25 快速过滤不相关文档
 * 2. 精排（Multi-Dim）   — 对候选集进行多维打分
 * 3. 去重                — 基于内容相似度去重
 * 4. 多样性保证           — 确保不同类型记忆都有代表
 * 5. 截断                — 返回 topK 结果
 * </pre>
 *
 * 相比原 MemoryRetriever 的改进：
 * - 从简单关键词匹配 → BM25 语义相关性
 * - 从单维评分 → 五维综合评分（BM25 + 关键词 + 时效 + 重要性 + 类型）
 * - 从无去重 → 基于 Jaccard 相似度的去重
 * - 从无多样性 → 类型多样性保证
 * - 从无质量指标 → 召回质量可追踪
 */
@Slf4j
public class HybridRetriever {

    private final RetrievalScorer scorer;
    private final RetrievalQualityMetrics metrics;

    private static final double JACCARD_DEDUP_THRESHOLD = 0.7;
    private static final int MAX_PER_TYPE = 5;

    public HybridRetriever(List<String> allDocuments) {
        Bm25Scorer bm25 = new Bm25Scorer(allDocuments);
        this.scorer = new RetrievalScorer(bm25);
        this.metrics = new RetrievalQualityMetrics();
    }

    public HybridRetriever(List<String> allDocuments, RetrievalScorer.ScorerWeights weights) {
        Bm25Scorer bm25 = new Bm25Scorer(allDocuments);
        this.scorer = new RetrievalScorer(bm25, weights);
        this.metrics = new RetrievalQualityMetrics();
    }

    /**
     * 执行混合检索。
     *
     * @param query     用户查询
     * @param candidates 候选记忆列表
     * @param topK      返回数量
     * @return 排序后的检索结果
     */
    public List<HybridRetrievalResult> retrieve(
            String query,
            List<RetrievalScorer.ScoredMemory> candidates,
            int topK) {

        long startTime = System.nanoTime();

        if (candidates.isEmpty() || query == null || query.isBlank()) {
            metrics.recordQuery(query, 0, 0, System.nanoTime() - startTime);
            return List.of();
        }

        List<RetrievalScorer.ScoredMemory> scored = candidates.stream()
                .map(c -> scorer.score(c, query))
                .filter(c -> c.score() > 0.01)
                .sorted(Comparator.comparingDouble(RetrievalScorer.ScoredMemory::score).reversed())
                .collect(Collectors.toList());

        List<RetrievalScorer.ScoredMemory> deduped = deduplicate(scored);

        List<RetrievalScorer.ScoredMemory> diversified = ensureDiversity(deduped);

        List<HybridRetrievalResult> results = diversified.stream()
                .limit(topK)
                .map(m -> new HybridRetrievalResult(
                        m.content(), m.memoryType(), m.score(), m.breakdown()))
                .collect(Collectors.toList());

        long elapsed = System.nanoTime() - startTime;
        metrics.recordQuery(query, candidates.size(), results.size(), elapsed);

        log.debug("[HybridRetriever] Query='{}', candidates={}, results={}, elapsed={}ms",
                query.length() > 50 ? query.substring(0, 50) + "..." : query,
                candidates.size(), results.size(), elapsed / 1_000_000);

        return results;
    }

    /**
     * 基于 Jaccard 相似度去重 — 移除内容高度相似的记忆。
     */
    private List<RetrievalScorer.ScoredMemory> deduplicate(
            List<RetrievalScorer.ScoredMemory> scored) {

        List<RetrievalScorer.ScoredMemory> result = new ArrayList<>();
        for (RetrievalScorer.ScoredMemory candidate : scored) {
            boolean isDuplicate = result.stream().anyMatch(kept ->
                    jaccardSimilarity(kept.content(), candidate.content()) > JACCARD_DEDUP_THRESHOLD);
            if (!isDuplicate) {
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * 类型多样性保证 — 每种类型最多 MAX_PER_TYPE 条，避免某类型垄断。
     */
    private List<RetrievalScorer.ScoredMemory> ensureDiversity(
            List<RetrievalScorer.ScoredMemory> scored) {

        Map<String, Integer> typeCount = new HashMap<>();
        List<RetrievalScorer.ScoredMemory> result = new ArrayList<>();

        for (RetrievalScorer.ScoredMemory mem : scored) {
            String type = mem.memoryType() != null ? mem.memoryType() : "UNKNOWN";
            int count = typeCount.getOrDefault(type, 0);
            if (count < MAX_PER_TYPE) {
                result.add(mem);
                typeCount.put(type, count + 1);
            }
        }
        return result;
    }

    /**
     * 计算 Jaccard 相似度。
     */
    private double jaccardSimilarity(String a, String b) {
        if (a == null || b == null) return 0;
        Set<String> tokensA = new HashSet<>(Bm25Scorer.tokenize(a));
        Set<String> tokensB = new HashSet<>(Bm25Scorer.tokenize(b));
        if (tokensA.isEmpty() && tokensB.isEmpty()) return 0;

        Set<String> intersection = new HashSet<>(tokensA);
        intersection.retainAll(tokensB);

        Set<String> union = new HashSet<>(tokensA);
        union.addAll(tokensB);

        return (double) intersection.size() / union.size();
    }

    /**
     * 获取检索质量指标。
     */
    public RetrievalQualityMetrics getMetrics() {
        return metrics;
    }

    /**
     * 混合检索结果。
     */
    public record HybridRetrievalResult(
            String content,
            String memoryType,
            double score,
            RetrievalScorer.ScoreBreakdown breakdown
    ) {
        public String toDebugString() {
            return String.format("[%.3f] %s | %s → %s",
                    score, memoryType,
                    content != null && content.length() > 80
                            ? content.substring(0, 80) + "..."
                            : content,
                    breakdown != null ? breakdown.toDebugString() : "N/A");
        }
    }
}