package com.mcp.tools.executor;

import com.mcp.tools.model.ToolExecutionRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class DefaultToolExecutor implements ToolExecutor {

    @Override
    public Mono<Object> execute(ToolExecutionRequest request) {
        // TODO: 从 registry 中找到对应方法并反射调用
        return Mono.just("Tool execution not fully implemented yet: " + request.getToolName());
    }
}