package com.mcp.engine.orchestrator;

import com.mcp.engine.agent.Agent;
import com.mcp.engine.agent.LLMRequest;
import com.mcp.engine.agent.bus.A2aMessageBus;
import com.mcp.engine.agent.card.A2aProtocol;
import com.mcp.engine.agent.card.AgentCard;
import com.mcp.engine.agent.registry.AgentRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多 Agent 协作编排器
 * <p>
 * 支持三种协作模式：
 * <ul>
 *   <li>PIPELINE - 流水线：Agent 依次处理，前一个输出作为后一个输入</li>
 *   <li>PARALLEL - 并行：多个 Agent 同时处理同一任务，合并结果</li>
 *   <li>DELEGATE - 委派：根据能力匹配选择最佳 Agent 处理</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiAgentOrchestrator {

    private final AgentRegistry agentRegistry;
    private final A2aMessageBus messageBus;

    private final Map<String, String> activeDelegations = new ConcurrentHashMap<>();

    public enum WorkflowMode {
        PIPELINE,
        PARALLEL,
        DELEGATE
    }

    /**
     * 委派模式：根据技能匹配选择最佳 Agent 处理任务
     */
    public Mono<String> delegate(String task, List<String> requiredSkills) {
        return agentRegistry.findBestAgent(task, requiredSkills)
                .flatMap(match -> {
                    if (match == null) {
                        return Mono.just("[MultiAgent] 没有可用的 Agent 处理此任务。");
                    }
                    log.info("[MultiAgent] Delegating to agent: {} (score={}, skills={})",
                            match.agentId(), match.score(), match.matchedSkills());

                    Agent agent = agentRegistry.getAgent(match.agentId()).orElse(null);
                    if (agent == null) {
                        return Mono.just("[MultiAgent] Agent 不可用: " + match.agentId());
                    }

                    activeDelegations.put(match.agentId(), task);
                    return agent.execute(LLMRequest.of("你是一个专业、友好的智能助手。", task))
                            .doOnSuccess(result -> {
                                activeDelegations.remove(match.agentId());
                                log.info("[MultiAgent] Task completed by agent: {}", match.agentId());
                            })
                            .doOnError(e -> {
                                activeDelegations.remove(match.agentId());
                                log.error("[MultiAgent] Task failed: agent={}, error={}",
                                        match.agentId(), e.getMessage());
                            });
                });
    }

    /**
     * 流水线模式：依次执行 Agent，每个 Agent 的输出作为下一个的输入
     */
    public Mono<String> pipeline(String task, List<String> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return Mono.just(task);
        }

        Mono<String> pipeline = Mono.just(task);
        for (String agentId : agentIds) {
            final String currentAgentId = agentId;
            pipeline = pipeline.flatMap(currentInput -> {
                Agent agent = agentRegistry.getAgent(currentAgentId).orElse(null);
                if (agent == null) {
                    log.warn("[MultiAgent] Pipeline agent not found: {}", currentAgentId);
                    return Mono.just(currentInput);
                }
                log.info("[MultiAgent] Pipeline step: {} processing...", agent.getName());
                return agent.execute(LLMRequest.of("你是一个专业、友好的智能助手。", currentInput));
            });
        }
        return pipeline;
    }

    /**
     * 并行模式：多个 Agent 同时处理同一任务，合并结果
     */
    public Mono<String> parallel(String task, List<String> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return Mono.just(task);
        }

        List<Mono<String>> results = agentIds.stream()
                .map(agentId -> {
                    Agent agent = agentRegistry.getAgent(agentId).orElse(null);
                    if (agent == null) {
                        return Mono.just("[Agent " + agentId + " 不可用]");
                    }
                    log.info("[MultiAgent] Parallel execution: {}", agent.getName());
                    return agent.execute(LLMRequest.of("你是一个专业、友好的智能助手。", task))
                            .map(result -> "[" + agent.getName() + "]: " + result)
                            .onErrorReturn("[" + agent.getName() + "]: 执行出错");
                })
                .toList();

        return Flux.merge(results)
                .collectList()
                .map(resultsList -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("=== 多 Agent 并行执行结果 ===\n\n");
                    for (int i = 0; i < resultsList.size(); i++) {
                        sb.append("── Agent ").append(i + 1).append(" ──\n");
                        sb.append(resultsList.get(i)).append("\n\n");
                    }
                    return sb.toString();
                });
    }

    /**
     * A2A 委派：通过消息总线发送任务到指定 Agent
     */
    public Mono<A2aProtocol.AgentTaskResponse> sendA2aTask(
            String fromAgentId, String toAgentId, String task, String context) {
        A2aProtocol.AgentTaskRequest request = A2aProtocol.AgentTaskRequest.builder()
                .fromAgentId(fromAgentId)
                .toAgentId(toAgentId)
                .task(task)
                .context(context)
                .build();
        return messageBus.sendTask(request);
    }

    public List<AgentCard> getAllAgentCards() {
        return agentRegistry.getAllCards();
    }

    public int getAgentCount() {
        return agentRegistry.agentCount();
    }
}