package com.mcp.tools.search;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvidenceItem(
        String title,
        String source,
        String url,
        String summary,
        List<String> keyPoints,
        String stance,
        double confidence,
        String publishedAt,
        int sourceRating
) {
    public static final String STANCE_NEUTRAL = "Neutral";
    public static final String STANCE_SUPPORTIVE = "Supportive";
    public static final String STANCE_CRITICAL = "Critical";
    public static final String STANCE_MIXED = "Mixed";

    public static final int RATING_UNKNOWN = 0;
    public static final int RATING_LOW = 1;
    public static final int RATING_MEDIUM = 3;
    public static final int RATING_HIGH = 4;
    public static final int RATING_VERY_HIGH = 5;

    public static EvidenceItem of(String title, String source, String url, String summary, List<String> keyPoints) {
        return new EvidenceItem(title, source, url, summary, keyPoints, STANCE_NEUTRAL, 0.5, null, RATING_UNKNOWN);
    }

    public static EvidenceItem of(String title, String source, String url, String summary,
                                   List<String> keyPoints, String stance, double confidence) {
        return new EvidenceItem(title, source, url, summary, keyPoints, stance, confidence, null, RATING_UNKNOWN);
    }

    public static EvidenceItem of(String title, String source, String url, String summary,
                                   List<String> keyPoints, String stance, double confidence,
                                   String publishedAt, int sourceRating) {
        return new EvidenceItem(title, source, url, summary, keyPoints, stance, confidence, publishedAt, sourceRating);
    }

    public String sourceRatingDisplay() {
        return switch (sourceRating) {
            case RATING_VERY_HIGH -> "★★★★★";
            case RATING_HIGH -> "★★★★";
            case RATING_MEDIUM -> "★★★";
            case RATING_LOW -> "★★";
            default -> "?";
        };
    }
}