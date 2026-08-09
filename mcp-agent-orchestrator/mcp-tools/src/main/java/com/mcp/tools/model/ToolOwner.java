package com.mcp.tools.model;

/**
 * 工具的架构归属（Capability Domain），而非用户权限。
 * 描述该工具属于哪个子系统维护，用于 Planner 的 Capability Query。
 */
public enum ToolOwner {

    WORKSPACE("工作区", "文件系统、项目结构、Git状态等"),
    IDE("IDE", "编辑器、诊断、符号导航、重构等"),
    MEMORY("记忆", "长期记忆、工作上下文、记忆压缩等"),
    REFLECTION("反思", "反思生成、失败学习、技能提取等"),
    PLANNER("规划器", "计划生成、任务分解、重规划等"),
    KNOWLEDGE("知识", "网页搜索、技能库、向量检索、Embedding等"),
    SYSTEM("系统", "系统命令、环境配置、进程管理等"),
    USER("用户", "用户自定义工具和扩展");

    private final String displayName;
    private final String description;

    ToolOwner(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}