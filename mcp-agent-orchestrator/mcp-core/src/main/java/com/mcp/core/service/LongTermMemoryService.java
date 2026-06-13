package com.mcp.core.service;

import com.mcp.core.domain.memory.MemoryCategory;
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

    private static final int MAX_WORKING_CONTEXT_TOKENS = 8000;
    private static final int COMPRESSION_THRESHOLD = 20;

    /**
     * 构建工作上下文 - 将会话相关的记忆包拼装成 LLM 可读的上下文字符串
     */
    public Mono<String> buildWorkingContext(String sessionId) {
        return Mono.fromCallable(() -> {
                    List<MemoryPackageEntity> packages = memoryPackageRepository
                            .findBySessionIdOrderByWeightDesc(sessionId);

                    if (packages.isEmpty()) {
                        return "";
                    }

                    // 增加访问计数
                    for (MemoryPackageEntity pkg : packages) {
                        memoryPackageRepository.incrementAccess(pkg.getId(), LocalDateTime.now());
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("## 长期记忆上下文\n");

                    int totalTokens = 0;
                    for (MemoryPackageEntity pkg : packages) {
                        String entry = formatMemoryEntry(pkg);
                        int entryTokens = estimateTokens(entry);
                        if (totalTokens + entryTokens > MAX_WORKING_CONTEXT_TOKENS) {
                            sb.append("\n*(记忆过多，已截断)*\n");
                            break;
                        }
                        sb.append(entry);
                        totalTokens += entryTokens;
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
            long count = memoryPackageRepository.countBySessionId(sessionId);
            if (count >= COMPRESSION_THRESHOLD) {
                log.info("[Memory] Session {} 记忆包数量 {} 达到压缩阈值 {}，触发压缩",
                        sessionId, count, COMPRESSION_THRESHOLD);
                compressMemory(sessionId);
            }
        }).then();
    }

    /**
     * 记忆压缩 - 按分类合并低权重记忆，生成摘要
     */
    private void compressMemory(String sessionId) {
        List<MemoryPackageEntity> allPackages = memoryPackageRepository
                .findBySessionIdOrderByWeightDesc(sessionId);

        if (allPackages.size() <= COMPRESSION_THRESHOLD) {
            return;
        }

        // 按分类分组
        var groupedByCategory = allPackages.stream()
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
        return String.format("- [%s] %s (权重:%.1f)\n",
                pkg.getCategory().getDisplayName(),
                pkg.getContent(),
                pkg.getWeight());
    }

    private int estimateTokens(String text) {
        return text == null ? 0 : text.length() / 4;
    }
}
