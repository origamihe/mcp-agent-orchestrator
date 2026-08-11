package com.mcp.core.service;

import com.mcp.core.domain.prompt.PromptTemplate;
import com.mcp.core.domain.prompt.PromptType;
import com.mcp.core.entity.PromptABResultEntity;
import com.mcp.core.entity.PromptTemplateEntity;
import com.mcp.core.mapper.PromptTemplateMapper;
import com.mcp.core.repository.PromptABResultRepository;
import com.mcp.core.repository.PromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Prompt A/B 测试服务 — 负责变体选择、效果追踪、统计对比。
 *
 * 核心能力：
 * 1. 加权随机选择变体（A/B/n 测试）
 * 2. 记录每次调用的成功率、耗时、Token 用量
 * 3. 支持用户评分反馈
 * 4. 提供变体效果对比数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptABTestService {

    private final PromptTemplateRepository promptRepository;
    private final PromptABResultRepository abResultRepository;
    private final PromptTemplateMapper mapper;

    /**
     * 根据类型加权随机选择变体。
     * 返回 (PromptTemplate, variant) 对。
     */
    public Mono<PromptTemplate> selectVariant(PromptType type) {
        return Mono.fromCallable(() -> {
            List<PromptTemplateEntity> variants = promptRepository.findLatestEnabledVariantsByType(type);
            if (variants.isEmpty()) {
                return null;
            }

            if (variants.size() == 1) {
                return mapper.toDomain(variants.get(0));
            }

            double totalWeight = variants.stream()
                    .mapToDouble(PromptTemplateEntity::getWeight)
                    .sum();
            double random = ThreadLocalRandom.current().nextDouble() * totalWeight;
            double cumulative = 0.0;

            for (PromptTemplateEntity variant : variants) {
                cumulative += variant.getWeight();
                if (random <= cumulative) {
                    log.debug("[ABTest] Selected variant '{}' for type {} (weight={})",
                            variant.getVariant(), type, variant.getWeight());
                    return mapper.toDomain(variant);
                }
            }

            return mapper.toDomain(variants.get(variants.size() - 1));
        });
    }

    /**
     * 记录调用成功。
     */
    @Transactional
    public void recordSuccess(String promptName, String variant, long durationMs, long tokens) {
        PromptABResultEntity result = getOrCreateResult(promptName, variant);
        result.setCallCount(result.getCallCount() + 1);
        result.setSuccessCount(result.getSuccessCount() + 1);
        result.setTotalDurationMs(result.getTotalDurationMs() + durationMs);
        result.setTotalTokens(result.getTotalTokens() + tokens);
        abResultRepository.save(result);
    }

    /**
     * 记录调用失败。
     */
    @Transactional
    public void recordFailure(String promptName, String variant, long durationMs) {
        PromptABResultEntity result = getOrCreateResult(promptName, variant);
        result.setCallCount(result.getCallCount() + 1);
        result.setFailureCount(result.getFailureCount() + 1);
        result.setTotalDurationMs(result.getTotalDurationMs() + durationMs);
        abResultRepository.save(result);
    }

    /**
     * 记录用户评分（0-5）。
     */
    @Transactional
    public void recordRating(String promptName, String variant, double rating) {
        PromptABResultEntity result = getOrCreateResult(promptName, variant);
        double newAvg = (result.getAvgRating() * result.getRatingCount() + rating)
                / (result.getRatingCount() + 1);
        result.setAvgRating(newAvg);
        result.setRatingCount(result.getRatingCount() + 1);
        abResultRepository.save(result);
    }

    /**
     * 获取变体效果对比数据。
     */
    public Mono<List<PromptABResultEntity>> getComparison(String promptName) {
        return Mono.fromCallable(() -> abResultRepository.findByPromptName(promptName));
    }

    /**
     * 获取最优变体（基于成功率 + 平均评分）。
     */
    public Mono<PromptABResultEntity> getBestVariant(String promptName) {
        return Mono.fromCallable(() ->
                abResultRepository.findByPromptName(promptName).stream()
                        .filter(r -> r.getCallCount() > 0)
                        .max((a, b) -> {
                            double scoreA = score(a);
                            double scoreB = score(b);
                            return Double.compare(scoreA, scoreB);
                        })
                        .orElse(null)
        );
    }

    private double score(PromptABResultEntity result) {
        double successRate = result.getCallCount() > 0
                ? (double) result.getSuccessCount() / result.getCallCount()
                : 0.0;
        double avgDuration = result.getCallCount() > 0
                ? (double) result.getTotalDurationMs() / result.getCallCount()
                : Double.MAX_VALUE;
        double durationScore = avgDuration > 0 ? 1000.0 / avgDuration : 0.0;
        return successRate * 0.4 + result.getAvgRating() * 0.4 + durationScore * 0.2;
    }

    private PromptABResultEntity getOrCreateResult(String promptName, String variant) {
        return abResultRepository.findByPromptNameAndVariant(promptName, variant)
                .orElseGet(() -> {
                    PromptABResultEntity entity = new PromptABResultEntity();
                    entity.setPromptName(promptName);
                    entity.setVariant(variant);
                    return entity;
                });
    }
}