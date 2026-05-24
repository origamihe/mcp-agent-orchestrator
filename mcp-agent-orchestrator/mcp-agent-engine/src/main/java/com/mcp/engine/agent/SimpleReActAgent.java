package com.mcp.engine.agent;

import com.mcp.llm.client.LlmClient;
import com.mcp.tools.registry.ToolRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class SimpleReActAgent implements Agent {

    private LlmClient llmClient;
    private ToolRegistry toolRegistry;

    @Override
    public String getId() {
        return "simple-react-agent";
    }

    @Override
    public String getName() {
        return "SimpleReActAgent";
    }

    @Override
    public Mono<String> execute(String task) {
        return llmClient.generate("请完成以下任务: " + task);
    }

    @Override
    public Mono<String> executeWithContext(String task, AgentContext context) {
        return execute(task);
    }

    @Override
    public void setLlmClient(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public void setToolRegistry(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }
}