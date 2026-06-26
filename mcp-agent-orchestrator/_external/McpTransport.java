package com.mcp.core.mcp.protocol;

import com.mcp.core.mcp.model.McpMessage;
import reactor.core.publisher.Flux;

/**
 * MCP 传输层抽象（支持 Stdio、SSE、WebSocket 等）
 */
public interface McpTransport {

    Flux<McpMessage> receive();

    void send(McpMessage message);

    void close();
}