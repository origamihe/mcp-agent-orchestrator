package com.mcp.core.retrieval;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * BM25 打分器 — 对记忆/文档进行 BM25 相关性评分。
 *
 * BM25 是 TF-IDF 的改进版本，解决了 TF 饱和问题。
 * 公式：score(D, Q) = Σ IDF(qi) * (f(qi, D) * (k1 + 1)) / (f(qi, D) + k1 * (1 - b + b * |D|/avgdl))
 *
 * 为什么用 BM25 而不是简单的关键词匹配：
 * - 关键词匹配无法区分"Python 编程"和"Python 蛇"的相关性差异
 * - BM25 通过 IDF 降低常见词权重，提高稀有词权重
 * - BM25 通过文档长度归一化，避免长文档天然得分更高
 *
 * 设计原则：
 * - 不需要外部依赖（如 Lucene）
 * - 内存内计算，适合 10K 级别记忆库
 * - 如果记忆量达到 100K+，可升级为 Lucene/Elasticsearch
 */
public class Bm25Scorer {

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private final Map<String, Double> idfCache = new HashMap<>();
    private final List<String> allDocuments;
    private final double avgDocLength;

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");

    public Bm25Scorer(List<String> allDocuments) {
        this.allDocuments = allDocuments;
        this.avgDocLength = allDocuments.stream()
                .mapToInt(Bm25Scorer::tokenizeCount)
                .average()
                .orElse(1.0);
    }

    /**
     * 计算单条文档对查询的 BM25 分数。
     */
    public double score(String document, String query) {
        List<String> docTokens = tokenize(document);
        List<String> queryTokens = tokenize(query);

        if (docTokens.isEmpty() || queryTokens.isEmpty()) return 0;

        double score = 0;
        int docLength = docTokens.size();

        Map<String, Integer> termFreq = termFrequency(docTokens);

        for (String qt : queryTokens) {
            double idf = computeIdf(qt);
            int tf = termFreq.getOrDefault(qt, 0);
            if (tf == 0) continue;

            double numerator = tf * (K1 + 1);
            double denominator = tf + K1 * (1 - B + B * docLength / avgDocLength);
            score += idf * numerator / denominator;
        }

        return score;
    }

    /**
     * 批量计算所有文档的 BM25 分数，返回排序后的结果。
     */
    public List<ScoredDocument> scoreAll(List<String> documents, String query) {
        return documents.stream()
                .map(doc -> new ScoredDocument(doc, score(doc, query)))
                .filter(sd -> sd.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .collect(Collectors.toList());
    }

    private double computeIdf(String term) {
        return idfCache.computeIfAbsent(term, t -> {
            long docCount = allDocuments.stream()
                    .filter(doc -> tokenize(doc).contains(t))
                    .count();
            int N = allDocuments.size();
            return Math.log(1 + (N - docCount + 0.5) / (docCount + 0.5));
        });
    }

    private Map<String, Integer> termFrequency(List<String> tokens) {
        Map<String, Integer> freq = new HashMap<>();
        for (String t : tokens) {
            freq.merge(t, 1, Integer::sum);
        }
        return freq;
    }

    public static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> tokens = new ArrayList<>();
        java.util.regex.Matcher matcher = TOKEN_PATTERN.matcher(text.toLowerCase());
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    public static int tokenizeCount(String text) {
        return tokenize(text).size();
    }

    /**
     * BM25 评分结果。
     */
    public record ScoredDocument(String document, double score) {}
}