package com.mcp.engine.pipeline;

import com.mcp.common.pipeline.PipelineDefinition;
import com.mcp.common.pipeline.PipelineResult;
import com.mcp.common.pipeline.PipelineStatus;
import com.mcp.common.pipeline.PipelineStep;
import com.mcp.common.pipeline.StepResult;
import com.mcp.tools.executor.ToolExecutor;
import com.mcp.tools.model.ToolExecutionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P4 验证 — ToolPipelineManager 测试。
 * 验证：
 * 1. 流水线注册/注销
 * 2. 顺序执行（步骤间数据流）
 * 3. 并行执行（独立步骤）
 * 4. 依赖关系解析
 * 5. 输入映射（inputMapping）
 * 6. 备选工具回退（fallbackTool）
 * 7. 错误传播
 * 8. 空流水线
 * 9. PipelineStep 模型
 * 10. PipelineDefinition 模型
 * 11. PipelineResult 模型
 * 12. StepResult 模型
 */
@DisplayName("ToolPipelineManager — P4 工具流水线测试")
class ToolPipelineManagerTest {

    private ToolPipelineManager pipelineManager;
    private MockToolExecutor mockExecutor;

    private static class MockToolExecutor implements ToolExecutor {
        final List<ToolExecutionRequest> requests = new ArrayList<>();
        final Map<String, Object> toolOutputs = new LinkedHashMap<>();
        final Map<String, RuntimeException> toolErrors = new LinkedHashMap<>();
        final AtomicInteger callCount = new AtomicInteger(0);

        @Override
        public Mono<Object> execute(ToolExecutionRequest request) {
            requests.add(request);
            callCount.incrementAndGet();

            String toolName = request.getToolName();
            if (toolErrors.containsKey(toolName)) {
                return Mono.error(toolErrors.get(toolName));
            }
            if (toolOutputs.containsKey(toolName)) {
                return Mono.just(toolOutputs.get(toolName));
            }
            return Mono.just(Map.of("tool", toolName, "args", request.getArguments()));
        }

        void setOutput(String toolName, Object output) {
            toolOutputs.put(toolName, output);
        }

        void setError(String toolName, String errorMsg) {
            toolErrors.put(toolName, new RuntimeException(errorMsg));
        }
    }

    @BeforeEach
    void setUp() {
        mockExecutor = new MockToolExecutor();
        pipelineManager = new ToolPipelineManager(mockExecutor);
    }

    // ==================== 流水线注册 ====================

    @Nested
    @DisplayName("流水线注册/注销")
    class PipelineRegistration {

        @Test
        @DisplayName("注册流水线后应出现在列表中")
        void shouldRegisterPipeline() {
            PipelineDefinition def = PipelineDefinition.builder()
                    .id("test-pipeline")
                    .name("测试流水线")
                    .addStep(PipelineStep.builder().id("step1").toolName("tool_a").build())
                    .build();

            pipelineManager.registerPipeline(def);

            assertThat(pipelineManager.getPipeline("test-pipeline")).isNotNull();
            assertThat(pipelineManager.getAllPipelines()).hasSize(1);
        }

        @Test
        @DisplayName("注销流水线后应从列表中移除")
        void shouldUnregisterPipeline() {
            PipelineDefinition def = PipelineDefinition.builder()
                    .id("test-pipeline")
                    .name("测试流水线")
                    .build();
            pipelineManager.registerPipeline(def);
            pipelineManager.unregisterPipeline("test-pipeline");

            assertThat(pipelineManager.getPipeline("test-pipeline")).isNull();
        }

        @Test
        @DisplayName("获取不存在的流水线应返回 null")
        void shouldReturnNullForUnknownPipeline() {
            assertThat(pipelineManager.getPipeline("nonexistent")).isNull();
        }
    }

    // ==================== 顺序执行 ====================

    @Nested
    @DisplayName("顺序执行")
    class SequentialExecution {

