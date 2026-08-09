package com.mcp.engine.reflection;

import com.mcp.core.domain.memory.SkillEntity;
import com.mcp.core.repository.SkillLibraryRepository;
import com.mcp.llm.client.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillLibraryService {

    private final SkillLibraryRepository repository;
    private final LlmClient llmClient;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final double HIGH_SUCCESS_THRESHOLD = 70.0;
    private static final double SKILL_SIMILARITY_THRESHOLD = 0.95;

    public record SkillStepGuidance(
            String skillName,
            double successRate,
            Map<String, Object> suggestedParams,
            String fallbackTool
    ) {}

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

    /**
     * 检查新 Skill 是否与已有 Skill 高度相似（>95%），如果是则跳过创建。
     *
     * @return 如果存在高度相似的 Skill 返回 true，否则 false
     */
    public boolean isTooSimilarToExisting(String newName, String newSteps, String newDescription) {
        List<SkillEntity> activeSkills = repository.findByIsActiveTrueOrderBySuccessRateDesc();
        String normalizedNewName = normalizeForComparison(newName);
        String normalizedNewSteps = normalizeForComparison(newSteps);

        for (SkillEntity existing : activeSkills) {
            String normalizedExistingName = normalizeForComparison(existing.getName());
            double nameSimilarity = calculateSimilarity(normalizedExistingName, normalizedNewName);

            String normalizedExistingSteps = normalizeForComparison(existing.getSteps());
            double stepsSimilarity = calculateSimilarity(normalizedExistingSteps, normalizedNewSteps);

            if (nameSimilarity > SKILL_SIMILARITY_THRESHOLD && stepsSimilarity > 0.8) {
                log.info("[SkillLibrary] 跳过重复 Skill: '{}' 与已有 '{}' 相似度 name={:.1%} steps={:.1%}",
                        newName, existing.getName(), nameSimilarity, stepsSimilarity);
                return true;
            }
        }
        return false;
    }

    private String normalizeForComparison(String text) {
        if (text == null) return "";
        return text.toLowerCase().trim()
                .replaceAll("[\\s\\-_\\.]+", " ")
                .replaceAll("[^a-z0-9\\u4e00-\\u9fff ]", "");
    }

    private double calculateSimilarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int maxLen = Math.max(a.length(), b.length());
        int distance = levenshteinDistance(a, b);
        return 1.0 - (double) distance / maxLen;
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

    public SkillEntity createOrUpdate(String name, String description, String triggers,
                                       String steps, String fallbackSteps) {
        if (isTooSimilarToExisting(name, steps, description)) {
            log.info("[SkillLibrary] 跳过 createOrUpdate: 与已有 Skill 高度相似 — name={}", name);
            return null;
        }

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

    public Optional<SkillStepGuidance> buildStepGuidance(List<SkillEntity> matchedSkills, String toolName) {
        if (matchedSkills == null || matchedSkills.isEmpty() || toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }

        for (SkillEntity skill : matchedSkills) {
            if (skill.getSteps() == null || skill.getSteps().isBlank()) {
                continue;
            }
            try {
                List<Map<String, Object>> stepsList = OBJECT_MAPPER.readValue(
                        skill.getSteps(),
                        OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, Map.class)
                );

                for (Map<String, Object> stepMap : stepsList) {
                    String stepTool = (String) stepMap.getOrDefault("tool", "");
                    if (toolName.equalsIgnoreCase(stepTool)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> params = (Map<String, Object>) stepMap.get("params");
                        String fallback = (String) stepMap.get("fallback");

                        Map<String, Object> suggestedParams = params != null
                                ? new HashMap<>(params) : new HashMap<>();

                        SkillStepGuidance guidance = new SkillStepGuidance(
                                skill.getName(),
                                skill.getSuccessRate(),
                                suggestedParams,
                                fallback
                        );

                        log.info("[SkillLibrary] Step guidance found: skill='{}' → tool='{}' params={}",
                                skill.getName(), toolName, suggestedParams.keySet());
                        return Optional.of(guidance);
                    }
                }
            } catch (Exception e) {
                log.debug("[SkillLibrary] 解析 Skill steps 失败: skill={}, error={}",
                        skill.getName(), e.getMessage());
            }
        }

        return Optional.empty();
    }
}