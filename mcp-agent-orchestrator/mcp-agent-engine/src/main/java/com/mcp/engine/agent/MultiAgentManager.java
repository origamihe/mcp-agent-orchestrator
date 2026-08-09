package com.mcp.engine.agent;

import com.mcp.common.agent.MultiAgentContext;
import com.mcp.engine.agent.card.AgentCard;
import com.mcp.engine.agent.registry.AgentRegistry;
import com.mcp.engine.orchestrator.MultiAgentOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 统一多 Agent 管理器 — 多 Agent 系统的对外门面。
 *
 * 职责：
 * 1. 多 Agent 上下文生成（供 Prompt 注入）
 * 2. Agent 委派 / 流水线 / 并行执行
 * 3. Agent 注册与发现
 * 4. Agent 统计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentManager {

    private final MultiAgentOrchestrator orchestrator;
    private final AgentRegistry agentRegistry;

    /**
     * 生成多 Agent 上下文 — 供 Prompt 注入。
     */
    public MultiAgentContext buildMultiAgentContext(String task, List<String> requiredSkills) {
        List<AgentCard> allCards = orchestrator.getAllAgentCards();

        List<MultiAgentContext.AgentInfo> availableAgents = allCards.stream()
                .map(card -> {
                    MultiAgentContext.AgentInfo info = MultiAgentContext.AgentInfo.of(
                            card.getAgentId(), card.getAgentName(),
                            card.getAgentType() != null ? card.getAgentType().name() : "GENERAL",
                            card.getSkills());
                    info.setDescription(card.getDescription());
                    return info;
                })
                .collect(Collectors.toList());

        List<MultiAgentContext.AgentInfo> matchedAgents = List.of();
        if (requiredSkills != null && !requiredSkills.isEmpty()) {
            List<AgentRegistry.AgentMatch> matches = agentRegistry.matchBySkills(requiredSkills);
            matchedAgents = matches.stream()
                    .map(m -> {
                        AgentCard card = m.card();
                        MultiAgentContext.AgentInfo info = MultiAgentContext.AgentInfo.of(
                                m.agentId(), card.getAgentName(),
                                card.getAgentType() != null ? card.getAgentType().name() : "GENERAL",
                                card.getSkills());
                        info.setMatchScore(m.score());
                        info.setDescription(card.getDescription());
                        return info;
                    })
                    .collect(Collectors.toList());
        }

        return MultiAgentContext.builder()
                .availableAgents(availableAgents)
                .matchedAgents(matchedAgents)
                .totalAgents(orchestrator.getAgentCount())
                .build();
    }

    /**
     * 委派任务到最佳匹配 Agent。
     */
    public Mono<String> delegate(String task, List<String> requiredSkills) {
        return orchestrator.delegate(task, requiredSkills);
    }

    /**
     * 流水线执行：依次执行 Agent。
     */
    public Mono<String> pipeline(String task, List<String> agentIds) {
        return orchestrator.pipeline(task, agentIds);
    }

    /**
     * 并行执行：多个 Agent 同时处理。
     */
    public Mono<String> parallel(String task, List<String> agentIds) {
        return orchestrator.parallel(task, agentIds);
    }

    /**
     * 获取所有 Agent 卡片。
     */
    public List<AgentCard> getAllAgentCards() {
        return orchestrator.getAllAgentCards();
    }

    /**
     * 获取 Agent 数量。
     */
    public int getAgentCount() {
        return orchestrator.getAgentCount();
    }

    /**
     * 统计多 Agent 数据。
     */
    public MultiAgentStats getStats() {
        List<AgentCard> cards = orchestrator.getAllAgentCards();
        long chatCount = cards.stream().filter(c -> c.getAgentType() == AgentCard.AgentType.CHAT).count();
        long codeCount = cards.stream().filter(c -> c.getAgentType() == AgentCard.AgentType.CODE).count();
        long searchCount = cards.stream().filter(c -> c.getAgentType() == AgentCard.AgentType.SEARCH).count();
        long plannerCount = cards.stream().filter(c -> c.getAgentType() == AgentCard.AgentType.PLANNER).count();

        return new MultiAgentStats(
                cards.size(),
                chatCount, codeCount, searchCount, plannerCount
        );
    }

    public record MultiAgentStats(
            int totalAgents,
            long chatAgents,
            long codeAgents,
            long searchAgents,
            long plannerAgents
    ) {
        public long otherAgents() {
            return totalAgents - chatAgents - codeAgents - searchAgents - plannerAgents;
        }
    }
}