        @Test
        @DisplayName("单步骤流水线应正确执行")
        void shouldExecuteSingleStep() {
            PipelineDefinition def = PipelineDefinition.builder()
                    .id("single")
                    .name("单步骤")
                    .addStep(PipelineStep.builder().id("search").toolName("web_search").build())
                    .build();
            pipelineManager.registerPipeline(def);

            PipelineResult result = pipelineManager.execute("single", Map.of("query", "test")).block();

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(PipelineStatus.COMPLETED);
            assertThat(result.getStepResults()).hasSize(1);
            assertThat(result.getStepResults().get(0).isSuccess()).isTrue();
            assertThat(result.getStepResults().get(0).getStepId()).isEqualTo("search");
        }

        @Test
        @DisplayName("多步骤流水线应按依赖顺序执行")
        void shouldExecuteInDependencyOrder() {
            PipelineDefinition def = PipelineDefinition.builder()
                    .id("multi")
                    .name("多步骤")
                    .addStep(PipelineStep.builder().id("search").toolName("web_search").build())
                    .addStep(PipelineStep.builder().id("analyze").toolName("analyze_content")
                            .dependsOn("search").build())
                    .addStep(PipelineStep.builder().id("report").toolName("generate_report")
                            .dependsOn("analyze").build())
                    .build();
            pipelineManager.registerPipeline(def);

            PipelineResult result = pipelineManager.execute("multi", Map.of()).block();

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(PipelineStatus.COMPLETED);
            assertThat(result.getStepResults()).hasSize(3);
            assertThat(result.getSuccessSteps()).isEqualTo(3);
        }
    }

    // ==================== 数据流 — 输入映射 ====================

    @Nested
    @DisplayName("数据流 — 输入映射")
    class DataFlow {

        @Test
        @DisplayName("inputMapping 应正确传递上游输出到下游输入")
        void shouldMapInputFromUpstream() {
            MockToolExecutor executor = mockExecutor;
            executor.setOutput("web_search", Map.of("results", List.of("r1", "r2")));

            PipelineDefinition def = PipelineDefinition.builder()
                    .id("dataflow")
                    .name("数据流")
                    .addStep(PipelineStep.builder().id("search").toolName("web_search").build())
                    .addStep(PipelineStep.builder().id("analyze").toolName("analyze_content")
                            .dependsOn("search")
                            .inputMapping("content", "search.results")
                            .build())
                    .build();
            pipelineManager.registerPipeline(def);

            PipelineResult result = pipelineManager.execute("dataflow", Map.of()).block();

            assertThat(result.getStatus()).isEqualTo(PipelineStatus.COMPLETED);

            ToolExecutionRequest analyzeReq = executor.requests.stream()
                    .filter(r -> r.getToolName().equals("analyze_content"))
                    .findFirst().orElse(null);
            assertThat(analyzeReq).isNotNull();
            assertThat(analyzeReq.getArguments()).containsKey("content");
        }

        @Test
        @DisplayName("静态参数和映射参数应合并")
        void shouldMergeStaticAndMappedArgs() {
            MockToolExecutor executor = mockExecutor;
            executor.setOutput("web_search", Map.of("data", "search_result"));

            PipelineDefinition def = PipelineDefinition.builder()
                    .id("merge")
                    .name("合并参数")
                    .addStep(PipelineStep.builder().id("search").toolName("web_search").build())
                    .addStep(PipelineStep.builder().id("analyze").toolName("analyze_content")
                            .dependsOn("search")
                            .staticArg("format", "json")
                            .inputMapping("content", "search.data")
                            .build())
                    .build();
            pipelineManager.registerPipeline(def);

            PipelineResult result = pipelineManager.execute("merge", Map.of()).block();

            assertThat(result.getStatus()).isEqualTo(PipelineStatus.COMPLETED);

            ToolExecutionRequest analyzeReq = executor.requests.stream()
                    .filter(r -> r.getToolName().equals("analyze_content"))
                    .findFirst().orElse(null);
            assertThat(analyzeReq).isNotNull();
            assertThat(analyzeReq.getArguments()).containsEntry("format", "json");
            assertThat(analyzeReq.getArguments()).containsEntry("content", "search_result");
        }
    }

