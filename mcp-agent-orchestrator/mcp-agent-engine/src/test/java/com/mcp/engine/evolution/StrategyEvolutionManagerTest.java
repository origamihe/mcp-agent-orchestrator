package com.mcp.engine.evolution;

import com.mcp.common.evolution.StrategyEvolutionContext;
import com.mcp.common.evolution.StrategyEvolutionContext.EvolutionRecommendation;
import com.mcp.common.evolution.StrategyEvolutionContext.StrategyDimension;
import com.mcp.common.evolution.StrategyEvolutionContext.StrategyPhase;
import com.mcp.core.domain.memory.FailureEntity;
import com.mcp.core.domain.memory.SkillEntity;
import com.mcp.engine.reflection.FailureLibraryService;
import com.mcp.engine.reflection.SkillLibraryService;
import com.mcp.llm.client.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("E4 — Strategy Evolution 策略进化")
class StrategyEvolutionManagerTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private SkillLibraryService skillLibraryService;

    @Mock
    private FailureLibraryService failureLibraryService;

    private StrategyEvolutionManager evolutionManager;

    @BeforeEach
    void setUp() {
        evolutionManager = new StrategyEvolutionManager(llmClient, skillLibraryService, failureLibraryService);

        lenient().when(skillLibraryService.getHighSuccessSkills()).thenReturn(List.of());
        lenient().when(failureLibraryService.getUnresolvedFailures()).thenReturn(List.of());
    }

    @Nested
    @DisplayName("进化上下文管理")
    class EvolutionContext {

        @Test
        @DisplayName("新 Agent 初始状态为 OBSERVING")
        void shouldStartInObservingPhase() {
            StrategyEvolutionContext ctx = evolutionManager.getOrCreate("agent-1");

            assertThat(ctx.getAgentId()).isEqualTo("agent-1");
            assertThat(ctx.getPhase()).isEqualTo(StrategyPhase.OBSERVING);
            assertThat(ctx.getExecutionCount()).isZero();
            assertThat(ctx.getCurrentStrategyVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("相同 Agent 返回同一上下文")
        void shouldReturnSameContext() {
            StrategyEvolutionContext ctx1 = evolutionManager.getOrCreate("agent-1");
            StrategyEvolutionContext ctx2 = evolutionManager.getOrCreate("agent-1");

            assertThat(ctx1).isSameAs(ctx2);
        }

        @Test
        @DisplayName("不同 Agent 返回不同上下文")
        void shouldReturnDifferentContexts() {
            StrategyEvolutionContext ctx1 = evolutionManager.getOrCreate("agent-1");
            StrategyEvolutionContext ctx2 = evolutionManager.getOrCreate("agent-2");

            assertThat(ctx1).isNotSameAs(ctx2);
        }
    }

    @Nested
    @DisplayName("执行记录与趋势")
    class ExecutionRecording {

        @Test
        @DisplayName("执行次数少于阈值时仍为 OBSERVING")
        void shouldStayObservingBeforeThreshold() {
            for (int i = 0; i < 5; i++) {
                evolutionManager.recordExecution("agent-1", true, 80, 500, true, true, true);
            }

            StrategyEvolutionContext ctx = evolutionManager.getEvolution("agent-1");
            assertThat(ctx.getPhase()).isEqualTo(StrategyPhase.OBSERVING);
            assertThat(ctx.getExecutionCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("连续成功多次后进入稳定或进化阶段")
        void shouldEvolveAfterConsecutiveSuccesses() {
            for (int i = 0; i < 15; i++) {
                evolutionManager.recordExecution("agent-1", true, 85 + i % 10, 400, true, true, true);
            }

            StrategyEvolutionContext ctx = evolutionManager.getEvolution("agent-1");
            assertThat(ctx.getExecutionCount()).isEqualTo(15);
            assertThat(ctx.getPhase()).isNotEqualTo(StrategyPhase.OBSERVING);
            assertThat(ctx.getTrendSlope()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("连续失败触发退化阶段")
        void shouldDegradeAfterConsecutiveFailures() {
            for (int i = 0; i < 10; i++) {
                evolutionManager.recordExecution("agent-1", true, 80, 500, true, true, true);
            }
            for (int i = 0; i < 3; i++) {
                evolutionManager.recordExecution("agent-1", false, 30, 1000, false, false, false);
            }

            StrategyEvolutionContext ctx = evolutionManager.getEvolution("agent-1");
            assertThat(ctx.getPhase()).isEqualTo(StrategyPhase.REGRESSING);
            assertThat(ctx.getConsecutiveFailures()).isEqualTo(3);
        }

        @Test
        @DisplayName("性能快照含正确指标")
        void shouldContainCorrectMetrics() {
            evolutionManager.recordExecution("agent-1", true, 90, 300, true, true, true);
            evolutionManager.recordExecution("agent-1", false, 40, 800, false, false, false);

            StrategyEvolutionContext ctx = evolutionManager.getEvolution("agent-1");
            assertThat(ctx.getPerformanceHistory()).hasSize(2);

            StrategyEvolutionContext.PerformanceSnapshot latest = ctx.getPerformanceHistory().get(1);
            assertThat(latest.getSuccessCount()).isEqualTo(1);
            assertThat(latest.getFailureCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("进化建议分析")
    class EvolutionRecommendations {

        @Test
        @DisplayName("执行次数不足时不生成建议")
        void shouldNotGenerateWhenInsufficientData() {
            List<EvolutionRecommendation> recs = evolutionManager.analyzeAndRecommend("agent-1").block();

            assertThat(recs).isEmpty();
        }

        @Test
        @DisplayName("数据充足时调用 LLM 生成建议")
        void shouldGenerateRecommendationsWithLLM() {
            for (int i = 0; i < 15; i++) {
                evolutionManager.recordExecution("agent-1", true, 80 + i % 15, 400, true, true, true);
            }

            String llmResponse = """
                    {
                        "phase": "STABILIZING",
                        "recommendations": [
                            {
                                "dimension": "AGENT_ROUTING",
                                "recommendation": "优先使用 SearchAgent 处理搜索类请求",
                                "confidence": 0.85,
                                "evidence": "SearchAgent 成功率高于 ChatAgent",
                                "priority": "HIGH"
                            },
                            {
                                "dimension": "TOOL_SELECTION",
                                "recommendation": "减少不必要的工具调用",
                                "confidence": 0.7,
                                "evidence": "部分工具调用未产生有效结果",
                                "priority": "MEDIUM"
                            }
                        ],
                        "summary": "策略稳定，有小幅优化空间"
                    }""";

            when(llmClient.generate(anyString())).thenReturn(Mono.just(llmResponse));

            List<EvolutionRecommendation> recs = evolutionManager.analyzeAndRecommend("agent-1").block();

            assertThat(recs).hasSize(2);
            assertThat(recs.get(0).getDimension()).isEqualTo(StrategyDimension.AGENT_ROUTING);
            assertThat(recs.get(0).getPriority()).isEqualTo(EvolutionRecommendation.Priority.HIGH);
            assertThat(recs.get(0).getConfidence()).isEqualTo(0.85);
        }
    }

    @Nested
    @DisplayName("策略变更与回滚")
    class StrategyChange {

        @Test
        @DisplayName("应用建议后版本号递增")
        void shouldIncrementVersionOnApply() {
            StrategyEvolutionContext ctx = evolutionManager.getOrCreate("agent-1");
            assertThat(ctx.getCurrentStrategyVersion()).isEqualTo(1);

            EvolutionRecommendation rec = EvolutionRecommendation.builder()
                    .dimension(StrategyDimension.AGENT_ROUTING)
                    .recommendation("优化路由策略")
                    .confidence(0.8)
                    .priority(EvolutionRecommendation.Priority.HIGH)
                    .build();

            evolutionManager.applyRecommendation("agent-1", rec);

            assertThat(ctx.getCurrentStrategyVersion()).isEqualTo(2);
            assertThat(ctx.getStrategyChanges()).hasSize(1);
        }

        @Test
        @DisplayName("回滚后版本号递减")
        void shouldDecrementVersionOnRollback() {
            EvolutionRecommendation rec = EvolutionRecommendation.builder()
                    .dimension(StrategyDimension.AGENT_ROUTING)
                    .recommendation("优化路由策略")
                    .confidence(0.8)
                    .priority(EvolutionRecommendation.Priority.HIGH)
                    .build();

            evolutionManager.applyRecommendation("agent-1", rec);
            boolean rolledBack = evolutionManager.rollback("agent-1");

            StrategyEvolutionContext ctx = evolutionManager.getEvolution("agent-1");
            assertThat(rolledBack).isTrue();
            assertThat(ctx.getCurrentStrategyVersion()).isEqualTo(1);
            assertThat(ctx.getPhase()).isEqualTo(StrategyPhase.OBSERVING);
        }

        @Test
        @DisplayName("无历史变更时回滚失败")
        void shouldFailRollbackWhenNoHistory() {
            boolean rolledBack = evolutionManager.rollback("agent-1");

            assertThat(rolledBack).isFalse();
        }
    }

    @Nested
    @DisplayName("健康度评分")
    class HealthScore {

        @Test
        @DisplayName("无数据时返回默认健康度")
        void shouldReturnDefaultWhenNoData() {
            double score = evolutionManager.computeHealthScore("agent-1");

            assertThat(score).isEqualTo(0.5);
        }

        @Test
        @DisplayName("高成功率时健康度较高")
        void shouldReturnHighScoreOnSuccess() {
            for (int i = 0; i < 10; i++) {
                evolutionManager.recordExecution("agent-1", true, 95, 200, true, true, true);
            }

            double score = evolutionManager.computeHealthScore("agent-1");
            assertThat(score).isGreaterThan(60);
        }

        @Test
        @DisplayName("退化时健康度降低")
        void shouldReturnLowScoreOnDegradation() {
            for (int i = 0; i < 10; i++) {
                evolutionManager.recordExecution("agent-1", true, 80, 400, true, true, true);
            }
            for (int i = 0; i < 5; i++) {
                evolutionManager.recordExecution("agent-1", false, 20, 1000, false, false, false);
            }

            double score = evolutionManager.computeHealthScore("agent-1");
            assertThat(score).isLessThan(60);
        }
    }

    @Nested
    @DisplayName("Prompt 片段生成")
    class PromptFragment {

        @Test
        @DisplayName("应生成包含进化状态的 Prompt 片段")
        void shouldGeneratePromptFragment() {
            for (int i = 0; i < 10; i++) {
                evolutionManager.recordExecution("agent-1", true, 85, 300, true, true, true);
            }

            String fragment = evolutionManager.buildEvolutionPrompt("agent-1");
            assertThat(fragment).contains("策略进化状态");
            assertThat(fragment).contains("STABILIZING");
            assertThat(fragment).contains("策略版本");
            assertThat(fragment).contains("成功率");
        }
    }
}