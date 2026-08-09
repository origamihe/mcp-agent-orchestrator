package com.mcp.engine.memory;

import com.mcp.common.identity.MemoryIdentity;
import com.mcp.core.domain.memory.MemoryType;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.repository.MemoryPackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 记忆合并器 - 在新增记忆前检索相似记忆，决定是创建、更新还是替换。
 *
 * 去重策略（完全规则化，不调用 LLM）：
 * 1. 优先基于 factKey（规范化实体键）查找完全匹配 → UPDATE
 * 2. 其次基于规范化内容的相似度（>85%）判断 → UPDATE
 * 3. 如果 factKey 匹配且 upgrade 计数达到阈值 → UPGRADE（升级记忆类型）
 * 4. 无匹配 → NEW（创建新记忆）
 *
 * 设计原则：Merge 不需要 LLM，factKey + 版本 + 时间戳 + 相似度 即可解决。
 * 这避免了每条记忆额外 7~10 秒的 LLM 调用开销。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryMergeService {

    private final MemoryPackageRepository repository;

    private static final Pattern QUOTE_PATTERN = Pattern.compile("[\"《》'\"\\u201c\\u201d\\u2018\\u2019]");
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[！!，。、；：？\\s]+$");
    private static final Pattern PREFIX_PATTERN = Pattern.compile("^(用户|我|他|她|它)?(最|很|非常|比较)?(喜欢|爱|讨厌|不喜欢|偏好|习惯|经常|总是)?");

    public MergeResult processCandidate(
            MemoryIdentity identity,
            MemoryEvaluator.ScoredMemory scored) {

        if (scored.isLowValue()) {
            log.info("[MemoryMerge] 丢弃低价值记忆: type={} content={} importance={}",
                    scored.memoryType(), scored.content(), scored.importance());
            return MergeResult.drop(scored);
        }

        String normalizedContent = normalizeContent(scored.content());
        String factKey = generateFactKey(scored.memoryType(), normalizedContent);

        Optional<MemoryPackageEntity> sameFactKey = findByIdentityAndFactKey(identity, factKey);
        if (sameFactKey.isPresent()) {
            MemoryPackageEntity existing = sameFactKey.get();
            existing.recordUpgrade();
            if (existing.isUpgradable()) {
                log.info("[MemoryMerge] factKey匹配+升级: {} → {} (factKey={})",
                        scored.content(), existing.getMemoryType(), factKey);
                return MergeResult.upgrade(existing, scored);
            }
            existing.incrementAccess();
            existing.setUpgradeCount(existing.getUpgradeCount() + 1);
            existing.setImportance(Math.max(existing.getImportance(), scored.importance()));
            existing.setConfidence(Math.max(existing.getConfidence(), scored.confidence()));
            existing.boostWeight(5);
            log.info("[MemoryMerge] factKey精确匹配 UPDATE: content={} factKey={} id={}",
                    scored.content(), factKey, existing.getId());
            return MergeResult.update(existing, scored.content(), scored);
        }

        List<MemoryPackageEntity> similar = findSimilarMemories(
                identity, scored.memoryType());

        if (similar.isEmpty()) {
            log.info("[MemoryMerge] 创建新记忆: type={} content={} factKey={} importance={}",
                    scored.memoryType(), scored.content(), factKey, scored.importance());
            return MergeResult.createNew(scored, factKey);
        }

        MemoryPackageEntity closest = similar.get(0);
        String closestNormalized = normalizeContent(closest.getContent());

        if (closest.getMemoryType() == scored.memoryType()
                && isHighSimilarity(closestNormalized, normalizedContent)) {
            closest.recordUpgrade();
            if (closest.isUpgradable()) {
                log.info("[MemoryMerge] 内容相似+升级: {} ({} → {})",
                        scored.content(), scored.memoryType(), closest.getMemoryType());
                return MergeResult.upgrade(closest, scored);
            }
            closest.incrementAccess();
            closest.setUpgradeCount(closest.getUpgradeCount() + 1);
            closest.setImportance(Math.max(closest.getImportance(), scored.importance()));
            closest.setConfidence(Math.max(closest.getConfidence(), scored.confidence()));
            closest.boostWeight(5);
            closest.setFactKey(factKey);
            log.info("[MemoryMerge] 规范化内容相似 UPDATE: type={} content={} id={}",
                    scored.memoryType(), scored.content(), closest.getId());
            return MergeResult.update(closest, scored.content(), scored);
        }

        log.info("[MemoryMerge] 无匹配，创建新记忆: type={} content={} factKey={} importance={}",
                scored.memoryType(), scored.content(), factKey, scored.importance());
        return MergeResult.createNew(scored, factKey);
    }

    public static String normalizeContent(String content) {
        if (content == null) return "";
        String result = QUOTE_PATTERN.matcher(content).replaceAll("");
        result = PREFIX_PATTERN.matcher(result).replaceAll("");
        result = PUNCTUATION_PATTERN.matcher(result).replaceAll("");
        result = result.toLowerCase().trim();
        result = result.replaceAll("\\s+", " ");
        return result;
    }

    public static String generateFactKey(MemoryType type, String normalizedContent) {
        if (normalizedContent == null || normalizedContent.isBlank()) return null;
        String prefix = switch (type) {
            case PREFERENCE -> "pref:";
            case PROFILE -> "prof:";
            case IDENTITY -> "id:";
            case RELATION -> "rel:";
            case HABIT -> "hab:";
            case GOAL -> "goal:";
            case PROJECT -> "proj:";
            case FACT -> "fact:";
            case SKILL -> "sk:";
            case SCHEDULE -> "sched:";
            case EVENT -> "evt:";
            case TEMPORARY -> "tmp:";
        };
        String key = normalizedContent.replaceAll("[^a-z0-9\\u4e00-\\u9fff_]", "_");
        key = key.replaceAll("_+", "_").replaceAll("^_|_$", "");
        if (key.length() > 150) {
            key = key.substring(0, 150);
        }
        return prefix + key;
    }

    private Optional<MemoryPackageEntity> findByIdentityAndFactKey(MemoryIdentity identity, String factKey) {
        if (factKey == null || factKey.isBlank()) return Optional.empty();
        if (identity.hasUserId()) {
            return repository.findByUserIdAndFactKeyAndIsActiveTrue(identity.userId(), factKey);
        }
        if (identity.groupId() != null) {
            return repository.findByGroupIdAndFactKeyAndIsActiveTrue(identity.groupId(), factKey);
        }
        return Optional.empty();
    }

    private boolean isHighSimilarity(String normalizedOld, String normalizedNew) {
        if (normalizedOld == null || normalizedNew == null) return false;
        if (normalizedOld.equals(normalizedNew)) return true;
        int maxLen = Math.max(normalizedOld.length(), normalizedNew.length());
        if (maxLen == 0) return false;
        int distance = levenshteinDistance(normalizedOld, normalizedNew);
        double similarity = 1.0 - (double) distance / maxLen;
        return similarity > 0.85;
    }

    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    private List<MemoryPackageEntity> findSimilarMemories(
            MemoryIdentity identity, MemoryType memoryType) {
        if (identity.hasUserId()) {
            return repository.findBySessionIdAndUserIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(
                    identity.sessionId(), identity.userId(), memoryType);
        }
        return repository.findBySessionIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(
                identity.sessionId(), memoryType);
    }

    public record MergeResult(
            MergeAction action,
            MemoryPackageEntity newEntity,
            List<MemoryPackageEntity> toDelete,
            MemoryEvaluator.ScoredMemory scored,
            String factKey
    ) {
        public String content() {
            return scored != null ? scored.content() : null;
        }

        public static MergeResult drop(MemoryEvaluator.ScoredMemory s) {
            return new MergeResult(MergeAction.DROP, null, List.of(), s, null);
        }

        public static MergeResult createNew(MemoryEvaluator.ScoredMemory s, String factKey) {
            return new MergeResult(MergeAction.NEW, null, List.of(), s, factKey);
        }

        public static MergeResult update(MemoryPackageEntity target, String newContent,
                                         MemoryEvaluator.ScoredMemory s) {
            target.setContent(newContent);
            target.setVersion(target.getVersion() + 1);
            target.setImportance(Math.max(target.getImportance(), s.importance()));
            target.setConfidence(Math.max(target.getConfidence(), s.confidence()));
            return new MergeResult(MergeAction.UPDATE, target, List.of(), s, target.getFactKey());
        }

        public static MergeResult replace(MemoryPackageEntity old, String newContent,
                                          MemoryEvaluator.ScoredMemory s) {
            old.setContent(newContent);
            old.setVersion(old.getVersion() + 1);
            old.setImportance(s.importance());
            old.setConfidence(s.confidence());
            old.setWeight(s.importance() / 10.0);
            old.setMemoryType(s.memoryType());
            old.setSourceQuote(s.sourceQuote());
            return new MergeResult(MergeAction.REPLACE, old, List.of(), s, old.getFactKey());
        }

        public static MergeResult merge(List<MemoryPackageEntity> oldList, String newContent,
                                        MemoryEvaluator.ScoredMemory s, String factKey) {
            MemoryPackageEntity merged = new MemoryPackageEntity();
            merged.setContent(newContent);
            merged.setFactKey(factKey);
            merged.setMemoryType(s.memoryType());
            merged.setImportance(s.importance());
            merged.setConfidence(s.confidence());
            merged.setWeight(s.importance() / 10.0);
            merged.setVersion(1);
            merged.setAccessCount(0);
            merged.setSourceQuote(s.sourceQuote());
            return new MergeResult(MergeAction.MERGE, merged, oldList, s, factKey);
        }

        public static MergeResult upgrade(MemoryPackageEntity target,
                                          MemoryEvaluator.ScoredMemory s) {
            target.setImportance(Math.min(100, target.getImportance() + 20));
            target.setConfidence(Math.min(100, target.getConfidence() + 10));
            target.setDecayRate(1.0);
            target.setTtl(null);
            target.setUpgradeCount(0);
            target.boostWeight(10);
            return new MergeResult(MergeAction.UPGRADE, target, List.of(), s, target.getFactKey());
        }

        public enum MergeAction {
            NEW, UPDATE, REPLACE, MERGE, DROP, UPGRADE
        }
    }
}