package com.mcp.engine.skill;

import com.mcp.core.retrieval.Bm25Scorer;
import com.mcp.core.retrieval.HybridRetriever;
import com.mcp.core.retrieval.RetrievalQualityMetrics;
import com.mcp.core.retrieval.RetrievalScorer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * P2: Skills 能力可组合 + Hybrid 召回升级 验证测试。
 */
class P2SkillAndRetrievalTest {

    // ==================== SkillPipeline Tests ====================

    @Nested
    @DisplayName("SkillPipeline — 能力可组合")
    class SkillPipelineTests {

        @Test
        @DisplayName("基本管道 — 三个步骤顺序执行")
        void shouldExecuteSequentialPipeline() {
            SkillPipeline pipeline = SkillPipeline.startWith("web_search")
                    .named("test_pipeline")
                    .then("deep_research")
                    .then("synthesize")
                    .build();

            assertEquals("test_pipeline", pipeline.getName());
            assertEquals(3, pipeline.getSteps().size());
            assertEquals("web_search", pipeline.getSteps().get(0).skillName());
            assertEquals("deep_research", pipeline.getSteps().get(1).skillName());
            assertEquals("synthesize", pipeline.getSteps().get(2).skillName());
        }

        @Test
        @DisplayName("管道执行 — 全部成功")
        void shouldExecuteAllStepsSuccessfully() {
            SkillPipeline pipeline = SkillPipeline.startWith("step_a")
                    .then("step_b")
                    .then("step_c")
                    .build();

            SkillPipeline.PipelineResult result = pipeline.execute(step ->
                    SkillPipeline.StepResult.success(step.name(), "output of " + step.skillName()));

            assertTrue(result.isComplete());
            assertEquals(3, result.successCount());
            assertEquals(0, result.failureCount());
            assertFalse(result.aborted());
        }

        @Test
        @DisplayName("管道执行 — failFast 模式下遇错中止")
        void shouldAbortOnFailureWithFailFast() {
            SkillPipeline pipeline = SkillPipeline.startWith("step_a")
                    .then("step_b")
                    .then("step_c")
                    .failFast(true)
                    .build();

            SkillPipeline.PipelineResult result = pipeline.execute(step -> {
                if ("step_b".equals(step.skillName())) {
                    return SkillPipeline.StepResult.failure(step.name(), "step_b failed");
                }
                return SkillPipeline.StepResult.success(step.name(), "ok");
            });

            assertFalse(result.isComplete());
            assertTrue(result.aborted());
            assertEquals(1, result.successCount());
            assertEquals(1, result.failureCount());
            assertEquals(2, result.steps().size()); // step_a + step_b, step_c 未执行
        }

        @Test
        @DisplayName("管道执行 — 非 failFast 模式下继续执行")
        void shouldContinueOnFailureWithoutFailFast() {
            SkillPipeline pipeline = SkillPipeline.startWith("step_a")
                    .then("step_b")
                    .then("step_c")
                    .failFast(false)
                    .build();

            SkillPipeline.PipelineResult result = pipeline.execute(step -> {
                if ("step_b".equals(step.skillName())) {
                    return SkillPipeline.StepResult.failure(step.name(), "step_b failed");
                }
                return SkillPipeline.StepResult.success(step.name(), "ok");
            });

            assertFalse(result.isComplete());
            assertFalse(result.aborted());
            assertEquals(2, result.successCount());
            assertEquals(1, result.failureCount());
            assertEquals(3, result.steps().size());
        }

        @Test
        @DisplayName("可选步骤 — 失败不影响整体")
        void shouldHandleOptionalSteps() {
            SkillPipeline pipeline = SkillPipeline.startWith("required_step")
                    .thenOptional("optional_step")
                    .then("final_step")
                    .build();

            assertFalse(pipeline.getSteps().get(1).required());
            assertTrue(pipeline.getSteps().get(0).required());
            assertTrue(pipeline.getSteps().get(2).required());
        }

        @Test
        @DisplayName("条件步骤 — 根据上下文决定是否执行")
        void shouldHandleConditionalSteps() {
            SkillPipeline pipeline = SkillPipeline.startWith("step_a")
                    .thenConditional("conditional_step", ctx -> ctx.lastSucceeded())
                    .then("step_c")
                    .build();

            SkillPipeline.PipelineResult result = pipeline.execute(step -> {
                if ("conditional_step".equals(step.skillName())) {
                    return SkillPipeline.StepResult.success(step.name(), "conditional executed");
                }
                return SkillPipeline.StepResult.success(step.name(), "ok");
            });

            assertTrue(result.isComplete());
            assertEquals(3, result.steps().size());
        }

