package com.mcp.engine.orchestrator;

import com.mcp.engine.agent.Agent;
import com.mcp.common.channel.RecallMode;
import reactor.core.publisher.Mono;

/**
 * Agent 调度中台核心
 */
public interface AgentOrchestrator {

    Mono<String> processRequest(String request, String sessionId);

    Mono<String> processRequestWithModel(String request, String sessionId, String modelConfigId);

    Mono<String> processRequestWithSystemPrompt(String request, String sessionId, String systemPrompt, String modelConfigId);

    Mono<String> processRequestWithHistory(String request, String sessionId, String systemPrompt, RecallMode recallMode);

    Mono<String> executeTask(String task, String agentName);

    void registerAgent(Agent agent);

    void registerDefaultTools();
}