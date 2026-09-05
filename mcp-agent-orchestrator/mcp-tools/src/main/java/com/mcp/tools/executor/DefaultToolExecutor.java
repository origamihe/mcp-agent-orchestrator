package com.mcp.tools.executor;

import com.mcp.tools.model.ToolDefinition;
import com.mcp.tools.model.ToolError;
import com.mcp.tools.model.ToolExecutionRequest;
import com.mcp.tools.model.ToolExecutionResult;
import com.mcp.tools.registry.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultToolExecutor implements ToolExecutor {

    private final ToolRegistry toolRegistry;
    private final List<ToolExecutionListener> listeners = new ArrayList<>();

    public void addListener(ToolExecutionListener listener) {
        listeners.add(listener);
    }

    @Override
    public Mono<ToolExecutionResult> execute(ToolExecutionRequest request) {
        String toolName = request.getToolName();
        long startTime = System.currentTimeMillis();

        notifyListenersStart(request);

        return toolRegistry.getTool(toolName)
                .switchIfEmpty(Mono.error(new RuntimeException("Tool not found: " + toolName)))
                .flatMap(definition -> {
                    if (!definition.isEnabled()) {
                        long duration = System.currentTimeMillis() - startTime;
                        String error = "Tool is disabled: " + toolName;
                        notifyListenersFailure(request, error, duration);
                        return Mono.just(ToolExecutionResult.executionError(
                                request.getRequestId(), toolName,
                                ToolError.internal(error),
                                Duration.ofMillis(duration)));
                    }

                    long timeoutMs = definition.getTimeoutMs();

                    return Mono.fromCallable(() -> {
                        Method method = getToolMethod(toolName);
                        Object target = getToolTarget(toolName);
                        if (method == null || target == null) {
                            throw new RuntimeException("Tool not found: " + toolName);
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
                        log.info("[ToolExecutor] Executing: {} with args: {}", toolName, request.getArguments());
                        return method.invoke(target, args);
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(Duration.ofMillis(timeoutMs))
                    .map(result -> {
                        long duration = System.currentTimeMillis() - startTime;
                        toolRegistry.recordToolExecution(toolName, true, duration, null);
                        log.info("[ToolExecutor] Success: {} ({}ms)", toolName, duration);
                        ToolExecutionResult execResult;
                        if (result instanceof ToolExecutionResult ter) {
                            execResult = new ToolExecutionResult(
                                    request.getRequestId(), toolName,
                                    ter.status(), ter.data(), ter.error(),
                                    Duration.ofMillis(duration),
                                    ter.metadata() != null ? ter.metadata() : Map.of()
                            );
                        } else {
                            execResult = ToolExecutionResult.success(
                                    request.getRequestId(), toolName, result, Duration.ofMillis(duration));
                        }
                        notifyListenersSuccess(request, execResult);
                        return execResult;
                    })
                    .onErrorResume(error -> {
                        long duration = System.currentTimeMillis() - startTime;
                        String errMsg = error.getMessage() != null ? error.getMessage() : "Unknown error";
                        toolRegistry.recordToolExecution(toolName, false, duration, errMsg);
                        log.warn("[ToolExecutor] Failed: {} ({}ms, error: {})", toolName, duration, errMsg);
                        if (error instanceof java.util.concurrent.TimeoutException) {
                            notifyListenersTimeout(request, duration);
                            return Mono.just(ToolExecutionResult.timeout(
                                    request.getRequestId(), toolName, Duration.ofMillis(duration)));
                        }
                        notifyListenersFailure(request, errMsg, duration);
                        return Mono.just(ToolExecutionResult.executionError(
                                request.getRequestId(), toolName,
                                ToolError.internal(errMsg), Duration.ofMillis(duration)));
                    });
                });
    }

    private void notifyListenersStart(ToolExecutionRequest request) {
        for (ToolExecutionListener listener : listeners) {
            try {
                listener.onExecutionStart(request);
            } catch (Exception e) {
                log.warn("[ToolExecutor] Listener error on start: {}", e.getMessage());
            }
        }
    }

    private void notifyListenersSuccess(ToolExecutionRequest request, ToolExecutionResult result) {
        for (ToolExecutionListener listener : listeners) {
            try {
                listener.onExecutionSuccess(request, result);
            } catch (Exception e) {
                log.warn("[ToolExecutor] Listener error on success: {}", e.getMessage());
            }
        }
    }

    private void notifyListenersFailure(ToolExecutionRequest request, String error, long elapsedMs) {
        for (ToolExecutionListener listener : listeners) {
            try {
                listener.onExecutionFailure(request, error, elapsedMs);
            } catch (Exception e) {
                log.warn("[ToolExecutor] Listener error on failure: {}", e.getMessage());
            }
        }
    }

    private void notifyListenersTimeout(ToolExecutionRequest request, long elapsedMs) {
        for (ToolExecutionListener listener : listeners) {
            try {
                listener.onExecutionTimeout(request, elapsedMs);
            } catch (Exception e) {
                log.warn("[ToolExecutor] Listener error on timeout: {}", e.getMessage());
            }
        }
    }

    private Method getToolMethod(String toolName) {
        if (toolRegistry instanceof com.mcp.tools.registry.DefaultToolRegistry dt) {
            return dt.getToolMethod(toolName);
        }
        return null;
    }

    private Object getToolTarget(String toolName) {
        if (toolRegistry instanceof com.mcp.tools.registry.DefaultToolRegistry dt) {
            return dt.getToolTarget(toolName);
        }
        return null;
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