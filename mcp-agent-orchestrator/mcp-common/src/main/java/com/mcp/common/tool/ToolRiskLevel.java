package com.mcp.common.tool;

/**
 * 工具风险等级 — 定义 Agent 工具的能力边界和安全策略。
 *
 * 按风险递增分为 6 级：
 * <pre>
 * L0 — 纯计算：不需要任何沙箱
 * L1 — 只读数据：不需要沙箱
 * L2 — 普通文件写入：需要 Workspace 隔离
 * L3 — 执行本地程序：需要 Process 沙箱
 * L4 — 网络 + 本地执行：需要 Container 沙箱
 * L5 — 系统级权限：默认禁止
 * </pre>
 *
 * 当前项目的工具体系主要处于 L0-L2：
 * - web_search / fetch_webpage → L1
 * - file_read / file_write / document_generation → L2
 * - memory / LLM → L0
 *
 * Sandbox 的优先级取决于 Agent 能力边界，而不是对标 DeepSeek Harness。
 * 如果未来 Agent 需要执行 shell/Python/Java/编译等，L3-L4 的 Sandbox 才会变成 P0。
 */
public enum ToolRiskLevel {

    L0("纯计算", "不需要沙箱"),
    L1("只读数据", "不需要沙箱"),
    L2("普通文件写入", "Workspace 隔离"),
    L3("执行本地程序", "Process 沙箱"),
    L4("网络 + 本地执行", "Container 沙箱"),
    L5("系统级权限", "默认禁止");

    private final String description;
    private final String requiredIsolation;

    ToolRiskLevel(String description, String requiredIsolation) {
        this.description = description;
        this.requiredIsolation = requiredIsolation;
    }

    public String getDescription() {
        return description;
    }

    public String getRequiredIsolation() {
        return requiredIsolation;
    }

    public boolean requiresSandbox() {
        return this.ordinal() >= L3.ordinal();
    }

    public boolean isBlocked() {
        return this == L5;
    }
}