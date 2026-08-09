package com.mcp.common.channel;

/**
 * ActiveContextSource — 当前活跃上下文的来源类型。
 *
 * 让"继续""下一步"等短消息不用通过 Regex 猜测用户意图，
 * 而是直接根据 Session 当前活跃的上下文来源决定加载策略。
 *
 * 例如：
 * - 用户在跑团中 → GAME → 自动加载文档上下文
 * - 用户在搜索结果中 → SEARCH_RESULT → 自动加载搜索上下文
 * - 用户在执行任务中 → TASK → 自动加载任务上下文
 */
public enum ActiveContextSource {

    /** 没有活跃上下文 */
    NONE,

    /** 活跃上下文来自 Artifact（文档/代码/表格等） */
    ARTIFACT,

    /** 活跃上下文来自工作区 */
    WORKSPACE,

    /** 活跃上下文来自长期记忆 */
    MEMORY,

    /** 活跃上下文来自搜索结果 */
    SEARCH_RESULT,

    /** 活跃上下文来自执行中的任务 */
    TASK,

    /** 活跃上下文来自跑团/角色扮演 */
    GAME
}