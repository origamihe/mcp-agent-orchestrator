package com.mcp.engine.reflection;

import com.mcp.llm.client.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskEvaluatorTest {

    @Mock
    private LlmClient llmClient;

    private TaskEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new TaskEvaluator(llmClient);
    }

    @Test
    @DisplayName("简单闲聊应跳过评估")
    void shouldSkipSimpleChat() {
        Mono<TaskEvaluator.TaskEvaluation> result = evaluator.evaluate("你好", "", "");
        StepVerifier.create(result)
                .assertNext(e -> {
                    assertThat(e.totalScore()).isEqualTo(0);
                    assertThat(e.isWorthLearning()).isFalse();
                    assertThat(e.learningType()).isEqualTo("NONE");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("null 请求应跳过评估")
    void shouldSkipNullRequest() {
        Mono<TaskEvaluator.TaskEvaluation> result = evaluator.evaluate(null, "", "");
        StepVerifier.create(result)
                .assertNext(e -> assertThat(e.isWorthLearning()).isFalse())
                .verifyComplete();
    }

    @Test
    @DisplayName("成功执行且 LLM 返回 SKILL 类型")
    void shouldReturnSkillWhenLlmSaysSkill() {
        String llmResponse = """
                {
                    "completionScore": 22,
                    "toolCorrectnessScore": 23,
                    "efficiencyScore": 20,
                    "reusabilityScore": 22,
                    "totalScore": 87,
                    "isSuccess": true,
                    "isWorthLearning": true,
                    "learningType": "SKILL",
                    "summary": "任务圆满完成，使用了正确的工具",
                    "failureReason": ""
                }
                """;
        when(llmClient.generate(anyString())).thenReturn(Mono.just(llmResponse));

        Mono<TaskEvaluator.TaskEvaluation> result = evaluator.evaluate(
                "帮我搜索所有 Java 文件", "使用了 search 工具", "search");

        StepVerifier.create(result)
                .assertNext(e -> {
                    assertThat(e.totalScore()).isEqualTo(87);
                    assertThat(e.isSuccess()).isTrue();
                    assertThat(e.isWorthLearning()).isTrue();
                    assertThat(e.learningType()).isEqualTo("SKILL");
                    assertThat(e.shouldGenerateSkill()).isTrue();
                    assertThat(e.shouldRecordFailure()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("执行失败时 LLM 返回 FAILURE 类型")
    void shouldReturnFailureWhenLlmSaysFailure() {
        String llmResponse = """
                {
                    "completionScore": 5,
                    "toolCorrectnessScore": 10,
                    "efficiencyScore": 5,
                    "reusabilityScore": 8,
                    "totalScore": 28,
                    "isSuccess": false,
                    "isWorthLearning": true,
                    "learningType": "FAILURE",
                    "summary": "文件未找到，工具选择错误",
                    "failureReason": "FileNotFoundException"
                }
                """;
        when(llmClient.generate(anyString())).thenReturn(Mono.just(llmResponse));

        Mono<TaskEvaluator.TaskEvaluation> result = evaluator.evaluate(
                "帮我读取不存在的文件", "read_file 失败", "read_file");

        StepVerifier.create(result)
                .assertNext(e -> {
                    assertThat(e.totalScore()).isEqualTo(28);
                    assertThat(e.isSuccess()).isFalse();
                    assertThat(e.isWorthLearning()).isTrue();
                    assertThat(e.learningType()).isEqualTo("FAILURE");
                    assertThat(e.shouldRecordFailure()).isTrue();
                    assertThat(e.shouldGenerateSkill()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("BOTH 类型同时生成 Skill 和 Failure")
    void shouldHandleBothType() {
        String llmResponse = """
                {
                    "completionScore": 15,
                    "toolCorrectnessScore": 20,
                    "efficiencyScore": 18,
                    "reusabilityScore": 20,
                    "totalScore": 73,
                    "isSuccess": true,
                    "isWorthLearning": true,
                    "learningType": "BOTH",
                    "summary": "虽然成功了但有可优化的地方",
                    "failureReason": "中间步骤有错误"
                }
                """;
        when(llmClient.generate(anyString())).thenReturn(Mono.just(llmResponse));

        Mono<TaskEvaluator.TaskEvaluation> result = evaluator.evaluate(
                "帮我重构代码", "部分成功", "read_file, search, replace");

        StepVerifier.create(result)
                .assertNext(e -> {
                    assertThat(e.learningType()).isEqualTo("BOTH");
                    assertThat(e.shouldGenerateSkill()).isTrue();
                    assertThat(e.shouldRecordFailure()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("无需学习的结果")
    void shouldReturnNONEWhenNotWorthLearning() {
        String llmResponse = """
                {
                    "completionScore": 15,
                    "toolCorrectnessScore": 15,
                    "efficiencyScore": 15,
                    "reusabilityScore": 15,
                    "totalScore": 60,
                    "isSuccess": true,
                    "isWorthLearning": false,
                    "learningType": "NONE",
                    "summary": "普通对话，无需学习",
                    "failureReason": ""
                }
                """;
        when(llmClient.generate(anyString())).thenReturn(Mono.just(llmResponse));

        Mono<TaskEvaluator.TaskEvaluation> result = evaluator.evaluate(
                "今天天气怎么样", "直接回答", "无");

        StepVerifier.create(result)
                .assertNext(e -> {
                    assertThat(e.isWorthLearning()).isFalse();
                    assertThat(e.learningType()).isEqualTo("NONE");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("LLM 返回异常时跳过评估")
    void shouldSkipOnLlmError() {
        when(llmClient.generate(anyString())).thenReturn(Mono.error(new RuntimeException("LLM timeout")));

        Mono<TaskEvaluator.TaskEvaluation> result = evaluator.evaluate(
                "帮我搜索文件", "执行中", "search");

        StepVerifier.create(result)
                .assertNext(e -> {
                    assertThat(e.totalScore()).isEqualTo(0);
                    assertThat(e.isWorthLearning()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("JSON 被 markdown 代码块包裹也能正确解析")
    void shouldParseJsonWithMarkdownWrapper() {
        String llmResponse = """
                ```json
                {
                    "completionScore": 25,
                    "toolCorrectnessScore": 25,
                    "efficiencyScore": 25,
                    "reusabilityScore": 25,
                    "totalScore": 100,
                    "isSuccess": true,
                    "isWorthLearning": true,
                    "learningType": "SKILL",
                    "summary": "完美执行",
                    "failureReason": ""
                }
                ```
                """;
        when(llmClient.generate(anyString())).thenReturn(Mono.just(llmResponse));

        Mono<TaskEvaluator.TaskEvaluation> result = evaluator.evaluate(
                "帮我写一个完美的代码", "成功", "write_file");

        StepVerifier.create(result)
                .assertNext(e -> {
                    assertThat(e.totalScore()).isEqualTo(100);
                    assertThat(e.isSuccess()).isTrue();
                })
                .verifyComplete();
    }
}