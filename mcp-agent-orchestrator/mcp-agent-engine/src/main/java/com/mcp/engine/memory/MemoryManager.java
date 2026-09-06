package com.mcp.engine.memory;

import com.mcp.common.identity.MemoryIdentity;
import com.mcp.common.memory.MemoryContext;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.repository.MemoryPackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * 统一记忆管理器 — 记忆系统的对外门面。
 *
 * 职责：
 * 1. 记忆生命周期管理（remember → extract → evaluate → merge → save）
 * 2. 记忆检索（recall → search → context）
 * 3. 记忆删除（forget）
 * 4. 记忆统计
 *
 * 这是 AgentRuntime 与记忆系统交互的唯一入口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryManager {

    private final MemoryLifecycleOrchestrator lifecycleOrchestrator;
    private final MemorySearchService searchService;
    private final MemoryPackageRepository repository;

    /**
     * 从对话中提取并存储记忆。
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @param groupId   群组 ID（可为 null）
     * @param conversation 对话内容
     * @return 完成信号
     */
    public Mono<Void> remember(String sessionId, String userId, String groupId,
                                String conversation) {
        MemoryIdentity identity = new MemoryIdentity(null, sessionId, userId, groupId, null);
        return lifecycleOrchestrator.processMemoryLifecycle(identity, conversation)
                .doOnSuccess(v -> log.debug("[MemoryManager] Remember completed for session={}", sessionId))
                .doOnError(e -> log.error("[MemoryManager] Remember failed for session={}: {}",
                        sessionId, e.getMessage()));
    }

    /**
     * 从对话中提取并存储记忆（带查询上下文）。
     */
    public Mono<Void> remember(String sessionId, String userId, String groupId,
                                String conversation, String query) {
        MemoryIdentity identity = new MemoryIdentity(null, sessionId, userId, groupId, null);
        return lifecycleOrchestrator.processMemoryLifecycle(identity, conversation, query)
                .doOnSuccess(v -> log.debug("[MemoryManager] Remember completed for session={}", sessionId))
                .doOnError(e -> log.error("[MemoryManager] Remember failed for session={}: {}",
                        sessionId, e.getMessage()));
    }

    /**
     * 召回记忆上下文 — 生成供 Prompt 注入的记忆信息。
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @param query     当前查询（用于相关性匹配）
     * @return 记忆上下文
     */
    public MemoryContext recall(String sessionId, String userId, String query) {
        MemoryContext context = searchService.buildMemoryContext(sessionId, userId, query);
        log.debug("[MemoryManager] Recall: hot={} relevant={} recent={} total={}",
                context.getHotMemories().size(),
                context.getRelevantMemories().size(),
                context.getRecentMemories().size(),
                context.getTotalMemories());
        return context;
    }

    /**
     * 召回记忆上下文（无查询）。
     */
    public MemoryContext recall(String sessionId, String userId) {
        return recall(sessionId, userId, null);
    }

    /**
     * 搜索记忆。
     */
    public List<MemoryContext.MemoryEntry> search(String userId, String keyword, int limit) {
        return searchService.search(userId, keyword, limit);
    }

    /**
     * 按类型搜索记忆。
     */
    public List<MemoryContext.MemoryEntry> searchByType(String userId, String memoryType, int limit) {
        return searchService.searchByType(userId, memoryType, limit);
    }

    /**
     * 获取指定记忆。
     */
    public Optional<MemoryPackageEntity> getMemory(Long memoryId) {
        return repository.findById(memoryId);
    }

    /**
     * 忘记（删除）一条记忆。
     */
    public boolean forget(Long memoryId) {
        Optional<MemoryPackageEntity> existing = repository.findById(memoryId);
        if (existing.isEmpty()) {
            log.warn("[MemoryManager] Forget failed: memory not found id={}", memoryId);
            return false;
        }
        MemoryPackageEntity entity = existing.get();
        entity.setActive(false);
        repository.save(entity);
        log.debug("[MemoryManager] Forgotten: id={} content=\"{}\"", memoryId, entity.getContent());
        return true;
    }

    /**
     * 永久删除一条记忆。
     */
    public boolean delete(Long memoryId) {
        if (!repository.existsById(memoryId)) {
            return false;
        }
        repository.deleteById(memoryId);
        log.debug("[MemoryManager] Deleted: id={}", memoryId);
        return true;
    }

    /**
     * 获取用户记忆总数。
     */
    public long countByUser(String userId) {
        List<MemoryPackageEntity> all = repository.findByUserIdOrderByWeightDesc(userId);
        return all.stream().filter(MemoryPackageEntity::isActive).count();
    }

    /**
     * 获取用户指定类型的记忆数量。
     */
    public long countByType(String userId, String memoryType) {
        return searchService.countByType(userId, memoryType);
    }

    /**
     * 清理过期记忆。
     */
    public int cleanupExpired() {
        List<MemoryPackageEntity> all = repository.findAll();
        int cleaned = 0;
        for (MemoryPackageEntity entity : all) {
            if (entity.shouldBeCollected()) {
                entity.setActive(false);
                repository.save(entity);
                cleaned++;
            }
        }
        if (cleaned > 0) {
            log.debug("[MemoryManager] Cleaned up {} expired memories", cleaned);
        }
        return cleaned;
    }
}