package com.mcp.engine.agent.consensus;

import com.mcp.common.agent.ConsensusResult;
import com.mcp.common.agent.ConsensusResult.AgentVote;
import com.mcp.common.agent.ConsensusResult.VoteStrategy;
import com.mcp.engine.agent.Agent;
import com.mcp.engine.agent.LLMRequest;
import com.mcp.engine.agent.registry.AgentRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 多 Agent 辩论机制 — Agent 间互相审查对方输出，迭代优化答案。
 *
 * <p>辩论流程：
 * <ol>
 *   <li>Round 0 — 各 Agent 独立回答</li>
 *   <li>Round N — 各 Agent 审查其他 Agent 的答案，给出批评和改进建议</li>
 *   <li>Round N — 各 Agent 根据反馈改进自己的答案</li>
 *   <li>最终轮 — 各 Agent 给出最终答案，由 Consensus 选出最佳</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentDebate {

    private final AgentRegistry agentRegistry;
    private final AgentConsensus agentConsensus;

    private static final int DEFAULT_MAX_ROUNDS = 2;

    private static final String REVIEW_PROMPT = """
            你正在参与一场多 Agent 辩论。请审查以下其他 Agent 的答案，并以批判性思维指出：
            1. 哪些部分有错误或遗漏
            2. 哪些部分的推理不够严谨
            3. 你可以从中学到什么来改进自己的答案

            问题：%s

            其他 Agent 的答案：
            %s

            请给出你的审查意见（不要重复原答案，只给出批评和改进建议）。
            """;

    private static final String REFINE_PROMPT = """
            你正在参与一场多 Agent 辩论。请根据其他 Agent 的反馈改进你的答案。

            问题：%s

            你之前的答案：
            %s

            其他 Agent 的反馈：
            %s

            请给出改进后的答案，综合所有反馈中的合理建议。
            """;

    /**
     * 执行辩论，返回共识结果。
     *
     * @param question 辩论问题
     * @param agentIds 参与辩论的 Agent ID 列表
     * @param maxRounds 最大辩论轮数
     */
    public Mono<ConsensusResult> debate(String question, List<String> agentIds, int maxRounds) {
        long start = System.currentTimeMillis();
        List<String> ids = resolveAgentIds(agentIds);
        int rounds = Math.max(1, Math.min(maxRounds, DEFAULT_MAX_ROUNDS + 1));

        List<AgentVote> currentVotes = new ArrayList<>();

        return debateRounds(question, ids, rounds, currentVotes, 0, start);
    }

    /**
     * 默认辩论（2 轮）。
     */
    public Mono<ConsensusResult> debate(String question, List<String> agentIds) {
        return debate(question, agentIds, DEFAULT_MAX_ROUNDS);
    }

    private Mono<ConsensusResult> debateRounds(String question, List<String> agentIds,
                                                int maxRounds, List<AgentVote> votes,
                                                int currentRound, long start) {
        if (currentRound >= maxRounds) {
            return buildFinalResult(question, votes, currentRound, start);
        }

        if (currentRound == 0) {
            return executeRound0(question, agentIds, votes)
                    .flatMap(v -> debateRounds(question, agentIds, maxRounds, v, 1, start));
        }

        return executeReviewRound(question, agentIds, votes, currentRound)
                .flatMap(v -> executeRefineRound(question, agentIds, v, currentRound))
                .flatMap(v -> debateRounds(question, agentIds, maxRounds, v, currentRound + 1, start));
    }

    /**
     * Round 0: 各 Agent 独立回答。
     */
    private Mono<List<AgentVote>> executeRound0(String question, List<String> agentIds, List<AgentVote> existing) {
        List<Mono<AgentVote>> monos = agentIds.stream()
                .map(agentId -> {
                    Agent agent = agentRegistry.getAgent(agentId).orElse(null);
                    if (agent == null) {
                        return Mono.just(AgentVote.of(agentId, agentId, "Agent 不可用", 0.0));
                    }
                    return agent.execute(LLMRequest.of("你是一个专业、友好的智能助手。", question))
                            .map(answer -> AgentVote.of(agentId, agent.getName(), answer, 0.5))
                            .onErrorResume(e -> Mono.just(AgentVote.of(agentId, agent.getName(),
                                    "执行失败: " + e.getMessage(), 0.0)));
                })
                .collect(Collectors.toList());

        return Mono.zip(monos, results -> {
            List<AgentVote> result = new ArrayList<>(existing);
            for (Object r : results) {
                result.add((AgentVote) r);
            }
            return result;
        });
    }

    /**
     * 审查轮：各 Agent 审查其他 Agent 的答案。
     */
    private Mono<List<AgentVote>> executeReviewRound(String question, List<String> agentIds,
                                                      List<AgentVote> currentVotes, int round) {
        List<Mono<AgentVote>> monos = new ArrayList<>();
        for (int i = 0; i < agentIds.size(); i++) {
            final int idx = i;
            String agentId = agentIds.get(i);
            Agent agent = agentRegistry.getAgent(agentId).orElse(null);
            if (agent == null) continue;

            List<String> otherAnswers = new ArrayList<>();
            for (int j = 0; j < currentVotes.size(); j++) {
                if (j != idx) {
                    AgentVote v = currentVotes.get(j);
                    otherAnswers.add("[" + v.getAgentName() + "]:\n" + v.getAnswer());
                }
            }

            String reviews = String.join("\n\n---\n\n", otherAnswers);
            String reviewPrompt = REVIEW_PROMPT.formatted(question, reviews);

            monos.add(agent.execute(LLMRequest.of("你是一个批判性思维专家。", reviewPrompt))
                    .map(review -> {
                        AgentVote v = new AgentVote();
                        v.setAgentId(agentId);
                        v.setAgentName(agent.getName());
                        v.setAnswer(review);
                        v.setConfidence(0.5);
                        return v;
                    })
                    .onErrorResume(e -> {
                        AgentVote v = AgentVote.of(agentId, agent.getName(),
                                currentVotes.get(idx).getAnswer(), 0.5);
                        return Mono.just(v);
                    }));
        }

        return Mono.zip(monos, results -> {
            List<AgentVote> result = new ArrayList<>();
            int idx = 0;
            for (Object r : results) {
                AgentVote review = (AgentVote) r;
                review.setAnswer(currentVotes.get(idx).getAnswer() + "\n\n[审查意见]\n" + review.getAnswer());
                result.add(review);
                idx++;
            }
            return result;
        });
    }

    /**
     * 改进轮：各 Agent 根据反馈改进答案。
     */
    private Mono<List<AgentVote>> executeRefineRound(String question, List<String> agentIds,
                                                      List<AgentVote> currentVotes, int round) {
        List<Mono<AgentVote>> monos = new ArrayList<>();
        for (int i = 0; i < agentIds.size(); i++) {
            final int idx = i;
            String agentId = agentIds.get(i);
            Agent agent = agentRegistry.getAgent(agentId).orElse(null);
            if (agent == null) continue;

            List<String> feedbacks = new ArrayList<>();
            for (int j = 0; j < currentVotes.size(); j++) {
                if (j != idx) {
                    AgentVote v = currentVotes.get(j);
                    String answer = v.getAnswer();
                    int reviewIdx = answer.indexOf("[审查意见]");
                    if (reviewIdx > 0) {
                        feedbacks.add("[" + v.getAgentName() + "]: " + answer.substring(reviewIdx));
                    }
                }
            }

            String feedback = feedbacks.isEmpty() ? "无其他 Agent 反馈" : String.join("\n\n", feedbacks);
            String refinePrompt = REFINE_PROMPT.formatted(question, currentVotes.get(idx).getAnswer(), feedback);

            monos.add(agent.execute(LLMRequest.of("你是一个专业、友好的智能助手。", refinePrompt))
                    .map(refined -> AgentVote.of(agentId, agent.getName(), refined, 0.7))
                    .onErrorResume(e -> Mono.just(AgentVote.of(agentId, agent.getName(),
                            currentVotes.get(idx).getAnswer(), 0.5))));
        }

        return Mono.zip(monos, results -> {
            List<AgentVote> result = new ArrayList<>();
            for (Object r : results) {
                result.add((AgentVote) r);
            }
            return result;
        });
    }

    /**
     * 构建最终结果 — 使用多数投票选出最佳答案。
     */
    private Mono<ConsensusResult> buildFinalResult(String question, List<AgentVote> votes,
                                                    int rounds, long start) {
        return agentConsensus.majorityVote(question, List.of())
                .map(result -> ConsensusResult.builder()
                        .question(question)
                        .strategy(VoteStrategy.MAJORITY)
                        .votes(votes)
                        .winner(result.getWinner())
                        .finalAnswer(result.getFinalAnswer())
                        .debateRounds(rounds)
                        .consensusReached(result.isConsensusReached())
                        .totalElapsedMs(System.currentTimeMillis() - start)
                        .build());
    }

    private List<String> resolveAgentIds(List<String> agentIds) {
        if (agentIds != null && !agentIds.isEmpty()) {
            return agentIds;
        }
        return agentRegistry.getAllCards().stream()
                .map(c -> c.getAgentId())
                .limit(3)
                .collect(Collectors.toList());
    }
}