package com.mcp.common.reflection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 反思上下文 — 供 AgentRuntime Prompt 组装时注入的反思信息。
 */
public class ReflectionContext {

    private List<ReflectionEntry> recentReflections;
    private List<ReflectionEntry> relevantFailures;
    private List<ReflectionEntry> relevantSkills;
    private int totalReflections;
    private Instant generatedAt;

    public ReflectionContext() {
        this.recentReflections = new ArrayList<>();
        this.relevantFailures = new ArrayList<>();
        this.relevantSkills = new ArrayList<>();
        this.generatedAt = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ReflectionContext ctx = new ReflectionContext();

        public Builder recentReflections(List<ReflectionEntry> reflections) { ctx.recentReflections = reflections; return this; }
        public Builder relevantFailures(List<ReflectionEntry> failures) { ctx.relevantFailures = failures; return this; }
        public Builder relevantSkills(List<ReflectionEntry> skills) { ctx.relevantSkills = skills; return this; }
        public Builder totalReflections(int total) { ctx.totalReflections = total; return this; }

        public ReflectionContext build() {
            ctx.generatedAt = Instant.now();
            return ctx;
        }
    }

    public String toPromptFragment() {
        if (isEmpty()) return "";

        StringBuilder sb = new StringBuilder(512);

        if (!relevantSkills.isEmpty()) {
            sb.append("【可用技能】\n");
            for (ReflectionEntry s : relevantSkills) {
                sb.append("- ").append(s.getName()).append(": ").append(s.getDescription()).append("\n");
            }
            sb.append("\n");
        }

        if (!relevantFailures.isEmpty()) {
            sb.append("【已知问题模式】\n");
            for (ReflectionEntry f : relevantFailures) {
                sb.append("- ").append(f.getName()).append(": ").append(f.getDescription()).append("\n");
                if (f.getCorrectApproach() != null) {
                    sb.append("  正确做法: ").append(f.getCorrectApproach()).append("\n");
                }
            }
            sb.append("\n");
        }

        if (!recentReflections.isEmpty()) {
            sb.append("【最近反思】\n");
            for (ReflectionEntry r : recentReflections) {
                sb.append("- ").append(r.getDescription()).append("\n");
            }
        }

        return sb.toString();
    }

    public boolean isEmpty() {
        return relevantSkills.isEmpty() && relevantFailures.isEmpty() && recentReflections.isEmpty();
    }

    public List<ReflectionEntry> getRecentReflections() { return recentReflections; }
    public void setRecentReflections(List<ReflectionEntry> recentReflections) { this.recentReflections = recentReflections; }
    public List<ReflectionEntry> getRelevantFailures() { return relevantFailures; }
    public void setRelevantFailures(List<ReflectionEntry> relevantFailures) { this.relevantFailures = relevantFailures; }
    public List<ReflectionEntry> getRelevantSkills() { return relevantSkills; }
    public void setRelevantSkills(List<ReflectionEntry> relevantSkills) { this.relevantSkills = relevantSkills; }
    public int getTotalReflections() { return totalReflections; }
    public void setTotalReflections(int totalReflections) { this.totalReflections = totalReflections; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    /**
     * 反思条目。
     */
    public static class ReflectionEntry {
        private Long id;
        private String name;
        private String description;
        private String correctApproach;
        private String type;
        private int occurrenceCount;

        public ReflectionEntry() {}

        public ReflectionEntry(Long id, String name, String description, String type) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.type = type;
        }

        public static ReflectionEntry of(Long id, String name, String description, String type) {
            return new ReflectionEntry(id, name, description, type);
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCorrectApproach() { return correctApproach; }
        public void setCorrectApproach(String correctApproach) { this.correctApproach = correctApproach; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public int getOccurrenceCount() { return occurrenceCount; }
        public void setOccurrenceCount(int occurrenceCount) { this.occurrenceCount = occurrenceCount; }
    }
}