package com.mcp.core.context;

/**
 * 上下文提供者 — 负责填充 PromptContext 的某一层或多层。
 *
 * 设计原则：
 * 1. 每个 Provider 只关心自己的数据层
 * 2. 从 BuildContext 中按需读取原始数据
 * 3. 通过 PromptContext.PromptContextBuilder 写入对应字段
 * 4. 如果某层不需要填充（数据不足），直接跳过，不做任何事
 *
 * 未来扩展：
 * - MemoryContextProvider（从 MemoryRetriever 获取）
 * - SkillContextProvider（从 SkillLibrary 获取）
 * - FailureContextProvider（从 FailureLibrary 获取）
 * - PlannerContextProvider（从 Planner 获取）
 */
@FunctionalInterface
public interface ContextProvider {

    /**
     * 从 BuildContext 中提取数据，填充到 PromptContextBuilder。
     * 如果当前 Provider 的条件不满足，直接返回不修改 builder。
     */
    void collect(PromptContext.PromptContextBuilder builder, BuildContext ctx);
}