package com.mcp.tools.registry;

import com.mcp.tools.annotation.McpTool;
import com.mcp.tools.model.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DefaultToolRegistry implements ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final Map<String, Method> methods = new ConcurrentHashMap<>();
    private final Map<String, Object> targets = new ConcurrentHashMap<>();

    @Override
    public void register(Object bean) {
        Class<?> clazz = bean.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            McpTool annotation = method.getAnnotation(McpTool.class);
            if (annotation == null) {
                continue;
            }
            String toolName = annotation.name().isEmpty() ? method.getName() : annotation.name();
            String description = annotation.description();
            ToolDefinition definition = ToolDefinition.builder()
                    .name(toolName)
                    .description(description)
                    .inputSchema(buildInputSchema(method))
                    .tags(List.of(annotation.tags()))
                    .version("1.0.0")
                    .build();
            tools.put(toolName, definition);
            methods.put(toolName, method);
            targets.put(toolName, bean);
            log.info("Tool registered: {} -> {}", toolName, description);
        }
    }

    @Override
    public Mono<ToolDefinition> getTool(String name) {
        return Mono.justOrEmpty(tools.get(name));
    }

    @Override
    public Flux<ToolDefinition> listTools() {
        return Flux.fromIterable(tools.values());
    }

    @Override
    public List<ToolDefinition> getAllTools() {
        return List.copyOf(tools.values());
    }

    @Override
    public boolean containsTool(String name) {
        return tools.containsKey(name);
    }

    public Method getToolMethod(String toolName) {
        return methods.get(toolName);
    }

    public Object getToolTarget(String toolName) {
        return targets.get(toolName);
    }

    private String buildInputSchema(Method method) {
        StringBuilder schema = new StringBuilder("{\"type\":\"object\",\"properties\":{");
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) schema.append(",");
            String paramName = params[i].getName();
            String jsonType = mapToJsonType(params[i].getType());
            schema.append("\"").append(paramName).append("\":{\"type\":\"").append(jsonType).append("\"}");
        }
        schema.append("}}");
        return schema.toString();
    }

    private String mapToJsonType(Class<?> type) {
        if (type == int.class || type == Integer.class) return "integer";
        if (type == long.class || type == Long.class) return "integer";
        if (type == double.class || type == Double.class) return "number";
        if (type == float.class || type == Float.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        return "string";
    }
}