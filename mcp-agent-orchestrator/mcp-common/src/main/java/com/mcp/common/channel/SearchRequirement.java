package com.mcp.common.channel;

/**
 * 搜索需求级别 — 在执行层决定"是否必须搜索"，而非依赖 LLM Prompt 自行判断。
 *
 * 语义：
 * <ul>
 *   <li>{@link #NONE} — 明确不需要外部搜索（如解释 HashMap 原理）</li>
 *   <li>{@link #OPTIONAL} — 搜索可能有帮助，但不是执行前提（如比较技术方案）</li>
 *   <li>{@link #REQUIRED} — 必须获得外部实时/最新/事实性信息后才能回答（如今天有什么新闻）</li>
 * </ul>
 *
 * 关键 invariant：
 * <pre>
 * SearchRequirement.REQUIRED + ToolCall.isEmpty() = 不能正常生成最终事实性回答
 * </pre>
 *
 * 不要把 SearchRequirement 与 Tool Authorization 混在一起。
 * SearchRequirement 表示"这个请求必须完成搜索"，
 * Tool Authorization 表示"当前 Agent 是否被允许使用搜索工具"。
 */
public enum SearchRequirement {

    NONE,

    OPTIONAL,

    REQUIRED
}