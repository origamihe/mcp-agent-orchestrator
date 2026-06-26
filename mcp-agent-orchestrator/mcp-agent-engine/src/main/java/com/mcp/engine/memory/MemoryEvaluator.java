package com.mcp.engine.memory;

import com.mcp.core.domain.memory.MemoryType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆评估器 - 对抽取的记忆候选进行评分。
 *
 * 评分维度：
 *   1. importance (0-100): 基于类型的基准分 + 置信度调整
 *   2. ttl (过期时间): 基于类型的生命周期
 *   3. 过滤低价值记忆: importance < 3 的直接丢弃
 *
 * 升级逻辑：
 *   连续出现 ≥3 次的记忆 → 升级生命周期（如 TEMPORARY → EVENT → FACT → PREFERENCE）
 */
@Slf4j
@Service
public class MemoryEvaluator {

    private static final int MIN_IMPORTANCE_TO_KEEP = 3;
    private static final int UPGRADE_THRESHOLD = 3;

    /**
     * 评估记忆候选，返回评分后的记忆。
     * importance < MIN_IMPORTANCE_TO_KEEP 的会被过滤掉（不进入数据库）。
     */
    public List<ScoredMemory> evaluate(List<MemoryExtractor.MemoryCandidate> candidates) {
        return candidates.stream()
                .map(this::score)
                .filter(s -> s.importance >= MIN_IMPORTANCE_TO_KEEP)
                .peek(s -> log.debug("[MemoryEvaluator] {} importance={} confidence={} ttl={}",
                        s.memoryType, s.importance, s.confidence, s.ttl))
                .toList();
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
                candidate.sourceQuote()
        );
    }

    /**
     * 基于记忆类型的基准重要性评分
     */
    private int getBaseImportance(MemoryType type) {
        return switch (type) {
            case PROFILE -> 95;
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

    /**
     * 基于记忆类型计算 TTL（过期时间）
     */
    private LocalDateTime calculateTtl(MemoryType type) {
        return switch (type.getLifecycle()) {
            case PERMANENT -> null;
            case LONG -> LocalDateTime.now().plusDays(365);
            case MEDIUM -> LocalDateTime.now().plusDays(30);
            case SHORT -> LocalDateTime.now().plusHours(24);
        };
    }

    /**
     * 评分后的记忆条目
     */
    public record ScoredMemory(
            String content,
            MemoryType memoryType,
            int importance,
            int confidence,
            LocalDateTime ttl,
            String sourceQuote
    ) {
        public boolean isLowValue() {
            return importance < 5;
        }

        public boolean isHighValue() {
            return importance >= 80;
        }
    }
}