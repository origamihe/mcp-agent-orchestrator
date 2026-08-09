package com.mcp.tools.model;

import lombok.Builder;
import lombok.Data;

/**
 * 工具查询条件 —— 支持多条件组合查询。
 * Planner 通过 CapabilityResolver 构建查询，Registry 执行查询。
 * 未来可扩展：namePattern、minPriority、tags 等。
 */
@Data
@Builder
public class ToolQuery {

    private ToolOwner owner;
    private ToolCapability capability;
    private ToolCategory category;
    @Builder.Default
    private Boolean enabled = true;

    public boolean isEmpty() {
        return owner == null && capability == null && category == null && enabled == null;
    }
}