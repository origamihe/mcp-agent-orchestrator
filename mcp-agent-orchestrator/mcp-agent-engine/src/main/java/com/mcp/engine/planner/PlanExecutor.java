package com.mcp.engine.planner;

import com.mcp.common.pipeline.PipelineDefinition;
import com.mcp.common.pipeline.PipelineResult;
import com.mcp.common.planner.PlanDAG;
import com.mcp.engine.pipeline.ToolPipelineManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * 计划执行器 — 将 Planner 生成的 PlanDAG 转换为 PipelineDefinition 并执行。
 *
 * 职责：
 * 1. 接收 PlanDAG
 * 2. 转换为 PipelineDefinition
 * 3. 注册并执行
 * 4. 返回 PipelineResult
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanExecutor {

    private final PlanDAGConverter converter;
    private final ToolPipelineManager pipelineManager;

    /**
     * 执行 PlanDAG。
     */
    public Mono<PipelineResult> execute(PlanDAG dag, Map<String, Object> initialInput) {
        if (dag.getNodes() == null || dag.getNodes().isEmpty()) {
            log.info("[PlanExecutor] Empty DAG, skipping execution");
            return Mono.empty();
        }

        PipelineDefinition pipeline = converter.convert(dag);
        pipelineManager.registerPipeline(pipeline);

        log.info("[PlanExecutor] Executing plan: {} ({} nodes)", dag.getIntent(), dag.getNodes().size());
        return pipelineManager.execute(pipeline.getId(), initialInput != null ? initialInput : Map.of())
                .doOnSuccess(result -> log.info("[PlanExecutor] Plan completed: status={}, steps={}/{}",
                        result.getStatus(), result.getSuccessSteps(), result.getTotalSteps()))
                .doOnError(error -> log.error("[PlanExecutor] Plan execution failed: {}", error.getMessage()));
    }

    /**
     * 执行 PlanDAG（无初始输入）。
     */
    public Mono<PipelineResult> execute(PlanDAG dag) {
        return execute(dag, Map.of());
    }

    /**
     * 从给定节点列表构建并执行 DAG 计划。
     */
    public Mono<PipelineResult> executeFromNodes(
            String intent,
            java.util.List<com.mcp.common.planner.PlanNode> nodes,
            Map<String, Object> initialInput) {

        PlanDAG dag = PlanDAG.builder()
                .id("plan-" + UUID.randomUUID().toString().substring(0, 8))
                .intent(intent)
                .nodes(nodes)
                .build();

        return execute(dag, initialInput);
    }
}