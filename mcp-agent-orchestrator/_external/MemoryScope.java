package com.mcp.core.domain.memory;

/**
 * 记忆作用域 - 决定记忆的生命周期和可变性
 *
 * PERSONA: 开发者定义，不可变，永不压缩，永不删除
 * USER:    从对话中学习，按用户隔离，可压缩，可衰减
 * GROUP:   群聊共有记忆，按群隔离，可压缩
 */
public enum MemoryScope {
    PERSONA("人格记忆", "开发者定义，不可变"),
    USER("用户记忆", "从对话中学习，按用户隔离"),
    GROUP("群记忆", "群聊共有，按群隔离");

    private final String displayName;
    private final String description;

    MemoryScope(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    public boolean isImmutable() {
        return this == PERSONA;
    }
}