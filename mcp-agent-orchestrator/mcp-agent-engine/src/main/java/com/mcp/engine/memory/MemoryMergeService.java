package com.mcp.engine.memory;

import com.mcp.core.domain.memory.MemoryType;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.repository.MemoryPackageRepository;
import com.mcp.llm.client.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 记忆合并器 - 在新增记忆前检索相似记忆，决定是创建、更新还是替换。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryMergeService {

    private final MemoryPackageRepository repository;
    private final LlmClient llmClient;

    private static final String MERGE_PROMPT = """
        你是一个记忆合并判断器。判断新旧记忆之间的关系。

        【旧记忆】
        %s

        【新记忆】
        %s

        【判断规则】
        - NEW: 新记忆与旧记忆无关，应独立创建
        - UPDATE: 新记忆是旧记忆的补充/更新，应合并为一条
        - REPLACE: 新记忆与旧记忆矛盾，应替换旧记忆
        - MERGE: 多条旧记忆描述同一主题，应合并为一条

        【输出格式】
        严格输出 JSON：
        {
            "action": "NEW / UPDATE / REPLACE / MERGE",
            "mergedContent": "合并后的内容（仅 UPDATE/REPLACE/MERGE 时需要）",
            "reason": "判断理由"
        }
        """;

    public MergeResult processCandidate(
            String sessionId, String userId, String groupId,
            MemoryEvaluator.ScoredMemory scored) {

        if (scored.isLowValue()) {
            log.debug("[MemoryMerge] 丢弃低价值记忆: {} (importance={})",
                    scored.content(), scored.importance());
            return MergeResult.drop(scored);
        }

        List<MemoryPackageEntity> similar = findSimilarMemories(
                sessionId, userId, groupId, scored.memoryType());

        if (similar.isEmpty()) {
            return MergeResult.createNew(scored);
        }

        MemoryPackageEntity closest = similar.get(0);

        if (closest.getMemoryType() == scored.memoryType()
                && isHighSimilarity(closest.getContent(), scored.content())) {
            closest.recordUpgrade();
            if (closest.isUpgradable()) {
                log.info("[MemoryMerge] 记忆升级: {} ({} → {})",
                        scored.content(), scored.memoryType(), closest.getMemoryType());
                return MergeResult.upgrade(closest, scored);
            }
            closest.incrementAccess();
            closest.setUpgradeCount(closest.getUpgradeCount() + 1);
            return MergeResult.update(closest, scored.content(), scored);
        }

        String oldMemories = similar.stream()
                .map(m -> "[ID:" + m.getId() + "] " + m.getContent())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        String prompt = MERGE_PROMPT.formatted(oldMemories, scored.content());

        try {
            String response = llmClient.generate(prompt).block();
            String action = extractAction(response);
            String mergedContent = extractMergedContent(response);

            return switch (action) {
                case "NEW" -> MergeResult.createNew(scored);
                case "UPDATE" -> MergeResult.update(similar.get(0), mergedContent, scored);
                case "REPLACE" -> MergeResult.replace(similar.get(0), mergedContent, scored);
                case "MERGE" -> MergeResult.merge(similar, mergedContent, scored);
                default -> MergeResult.createNew(scored);
            };
        } catch (Exception e) {
            log.warn("[MemoryMerge] LLM 判断失败，默认创建新记忆: {}", e.getMessage());
            return MergeResult.createNew(scored);
        }
    }

    private boolean isHighSimilarity(String oldContent, String newContent) {
        if (oldContent == null || newContent == null) return false;
        String a = oldContent.toLowerCase().trim();
        String b = newContent.toLowerCase().trim();
        if (a.equals(b)) return true;
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return false;
        int commonChars = 0;
        for (int i = 0; i < Math.min(a.length(), b.length()); i++) {
            if (a.charAt(i) == b.charAt(i)) commonChars++;
        }
        return (double) commonChars / maxLen > 0.7;
    }

    private List<MemoryPackageEntity> findSimilarMemories(
            String sessionId, String userId, String groupId, MemoryType memoryType) {
        List<MemoryPackageEntity> bySession = repository.findBySessionIdOrderByWeightDesc(sessionId);
        return bySession.stream()
                .filter(m -> m.getMemoryType() == memoryType)
                .filter(MemoryPackageEntity::isActive)
                .filter(m -> userId == null || userId.equals(m.getUserId()))
                .toList();
    }

    private String extractAction(String response) {
        if (response.contains("\"NEW\"")) return "NEW";
        if (response.contains("\"UPDATE\"")) return "UPDATE";
        if (response.contains("\"REPLACE\"")) return "REPLACE";
        if (response.contains("\"MERGE\"")) return "MERGE";
        return "NEW";
    }

    private String extractMergedContent(String response) {
        try {
            int start = response.indexOf("\"mergedContent\"");
            if (start < 0) return null;
            start = response.indexOf("\"", start + 16) + 1;
            int end = response.indexOf("\"", start);
            if (end > start) {
                return response.substring(start, end);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public record MergeResult(
            MergeAction action,
            MemoryPackageEntity newEntity,
            List<MemoryPackageEntity> toDelete,
            MemoryEvaluator.ScoredMemory scored
    ) {
        public static MergeResult drop(MemoryEvaluator.ScoredMemory s) {
            return new MergeResult(MergeAction.DROP, null, List.of(), s);
        }

        public static MergeResult createNew(MemoryEvaluator.ScoredMemory s) {
            return new MergeResult(MergeAction.NEW, null, List.of(), s);
        }

        public static MergeResult update(MemoryPackageEntity target, String newContent,
                                         MemoryEvaluator.ScoredMemory s) {
            target.setContent(newContent);
            target.setVersion(target.getVersion() + 1);
            target.boostWeight(5);
            target.setImportance(Math.max(target.getImportance(), s.importance()));
            target.setConfidence(Math.max(target.getConfidence(), s.confidence()));
            return new MergeResult(MergeAction.UPDATE, target, List.of(), s);
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
            return new MergeResult(MergeAction.REPLACE, old, List.of(), s);
        }

        public static MergeResult merge(List<MemoryPackageEntity> oldList, String newContent,
                                        MemoryEvaluator.ScoredMemory s) {
            MemoryPackageEntity merged = new MemoryPackageEntity();
            merged.setContent(newContent);
            merged.setMemoryType(s.memoryType());
            merged.setImportance(s.importance());
            merged.setConfidence(s.confidence());
            merged.setWeight(s.importance() / 10.0);
            merged.setVersion(1);
            merged.setAccessCount(0);
            merged.setSourceQuote(s.sourceQuote());
            return new MergeResult(MergeAction.MERGE, merged, oldList, s);
        }

        public static MergeResult upgrade(MemoryPackageEntity target,
                                          MemoryEvaluator.ScoredMemory s) {
            target.setImportance(Math.min(100, target.getImportance() + 20));
            target.setConfidence(Math.min(100, target.getConfidence() + 10));
            target.setDecayRate(1.0);
            target.setTtl(null);
            target.setUpgradeCount(0);
            target.boostWeight(10);
            return new MergeResult(MergeAction.UPGRADE, target, List.of(), s);
        }
    }

    public enum MergeAction {
        NEW, UPDATE, REPLACE, MERGE, DROP, UPGRADE
    }
}