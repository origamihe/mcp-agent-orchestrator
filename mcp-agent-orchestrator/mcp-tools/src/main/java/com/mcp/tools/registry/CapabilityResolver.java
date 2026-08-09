package com.mcp.tools.registry;

import com.mcp.tools.model.ToolCapability;
import com.mcp.tools.model.ToolDefinition;
import com.mcp.tools.model.ToolQuery;
import com.mcp.tools.model.ToolScore;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 能力解析器 —— Planner 与 Tool Registry 之间的语义层。
 * Planner 输出能力需求（ToolQuery），Resolver 负责解析为具体工具列表。
 * resolveRanked() 在此基础上加入多维度评分排序。
 */
public interface CapabilityResolver {

    /**
     * 根据查询条件解析可用的工具列表。
     */
    List<ToolDefinition> resolve(ToolQuery query);

    /**
     * 根据查询条件解析并返回评分排序后的工具列表。
     * 默认实现按 ToolDefinition.priority 降序排列。
     */
    default List<ToolScore> resolveRanked(ToolQuery query) {
        return resolve(query).stream()
                .map(tool -> ToolScore.builder()
                        .tool(tool)
                        .priorityBonus(tool.getPriority() * 2.0)
                        .build())
                .sorted((a, b) -> Double.compare(b.getCompositeScore(), a.getCompositeScore()))
                .collect(Collectors.toList());
    }

    /**
     * 根据单一能力解析工具列表（便捷方法）。
     */
    default List<ToolDefinition> resolveByCapability(ToolCapability capability) {
        return resolve(ToolQuery.builder()
                .capability(capability)
                .enabled(true)
                .build());
    }
}