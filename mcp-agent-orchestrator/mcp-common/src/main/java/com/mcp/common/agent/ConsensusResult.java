package com.mcp.common.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * 多 Agent 共识结果 — 包含投票详情、获胜者与最终合成答案。
 */
public class ConsensusResult {

    private String question;
    private String finalAnswer;
    private VoteStrategy strategy;
    private List<AgentVote> votes;
    private AgentVote winner;
    private int debateRounds;
    private long totalElapsedMs;
    private boolean consensusReached;

    public ConsensusResult() {
        this.votes = new ArrayList<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ConsensusResult result = new ConsensusResult();

        public Builder question(String question) { result.question = question; return this; }
        public Builder finalAnswer(String answer) { result.finalAnswer = answer; return this; }
        public Builder strategy(VoteStrategy s) { result.strategy = s; return this; }
        public Builder votes(List<AgentVote> v) { result.votes = v; return this; }
        public Builder winner(AgentVote w) { result.winner = w; return this; }
        public Builder debateRounds(int r) { result.debateRounds = r; return this; }
        public Builder totalElapsedMs(long ms) { result.totalElapsedMs = ms; return this; }
        public Builder consensusReached(boolean r) { result.consensusReached = r; return this; }

        public ConsensusResult build() { return result; }
    }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getFinalAnswer() { return finalAnswer; }
    public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }
    public VoteStrategy getStrategy() { return strategy; }
    public void setStrategy(VoteStrategy strategy) { this.strategy = strategy; }
    public List<AgentVote> getVotes() { return votes; }
    public void setVotes(List<AgentVote> votes) { this.votes = votes; }
    public AgentVote getWinner() { return winner; }
    public void setWinner(AgentVote winner) { this.winner = winner; }
    public int getDebateRounds() { return debateRounds; }
    public void setDebateRounds(int debateRounds) { this.debateRounds = debateRounds; }
    public long getTotalElapsedMs() { return totalElapsedMs; }
    public void setTotalElapsedMs(long totalElapsedMs) { this.totalElapsedMs = totalElapsedMs; }
    public boolean isConsensusReached() { return consensusReached; }
    public void setConsensusReached(boolean consensusReached) { this.consensusReached = consensusReached; }

    public enum VoteStrategy {
        MAJORITY,
        JUDGE_EVALUATION,
        WEIGHTED
    }

    public static class AgentVote {
        private String agentId;
        private String agentName;
        private String answer;
        private double confidence;
        private boolean selected;

        public AgentVote() {}

        public static AgentVote of(String agentId, String agentName, String answer, double confidence) {
            AgentVote v = new AgentVote();
            v.agentId = agentId;
            v.agentName = agentName;
            v.answer = answer;
            v.confidence = confidence;
            return v;
        }

        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }
        public String getAgentName() { return agentName; }
        public void setAgentName(String agentName) { this.agentName = agentName; }
        public String getAnswer() { return answer; }
        public void setAnswer(String answer) { this.answer = answer; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public boolean isSelected() { return selected; }
        public void setSelected(boolean selected) { this.selected = selected; }
    }
}