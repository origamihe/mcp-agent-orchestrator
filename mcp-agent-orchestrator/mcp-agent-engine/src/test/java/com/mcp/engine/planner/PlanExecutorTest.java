package com.mcp.engine.planner;

import com.mcp.common.pipeline.PipelineResult;
import com.mcp.common.pipeline.PipelineStatus;
import com.mcp.common.planner.PlanDAG;
import com.mcp.common.planner.PlanNode;
import com.mcp.tools.executor.ToolExecutor;
import com.mcp.tools.model.ToolExecutionRequest;
import com.mcp.tools.model.ToolExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P5 验证 — PlanDAG / PlanExecutor 测试。
 * 验证：
 * 1. PlanDAG 模型（节点、依赖、循环检测）
 * 2. PlanDAGConverter（DAG → PipelineDefinition）
 * 3. PlanExecutor（DAG 计划执行）
 */
@DisplayName("P5 — PlanDAG & PlanExecutor")
class PlanExecutorTest {

    private MockToolExecutor mockExecutor;
    private com.mcp.engine.pipeline.ToolPipelineManager pipelineManager;
    private PlanDAGConverter converter;
    private PlanExecutor planExecutor;

    @BeforeEach
    void setUp() {
        mockExecutor = new MockToolExecutor();
        pipelineManager = new com.mcp.engine.pipeline.ToolPipelineManager(mockExecutor);
        converter = new PlanDAGConverter();
        planExecutor = new PlanExecutor(converter, pipelineManager);
    }

    // ==================== PlanDAG 模型验证 ====================

    @Nested
    @DisplayName("PlanDAG 模型")
    class PlanDAGModel {

        @Test
        @DisplayName("应正确构建简单 DAG")
        void shouldBuildSimpleDAG() {
            PlanDAG dag = PlanDAG.builder()
                    .id("dag1")
                    .intent("搜索并分析")
                    .addNode(PlanNode.builder().id("search").toolName("web_search").build())
                    .addNode(PlanNode.builder().id("analyze").toolName("analyze_content")
                            .dependsOn("search").build())
                    .build();

            assertThat(dag.getNodes()).hasSize(2);
            assertThat(dag.getIntent()).isEqualTo("搜索并分析");
            assertThat(dag.getNodes().get(1).getDependsOn()).contains("search");
        }

        @Test
        @DisplayName("应检测循环依赖")
        void shouldDetectCycle() {
            assertThatThrownBy(() ->
                    PlanDAG.builder()
                            .id("cycle")
                            .intent("循环")
                            .addNode(PlanNode.builder().id("a").toolName("t1").dependsOn("b").build())
                            .addNode(PlanNode.builder().id("b").toolName("t2").dependsOn("a").build())
                            .build()
            ).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cycle");
        }

        @Test
        @DisplayName("应检测缺失的依赖节点")
        void shouldDetectMissingDependency() {
            assertThatThrownBy(() ->
                    PlanDAG.builder()
                            .id("missing")
                            .intent("缺失依赖")
                            .addNode(PlanNode.builder().id("a").toolName("t1").dependsOn("nonexistent").build())
                            .build()
            ).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-existent");
        }

        @Test
        @DisplayName("应支持菱形依赖（DAG）")
        void shouldSupportDiamondDependency() {
            PlanDAG dag = PlanDAG.builder()
                    .id("diamond")
                    .intent("菱形依赖")
                    .addNode(PlanNode.builder().id("start").toolName("search").build())
                    .addNode(PlanNode.builder().id("analyze1").toolName("analyze_code")
                            .dependsOn("start").build())
                    .addNode(PlanNode.builder().id("analyze2").toolName("analyze_docs")
                            .dependsOn("start").build())
                    .addNode(PlanNode.builder().id("merge").toolName("merge_results")
                            .dependsOn("analyze1").dependsOn("analyze2").build())
                    .build();

            assertThat(dag.getNodes()).hasSize(4);
            PlanNode merge = dag.getNodes().get(3);
            assertThat(merge.getDependsOn()).containsExactly("analyze1", "analyze2");
        }

