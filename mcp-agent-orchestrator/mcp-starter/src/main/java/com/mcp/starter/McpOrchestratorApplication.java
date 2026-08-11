package com.mcp.starter;

import com.mcp.engine.agent.SimpleReActAgent;
import com.mcp.engine.agent.impl.SearchAgent;
import com.mcp.engine.orchestrator.AgentOrchestrator;
import com.mcp.llm.client.LlmClient;
import com.mcp.tools.executor.ToolExecutor;
import com.mcp.tools.model.ToolCategory;
import com.mcp.tools.registry.ToolRegistry;
import com.mcp.tools.tool.*;
import com.mcp.tools.tool.document.DocumentReadToolSet;
import com.mcp.tools.tool.document.DocumentSearchToolSet;
import com.mcp.tools.tool.read.ReadToolSet;
import com.mcp.tools.tool.edit.EditToolSet;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.stream.Collectors;

@SpringBootApplication
@ComponentScan(basePackages = "com.mcp")
@EnableJpaRepositories(basePackages = "com.mcp.core.repository")
@EntityScan(basePackages = "com.mcp.core")
@EnableAsync
@EnableScheduling
@EnableCaching
public class McpOrchestratorApplication {

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(McpOrchestratorApplication.class, args);

        AgentOrchestrator orchestrator = context.getBean(AgentOrchestrator.class);
        ToolRegistry toolRegistry = context.getBean(ToolRegistry.class);
        ToolExecutor toolExecutor = context.getBean(ToolExecutor.class);
        LlmClient llmClient = context.getBean(LlmClient.class);

        // 1. 注册工具
        ReadToolSet readToolSet = context.getBean(ReadToolSet.class);
        toolRegistry.register(readToolSet);
        EditToolSet editToolSet = context.getBean(EditToolSet.class);
        toolRegistry.register(editToolSet);
        WebSearchTool webSearchTool = context.getBean(WebSearchTool.class);
        toolRegistry.register(webSearchTool);
        MultiSearchTool multiSearchTool = context.getBean(MultiSearchTool.class);
        toolRegistry.register(multiSearchTool);
        FetchWebpageTool fetchWebpageTool = context.getBean(FetchWebpageTool.class);
        toolRegistry.register(fetchWebpageTool);
        DeepResearchTool deepResearchTool = context.getBean(DeepResearchTool.class);
        toolRegistry.register(deepResearchTool);
        DocumentReadToolSet documentReadToolSet = context.getBean(DocumentReadToolSet.class);
        toolRegistry.register(documentReadToolSet);
        DocumentSearchToolSet documentSearchToolSet = context.getBean(DocumentSearchToolSet.class);
        toolRegistry.register(documentSearchToolSet);
        DocxGeneratorTool docxGeneratorTool = context.getBean(DocxGeneratorTool.class);
        toolRegistry.register(docxGeneratorTool);
        orchestrator.registerDefaultTools();

        // 2. 配置并注册 Agent
        SimpleReActAgent agent = context.getBean(SimpleReActAgent.class);
        agent.setLlmClient(llmClient);
        agent.setToolRegistry(toolRegistry);
        orchestrator.registerAgent(agent);
        System.out.println("已注册 Agent: " + agent.getName());

        SearchAgent searchAgent = context.getBean(SearchAgent.class);
        searchAgent.setLlmClient(llmClient);
        searchAgent.setToolRegistry(toolRegistry);
        orchestrator.registerAgent(searchAgent);
        System.out.println("已注册 Agent: " + searchAgent.getName());

        System.out.println("============================================");
        System.out.println("MCP Agent Orchestrator 启动完成");
        System.out.println("MCP 接口地址: http://localhost:8080/mcp");
        System.out.println("已注册 Agent: " + agent.getName());
        System.out.println("已注册工具数: " + toolRegistry.getAllTools().size());
        System.out.println("已启用工具数: " + toolRegistry.getEnabledTools().size());
        System.out.println("--- 按分类统计 ---");
        for (ToolCategory cat : ToolCategory.values()) {
            int count = toolRegistry.getToolsByCategory(cat).size();
            if (count > 0) {
                System.out.println("  " + cat.getDisplayName() + " (" + cat.name() + "): " + count + " 个");
            }
        }
        System.out.println("--- 工具列表 ---");
        toolRegistry.getAllTools().forEach(t ->
                System.out.println("  - " + t.getName() + " [" + t.getCategory() + "] "
                        + (t.isEnabled() ? "✓" : "✗") + " " + t.getDescription())
        );
        System.out.println("--- 健康检查 ---");
        toolRegistry.healthCheckAll().forEach(hc ->
                System.out.println("  " + (hc.healthy() ? "✓" : "✗") + " " + hc.toolName()
                        + " (" + hc.responseTimeMs() + "ms) " + hc.message())
        );
        System.out.println("============================================");
    }
}