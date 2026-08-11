package com.mcp.engine.agent.consensus;

import com.mcp.common.agent.ConsensusResult;
import com.mcp.common.agent.ConsensusResult.AgentVote;
import com.mcp.common.agent.ConsensusResult.VoteStrategy;
import com.mcp.engine.agent.Agent;
import com.mcp.engine.agent.card.AgentCard;
import com.mcp.engine.agent.registry.AgentRegistry;
import com.mcp.llm.client.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Multi-Agent Consensus & Debate — 多 Agent 共识与辩论")
class AgentConsensusTest {

    @Mock
    private AgentRegistry agentRegistry;

    @Mock
    private LlmClient llmClient;

    @Mock
    private Agent chatAgent;

    @Mock
    private Agent codeAgent;

    @Mock
    private Agent searchAgent;

    private AgentConsensus agentConsensus;

    @BeforeEach
    void setUp() {
        agentConsensus = new AgentConsensus(agentRegistry, llmClient);

        lenient().when(chatAgent.getId()).thenReturn("chat-agent");
        lenient().when(chatAgent.getName()).thenReturn("ChatAgent");
        lenient().when(codeAgent.getId()).thenReturn("code-agent");
        lenient().when(codeAgent.getName()).thenReturn("CodeAgent");
        lenient().when(searchAgent.getId()).thenReturn("search-agent");
        lenient().when(searchAgent.getName()).thenReturn("SearchAgent");

        lenient().when(agentRegistry.getAgent("chat-agent")).thenReturn(Optional.of(chatAgent));
        lenient().when(agentRegistry.getAgent("code-agent")).thenReturn(Optional.of(codeAgent));
        lenient().when(agentRegistry.getAgent("search-agent")).thenReturn(Optional.of(searchAgent));
    }

    @Nested
    @DisplayName("多数投票共识")
    class MajorityVote {

