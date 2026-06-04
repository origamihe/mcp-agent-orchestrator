package com.mcp.core.domain.chat;

/**
 * 消息角色
 */
public enum MessageRole {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system"),
    TOOL("tool");

    private final String code;

    MessageRole(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}