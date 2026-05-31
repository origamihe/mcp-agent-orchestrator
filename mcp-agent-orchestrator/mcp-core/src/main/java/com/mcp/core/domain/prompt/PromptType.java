package com.mcp.core.domain.prompt;

/**
 * Prompt 类型
 */
public enum PromptType {
    SYSTEM("system"),           // 系统提示词
    TASK("task"),               // 任务提示词
    TOOL_CALLING("tool_calling"), // 工具调用提示
    AGENT_SPECIFIC("agent_specific"), // 特定 Agent 专用
    SUMMARY("summary");         // 历史总结提示

    private final String code;

    PromptType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}