package com.mcp.common.execution;

/**
 * 统一执行状态 — 覆盖 Tool 调用、Agent 执行、Pipeline 步骤的完整生命周期。
 *
 * 设计原则：
 * - 所有执行组件共享同一套状态语义
 * - 替代 boolean success / String error 等碎片化表达
 * - 支持部分成功、业务错误、超时、拒绝、取消等细粒度状态
 *
 * 状态流转：
 * <pre>
 * PENDING
 *    ↓
 * RUNNING
 *    ↓
 * ├── SUCCESS
 * ├── PARTIAL_SUCCESS
 * ├── BUSINESS_ERROR
 * ├── EXECUTION_ERROR
 * ├── TIMEOUT
 * ├── DENIED
 * └── CANCELLED
 * </pre>
 */
public enum ExecutionStatus {

    PENDING,

    RUNNING,

    WAITING_TOOL,

    SUCCESS,

    PARTIAL_SUCCESS,

    BUSINESS_ERROR,

    EXECUTION_ERROR,

    TIMEOUT,

    DENIED,

    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCESS
                || this == PARTIAL_SUCCESS
                || this == BUSINESS_ERROR
                || this == EXECUTION_ERROR
                || this == TIMEOUT
                || this == DENIED
                || this == CANCELLED;
    }

    public boolean isSuccess() {
        return this == SUCCESS || this == PARTIAL_SUCCESS;
    }

    public boolean isError() {
        return this == BUSINESS_ERROR || this == EXECUTION_ERROR;
    }

    public boolean isFailure() {
        return this == EXECUTION_ERROR || this == TIMEOUT || this == DENIED || this == CANCELLED;
    }
}