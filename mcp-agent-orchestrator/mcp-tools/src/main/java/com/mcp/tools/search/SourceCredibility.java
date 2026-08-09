package com.mcp.tools.search;

import java.util.Map;

/**
 * 来源可信度评级服务。
 * 基于已知来源的公信力进行打分，未知来源默认 RATING_UNKNOWN。
 */
public class SourceCredibility {

    private static final Map<String, Integer> RATINGS = Map.ofEntries(
            Map.entry("reuters", EvidenceItem.RATING_VERY_HIGH),
            Map.entry("associated press", EvidenceItem.RATING_VERY_HIGH),
            Map.entry("ap", EvidenceItem.RATING_VERY_HIGH),
            Map.entry("bbc", EvidenceItem.RATING_VERY_HIGH),
            Map.entry("bloomberg", EvidenceItem.RATING_HIGH),
            Map.entry("the guardian", EvidenceItem.RATING_HIGH),
            Map.entry("guardian", EvidenceItem.RATING_HIGH),
            Map.entry("cnn", EvidenceItem.RATING_HIGH),
            Map.entry("the new york times", EvidenceItem.RATING_HIGH),
            Map.entry("nytimes", EvidenceItem.RATING_HIGH),
            Map.entry("washington post", EvidenceItem.RATING_HIGH),
            Map.entry("wall street journal", EvidenceItem.RATING_HIGH),
            Map.entry("wsj", EvidenceItem.RATING_HIGH),
            Map.entry("financial times", EvidenceItem.RATING_HIGH),
            Map.entry("npr", EvidenceItem.RATING_HIGH),
            Map.entry("al jazeera", EvidenceItem.RATING_MEDIUM),
            Map.entry("the economist", EvidenceItem.RATING_HIGH),
            Map.entry("nature", EvidenceItem.RATING_VERY_HIGH),
            Map.entry("science", EvidenceItem.RATING_VERY_HIGH),
            Map.entry("nbc news", EvidenceItem.RATING_MEDIUM),
            Map.entry("abc news", EvidenceItem.RATING_MEDIUM),
            Map.entry("cbs news", EvidenceItem.RATING_MEDIUM),
            Map.entry("fox news", EvidenceItem.RATING_LOW),
            Map.entry("msnbc", EvidenceItem.RATING_LOW),
            Map.entry("cnbc", EvidenceItem.RATING_MEDIUM),
            Map.entry("politico", EvidenceItem.RATING_MEDIUM),
            Map.entry("the hill", EvidenceItem.RATING_MEDIUM),
            Map.entry("vox", EvidenceItem.RATING_MEDIUM),
            Map.entry("buzzfeed", EvidenceItem.RATING_LOW),
            Map.entry("wikipedia", EvidenceItem.RATING_LOW),
            Map.entry("reddit", EvidenceItem.RATING_LOW),
            Map.entry("twitter", EvidenceItem.RATING_LOW),
            Map.entry("x", EvidenceItem.RATING_LOW),
            Map.entry("新华网", EvidenceItem.RATING_HIGH),
            Map.entry("人民网", EvidenceItem.RATING_HIGH),
            Map.entry("环球时报", EvidenceItem.RATING_MEDIUM),
            Map.entry("央视新闻", EvidenceItem.RATING_HIGH),
            Map.entry("财新", EvidenceItem.RATING_HIGH),
            Map.entry("第一财经", EvidenceItem.RATING_MEDIUM),
            Map.entry("nhk", EvidenceItem.RATING_HIGH),
            Map.entry("france 24", EvidenceItem.RATING_MEDIUM),
            Map.entry("dw", EvidenceItem.RATING_MEDIUM)
    );

    private SourceCredibility() {}

    /**
     * 根据来源名称返回可信度评分。
     * 返回 0-5 之间的整数，0 表示未知。
     */
    public static int rate(String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            return EvidenceItem.RATING_UNKNOWN;
        }
        String normalized = sourceName.toLowerCase().trim();
        return RATINGS.getOrDefault(normalized, EvidenceItem.RATING_UNKNOWN);
    }

    public static String ratingDisplay(int rating) {
        return switch (rating) {
            case EvidenceItem.RATING_VERY_HIGH -> "★★★★★";
            case EvidenceItem.RATING_HIGH -> "★★★★";
            case EvidenceItem.RATING_MEDIUM -> "★★★";
            case EvidenceItem.RATING_LOW -> "★★";
            default -> "?";
        };
    }
}