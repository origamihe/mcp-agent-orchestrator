package com.mcp.tools.pipeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 管道执行结果 — 包含每步执行的详细信息和最终输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolPipelineResult {

    private String pipelineId;

    private boolean success;

    /**
     * 每步执行结果，key 为 stepId。
     */
    private Map<String, StepResult> stepResults;

    /**
     * 管道最终输出（最后一步的输出）。
     */
    private Object finalOutput;

    /**
     * 失败时的错误信息。
     */
    private String error;

    /**
     * 总耗时毫秒。
     */
    private long elapsedMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepResult {
        private String stepId;
        private String toolName;
        private boolean success;
        private Object output;
        private String error;
        private long elapsedMs;
    }
}