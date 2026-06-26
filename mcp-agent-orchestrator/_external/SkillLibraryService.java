package com.mcp.engine.reflection;

import com.mcp.core.domain.memory.SkillEntity;
import com.mcp.core.repository.SkillLibraryRepository;
import com.mcp.llm.client.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillLibraryService {

    private final SkillLibraryRepository repository;
    private final LlmClient llmClient;

    private static final double HIGH_SUCCESS_THRESHOLD = 70.0;

    public SkillEntity create(SkillEntity skill) {
        SkillEntity saved = repository.save(skill);
        log.info("[SkillLibrary] 创建 Skill: {} (v{})", saved.getName(), saved.getVersion());
        return saved;
    }

    public SkillEntity evolve(SkillEntity oldSkill, String improvedSteps) {
        if (oldSkill.getEvolvedFromId() != null) {
            oldSkill = repository.findById(oldSkill.getEvolvedFromId()).orElse(oldSkill);
        }
        SkillEntity evolved = oldSkill.evolve(
                oldSkill.getName() + " v" + (oldSkill.getVersion() + 1),
                improvedSteps);
        SkillEntity saved = repository.save(evolved);
        repository.deactivate(oldSkill.getId());
        log.info("[SkillLibrary] Skill 进化: {} (v{} → v{})",
                oldSkill.getName(), oldSkill.getVersion(), saved.getVersion());
        return saved;
    }

    public SkillEntity createOrUpdate(String name, String description, String triggers,
                                       String steps, String fallbackSteps) {
        List<SkillEntity> existing = repository.findActiveByName(name);
        if (!existing.isEmpty()) {
            SkillEntity latest = existing.get(0);
            if (latest.getSuccessRate() < HIGH_SUCCESS_THRESHOLD) {
                return evolve(latest, steps);
            }
            latest.setVersion(latest.getVersion() + 1);
            latest.setSteps(steps);
            latest.setFallbackSteps(fallbackSteps);
            latest.setDescription(description);
            repository.save(latest);
            log.info("[SkillLibrary] 更新 Skill: {} (v{})", latest.getName(), latest.getVersion());
            return latest;
        }

        SkillEntity newSkill = SkillEntity.builder()
                .name(name)
                .description(description)
                .triggers(triggers)
                .steps(steps)
                .fallbackSteps(fallbackSteps)
                .version(1)
                .successRate(0.0)
                .isActive(true)
                .build();
        return repository.save(newSkill);
    }

    public void recordExecution(Long skillId, boolean success) {
        repository.recordExecution(skillId, success ? 1 : 0, success ? 0 : 1);
    }

    public List<SkillEntity> retrieveRelevantSkills(String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return List.of();
        }

        List<SkillEntity> activeSkills = repository.findByIsActiveTrueOrderBySuccessRateDesc();

        List<SkillEntity> matched = activeSkills.stream()
                .filter(s -> matchesTrigger(s, userQuery))
                .sorted((a, b) -> Double.compare(b.getSuccessRate(), a.getSuccessRate()))
                .limit(5)
                .toList();

        log.info("[SkillLibrary] 检索: query='{}' → 匹配 {} 个 Skill (共 {} 个活跃)",
                userQuery.substring(0, Math.min(50, userQuery.length())),
                matched.size(), activeSkills.size());
        return matched;
    }

    public String buildSkillPrompt(List<SkillEntity> skills) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 可复用技能 (Skill Library)\n");
        sb.append("以下是从历史成功经验中提炼的可复用执行模式，优先参考：\n\n");

        for (int i = 0; i < skills.size(); i++) {
            SkillEntity s = skills.get(i);
            sb.append("### Skill ").append(i + 1).append(": ").append(s.getName());
            sb.append(" (成功率: ").append(String.format("%.0f%%", s.getSuccessRate())).append(")\n");
            if (s.getDescription() != null && !s.getDescription().isEmpty()) {
                sb.append("描述: ").append(s.getDescription()).append("\n");
            }
            sb.append("执行步骤: ").append(s.getSteps()).append("\n");
            if (s.getFallbackSteps() != null && !s.getFallbackSteps().isEmpty()) {
                sb.append("降级步骤: ").append(s.getFallbackSteps()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public List<SkillEntity> getHighSuccessSkills() {
        return repository.findHighSuccessSkills(HIGH_SUCCESS_THRESHOLD);
    }

    public SkillEntity getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Skill not found: " + id));
    }

    private boolean matchesTrigger(SkillEntity skill, String query) {
        if (skill.getTriggers() == null || skill.getTriggers().isEmpty()) {
            return false;
        }
        try {
            String triggersJson = skill.getTriggers().toLowerCase();
            String lowerQuery = query.toLowerCase();
            String[] triggerWords = triggersJson.replaceAll("[\\[\\]\"]", "").split(",");
            for (String word : triggerWords) {
                String trimmed = word.trim();
                if (!trimmed.isEmpty() && lowerQuery.contains(trimmed)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("[SkillLibrary] 触发词匹配异常: {}", e.getMessage());
        }
        return false;
    }
}