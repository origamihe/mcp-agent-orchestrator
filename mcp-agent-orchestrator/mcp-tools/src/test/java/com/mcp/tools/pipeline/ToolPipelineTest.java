package com.mcp.tools.pipeline;

import com.mcp.tools.executor.ToolExecutor;
import com.mcp.tools.model.ToolExecutionRequest;
import com.mcp.tools.model.ToolExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tool Pipeline — 工具管道编排")
@SuppressWarnings("unchecked")
class ToolPipelineTest {

    @Mock
    private ToolExecutor toolExecutor;

    private ToolPipelineExecutor pipelineExecutor;

    @BeforeEach
    void setUp() {
        pipelineExecutor = new ToolPipelineExecutor(toolExecutor, null);
    }

    @Nested
    @DisplayName("参数引用解析")
    class ArgumentResolution {

        @Test
        @DisplayName("字面量参数直接传递")
        void shouldPassLiteralArguments() {
            Map<String, Object> stepOutputs = Map.of();
            Map<String, Object> args = Map.of("query", "hello world", "limit", 10);

            Map<String, Object> resolved = pipelineExecutor.resolveArguments(args, stepOutputs);

            assertThat(resolved).containsEntry("query", "hello world");
            assertThat(resolved).containsEntry("limit", 10);
        }

        @Test
        @DisplayName("${stepId} 引用完整步骤输出")
        void shouldResolveStepIdReference() {
            Map<String, Object> stepOutputs = Map.of("search", "search results here");
            Map<String, Object> args = Map.of("content", "${search}");

            Map<String, Object> resolved = pipelineExecutor.resolveArguments(args, stepOutputs);

            assertThat(resolved).containsEntry("content", "search results here");
        }

        @Test
        @DisplayName("${stepId.fieldName} 引用步骤输出的特定字段")
        void shouldResolveStepIdFieldReference() {
            Map<String, Object> stepOutputs = Map.of("search", Map.of("data", "extracted data", "count", 5));
            Map<String, Object> args = Map.of("content", "${search.data}");

            Map<String, Object> resolved = pipelineExecutor.resolveArguments(args, stepOutputs);

            assertThat(resolved).containsEntry("content", "extracted data");
        }

        @Test
        @DisplayName("混合字面量和引用的参数")
        void shouldMixLiteralAndReference() {
            Map<String, Object> stepOutputs = Map.of("search", "search results");
            Map<String, Object> args = Map.of(
                    "title", "Research Report",
                    "content", "${search}",
                    "format", "docx"
            );

            Map<String, Object> resolved = pipelineExecutor.resolveArguments(args, stepOutputs);

            assertThat(resolved).containsEntry("title", "Research Report");
            assertThat(resolved).containsEntry("content", "search results");
            assertThat(resolved).containsEntry("format", "docx");
        }

        @Test
        @DisplayName("引用不存在的步骤返回空字符串")
        void shouldReturnEmptyForMissingStep() {
            Map<String, Object> stepOutputs = Map.of();
            Map<String, Object> args = Map.of("content", "${nonexistent}");

            Map<String, Object> resolved = pipelineExecutor.resolveArguments(args, stepOutputs);

            assertThat(resolved).containsEntry("content", "");
        }

        @Test
        @DisplayName("null 参数返回空 Map")
        void shouldReturnEmptyForNullArgs() {
            Map<String, Object> resolved = pipelineExecutor.resolveArguments(null, Map.of());

            assertThat(resolved).isEmpty();
        }
    }

    @Nested
    @DisplayName("管道执行")
    class PipelineExecution {

        @Test
        @DisplayName("单步管道执行成功")
        void shouldExecuteSingleStepPipeline() {
            when(toolExecutor.execute(any())).thenReturn(Mono.just(ToolExecutionResult.success("step1", "test_tool", "step1 output", java.time.Duration.ZERO)));

            ToolPipeline pipeline = ToolPipeline.builder()
                    .pipelineId("test-single")
                    .name("Test Single Step")
                    .steps(List.of(
                            ToolPipelineStep.builder()
                                    .stepId("step1")
                                    .toolName("test_tool")
                                    .arguments(Map.of("input", "test"))
                                    .build()
                    ))
                    .build();

            StepVerifier.create(pipelineExecutor.execute(pipeline))
                    .assertNext(result -> {
                        assertThat(result.isSuccess()).isTrue();
                        assertThat(result.getFinalOutput()).isEqualTo("step1 output");
                        assertThat(result.getStepResults()).hasSize(1);
                        assertThat(result.getStepResults().get("step1").isSuccess()).isTrue();
                    })
                    .verifyComplete();

            verify(toolExecutor, times(1)).execute(any());
        }

        @Test
        @DisplayName("多步管道顺序执行并传递引用")
        void shouldExecuteMultiStepPipelineWithReferences() {
            when(toolExecutor.execute(any())).thenReturn(
                    Mono.just(ToolExecutionResult.success("stepA", "tool_a", Map.of("data", "result from A"), java.time.Duration.ZERO)),
                    Mono.just(ToolExecutionResult.success("stepB", "tool_b", "result from B", java.time.Duration.ZERO)));

            ToolPipeline pipeline = ToolPipeline.builder()
                    .pipelineId("test-multi")
                    .name("Test Multi Step")
                    .steps(List.of(
                            ToolPipelineStep.builder()
                                    .stepId("stepA")
                                    .toolName("tool_a")
                                    .arguments(Map.of("query", "hello"))
                                    .extractField("data")
                                    .build(),
                            ToolPipelineStep.builder()
                                    .stepId("stepB")
                                    .toolName("tool_b")
                                    .arguments(Map.of("input", "${stepA}"))
                                    .build()
                    ))
                    .build();

            StepVerifier.create(pipelineExecutor.execute(pipeline))
                    .assertNext(result -> {
                        assertThat(result.isSuccess()).isTrue();
                        assertThat(result.getStepResults()).hasSize(2);
                        assertThat(result.getStepResults().get("stepA").isSuccess()).isTrue();
                        assertThat(result.getStepResults().get("stepB").isSuccess()).isTrue();
                    })
                    .verifyComplete();

            verify(toolExecutor, times(2)).execute(any());
        }

