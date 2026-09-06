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
import com.mcp.core.service.LlmConfigService;
import com.mcp.core.domain.llm.LlmModelConfig;
import com.mcp.core.domain.llm.ProviderAvailability;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.util.stream.Collectors;

@SpringBootApplication
@ComponentScan(basePackages = "com.mcp")
@EnableJpaRepositories(basePackages = "com.mcp.core.repository")
@EntityScan(basePackages = "com.mcp.core")
@EnableAsync
@EnableScheduling
@EnableCaching
public class McpOrchestratorApplication {

    private static final int DEFAULT_PORT = 8080;
    private static final int PORT_CHECK_TIMEOUT_MS = 2000;

    public static void main(String[] args) {
        int port = resolveServerPort(args);

        if (!isPortAvailable(port)) {
            printPortConflictError(port);
            System.exit(1);
        }

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
        System.out.println("--- 工具健康检查 ---");
        toolRegistry.healthCheckAll().forEach(hc ->
                System.out.println("  " + (hc.healthy() ? "✓" : "✗") + " " + hc.toolName()
                        + " (" + hc.responseTimeMs() + "ms) " + hc.message())
        );

        System.out.println();
        printStartupReadinessReport(context);
        System.out.println("============================================");
    }

    private static void printStartupReadinessReport(ApplicationContext context) {
        System.out.println("=== 启动就绪检查 (Startup Readiness Report) ===");
        System.out.println();

        boolean llmReady = checkLlmReadiness(context);
        boolean dbReady = checkDatabaseReadiness(context);
        boolean toolsReady = checkToolReadiness(context);

        boolean allReady = llmReady && dbReady && toolsReady;

        System.out.println();
        if (allReady) {
            System.out.println("  ✓✓✓  SYSTEM READY — 所有核心组件就绪，系统可正常服务");
        } else {
            System.out.println("  ⚠ SYSTEM DEGRADED — 部分组件未就绪，系统以降级模式运行");
            if (!llmReady) System.out.println("    → LLM 服务不可用，Agent 对话功能受限");
            if (!dbReady) System.out.println("    → 数据库连接异常，持久化功能不可用");
            if (!toolsReady) System.out.println("    → 部分工具健康检查未通过");
        }
    }

    private static boolean checkLlmReadiness(ApplicationContext context) {
        try {
            LlmConfigService llmConfigService = context.getBean(LlmConfigService.class);
            ProviderAvailability providerAvailability = context.getBean(ProviderAvailability.class);
            LlmModelConfig defaultConfig = llmConfigService.getDefaultConfig().block();

            if (defaultConfig == null) {
                System.out.println("  ✗ LLM 配置: 未找到默认配置");
                return false;
            }

            boolean providerAvailable = providerAvailability.isProviderAvailable(defaultConfig.getProvider());
            if (providerAvailable) {
                System.out.println("  ✓ LLM 配置: " + defaultConfig.getProvider() + " / " + defaultConfig.getModelName() + " — 可用");
            } else {
                System.out.println("  ✗ LLM 配置: " + defaultConfig.getProvider() + " / " + defaultConfig.getModelName() + " — Provider 不可用");
            }
            return providerAvailable;
        } catch (Exception e) {
            System.out.println("  ✗ LLM 检查失败: " + e.getMessage());
            return false;
        }
    }

    private static boolean checkDatabaseReadiness(ApplicationContext context) {
        try {
            DataSource dataSource = context.getBean(DataSource.class);
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(3)) {
                    System.out.println("  ✓ 数据库连接: " + conn.getMetaData().getURL() + " — 正常");
                    return true;
                }
            }
            System.out.println("  ✗ 数据库连接: 连接无效");
            return false;
        } catch (Exception e) {
            System.out.println("  ✗ 数据库连接: " + e.getMessage());
            return false;
        }
    }

    private static boolean checkToolReadiness(ApplicationContext context) {
        try {
            ToolRegistry toolRegistry = context.getBean(ToolRegistry.class);
            long unhealthyCount = toolRegistry.healthCheckAll().stream()
                    .filter(hc -> !hc.healthy())
                    .count();
            long totalCount = toolRegistry.getAllTools().size();
            long healthyCount = totalCount - unhealthyCount;

            if (unhealthyCount == 0) {
                System.out.println("  ✓ 工具状态: " + healthyCount + "/" + totalCount + " 工具健康检查通过");
                return true;
            } else {
                System.out.println("  ⚠ 工具状态: " + healthyCount + "/" + totalCount + " 工具健康，"
                        + unhealthyCount + " 个工具异常");
                return false;
            }
        } catch (Exception e) {
            System.out.println("  ✗ 工具检查失败: " + e.getMessage());
            return false;
        }
    }

    private static int resolveServerPort(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--server.port".equals(args[i])) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    System.err.println("[WARN] Invalid --server.port value: " + args[i + 1] + ", using default " + DEFAULT_PORT);
                    return DEFAULT_PORT;
                }
            }
        }
        String envPort = System.getenv("SERVER_PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                return Integer.parseInt(envPort);
            } catch (NumberFormatException e) {
                System.err.println("[WARN] Invalid SERVER_PORT env value: " + envPort + ", using default " + DEFAULT_PORT);
                return DEFAULT_PORT;
            }
        }
        return DEFAULT_PORT;
    }

    private static boolean isPortAvailable(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), PORT_CHECK_TIMEOUT_MS);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static void printPortConflictError(int port) {
        System.err.println();
        System.err.println("======================================================");
        System.err.println("  ERROR: Port " + port + " is already in use");
        System.err.println("======================================================");
        System.err.println();
        System.err.println("  The MCP Agent Orchestrator cannot start because");
        System.err.println("  port " + port + " is already occupied by another process.");
        System.err.println();
        System.err.println("  To resolve this issue:");
        System.err.println();
        System.err.println("  1. Find and stop the process using port " + port + ":");
        System.err.println("     Windows:  netstat -ano | findstr :" + port);
        System.err.println("               taskkill /PID <PID> /F");
        System.err.println("     Linux:    lsof -i :" + port);
        System.err.println("               kill -9 <PID>");
        System.err.println();
        System.err.println("  2. Or use a different port:");
        System.err.println("     mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=" + (port + 1));
        System.err.println("     or");
        System.err.println("     set SERVER_PORT=" + (port + 1) + " && mvn spring-boot:run");
        System.err.println();
        System.err.println("======================================================");
        System.err.println();
    }
}