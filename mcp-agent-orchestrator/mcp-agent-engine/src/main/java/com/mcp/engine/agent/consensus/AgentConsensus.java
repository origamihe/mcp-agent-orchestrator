package com.mcp.engine.agent.consensus;

import com.mcp.common.agent.ConsensusResult;
import com.mcp.common.agent.ConsensusResult.AgentVote;
import com.mcp.common.agent.ConsensusResult.VoteStrategy;
import com.mcp.engine.agent.Agent;
import com.mcp.engine.agent.LLMRequest;
import com.mcp.engine.agent.registry.AgentRegistry;
import com.mcp.llm.client.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 多 Agent 共识编排器 — 并行执行多 Agent，通过投票/裁判选出最佳答案。
 *
 * <p>支持三种策略：
 * <ul>
 *   <li>MAJORITY — 多数投票：按相似度聚类，选出多数派答案</li>
 *   <li>JUDGE_EVALUATION — 裁判评估：由 LLM Judge 评分各 Agent 答案，选最高分</li>
 *   <li>WEIGHTED — 加权投票：按 Agent 匹配度加权计分</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentConsensus {

    private final AgentRegistry agentRegistry;
    private final LlmClient llmClient;

    private static final String JUDGE_SYSTEM_PROMPT = """
            【多 Agent 答案裁判】
            你的任务是评估多个 Agent 对同一问题的回答质量。

            评分标准（1-10 分）：
            1. 准确性 — 回答是否正确、符合事实
            2. 完整性 — 是否覆盖问题的所有方面
            3. 清晰度 — 表达是否清晰、结构是否良好
            4. 实用性 — 是否对用户有实际帮助

            对于每个 Agent 的答案，给出评分和简短理由。
            最后选择最佳答案（winner_agent_id），并给出综合建议。

            输出格式（JSON）：
            {
              "evaluations": [
                {"agent_id": "...", "score": 8, "reason": "..."},
                ...
              ],
              "winner_agent_id": "...",
              "synthesis": "综合建议..."
            }
            """;

    /**
     * 多数投票共识 — 并行执行 Agent，按相似度选出多数派答案。
     */
    public Mono<ConsensusResult> majorityVote(String question, List<String> agentIds) {
        long start = System.currentTimeMillis();
        List<String> ids = resolveAgentIds(agentIds);

        return executeAgents(question, ids)
                .collectList()
                .map(votes -> {
                    String winner = selectMajority(votes);
                    if (winner != null) {
                        votes.stream()
                                .filter(v -> v.getAgentId().equals(winner))
                                .findFirst()
                                .ifPresent(v -> v.setSelected(true));
                    }

                    return ConsensusResult.builder()
                            .question(question)
                            .strategy(VoteStrategy.MAJORITY)
                            .votes(votes)
                            .winner(votes.stream().filter(AgentVote::isSelected).findFirst().orElse(null))
                            .finalAnswer(winner != null
                                    ? votes.stream().filter(v -> v.getAgentId().equals(winner)).findFirst()
                                    .map(AgentVote::getAnswer).orElse("无共识")
                                    : "Agent 未能达成共识")
                            .consensusReached(winner != null)
                            .totalElapsedMs(System.currentTimeMillis() - start)
                            .build();
                });
    }

    /**
     * 裁判评估共识 — 由 LLM Judge 评分各 Agent 答案，选最高分。
     */
    public Mono<ConsensusResult> judgeEvaluation(String question, List<String> agentIds) {
        long start = System.currentTimeMillis();
        List<String> ids = resolveAgentIds(agentIds);

        return executeAgents(question, ids)
                .collectList()
                .flatMap(votes -> evaluateWithJudge(question, votes)
                        .map(evaluation -> {
                            String winnerId = evaluation.get("winner");
                            if (winnerId != null) {
                                votes.stream()
                                        .filter(v -> v.getAgentId().equals(winnerId))
                                        .findFirst()
                                        .ifPresent(v -> v.setSelected(true));
                            }

                            String synthesis = evaluation.getOrDefault("synthesis", "综合结果");
                            return ConsensusResult.builder()
                                    .question(question)
                                    .strategy(VoteStrategy.JUDGE_EVALUATION)
                                    .votes(votes)
                                    .winner(votes.stream().filter(AgentVote::isSelected).findFirst().orElse(null))
                                    .finalAnswer(synthesis)
                                    .consensusReached(winnerId != null)
                                    .totalElapsedMs(System.currentTimeMillis() - start)
                                    .build();
                        }));
    }

    /**
     * 加权投票共识 — 按 Agent 技能匹配度加权计分。
     */
    public Mono<ConsensusResult> weightedVote(String question, List<String> agentIds) {
        long start = System.currentTimeMillis();
        List<String> ids = resolveAgentIds(agentIds);

        return executeAgents(question, ids)
                .collectList()
                .map(votes -> {
                    String winner = selectWeighted(votes);
                    if (winner != null) {
                        votes.stream()
                                .filter(v -> v.getAgentId().equals(winner))
                                .findFirst()
                                .ifPresent(v -> v.setSelected(true));
                    }

                    return ConsensusResult.builder()
                            .question(question)
                            .strategy(VoteStrategy.WEIGHTED)
                            .votes(votes)
                            .winner(votes.stream().filter(AgentVote::isSelected).findFirst().orElse(null))
                            .finalAnswer(winner != null
                                    ? votes.stream().filter(v -> v.getAgentId().equals(winner)).findFirst()
                                    .map(AgentVote::getAnswer).orElse("无共识")
                                    : "Agent 未能达成共识")
                            .consensusReached(winner != null)
                            .totalElapsedMs(System.currentTimeMillis() - start)
                            .build();
                });
    }

    private Flux<AgentVote> executeAgents(String question, List<String> agentIds) {
        return Flux.fromIterable(agentIds)
                .flatMap(agentId -> {
                    Agent agent = agentRegistry.getAgent(agentId).orElse(null);
                    if (agent == null) {
                        return Mono.just(AgentVote.of(agentId, agentId, "Agent 不可用", 0.0));
                    }
                    return agent.execute(LLMRequest.of("你是一个专业、友好的智能助手。", question))
                            .map(answer -> AgentVote.of(agentId, agent.getName(), answer, estimateConfidence(answer)))
                            .onErrorResume(e -> Mono.just(AgentVote.of(agentId, agent.getName(),
                                    "执行失败: " + e.getMessage(), 0.0)));
                });
    }

    private List<String> resolveAgentIds(List<String> agentIds) {
        if (agentIds != null && !agentIds.isEmpty()) {
            return agentIds;
        }
        return agentRegistry.getAllCards().stream()
                .map(c -> c.getAgentId())
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * 多数投票：按答案长度和关键词重叠度聚类，选出最多的那一组。
     */
    private String selectMajority(List<AgentVote> votes) {
        if (votes.isEmpty()) return null;
        if (votes.size() == 1) return votes.get(0).getAgentId();

        Map<String, Long> clusters = votes.stream()
                .collect(Collectors.groupingBy(
                        v -> simplifyAnswer(v.getAnswer()),
                        Collectors.counting()));

        String majorityAnswer = clusters.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        if (majorityAnswer == null) return null;

        return votes.stream()
                .filter(v -> simplifyAnswer(v.getAnswer()).equals(majorityAnswer))
                .max(Comparator.comparingDouble(AgentVote::getConfidence))
                .map(AgentVote::getAgentId)
                .orElse(null);
    }

    /**
     * 加权投票：按答案长度和置信度加权。
     */
    private String selectWeighted(List<AgentVote> votes) {
        if (votes.isEmpty()) return null;
        return votes.stream()
                .filter(v -> v.getConfidence() > 0)
                .max(Comparator.comparingDouble(v -> v.getConfidence() * (1 + Math.log1p(v.getAnswer().length()))))
                .map(AgentVote::getAgentId)
                .orElse(votes.get(0).getAgentId());
    }

    /**
     * 简化答案用于聚类比较。
     */
    private String simplifyAnswer(String answer) {
        if (answer == null || answer.isBlank()) return "";
        String s = answer.toLowerCase().replaceAll("\\s+", " ").trim();
        return s.length() > 100 ? s.substring(0, 100) : s;
    }

    /**
     * 根据答案长度和结构估算置信度。
     */
    private double estimateConfidence(String answer) {
        if (answer == null || answer.isBlank()) return 0.0;
        double score = Math.min(1.0, Math.log1p(answer.length()) / 8.0);
        if (answer.contains("1.") || answer.contains("2.")) score += 0.1;
        if (answer.contains("总结") || answer.contains("综上所述")) score += 0.1;
        return Math.min(1.0, score);
    }

    /**
     * 使用 LLM Judge 评估各 Agent 答案。
     */
    private Mono<Map<String, String>> evaluateWithJudge(String question, List<AgentVote> votes) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("问题：").append(question).append("\n\n");
        for (int i = 0; i < votes.size(); i++) {
            AgentVote v = votes.get(i);
            prompt.append("Agent ").append(i + 1).append(" (").append(v.getAgentName()).append("):\n");
            prompt.append(v.getAnswer()).append("\n\n");
        }

        return llmClient.generateWithSystemPrompt(JUDGE_SYSTEM_PROMPT, prompt.toString())
                .map(this::parseJudgeResponse)
                .onErrorResume(e -> {
                    log.warn("[Consensus] Judge LLM failed, falling back to majority: {}", e.getMessage());
                    String winner = selectMajority(votes);
                    return Mono.just(Map.of(
                            "winner", winner != null ? winner : "",
                            "synthesis", "裁判评估失败，使用多数投票结果。"
                    ));
                });
    }

    /**
     * 解析 Judge 的 JSON 响应。
     */
    private Map<String, String> parseJudgeResponse(String raw) {
        try {
            String json = raw;
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);

            String winner = root.has("winner_agent_id") ? root.get("winner_agent_id").asText() : null;
            String synthesis = root.has("synthesis") ? root.get("synthesis").asText() : "综合结果";

            return Map.of("winner", winner != null ? winner : "", "synthesis", synthesis);
        } catch (Exception e) {
            log.warn("[Consensus] Failed to parse judge response: {}", e.getMessage());
            return Map.of("winner", "", "synthesis", raw);
        }
    }
}