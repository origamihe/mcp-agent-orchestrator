package com.mcp.common.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 流水线定义 — 定义工具流水线的步骤序列。
 *
 * 示例（搜索→分析→报告）：
 * <pre>
 * PipelineDefinition pipeline = PipelineDefinition.builder()
 *     .id("search-analyze-report")
 *     .name("搜索分析报告")
 *     .description("搜索信息 → 分析整理 → 生成报告")
 *     .addStep(PipelineStep.builder()
 *         .id("search")
 *         .toolName("web_search")
 *         .description("搜索相关信息")
 *         .build())
 *     .addStep(PipelineStep.builder()
 *         .id("analyze")
 *         .toolName("analyze_content")
 *         .description("分析搜索结果")
 *         .dependsOn("search")
 *         .inputMapping("content", "search.result")
 *         .build())
 *     .addStep(PipelineStep.builder()
 *         .id("report")
 *         .toolName("generate_report")
 *         .description("生成报告")
 *         .dependsOn("analyze")
 *         .build())
 *     .build();
 * </pre>
 */
public class PipelineDefinition {
    private String id;
    private String name;
    private String description;
    private List<PipelineStep> steps;
    private int maxTotalTimeoutMs;
    private boolean continueOnError;
    private boolean parallelizeIndependent;

    public PipelineDefinition() {
        this.id = UUID.randomUUID().toString();
        this.steps = new ArrayList<>();
        this.maxTotalTimeoutMs = 300_000;
        this.continueOnError = false;
        this.parallelizeIndependent = true;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final PipelineDefinition def = new PipelineDefinition();

        public Builder id(String id) { def.id = id; return this; }
        public Builder name(String name) { def.name = name; return this; }
        public Builder description(String description) { def.description = description; return this; }
        public Builder addStep(PipelineStep step) { def.steps.add(step); return this; }
        public Builder steps(List<PipelineStep> steps) { def.steps = steps; return this; }
        public Builder maxTotalTimeoutMs(int ms) { def.maxTotalTimeoutMs = ms; return this; }
        public Builder continueOnError(boolean continueOnError) { def.continueOnError = continueOnError; return this; }
        public Builder parallelizeIndependent(boolean parallelizeIndependent) { def.parallelizeIndependent = parallelizeIndependent; return this; }
        public PipelineDefinition build() { return def; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<PipelineStep> getSteps() { return steps; }
    public void setSteps(List<PipelineStep> steps) { this.steps = steps; }

    public int getMaxTotalTimeoutMs() { return maxTotalTimeoutMs; }
    public void setMaxTotalTimeoutMs(int maxTotalTimeoutMs) { this.maxTotalTimeoutMs = maxTotalTimeoutMs; }

    public boolean isContinueOnError() { return continueOnError; }
    public void setContinueOnError(boolean continueOnError) { this.continueOnError = continueOnError; }

    public boolean isParallelizeIndependent() { return parallelizeIndependent; }
    public void setParallelizeIndependent(boolean parallelizeIndependent) { this.parallelizeIndependent = parallelizeIndependent; }
}