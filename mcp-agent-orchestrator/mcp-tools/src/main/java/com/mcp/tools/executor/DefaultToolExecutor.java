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
import java.util.Map;

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
                    Object rawValue = inputArgs.get(params[i].getName());
                    args[i] = coerceType(rawValue, params[i].getType());
                }
            }
            log.info("Executing tool: {} with args: {}", request.getToolName(), request.getArguments());
            return method.invoke(target, args);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Object coerceType(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isAssignableFrom(value.getClass())) return value;
        String strValue = String.valueOf(value);
        if (targetType == int.class || targetType == Integer.class) {
            if (strValue.isEmpty() || "null".equals(strValue)) return 0;
            return Integer.parseInt(strValue);
        }
        if (targetType == long.class || targetType == Long.class) {
            if (strValue.isEmpty() || "null".equals(strValue)) return 0L;
            return Long.parseLong(strValue);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (strValue.isEmpty() || "null".equals(strValue)) return false;
            return Boolean.parseBoolean(strValue);
        }
        if (targetType == double.class || targetType == Double.class) {
            if (strValue.isEmpty() || "null".equals(strValue)) return 0.0;
            return Double.parseDouble(strValue);
        }
        if (targetType == float.class || targetType == Float.class) {
            if (strValue.isEmpty() || "null".equals(strValue)) return 0.0f;
            return Float.parseFloat(strValue);
        }
        return value;
    }
}