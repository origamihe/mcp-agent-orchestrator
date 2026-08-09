package com.mcp.common.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 多 Agent 上下文 — 供 Prompt 注入的多 Agent 协作信息。
 */
public class MultiAgentContext {

    private List<AgentInfo> availableAgents;
    private List<AgentInfo> matchedAgents;
    private List<String> activeDelegations;
    private int totalAgents;
    private Instant generatedAt;

    public MultiAgentContext() {
        this.availableAgents = new ArrayList<>();
        this.matchedAgents = new ArrayList<>();
        this.activeDelegations = new ArrayList<>();
        this.generatedAt = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final MultiAgentContext ctx = new MultiAgentContext();

        public Builder availableAgents(List<AgentInfo> agents) { ctx.availableAgents = agents; return this; }
        public Builder matchedAgents(List<AgentInfo> agents) { ctx.matchedAgents = agents; return this; }
        public Builder activeDelegations(List<String> delegations) { ctx.activeDelegations = delegations; return this; }
        public Builder totalAgents(int total) { ctx.totalAgents = total; return this; }

        public MultiAgentContext build() {
            ctx.generatedAt = Instant.now();
            return ctx;
        }
    }

    public String toPromptFragment() {
        if (isEmpty()) return "";

        StringBuilder sb = new StringBuilder(512);

        if (!matchedAgents.isEmpty()) {
            sb.append("【可用协作 Agent】\n");
            for (AgentInfo info : matchedAgents) {
                sb.append("- ").append(info.getName());
                sb.append(" (").append(info.getType()).append(")");
                if (info.getSkills() != null && !info.getSkills().isEmpty()) {
                    sb.append(" 技能: ").append(String.join(", ", info.getSkills()));
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (!availableAgents.isEmpty()) {
            sb.append("【在线 Agent 列表】\n");
            for (AgentInfo info : availableAgents) {
                sb.append("- ").append(info.getName()).append(" (").append(info.getType()).append(")\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public boolean isEmpty() {
        return matchedAgents.isEmpty() && availableAgents.isEmpty();
    }

    public List<AgentInfo> getAvailableAgents() { return availableAgents; }
    public void setAvailableAgents(List<AgentInfo> availableAgents) { this.availableAgents = availableAgents; }
    public List<AgentInfo> getMatchedAgents() { return matchedAgents; }
    public void setMatchedAgents(List<AgentInfo> matchedAgents) { this.matchedAgents = matchedAgents; }
    public List<String> getActiveDelegations() { return activeDelegations; }
    public void setActiveDelegations(List<String> activeDelegations) { this.activeDelegations = activeDelegations; }
    public int getTotalAgents() { return totalAgents; }
    public void setTotalAgents(int totalAgents) { this.totalAgents = totalAgents; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    public static class AgentInfo {
        private String id;
        private String name;
        private String type;
        private String description;
        private List<String> skills;
        private double matchScore;

        public AgentInfo() {}

        public static AgentInfo of(String id, String name, String type, List<String> skills) {
            AgentInfo info = new AgentInfo();
            info.id = id;
            info.name = name;
            info.type = type;
            info.skills = skills;
            return info;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getSkills() { return skills; }
        public void setSkills(List<String> skills) { this.skills = skills; }
        public double getMatchScore() { return matchScore; }
        public void setMatchScore(double matchScore) { this.matchScore = matchScore; }
    }
}