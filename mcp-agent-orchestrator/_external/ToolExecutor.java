package com.mcp.tools.executor;

import com.mcp.tools.model.ToolExecutionRequest;
import reactor.core.publisher.Mono;

public interface ToolExecutor {

    Mono<Object> execute(ToolExecutionRequest request);
}