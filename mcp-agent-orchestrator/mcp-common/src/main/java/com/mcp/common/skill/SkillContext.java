package com.mcp.common.skill;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 技能上下文 — 供 AgentRuntime Prompt 组装时注入的技能信息。
 */
public class SkillContext {

    private List<SkillEntry> matchedSkills;
    private List<SkillEntry> relatedSkills;
    private List<SkillEntry> highSuccessSkills;
    private int totalActiveSkills;
    private Instant generatedAt;

    public SkillContext() {
        this.matchedSkills = new ArrayList<>();
        this.relatedSkills = new ArrayList<>();
        this.highSuccessSkills = new ArrayList<>();
        this.generatedAt = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final SkillContext ctx = new SkillContext();

        public Builder matchedSkills(List<SkillEntry> skills) { ctx.matchedSkills = skills; return this; }
        public Builder relatedSkills(List<SkillEntry> skills) { ctx.relatedSkills = skills; return this; }
        public Builder highSuccessSkills(List<SkillEntry> skills) { ctx.highSuccessSkills = skills; return this; }
        public Builder totalActiveSkills(int total) { ctx.totalActiveSkills = total; return this; }

        public SkillContext build() {
            ctx.generatedAt = Instant.now();
            return ctx;
        }
    }

    public String toPromptFragment() {
        if (isEmpty()) return "";

        StringBuilder sb = new StringBuilder(512);

        if (!matchedSkills.isEmpty()) {
            sb.append("【可复用技能】\n");
            for (SkillEntry s : matchedSkills) {
                sb.append("- ").append(s.getName());
                sb.append(" (成功率: ").append(String.format("%.0f%%", s.getSuccessRate())).append(")\n");
                if (s.getDescription() != null) sb.append("  ").append(s.getDescription()).append("\n");
                if (s.getSteps() != null) sb.append("  步骤: ").append(s.getSteps()).append("\n");
            }
            sb.append("\n");
        }

        if (!relatedSkills.isEmpty()) {
            sb.append("【关联技能推荐】\n");
            for (SkillEntry s : relatedSkills) {
                sb.append("- ").append(s.getName());
                sb.append(" (成功率: ").append(String.format("%.0f%%", s.getSuccessRate())).append(")\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public boolean isEmpty() {
        return matchedSkills.isEmpty() && relatedSkills.isEmpty() && highSuccessSkills.isEmpty();
    }

    public List<SkillEntry> getMatchedSkills() { return matchedSkills; }
    public void setMatchedSkills(List<SkillEntry> matchedSkills) { this.matchedSkills = matchedSkills; }
    public List<SkillEntry> getRelatedSkills() { return relatedSkills; }
    public void setRelatedSkills(List<SkillEntry> relatedSkills) { this.relatedSkills = relatedSkills; }
    public List<SkillEntry> getHighSuccessSkills() { return highSuccessSkills; }
    public void setHighSuccessSkills(List<SkillEntry> highSuccessSkills) { this.highSuccessSkills = highSuccessSkills; }
    public int getTotalActiveSkills() { return totalActiveSkills; }
    public void setTotalActiveSkills(int totalActiveSkills) { this.totalActiveSkills = totalActiveSkills; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    public static class SkillEntry {
        private Long id;
        private String name;
        private String description;
        private String steps;
        private String fallbackSteps;
        private double successRate;
        private int version;
        private int totalExecutions;

        public SkillEntry() {}

        public static SkillEntry of(Long id, String name, String description, double successRate, int version) {
            SkillEntry e = new SkillEntry();
            e.id = id;
            e.name = name;
            e.description = description;
            e.successRate = successRate;
            e.version = version;
            return e;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getSteps() { return steps; }
        public void setSteps(String steps) { this.steps = steps; }
        public String getFallbackSteps() { return fallbackSteps; }
        public void setFallbackSteps(String fallbackSteps) { this.fallbackSteps = fallbackSteps; }
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public int getTotalExecutions() { return totalExecutions; }
        public void setTotalExecutions(int totalExecutions) { this.totalExecutions = totalExecutions; }
    }
}