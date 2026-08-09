package com.mcp.engine.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TraceDiff - 退化检测")
class TraceDiffTest {

    private static TraceRecord baseline() {
        return TraceRecord.builder()
                .traceId("trace-baseline")
                .userMessage("帮我整理昨天讨论的 Java 项目")
                .renderedPrompt("好的，我来帮你整理昨天的 Java 项目讨论内容。以下是整理结果...")
                .elapsedMs(50)
                .layerCount(5)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("正常对比")
    class NormalComparison {

        @Test
        @DisplayName("diff001: 相同输出应无退化")
        void identicalShouldPass() {
            TraceRecord b = baseline();
            TraceRecord c = TraceRecord.builder()
                    .traceId("trace-current")
                    .userMessage(b.userMessage())
                    .renderedPrompt(b.renderedPrompt())
                    .elapsedMs(b.elapsedMs())
                    .layerCount(b.layerCount())
                    .timestamp(LocalDateTime.now())
                    .build();

            TraceDiff.Result result = TraceDiff.compare(b, c);

            assertThat(result.passed()).isTrue();
            assertThat(result.regressions()).isEmpty();
        }

        @Test
        @DisplayName("diff002: 延迟降低应检测到改善")
        void lowerLatencyShouldBeImprovement() {
            TraceRecord b = baseline();
            TraceRecord c = TraceRecord.builder()
                    .traceId("trace-current")
                    .userMessage(b.userMessage())
                    .renderedPrompt(b.renderedPrompt())
                    .elapsedMs(10)
                    .layerCount(b.layerCount())
                    .timestamp(LocalDateTime.now())
                    .build();

            TraceDiff.Result result = TraceDiff.compare(b, c);

            assertThat(result.passed()).isTrue();
            assertThat(result.improvements()).isNotEmpty();
            assertThat(result.improvements().get(0)).contains("延迟显著降低");
        }

        @Test
        @DisplayName("diff003: 延迟翻倍应检测到退化")
        void doubledLatencyShouldBeRegression() {
            TraceRecord b = baseline();
            TraceRecord c = TraceRecord.builder()
                    .traceId("trace-current")
                    .userMessage(b.userMessage())
                    .renderedPrompt(b.renderedPrompt())
                    .elapsedMs(200)
                    .layerCount(b.layerCount())
                    .timestamp(LocalDateTime.now())
                    .build();

            TraceDiff.Result result = TraceDiff.compare(b, c);

            assertThat(result.passed()).isFalse();
            assertThat(result.regressions()).isNotEmpty();
            assertThat(result.regressions().get(0)).contains("延迟显著增加");
        }

        @Test
        @DisplayName("diff004: 输出为空应检测到严重退化")
        void emptyOutputShouldBeRegression() {
            TraceRecord b = baseline();
            TraceRecord c = TraceRecord.builder()
                    .traceId("trace-current")
                    .userMessage(b.userMessage())
                    .renderedPrompt("")
                    .elapsedMs(b.elapsedMs())
                    .layerCount(b.layerCount())
                    .timestamp(LocalDateTime.now())
                    .build();

            TraceDiff.Result result = TraceDiff.compare(b, c);

            assertThat(result.passed()).isFalse();
            assertThat(result.regressions()).isNotEmpty();
            assertThat(result.regressions().get(0)).contains("System Prompt 为空");
        }

        @Test
        @DisplayName("diff005: 输出显著缩短应检测到退化")
        void significantlyShorterOutputShouldBeRegression() {
            TraceRecord b = baseline();
            TraceRecord c = TraceRecord.builder()
                    .traceId("trace-current")
                    .userMessage(b.userMessage())
                    .renderedPrompt("ok")
                    .elapsedMs(b.elapsedMs())
                    .layerCount(b.layerCount())
                    .timestamp(LocalDateTime.now())
                    .build();

            TraceDiff.Result result = TraceDiff.compare(b, c);

            assertThat(result.passed()).isFalse();
            assertThat(result.regressions()).isNotEmpty();
            assertThat(result.regressions().get(0)).contains("System Prompt 显著缩短");
        }

        @Test
        @DisplayName("diff006: 上下文层数归零应检测到退化")
        void zeroLayerCountShouldBeRegression() {
            TraceRecord b = baseline();
            TraceRecord c = TraceRecord.builder()
                    .traceId("trace-current")
                    .userMessage(b.userMessage())
                    .renderedPrompt(b.renderedPrompt())
                    .elapsedMs(b.elapsedMs())
                    .layerCount(0)
                    .timestamp(LocalDateTime.now())
                    .build();

            TraceDiff.Result result = TraceDiff.compare(b, c);

            assertThat(result.passed()).isFalse();
            assertThat(result.regressions()).isNotEmpty();
            assertThat(result.regressions().get(0)).contains("上下文层数为 0");
        }

        @Test
        @DisplayName("diff007: null 基准应返回失败")
        void nullBaselineShouldFail() {
            TraceRecord c = baseline();

            TraceDiff.Result result = TraceDiff.compare(null, c);

            assertThat(result.passed()).isFalse();
            assertThat(result.summary()).contains("为空");
        }

        @Test
        @DisplayName("diff008: null 当前应返回失败")
        void nullCurrentShouldFail() {
            TraceRecord b = baseline();

            TraceDiff.Result result = TraceDiff.compare(b, null);

            assertThat(result.passed()).isFalse();
            assertThat(result.summary()).contains("为空");
        }
    }

    @Nested
    @DisplayName("多维度综合对比")
    class MultiDimensionComparison {

        @Test
        @DisplayName("diff009: 多项退化应全部检测")
        void multipleRegressionsShouldAllBeDetected() {
            TraceRecord b = TraceRecord.builder()
                    .traceId("baseline")
                    .userMessage("test")
                    .renderedPrompt("完整的输出内容，包含详细的解释和说明")
                    .elapsedMs(50)
                    .layerCount(6)
                    .timestamp(LocalDateTime.now())
                    .build();

            TraceRecord c = TraceRecord.builder()
                    .traceId("current")
                    .userMessage("test")
                    .renderedPrompt("")
                    .elapsedMs(200)
                    .layerCount(0)
                    .timestamp(LocalDateTime.now())
                    .build();

            TraceDiff.Result result = TraceDiff.compare(b, c);

            assertThat(result.passed()).isFalse();
            assertThat(result.regressions()).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("diff010: 多项改善应全部检测")
        void multipleImprovementsShouldAllBeDetected() {
            TraceRecord b = TraceRecord.builder()
                    .traceId("baseline")
                    .userMessage("test")
                    .renderedPrompt("短")
                    .elapsedMs(200)
                    .layerCount(2)
                    .timestamp(LocalDateTime.now())
                    .build();

            TraceRecord c = TraceRecord.builder()
                    .traceId("current")
                    .userMessage("test")
                    .renderedPrompt("非常详细的输出，包含了更多的上下文信息和解释说明")
                    .elapsedMs(10)
                    .layerCount(4)
                    .timestamp(LocalDateTime.now())
                    .build();

            TraceDiff.Result result = TraceDiff.compare(b, c);

            assertThat(result.passed()).isTrue();
            assertThat(result.improvements()).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("LLM 输出对比")
    class LlmOutputComparison {

        @Test
        @DisplayName("diff011: LLM 输出为空应检测退化")
        void emptyLlmOutputShouldBeRegression() {
            TraceRecord b = TraceRecord.builder()
                    .traceId("baseline")
                    .userMessage("test")
                    .llmOutput("原始模型的完整输出")
                    .elapsedMs(50)
                    .layerCount(4)
                    .timestamp(LocalDateTime.now())
                    .build();

            TraceRecord c = TraceRecord.builder()
                    .traceId("current")
                    .userMessage("test")
                    .llmOutput("")
                    .elapsedMs(50)
                    .layerCount(4)
                    .timestamp(LocalDateTime.now())
                    .build();

            TraceDiff.Result result = TraceDiff.compare(b, c);

            assertThat(result.passed()).isFalse();
            assertThat(result.regressions()).isNotEmpty();
            assertThat(result.regressions().get(0)).contains("LLM 输出为空");
        }

        @Test
        @DisplayName("diff012: LLM 输出显著缩短应检测退化")
        void significantlyShorterLlmOutputShouldBeRegression() {
            TraceRecord b = TraceRecord.builder()
                    .traceId("baseline")
                    .userMessage("test")
                    .llmOutput("原始模型给出了非常详细的回答，包含了完整的解释和示例代码")
                    .elapsedMs(50)
                    .layerCount(4)
                    .timestamp(LocalDateTime.now())
                    .build();

            TraceRecord c = TraceRecord.builder()
                    .traceId("current")
                    .userMessage("test")
                    .llmOutput("ok")
                    .elapsedMs(50)
                    .layerCount(4)
                    .timestamp(LocalDateTime.now())
                    .build();

            TraceDiff.Result result = TraceDiff.compare(b, c);

            assertThat(result.passed()).isFalse();
            assertThat(result.regressions()).isNotEmpty();
            assertThat(result.regressions().get(0)).contains("LLM 输出显著缩短");
        }

        @Test
        @DisplayName("diff013: LLM 输出更详细应检测改善")
        void moreDetailedLlmOutputShouldBeImprovement() {
            TraceRecord b = TraceRecord.builder()
                    .traceId("baseline")
                    .userMessage("test")
                    .llmOutput("简短回答")
                    .elapsedMs(50)
                    .layerCount(4)
                    .timestamp(LocalDateTime.now())
                    .build();

            TraceRecord c = TraceRecord.builder()
                    .traceId("current")
                    .userMessage("test")
                    .llmOutput("新模型给出了非常详细的回答，包含了完整的解释、示例代码和最佳实践建议")
                    .elapsedMs(50)
                    .layerCount(4)
                    .timestamp(LocalDateTime.now())
                    .build();

            TraceDiff.Result result = TraceDiff.compare(b, c);

            assertThat(result.passed()).isTrue();
            assertThat(result.improvements()).isNotEmpty();
            assertThat(result.improvements().get(0)).contains("LLM 输出更详细");
        }

        @Test
        @DisplayName("diff014: 有 llmOutput 时优先对比 llmOutput 而非 renderedPrompt")
        void llmOutputTakesPrecedenceOverRenderedPrompt() {
            TraceRecord b = TraceRecord.builder()
                    .traceId("baseline")
                    .userMessage("test")
                    .renderedPrompt("SYSTEM PROMPT A")
                    .llmOutput("原始 LLM 输出")
                    .elapsedMs(50)
                    .layerCount(4)
                    .timestamp(LocalDateTime.now())
                    .build();

            TraceRecord c = TraceRecord.builder()
                    .traceId("current")
                    .userMessage("test")
                    .renderedPrompt("SYSTEM PROMPT B")
                    .llmOutput("")
                    .elapsedMs(50)
                    .layerCount(4)
                    .timestamp(LocalDateTime.now())
                    .build();

            TraceDiff.Result result = TraceDiff.compare(b, c);

            assertThat(result.passed()).isFalse();
            assertThat(result.details()).containsKey("baselineLlmOutputLength");
            assertThat(result.regressions().get(0)).contains("LLM 输出为空");
        }
    }
}