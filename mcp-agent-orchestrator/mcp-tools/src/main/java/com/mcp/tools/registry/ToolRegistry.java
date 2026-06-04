package com.mcp.tools.registry;

import com.mcp.tools.model.ToolDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ToolRegistry {

    void register(Object bean);

    Mono<ToolDefinition> getTool(String name);

    Flux<ToolDefinition> listTools();

    List<ToolDefinition> getAllTools();

    boolean containsTool(String name);
}