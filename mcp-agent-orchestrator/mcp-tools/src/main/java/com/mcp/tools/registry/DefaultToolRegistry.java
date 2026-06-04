package com.mcp.tools.registry;

import com.mcp.tools.annotation.McpTool;
import com.mcp.tools.model.ToolDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.MethodInvoker;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DefaultToolRegistry implements ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final Map<String, MethodInvoker> invokers = new ConcurrentHashMap<>();

    @Override
    public void register(Object bean) {
        // 扫描 @McpTool 注解的方法并注册（后续实现）
        // 当前为骨架，后面会补充完整扫描逻辑
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
}