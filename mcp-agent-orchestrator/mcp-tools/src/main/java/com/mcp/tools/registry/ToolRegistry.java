package com.mcp.tools.registry;

import com.mcp.tools.model.HealthCheckResult;
import com.mcp.tools.model.ToolCapability;
import com.mcp.tools.model.ToolCategory;
import com.mcp.tools.model.ToolDefinition;
import com.mcp.tools.model.ToolOwner;
import com.mcp.tools.model.ToolQuery;
import com.mcp.tools.model.ToolStats;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public interface ToolRegistry {

    // ==================== Registration ====================

    void register(Object bean);

    void registerWithDefinition(ToolDefinition definition, Object target, Method method);

    void unregister(String toolName);

    // ==================== Discovery ====================

    Mono<ToolDefinition> getTool(String name);

    Flux<ToolDefinition> listTools();

    List<ToolDefinition> getAllTools();

    boolean containsTool(String name);

    List<ToolDefinition> getToolsByCategory(ToolCategory category);

    List<ToolDefinition> getToolsByOwner(ToolOwner owner);

    List<ToolDefinition> getToolsByCapability(ToolCapability capability);

    List<ToolDefinition> getEnabledTools();

    List<ToolDefinition> query(ToolQuery query);

    /**
     * 获取所有已注册工具的能力集合。
     */
    default Set<ToolCapability> getAllCapabilities() {
        Set<ToolCapability> caps = new HashSet<>();
        for (ToolDefinition tool : getAllTools()) {
            if (tool.getCapabilities() != null) {
                caps.addAll(tool.getCapabilities());
            }
        }
        return caps;
    }

    // ==================== Lifecycle ====================

    void enableTool(String name);

    void disableTool(String name);

    boolean isToolEnabled(String name);

    // ==================== Statistics ====================

    ToolStats getToolStats(String name);

    List<ToolStats> getAllToolStats();

    void recordToolExecution(String toolName, boolean success, long durationMs, String error);

    // ==================== Health ====================

    HealthCheckResult healthCheck(String toolName);

    List<HealthCheckResult> healthCheckAll();
}