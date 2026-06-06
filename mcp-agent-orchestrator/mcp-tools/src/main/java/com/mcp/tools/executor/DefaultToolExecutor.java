package com.mcp.tools.executor;

import com.mcp.tools.model.ToolExecutionRequest;
import com.mcp.tools.registry.DefaultToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultToolExecutor implements ToolExecutor {

    private final DefaultToolRegistry toolRegistry;

    @Override
    public Mono<Object> execute(ToolExecutionRequest request) {
        return Mono.fromCallable(() -> {
            Method method = toolRegistry.getToolMethod(request.getToolName());
            Object target = toolRegistry.getToolTarget(request.getToolName());
            if (method == null || target == null) {
                throw new RuntimeException("Tool not found: " + request.getToolName());
            }

            Parameter[] params = method.getParameters();
            Object[] args = new Object[params.length];
            var inputArgs = request.getArguments();
            for (int i = 0; i < params.length; i++) {
                if (inputArgs != null && inputArgs.containsKey(params[i].getName())) {
                    args[i] = inputArgs.get(params[i].getName());
                }
            }

            log.info("Executing tool: {} with args: {}", request.getToolName(), request.getArguments());
            return method.invoke(target, args);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}