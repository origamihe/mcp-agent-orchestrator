package com.mcp.tools.sandbox;

import com.mcp.common.tool.ToolRiskLevel;

/**
 * 沙箱策略 — 根据 ToolRiskLevel 决定使用哪种隔离策略。
 *
 * 映射关系：
 * <pre>
 * ToolRiskLevel → 隔离策略
 * L0 (纯计算)      → NONE（直接执行）
 * L1 (只读数据)    → NONE（直接执行）
 * L2 (文件写入)    → WorkspaceSandbox（限制写入路径）
 * L3 (本地程序)    → ProcessSandboxExecutor（进程超时+资源限制）
 * L4 (网络+本地)   → ContainerSandboxExecutor（Docker，未实现）
 * L5 (系统级)      → BLOCKED（默认禁止）
 * </pre>
 */
public class SandboxPolicy {

    private final WorkspaceSandbox workspaceSandbox;
    private final SandboxExecutor processSandbox;

    public SandboxPolicy(WorkspaceSandbox workspaceSandbox, SandboxExecutor processSandbox) {
        this.workspaceSandbox = workspaceSandbox;
        this.processSandbox = processSandbox;
    }

    /**
     * 决策结果 — 包含是否需要沙箱、使用哪种隔离策略。
     */
    public enum Decision {
        NONE,
        WORKSPACE_ISOLATION,
        PROCESS_SANDBOX,
        CONTAINER_SANDBOX,
        BLOCKED
    }

    /**
     * 根据 ToolRiskLevel 决定沙箱策略。
     */
    public Decision decide(ToolRiskLevel riskLevel) {
        return switch (riskLevel) {
            case L0, L1 -> Decision.NONE;
            case L2 -> Decision.WORKSPACE_ISOLATION;
            case L3 -> Decision.PROCESS_SANDBOX;
            case L4 -> Decision.CONTAINER_SANDBOX;
            case L5 -> Decision.BLOCKED;
        };
    }

    /**
     * 获取 Workspace 隔离实例。
     */
    public WorkspaceSandbox getWorkspaceSandbox() {
        return workspaceSandbox;
    }

    /**
     * 获取进程沙箱执行器。
     */
    public SandboxExecutor getProcessSandbox() {
        return processSandbox;
    }
}