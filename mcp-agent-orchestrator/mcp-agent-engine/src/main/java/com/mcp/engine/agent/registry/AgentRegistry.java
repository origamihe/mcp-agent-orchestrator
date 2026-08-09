package com.mcp.engine.agent.registry;

import com.mcp.engine.agent.Agent;
import com.mcp.engine.agent.card.AgentCard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Agent 注册中心 - 能力驱动的 Agent 发现与路由
 * <p>
 * 提供基于 AgentCard 的 Agent 注册、发现、匹配能力。
 * 支持按技能、工具、Agent 类型进行多维度匹配。
 */
@Slf4j
@Component
public class AgentRegistry {

    private final Map<String, Agent> agentMap = new ConcurrentHashMap<>();
    private final Map<String, AgentCard> cardMap = new ConcurrentHashMap<>();

    public void register(Agent agent, AgentCard card) {
        agentMap.put(agent.getId(), agent);
        cardMap.put(agent.getId(), card);
        log.info("[AgentRegistry] Registered agent: {} (type={}, skills={})",
                agent.getName(), card.getAgentType(), card.getSkills());
    }

    public void unregister(String agentId) {
        agentMap.remove(agentId);
        cardMap.remove(agentId);
        log.info("[AgentRegistry] Unregistered agent: {}", agentId);
    }

    public Optional<Agent> getAgent(String agentId) {
        return Optional.ofNullable(agentMap.get(agentId));
    }

    public Optional<AgentCard> getCard(String agentId) {
        return Optional.ofNullable(cardMap.get(agentId));
    }

    public Collection<Agent> getAllAgents() {
        return Collections.unmodifiableCollection(agentMap.values());
    }

    public List<AgentCard> getAllCards() {
        return List.copyOf(cardMap.values());
    }

    public int agentCount() {
        return agentMap.size();
    }

    /**
     * 按技能匹配 Agent（匹配度排序）
     */
    public List<AgentMatch> matchBySkills(List<String> requiredSkills) {
        if (requiredSkills == null || requiredSkills.isEmpty()) {
            return List.of();
        }
        return cardMap.entrySet().stream()
                .map(entry -> {
                    AgentCard card = entry.getValue();
                    List<String> matched = card.getSkills() != null
                            ? card.getSkills().stream()
                                    .filter(s -> requiredSkills.stream().anyMatch(
                                            rs -> rs.equalsIgnoreCase(s)))
                                    .toList()
                            : List.of();
                    double score = card.getSkills() != null && !card.getSkills().isEmpty()
                            ? (double) matched.size() / requiredSkills.size()
                            : 0.0;
                    return new AgentMatch(entry.getKey(), card, matched, score);
                })
                .filter(m -> m.score > 0)
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .collect(Collectors.toList());
    }

    /**
     * 按 Agent 类型匹配
     */
    public List<AgentMatch> matchByType(AgentCard.AgentType agentType) {
        if (agentType == null) {
            return List.of();
        }
        return cardMap.entrySet().stream()
                .filter(entry -> entry.getValue().getAgentType() == agentType)
                .map(entry -> {
                    AgentCard card = entry.getValue();
                    return new AgentMatch(entry.getKey(), card, card.getSkills(), 1.0);
                })
                .collect(Collectors.toList());
    }

    /**
     * 智能匹配：结合技能和类型进行多维度匹配
     */
    public Mono<AgentMatch> findBestAgent(String task, List<String> requiredSkills) {
        return Mono.fromCallable(() -> {
            List<AgentMatch> bySkills = matchBySkills(requiredSkills);
            if (!bySkills.isEmpty()) {
                return bySkills.get(0);
            }
            if (!agentMap.isEmpty()) {
                String defaultId = agentMap.keySet().iterator().next();
                AgentCard defaultCard = cardMap.get(defaultId);
                return new AgentMatch(defaultId, defaultCard, List.of(), 0.0);
            }
            return null;
        });
    }

    /**
     * 匹配结果
     */
    public record AgentMatch(
            String agentId,
            AgentCard card,
            List<String> matchedSkills,
            double score
    ) {
        public boolean isExactMatch() {
            return score >= 1.0;
        }
    }
}