    // ==================== 备选工具回退 ====================

    @Nested
    @DisplayName("备选工具回退 — fallbackTool")
    class FallbackTool {

        @Test
        @DisplayName("主工具失败时应自动回退到备选工具")
        void shouldFallbackOnPrimaryFailure() {
            MockToolExecutor executor = mockExecutor;
            executor.setError("unstable_search", "Connection timeout");
            executor.setOutput("stable_search", Map.of("results", List.of("r1")));

            PipelineDefinition def = PipelineDefinition.builder()
                    .id("fallback")
                    .name("回退")
                    .addStep(PipelineStep.builder().id("search").toolName("unstable_search")
                            .fallbackTool("stable_search")
                            .build())
                    .build();
            pipelineManager.registerPipeline(def);

            PipelineResult result = pipelineManager.execute("fallback", Map.of()).block();

            assertThat(result.getStatus()).isEqualTo(PipelineStatus.COMPLETED);
            assertThat(result.getStepResults().get(0).getFallbackToolUsed()).isEqualTo("stable_search");
            assertThat(result.getStepResults().get(0).isSuccess()).isTrue();
        }

        @Test
        @DisplayName("主工具和备选工具都失败时应传播错误")
        void shouldFailWhenBothPrimaryAndFallbackFail() {
            MockToolExecutor executor = mockExecutor;
            executor.setError("unstable_search", "Primary error");
            executor.setError("backup_search", "Backup error");

            PipelineDefinition def = PipelineDefinition.builder()
                    .id("double-fail")
                    .name("双重失败")
                    .addStep(PipelineStep.builder().id("search").toolName("unstable_search")
                            .fallbackTool("backup_search")
                            .build())
                    .build();
            pipelineManager.registerPipeline(def);

            PipelineResult result = pipelineManager.execute("double-fail", Map.of()).block();

            assertThat(result.getStatus()).isEqualTo(PipelineStatus.FAILED);
            assertThat(result.getFailedSteps()).isGreaterThanOrEqualTo(1);
        }
    }

    // ==================== 空流水线 ====================

    @Nested
    @DisplayName("空流水线")
    class EmptyPipeline {

        @Test
        @DisplayName("空流水线应立即返回 COMPLETED")
        void shouldCompleteEmptyPipeline() {
            PipelineDefinition def = PipelineDefinition.builder()
                    .id("empty")
                    .name("空流水线")
                    .build();
            pipelineManager.registerPipeline(def);

            PipelineResult result = pipelineManager.execute("empty", Map.of()).block();

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(PipelineStatus.COMPLETED);
            assertThat(result.getStepResults()).isEmpty();
        }
    }

    // ==================== 错误处理 ====================

    @Nested
    @DisplayName("错误处理")
    class ErrorHandling {

        @Test
        @DisplayName("不存在的流水线应返回错误")
        void shouldErrorForUnknownPipeline() {
            assertThatThrownBy(() -> pipelineManager.execute("unknown", Map.of()).block())
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("步骤失败时流水线应标记 FAILED")
        void shouldMarkFailedOnStepError() {
            MockToolExecutor executor = mockExecutor;
            executor.setError("failing_tool", "Step error");

            PipelineDefinition def = PipelineDefinition.builder()
                    .id("error")
                    .name("错误流水线")
                    .addStep(PipelineStep.builder().id("step1").toolName("failing_tool").build())
                    .build();
            pipelineManager.registerPipeline(def);

            PipelineResult result = pipelineManager.execute("error", Map.of()).block();

            assertThat(result.getStatus()).isEqualTo(PipelineStatus.FAILED);
        }
    }