        @Test
        @DisplayName("PipelineContext — 状态正确传递")
        void shouldTrackContextState() {
            SkillPipeline.PipelineContext ctx = new SkillPipeline.PipelineContext();

            assertFalse(ctx.isAborted());
            assertNull(ctx.lastResult());

            ctx.record(SkillPipeline.StepResult.success("step1", "output1"));
            assertTrue(ctx.lastSucceeded());
            assertEquals("step1", ctx.lastResult().stepName());

            ctx.record(SkillPipeline.StepResult.failure("step2", "error"));
            assertFalse(ctx.lastSucceeded());

            ctx.abort();
            assertTrue(ctx.isAborted());
        }

        @Test
        @DisplayName("StepResult — 成功/失败工厂方法")
        void shouldCreateStepResults() {
            SkillPipeline.StepResult success = SkillPipeline.StepResult.success("s1", "result");
            assertTrue(success.success());
            assertEquals("result", success.output());
            assertNull(success.error());

            SkillPipeline.StepResult failure = SkillPipeline.StepResult.failure("f1", "oops");
            assertFalse(failure.success());
            assertNull(failure.output());
            assertEquals("oops", failure.error());
        }
    }

    // ==================== SkillComposer Tests ====================

    @Nested
    @DisplayName("SkillComposer — 工作流模板")
    class SkillComposerTests {

        @Test
        @DisplayName("预置模板 — SEARCH_AND_SYNTHESIZE")
        void shouldHaveSearchAndSynthesizeTemplate() {
            SkillComposer composer = new SkillComposer();
            SkillPipeline template = composer.getTemplate("SEARCH_AND_SYNTHESIZE");

            assertNotNull(template);
            assertEquals(3, template.getSteps().size());
            assertThat(template.getSteps().stream().map(SkillPipeline.PipelineStep::skillName))
                    .containsExactly("web_search", "deep_research", "synthesize");
        }

        @Test
        @DisplayName("预置模板 — FACT_CHECK")
        void shouldHaveFactCheckTemplate() {
            SkillComposer composer = new SkillComposer();
            SkillPipeline template = composer.getTemplate("FACT_CHECK");

            assertNotNull(template);
            assertEquals(3, template.getSteps().size());
            assertThat(template.getSteps().stream().map(SkillPipeline.PipelineStep::skillName))
                    .containsExactly("web_search", "cross_verify", "summarize");
        }

        @Test
        @DisplayName("预置模板 — DOCUMENT_GENERATE（5 步）")
        void shouldHaveDocumentGenerateTemplate() {
            SkillComposer composer = new SkillComposer();
            SkillPipeline template = composer.getTemplate("DOCUMENT_GENERATE");

            assertNotNull(template);
            assertEquals(5, template.getSteps().size());
        }

        @Test
        @DisplayName("预置模板 — DAILY_BRIEFING")
        void shouldHaveDailyBriefingTemplate() {
            SkillComposer composer = new SkillComposer();
            SkillPipeline template = composer.getTemplate("DAILY_BRIEFING");

            assertNotNull(template);
            assertEquals(4, template.getSteps().size());
        }

        @Test
        @DisplayName("意图匹配 — 搜索意图匹配 SEARCH_AND_SYNTHESIZE")
        void shouldMatchSearchIntent() {
            SkillComposer composer = new SkillComposer();
            List<SkillComposer.MatchedTemplate> matches = composer.match("搜索最新 AI 新闻");

            assertFalse(matches.isEmpty());
            assertThat(matches.get(0).name()).isEqualTo("SEARCH_AND_SYNTHESIZE");
        }

        @Test
        @DisplayName("意图匹配 — 文档意图匹配 DOCUMENT_GENERATE")
        void shouldMatchDocumentIntent() {
            SkillComposer composer = new SkillComposer();
            List<SkillComposer.MatchedTemplate> matches = composer.match("生成一份项目报告文档");

            assertFalse(matches.isEmpty());
            assertThat(matches.get(0).name()).isEqualTo("DOCUMENT_GENERATE");
        }

        @Test
        @DisplayName("意图匹配 — 日报意图匹配 DAILY_BRIEFING")
        void shouldMatchDailyIntent() {
            SkillComposer composer = new SkillComposer();
            List<SkillComposer.MatchedTemplate> matches = composer.match("生成今日简报");

            assertFalse(matches.isEmpty());
            assertThat(matches.get(0).name()).isEqualTo("DAILY_BRIEFING");
        }

