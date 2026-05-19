package com.mcp.starter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.mcp")
public class McpOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpOrchestratorApplication.class, args);
        System.out.println("🚀 MCP Agent Orchestrator 启动成功！");
    }
}