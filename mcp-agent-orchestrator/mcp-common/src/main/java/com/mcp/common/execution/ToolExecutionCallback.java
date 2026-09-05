package com.mcp.common.execution;

/**
 * Tool 执行回调 — 用于 Pipeline 执行过程中向外部报告工具调用状态。
 *
 * 设计原则：
 * - 避免 mcp-tools → mcp-agent-engine 的模块依赖
 * - 由调用方（如 DefaultAgentOrchestrator）注入实现
 * - 实现方（如 ExecutionState）通过此接口接收状态更新
 */
public interface ToolExecutionCallback {

    /**
     * 工具调用开始前回调。
     *
     * @param toolCallId 工具调用标识
     */
    void onToolStart(String toolCallId);

    /**
     * 工具调用完成后回调。
     *
     * @param toolCallId 工具调用标识
     * @param success    是否成功
     */
    void onToolComplete(String toolCallId, boolean success);

    /**
     * 空实现 — 当不需要回调时使用。
     */
    ToolExecutionCallback NOOP = new ToolExecutionCallback() {
        @Override
        public void onToolStart(String toolCallId) {}

        @Override
        public void onToolComplete(String toolCallId, boolean success) {}
    };
}