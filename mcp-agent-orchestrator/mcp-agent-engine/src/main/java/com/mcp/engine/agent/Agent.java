package com.mcp.engine.agent;

import com.mcp.llm.client.LlmClient;
import com.mcp.tools.registry.ToolRegistry;
import reactor.core.publisher.Mono;

/**
 * Agent 抽象接口
 */
public interface Agent {

    String getId();
    String getName();

    Mono<String> execute(String task);
    Mono<String> executeWithContext(String task, AgentContext context);

    void setLlmClient(LlmClient llmClient);
    void setToolRegistry(ToolRegistry toolRegistry);
}