package com.mcp.engine.memory;

import com.mcp.core.domain.memory.MemoryCategory;
import com.mcp.core.domain.memory.MemoryScope;
import com.mcp.core.domain.memory.MemoryType;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.repository.MemoryPackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 记忆生命周期编排器 - 串联 Judge → Merge → Save 全流程。
 *
 * 放在 mcp-agent-engine 而非 mcp-core，因为需要同时依赖 mcp-core（DB）和 mcp-llm（LLM判断）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryLifecycleOrchestrator {

    private final MemoryPackageRepository memoryPackageRepository;
    private final MemoryExtractor memoryExtractor;
    private final MemoryEvaluator memoryEvaluator;
    private final MemoryMergeService memoryMergeService;

    /**
     * 记忆生命周期入口：从对话中提取、判断、合并记忆。
     * 在每次对话保存后调用。
     */
    public Mono<Void> processMemoryLifecycle(String sessionId, String userId, String groupId,
                                             String recentConversation) {
        return memoryExtractor.extract(recentConversation)
                .flatMap(candidates -> {
                    if (candidates.isEmpty()) {
                        log.debug("[MemoryLifecycle] 无候选记忆");
                        return Mono.empty();
                    }

                    List<MemoryEvaluator.ScoredMemory> scored = memoryEvaluator.evaluate(candidates);
                    if (scored.isEmpty()) {
                        log.debug("[MemoryLifecycle] 所有候选记忆被过滤（importance<3）");
                        return Mono.empty();
                    }

                    return Mono.fromRunnable(() -> {
                        int created = 0, updated = 0, replaced = 0, merged = 0, dropped = 0, upgraded = 0;
                        for (var s : scored) {
                            MemoryMergeService.MergeResult result =
                                    memoryMergeService.processCandidate(sessionId, userId, groupId, s);

                            switch (result.action()) {
                                case DROP -> dropped++;
                                case NEW -> {
                                    MemoryPackageEntity entity = buildEntityFromScored(
                                            sessionId, userId, groupId, s);
                                    memoryPackageRepository.save(entity);
                                    created++;
                                }
                                case UPDATE -> {
                                    memoryPackageRepository.save(result.newEntity());
                                    updated++;
                                }
                                case REPLACE -> {
                                    memoryPackageRepository.save(result.newEntity());
                                    replaced++;
                                }
                                case MERGE -> {
                                    for (var old : result.toDelete()) {
                                        memoryPackageRepository.delete(old);
                                    }
                                    memoryPackageRepository.save(result.newEntity());
                                    merged++;
                                }
                                case UPGRADE -> {
                                    memoryPackageRepository.save(result.newEntity());
                                    upgraded++;
                                }
                            }
                        }
                        log.info("[MemoryLifecycle] 完成: 新增{} 更新{} 替换{} 合并{} 升级{} 丢弃{} (候选{}条→评分{}条)",
                                created, updated, replaced, merged, upgraded, dropped,
                                candidates.size(), scored.size());
                    });
                })
                .then();
    }

    private MemoryPackageEntity buildEntityFromScored(
            String sessionId, String userId, String groupId,
            MemoryEvaluator.ScoredMemory scored) {
        MemoryPackageEntity entity = new MemoryPackageEntity();
        entity.setSessionId(sessionId);
        entity.setUserId(userId);
        entity.setGroupId(groupId);
        entity.setContent(scored.content());
        entity.setMemoryType(scored.memoryType());
        entity.setImportance(scored.importance());
        entity.setConfidence(scored.confidence());
        entity.setWeight(scored.importance() / 10.0);
        entity.setTtl(scored.ttl());
        entity.setSourceQuote(scored.sourceQuote());
        entity.setScope(MemoryScope.USER);
        entity.setCategory(mapMemoryTypeToCategory(scored.memoryType()));
        entity.setVersion(1);
        entity.setAccessCount(0);
        entity.setUpgradeCount(0);
        entity.setDecayRate(calculateDecayRate(scored.memoryType()));
        return entity;
    }

    private MemoryCategory mapMemoryTypeToCategory(MemoryType type) {
        return switch (type) {
            case PROFILE, RELATION -> MemoryCategory.CONFIRMED_FACTS;
            case PREFERENCE, HABIT -> MemoryCategory.USER_PREFERENCES;
            case GOAL -> MemoryCategory.OPEN_TASKS;
            case PROJECT -> MemoryCategory.PROJECT_CONTEXT;
            case FACT -> MemoryCategory.CONFIRMED_FACTS;
            case SKILL -> MemoryCategory.CONFIRMED_FACTS;
            case SCHEDULE -> MemoryCategory.OPEN_TASKS;
            case TEMPORARY, EVENT -> MemoryCategory.SUMMARY;
        };
    }

    private double calculateDecayRate(MemoryType type) {
        return switch (type.getLifecycle()) {
            case PERMANENT -> 1.0;
            case LONG -> 0.995;
            case MEDIUM -> 0.98;
            case SHORT -> 0.95;
        };
    }
}