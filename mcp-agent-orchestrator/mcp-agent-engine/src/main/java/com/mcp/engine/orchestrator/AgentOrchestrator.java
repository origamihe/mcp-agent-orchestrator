package com.mcp.engine.orchestrator;

import com.mcp.engine.agent.Agent;
import reactor.core.publisher.Mono;

/**
 * Agent 调度中台核心
 */
public interface AgentOrchestrator {

    Mono<String> processRequest(String request, String sessionId);

    Mono<String> executeTask(String task, String agentName);

    void registerAgent(Agent agent);

    void registerDefaultTools();
}