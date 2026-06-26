package com.mcp.engine.planner;

import com.mcp.tools.model.ToolDefinition;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PlanContext {

    private List<ToolDefinition> availableTools;
    private String sessionId;
    private List<String> recentFiles;
    private List<String> conversationSummary;
    @Builder.Default
    private int maxSteps = 8;
}