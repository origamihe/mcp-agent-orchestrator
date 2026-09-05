package com.mcp.engine.trace;

import com.mcp.tools.executor.ToolExecutionListener;
import com.mcp.tools.model.ToolExecutionRequest;
import com.mcp.tools.model.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Trace 录制 ToolExecutionListener — 将工具执行事件记录到 SessionTrace。
 *
 * 通过 ToolExecutionListener 接口注入到 DefaultToolExecutor，
 * 实现跨模块（mcp-tools → mcp-agent-engine）的可观测性。
 *
 * 设计原则：
 * - 使用 SessionTraceHolder 获取当前线程的 SessionTrace（ThreadLocal）
 * - 监听器异常不影响工具执行（try-catch 包裹）
 * - 记录 TOOL_CALL / TOOL_RESULT 事件，与 SearchAgent 中的记录保持一致
 */
@Slf4j
@Component
public class TraceRecordingToolExecutionListener implements ToolExecutionListener {

    @Override
    public void onExecutionStart(ToolExecutionRequest request) {
        SessionTrace trace = SessionTraceHolder.currentOrNull();
        if (trace != null) {
            trace.recordToolCall(
                    request.getToolName(),
                    request.getArguments() != null ? request.getArguments().toString() : "{}",
                    0
            );
        }
    }

    @Override
    public void onExecutionSuccess(ToolExecutionRequest request, ToolExecutionResult result) {
        SessionTrace trace = SessionTraceHolder.currentOrNull();
        if (trace != null) {
            int resultChars = result.data() != null ? result.data().toString().length() : 0;
            trace.recordToolResult(
                    request.getToolName(),
                    true,
                    resultChars,
                    0,
                    null
            );
        }
    }

    @Override
    public void onExecutionFailure(ToolExecutionRequest request, String error, long elapsedMs) {
        SessionTrace trace = SessionTraceHolder.currentOrNull();
        if (trace != null) {
            trace.recordToolResult(
                    request.getToolName(),
                    false,
                    0,
                    0,
                    error
            );
        }
    }

    @Override
    public void onExecutionTimeout(ToolExecutionRequest request, long elapsedMs) {
        SessionTrace trace = SessionTraceHolder.currentOrNull();
        if (trace != null) {
            trace.recordToolResult(
                    request.getToolName(),
                    false,
                    0,
                    0,
                    "Timeout after " + elapsedMs + "ms"
            );
        }
    }
}