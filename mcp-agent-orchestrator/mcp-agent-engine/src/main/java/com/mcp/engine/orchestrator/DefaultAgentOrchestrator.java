package com.mcp.engine.orchestrator;

import com.mcp.engine.agent.Agent;
import com.mcp.llm.client.LlmClient;
import com.mcp.tools.executor.ToolExecutor;
import com.mcp.tools.registry.ToolRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DefaultAgentOrchestrator implements AgentOrchestrator {

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    public DefaultAgentOrchestrator(LlmClient llmClient,
                                    ToolRegistry toolRegistry,
                                    ToolExecutor toolExecutor) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
    }

    @Override
    public Mono<String> processRequest(String request, String sessionId) {
        // TODO: 实现完整的 ReAct / Plan-and-Execute 流程
        return llmClient.generateWithSystemPrompt(
                "你是一个智能 Agent Orchestrator，请分析用户请求并决定是否需要调用工具。",
                request
        ).map(response -> "Orchestrator 处理结果: " + response);
    }

    @Override
    public Mono<String> executeTask(String task, String agentName) {
        Agent agent = agents.get(agentName);
        if (agent == null) {
            return Mono.error(new RuntimeException("Agent not found: " + agentName));
        }
        return agent.execute(task);
    }

    @Override
    public void registerAgent(Agent agent) {
        agents.put(agent.getName(), agent);
    }

    @Override
    public void registerDefaultTools() {
        // 后续实现自动注册工具
    }
}