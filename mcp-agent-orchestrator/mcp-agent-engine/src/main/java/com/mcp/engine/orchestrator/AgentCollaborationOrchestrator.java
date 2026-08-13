package com.mcp.engine.orchestrator;

import com.mcp.engine.agent.Agent;
import com.mcp.engine.agent.LLMRequest;
import com.mcp.engine.agent.bus.A2aMessageBus;
import com.mcp.engine.agent.card.A2aProtocol;
import com.mcp.engine.agent.card.AgentCard;
import com.mcp.engine.agent.registry.AgentRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Agent 协作编排器 — 实现实际的多 Agent 链式协作场景。
 *
 * 通过 {@link CollaborationPipelineRegistry} 管理和执行预定义的协作流水线。
 * 新增流水线只需在 Registry 中注册，无需修改本类。
 *
 * 基础 PIPELINE / PARALLEL / DELEGATE 模式参见 {@link MultiAgentOrchestrator}。
 */
@Slf4j
@Component
public class AgentCollaborationOrchestrator {

    private final AgentRegistry agentRegistry;
    private final A2aMessageBus messageBus;
    private final CollaborationPipelineRegistry pipelineRegistry;

    public AgentCollaborationOrchestrator(AgentRegistry agentRegistry,
                                          A2aMessageBus messageBus,
                                          CollaborationPipelineRegistry pipelineRegistry) {
        this.agentRegistry = agentRegistry;
        this.messageBus = messageBus;
        this.pipelineRegistry = pipelineRegistry;
    }

    /**
     * 通用流水线执行入口 — 按名称查找并执行流水线。
     *
     * @param pipelineName 流水线名称（如 "searchToCodeToChat", "codeToReviewToChat", "searchToChat"）
     * @param userMessage  用户原始消息
     * @return 流水线最终输出
     */
    public Mono<String> execute(String pipelineName, String userMessage) {
        CollaborationPipeline pipeline = pipelineRegistry.get(pipelineName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown pipeline: " + pipelineName));

        log.info("[Collab] Pipeline '{}' start ({} stages)", pipelineName, pipeline.stageCount());

        return executePipeline(pipeline, userMessage)
                .doOnSuccess(result -> log.info("[Collab] Pipeline '{}' complete ({} chars)",
                        pipelineName, result != null ? result.length() : 0))
                .doOnError(e -> log.error("[Collab] Pipeline '{}' failed: {}", pipelineName, e.getMessage()));
    }

    /**
     * Search → Code → Chat 流水线。
     */
    public Mono<String> searchToCodeToChat(String userMessage) {
        return execute("searchToCodeToChat", userMessage);
    }

    /**
     * Code → Review → Chat 流水线。
     */
    public Mono<String> codeToReviewToChat(String userMessage) {
        return execute("codeToReviewToChat", userMessage);
    }

    /**
     * Search → Chat 流水线。
     */
    public Mono<String> searchToChat(String userMessage) {
        return execute("searchToChat", userMessage);
    }

    /**
     * 通过 A2A 消息总线委派任务到指定 Agent。
     */
    public Mono<String> a2aDelegation(String fromAgentId, String toAgentId, String task, String context) {
        log.info("[Collab] A2A delegation: {} -> {} (task: {})", fromAgentId, toAgentId,
                task.length() > 50 ? task.substring(0, 50) + "..." : task);

        A2aProtocol.AgentTaskRequest request = A2aProtocol.AgentTaskRequest.builder()
                .fromAgentId(fromAgentId)
                .toAgentId(toAgentId)
                .task(task)
                .context(context)
                .build();

        return messageBus.sendTask(request)
                .map(response -> {
                    if (response.isSuccess()) {
                        log.info("[Collab] A2A task {} completed by {}", response.getTaskId(), toAgentId);
                        return response.getResult();
                    } else {
                        log.warn("[Collab] A2A task {} failed: {}", response.getTaskId(), response.getErrorMessage());
                        return "[A2A 委派失败] " + (response.getErrorMessage() != null ? response.getErrorMessage() : "未知错误");
                    }
                });
    }

    public List<AgentCard> getAvailableAgents() {
        return agentRegistry.getAllCards();
    }

    public List<CollaborationPipeline> getAvailablePipelines() {
        return pipelineRegistry.getAll();
    }

    private Mono<String> executePipeline(CollaborationPipeline pipeline, String userMessage) {
        List<CollaborationPipelineStage> stages = pipeline.stages();
        Mono<String> chain = Mono.just(userMessage);

        for (int i = 0; i < stages.size(); i++) {
            final int stageIndex = i;
            final CollaborationPipelineStage stage = stages.get(i);
            final boolean isFirstStage = (i == 0);

            chain = chain.flatMap(input -> {
                String renderedPrompt = stage.renderPrompt(userMessage, isFirstStage ? null : input);
                return executeStage(stage, renderedPrompt, stageIndex + 1, pipeline.stageCount());
            });
        }

        return chain;
    }

    private Mono<String> executeStage(CollaborationPipelineStage stage, String renderedPrompt,
                                       int stageNum, int totalStages) {
        Agent agent = agentRegistry.getAgent(stage.agentName()).orElse(null);
        if (agent == null) {
            log.warn("[Collab] Agent '{}' not available for stage {}/{}, passing through",
                    stage.agentName(), stageNum, totalStages);
            return Mono.just(renderedPrompt);
        }

        LLMRequest request = LLMRequest.of(stage.systemPrompt(), renderedPrompt);
        log.info("[Collab] Stage {}/{}: {} executing...", stageNum, totalStages, stage.agentName());
        return agent.execute(request)
                .doOnSuccess(r -> log.info("[Collab] Stage {}/{}: {} completed ({} chars)",
                        stageNum, totalStages, stage.agentName(), r != null ? r.length() : 0));
    }
}