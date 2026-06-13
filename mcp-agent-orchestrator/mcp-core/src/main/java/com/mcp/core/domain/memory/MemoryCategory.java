package com.mcp.core.domain.memory;

/**
 * 记忆分类 - 按照用户需求结构化存储
 */
public enum MemoryCategory {
    USER_PREFERENCES("用户长期偏好", "语言风格、格式要求、禁忌等"),
    PROJECT_CONTEXT("任务背景", "项目目标、角色设定、上下文"),
    CONFIRMED_FACTS("已确认事实", "稳定且重要的信息"),
    OPEN_TASKS("未解决问题", "当前卡点、待办事项"),
    DECISION_HISTORY("决策历史", "为什么这样选、弃用了什么方案"),
    IMPORTANT_CONSTRAINTS("约束条件", "重要限制和禁止事项"),
    SUMMARY("对话摘要", "分层摘要第一层"),
    LONG_TERM("超压缩长期记忆", "最稳定的核心信息");

    private final String displayName;
    private final String description;

    MemoryCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}