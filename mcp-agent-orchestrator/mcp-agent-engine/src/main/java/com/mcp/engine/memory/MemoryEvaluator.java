package com.mcp.engine.memory;

import com.mcp.core.domain.memory.MemoryType;
import com.mcp.core.entity.MemoryPackageEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class MemoryEvaluator {

    public static final int MIN_IMPORTANCE_TO_KEEP = 3;
    private static final int UPGRADE_THRESHOLD = 3;

    public List<ScoredMemory> evaluate(List<MemoryExtractor.MemoryCandidate> candidates) {
        return candidates.stream()
                .map(this::score)
                .filter(s -> s.importance >= MIN_IMPORTANCE_TO_KEEP)
                .peek(s -> log.info("[MemoryEvaluator] type={} baseImportance={} adjustedImportance={} confidence={} ttl={}",
                        s.memoryType, getBaseImportance(s.memoryType), s.importance, s.confidence, s.ttl))
                .toList();
    }

    public List<ScoredMemory> evaluateWithPriority(List<MemoryExtractor.MemoryCandidate> candidates,
                                                    String query) {
        return candidates.stream()
                .map(c -> scoreWithPriority(c, query))
                .filter(s -> s.importance >= MIN_IMPORTANCE_TO_KEEP)
                .peek(s -> log.info("[MemoryEvaluator] type={} importance={} priority={} tier={} query=\"{}\"",
                        s.memoryType, s.importance, s.priority != null ? s.priority.totalScore() : 0,
                        s.priority != null ? s.priority.deriveTier() : MemoryTier.COLD, query))
                .toList();
    }

    public List<ScoredMemory> reEvaluateExisting(List<MemoryPackageEntity> existingMemories,
                                                  String query) {
        List<ScoredMemory> result = new ArrayList<>();
        for (MemoryPackageEntity mem : existingMemories) {
            int recencyScore = MemoryPriority.calcRecencyScore(mem.getLastAccessedAt());
            int frequencyScore = MemoryPriority.calcFrequencyScore(
                    mem.getAccessCount(),
                    (int) java.time.temporal.ChronoUnit.DAYS.between(
                            mem.getCreatedAt().toLocalDate(), java.time.LocalDate.now()));
            int relevanceScore = calcRelevanceScore(mem.getContent(), query);
            double confidenceWeight = mem.getConfidence() / 100.0;

            MemoryPriority priority = MemoryPriority.of(
                    mem.getImportance(), recencyScore, frequencyScore,
                    relevanceScore, confidenceWeight);

            MemoryTier tier = priority.deriveTier();
            if (mem.isPermanent()) {
                tier = MemoryTier.HOT;
            }

            result.add(new ScoredMemory(
                    mem.getContent(), mem.getMemoryType(), mem.getImportance(),
                    mem.getConfidence(), mem.getTtl(), mem.getSourceQuote(),
                    priority, tier));
        }
        return result;
    }

    private ScoredMemory score(MemoryExtractor.MemoryCandidate candidate) {
        int baseImportance = getBaseImportance(candidate.memoryType());
        int adjustedImportance = Math.min(100,
                (int) (baseImportance * (candidate.confidence() / 100.0)));
        LocalDateTime ttl = calculateTtl(candidate.memoryType());

        return new ScoredMemory(
                candidate.content(),
                candidate.memoryType(),
                adjustedImportance,
                candidate.confidence(),
                ttl,
                candidate.sourceQuote(),
                null, null);
    }

    private ScoredMemory scoreWithPriority(MemoryExtractor.MemoryCandidate candidate, String query) {
        int baseImportance = getBaseImportance(candidate.memoryType());
        int adjustedImportance = Math.min(100,
                (int) (baseImportance * (candidate.confidence() / 100.0)));
        LocalDateTime ttl = calculateTtl(candidate.memoryType());

        int recencyScore = 100;
        int frequencyScore = 0;
        int relevanceScore = calcRelevanceScore(candidate.content(), query);
        double confidenceWeight = candidate.confidence() / 100.0;

        MemoryPriority priority = MemoryPriority.of(
                adjustedImportance, recencyScore, frequencyScore,
                relevanceScore, confidenceWeight);

        return new ScoredMemory(
                candidate.content(),
                candidate.memoryType(),
                adjustedImportance,
                candidate.confidence(),
                ttl,
                candidate.sourceQuote(),
                priority, priority.deriveTier());
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

    private int getBaseImportance(MemoryType type) {
        return switch (type) {
            case PROFILE -> 95;
            case IDENTITY -> 92;
            case RELATION -> 90;
            case PREFERENCE -> 85;
            case HABIT -> 80;
            case GOAL -> 75;
            case PROJECT -> 70;
            case FACT -> 60;
            case SKILL -> 65;
            case SCHEDULE -> 55;
            case EVENT -> 40;
            case TEMPORARY -> 5;
        };
    }

    private LocalDateTime calculateTtl(MemoryType type) {
        return switch (type.getLifecycle()) {
            case PERMANENT -> null;
            case LONG -> LocalDateTime.now().plusDays(365);
            case MEDIUM -> LocalDateTime.now().plusDays(30);
            case SHORT -> LocalDateTime.now().plusHours(24);
        };
    }

    public record ScoredMemory(
            String content,
            MemoryType memoryType,
            int importance,
            int confidence,
            LocalDateTime ttl,
            String sourceQuote,
            MemoryPriority priority,
            MemoryTier tier
    ) {
        public ScoredMemory(String content, MemoryType memoryType, int importance, int confidence, boolean isHighValue) {
            this(content, memoryType, importance, confidence, null, null, null, null);
        }

        public boolean isLowValue() {
            return importance < 5;
        }

        public boolean isHighValue() {
            return importance >= 80;
        }

        public boolean isHotTier() {
            return tier == MemoryTier.HOT;
        }
    }
}