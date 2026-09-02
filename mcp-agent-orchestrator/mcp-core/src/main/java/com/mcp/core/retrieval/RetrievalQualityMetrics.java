package com.mcp.core.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 召回质量指标 — 追踪检索系统的运行质量。
 *
 * 核心指标：
 * <pre>
 * 1. 平均召回率 (Avg Recall)     — 返回结果数 / 候选集数
 * 2. 命中率 (Hit Rate)           — 有结果的查询比例
 * 3. 平均延迟 (Avg Latency)      — 每次检索的耗时
 * 4. 去重率 (Dedup Rate)         — 被去重移除的候选比例
 * 5. 零结果率 (Zero Result Rate) — 返回 0 结果的查询比例
 * </pre>
 *
 * 设计原则：
 * - 有明确召回指标后再改 Hybrid 策略，而不是凭感觉
 * - 指标驱动优化：如果零结果率 > 20%，需要降低阈值；如果延迟 > 100ms，需要优化索引
 * - 不依赖外部监控系统，内置轻量级指标收集
 */
public class RetrievalQualityMetrics {

    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong totalCandidates = new AtomicLong(0);
    private final AtomicLong totalResults = new AtomicLong(0);
    private final AtomicLong totalLatencyNs = new AtomicLong(0);
    private final AtomicLong zeroResultQueries = new AtomicLong(0);
    private final AtomicLong totalDeduped = new AtomicLong(0);

    private final List<QueryRecord> recentQueries = new ArrayList<>();
    private static final int MAX_RECENT = 100;

    /**
     * 记录一次查询。
     */
    public void recordQuery(String query, int candidateCount, int resultCount, long latencyNs) {
        totalQueries.incrementAndGet();
        totalCandidates.addAndGet(candidateCount);
        totalResults.addAndGet(resultCount);
        totalLatencyNs.addAndGet(latencyNs);

        if (resultCount == 0) {
            zeroResultQueries.incrementAndGet();
        }

        synchronized (recentQueries) {
            recentQueries.add(new QueryRecord(
                    query != null && query.length() > 100 ? query.substring(0, 100) + "..." : query,
                    candidateCount, resultCount, latencyNs));
            if (recentQueries.size() > MAX_RECENT) {
                recentQueries.remove(0);
            }
        }
    }

    /**
     * 记录去重数量。
     */
    public void recordDedup(int count) {
        totalDeduped.addAndGet(count);
    }

    /**
     * 平均召回率：结果数 / 候选数。
     */
    public double avgRecallRate() {
        long queries = totalQueries.get();
        long candidates = totalCandidates.get();
        if (queries == 0 || candidates == 0) return 0;
        return (double) totalResults.get() / candidates;
    }

    /**
     * 命中率：有结果的查询比例。
     */
    public double hitRate() {
        long queries = totalQueries.get();
        if (queries == 0) return 0;
        return 1.0 - (double) zeroResultQueries.get() / queries;
    }

    /**
     * 平均延迟（毫秒）。
     */
    public double avgLatencyMs() {
        long queries = totalQueries.get();
        if (queries == 0) return 0;
        return (double) totalLatencyNs.get() / queries / 1_000_000;
    }

    /**
     * 零结果率。
     */
    public double zeroResultRate() {
        long queries = totalQueries.get();
        if (queries == 0) return 0;
        return (double) zeroResultQueries.get() / queries;
    }

    /**
     * 去重率。
     */
    public double dedupRate() {
        long total = totalCandidates.get();
        if (total == 0) return 0;
        return (double) totalDeduped.get() / total;
    }

    /**
     * 总查询数。
     */
    public long totalQueries() {
        return totalQueries.get();
    }

    /**
     * 生成摘要报告。
     */
    public MetricsReport report() {
        return new MetricsReport(
                totalQueries.get(),
                avgRecallRate(),
                hitRate(),
                avgLatencyMs(),
                zeroResultRate(),
                dedupRate(),
                getRecentQueries()
        );
    }

    /**
     * 最近查询记录（用于调试）。
     */
    public List<QueryRecord> getRecentQueries() {
        synchronized (recentQueries) {
            return List.copyOf(recentQueries);
        }
    }

    /**
     * 清空指标。
     */
    public void reset() {
        totalQueries.set(0);
        totalCandidates.set(0);
        totalResults.set(0);
        totalLatencyNs.set(0);
        zeroResultQueries.set(0);
        totalDeduped.set(0);
        synchronized (recentQueries) {
            recentQueries.clear();
        }
    }

    public record QueryRecord(String query, int candidateCount, int resultCount, long latencyNs) {}

    public record MetricsReport(
            long totalQueries,
            double avgRecallRate,
            double hitRate,
            double avgLatencyMs,
            double zeroResultRate,
            double dedupRate,
            List<QueryRecord> recentQueries
    ) {
        @Override
        public String toString() {
            return String.format(
                    "RetrievalMetrics{queries=%d, recall=%.2f%%, hitRate=%.2f%%, latency=%.2fms, zeroResult=%.2f%%, dedup=%.2f%%}",
                    totalQueries,
                    avgRecallRate * 100,
                    hitRate * 100,
                    avgLatencyMs,
                    zeroResultRate * 100,
                    dedupRate * 100);
        }
    }
}