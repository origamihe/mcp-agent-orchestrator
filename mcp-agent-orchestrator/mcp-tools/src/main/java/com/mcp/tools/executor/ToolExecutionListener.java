package com.mcp.tools.executor;

import com.mcp.tools.model.ToolExecutionRequest;
import com.mcp.tools.model.ToolExecutionResult;

/**
 * Tool 执行监听器 — 跨模块的可观测性接口。
 *
 * 由 mcp-agent-engine 实现，注入到 DefaultToolExecutor 中，
 * 用于在工具执行的关键节点记录 Trace 事件、指标等。
 *
 * 设计原则：
 * - 接口定义在 mcp-tools（低层模块），实现在 mcp-agent-engine（高层模块）
 * - 遵循依赖倒置原则，避免 mcp-tools → mcp-agent-engine 的编译依赖
 */
public interface ToolExecutionListener {

    void onExecutionStart(ToolExecutionRequest request);

    void onExecutionSuccess(ToolExecutionRequest request, ToolExecutionResult result);

    void onExecutionFailure(ToolExecutionRequest request, String error, long elapsedMs);

    void onExecutionTimeout(ToolExecutionRequest request, long elapsedMs);
}