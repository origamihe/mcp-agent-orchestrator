package com.mcp.engine.orchestrator;

/**
 * 协作流水线阶段定义 — 描述流水线中单个 Agent 的执行配置。
 *
 * 模板占位符：
 * <ul>
 *   <li>{@code {userMessage}} — 原始用户消息</li>
 *   <li>{@code {input}} — 前一阶段的输出结果</li>
 * </ul>
 *
 * @param agentName     Agent 注册名称（如 "search-agent", "code-agent", "chat-agent"）
 * @param systemPrompt  System Prompt，定义该阶段的角色和行为
 * @param promptTemplate 用户 Prompt 模板，支持 {@code {userMessage}} 和 {@code {input}} 占位符
 */
public record CollaborationPipelineStage(
        String agentName,
        String systemPrompt,
        String promptTemplate
) {
    public String renderPrompt(String userMessage, String previousStageOutput) {
        return promptTemplate
                .replace("{userMessage}", userMessage)
                .replace("{input}", previousStageOutput != null ? previousStageOutput : "");
    }
}