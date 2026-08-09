package com.mcp.common.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流水线步骤 — 单个工具执行步骤。
 *
 * 支持：
 * - 输入映射：从上游步骤的输出中提取字段作为本步骤的输入参数
 * - 条件执行：当某个条件满足时才执行
 * - 备选工具：主工具失败时自动切换到备选工具
 * - 并行执行：与同组其他步骤并行执行
 */
public class PipelineStep {
    private String id;
    private String toolName;
    private String description;
    private Map<String, Object> staticArgs;
    private Map<String, String> inputMapping;
    private String fallbackTool;
    private List<String> dependsOn;
    private String condition;
    private String parallelGroup;
    private int maxRetries;
    private long timeoutMs;

    public PipelineStep() {
        this.staticArgs = new LinkedHashMap<>();
        this.inputMapping = new LinkedHashMap<>();
        this.dependsOn = new ArrayList<>();
        this.maxRetries = 1;
        this.timeoutMs = 30_000;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final PipelineStep step = new PipelineStep();

        public Builder id(String id) { step.id = id; return this; }
        public Builder toolName(String toolName) { step.toolName = toolName; return this; }
        public Builder description(String desc) { step.description = desc; return this; }
        public Builder staticArg(String key, Object value) { step.staticArgs.put(key, value); return this; }
        public Builder staticArgs(Map<String, Object> args) { step.staticArgs.putAll(args); return this; }
        public Builder inputMapping(String paramName, String sourcePath) { step.inputMapping.put(paramName, sourcePath); return this; }
        public Builder inputMappings(Map<String, String> mappings) { step.inputMapping.putAll(mappings); return this; }
        public Builder fallbackTool(String tool) { step.fallbackTool = tool; return this; }
        public Builder dependsOn(String stepId) { step.dependsOn.add(stepId); return this; }
        public Builder dependsOn(List<String> stepIds) { step.dependsOn.addAll(stepIds); return this; }
        public Builder condition(String condition) { step.condition = condition; return this; }
        public Builder parallelGroup(String group) { step.parallelGroup = group; return this; }
        public Builder maxRetries(int maxRetries) { step.maxRetries = maxRetries; return this; }
        public Builder timeoutMs(long timeoutMs) { step.timeoutMs = timeoutMs; return this; }
        public PipelineStep build() { return step; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getStaticArgs() { return staticArgs; }
    public void setStaticArgs(Map<String, Object> staticArgs) { this.staticArgs = staticArgs; }

    public Map<String, String> getInputMapping() { return inputMapping; }
    public void setInputMapping(Map<String, String> inputMapping) { this.inputMapping = inputMapping; }

    public String getFallbackTool() { return fallbackTool; }
    public void setFallbackTool(String fallbackTool) { this.fallbackTool = fallbackTool; }

    public List<String> getDependsOn() { return dependsOn; }
    public void setDependsOn(List<String> dependsOn) { this.dependsOn = dependsOn; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public String getParallelGroup() { return parallelGroup; }
    public void setParallelGroup(String parallelGroup) { this.parallelGroup = parallelGroup; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
}