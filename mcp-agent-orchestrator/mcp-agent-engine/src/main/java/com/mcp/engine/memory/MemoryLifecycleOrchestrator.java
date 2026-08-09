package com.mcp.engine.memory;

import com.mcp.common.identity.MemoryIdentity;
import com.mcp.core.domain.memory.MemoryCategory;
import com.mcp.core.domain.memory.MemoryScope;
import com.mcp.core.domain.memory.MemoryType;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.repository.MemoryPackageRepository;
import com.mcp.engine.retry.RetryManager;
import com.mcp.engine.retry.RetryTask;
import com.mcp.llm.client.ChatMessage;
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
    private final MemoryConflictResolver memoryConflictResolver;
    private final RetryManager retryManager;

    private static final int MEMORY_MAX_RETRIES = 3;

    public Mono<Void> processMemoryLifecycle(MemoryIdentity identity,
                                             String recentConversation) {
        return processMemoryLifecycle(identity, recentConversation, null);
    }

    public Mono<Void> processMemoryLifecycle(MemoryIdentity identity,
                                             String recentConversation, String query) {
        String convPreview = recentConversation != null && recentConversation.length() > 200
                ? recentConversation.substring(0, 200) + "..."
                : recentConversation;
        log.info("[MemoryLifecycle] 开始处理 session={} userId={} groupId={} 对话预览: {}",
                identity.sessionId(), identity.userId(), identity.groupId(), convPreview);

        return memoryExtractor.extract(recentConversation)
                .flatMap(candidates -> processCandidates(identity, candidates, query,
                        () -> processMemoryLifecycle(identity, recentConversation, query)));
    }

    /**
     * 基于消息列表的记忆生命周期处理（推荐方式）。
     * 每条消息携带 {@link com.mcp.llm.client.MessageType} 元数据，
     * MemoryExtractor 在 Java 层直接过滤掉 PLAN/TOOL/SYSTEM/TEMPLATE 类型的消息，
     * 只保留 NORMAL 和 SUMMARY 类型用于记忆抽取。
     * 配合 LLM Prompt 中的禁止抽取规则，形成 Java + LLM 双层防护。
     */
    public Mono<Void> processMemoryLifecycle(MemoryIdentity identity,
                                             List<ChatMessage> messages, String query) {
        if (messages == null || messages.isEmpty()) {
            log.info("[MemoryLifecycle] 消息列表为空，跳过 session={}", identity.sessionId());
            return Mono.empty();
        }

        log.info("[MemoryLifecycle] 开始处理 session={} userId={} groupId={} 消息数={}",
                identity.sessionId(), identity.userId(), identity.groupId(), messages.size());

        return memoryExtractor.extractWithMessages(messages)
                .flatMap(candidates -> processCandidates(identity, candidates, query,
                        () -> processMemoryLifecycle(identity, messages, query)));
    }

    private Mono<Void> processCandidates(MemoryIdentity identity,
                                         List<MemoryExtractor.MemoryCandidate> candidates,
                                         String query,
                                         RetryableAction retryAction) {
        if (candidates.isEmpty()) {
            log.info("[MemoryLifecycle] 无候选记忆 — LLM 未抽取到任何结构化记忆");
            return Mono.empty();
        }

        log.info("[MemoryLifecycle] LLM 抽取到 {} 条候选记忆", candidates.size());
        for (var c : candidates) {
            log.info("[MemoryLifecycle] 候选: type={} content=\"{}\" confidence={}",
                    c.memoryType(), c.content(), c.confidence());
        }

        List<MemoryEvaluator.ScoredMemory> scored = query != null
                ? memoryEvaluator.evaluateWithPriority(candidates, query)
                : memoryEvaluator.evaluate(candidates);

        if (scored.isEmpty()) {
            log.info("[MemoryLifecycle] 所有候选记忆被过滤（importance<{}），共{}条候选被丢弃",
                    MemoryEvaluator.MIN_IMPORTANCE_TO_KEEP, candidates.size());
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
            int created = 0, updated = 0, replaced = 0, merged = 0, dropped = 0, upgraded = 0;
            for (var s : scored) {
                MemoryMergeService.MergeResult result =
                        memoryMergeService.processCandidate(identity, s);

                switch (result.action()) {
                    case DROP -> dropped++;
                    case NEW -> {
                        MemoryPackageEntity entity = buildEntityFromScored(
                                identity, s, result.factKey());
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

            resolveConflicts(identity);

            log.info("[MemoryLifecycle] 完成 session={}: 新增{} 更新{} 替换{} 合并{} 升级{} 丢弃{} (候选{}条→评分{}条)",
                    identity.sessionId(), created, updated, replaced, merged, upgraded, dropped,
                    candidates.size(), scored.size());
            return true;
        })
        .then()
        .onErrorResume(e -> {
            if (RetryManager.isRetryable(e)) {
                log.warn("[MemoryLifecycle] LLM 调用失败，提交重试: session={}, error={}",
                        identity.sessionId(), e.getMessage());
                retryManager.submit(RetryTask.builder()
                        .sessionId(identity.sessionId())
                        .userId(identity.userId())
                        .taskType(RetryTask.TaskType.MEMORY_LIFECYCLE)
                        .action(() -> retryAction.execute())
                        .maxRetries(MEMORY_MAX_RETRIES)
                        .build());
            } else {
                log.error("[MemoryLifecycle] 不可重试的错误: session={}, error={}",
                        identity.sessionId(), e.getMessage());
            }
            return Mono.empty();
        });
    }

    @FunctionalInterface
    private interface RetryableAction {
        Mono<Void> execute();
    }

    private void resolveConflicts(MemoryIdentity identity) {
        List<MemoryPackageEntity> recentMemories = memoryPackageRepository
                .findBySessionIdOrderByWeightDesc(identity.sessionId());
        List<MemoryConflictResolver.ConflictGroup> conflicts =
                memoryConflictResolver.detectAndResolve(recentMemories);

        if (!conflicts.isEmpty()) {
            int resolvedCount = 0;
            for (var cg : conflicts) {
                if (cg.resolution() != MemoryConflictResolver.Resolution.NONE) {
                    resolvedCount++;
                    for (MemoryPackageEntity m : cg.conflicting()) {
                        memoryPackageRepository.save(m);
                    }
                }
            }
            if (resolvedCount > 0) {
                log.info("[MemoryLifecycle] 冲突解决: {} 组冲突已处理", resolvedCount);
            }
        }
    }

    private MemoryPackageEntity buildEntityFromScored(
            MemoryIdentity identity,
            MemoryEvaluator.ScoredMemory scored,
            String factKey) {
        MemoryPackageEntity entity = new MemoryPackageEntity();
        entity.setSessionId(identity.sessionId());
        entity.setUserId(identity.userId());
        entity.setGroupId(identity.groupId());
        entity.setFactKey(factKey);
        entity.setContent(scored.content());
        entity.setMemoryType(scored.memoryType());
        entity.setImportance(scored.importance());
        entity.setConfidence(scored.confidence());
        entity.setWeight(scored.priority() != null
                ? scored.priority().totalScore() / 10.0
                : scored.importance() / 10.0);
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
            case PROFILE, IDENTITY, RELATION -> MemoryCategory.CONFIRMED_FACTS;
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