package com.mcp.engine.skill;

import com.mcp.common.skill.SkillContext;
import com.mcp.core.domain.memory.SkillEntity;
import com.mcp.core.repository.SkillLibraryRepository;
import com.mcp.engine.reflection.LearningBudgetManager;
import com.mcp.engine.reflection.SkillGraphService;
import com.mcp.engine.reflection.SkillLibraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 统一技能管理器 — 技能系统的对外门面。
 *
 * 职责：
 * 1. 技能 CRUD 与进化
 * 2. 技能上下文生成（供 Prompt 注入）
 * 3. 技能检索与匹配
 * 4. 技能执行记录
 * 5. 技能统计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillManager {

    private final SkillLibraryService skillLibraryService;
    private final SkillGraphService skillGraphService;
    private final LearningBudgetManager learningBudgetManager;
    private final SkillLibraryRepository skillLibraryRepository;

    /**
     * 生成技能上下文 — 供 Prompt 注入。
     */
    public SkillContext buildSkillContext(String userQuery) {
        List<SkillEntity> matchedSkills = skillLibraryService.retrieveRelevantSkills(userQuery);

        List<SkillContext.SkillEntry> matchedEntries = toEntries(matchedSkills);

        List<Long> matchedIds = matchedSkills.stream().map(SkillEntity::getId).toList();
        List<SkillEntity> relatedSkills = skillGraphService != null
                ? skillGraphService.getRelatedSkills(matchedIds, 3)
                : List.of();
        List<SkillContext.SkillEntry> relatedEntries = toEntries(relatedSkills);

        List<SkillEntity> highSuccessSkills = skillLibraryService.getHighSuccessSkills();
        List<SkillContext.SkillEntry> highSuccessEntries = highSuccessSkills.stream()
                .limit(5)
                .map(this::toEntry)
                .collect(Collectors.toList());

        List<SkillEntity> allActive = skillLibraryRepository.findByIsActiveTrueOrderBySuccessRateDesc();

        return SkillContext.builder()
                .matchedSkills(matchedEntries)
                .relatedSkills(relatedEntries)
                .highSuccessSkills(highSuccessEntries)
                .totalActiveSkills(allActive.size())
                .build();
    }

    /**
     * 创建或更新技能。
     */
    public SkillEntity createOrUpdate(String name, String description, String triggers,
                                       String steps, String fallbackSteps) {
        return skillLibraryService.createOrUpdate(name, description, triggers, steps, fallbackSteps);
    }

    /**
     * 进化已有技能。
     */
    public SkillEntity evolve(Long skillId, String improvedSteps) {
        SkillEntity existing = skillLibraryRepository.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));
        return skillLibraryService.evolve(existing, improvedSteps);
    }

    /**
     * 记录技能执行结果。
     */
    public void recordExecution(Long skillId, boolean success) {
        skillLibraryService.recordExecution(skillId, success);
    }

    /**
     * 记录技能共现。
     */
    public void recordCoOccurrence(Long skillId1, Long skillId2) {
        skillGraphService.recordCoOccurrence(skillId1, skillId2);
    }

    /**
     * 记录技能共现列表。
     */
    public void recordCoOccurrences(List<Long> skillIds) {
        skillGraphService.recordCoOccurrences(skillIds);
    }

    /**
     * 获取关联技能。
     */
    public List<SkillEntity> getRelatedSkills(List<Long> skillIds, int limit) {
        return skillGraphService.getRelatedSkills(skillIds, limit);
    }

    /**
     * 添加前置依赖。
     */
    public void addPrerequisite(Long sourceSkillId, Long prerequisiteSkillId) {
        skillGraphService.addPrerequisite(sourceSkillId, prerequisiteSkillId);
    }

    /**
     * 添加替代技能。
     */
    public void addAlternative(Long sourceSkillId, Long alternativeSkillId) {
        skillGraphService.addAlternative(sourceSkillId, alternativeSkillId);
    }

    /**
     * 检查是否应该触发学习。
     */
    public boolean shouldLearn(String sessionId, String userRequest) {
        return learningBudgetManager.shouldReflect(sessionId, userRequest);
    }

    /**
     * 记录学习事件。
     */
    public void recordLearning(String sessionId) {
        learningBudgetManager.recordReflection(sessionId);
    }

    /**
     * 获取活跃技能列表。
     */
    public List<SkillEntity> getActiveSkills() {
        return skillLibraryRepository.findByIsActiveTrueOrderBySuccessRateDesc();
    }

    /**
     * 统计技能数据。
     */
    public SkillStats getStats() {
        List<SkillEntity> allSkills = skillLibraryRepository.findAll();
        List<SkillEntity> activeSkills = allSkills.stream()
                .filter(SkillEntity::isActive)
                .toList();

        long evolvedCount = activeSkills.stream()
                .filter(s -> s.getEvolvedFromId() != null)
                .count();

        double avgSuccessRate = activeSkills.stream()
                .mapToDouble(SkillEntity::getSuccessRate)
                .average()
                .orElse(0.0);

        int totalExecutions = activeSkills.stream()
                .mapToInt(SkillEntity::getTotalExecutions)
                .sum();

        return new SkillStats(
                activeSkills.size(),
                allSkills.size() - activeSkills.size(),
                evolvedCount,
                avgSuccessRate,
                totalExecutions
        );
    }

    private List<SkillContext.SkillEntry> toEntries(List<SkillEntity> entities) {
        return entities.stream().map(this::toEntry).collect(Collectors.toList());
    }

    private SkillContext.SkillEntry toEntry(SkillEntity entity) {
        SkillContext.SkillEntry entry = SkillContext.SkillEntry.of(
                entity.getId(), entity.getName(), entity.getDescription(),
                entity.getSuccessRate(), entity.getVersion());
        entry.setSteps(entity.getSteps());
        entry.setFallbackSteps(entity.getFallbackSteps());
        entry.setTotalExecutions(entity.getTotalExecutions());
        return entry;
    }

    public record SkillStats(
            int activeSkills,
            int inactiveSkills,
            long evolvedSkills,
            double avgSuccessRate,
            int totalExecutions
    ) {
        public double evolutionRate() {
            int total = activeSkills + inactiveSkills;
            return total > 0 ? (double) evolvedSkills / total * 100 : 0;
        }
    }
}