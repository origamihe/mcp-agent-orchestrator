package com.mcp.starter;

import com.mcp.engine.agent.SimpleReActAgent;
import com.mcp.engine.orchestrator.AgentOrchestrator;
import com.mcp.llm.client.LlmClient;
import com.mcp.tools.executor.ToolExecutor;
import com.mcp.tools.registry.ToolRegistry;
import com.mcp.tools.tool.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ComponentScan(basePackages = "com.mcp")
@EnableJpaRepositories(basePackages = "com.mcp.core.repository")
@EntityScan(basePackages = "com.mcp.core")
@EnableAsync
public class McpOrchestratorApplication {

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(McpOrchestratorApplication.class, args);

        AgentOrchestrator orchestrator = context.getBean(AgentOrchestrator.class);
        ToolRegistry toolRegistry = context.getBean(ToolRegistry.class);
        ToolExecutor toolExecutor = context.getBean(ToolExecutor.class);
        LlmClient llmClient = context.getBean(LlmClient.class);

        // 1. 注册工具
        FileReadTool fileReadTool = context.getBean(FileReadTool.class);
        toolRegistry.register(fileReadTool);
        FileWriteTool fileWriteTool = context.getBean(FileWriteTool.class);
        toolRegistry.register(fileWriteTool);
        WebSearchTool webSearchTool = context.getBean(WebSearchTool.class);
        toolRegistry.register(webSearchTool);
        MultiSearchTool multiSearchTool = context.getBean(MultiSearchTool.class);
        toolRegistry.register(multiSearchTool);
        FetchWebpageTool fetchWebpageTool = context.getBean(FetchWebpageTool.class);
        toolRegistry.register(fetchWebpageTool);
        DeepResearchTool deepResearchTool = context.getBean(DeepResearchTool.class);
        toolRegistry.register(deepResearchTool);
        orchestrator.registerDefaultTools();

        // 2. 配置并注册 Agent
        SimpleReActAgent agent = context.getBean(SimpleReActAgent.class);
        agent.setLlmClient(llmClient);
        agent.setToolRegistry(toolRegistry);
        agent.setToolExecutor(toolExecutor);
        orchestrator.registerAgent(agent);

        System.out.println("============================================");
        System.out.println("MCP Agent Orchestrator 启动完成");
        System.out.println("MCP 接口地址: http://localhost:8080/mcp");
        System.out.println("已注册 Agent: " + agent.getName());
        System.out.println("已注册工具数: " + toolRegistry.getAllTools().size());
        toolRegistry.getAllTools().forEach(t ->
                System.out.println("  - " + t.getName() + ": " + t.getDescription())
        );
        System.out.println("============================================");
    }
}