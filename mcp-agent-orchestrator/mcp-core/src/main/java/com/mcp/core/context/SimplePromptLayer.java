package com.mcp.core.context;

/**
 * 简单 Prompt 层实现 — 一个不可变的数据载体。
 *
 * 所有 ContextProvider 产出的层数据最终都通过此 record 承载，
 * 由 PromptContext.toLayers() 统一构造。
 */
public record SimplePromptLayer(String name, int priority, String content) implements PromptLayer {

    @Override
    public String render() {
        return content != null ? content : "";
    }
}