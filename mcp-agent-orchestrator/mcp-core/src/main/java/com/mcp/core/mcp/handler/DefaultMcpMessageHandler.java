package com.mcp.core.mcp.handler;

import com.mcp.core.mcp.model.McpError;
import com.mcp.core.mcp.model.McpMessage;
import reactor.core.publisher.Mono;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DefaultMcpMessageHandler implements McpMessageHandler {

    private static final int METHOD_NOT_FOUND = -32601;

    @Override
    public Mono<McpMessage> handle(McpMessage message) {
        String method = message.getMethod();
        if (method == null || method.isBlank()) {
            return Mono.just(McpMessage.builder()
                    .id(message.getId())
                    .error(new McpError(METHOD_NOT_FOUND, "Method not specified", null))
                    .build());
        }

        return switch (method) {
            case "ping" -> handlePing(message);
            case "initialize" -> handleInitialize(message);
            default -> Mono.just(McpMessage.builder()
                    .id(message.getId())
                    .error(new McpError(METHOD_NOT_FOUND,
                            "Method not found: " + method, null))
                    .build());
        };
    }

    private Mono<McpMessage> handlePing(McpMessage req) {
        return Mono.just(McpMessage.builder()
                .id(req.getId())
                .result(Map.of("pong", true))
                .build());
    }

    private Mono<McpMessage> handleInitialize(McpMessage req) {
        return Mono.just(McpMessage.builder()
                .id(req.getId())
                .result(Map.of(
                        "protocolVersion", "2024-11-05",
                        "serverInfo", Map.of(
                                "name", "MCP Agent Orchestrator",
                                "version", "0.0.1"
                        ),
                        "capabilities", Map.of(
                                "tools", Map.of("listChanged", true),
                                "prompts", Map.of("listChanged", false),
                                "resources", Map.of("subscribe", false, "listChanged", false)
                        )
                ))
                .build());
    }
}