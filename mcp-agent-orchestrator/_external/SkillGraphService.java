package com.mcp.engine.reflection;

import com.mcp.core.domain.memory.SkillDependencyEntity;
import com.mcp.core.domain.memory.SkillEntity;
import com.mcp.core.repository.SkillDependencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillGraphService {

    private final SkillDependencyRepository dependencyRepository;
    private final SkillLibraryService skillLibraryService;

    private static final int MIN_CO_OCCURRENCE = 2;
    private static final double HIGH_CONFIDENCE = 0.6;

    public void recordCoOccurrence(Long skillId1, Long skillId2) {
        if (skillId1 == null || skillId2 == null || skillId1.equals(skillId2)) {
            return;
        }

        List<SkillDependencyEntity> existing = dependencyRepository
                .findDependencyBetween(skillId1, skillId2);
        if (!existing.isEmpty()) {
            SkillDependencyEntity dep = existing.get(0);
            dependencyRepository.incrementCoOccurrence(dep.getId());
            double newConfidence = (double) (dep.getCoOccurrenceCount() + 1)
                    / (dep.getCoOccurrenceCount() + 2);
            dependencyRepository.updateConfidence(dep.getId(), newConfidence);
            log.debug("[SkillGraph] 共现+1: {} ↔ {} (count={})",
                    skillId1, skillId2, dep.getCoOccurrenceCount() + 1);
            return;
        }

        SkillDependencyEntity forward = SkillDependencyEntity.builder()
                .sourceSkillId(skillId1)
                .targetSkillId(skillId2)
                .dependencyType(SkillDependencyEntity.DependencyType.FOLLOWS)
                .coOccurrenceCount(1)
                .confidence(0.5)
                .build();
        dependencyRepository.save(forward);

        SkillDependencyEntity backward = SkillDependencyEntity.builder()
                .sourceSkillId(skillId2)
                .targetSkillId(skillId1)
                .dependencyType(SkillDependencyEntity.DependencyType.FOLLOWS)
                .coOccurrenceCount(1)
                .confidence(0.5)
                .build();
        dependencyRepository.save(backward);

        log.debug("[SkillGraph] 新共现: {} ↔ {}", skillId1, skillId2);
    }

    public void recordCoOccurrences(List<Long> skillIds) {
        if (skillIds == null || skillIds.size() < 2) {
            return;
        }
        for (int i = 0; i < skillIds.size(); i++) {
            for (int j = i + 1; j < skillIds.size(); j++) {
                recordCoOccurrence(skillIds.get(i), skillIds.get(j));
            }
        }
    }

    public List<SkillEntity> getRelatedSkills(List<Long> skillIds, int limit) {
        if (skillIds == null || skillIds.isEmpty()) {
            return List.of();
        }

        List<SkillDependencyEntity> dependencies = dependencyRepository
                .findBySourceSkillIds(skillIds);

        List<SkillDependencyEntity> filtered = dependencies.stream()
                .filter(d -> d.getCoOccurrenceCount() >= MIN_CO_OCCURRENCE)
                .filter(d -> d.getConfidence() >= HIGH_CONFIDENCE)
                .filter(d -> !skillIds.contains(d.getTargetSkillId()))
                .sorted((a, b) -> {
                    int countCompare = Integer.compare(b.getCoOccurrenceCount(), a.getCoOccurrenceCount());
                    if (countCompare != 0) return countCompare;
                    return Double.compare(b.getConfidence(), a.getConfidence());
                })
                .limit(limit)
                .toList();

        Set<Long> seen = new HashSet<>();
        List<SkillEntity> result = new ArrayList<>();
        for (SkillDependencyEntity dep : filtered) {
            if (seen.add(dep.getTargetSkillId())) {
                try {
                    result.add(skillLibraryService.getById(dep.getTargetSkillId()));
                } catch (Exception e) {
                    log.debug("[SkillGraph] 关联 Skill {} 不存在", dep.getTargetSkillId());
                }
            }
        }
        return result;
    }

    public String buildRelatedSkillPrompt(List<SkillEntity> relatedSkills) {
        if (relatedSkills == null || relatedSkills.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 关联技能推荐 (Skill Graph)\n");
        sb.append("以下技能经常与已匹配的技能一起使用，参考执行顺序：\n\n");

        for (int i = 0; i < relatedSkills.size(); i++) {
            SkillEntity s = relatedSkills.get(i);
            sb.append("  ").append(i + 1).append(". ").append(s.getName());
            sb.append(" (成功率: ").append(String.format("%.0f%%", s.getSuccessRate())).append(")\n");
            if (s.getSteps() != null && !s.getSteps().isEmpty()) {
                sb.append("     ").append(s.getSteps()).append("\n");
            }
        }
        return sb.toString();
    }

    public void addPrerequisite(Long sourceSkillId, Long prerequisiteSkillId) {
        SkillDependencyEntity dep = SkillDependencyEntity.builder()
                .sourceSkillId(sourceSkillId)
                .targetSkillId(prerequisiteSkillId)
                .dependencyType(SkillDependencyEntity.DependencyType.PREREQUISITE)
                .coOccurrenceCount(1)
                .confidence(1.0)
                .build();
        dependencyRepository.save(dep);
        log.info("[SkillGraph] 前置依赖: {} → {}", sourceSkillId, prerequisiteSkillId);
    }

    public void addAlternative(Long sourceSkillId, Long alternativeSkillId) {
        SkillDependencyEntity dep = SkillDependencyEntity.builder()
                .sourceSkillId(sourceSkillId)
                .targetSkillId(alternativeSkillId)
                .dependencyType(SkillDependencyEntity.DependencyType.ALTERNATIVE)
                .coOccurrenceCount(1)
                .confidence(1.0)
                .build();
        dependencyRepository.save(dep);
        log.info("[SkillGraph] 替代方案: {} ↔ {}", sourceSkillId, alternativeSkillId);
    }
}