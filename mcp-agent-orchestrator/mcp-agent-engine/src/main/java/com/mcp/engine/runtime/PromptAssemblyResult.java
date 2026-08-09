package com.mcp.engine.runtime;

import com.mcp.core.context.PromptPolicy;
import com.mcp.engine.reflection.PromptEnricher;

/**
 * Prompt 组装结果 — AgentRuntime.assemble() 的输出。
 *
 * 包含：
 * - assembledPrompt：最终渲染后的 System Prompt 字符串
 * - policy：使用的 PromptPolicy
 * - layerCount：参与渲染的层数
 */
public record PromptAssemblyResult(
        String assembledPrompt,
        PromptPolicy policy,
        int layerCount
) {

    /**
     * 将 memoryContext、artifactContext、historyContext 和 enrichment
     * 合并到 assembledPrompt 中，返回最终的 fullPrompt。
     *
     * @param historyContext 对话历史摘要，在 GAME 模式进入 System Prompt 以提升上下文权重
     */
    public String toFullPrompt(String memoryContext, String artifactContext,
                               String historyContext,
                               PromptEnricher.EnrichmentResult enrichment) {
        String result = assembledPrompt;
        if (memoryContext != null && !memoryContext.isEmpty()) {
            result = result + "\n\n" + memoryContext;
        }
        if (artifactContext != null && !artifactContext.isEmpty()) {
            result = result + "\n\n" + artifactContext;
        }
        if (historyContext != null && !historyContext.isEmpty()) {
            result = result + "\n\n【对话历史】\n" + historyContext;
        }
        if (enrichment != null && !enrichment.isEmpty()) {
            result = result + "\n\n" + enrichment.promptText();
        }
        return result;
    }

    /**
     * 创建 PromptAssemblyResult。
     */
    public static PromptAssemblyResult of(String assembledPrompt, PromptPolicy policy, int layerCount) {
        return new PromptAssemblyResult(assembledPrompt, policy, layerCount);
    }
}