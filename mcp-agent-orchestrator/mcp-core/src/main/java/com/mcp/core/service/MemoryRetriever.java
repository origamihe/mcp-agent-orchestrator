package com.mcp.core.service;

import com.mcp.core.domain.memory.MemoryScope;
import com.mcp.core.domain.memory.MemoryType;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.repository.MemoryPackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆检索器 - 分层召回策略。
 *
 * 核心策略：
 *   Layer 1 (Always Inject): PREFERENCE, PROFILE, RELATION, HABIT — 不依赖 Query 匹配，全部注入
 *   Layer 2 (Semantic Search): FACT, PROJECT, GOAL, SKILL, SCHEDULE, EVENT, TEMPORARY — 按语义相关性召回
 *   Layer 3 (Summary): 压缩摘要记忆
 *
 * 设计原则：
 *   - PREFERENCE/IDENTITY/RELATION 永远重要，不需要 Embedding 匹配
 *   - 每层有独立的 Token 预算
 *   - 结果按层返回，便于 LongTermMemoryService 构建分层上下文
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryRetriever {

    private final MemoryPackageRepository repository;

    private static final int DEFAULT_TOP_K = 10;
    private static final int MAX_RETRIEVAL_TOKENS = 6000;

    private static final int MAX_ALWAYS_INJECT_TOKENS = 3000;
    private static final int MAX_ALWAYS_INJECT_ITEMS = 20;
    private static final int MAX_EPISODE_TOKENS = 3000;

    private static final List<MemoryType> ALWAYS_INJECT_TYPES = List.of(
            MemoryType.PREFERENCE, MemoryType.PROFILE, MemoryType.RELATION, MemoryType.HABIT);

    private static final List<MemoryType> EPISODE_TYPES = List.of(
            MemoryType.FACT, MemoryType.PROJECT, MemoryType.GOAL,
            MemoryType.SKILL, MemoryType.SCHEDULE, MemoryType.EVENT, MemoryType.TEMPORARY);

    public List<MemoryPackageEntity> retrieve(String userQuery, String userId, String groupId) {
        return retrieve(userQuery, userId, groupId, DEFAULT_TOP_K);
    }

    public List<MemoryPackageEntity> retrieve(String userQuery, String userId, String groupId, int topK) {
        return retrieveWithTiers(userQuery, userId, groupId, topK).stream()
                .map(MemoryRetrievalResult::memory)
                .collect(Collectors.toList());
    }

    /**
     * 分层召回：Always-Inject 层 + Episode 语义搜索层。
     * 使用合并查询（1次 SQL 替代原 4 次），在 Java 层按类型分类。
     */
    public List<MemoryRetrievalResult> retrieveWithTiers(String userQuery, String userId, String groupId, int topK) {
        List<MemoryPackageEntity> allActive = repository.findAllActiveByUserIdOrGroupId(userId, groupId);

        List<MemoryPackageEntity> alwaysInjectMemories = new ArrayList<>();
        List<MemoryPackageEntity> episodeMemories = new ArrayList<>();
        for (MemoryPackageEntity m : allActive) {
            if (m.getImportance() < 5) continue;
            if (ALWAYS_INJECT_TYPES.contains(m.getMemoryType())) {
                alwaysInjectMemories.add(m);
            } else if (EPISODE_TYPES.contains(m.getMemoryType())) {
                episodeMemories.add(m);
            }
        }

        alwaysInjectMemories.sort(Comparator.comparingDouble(MemoryPackageEntity::getWeight).reversed());
        if (alwaysInjectMemories.size() > MAX_ALWAYS_INJECT_ITEMS) {
            alwaysInjectMemories = alwaysInjectMemories.subList(0, MAX_ALWAYS_INJECT_ITEMS);
        }

        List<MemoryRetrievalResult> result = new ArrayList<>();
        int totalTokens = 0;

        int alwaysTokens = 0;
        for (MemoryPackageEntity m : alwaysInjectMemories) {
            double score = m.getImportance() * 0.6 + m.getWeight() * 0.4;
            int tokens = estimateTokens(m.getContent());
            if (alwaysTokens + tokens > MAX_ALWAYS_INJECT_TOKENS) break;
            result.add(new MemoryRetrievalResult(m, score, RetrievalTier.HOT, tokens));
            alwaysTokens += tokens;
        }
        totalTokens += alwaysTokens;

        if (alwaysInjectMemories.isEmpty()) {
            log.info("[MemoryRetriever] Always-Inject 层: 无记忆 userId={} groupId={}", userId, groupId);
        } else {
            log.info("[MemoryRetriever] Always-Inject 层: 注入{}条, token={}, 类型: {}",
                    result.size(), alwaysTokens,
                    result.stream().map(r -> r.memory().getMemoryType().name())
                            .distinct().collect(Collectors.joining(",")));
        }

        int remainingTokens = MAX_RETRIEVAL_TOKENS - totalTokens;
        int remainingSlots = topK - result.size();
        if (remainingSlots > 0 && remainingTokens > 0 && !episodeMemories.isEmpty()) {
            List<MemoryRetrievalResult> episode = scoreAndRankEpisodes(episodeMemories, userQuery, remainingSlots, remainingTokens);
            int episodeTokens = 0;
            for (MemoryRetrievalResult r : episode) {
                if (episodeTokens + r.tokenCount() > remainingTokens) break;
                result.add(r);
                episodeTokens += r.tokenCount();
                if (result.size() >= topK) break;
            }

            if (!episode.isEmpty()) {
                log.info("[MemoryRetriever] Episode 层: 注入{}条, token={}",
                        episode.size(), episodeTokens);
            }
        }

        log.info("[MemoryRetriever] 分层召回: Always-Inject={}条, Episode检索={}条, 总计={}条, token={}",
                alwaysInjectMemories.size(), result.size() - alwaysInjectMemories.size(), result.size(), totalTokens);
        return result;
    }

    /**
     * Always-Inject 层：PREFERENCE/PROFILE/RELATION/HABIT 全部注入（不依赖Query匹配）。
     * 限制最多 MAX_ALWAYS_INJECT_ITEMS 条，按 weight 降序。
     * @deprecated 已由 retrieveWithTiers 中的合并查询替代，仅保留供外部可能的直接调用
     */
    @Deprecated
    private List<MemoryRetrievalResult> retrieveAlwaysInject(String userId, String groupId) {
        List<MemoryPackageEntity> memories = new ArrayList<>();
        if (userId != null) {
            memories.addAll(repository.findByUserIdAndMemoryTypeIn(userId, ALWAYS_INJECT_TYPES));
        }
        if (groupId != null) {
            memories.addAll(repository.findByGroupIdAndMemoryTypeIn(groupId, ALWAYS_INJECT_TYPES));
        }

        return memories.stream()
                .filter(MemoryPackageEntity::isActive)
                .filter(m -> m.getImportance() >= 5)
                .sorted(Comparator.comparingDouble(MemoryPackageEntity::getWeight).reversed())
                .limit(MAX_ALWAYS_INJECT_ITEMS)
                .map(m -> {
                    double score = m.getImportance() * 0.6 + m.getWeight() * 0.4;
                    int tokens = estimateTokens(m.getContent());
                    return new MemoryRetrievalResult(m, score, RetrievalTier.HOT, tokens);
                })
                .collect(Collectors.toList());
    }

    /**
     * 对预取的 Episode 记忆进行评分和排序（不执行数据库查询）。
     * 原 retrieveEpisode + fetchEpisodeMemories 的合并优化版。
     */
    private List<MemoryRetrievalResult> scoreAndRankEpisodes(List<MemoryPackageEntity> episodeMemories,
                                                              String userQuery, int topK, int maxTokens) {
        List<ScoredMemoryEntity> scored = episodeMemories.stream()
                .map(m -> {
                    double relevance = computeRelevance(m, userQuery);
                    double tierBonus = getTierBonus(m);
                    double score = m.getImportance() * 0.4 + relevance * 25.0 + tierBonus * 20.0;
                    return new ScoredMemoryEntity(m, score, deriveTier(score));
                })
                .sorted(Comparator.comparingDouble(ScoredMemoryEntity::score).reversed())
                .toList();

        List<MemoryRetrievalResult> result = new ArrayList<>();
        int totalTokens = 0;
        for (ScoredMemoryEntity sm : scored) {
            if (sm.tier == RetrievalTier.ARCHIVED) continue;
            int tokens = estimateTokens(sm.entity.getContent());
            if (totalTokens + tokens > maxTokens) break;
            result.add(new MemoryRetrievalResult(
                    sm.entity, sm.score, sm.tier, tokens));
            totalTokens += tokens;
            if (result.size() >= topK) break;
        }

        return result;
    }

    private double getTierBonus(MemoryPackageEntity memory) {
        if (memory.isPermanent()) return 5.0;
        double weight = memory.getWeight();
        if (weight >= 70) return 5.0;
        if (weight >= 40) return 3.0;
        if (weight >= 10) return 1.0;
        return 0;
    }

    private RetrievalTier deriveTier(double score) {
        if (score >= 70) return RetrievalTier.HOT;
        if (score >= 40) return RetrievalTier.WARM;
        if (score >= 10) return RetrievalTier.COLD;
        return RetrievalTier.ARCHIVED;
    }

    public enum RetrievalTier {
        HOT, WARM, COLD, ARCHIVED
    }

    public record MemoryRetrievalResult(
            MemoryPackageEntity memory,
            double score,
            RetrievalTier tier,
            int tokenCount
    ) {}

    /**
     * 计算单条记忆与查询的关键词相关性（仅用于 Episode 层）。
     */
    private double computeRelevance(MemoryPackageEntity memory, String query) {
        double score = 0;

        if (query != null && memory.getContent() != null) {
            String lowerContent = memory.getContent().toLowerCase();
            String lowerQuery = query.toLowerCase();
            String[] queryWords = lowerQuery.split("\\s+");
            for (String word : queryWords) {
                if (word.length() >= 2 && lowerContent.contains(word)) {
                    score += 2;
                }
            }
        }

        if (memory.getMemoryType() != null) {
            score += switch (memory.getMemoryType().getLifecycle()) {
                case PERMANENT -> 3;
                case LONG -> 2;
                case MEDIUM -> 1;
                case SHORT -> 0.5;
            };
        }

        return score;
    }

    private int estimateTokens(String text) {
        return text == null ? 0 : text.length() / 4;
    }

    private record ScoredMemoryEntity(MemoryPackageEntity entity, double score, RetrievalTier tier) {}
}