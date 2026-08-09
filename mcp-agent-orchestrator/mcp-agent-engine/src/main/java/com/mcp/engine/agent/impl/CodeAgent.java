package com.mcp.engine.agent.impl;

import com.mcp.engine.agent.Agent;
import com.mcp.engine.agent.LLMRequest;
import com.mcp.engine.agent.card.AgentCard;
import com.mcp.engine.runtime.AgentRuntime;
import com.mcp.llm.client.LlmClient;
import com.mcp.tools.registry.ToolRegistry;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 代码 Agent - 专注于代码生成、分析、审查。
 * 不再硬编码 Prompt，统一使用 LLMRequest 中的 systemPrompt（由 PromptComposer 生成）。
 */
@Slf4j
@Component
public class CodeAgent implements Agent {

    @Setter
    private LlmClient llmClient;
    @Setter
    private ToolRegistry toolRegistry;
    @Setter
    private AgentRuntime agentRuntime;

    @Override
    public String getId() {
        return "code-agent";
    }

    @Override
    public String getName() {
        return "CodeAgent";
    }

    @Override
    public AgentCard getAgentCard() {
        return AgentCard.builder()
                .agentId(getId())
                .agentName(getName())
                .agentType(AgentCard.AgentType.CODE)
                .description("代码生成、分析、审查、重构专用 Agent")
                .skills(List.of("code-generation", "code-review", "code-analysis", "refactoring"))
                .toolNames(List.of("read_file", "write_file", "edit_file", "search_code"))
                .version("1.0.0")
                .build();
    }

    @Override
    public Mono<String> execute(LLMRequest request) {
        log.info("[CodeAgent] Executing: session={}, userMessage={}",
                request.getSessionId(),
                request.getUserMessage() != null
                        ? request.getUserMessage().substring(0, Math.min(50, request.getUserMessage().length()))
                        : "(empty)");
        if (agentRuntime == null) {
            return Mono.just("[CodeAgent] AgentRuntime 未配置，无法执行代码任务。");
        }
        String systemPrompt = request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()
                ? request.getSystemPrompt()
                : "你是一个专业的代码助手。请用中文回答，代码块使用合适的语言标记。";
        return agentRuntime.run(systemPrompt, request.getUserMessage());
    }
}