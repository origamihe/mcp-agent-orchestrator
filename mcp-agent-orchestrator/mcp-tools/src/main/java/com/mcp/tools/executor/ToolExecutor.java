package com.mcp.tools.executor;

import com.mcp.tools.model.ToolExecutionRequest;
import com.mcp.tools.model.ToolExecutionResult;
import reactor.core.publisher.Mono;

public interface ToolExecutor {

    Mono<ToolExecutionResult> execute(ToolExecutionRequest request);
}