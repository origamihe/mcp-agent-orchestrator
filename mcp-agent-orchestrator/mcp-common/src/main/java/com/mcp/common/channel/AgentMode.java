package com.mcp.common.channel;

/**
 * Agent 运行时模式枚举。
 * 每个模式对应一套不同的 Prompt 层级、角色锁规则和输出约束。
 */
public enum AgentMode {
    CHAT,
    GAME,
    NPC,
    COMPANION,
    CODING,
    WORKFLOW;

    public boolean isRoleMode() {
        return this == GAME || this == NPC;
    }

    public boolean isNonChat() {
        return this != CHAT;
    }
}