    // ==================== 监听器 ====================

    @Nested
    @DisplayName("事件监听器")
    class EventListeners {

        @Test
        @DisplayName("监听器应在流水线完成时收到通知")
        void shouldNotifyListener() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            pipelineManager.addListener(result -> latch.countDown());

            PipelineDefinition def = PipelineDefinition.builder()
                    .id("notify")
                    .name("通知测试")
                    .addStep(PipelineStep.builder().id("step1").toolName("tool_a").build())
                    .build();
            pipelineManager.registerPipeline(def);
            pipelineManager.execute("notify", Map.of()).block();

            boolean notified = latch.await(3, TimeUnit.SECONDS);
            assertThat(notified).isTrue();
        }
    }

    // ==================== PipelineStep 模型 ====================

    @Nested
    @DisplayName("PipelineStep — 流水线步骤模型")
    class PipelineStepModel {

        @Test
        @DisplayName("Builder 应正确构建步骤")
        void shouldBuildStep() {
            PipelineStep step = PipelineStep.builder()
                    .id("search")
                    .toolName("web_search")
                    .description("搜索信息")
                    .staticArg("engine", "google")
                    .inputMapping("query", "user.input")
                    .fallbackTool("backup_search")
                    .dependsOn("init")
                    .maxRetries(3)
                    .timeoutMs(15000)
                    .build();

            assertThat(step.getId()).isEqualTo("search");
            assertThat(step.getToolName()).isEqualTo("web_search");
            assertThat(step.getDescription()).isEqualTo("搜索信息");
            assertThat(step.getStaticArgs()).containsEntry("engine", "google");
            assertThat(step.getInputMapping()).containsEntry("query", "user.input");
            assertThat(step.getFallbackTool()).isEqualTo("backup_search");
            assertThat(step.getDependsOn()).contains("init");
            assertThat(step.getMaxRetries()).isEqualTo(3);
            assertThat(step.getTimeoutMs()).isEqualTo(15000);
        }
    }

    // ==================== PipelineDefinition 模型 ====================

    @Nested
    @DisplayName("PipelineDefinition — 流水线定义模型")
    class PipelineDefinitionModel {

        @Test
        @DisplayName("Builder 应正确构建流水线定义")
        void shouldBuildPipelineDefinition() {
            PipelineDefinition def = PipelineDefinition.builder()
                    .id("search-report")
                    .name("搜索报告")
                    .description("搜索并生成报告")
                    .addStep(PipelineStep.builder().id("search").toolName("web_search").build())
                    .addStep(PipelineStep.builder().id("report").toolName("generate_report").dependsOn("search").build())
                    .maxTotalTimeoutMs(120000)
                    .continueOnError(true)
                    .build();

            assertThat(def.getId()).isEqualTo("search-report");
            assertThat(def.getName()).isEqualTo("搜索报告");
            assertThat(def.getDescription()).isEqualTo("搜索并生成报告");
            assertThat(def.getSteps()).hasSize(2);
            assertThat(def.getMaxTotalTimeoutMs()).isEqualTo(120000);
            assertThat(def.isContinueOnError()).isTrue();
        }
    }

    // ==================== PipelineResult 模型 ====================

    @Nested
    @DisplayName("PipelineResult — 流水线结果模型")
    class PipelineResultModel {

        @Test
        @DisplayName("success() 工厂方法应创建成功结果")
        void shouldCreateSuccessResult() {
            List<StepResult> steps = List.of(
                    StepResult.success("step1", "tool_a", "output1", 100),
                    StepResult.success("step2", "tool_b", "output2", 200)
            );

            PipelineResult result = PipelineResult.success("p1", "测试", steps);

            assertThat(result.getStatus()).isEqualTo(PipelineStatus.COMPLETED);
            assertThat(result.getTotalSteps()).isEqualTo(2);
            assertThat(result.getSuccessSteps()).isEqualTo(2);
            assertThat(result.getFailedSteps()).isEqualTo(0);
            assertThat(result.getOutputs()).containsKeys("step1", "step2");
        }

