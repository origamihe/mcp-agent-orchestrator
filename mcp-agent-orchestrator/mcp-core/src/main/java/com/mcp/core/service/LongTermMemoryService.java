package com.mcp.core.service;

import com.mcp.core.domain.memory.MemoryCategory;
import com.mcp.core.domain.memory.MemoryScope;
import com.mcp.core.domain.memory.MemoryType;
import com.mcp.common.identity.MemoryIdentity;
import com.mcp.common.identity.UserRole;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.repository.MemoryPackageRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 长期记忆服务 - 分层上下文构建。
 *
 * 上下文分层（每层固定 Token 预算）：
 *   Layer 1: Persona（不可变，永久注入）
 *   Layer 2: Identity（PROFILE，Always Inject）
 *   Layer 3: Preference（PREFERENCE, HABIT，Always Inject）
 *   Layer 4: Relationship（RELATION，Always Inject）
 *   Layer 5: Session Summary（压缩摘要）
 *   Layer 6: Episode（语义搜索：FACT/PROJECT/GOAL/SKILL/SCHEDULE/EVENT/TEMPORARY）
 */
@Service
@RequiredArgsConstructor
public class LongTermMemoryService {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryService.class);

    private final MemoryPackageRepository memoryPackageRepository;
    private final MemoryBoundaryGuard memoryBoundaryGuard;
    private final PersonaMemoryStore personaMemoryStore;
    private final MemoryRetriever memoryRetriever;

    private static final int MAX_WORKING_CONTEXT_TOKENS = 8000;
    private static final int COMPRESSION_THRESHOLD = 20;

    private static final int LAYER_IDENTITY_TOKEN_BUDGET = 1000;
    private static final int LAYER_PREFERENCE_TOKEN_BUDGET = 1500;
    private static final int LAYER_RELATION_TOKEN_BUDGET = 800;
    private static final int LAYER_EPISODE_TOKEN_BUDGET = 2500;

    /**
     * 构建分层工作上下文（含用户角色权限检查）。
     *
     * @param identity 记忆身份标识
     * @param userRole 用户角色（OWNER/ADMIN 可覆盖 Persona 边界规则）
     */
    public Mono<String> buildWorkingContext(MemoryIdentity identity, UserRole userRole) {
        return buildWorkingContext(identity, null, userRole);
    }

    public Mono<String> buildWorkingContext(MemoryIdentity identity, String currentQuery, UserRole userRole) {
        return buildWorkingContext(identity.sessionId(), identity.userId(), identity.groupId(), currentQuery, userRole);
    }

    public Mono<String> buildWorkingContext(String sessionId, String userId, String groupId,
                                            String currentQuery, UserRole userRole) {
        long startTime = System.currentTimeMillis();
        log.info("[DIAG-Memory] buildWorkingContext START | sessionId={} | userId={} | groupId={} | hasQuery={}",
                sessionId, userId, groupId, currentQuery != null && !currentQuery.isEmpty());

        return Mono.fromCallable(() -> {
                    StringBuilder sb = new StringBuilder();

                    String personaText = personaMemoryStore.getPersonaMemoryText();
                    if (!personaText.isEmpty()) {
                        sb.append("## 角色设定 (Persona)\n");
                        sb.append(personaText).append("\n\n");
                        log.info("[DIAG-Memory] Persona loaded: {} chars", personaText.length());
                    }

                    long retrieveStart = System.currentTimeMillis();
                    List<MemoryRetriever.MemoryRetrievalResult> retrieved = memoryRetriever
                            .retrieveWithTiers(currentQuery != null ? currentQuery : "",
                                    userId, groupId, 10);
                    long retrieveEnd = System.currentTimeMillis();
                    log.info("[DIAG-Memory] MemoryRetriever.retrieveWithTiers completed in {}ms | resultCount={}",
                            retrieveEnd - retrieveStart, retrieved.size());

                    if (retrieved.isEmpty()) {
                        long totalElapsed = System.currentTimeMillis() - startTime;
                        log.info("[DIAG-Memory] buildWorkingContext END (empty) | totalElapsed={}ms", totalElapsed);
                        return sb.toString();
                    }

                    List<MemoryPackageEntity> memoryEntities = retrieved.stream()
                            .map(MemoryRetriever.MemoryRetrievalResult::memory)
                            .toList();
                    List<MemoryPackageEntity> filtered = memoryBoundaryGuard.filterConflicting(memoryEntities, userRole);

                    log.info("[DIAG-Memory] BoundaryGuard filtered: {} → {} memories", memoryEntities.size(), filtered.size());

                    if (filtered.isEmpty()) {
                        long totalElapsed = System.currentTimeMillis() - startTime;
                        log.info("[DIAG-Memory] buildWorkingContext END (all filtered) | totalElapsed={}ms", totalElapsed);
                        return sb.toString();
                    }

                    var identityMemories = new ArrayList<MemoryPackageEntity>();
                    var preferenceMemories = new ArrayList<MemoryPackageEntity>();
                    var relationMemories = new ArrayList<MemoryPackageEntity>();
                    var episodeMemories = new ArrayList<MemoryPackageEntity>();

                    for (MemoryPackageEntity pkg : filtered) {
                        MemoryType type = pkg.getMemoryType();
                        if (type == MemoryType.PROFILE || type == MemoryType.IDENTITY) {
                            identityMemories.add(pkg);
                        } else if (type == MemoryType.PREFERENCE || type == MemoryType.HABIT) {
                            preferenceMemories.add(pkg);
                        } else if (type == MemoryType.RELATION) {
                            relationMemories.add(pkg);
                        } else {
                            episodeMemories.add(pkg);
                        }
                    }

                    if (!identityMemories.isEmpty()) {
                        sb.append("## 用户身份 (Identity)\n");
                        int tokens = appendLayer(sb, identityMemories, retrieved, LAYER_IDENTITY_TOKEN_BUDGET);
                        log.info("[Memory] Identity 层: {}条, {} tokens", identityMemories.size(), tokens);
                        sb.append("\n");
                    }

                    if (!preferenceMemories.isEmpty()) {
                        sb.append("## 用户偏好 (Preference)\n");
                        int tokens = appendLayer(sb, preferenceMemories, retrieved, LAYER_PREFERENCE_TOKEN_BUDGET);
                        log.info("[Memory] Preference 层: {}条, {} tokens", preferenceMemories.size(), tokens);
                        sb.append("\n");
                    }

                    if (!relationMemories.isEmpty()) {
                        sb.append("## 人际关系 (Relationship)\n");
                        int tokens = appendLayer(sb, relationMemories, retrieved, LAYER_RELATION_TOKEN_BUDGET);
                        log.info("[Memory] Relationship 层: {}条, {} tokens", relationMemories.size(), tokens);
                        sb.append("\n");
                    }

                    if (!episodeMemories.isEmpty()) {
                        sb.append("## 相关记忆 (Episode)\n");
                        int tokens = appendLayer(sb, episodeMemories, retrieved, LAYER_EPISODE_TOKEN_BUDGET);
                        log.info("[Memory] Episode 层: {}条, {} tokens", episodeMemories.size(), tokens);
                    }

                    int totalSize = sb.length();
                    long totalElapsed = System.currentTimeMillis() - startTime;
                    log.info("[DIAG-Memory] 分层上下文构建完成: 总大小={} chars (~{} tokens) | totalElapsed={}ms | identity={} pref={} rel={} ep={}",
                            totalSize, totalSize / 4, totalElapsed,
                            identityMemories.size(), preferenceMemories.size(),
                            relationMemories.size(), episodeMemories.size());
                    return sb.toString();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private int appendLayer(StringBuilder sb, List<MemoryPackageEntity> memories,
                            List<MemoryRetriever.MemoryRetrievalResult> retrieved, int tokenBudget) {
        int totalTokens = 0;
        List<Long> accessedIds = new ArrayList<>();
        for (MemoryPackageEntity pkg : memories) {
            var tierResult = findTierResult(retrieved, pkg);
            String entry = formatMemoryEntryWithTier(pkg, tierResult);
            int entryTokens = estimateTokens(entry);
            if (totalTokens + entryTokens > tokenBudget) {
                sb.append("*(该层记忆过多，已截断)*\n");
                break;
            }
            sb.append(entry);
            totalTokens += entryTokens;
            accessedIds.add(pkg.getId());
        }
        if (!accessedIds.isEmpty()) {
            memoryPackageRepository.batchIncrementAccess(accessedIds, LocalDateTime.now());
        }
        return totalTokens;
    }

    private MemoryRetriever.MemoryRetrievalResult findTierResult(
            List<MemoryRetriever.MemoryRetrievalResult> results, MemoryPackageEntity entity) {
        return results.stream()
                .filter(r -> r.memory().getId().equals(entity.getId()))
                .findFirst()
                .orElse(null);
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

    private String formatMemoryEntryWithTier(MemoryPackageEntity pkg,
                                              MemoryRetriever.MemoryRetrievalResult tierResult) {
        String tierEmoji = "🔹";
        if (tierResult != null) {
            tierEmoji = switch (tierResult.tier()) {
                case HOT -> "🔥";
                case WARM -> "🟡";
                case COLD -> "🔵";
                case ARCHIVED -> "⬜";
            };
        }
        return String.format("%s [%s|P%s] %s\n",
                tierEmoji,
                pkg.getMemoryType() != null ? pkg.getMemoryType().getDisplayName()
                        : pkg.getCategory().getDisplayName(),
                tierResult != null ? String.format("%.0f", tierResult.score()) : "?",
                pkg.getContent());
    }

    private int estimateTokens(String text) {
        return text == null ? 0 : text.length() / 4;
    }
}