        @Test
        @DisplayName("failFast=true 时步骤失败终止管道")
        void shouldStopOnFailFastFailure() {
            when(toolExecutor.execute(any()))
                    .thenReturn(Mono.just(ToolExecutionResult.success("step1", "tool_a", "step1 ok", java.time.Duration.ZERO)))
                    .thenReturn(Mono.error(new RuntimeException("step2 failed")));

            ToolPipeline pipeline = ToolPipeline.builder()
                    .pipelineId("test-failfast")
                    .steps(List.of(
                            ToolPipelineStep.builder().stepId("step1").toolName("tool_a").build(),
                            ToolPipelineStep.builder().stepId("step2").toolName("tool_b").failFast(true).build(),
                            ToolPipelineStep.builder().stepId("step3").toolName("tool_c").build()
                    ))
                    .build();

            StepVerifier.create(pipelineExecutor.execute(pipeline))
                    .assertNext(result -> {
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getError()).contains("step2 failed");
                        assertThat(result.getStepResults()).hasSize(2);
                        assertThat(result.getStepResults().get("step1").isSuccess()).isTrue();
                        assertThat(result.getStepResults().get("step2").isSuccess()).isFalse();
                        assertThat(result.getStepResults()).doesNotContainKey("step3");
                    })
                    .verifyComplete();

            verify(toolExecutor, times(2)).execute(any());
        }

        @Test
        @DisplayName("failFast=false 时步骤失败继续执行")
        void shouldContinueOnNonFailFastFailure() {
            when(toolExecutor.execute(any()))
                    .thenReturn(Mono.just(ToolExecutionResult.success("step1", "tool_a", "step1 ok", java.time.Duration.ZERO)))
                    .thenReturn(Mono.error(new RuntimeException("step2 failed")))
                    .thenReturn(Mono.just(ToolExecutionResult.success("step3", "tool_c", "step3 ok", java.time.Duration.ZERO)));

            ToolPipeline pipeline = ToolPipeline.builder()
                    .pipelineId("test-non-failfast")
                    .steps(List.of(
                            ToolPipelineStep.builder().stepId("step1").toolName("tool_a").build(),
                            ToolPipelineStep.builder().stepId("step2").toolName("tool_b").failFast(false).build(),
                            ToolPipelineStep.builder().stepId("step3").toolName("tool_c").build()
                    ))
                    .build();

            StepVerifier.create(pipelineExecutor.execute(pipeline))
                    .assertNext(result -> {
                        assertThat(result.isSuccess()).isTrue();
                        assertThat(result.getStepResults()).hasSize(3);
                        assertThat(result.getStepResults().get("step2").isSuccess()).isFalse();
                        assertThat(result.getStepResults().get("step3").isSuccess()).isTrue();
                    })
                    .verifyComplete();

            verify(toolExecutor, times(3)).execute(any());
        }
    }

    @Nested
    @DisplayName("管道注册中心")
    class PipelineRegistryTests {

        @Test
        @DisplayName("预注册 4 个默认管道")
        void shouldHaveDefaultPipelines() {
            PipelineRegistry registry = new PipelineRegistry();

            assertThat(registry.listAll()).hasSize(4);
            assertThat(registry.contains("search-and-generate-docx")).isTrue();
            assertThat(registry.contains("search-and-generate-ppt")).isTrue();
            assertThat(registry.contains("search-and-summarize")).isTrue();
            assertThat(registry.contains("fetch-and-validate")).isTrue();
        }

        @Test
        @DisplayName("动态注册和注销管道")
        void shouldRegisterAndUnregister() {
            PipelineRegistry registry = new PipelineRegistry();
            int initialCount = registry.listAll().size();

            ToolPipeline custom = ToolPipeline.builder()
                    .pipelineId("custom-pipeline")
                    .name("Custom Pipeline")
                    .steps(List.of())
                    .build();

            registry.register(custom);
            assertThat(registry.listAll()).hasSize(initialCount + 1);
            assertThat(registry.get("custom-pipeline")).isNotNull();

            registry.unregister("custom-pipeline");
            assertThat(registry.listAll()).hasSize(initialCount);
            assertThat(registry.get("custom-pipeline")).isNull();
        }

        @Test
        @DisplayName("search-and-generate-docx 管道包含 2 个步骤")
        void shouldHaveSearchAndGeneratePipeline() {
            PipelineRegistry registry = new PipelineRegistry();
            ToolPipeline pipeline = registry.get("search-and-generate-docx");

            assertThat(pipeline).isNotNull();
            assertThat(pipeline.getSteps()).hasSize(2);
            assertThat(pipeline.getSteps().get(0).getToolName()).isEqualTo("multi_search");
            assertThat(pipeline.getSteps().get(1).getToolName()).isEqualTo("generate_docx");
        }
    }
}