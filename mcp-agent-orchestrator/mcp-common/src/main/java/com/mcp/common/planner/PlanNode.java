package com.mcp.common.planner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAG 计划节点，描述一个可执行的步骤及其数据流关系。
 */
public class PlanNode {

    private String id;
    private String description;
    private String toolName;
    private String capability;
    private Map<String, Object> staticArgs;
    private Map<String, String> inputMapping;
    private List<String> dependsOn;
    private String fallbackTool;
    private int maxRetries;
    private long timeoutMs;

    public PlanNode() {
        this.staticArgs = new LinkedHashMap<>();
        this.inputMapping = new LinkedHashMap<>();
        this.dependsOn = new ArrayList<>();
        this.maxRetries = 0;
        this.timeoutMs = 30_000;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final PlanNode node = new PlanNode();

        public Builder id(String id) { node.id = id; return this; }
        public Builder description(String description) { node.description = description; return this; }
        public Builder toolName(String toolName) { node.toolName = toolName; return this; }
        public Builder capability(String capability) { node.capability = capability; return this; }
        public Builder staticArg(String key, Object value) { node.staticArgs.put(key, value); return this; }
        public Builder staticArgs(Map<String, Object> args) { node.staticArgs.putAll(args); return this; }
        public Builder inputMapping(String paramName, String sourcePath) { node.inputMapping.put(paramName, sourcePath); return this; }
        public Builder dependsOn(String nodeId) { node.dependsOn.add(nodeId); return this; }
        public Builder dependsOn(List<String> nodeIds) { node.dependsOn.addAll(nodeIds); return this; }
        public Builder fallbackTool(String fallbackTool) { node.fallbackTool = fallbackTool; return this; }
        public Builder maxRetries(int maxRetries) { node.maxRetries = maxRetries; return this; }
        public Builder timeoutMs(long timeoutMs) { node.timeoutMs = timeoutMs; return this; }

        public PlanNode build() { return node; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getCapability() { return capability; }
    public void setCapability(String capability) { this.capability = capability; }
    public Map<String, Object> getStaticArgs() { return staticArgs; }
    public void setStaticArgs(Map<String, Object> staticArgs) { this.staticArgs = staticArgs; }
    public Map<String, String> getInputMapping() { return inputMapping; }
    public void setInputMapping(Map<String, String> inputMapping) { this.inputMapping = inputMapping; }
    public List<String> getDependsOn() { return dependsOn; }
    public void setDependsOn(List<String> dependsOn) { this.dependsOn = dependsOn; }
    public String getFallbackTool() { return fallbackTool; }
    public void setFallbackTool(String fallbackTool) { this.fallbackTool = fallbackTool; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
}