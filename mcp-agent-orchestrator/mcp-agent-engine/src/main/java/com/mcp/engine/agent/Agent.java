package com.mcp.engine.agent;

import com.mcp.engine.agent.card.AgentCard;
import com.mcp.engine.runtime.AgentRuntime;
import com.mcp.llm.client.LlmClient;
import com.mcp.tools.registry.ToolRegistry;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Agent 抽象接口 — 统一使用 LLMRequest 作为唯一入参。
 *
 * 设计原则：
 * 1. execute(LLMRequest) 是唯一强制的执行入口，所有 Agent 实现必须基于此方法
 * 2. LLMRequest 承载了 systemPrompt、userMessage、tools、memory、workspace 等全部上下文
 */
public interface Agent {

    String getId();
    String getName();

    default AgentCard getAgentCard() {
        return AgentCard.builder()
                .agentId(getId())
                .agentName(getName())
                .agentType(AgentCard.AgentType.GENERAL)
                .description("通用 Agent")
                .skills(List.of())
                .toolNames(List.of())
                .version("1.0.0")
                .build();
    }

    /**
     * 统一执行入口 — 所有 Agent 的唯一强制实现方法。
     * LLMRequest 已包含完整的 systemPrompt、userMessage 及所有上下文。
     */
    Mono<String> execute(LLMRequest request);

    void setLlmClient(LlmClient llmClient);
    void setToolRegistry(ToolRegistry toolRegistry);

    /**
     * 设置 AgentRuntime，Agent 通过 Runtime 统一调用 LLM。
     * 默认空实现，子类可覆盖。
     */
    default void setAgentRuntime(AgentRuntime runtime) {
        // 默认空实现，子类按需覆盖
    }
}