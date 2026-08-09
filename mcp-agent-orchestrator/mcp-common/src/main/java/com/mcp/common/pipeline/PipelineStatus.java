package com.mcp.common.pipeline;

/**
 * 流水线状态。
 */
public enum PipelineStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    PARTIAL_SUCCESS
}