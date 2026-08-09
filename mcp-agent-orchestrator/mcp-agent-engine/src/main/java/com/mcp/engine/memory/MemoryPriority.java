package com.mcp.engine.memory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record MemoryPriority(
        int totalScore,          // 综合优先级 (0-100)
        int importanceScore,     // 重要性维度 (0-100)
        int recencyScore,        // 最近性维度 (0-100)
        int frequencyScore,      // 频率维度 (0-100)
        int relevanceScore,      // 相关性维度 (0-100)
        double confidenceWeight  // 置信度权重 (0.0-1.0)
) {
    public static final int MAX_SCORE = 100;
    public static final int MIN_SCORE = 0;

    public static MemoryPriority of(int importanceScore, int recencyScore,
                                    int frequencyScore, int relevanceScore,
                                    double confidenceWeight) {
        double weighted = importanceScore * 0.40
                + recencyScore * 0.25
                + frequencyScore * 0.20
                + relevanceScore * 0.15;
        int total = (int) Math.round(weighted * confidenceWeight);
        total = Math.clamp(total, MIN_SCORE, MAX_SCORE);
        return new MemoryPriority(total, importanceScore, recencyScore,
                frequencyScore, relevanceScore, confidenceWeight);
    }

    public static int calcRecencyScore(LocalDateTime lastAccessedAt) {
        if (lastAccessedAt == null) return 0;
        long daysSince = ChronoUnit.DAYS.between(lastAccessedAt, LocalDateTime.now());
        if (daysSince <= 0) return 100;
        if (daysSince <= 1) return 95;
        if (daysSince <= 3) return 85;
        if (daysSince <= 7) return 70;
        if (daysSince <= 14) return 50;
        if (daysSince <= 30) return 30;
        if (daysSince <= 90) return 10;
        return 0;
    }

    public static int calcFrequencyScore(int accessCount, int daysSinceCreation) {
        if (daysSinceCreation <= 0) return 0;
        double freqPerDay = (double) accessCount / daysSinceCreation;
        if (freqPerDay >= 1.0) return 100;
        if (freqPerDay >= 0.5) return 85;
        if (freqPerDay >= 0.2) return 70;
        if (freqPerDay >= 0.1) return 50;
        if (freqPerDay >= 0.05) return 30;
        if (freqPerDay >= 0.01) return 10;
        return 0;
    }

    public MemoryTier deriveTier() {
        if (totalScore >= 70) return MemoryTier.HOT;
        if (totalScore >= 40) return MemoryTier.WARM;
        if (totalScore >= 10) return MemoryTier.COLD;
        return MemoryTier.ARCHIVED;
    }

    public boolean isHighPriority() {
        return totalScore >= 70;
    }

    public boolean isLowPriority() {
        return totalScore < 20;
    }
}