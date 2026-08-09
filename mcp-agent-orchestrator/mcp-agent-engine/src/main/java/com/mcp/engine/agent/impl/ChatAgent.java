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
 * 聊天 Agent - 专注于对话、问答、角色扮演。
 * 不再硬编码 Prompt，统一使用 LLMRequest 中的 systemPrompt（由 PromptComposer 生成）。
 */
@Slf4j
@Component
public class ChatAgent implements Agent {

    @Setter
    private LlmClient llmClient;
    @Setter
    private ToolRegistry toolRegistry;
    @Setter
    private AgentRuntime agentRuntime;

    @Override
    public String getId() {
        return "chat-agent";
    }

    @Override
    public String getName() {
        return "ChatAgent";
    }

    @Override
    public AgentCard getAgentCard() {
        return AgentCard.builder()
                .agentId(getId())
                .agentName(getName())
                .agentType(AgentCard.AgentType.CHAT)
                .description("自然对话、问答、角色扮演专用 Agent")
                .skills(List.of("chat", "qa", "roleplay", "translation", "summarization"))
                .toolNames(List.of())
                .version("1.0.0")
                .build();
    }

    @Override
    public Mono<String> execute(LLMRequest request) {
        log.info("[ChatAgent] Executing: session={}, userMessage={}",
                request.getSessionId(),
                request.getUserMessage() != null
                        ? request.getUserMessage().substring(0, Math.min(50, request.getUserMessage().length()))
                        : "(empty)");
        if (agentRuntime == null) {
            return Mono.just("[ChatAgent] AgentRuntime 未配置，无法执行对话任务。");
        }
        String systemPrompt = request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()
                ? request.getSystemPrompt()
                : "你是一个友好、专业的对话助手。请用中文回答，语气亲切自然。";
        return agentRuntime.run(systemPrompt, request.getUserMessage());
    }
}