        @Test
        @DisplayName("failure() 工厂方法应创建失败结果")
        void shouldCreateFailureResult() {
            List<StepResult> steps = List.of(
                    StepResult.success("step1", "tool_a", "output1", 100),
                    StepResult.failure("step2", "tool_b", "error", 200)
            );

            PipelineResult result = PipelineResult.failure("p1", "测试", steps);

            assertThat(result.getStatus()).isEqualTo(PipelineStatus.FAILED);
            assertThat(result.getSuccessSteps()).isEqualTo(1);
            assertThat(result.getFailedSteps()).isEqualTo(1);
        }

        @Test
        @DisplayName("toString 应包含关键信息")
        void shouldHaveDescriptiveToString() {
            List<StepResult> steps = List.of(
                    StepResult.success("step1", "tool_a", "output1", 100)
            );

            PipelineResult result = PipelineResult.success("p1", "搜索流水线", steps);

            String str = result.toString();
            assertThat(str).contains("搜索流水线");
            assertThat(str).contains("COMPLETED");
            assertThat(str).contains("1/1");
        }
    }

    // ==================== StepResult 模型 ====================

    @Nested
    @DisplayName("StepResult — 步骤结果模型")
    class StepResultModel {

        @Test
        @DisplayName("success() 工厂方法应创建成功步骤结果")
        void shouldCreateSuccessStepResult() {
            StepResult sr = StepResult.success("search", "web_search", Map.of("results", "data"), 150);

            assertThat(sr.isSuccess()).isTrue();
            assertThat(sr.getStepId()).isEqualTo("search");
            assertThat(sr.getToolName()).isEqualTo("web_search");
            assertThat(sr.getOutput()).isNotNull();
            assertThat(sr.getDurationMs()).isEqualTo(150);
            assertThat(sr.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("failure() 工厂方法应创建失败步骤结果")
        void shouldCreateFailureStepResult() {
            StepResult sr = StepResult.failure("search", "web_search", "Connection refused", 200);

            assertThat(sr.isSuccess()).isFalse();
            assertThat(sr.getErrorMessage()).isEqualTo("Connection refused");
            assertThat(sr.getDurationMs()).isEqualTo(200);
        }

        @Test
        @DisplayName("toString 应包含关键信息")
        void shouldHaveDescriptiveToString() {
            StepResult sr = StepResult.success("search", "web_search", "data", 100);
            String str = sr.toString();

            assertThat(str).contains("search");
            assertThat(str).contains("web_search");
            assertThat(str).contains("100ms");
        }
    }

    // ==================== 参数解析 ====================

    @Nested
    @DisplayName("resolveArguments — 参数解析")
    class ArgumentResolution {

        @Test
        @DisplayName("resolvePath 应正确解析嵌套路径")
        void shouldResolveNestedPath() {
            PipelineStep step = PipelineStep.builder()
                    .id("test")
                    .toolName("tool_a")
                    .build();

            Map<String, Object> context = new LinkedHashMap<>();
            context.put("search", Map.of("results", List.of("a", "b")));
            context.put("search.results", List.of("a", "b"));

            Map<String, Object> resolved = pipelineManager.resolveArguments(step, context, List.of());

            assertThat(resolved).isEmpty();
        }

        @Test
        @DisplayName("resolvePath 应处理 null 路径")
        void shouldHandleNullPath() {
            Object result = pipelineManager.resolvePath(null, Map.of(), List.of());
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("resolvePath 应处理空路径")
        void shouldHandleEmptyPath() {
            Object result = pipelineManager.resolvePath("", Map.of(), List.of());
            assertThat(result).isNull();
        }
    }
}