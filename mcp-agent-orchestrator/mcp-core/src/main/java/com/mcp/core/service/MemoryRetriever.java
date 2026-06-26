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
 * 记忆检索器 - 根据当前查询智能召回最相关的记忆。
 *
 * 核心策略：
 *   1. 根据查询意图匹配记忆类型（不是全量返回）
 *   2. 按 importance × relevance 排序
 *   3. 返回 top-K（限制 token 预算）
 *
 * 与旧版 LongTermMemoryService 全量 weight 排序的区别：
 *   - 类型匹配：根据查询意图只召回相关类型的记忆
 *   - 关键词相关性：查询词与记忆内容的关键词匹配
 *   - 智能 top-K：在 token 预算内返回最相关的记忆
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryRetriever {

    private final MemoryPackageRepository repository;

    private static final int DEFAULT_TOP_K = 10;
    private static final int MAX_RETRIEVAL_TOKENS = 6000;

    /**
     * 根据用户查询召回最相关的记忆。
     *
     * @param userQuery 用户当前的问题/输入
     * @param userId    用户ID（可选）
     * @param groupId   群ID（可选）
     * @return 排序后的相关记忆列表
     */
    public List<MemoryPackageEntity> retrieve(String userQuery, String userId, String groupId) {
        return retrieve(userQuery, userId, groupId, DEFAULT_TOP_K);
    }

    public List<MemoryPackageEntity> retrieve(String userQuery, String userId, String groupId, int topK) {
        List<MemoryPackageEntity> allMemories = fetchMemories(userId, groupId);

        if (allMemories.isEmpty()) {
            return List.of();
        }

        List<MemoryType> relevantTypes = inferRelevantTypes(userQuery);

        List<MemoryPackageEntity> filtered = allMemories.stream()
                .filter(MemoryPackageEntity::isActive)
                .filter(m -> m.getImportance() >= 5)
                .toList();

        List<ScoredMemoryEntity> scored = filtered.stream()
                .map(m -> {
                    double relevance = computeRelevance(m, userQuery, relevantTypes);
                    double score = m.getImportance() * 0.7 + relevance * 30.0;
                    return new ScoredMemoryEntity(m, score);
                })
                .sorted(Comparator.comparingDouble(ScoredMemoryEntity::score).reversed())
                .toList();

        List<MemoryPackageEntity> result = new ArrayList<>();
        int totalTokens = 0;
        for (ScoredMemoryEntity sm : scored) {
            int tokens = estimateTokens(sm.entity.getContent());
            if (totalTokens + tokens > MAX_RETRIEVAL_TOKENS) break;
            result.add(sm.entity);
            totalTokens += tokens;
            if (result.size() >= topK) break;
        }

        log.info("[MemoryRetriever] 查询召回: 总记忆{}条, 匹配{}条, 返回{}条, token预算{}",
                allMemories.size(), scored.size(), result.size(), totalTokens);
        return result;
    }

    /**
     * 获取所有用户/群记忆
     */
    private List<MemoryPackageEntity> fetchMemories(String userId, String groupId) {
        List<MemoryPackageEntity> all = new ArrayList<>();
        if (userId != null) {
            all.addAll(repository.findByUserIdOrderByWeightDesc(userId));
        }
        if (groupId != null) {
            all.addAll(repository.findByGroupIdOrderByWeightDesc(groupId));
        }
        if (userId == null && groupId == null) {
            all.addAll(repository.findAll());
        }
        return all.stream()
                .filter(m -> m.getScope() != MemoryScope.PERSONA)
                .collect(Collectors.toList());
    }

    /**
     * 根据用户查询推断需要召回的记忆类型。
     *
     * 例如：
     *   "帮我写代码" → PROJECT, SKILL, PREFERENCE
     *   "我是谁" → PROFILE, RELATION
     *   "上次那个项目" → PROJECT, FACT
     */
    private List<MemoryType> inferRelevantTypes(String query) {
        if (query == null || query.isBlank()) {
            return List.of(MemoryType.values());
        }

        String lower = query.toLowerCase();
        List<MemoryType> types = new ArrayList<>();

        if (containsAny(lower, "我是", "叫我", "称呼", "名字", "昵称", "身份")) {
            types.add(MemoryType.PROFILE);
            types.add(MemoryType.RELATION);
        }
        if (containsAny(lower, "喜欢", "不喜欢", "偏好", "习惯", "经常", "总是", "爱好")) {
            types.add(MemoryType.PREFERENCE);
            types.add(MemoryType.HABIT);
        }
        if (containsAny(lower, "项目", "开发", "写", "代码", "编程", "任务", "工作")) {
            types.add(MemoryType.PROJECT);
            types.add(MemoryType.SKILL);
            types.add(MemoryType.GOAL);
        }
        if (containsAny(lower, "目标", "计划", "打算", "想做", "完成")) {
            types.add(MemoryType.GOAL);
            types.add(MemoryType.PROJECT);
        }
        if (containsAny(lower, "事实", "知道", "了解", "是什么", "定义")) {
            types.add(MemoryType.FACT);
        }
        if (containsAny(lower, "关系", "同事", "朋友", "家人", "谁")) {
            types.add(MemoryType.RELATION);
            types.add(MemoryType.PROFILE);
        }
        if (containsAny(lower, "日程", "安排", "时间", "日期", "会议", "出差")) {
            types.add(MemoryType.SCHEDULE);
        }

        if (types.isEmpty()) {
            return List.of(MemoryType.values());
        }

        Set<MemoryType> unique = new LinkedHashSet<>(types);
        unique.add(MemoryType.PROFILE);
        unique.add(MemoryType.PREFERENCE);
        return new ArrayList<>(unique);
    }

    /**
     * 计算单条记忆与查询的相关性
     */
    private double computeRelevance(MemoryPackageEntity memory, String query,
                                    List<MemoryType> relevantTypes) {
        double score = 0;

        if (relevantTypes.contains(memory.getMemoryType())) {
            score += 5;
        }

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

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private int estimateTokens(String text) {
        return text == null ? 0 : text.length() / 4;
    }

    private record ScoredMemoryEntity(MemoryPackageEntity entity, double score) {}
}