        @Test
        @DisplayName("模板列表 — 包含所有预置模板")
        void shouldListAllTemplates() {
            SkillComposer composer = new SkillComposer();
            List<String> templates = composer.listTemplates();

            assertThat(templates).contains(
                    "SEARCH_AND_SYNTHESIZE", "FACT_CHECK",
                    "DOCUMENT_GENERATE", "DAILY_BRIEFING");
        }
    }

    // ==================== BM25 Tests ====================

    @Nested
    @DisplayName("Bm25Scorer")
    class Bm25ScorerTests {

        @Test
        @DisplayName("完全相同文档得分最高")
        void shouldScoreExactMatchHighest() {
            List<String> docs = List.of("Python 编程语言学习指南", "Java 编程入门", "Python 数据分析");
            Bm25Scorer scorer = new Bm25Scorer(docs);

            double score1 = scorer.score("Python 编程语言学习指南", "Python 编程");
            double score2 = scorer.score("Java 编程入门", "Python 编程");

            assertTrue(score1 > score2, "Python doc should score higher than Java doc");
            assertTrue(score1 > 0, "Related doc should have positive score");
        }

        @Test
        @DisplayName("不相关文档得分为 0")
        void shouldScoreZeroForIrrelevant() {
            List<String> docs = List.of("Python 编程", "Java 开发", "前端 React");
            Bm25Scorer scorer = new Bm25Scorer(docs);

            double score = scorer.score("今天天气很好", "Python 编程");
            assertEquals(0, score, 0.001);
        }

        @Test
        @DisplayName("Tokenize — 中文分词")
        void shouldTokenizeChinese() {
            List<String> tokens = Bm25Scorer.tokenize("Python编程语言学习指南");
            assertThat(tokens).contains("python编程语言学习指南");
        }

        @Test
        @DisplayName("Tokenize — 混合中英文")
        void shouldTokenizeMixed() {
            List<String> tokens = Bm25Scorer.tokenize("学习 Python 和 Java 编程");
            assertThat(tokens).isNotEmpty();
            assertThat(tokens).contains("python", "java");
        }
    }

    // ==================== HybridRetriever Tests ====================

    @Nested
    @DisplayName("HybridRetriever")
    class HybridRetrieverTests {

        @Test
        @DisplayName("基本检索 — 返回相关结果")
        void shouldRetrieveRelevantResults() {
            List<String> allDocs = List.of(
                    "用户喜欢 Python 编程",
                    "用户偏好使用 VS Code 编辑器",
                    "用户昨天吃了火锅",
                    "用户最常用的框架是 Spring Boot",
                    "用户不喜欢 Java Swing"
            );

            HybridRetriever retriever = new HybridRetriever(allDocs);

            List<RetrievalScorer.ScoredMemory> candidates = allDocs.stream()
                    .map(doc -> new RetrievalScorer.ScoredMemory(
                            doc, "PREFERENCE", 80, java.time.Instant.now()))
                    .toList();

            List<HybridRetriever.HybridRetrievalResult> results =
                    retriever.retrieve("Python 编程", candidates, 3);

            assertFalse(results.isEmpty());
            assertTrue(results.size() <= 3);
            assertTrue(results.get(0).score() > 0);
        }

        @Test
        @DisplayName("空查询 — 返回空结果")
        void shouldReturnEmptyForBlankQuery() {
            List<String> allDocs = List.of("test doc");
            HybridRetriever retriever = new HybridRetriever(allDocs);

            List<RetrievalScorer.ScoredMemory> candidates = List.of(
                    new RetrievalScorer.ScoredMemory("test doc", "FACT", 50, java.time.Instant.now()));

            List<HybridRetriever.HybridRetrievalResult> results =
                    retriever.retrieve("", candidates, 5);

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("Jaccard 去重 — 相似内容只保留一条")
        void shouldDeduplicateSimilarContent() {
            List<String> allDocs = List.of(
                    "Python is the best programming language",
                    "Python is the best programming language for beginners",
                    "Java is another programming language"
            );

            HybridRetriever retriever = new HybridRetriever(allDocs);

            List<RetrievalScorer.ScoredMemory> candidates = allDocs.stream()
                    .map(doc -> new RetrievalScorer.ScoredMemory(
                            doc, "PREFERENCE", 80, java.time.Instant.now()))
                    .toList();

            List<HybridRetriever.HybridRetrievalResult> results =
                    retriever.retrieve("Python programming", candidates, 5);

            assertTrue(results.size() <= 2,
                    "Expected ≤2 results after dedup, got " + results.size());
        }

        @Test
        @DisplayName("RetrievalQualityMetrics — 记录和报告")
        void shouldTrackQualityMetrics() {
            List<String> allDocs = List.of("Python 编程", "Java 开发", "前端 React");
            HybridRetriever retriever = new HybridRetriever(allDocs);

            List<RetrievalScorer.ScoredMemory> candidates = allDocs.stream()
                    .map(doc -> new RetrievalScorer.ScoredMemory(
                            doc, "FACT", 50, java.time.Instant.now()))
                    .toList();

            retriever.retrieve("Python", candidates, 3);
            retriever.retrieve("", candidates, 3); // 零结果查询

            RetrievalQualityMetrics metrics = retriever.getMetrics();
            assertEquals(2, metrics.totalQueries());
            assertTrue(metrics.hitRate() > 0);
            assertTrue(metrics.zeroResultRate() > 0);
            assertTrue(metrics.avgLatencyMs() >= 0);
        }
    }

