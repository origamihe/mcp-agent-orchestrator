package com.mcp.engine.planner;

import com.mcp.tools.model.ToolCapability;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class PlanStep {

    private StepType type;
    private String toolName;
    private ToolCapability capability;
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

    /**
     * 获取可展示的工具/能力名称。
     * 优先 toolName，若为 null 则回退到 capability。
     */
    public String getDisplayName() {
        if (toolName != null && !toolName.isBlank()) {
            return toolName;
        }
        if (capability != null) {
            return capability.name();
        }
        return "unknown";
    }
}