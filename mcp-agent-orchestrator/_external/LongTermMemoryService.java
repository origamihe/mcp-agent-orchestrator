package com.mcp.core.service;

import com.mcp.core.domain.memory.MemoryCategory;
import com.mcp.core.domain.memory.MemoryScope;
import com.mcp.core.domain.memory.MemoryType;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.repository.MemoryPackageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 长期记忆服务 - 三层记忆架构的核心编排
 * 原始记录 → 压缩记忆 → 工作上下文
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LongTermMemoryService {

    private final MemoryPackageRepository memoryPackageRepository;
    private final MemoryBoundaryGuard memoryBoundaryGuard;
    private final PersonaMemoryStore personaMemoryStore;
    private final MemoryRetriever memoryRetriever;

    private static final int MAX_WORKING_CONTEXT_TOKENS = 8000;
    private static final int COMPRESSION_THRESHOLD = 20;

    /**
     * 构建分层工作上下文
     * Layer 1: Persona 记忆（不可变，永久注入）
     * Layer 2: User 记忆（按 userId 过滤，经 MemoryBoundaryGuard 校验）
     * Layer 3: Group 记忆（按 groupId 过滤）
     */
    public Mono<String> buildWorkingContext(String sessionId) {
        return buildWorkingContext(sessionId, null, null);
    }

    public Mono<String> buildWorkingContext(String sessionId, String userId, String groupId) {
        return buildWorkingContext(sessionId, userId, groupId, null);
    }

    public Mono<String> buildWorkingContext(String sessionId, String userId, String groupId,
                                            String currentQuery) {
        return Mono.fromCallable(() -> {
                    StringBuilder sb = new StringBuilder();

                    String personaText = personaMemoryStore.getPersonaMemoryText();
                    if (!personaText.isEmpty()) {
                        sb.append(personaText).append("\n");
                    }

                    List<MemoryPackageEntity> retrieved = memoryRetriever.retrieve(
                            currentQuery != null ? currentQuery : "", userId, groupId);

                    List<MemoryPackageEntity> filtered = memoryBoundaryGuard.filterConflicting(retrieved);

                    if (!filtered.isEmpty()) {
                        sb.append("【用户偏好与长期记忆 - 可动态调整】\n");

                        int totalTokens = 0;
                        int count = 0;
                        for (MemoryPackageEntity pkg : filtered) {
                            String entry = formatMemoryEntry(pkg);
                            int entryTokens = estimateTokens(entry);
                            if (totalTokens + entryTokens > MAX_WORKING_CONTEXT_TOKENS) {
                                sb.append("\n*(记忆过多，已截断)*\n");
                                break;
                            }
                            sb.append(entry);
                            totalTokens += entryTokens;
                            count++;

                            memoryPackageRepository.incrementAccess(pkg.getId(), LocalDateTime.now());
                        }

                        if (count > 0) {
                            sb.append("\n*(以上为长期记忆，仅供参考，不改变你的人格设定)*\n");
                        }
                    }

                    return sb.toString();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 检查是否需要进行记忆压缩，并在需要时执行压缩
     */
    public Mono<Void> checkAndCompressIfNeeded(String sessionId) {
        return Mono.fromRunnable(() -> {
            long count = memoryPackageRepository.countBySessionIdExcludingPersona(sessionId);
            if (count >= COMPRESSION_THRESHOLD) {
                log.info("[Memory] Session {} 非Persona记忆包数量 {} 达到压缩阈值 {}，触发压缩",
                        sessionId, count, COMPRESSION_THRESHOLD);
                compressMemory(sessionId);
            }
        }).then();
    }

    /**
     * 记忆压缩 - 只压缩 USER 和 GROUP 作用域的记忆，PERSONA 记忆永不参与压缩。
     */
    private void compressMemory(String sessionId) {
        List<MemoryPackageEntity> allPackages = memoryPackageRepository
                .findBySessionIdOrderByWeightDesc(sessionId);

        // 过滤掉 PERSONA 作用域的记忆
        List<MemoryPackageEntity> compressible = allPackages.stream()
                .filter(p -> p.getScope() != MemoryScope.PERSONA)
                .toList();

        if (compressible.size() <= COMPRESSION_THRESHOLD) {
            return;
        }

        // 按分类分组
        var groupedByCategory = compressible.stream()
                .collect(Collectors.groupingBy(MemoryPackageEntity::getCategory));

        for (var entry : groupedByCategory.entrySet()) {
            List<MemoryPackageEntity> categoryPackages = entry.getValue();
            if (categoryPackages.size() <= 3) {
                continue;
            }

            // 保留权重最高的前3条，其余合并为一条摘要
            var sorted = categoryPackages.stream()
                    .sorted(Comparator.comparingDouble(MemoryPackageEntity::getWeight).reversed())
                    .toList();

            List<MemoryPackageEntity> toKeep = sorted.subList(0, Math.min(3, sorted.size()));
            List<MemoryPackageEntity> toMerge = sorted.subList(toKeep.size(), sorted.size());

            if (!toMerge.isEmpty()) {
                String mergedContent = toMerge.stream()
                        .map(MemoryPackageEntity::getContent)
                        .collect(Collectors.joining(" | "));

                // 删除被合并的记忆包
                for (MemoryPackageEntity pkg : toMerge) {
                    memoryPackageRepository.delete(pkg);
                }

                // 创建新的摘要记忆包
                MemoryPackageEntity summary = new MemoryPackageEntity();
                summary.setSessionId(sessionId);
                summary.setCategory(MemoryCategory.SUMMARY);
                summary.setContent("[压缩摘要] " + mergedContent);
                summary.setScope(MemoryScope.USER);
                summary.setVersion(toKeep.get(0).getVersion() + 1);
                summary.setAccessCount(0);
                summary.setWeight(1.5);
                summary.setCreatedAt(LocalDateTime.now());
                summary.setLastAccessedAt(LocalDateTime.now());
                memoryPackageRepository.save(summary);

                log.info("[Memory] Session {} 分类 {} 压缩完成：{} 条合并为 1 条摘要",
                        sessionId, entry.getKey(), toMerge.size());
            }
        }
    }

    private String formatMemoryEntry(MemoryPackageEntity pkg) {
        return String.format("- [%s] %s (重要性:%d, 权重:%.1f, 访问:%d)\n",
                pkg.getMemoryType() != null ? pkg.getMemoryType().getDisplayName() : pkg.getCategory().getDisplayName(),
                pkg.getContent(),
                pkg.getImportance(),
                pkg.getWeight(),
                pkg.getAccessCount());
    }

    private int estimateTokens(String text) {
        return text == null ? 0 : text.length() / 4;
    }
}