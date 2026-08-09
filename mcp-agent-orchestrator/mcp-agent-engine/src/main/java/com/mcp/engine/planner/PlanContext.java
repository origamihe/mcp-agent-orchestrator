package com.mcp.engine.planner;

import com.mcp.tools.model.ToolCapability;
import com.mcp.tools.model.ToolDefinition;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Builder
public class PlanContext {

    private List<ToolDefinition> availableTools;
    private Set<ToolCapability> availableCapabilities;
    private String sessionId;
    private List<String> recentFiles;
    private List<String> conversationSummary;
    private String workspaceContext;
    @Builder.Default
    private int maxSteps = 8;
}