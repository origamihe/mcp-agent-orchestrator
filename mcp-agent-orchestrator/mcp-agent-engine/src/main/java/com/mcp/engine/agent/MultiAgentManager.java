package com.mcp.engine.agent;

import com.mcp.common.agent.ConsensusResult;
import com.mcp.common.agent.MultiAgentContext;
import com.mcp.engine.agent.card.AgentCard;
import com.mcp.engine.agent.consensus.AgentConsensus;
import com.mcp.engine.agent.consensus.AgentDebate;
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
 * 2. Agent 委派 / 流水线 / 并行 / 共识 / 辩论
 * 3. Agent 注册与发现
 * 4. Agent 统计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentManager {

    private final MultiAgentOrchestrator orchestrator;
    private final AgentRegistry agentRegistry;
    private final AgentConsensus agentConsensus;
    private final AgentDebate agentDebate;

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

    /**
     * 多数投票共识 — 多 Agent 并行回答，按多数派选出最佳答案。
     */
    public Mono<ConsensusResult> majorityVote(String question, List<String> agentIds) {
        return agentConsensus.majorityVote(question, agentIds);
    }

    /**
     * 裁判评估共识 — LLM Judge 评分各 Agent 答案，选最高分。
     */
    public Mono<ConsensusResult> judgeEvaluation(String question, List<String> agentIds) {
        return agentConsensus.judgeEvaluation(question, agentIds);
    }

    /**
     * 加权投票共识 — 按 Agent 匹配度加权计分。
     */
    public Mono<ConsensusResult> weightedVote(String question, List<String> agentIds) {
        return agentConsensus.weightedVote(question, agentIds);
    }

    /**
     * 多 Agent 辩论 — 多轮辩论后达成共识。
     */
    public Mono<ConsensusResult> debate(String question, List<String> agentIds, int maxRounds) {
        return agentDebate.debate(question, agentIds, maxRounds);
    }

    /**
     * 多 Agent 辩论（默认 2 轮）。
     */
    public Mono<ConsensusResult> debate(String question, List<String> agentIds) {
        return agentDebate.debate(question, agentIds);
    }
}