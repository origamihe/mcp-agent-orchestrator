package com.mcp.engine.reflection;

import com.mcp.common.reflection.ReflectionContext;
import com.mcp.core.domain.memory.FailureEntity;
import com.mcp.core.domain.memory.ReflectionLogEntity;
import com.mcp.core.domain.memory.SkillEntity;
import com.mcp.core.repository.FailureLibraryRepository;
import com.mcp.core.repository.ReflectionLogRepository;
import com.mcp.core.repository.SkillLibraryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 统一反思管理器 — 反思系统的对外门面。
 *
 * 职责：
 * 1. 任务评估与反思触发
 * 2. 反思上下文生成（供 Prompt 注入）
 * 3. 失败模式查询与匹配
 * 4. 技能库查询
 * 5. 反思统计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReflectionManager {

    private final TaskEvaluator taskEvaluator;
    private final ReflectionAgent reflectionAgent;
    private final FailureLibraryService failureLibraryService;
    private final SkillLibraryService skillLibraryService;
    private final ReflectionLogRepository reflectionLogRepository;
    private final FailureLibraryRepository failureLibraryRepository;
    private final SkillLibraryRepository skillLibraryRepository;

    /**
     * 评估任务并触发反思（异步）。
     */
    public void evaluateAndReflect(String userRequest, String agentExecution,
                                    List<String> toolsUsed, String sessionId, String userId) {
        taskEvaluator.evaluate(userRequest, agentExecution,
                        toolsUsed != null ? String.join(", ", toolsUsed) : "")
                .subscribe(evaluation -> {
                    if (evaluation.isWorthLearning()) {
                        reflectionAgent.reflect(evaluation, userRequest, agentExecution,
                                toolsUsed, sessionId, userId);
                    }
                });
    }

    /**
     * 生成反思上下文 — 供 Prompt 注入。
     */
    public ReflectionContext buildReflectionContext(String userId, String query) {
        List<SkillEntity> activeSkills = skillLibraryRepository.findByIsActiveTrueOrderBySuccessRateDesc();
        List<FailureEntity> unresolvedFailures = failureLibraryRepository.findByIsResolvedFalseOrderByOccurrenceCountDesc();

        List<ReflectionContext.ReflectionEntry> skillEntries = activeSkills.stream()
                .limit(5)
                .map(s -> ReflectionContext.ReflectionEntry.of(
                        s.getId(), s.getName(), s.getDescription(), "SKILL"))
                .collect(Collectors.toList());

        List<ReflectionContext.ReflectionEntry> failureEntries = unresolvedFailures.stream()
                .limit(5)
                .map(f -> {
                    ReflectionContext.ReflectionEntry e = ReflectionContext.ReflectionEntry.of(
                            f.getId(), f.getTaskPattern(),
                            f.getRootCause() != null ? f.getRootCause() : f.getErrorSignature(),
                            "FAILURE");
                    e.setCorrectApproach(f.getCorrectApproach());
                    e.setOccurrenceCount(f.getOccurrenceCount());
                    return e;
                })
                .collect(Collectors.toList());

        List<ReflectionLogEntity> recentLogs = reflectionLogRepository
                .findByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(5)
                .toList();

        List<ReflectionContext.ReflectionEntry> recentEntries = recentLogs.stream()
                .map(log -> ReflectionContext.ReflectionEntry.of(
                        log.getId(), log.getOutcome() != null ? log.getOutcome().name() : "UNKNOWN",
                        log.getReflection() != null ? log.getReflection() : "",
                        "REFLECTION"))
                .collect(Collectors.toList());

        return ReflectionContext.builder()
                .relevantSkills(skillEntries)
                .relevantFailures(failureEntries)
                .recentReflections(recentEntries)
                .totalReflections(activeSkills.size() + unresolvedFailures.size())
                .build();
    }

    /**
     * 获取活跃技能列表。
     */
    public List<SkillEntity> getActiveSkills() {
        return skillLibraryRepository.findByIsActiveTrueOrderBySuccessRateDesc();
    }

    /**
     * 获取未解决失败模式。
     */
    public List<FailureEntity> getUnresolvedFailures() {
        return failureLibraryRepository.findByIsResolvedFalseOrderByOccurrenceCountDesc();
    }

    /**
     * 标记失败模式为已解决。
     */
    public void markFailureResolved(Long failureId, Long skillId) {
        failureLibraryService.markResolved(failureId, skillId);
        log.info("[ReflectionManager] Failure {} resolved by Skill {}", failureId, skillId);
    }

    /**
     * 统计反思数据。
     */
    public ReflectionStats getStats() {
        List<SkillEntity> activeSkills = skillLibraryRepository.findByIsActiveTrueOrderBySuccessRateDesc();
        List<FailureEntity> unresolved = failureLibraryRepository.findByIsResolvedFalseOrderByOccurrenceCountDesc();
        List<FailureEntity> allFailures = failureLibraryRepository.findAll();

        long resolvedFailures = allFailures.stream()
                .filter(FailureEntity::isResolved)
                .count();

        return new ReflectionStats(
                activeSkills.size(),
                unresolved.size(),
                resolvedFailures,
                (int) reflectionLogRepository.count()
        );
    }

    public record ReflectionStats(
            int totalSkills,
            int unresolvedFailures,
            long resolvedFailures,
            int totalReflections
    ) {
        public double resolutionRate() {
            long total = unresolvedFailures + resolvedFailures;
            return total > 0 ? (double) resolvedFailures / total * 100 : 0;
        }
    }
}