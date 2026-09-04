package com.mcp.engine.trace;

/**
 * 会话事件类型 — 覆盖 Agent 执行管线的完整生命周期。
 *
 * 每个事件类型对应执行链路中的一个关键决策点或数据注入点。
 * 排序按执行管线的时间顺序。
 */
public enum SessionEventType {

    REQUEST_RECEIVED,

    USER_MESSAGE,

    CONTEXT_CLASSIFICATION,

    PLAN_CREATED,

    AGENT_SELECTION,

    CONTEXT_BUILT,

    CONTEXT_INJECTION,

    SYSTEM_PROMPT,

    AGENT_STARTED,

    AGENT_ITERATION,

    TOOL_DECISION,

    TOOL_CALL,

    TOOL_RESULT,

    PIPELINE_STEP,

    MEMORY_READ,

    MEMORY_WRITE,

    MEMORY_INJECTION,

    POLICY_DECISION,

    SUBAGENT_SCHEDULE,

    LLM_RESPONSE,

    LLM_CALL,

    COMPRESSION,

    ARTIFACT_CREATED,

    CONTRACT_VIOLATION,

    EXECUTION_COMPLETED,

    FINAL_RESPONSE
}