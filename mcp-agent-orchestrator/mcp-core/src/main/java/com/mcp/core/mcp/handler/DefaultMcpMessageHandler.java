package com.mcp.core.mcp.handler;

import com.mcp.core.mcp.model.McpMessage;
import reactor.core.publisher.Mono;
import org.springframework.stereotype.Component;

@Component
public class DefaultMcpMessageHandler implements McpMessageHandler {

    @Override
    public Mono<McpMessage> handle(McpMessage message) {
        // TODO: 根据 method 分发到不同处理器
        return Mono.just(McpMessage.builder()
                .id(message.getId())
                .result("Not implemented yet")
                .build());
    }
}