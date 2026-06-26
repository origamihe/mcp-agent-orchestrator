package com.mcp.engine.planner;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class PlanStep {

    private StepType type;
    private String toolName;
    private Map<String, Object> arguments;
    private String reason;
    private List<String> dependsOn;

    public enum StepType {
        READ,
        SEARCH,
        ANALYZE,
        MODIFY,
        VALIDATE,
        OBSERVE
    }
}