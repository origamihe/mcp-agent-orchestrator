package com.mcp.starter;

import com.mcp.engine.orchestrator.AgentOrchestrator;
import com.mcp.tools.registry.ToolRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ComponentScan(basePackages = "com.mcp")
@EnableJpaRepositories(basePackages = "com.mcp.core.repository") // 显式指定 Repository 扫描路径
@EntityScan(basePackages = "com.mcp.core")                      // 显式指定 Entity 实体类扫描路径
@EnableAsync
public class McpOrchestratorApplication {

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(McpOrchestratorApplication.class, args);

        // 启动后自动注册默认组件
        AgentOrchestrator orchestrator = context.getBean(AgentOrchestrator.class);
        ToolRegistry toolRegistry = context.getBean(ToolRegistry.class);

        orchestrator.registerDefaultTools();

        System.out.println("MCP Agent Orchestrator 启动");
        System.out.println("MCP 接口地址: http://localhost:8080/mcp");
        System.out.println("测试工具列表: POST /mcp (method: tools/list)");
    }
}