        @Test
        @DisplayName("应支持无依赖的独立节点")
        void shouldSupportIndependentNodes() {
            PlanDAG dag = PlanDAG.builder()
                    .id("independent")
                    .intent("独立任务")
                    .addNode(PlanNode.builder().id("task1").toolName("search_web").build())
                    .addNode(PlanNode.builder().id("task2").toolName("search_code").build())
                    .addNode(PlanNode.builder().id("task3").toolName("read_file").build())
                    .build();

            assertThat(dag.getNodes()).hasSize(3);
            dag.getNodes().forEach(n -> assertThat(n.getDependsOn()).isEmpty());
        }
    }

    // ==================== PlanDAGConverter 验证 ====================

    @Nested
    @DisplayName("PlanDAGConverter")
    class PlanDAGConverterTests {

        @Test
        @DisplayName("应将 DAG 正确转换为 PipelineDefinition")
        void shouldConvertDAGToPipeline() {
            PlanDAG dag = PlanDAG.builder()
                    .id("plan1")
                    .intent("搜索并生成报告")
                    .reasoning("先搜索，再分析，最后生成报告")
                    .addNode(PlanNode.builder().id("search").toolName("web_search")
                            .description("搜索相关信息").build())
                    .addNode(PlanNode.builder().id("analyze").toolName("analyze_content")
                            .dependsOn("search")
                            .inputMapping("content", "search.results").build())
                    .addNode(PlanNode.builder().id("report").toolName("generate_report")
                            .dependsOn("analyze")
                            .staticArg("format", "markdown").build())
                    .build();

            com.mcp.common.pipeline.PipelineDefinition pipeline = converter.convert(dag);

            assertThat(pipeline.getSteps()).hasSize(3);
            assertThat(pipeline.getName()).isEqualTo("搜索并生成报告");
            assertThat(pipeline.getDescription()).isEqualTo("先搜索，再分析，最后生成报告");

            com.mcp.common.pipeline.PipelineStep analyzeStep = pipeline.getSteps().get(1);
            assertThat(analyzeStep.getDependsOn()).contains("search");
            assertThat(analyzeStep.getInputMapping()).containsEntry("content", "search.results");

            com.mcp.common.pipeline.PipelineStep reportStep = pipeline.getSteps().get(2);
            assertThat(reportStep.getStaticArgs()).containsEntry("format", "markdown");
        }

        @Test
        @DisplayName("应保留回退工具配置")
        void shouldPreserveFallbackTool() {
            PlanDAG dag = PlanDAG.builder()
                    .id("fallback")
                    .intent("回退")
                    .addNode(PlanNode.builder().id("search").toolName("unstable_search")
                            .fallbackTool("stable_search").build())
                    .build();

            com.mcp.common.pipeline.PipelineDefinition pipeline = converter.convert(dag);

            assertThat(pipeline.getSteps().get(0).getFallbackTool()).isEqualTo("stable_search");
        }
    }

    // ==================== PlanExecutor 验证 ====================

    @Nested
    @DisplayName("PlanExecutor 执行")
    class PlanExecutorExecution {

        @Test
        @DisplayName("应正确执行简单 DAG 计划")
        void shouldExecuteSimpleDAG() {
            mockExecutor.setOutput("web_search", Map.of("results", List.of("r1", "r2")));
            mockExecutor.setOutput("analyze_content", Map.of("summary", "分析结果"));

            PlanDAG dag = PlanDAG.builder()
                    .id("exec1")
                    .intent("搜索并分析")
                    .addNode(PlanNode.builder().id("search").toolName("web_search").build())
                    .addNode(PlanNode.builder().id("analyze").toolName("analyze_content")
                            .dependsOn("search")
                            .inputMapping("content", "search.results").build())
                    .build();

            PipelineResult result = planExecutor.execute(dag).block();

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(PipelineStatus.COMPLETED);
            assertThat(result.getTotalSteps()).isEqualTo(2);
            assertThat(result.getSuccessSteps()).isEqualTo(2);
        }

