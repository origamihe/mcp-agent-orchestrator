package com.mcp.tools.registry;

import com.mcp.tools.annotation.McpTool;
import com.mcp.tools.model.HealthCheckResult;
import com.mcp.tools.model.ToolCapability;
import com.mcp.tools.model.ToolCategory;
import com.mcp.tools.model.ToolDefinition;
import com.mcp.tools.model.ToolOwner;
import com.mcp.tools.model.ToolQuery;
import com.mcp.tools.model.ToolStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DefaultToolRegistry implements ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final Map<String, Method> methods = new ConcurrentHashMap<>();
    private final Map<String, Object> targets = new ConcurrentHashMap<>();
    private final Map<String, ToolStats> stats = new ConcurrentHashMap<>();

    // ==================== Registration ====================

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
                    .category(annotation.category())
                    .capabilities(Set.of(annotation.capabilities()))
                    .owner(annotation.owner())
                    .enabled(true)
                    .timeoutMs(annotation.timeoutMs())
                    .examples(List.of(annotation.examples()))
                    .priority(annotation.priority())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            tools.put(toolName, definition);
            methods.put(toolName, method);
            targets.put(toolName, bean);
            stats.putIfAbsent(toolName, ToolStats.empty(toolName));
            log.info("[ToolRegistry] Registered: {} [{}|{}|{}] → {}",
                    toolName, annotation.category(),
                    Arrays.toString(annotation.capabilities()),
                    annotation.owner(), description);
        }
    }

    @Override
    public void registerWithDefinition(ToolDefinition definition, Object target, Method method) {
        String name = definition.getName();
        tools.put(name, definition);
        methods.put(name, method);
        targets.put(name, target);
        stats.putIfAbsent(name, ToolStats.empty(name));
        log.info("[ToolRegistry] Registered with definition: {} [{}]", name, definition.getCategory());
    }

    @Override
    public void unregister(String toolName) {
        tools.remove(toolName);
        methods.remove(toolName);
        targets.remove(toolName);
        stats.remove(toolName);
        log.info("[ToolRegistry] Unregistered: {}", toolName);
    }

    // ==================== Discovery ====================

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

    @Override
    public List<ToolDefinition> getToolsByCategory(ToolCategory category) {
        return tools.values().stream()
                .filter(t -> t.getCategory() == category)
                .collect(Collectors.toList());
    }

    @Override
    public List<ToolDefinition> getToolsByOwner(ToolOwner owner) {
        return tools.values().stream()
                .filter(t -> t.getOwner() == owner)
                .collect(Collectors.toList());
    }

    @Override
    public List<ToolDefinition> getToolsByCapability(ToolCapability capability) {
        return tools.values().stream()
                .filter(t -> t.getCapabilities() != null && t.getCapabilities().contains(capability))
                .collect(Collectors.toList());
    }

    @Override
    public List<ToolDefinition> getEnabledTools() {
        return tools.values().stream()
                .filter(ToolDefinition::isEnabled)
                .collect(Collectors.toList());
    }

    @Override
    public List<ToolDefinition> query(ToolQuery query) {
        return tools.values().stream()
                .filter(t -> query.getOwner() == null || t.getOwner() == query.getOwner())
                .filter(t -> query.getCapability() == null
                        || (t.getCapabilities() != null && t.getCapabilities().contains(query.getCapability())))
                .filter(t -> query.getCategory() == null || t.getCategory() == query.getCategory())
                .filter(t -> query.getEnabled() == null || t.isEnabled() == query.getEnabled())
                .collect(Collectors.toList());
    }

    // ==================== Lifecycle ====================

    @Override
    public void enableTool(String name) {
        ToolDefinition def = tools.get(name);
        if (def != null) {
            def.setEnabled(true);
            def.setUpdatedAt(Instant.now());
            log.info("[ToolRegistry] Enabled: {}", name);
        }
    }

    @Override
    public void disableTool(String name) {
        ToolDefinition def = tools.get(name);
        if (def != null) {
            def.setEnabled(false);
            def.setUpdatedAt(Instant.now());
            log.info("[ToolRegistry] Disabled: {}", name);
        }
    }

    @Override
    public boolean isToolEnabled(String name) {
        ToolDefinition def = tools.get(name);
        return def != null && def.isEnabled();
    }

    // ==================== Statistics ====================

    @Override
    public ToolStats getToolStats(String name) {
        return stats.getOrDefault(name, ToolStats.empty(name));
    }

    @Override
    public List<ToolStats> getAllToolStats() {
        return new ArrayList<>(stats.values());
    }

    @Override
    public void recordToolExecution(String toolName, boolean success, long durationMs, String error) {
        stats.compute(toolName, (k, v) -> {
            ToolStats current = v != null ? v : ToolStats.empty(toolName);
            return current.withExecution(success, durationMs, error);
        });
    }

    // ==================== Health ====================

    @Override
    public HealthCheckResult healthCheck(String toolName) {
        if (!containsTool(toolName)) {
            return HealthCheckResult.unhealthy(toolName, "Tool not registered");
        }
        if (!isToolEnabled(toolName)) {
            return HealthCheckResult.unhealthy(toolName, "Tool is disabled");
        }
        long start = System.currentTimeMillis();
        try {
            Method method = methods.get(toolName);
            Object target = targets.get(toolName);
            if (method == null || target == null) {
                return HealthCheckResult.unhealthy(toolName, "Method or target is null");
            }
            long elapsed = System.currentTimeMillis() - start;
            return HealthCheckResult.healthy(toolName, elapsed);
        } catch (Exception e) {
            return HealthCheckResult.unhealthy(toolName, "Health check failed: " + e.getMessage());
        }
    }

    @Override
    public List<HealthCheckResult> healthCheckAll() {
        return tools.keySet().stream()
                .map(this::healthCheck)
                .collect(Collectors.toList());
    }

    // ==================== Internal helpers ====================

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