        @Test
        @DisplayName("多个 Agent 答案相同时达成共识")
        void shouldReachConsensusWhenAllAgentsAgree() {
            when(chatAgent.execute(any())).thenReturn(Mono.just("答案是 42"));
            when(codeAgent.execute(any())).thenReturn(Mono.just("答案是 42"));
            when(searchAgent.execute(any())).thenReturn(Mono.just("答案是 42"));

            StepVerifier.create(agentConsensus.majorityVote("1+1=?", List.of("chat-agent", "code-agent", "search-agent")))
                    .assertNext(result -> {
                        assertThat(result.isConsensusReached()).isTrue();
                        assertThat(result.getStrategy()).isEqualTo(VoteStrategy.MAJORITY);
                        assertThat(result.getVotes()).hasSize(3);
                        assertThat(result.getWinner()).isNotNull();
                        assertThat(result.getFinalAnswer()).contains("42");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("多数派答案胜出")
        void shouldSelectMajorityAnswer() {
            when(chatAgent.execute(any())).thenReturn(Mono.just("Java 是最好的语言"));
            when(codeAgent.execute(any())).thenReturn(Mono.just("Java 是最好的语言"));
            when(searchAgent.execute(any())).thenReturn(Mono.just("Python 是最好的语言"));

            StepVerifier.create(agentConsensus.majorityVote("最好的编程语言?", List.of("chat-agent", "code-agent", "search-agent")))
                    .assertNext(result -> {
                        assertThat(result.isConsensusReached()).isTrue();
                        assertThat(result.getFinalAnswer()).contains("Java");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("只有一个可用 Agent 时直接返回")
        void shouldReturnSingleAgentResult() {
            when(chatAgent.execute(any())).thenReturn(Mono.just("唯一答案"));

            StepVerifier.create(agentConsensus.majorityVote("question?", List.of("chat-agent")))
                    .assertNext(result -> {
                        assertThat(result.isConsensusReached()).isTrue();
                        assertThat(result.getVotes()).hasSize(1);
                        assertThat(result.getFinalAnswer()).isEqualTo("唯一答案");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("加权投票共识")
    class WeightedVote {

        @Test
        @DisplayName("按置信度加权选出最佳答案")
        void shouldSelectWeightedBest() {
            when(chatAgent.execute(any())).thenReturn(Mono.just("简短回答"));
            when(codeAgent.execute(any())).thenReturn(Mono.just("详细回答：1. 第一点 2. 第二点 3. 第三点 综上所述..."));
            when(searchAgent.execute(any())).thenReturn(Mono.just("中等回答"));

            StepVerifier.create(agentConsensus.weightedVote("question?", List.of("chat-agent", "code-agent", "search-agent")))
                    .assertNext(result -> {
                        assertThat(result.getStrategy()).isEqualTo(VoteStrategy.WEIGHTED);
                        assertThat(result.getVotes()).hasSize(3);
                        assertThat(result.getWinner()).isNotNull();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("裁判评估共识")
    class JudgeEvaluation {

        @Test
        @DisplayName("LLM Judge 评估后选出最佳答案")
        void shouldSelectBestByJudgeEvaluation() {
            when(chatAgent.execute(any())).thenReturn(Mono.just("答案 A"));
            when(codeAgent.execute(any())).thenReturn(Mono.just("答案 B"));
            when(searchAgent.execute(any())).thenReturn(Mono.just("答案 C"));

            String judgeResponse = """
                    {
                      "evaluations": [
                        {"agent_id": "chat-agent", "score": 7, "reason": "清晰"},
                        {"agent_id": "code-agent", "score": 9, "reason": "准确"},
                        {"agent_id": "search-agent", "score": 6, "reason": "一般"}
                      ],
                      "winner_agent_id": "code-agent",
                      "synthesis": "Agent code-agent 的答案最优"
                    }""";

            when(llmClient.generateWithSystemPrompt(anyString(), anyString()))
                    .thenReturn(Mono.just(judgeResponse));

            StepVerifier.create(agentConsensus.judgeEvaluation("question?", List.of("chat-agent", "code-agent", "search-agent")))
                    .assertNext(result -> {
                        assertThat(result.getStrategy()).isEqualTo(VoteStrategy.JUDGE_EVALUATION);
                        assertThat(result.isConsensusReached()).isTrue();
                        assertThat(result.getWinner()).isNotNull();
                        assertThat(result.getWinner().getAgentId()).isEqualTo("code-agent");
                        assertThat(result.getFinalAnswer()).contains("最优");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Judge LLM 失败时回退多数投票")
        void shouldFallbackToMajorityOnJudgeFailure() {
            when(chatAgent.execute(any())).thenReturn(Mono.just("答案 A"));
            when(codeAgent.execute(any())).thenReturn(Mono.just("答案 A"));
            when(searchAgent.execute(any())).thenReturn(Mono.just("答案 B"));

            when(llmClient.generateWithSystemPrompt(anyString(), anyString()))
                    .thenReturn(Mono.error(new RuntimeException("LLM timeout")));

            StepVerifier.create(agentConsensus.judgeEvaluation("question?", List.of("chat-agent", "code-agent", "search-agent")))
                    .assertNext(result -> {
                        assertThat(result.getStrategy()).isEqualTo(VoteStrategy.JUDGE_EVALUATION);
                        assertThat(result.getFinalAnswer()).contains("裁判评估失败");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Agent 执行失败处理")
    class ErrorHandling {

        @Test
        @DisplayName("部分 Agent 失败不影响共识")
        void shouldHandlePartialFailure() {
            when(chatAgent.execute(any())).thenReturn(Mono.just("正确回答：1. 第一点 2. 第二点"));
            when(codeAgent.execute(any())).thenReturn(Mono.error(new RuntimeException("code agent crash")));
            when(searchAgent.execute(any())).thenReturn(Mono.just("正确回答：1. 第一点 2. 第二点"));

            StepVerifier.create(agentConsensus.majorityVote("question?", List.of("chat-agent", "code-agent", "search-agent")))
                    .assertNext(result -> {
                        assertThat(result.getVotes()).hasSize(3);
                        assertThat(result.getVotes().get(1).getConfidence()).isZero();
                        assertThat(result.getFinalAnswer()).contains("正确回答");
                    })
                    .verifyComplete();
        }
    }
}