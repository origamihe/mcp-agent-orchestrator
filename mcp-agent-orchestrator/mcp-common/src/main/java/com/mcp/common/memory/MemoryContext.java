package com.mcp.common.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 记忆上下文 — 供 AgentRuntime Prompt 组装时注入的记忆信息。
 *
 * 包含三层记忆：
 * - hotMemories：高频访问的热记忆（始终注入）
 * - relevantMemories：与当前查询相关的记忆（按需注入）
 * - recentMemories：最近创建/更新的记忆
 */
public class MemoryContext {

    private List<MemoryEntry> hotMemories;
    private List<MemoryEntry> relevantMemories;
    private List<MemoryEntry> recentMemories;
    private int totalMemories;
    private Instant generatedAt;

    public MemoryContext() {
        this.hotMemories = new ArrayList<>();
        this.relevantMemories = new ArrayList<>();
        this.recentMemories = new ArrayList<>();
        this.generatedAt = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final MemoryContext ctx = new MemoryContext();

        public Builder hotMemories(List<MemoryEntry> memories) { ctx.hotMemories = memories; return this; }
        public Builder relevantMemories(List<MemoryEntry> memories) { ctx.relevantMemories = memories; return this; }
        public Builder recentMemories(List<MemoryEntry> memories) { ctx.recentMemories = memories; return this; }
        public Builder totalMemories(int total) { ctx.totalMemories = total; return this; }

        public MemoryContext build() {
            ctx.generatedAt = Instant.now();
            return ctx;
        }
    }

    public String toPromptFragment() {
        if (isEmpty()) return "";

        StringBuilder sb = new StringBuilder(512);
        sb.append("【用户记忆】\n");

        if (!hotMemories.isEmpty()) {
            sb.append("重要信息：\n");
            for (MemoryEntry m : hotMemories) {
                sb.append("- ").append(m.getContent()).append("\n");
            }
        }

        if (!relevantMemories.isEmpty()) {
            sb.append("相关信息：\n");
            for (MemoryEntry m : relevantMemories) {
                sb.append("- ").append(m.getContent()).append("\n");
            }
        }

        if (!recentMemories.isEmpty()) {
            sb.append("最近信息：\n");
            for (MemoryEntry m : recentMemories) {
                sb.append("- ").append(m.getContent()).append("\n");
            }
        }

        return sb.toString();
    }

    public boolean isEmpty() {
        return hotMemories.isEmpty() && relevantMemories.isEmpty() && recentMemories.isEmpty();
    }

    public List<MemoryEntry> getHotMemories() { return hotMemories; }
    public void setHotMemories(List<MemoryEntry> hotMemories) { this.hotMemories = hotMemories; }
    public List<MemoryEntry> getRelevantMemories() { return relevantMemories; }
    public void setRelevantMemories(List<MemoryEntry> relevantMemories) { this.relevantMemories = relevantMemories; }
    public List<MemoryEntry> getRecentMemories() { return recentMemories; }
    public void setRecentMemories(List<MemoryEntry> recentMemories) { this.recentMemories = recentMemories; }
    public int getTotalMemories() { return totalMemories; }
    public void setTotalMemories(int totalMemories) { this.totalMemories = totalMemories; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    /**
     * 记忆条目 — 轻量级记忆摘要，用于上下文注入。
     */
    public static class MemoryEntry {
        private Long id;
        private String content;
        private String type;
        private int importance;
        private String tier;

        public MemoryEntry() {}

        public MemoryEntry(Long id, String content, String type, int importance, String tier) {
            this.id = id;
            this.content = content;
            this.type = type;
            this.importance = importance;
            this.tier = tier;
        }

        public static MemoryEntry of(Long id, String content, String type, int importance, String tier) {
            return new MemoryEntry(id, content, type, importance, tier);
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public int getImportance() { return importance; }
        public void setImportance(int importance) { this.importance = importance; }
        public String getTier() { return tier; }
        public void setTier(String tier) { this.tier = tier; }
    }
}