    // ==================== RetrievalScorer Tests ====================

    @Nested
    @DisplayName("RetrievalScorer — 多维打分")
    class RetrievalScorerTests {

        @Test
        @DisplayName("相关文档得分高于不相关文档")
        void shouldScoreRelevantHigher() {
            List<String> allDocs = List.of("Python 编程", "Java 开发", "前端 React");
            Bm25Scorer bm25 = new Bm25Scorer(allDocs);
            RetrievalScorer scorer = new RetrievalScorer(bm25);

            RetrievalScorer.ScoredMemory relevant = new RetrievalScorer.ScoredMemory(
                    "Python 编程语言学习", "PREFERENCE", 90, java.time.Instant.now());
            RetrievalScorer.ScoredMemory irrelevant = new RetrievalScorer.ScoredMemory(
                    "今天天气很好", "TEMPORARY", 10, java.time.Instant.now().minusSeconds(86400 * 30));

            RetrievalScorer.ScoredMemory scoredRelevant = scorer.score(relevant, "Python 编程");
            RetrievalScorer.ScoredMemory scoredIrrelevant = scorer.score(irrelevant, "Python 编程");

            assertTrue(scoredRelevant.score() > scoredIrrelevant.score(),
                    String.format("Relevant=%.3f should be > Irrelevant=%.3f",
                            scoredRelevant.score(), scoredIrrelevant.score()));
        }

        @Test
        @DisplayName("ScoreBreakdown — 各维度得分可分解")
        void shouldProvideScoreBreakdown() {
            List<String> allDocs = List.of("Python 编程学习");
            Bm25Scorer bm25 = new Bm25Scorer(allDocs);
            RetrievalScorer scorer = new RetrievalScorer(bm25);

            RetrievalScorer.ScoredMemory mem = new RetrievalScorer.ScoredMemory(
                    "Python 编程语言学习指南", "PREFERENCE", 90, java.time.Instant.now());

            RetrievalScorer.ScoredMemory scored = scorer.score(mem, "Python 编程");

            assertNotNull(scored.breakdown());
            assertTrue(scored.breakdown().bm25() >= 0);
            assertTrue(scored.breakdown().keyword() >= 0);
            assertTrue(scored.breakdown().recency() >= 0);
            assertTrue(scored.breakdown().importance() >= 0);
            assertTrue(scored.breakdown().typeWeight() >= 0);

            String debug = scored.breakdown().toDebugString();
            assertThat(debug).contains("BM25=");
            assertThat(debug).contains("Keyword=");
        }

        @Test
        @DisplayName("搜索优化权重 — BM25 权重更高")
        void shouldUseSearchOptimizedWeights() {
            List<String> allDocs = List.of("test");
            Bm25Scorer bm25 = new Bm25Scorer(allDocs);
            RetrievalScorer scorer = new RetrievalScorer(bm25, RetrievalScorer.ScorerWeights.searchOptimized());

            RetrievalScorer.ScoredMemory mem = new RetrievalScorer.ScoredMemory(
                    "test", "FACT", 50, java.time.Instant.now());

            RetrievalScorer.ScoredMemory scored = scorer.score(mem, "test");
            assertNotNull(scored.breakdown());

            RetrievalScorer.ScorerWeights weights = RetrievalScorer.ScorerWeights.searchOptimized();
            assertEquals(0.45, weights.bm25(), 0.001);
            assertEquals(0.30, weights.keyword(), 0.001);
        }
    }
}