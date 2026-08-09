package com.mcp.engine.memory;

public enum MemoryTier {

    HOT(70, "热记忆", "高频访问，始终注入上下文"),
    WARM(40, "温记忆", "周期性访问，按需注入"),
    COLD(10, "冷记忆", "低频访问，仅检索时加载"),
    ARCHIVED(0, "归档记忆", "已归档，不参与上下文注入");

    private final int minScore;
    private final String displayName;
    private final String description;

    MemoryTier(int minScore, String displayName, String description) {
        this.minScore = minScore;
        this.displayName = displayName;
        this.description = description;
    }

    public int getMinScore() {
        return minScore;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean shouldInjectToContext() {
        return this == HOT;
    }

    public boolean shouldRetrieve() {
        return this != ARCHIVED;
    }
}