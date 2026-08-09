package com.mcp.engine.agent.bootstrap;

import com.mcp.engine.agent.Agent;
import com.mcp.engine.agent.bus.A2aMessageBus;
import com.mcp.engine.agent.registry.AgentRegistry;
import com.mcp.engine.runtime.AgentRuntime;
import com.mcp.llm.client.LlmClient;
import com.mcp.tools.registry.ToolRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 自动注册引导器
 * <p>
 * 在应用启动时自动将所有 Agent 注册到 AgentRegistry 和 A2aMessageBus。
 * 新 Agent 只需添加为 Spring Bean 即可自动被发现和注册。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentBootstrap {

    private final List<Agent> allAgents;
    private final AgentRegistry agentRegistry;
    private final A2aMessageBus messageBus;
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final AgentRuntime agentRuntime;

    @PostConstruct
    public void bootstrap() {
        log.info("[AgentBootstrap] Bootstrapping {} agents...", allAgents.size());

        for (Agent agent : allAgents) {
            agent.setLlmClient(llmClient);
            agent.setToolRegistry(toolRegistry);
            agent.setAgentRuntime(agentRuntime);

            agentRegistry.register(agent, agent.getAgentCard());
            messageBus.registerAgent(agent.getId());

            log.info("[AgentBootstrap] Agent registered: {} (id={}, type={})",
                    agent.getName(), agent.getId(), agent.getAgentCard().getAgentType());
        }

        log.info("[AgentBootstrap] Bootstrap complete. {} agents ready.", agentRegistry.agentCount());
    }
}