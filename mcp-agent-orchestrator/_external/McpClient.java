package com.mcp.core.mcp.protocol;

import com.mcp.core.mcp.model.McpMessage;
import reactor.core.publisher.Mono;

/**
 * MCP Client（AI Agent 侧）
 */
public interface McpClient {

    Mono<McpMessage> sendRequest(McpMessage request);

    Mono<McpMessage> initialize();

    Mono<McpMessage> listTools();

    Mono<McpMessage> callTool(String toolName, Object arguments);
}