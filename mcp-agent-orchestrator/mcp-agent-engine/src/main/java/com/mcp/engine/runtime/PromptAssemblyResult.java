package com.mcp.engine.runtime;

import com.mcp.core.context.PromptPolicy;
import com.mcp.engine.reflection.PromptEnricher;
import lombok.extern.slf4j.Slf4j;

/**
 * Prompt 组装结果 — AgentRuntime.assemble() 的输出。
 *
 * 包含：
 * - assembledPrompt：最终渲染后的 System Prompt 字符串
 * - policy：使用的 PromptPolicy
 * - layerCount：参与渲染的层数
 */
@Slf4j
public record PromptAssemblyResult(
        String assembledPrompt,
        PromptPolicy policy,
        int layerCount
) {

    /**
     * 最大 System Prompt 总长度（字符数）。
     * 超过此限制时，按优先级截断：核心 Prompt > 历史 > 记忆 > 制品。
     */
    private static final int MAX_TOTAL_PROMPT_LENGTH = 8000;

    /**
     * 将 memoryContext、artifactContext、historyContext 和 enrichment
     * 合并到 assembledPrompt 中，返回最终的 fullPrompt。
     *
     * 当总长度超过 {@link #MAX_TOTAL_PROMPT_LENGTH} 时，按优先级截断非核心上下文，
     * 优先保留核心 System Prompt 和 enrichment 的完整性。
     *
     * @param historyContext 对话历史摘要，在 GAME 模式进入 System Prompt 以提升上下文权重
     */
    public String toFullPrompt(String memoryContext, String artifactContext,
                               String historyContext,
                               PromptEnricher.EnrichmentResult enrichment) {
        String result = assembledPrompt;

        int coreLength = result != null ? result.length() : 0;
        int memLen = memoryContext != null ? memoryContext.length() : 0;
        int artLen = artifactContext != null ? artifactContext.length() : 0;
        int histLen = historyContext != null ? historyContext.length() : 0;
        int enrichLen = (enrichment != null && !enrichment.isEmpty()) ? enrichment.promptText().length() : 0;

        int estimatedTotal = coreLength + memLen + artLen + histLen + enrichLen;

        if (estimatedTotal <= MAX_TOTAL_PROMPT_LENGTH) {
            return appendAll(result, memoryContext, artifactContext, historyContext, enrichment);
        }

        log.info("[PromptAssembly] Total prompt ({}) exceeds limit ({}), applying truncation: "
                + "core={}, mem={}, art={}, hist={}, enrich={}",
                estimatedTotal, MAX_TOTAL_PROMPT_LENGTH, coreLength, memLen, artLen, histLen, enrichLen);

        int remainingBudget = MAX_TOTAL_PROMPT_LENGTH - coreLength;

        if (enrichment != null && !enrichment.isEmpty()) {
            String enrichText = enrichment.promptText();
            if (enrichText.length() <= remainingBudget) {
                result = result + "\n\n" + enrichText;
                remainingBudget -= enrichText.length();
            } else {
                result = result + "\n\n" + enrichText.substring(0, remainingBudget);
                remainingBudget = 0;
            }
        }

        if (remainingBudget > 0 && historyContext != null && !historyContext.isEmpty()) {
            if (historyContext.length() <= remainingBudget) {
                result = result + "\n\n【对话历史】\n" + historyContext;
                remainingBudget -= historyContext.length();
            } else {
                String truncated = historyContext.substring(0, remainingBudget);
                result = result + "\n\n【对话历史】\n" + truncated;
                remainingBudget = 0;
            }
        }

        if (remainingBudget > 0 && memoryContext != null && !memoryContext.isEmpty()) {
            if (memoryContext.length() <= remainingBudget) {
                result = result + "\n\n" + memoryContext;
                remainingBudget -= memoryContext.length();
            } else {
                String truncated = memoryContext.substring(0, remainingBudget);
                result = result + "\n\n" + truncated;
                remainingBudget = 0;
            }
        }

        if (remainingBudget > 0 && artifactContext != null && !artifactContext.isEmpty()) {
            if (artifactContext.length() <= remainingBudget) {
                result = result + "\n\n" + artifactContext;
            } else {
                result = result + "\n\n" + artifactContext.substring(0, remainingBudget);
            }
        }

        log.info("[PromptAssembly] Truncation complete: final length={}, budget left={}",
                result.length(), remainingBudget);
        return result;
    }

    private String appendAll(String result, String memoryContext, String artifactContext,
                             String historyContext, PromptEnricher.EnrichmentResult enrichment) {
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