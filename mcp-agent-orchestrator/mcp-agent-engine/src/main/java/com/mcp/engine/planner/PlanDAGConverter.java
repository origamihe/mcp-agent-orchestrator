package com.mcp.engine.planner;

import com.mcp.common.pipeline.PipelineDefinition;
import com.mcp.common.pipeline.PipelineStep;
import com.mcp.common.planner.PlanDAG;
import com.mcp.common.planner.PlanNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 将 PlanDAG 转换为 PipelineDefinition，供 ToolPipelineManager 执行。
 */
@Slf4j
@Component
public class PlanDAGConverter {

    /**
     * 将 PlanDAG 转换为 PipelineDefinition。
     * 每个 PlanNode 映射为一个 PipelineStep。
     */
    public PipelineDefinition convert(PlanDAG dag) {
        String pipelineId = dag.getId() != null ? dag.getId() : "plan-" + UUID.randomUUID().toString().substring(0, 8);
        String pipelineName = dag.getIntent() != null ? dag.getIntent() : "Plan Pipeline";

        PipelineDefinition.Builder pipelineBuilder = PipelineDefinition.builder()
                .id(pipelineId)
                .name(pipelineName)
                .description(dag.getReasoning())
                .parallelizeIndependent(true);

        List<String> nodeIds = new ArrayList<>();
        for (PlanNode node : dag.getNodes()) {
            PipelineStep step = convertNode(node);
            pipelineBuilder.addStep(step);
            nodeIds.add(node.getId());
        }

        log.info("[PlanDAGConverter] Converted DAG to pipeline: {} nodes -> pipeline '{}'",
                dag.getNodes().size(), pipelineName);

        return pipelineBuilder.build();
    }

    private PipelineStep convertNode(PlanNode node) {
        PipelineStep.Builder stepBuilder = PipelineStep.builder()
                .id(node.getId())
                .toolName(node.getToolName() != null ? node.getToolName() : node.getCapability())
                .description(node.getDescription());

        if (node.getStaticArgs() != null) {
            node.getStaticArgs().forEach(stepBuilder::staticArg);
        }

        if (node.getInputMapping() != null) {
            node.getInputMapping().forEach(stepBuilder::inputMapping);
        }

        if (node.getDependsOn() != null) {
            node.getDependsOn().forEach(stepBuilder::dependsOn);
        }

        if (node.getFallbackTool() != null) {
            stepBuilder.fallbackTool(node.getFallbackTool());
        }

        stepBuilder.maxRetries(node.getMaxRetries());
        stepBuilder.timeoutMs(node.getTimeoutMs());

        return stepBuilder.build();
    }
}