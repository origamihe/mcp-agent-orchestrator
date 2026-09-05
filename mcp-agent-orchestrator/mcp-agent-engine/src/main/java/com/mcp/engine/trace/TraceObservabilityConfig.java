package com.mcp.engine.trace;

import com.mcp.tools.executor.DefaultToolExecutor;
import com.mcp.tools.executor.ToolExecutionListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Trace 可观测性配置 — 将 TraceRecordingToolExecutionListener 注入到 DefaultToolExecutor。
 *
 * 通过 @PostConstruct 在 Bean 初始化后注册监听器，
 * 确保所有工具执行都被记录到 SessionTrace。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean({DefaultToolExecutor.class, TraceRecordingToolExecutionListener.class})
public class TraceObservabilityConfig {

    private final DefaultToolExecutor toolExecutor;
    private final TraceRecordingToolExecutionListener traceListener;

    @PostConstruct
    public void registerTraceListener() {
        toolExecutor.addListener(traceListener);
        log.info("[TraceObservability] ToolExecutionListener registered on DefaultToolExecutor");
    }
}