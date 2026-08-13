package com.mcp.engine.orchestrator;

import java.util.List;

/**
 * 协作流水线定义 — 描述一个完整的多 Agent 链式协作流水线。
 *
 * 流水线按阶段顺序执行，每个阶段的输出作为下一阶段的输入（{@code {input}} 占位符）。
 * 第一个阶段的 {@code {input}} 为原始用户消息。
 *
 * @param name        流水线名称（如 "searchToCodeToChat"）
 * @param description 流水线描述
 * @param stages      有序的阶段列表（至少 2 个阶段）
 */
public record CollaborationPipeline(
        String name,
        String description,
        List<CollaborationPipelineStage> stages
) {
    public CollaborationPipeline {
        if (stages == null || stages.size() < 2) {
            throw new IllegalArgumentException("Pipeline '" + name + "' must have at least 2 stages");
        }
    }

    public int stageCount() {
        return stages.size();
    }
}