        @Test
        @DisplayName("应支持数据流传递")
        void shouldSupportDataFlow() {
            mockExecutor.setOutput("web_search", Map.of("data", "hello_world"));
            mockExecutor.setOutput("process", Map.of("result", "ok"));

            PlanDAG dag = PlanDAG.builder()
                    .id("dataflow")
                    .intent("数据流")
                    .addNode(PlanNode.builder().id("search").toolName("web_search").build())
                    .addNode(PlanNode.builder().id("process").toolName("process")
                            .dependsOn("search")
                            .inputMapping("input", "search.data").build())
                    .build();

            PipelineResult result = planExecutor.execute(dag).block();

            assertThat(result.getStatus()).isEqualTo(PipelineStatus.COMPLETED);

            ToolExecutionRequest processReq = mockExecutor.requests.stream()
                    .filter(r -> r.getToolName().equals("process"))
                    .findFirst().orElse(null);
            assertThat(processReq).isNotNull();
            assertThat(processReq.getArguments()).containsEntry("input", "hello_world");
        }

        @Test
        @DisplayName("菱形依赖应正确执行")
        void shouldExecuteDiamondDAG() {
            mockExecutor.setOutput("search", Map.of("results", List.of("a", "b")));
            mockExecutor.setOutput("analyze1", Map.of("score", 85));
            mockExecutor.setOutput("analyze2", Map.of("score", 90));
            mockExecutor.setOutput("merge", Map.of("final", 88));

            PlanDAG dag = PlanDAG.builder()
                    .id("diamond")
                    .intent("菱形依赖")
                    .addNode(PlanNode.builder().id("start").toolName("search").build())
                    .addNode(PlanNode.builder().id("analyze1").toolName("analyze1").dependsOn("start").build())
                    .addNode(PlanNode.builder().id("analyze2").toolName("analyze2").dependsOn("start").build())
                    .addNode(PlanNode.builder().id("merge").toolName("merge")
                            .dependsOn("analyze1").dependsOn("analyze2").build())
                    .build();

            PipelineResult result = planExecutor.execute(dag).block();

            assertThat(result.getStatus()).isEqualTo(PipelineStatus.COMPLETED);
            assertThat(result.getTotalSteps()).isEqualTo(4);
            assertThat(result.getSuccessSteps()).isEqualTo(4);
        }

        @Test
        @DisplayName("节点失败应有回退")
        void shouldFallbackOnFailure() {
            mockExecutor.setError("unstable", "Connection error");
            mockExecutor.setOutput("stable", Map.of("data", "fallback_result"));

            PlanDAG dag = PlanDAG.builder()
                    .id("fallback")
                    .intent("回退测试")
                    .addNode(PlanNode.builder().id("search").toolName("unstable")
                            .fallbackTool("stable").build())
                    .build();

            PipelineResult result = planExecutor.execute(dag).block();

            assertThat(result.getStatus()).isEqualTo(PipelineStatus.COMPLETED);
            assertThat(result.getStepResults().get(0).getFallbackToolUsed()).isEqualTo("stable");
        }
    }

    // ==================== Mock Tool Executor ====================

    static class MockToolExecutor implements ToolExecutor {

        final Map<String, Object> toolOutputs = new java.util.concurrent.ConcurrentHashMap<>();
        final Map<String, String> toolErrors = new java.util.concurrent.ConcurrentHashMap<>();
        final List<ToolExecutionRequest> requests = new CopyOnWriteArrayList<>();
        final AtomicInteger callCount = new AtomicInteger(0);

        void setOutput(String toolName, Object output) {
            toolOutputs.put(toolName, output);
        }

        void setError(String toolName, String errorMessage) {
            toolErrors.put(toolName, errorMessage);
        }

        @Override
        public Mono<ToolExecutionResult> execute(ToolExecutionRequest request) {
            requests.add(request);
            callCount.incrementAndGet();

            String toolName = request.getToolName();
            if (toolErrors.containsKey(toolName)) {
                return Mono.error(new RuntimeException(toolErrors.get(toolName)));
            }
            Object output = toolOutputs.getOrDefault(toolName,
                    Map.of("tool", toolName, "args", request.getArguments()));
            return Mono.just(ToolExecutionResult.success(request.getRequestId(), toolName, output, java.time.Duration.ZERO));
        }
    }
}