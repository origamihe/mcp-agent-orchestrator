package com.mcp.engine.execution;

import com.mcp.common.channel.ContextRequirement;
import com.mcp.common.channel.WorkingContext;
import com.mcp.common.context.RequestContext;
import com.mcp.common.identity.MemoryIdentity;
import com.mcp.engine.execution.ExecutionPlan.ExecutionMode;
import com.mcp.engine.execution.ExecutionPlan.MemoryPolicy;
import com.mcp.engine.execution.ExecutionPlan.TimeoutPolicy;
import com.mcp.engine.execution.ExecutionPlan.ToolPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 执行计划构建器 — 负责将 RequestContext 转换为 ExecutionPlan。
 *
 * 职责：
 * 1. 分析请求特征（任务类型、上下文需求、Agent 选择）
 * 2. 构建 ExecutionPlan（包含 Agent、Pipeline、Tool、Memory、Timeout 策略）
 * 3. 替代 DefaultAgentOrchestrator.internalProcess() 中的 if/else 路由链
 *
 * 设计原则：
 * - 纯函数式：输入 RequestContext，输出 ExecutionPlan
 * - 不依赖任何 I/O 操作
 * - 所有决策都是声明式的，可通过 Configuration 覆盖
 */
@Slf4j
@Component
public class ExecutionPlanner {

    public ExecutionPlan plan(RequestContext ctx, ContextRequirement requirement, WorkingContext workingCtx) {
        MemoryIdentity identity = ctx.getIdentity();
        String currentTask = workingCtx.getCurrentTask();

        ExecutionPlan.ExecutionPlanBuilder builder = ExecutionPlan.builder()
                .identity(identity)
                .contextRequirement(requirement);

        if (currentTask == null || currentTask.isBlank()) {
            return planFastPath(builder, workingCtx);
        }

        if (currentTask.startsWith("SEARCH:")) {
            return planSearchAgent(builder, workingCtx);
        }

        if (currentTask.startsWith("DOCX_GENERATION:") || currentTask.startsWith("PPT_GENERATION:")) {
            return planDocumentGeneration(builder, currentTask, workingCtx);
        }

        return planFastPath(builder, workingCtx);
    }

    private ExecutionPlan planSearchAgent(ExecutionPlan.ExecutionPlanBuilder builder, WorkingContext workingCtx) {
        log.info("[ExecutionPlanner] Planning SEARCH agent execution");
        return builder
                .mode(ExecutionMode.AGENT)
                .agentId("search-agent")
                .toolPolicy(ToolPolicy.SEARCH_ALLOWED)
                .memoryPolicy(MemoryPolicy.READ_ONLY)
                .timeoutPolicy(TimeoutPolicy.LONG)
                .build();
    }

    private ExecutionPlan planDocumentGeneration(ExecutionPlan.ExecutionPlanBuilder builder,
                                                  String currentTask, WorkingContext workingCtx) {
        boolean isDocx = currentTask.startsWith("DOCX_GENERATION:");
        String pipelineId = isDocx ? "search-and-generate-docx" : "search-and-generate-ppt";

        log.info("[ExecutionPlanner] Planning {} execution: pipeline={}, fallback=search-agent",
                isDocx ? "DOCX_GENERATION" : "PPT_GENERATION", pipelineId);

        return builder
                .mode(ExecutionMode.PIPELINE)
                .pipelineId(pipelineId)
                .agentId("search-agent")
                .toolPolicy(ToolPolicy.SEARCH_ALLOWED)
                .memoryPolicy(MemoryPolicy.READ_ONLY)
                .timeoutPolicy(TimeoutPolicy.LONG)
                .build();
    }

    private ExecutionPlan planFastPath(ExecutionPlan.ExecutionPlanBuilder builder, WorkingContext workingCtx) {
        log.info("[ExecutionPlanner] Planning FAST_PATH execution");
        return builder
                .mode(ExecutionMode.FAST_PATH)
                .toolPolicy(ToolPolicy.DEFAULT)
                .memoryPolicy(MemoryPolicy.DEFAULT)
                .timeoutPolicy(TimeoutPolicy.DEFAULT)
                .build();
    }
}