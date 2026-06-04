package com.mcp.core.mcp.handler;

import com.mcp.core.mcp.model.McpMessage;
import reactor.core.publisher.Mono;

public interface McpMessageHandler {

    Mono<McpMessage> handle(McpMessage message);
}