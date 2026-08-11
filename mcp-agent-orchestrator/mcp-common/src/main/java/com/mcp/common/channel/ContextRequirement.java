package com.mcp.common.channel;

/**
 * ContextRequirement — 上下文加载需求等级。
 *
 * 与 Intent 完全解耦。由 DefaultAgentOrchestrator 根据
 * SessionState + WorkingContext 综合判断，90% 依赖状态，10% 依赖消息。
 *
 * 不负责决定是否走 FULL Pipeline（FULL 由 Intent/Planner 决定）。
 * 只负责确定"需要加载多少上下文"。
 */
public enum ContextRequirement {

    /** 不需要上下文 — 纯聊天、问候 */
    NONE,

    /** 仅需对话历史 */
    CONVERSATION,

    /** 需要文档上下文（增量：Summary + Chunk） */
    DOCUMENT,

    /** 需要工作区上下文 */
    WORKSPACE,

    /** 需要搜索上下文 — 工具调用、ReAct 循环 */
    SEARCH
}