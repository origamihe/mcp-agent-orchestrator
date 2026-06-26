package com.mcp.core.service;

import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.repository.MemoryPackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆垃圾回收器 - 定期清理低质量记忆，执行衰减。
 *
 * 回收策略：
 * 1. 衰减：每日对所有非永久记忆执行 weight *= decayRate
 * 2. 清理：importance < 30 AND 30天未访问 AND accessCount < 3 → 软删除
 * 3. 硬删除：软删除后 7 天仍未恢复 → 物理删除
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryGarbageCollector {

    private final MemoryPackageRepository repository;

    private static final int LOW_IMPORTANCE_THRESHOLD = 30;
    private static final int LOW_ACCESS_THRESHOLD = 3;
    private static final int STALE_DAYS = 30;
    private static final int HARD_DELETE_DAYS = 7;

    /**
     * 每日凌晨 3 点执行衰减
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void dailyDecay() {
        log.info("[MemoryGC] 开始每日衰减...");
        List<MemoryPackageEntity> allActive = repository.findAll()
                .stream()
                .filter(MemoryPackageEntity::isActive)
                .filter(m -> !m.isPermanent())
                .toList();

        int decayedCount = 0;
        int deactivatedCount = 0;

        for (MemoryPackageEntity mem : allActive) {
            double oldWeight = mem.getWeight();
            mem.applyDecay();
            if (mem.getWeight() < oldWeight) {
                decayedCount++;
            }
            if (!mem.isActive()) {
                deactivatedCount++;
            }
            repository.save(mem);
        }

        log.info("[MemoryGC] 衰减完成: {} 条权重降低, {} 条被软删除", decayedCount, deactivatedCount);
    }

    /**
     * 每日凌晨 4 点执行垃圾回收
     */
    @Scheduled(cron = "0 0 4 * * ?")
    @Transactional
    public void collectGarbage() {
        log.info("[MemoryGC] 开始垃圾回收...");
        LocalDateTime staleThreshold = LocalDateTime.now().minusDays(STALE_DAYS);
        LocalDateTime now = LocalDateTime.now();

        List<MemoryPackageEntity> allActive = repository.findAll()
                .stream()
                .filter(MemoryPackageEntity::isActive)
                .filter(m -> !m.isPermanent())
                .toList();

        int ttlExpired = 0;
        int lowValueDeleted = 0;

        for (MemoryPackageEntity mem : allActive) {
            if (mem.isExpired()) {
                mem.setActive(false);
                repository.save(mem);
                ttlExpired++;
                log.debug("[MemoryGC] TTL过期: [{}] {} (ttl={})",
                        mem.getMemoryType(), mem.getContent(), mem.getTtl());
                continue;
            }

            if (mem.getImportance() < LOW_IMPORTANCE_THRESHOLD
                    && mem.getAccessCount() < LOW_ACCESS_THRESHOLD
                    && mem.getLastAccessedAt().isBefore(staleThreshold)) {
                mem.setActive(false);
                repository.save(mem);
                lowValueDeleted++;
                log.debug("[MemoryGC] 低价值清理: [{}] {} (importance={}, access={})",
                        mem.getMemoryType(), mem.getContent(),
                        mem.getImportance(), mem.getAccessCount());
            }
        }

        log.info("[MemoryGC] 垃圾回收完成: TTL过期{}条, 低价值{}条", ttlExpired, lowValueDeleted);
    }

    /**
     * 每周日凌晨 5 点执行硬删除（物理删除软删除超过 7 天的记忆）
     */
    @Scheduled(cron = "0 0 5 * * 0")
    @Transactional
    public void hardDelete() {
        log.info("[MemoryGC] 开始硬删除...");
        LocalDateTime hardDeleteThreshold = LocalDateTime.now().minusDays(HARD_DELETE_DAYS);

        List<MemoryPackageEntity> toHardDelete = repository.findAll()
                .stream()
                .filter(m -> !m.isActive())
                .filter(m -> m.getLastAccessedAt().isBefore(hardDeleteThreshold))
                .toList();

        for (MemoryPackageEntity mem : toHardDelete) {
            repository.delete(mem);
            log.debug("[MemoryGC] 硬删除: [{}] {}", mem.getMemoryType(), mem.getContent());
        }

        if (!toHardDelete.isEmpty()) {
            log.info("[MemoryGC] 硬删除完成: {} 条被物理删除", toHardDelete.size());
        }
    }

    /**
     * 当记忆被重新提及时恢复权重
     */
    public void reinforceMemory(MemoryPackageEntity memory, double boostAmount) {
        memory.boostWeight(boostAmount);
        memory.incrementAccess();
        repository.save(memory);
        log.debug("[MemoryGC] 记忆强化: [{}] {} weight={}", memory.getMemoryType(), memory.getContent(), memory.getWeight());
    }
}