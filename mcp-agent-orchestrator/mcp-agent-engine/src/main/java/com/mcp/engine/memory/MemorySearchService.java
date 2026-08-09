package com.mcp.engine.memory;

import com.mcp.common.memory.MemoryContext;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.repository.MemoryPackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆检索服务 — 支持多维度记忆搜索和上下文生成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemorySearchService {

    private final MemoryPackageRepository repository;

    private static final int MAX_HOT_MEMORIES = 10;
    private static final int MAX_RELEVANT_MEMORIES = 5;
    private static final int MAX_RECENT_MEMORIES = 5;

    /**
     * 生成记忆上下文，供 Prompt 注入使用。
     */
    public MemoryContext buildMemoryContext(String sessionId, String userId, String query) {
        List<MemoryPackageEntity> allMemories = repository.findByUserIdOrderByWeightDesc(userId);

        if (allMemories.isEmpty()) {
            log.debug("[MemorySearch] No memories found for userId={}", userId);
            return MemoryContext.builder().totalMemories(0).build();
        }

        List<MemoryPackageEntity> active = allMemories.stream()
                .filter(MemoryPackageEntity::isActive)
                .toList();

        List<MemoryContext.MemoryEntry> hotMemories = active.stream()
                .filter(m -> m.getWeight() >= 70)
                .sorted(Comparator.comparingDouble(MemoryPackageEntity::getWeight).reversed())
                .limit(MAX_HOT_MEMORIES)
                .map(this::toEntry)
                .collect(Collectors.toList());

        List<MemoryContext.MemoryEntry> relevantMemories = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            relevantMemories = active.stream()
                    .filter(m -> isRelevant(m.getContent(), query))
                    .filter(m -> m.getWeight() < 70)
                    .sorted(Comparator.comparingDouble(MemoryPackageEntity::getWeight).reversed())
                    .limit(MAX_RELEVANT_MEMORIES)
                    .map(this::toEntry)
                    .collect(Collectors.toList());
        }

        List<MemoryContext.MemoryEntry> recentMemories = active.stream()
                .sorted(Comparator.comparing(
                        MemoryPackageEntity::getLastAccessedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_RECENT_MEMORIES)
                .map(this::toEntry)
                .collect(Collectors.toList());

        return MemoryContext.builder()
                .hotMemories(hotMemories)
                .relevantMemories(relevantMemories)
                .recentMemories(recentMemories)
                .totalMemories(active.size())
                .build();
    }

    /**
     * 按关键词搜索记忆。
     */
    public List<MemoryContext.MemoryEntry> search(String userId, String keyword, int limit) {
        List<MemoryPackageEntity> all = repository.findByUserIdOrderByWeightDesc(userId);

        return all.stream()
                .filter(MemoryPackageEntity::isActive)
                .filter(m -> m.getContent() != null
                        && m.getContent().toLowerCase().contains(keyword.toLowerCase()))
                .sorted(Comparator.comparingDouble(MemoryPackageEntity::getWeight).reversed())
                .limit(limit > 0 ? limit : 10)
                .map(this::toEntry)
                .collect(Collectors.toList());
    }

    /**
     * 按类型搜索记忆。
     */
    public List<MemoryContext.MemoryEntry> searchByType(String userId, String memoryType, int limit) {
        List<MemoryPackageEntity> all = repository.findByUserIdOrderByWeightDesc(userId);

        return all.stream()
                .filter(MemoryPackageEntity::isActive)
                .filter(m -> m.getMemoryType() != null
                        && m.getMemoryType().name().equalsIgnoreCase(memoryType))
                .sorted(Comparator.comparingDouble(MemoryPackageEntity::getWeight).reversed())
                .limit(limit > 0 ? limit : 10)
                .map(this::toEntry)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定类型的记忆数量。
     */
    public long countByType(String userId, String memoryType) {
        List<MemoryPackageEntity> all = repository.findByUserIdOrderByWeightDesc(userId);
        return all.stream()
                .filter(MemoryPackageEntity::isActive)
                .filter(m -> m.getMemoryType() != null
                        && m.getMemoryType().name().equalsIgnoreCase(memoryType))
                .count();
    }

    private boolean isRelevant(String content, String query) {
        if (content == null || query == null) return false;
        String lowerContent = content.toLowerCase();
        String lowerQuery = query.toLowerCase();

        String[] queryWords = lowerQuery.split("\\s+");
        int matchCount = 0;
        for (String word : queryWords) {
            if (word.length() >= 2 && lowerContent.contains(word)) {
                matchCount++;
            }
        }
        return matchCount > 0;
    }

    private MemoryContext.MemoryEntry toEntry(MemoryPackageEntity entity) {
        String tier = entity.getWeight() >= 70 ? "HOT"
                : entity.getWeight() >= 40 ? "WARM"
                : entity.getWeight() >= 10 ? "COLD" : "ARCHIVED";

        return MemoryContext.MemoryEntry.of(
                entity.getId(),
                entity.getContent(),
                entity.getMemoryType() != null ? entity.getMemoryType().name() : "FACT",
                entity.getImportance(),
                tier);
    }
}