package com.mcp.engine.memory;

import com.mcp.core.entity.MemoryPackageEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MemoryRanker {

    public static final int MAX_CONTEXT_MEMORIES = 8;

    public List<RankedMemory> rank(List<MemoryPackageEntity> memories, String query) {
        return memories.stream()
                .filter(MemoryPackageEntity::isActive)
                .map(m -> rankSingle(m, query))
                .sorted(Comparator.<RankedMemory>comparingInt(r -> r.priority().totalScore()).reversed())
                .collect(Collectors.toList());
    }

    public List<RankedMemory> rankForContext(List<MemoryPackageEntity> memories, String query) {
        return rank(memories, query).stream()
                .filter(r -> r.tier().shouldInjectToContext())
                .limit(MAX_CONTEXT_MEMORIES)
                .collect(Collectors.toList());
    }

    public List<RankedMemory> rankForRetrieval(List<MemoryPackageEntity> memories, String query) {
        return rank(memories, query).stream()
                .filter(r -> r.tier().shouldRetrieve())
                .limit(MAX_CONTEXT_MEMORIES * 2)
                .collect(Collectors.toList());
    }

    private RankedMemory rankSingle(MemoryPackageEntity memory, String query) {
        int importanceScore = memory.getImportance();
        int recencyScore = MemoryPriority.calcRecencyScore(memory.getLastAccessedAt());
        int frequencyScore = calcFrequencyScore(memory);
        int relevanceScore = calcRelevanceScore(memory.getContent(), query);
        double confidenceWeight = memory.getConfidence() / 100.0;

        MemoryPriority priority = MemoryPriority.of(
                importanceScore, recencyScore, frequencyScore,
                relevanceScore, confidenceWeight);

        MemoryTier tier = priority.deriveTier();

        if (memory.isPermanent()) {
            tier = MemoryTier.HOT;
        }

        return new RankedMemory(memory, priority, tier);
    }

    private int calcFrequencyScore(MemoryPackageEntity memory) {
        int daysSinceCreation = (int) ChronoUnit.DAYS.between(
                memory.getCreatedAt().toLocalDate(), java.time.LocalDate.now());
        return MemoryPriority.calcFrequencyScore(memory.getAccessCount(), Math.max(daysSinceCreation, 1));
    }

    private int calcRelevanceScore(String content, String query) {
        if (query == null || query.isBlank()) return 50;
        if (content == null || content.isBlank()) return 0;

        String lowerContent = content.toLowerCase();
        String lowerQuery = query.toLowerCase();

        String[] queryWords = lowerQuery.split("[\\s，,。.!！?？]+");
        int matchCount = 0;
        for (String word : queryWords) {
            if (word.length() >= 2 && lowerContent.contains(word)) {
                matchCount++;
            }
        }

        if (matchCount == 0) return 10;
        if (queryWords.length == 0) return 10;

        double ratio = (double) matchCount / queryWords.length;
        if (ratio >= 0.8) return 100;
        if (ratio >= 0.6) return 85;
        if (ratio >= 0.4) return 65;
        if (ratio >= 0.2) return 40;
        return 20;
    }

    public record RankedMemory(
            MemoryPackageEntity memory,
            MemoryPriority priority,
            MemoryTier tier
    ) {
        public String formatForPrompt() {
            return String.format("[%s|P%d] %s",
                    memory.getMemoryType().getDisplayName(),
                    priority.totalScore(),
                    memory.getContent());
        }
    }
}