package com.mcp.engine.trace;

/**
 * 会话事件类型 — 覆盖 Agent 执行管线的完整生命周期。
 *
 * 每个事件类型对应执行链路中的一个关键决策点或数据注入点。
 * 排序按执行管线的时间顺序。
 */
public enum SessionEventType {

    USER_MESSAGE,

    CONTEXT_CLASSIFICATION,

    AGENT_SELECTION,

    CONTEXT_INJECTION,

    SYSTEM_PROMPT,

    TOOL_DECISION,

    TOOL_CALL,

    TOOL_RESULT,

    MEMORY_INJECTION,

    SUBAGENT_SCHEDULE,

    LLM_RESPONSE,

    COMPRESSION,

    CONTRACT_VIOLATION,

    FINAL_RESPONSE
}