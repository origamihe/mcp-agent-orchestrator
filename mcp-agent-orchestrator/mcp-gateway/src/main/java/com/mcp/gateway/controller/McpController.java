package com.mcp.gateway.controller;

import com.mcp.core.mcp.model.McpMessage;
import com.mcp.engine.orchestrator.AgentOrchestrator;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/mcp")
public class McpController {

    private final AgentOrchestrator orchestrator;

    public McpController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * MCP 标准入口（JSON-RPC 风格）
     */
    @PostMapping
    public Mono<McpMessage> handleMcpRequest(@RequestBody McpMessage message) {
        return processMcpMessage(message);
    }

    private Mono<McpMessage> processMcpMessage(McpMessage req) {
        return switch (req.getMethod()) {
            case "initialize" -> handleInitialize(req);
            case "tools/list" -> handleListTools(req);
            case "tools/call" -> handleToolCall(req);
            case "agent/process" -> handleAgentProcess(req);
            default -> Mono.just(McpMessage.builder()
                    .id(req.getId())
                    .error(new com.mcp.core.mcp.model.McpError(-32601, "Method not found", null))
                    .build());
        };
    }

    private Mono<McpMessage> handleInitialize(McpMessage req) {
        return Mono.just(McpMessage.builder()
                .id(req.getId())
                .result("MCP Agent Orchestrator v0.0.1 initialized")
                .build());
    }

    private Mono<McpMessage> handleListTools(McpMessage req) {
        // TODO: 从 ToolRegistry 获取工具列表
        return Mono.just(McpMessage.builder()
                .id(req.getId())
                .result("tools will be listed here")
                .build());
    }

    private Mono<McpMessage> handleToolCall(McpMessage req) {
        // TODO: 调用 ToolExecutor
        return Mono.just(McpMessage.builder()
                .id(req.getId())
                .result("Tool call result")
                .build());
    }

    private Mono<McpMessage> handleAgentProcess(McpMessage req) {
        String task = req.getParams() != null ? req.getParams().toString() : "";
        return orchestrator.processRequest(task, req.getId())
                .map(result -> McpMessage.builder()
                        .id(req.getId())
                        .result(result)
                        .build());
    }
}