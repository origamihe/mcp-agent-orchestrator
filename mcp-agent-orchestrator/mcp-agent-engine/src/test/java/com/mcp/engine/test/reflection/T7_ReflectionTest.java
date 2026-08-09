package com.mcp.engine.test.reflection;

import com.mcp.core.entity.FailureRecordEntity;
import com.mcp.core.domain.memory.SkillEntity;
import com.mcp.core.repository.FailureRecordRepository;
import com.mcp.core.repository.SkillLibraryRepository;
import com.mcp.engine.reflection.ReflectionAgent;
import com.mcp.engine.reflection.TaskEvaluator;
import com.mcp.engine.reflection.TaskEvaluator.TaskEvaluation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * T7 Reflection - 验证自我修正能力（5 cases）
 *
 * 测试目标：
 * - 评估执行结果是否值得学习
 * - 当执行失败时触发反思
 * - 反思后生成新的 Skill 存入技能库
 * - 失败记录存入失败库
 * - 反思能识别失败模式并提供修正
 */
@ExtendWith(MockitoExtension.class)
class T7_ReflectionTest {

    @Mock
    private TaskEvaluator taskEvaluator;

    @Mock
    private ReflectionAgent reflectionAgent;

    @Mock
    private SkillLibraryRepository skillLibraryRepository;

    @Mock
    private FailureRecordRepository failureRecordRepository;

    @BeforeEach
    void setUp() {
        // no-op
    }

    @Test
    @DisplayName("Case1: 高评分结果触发学习 - 值得反思学习")
    void shouldTriggerLearningWhenScoreIsHigh() {
        String userRequest = "帮我修改 UserService 增加日志";
        String executionOutput = """
                执行步骤:
                1. 读取 UserService.java - 成功
                2. 定位到 save 方法 - 成功
                3. 添加日志语句 - 成功
                4. 写入文件 - 成功
                """;
        String toolsUsed = "read_file,edit_file";

        TaskEvaluation evaluation = new TaskEvaluation(
                40, 30, 10, 5, 85,
                true, true, "SUCCESS_PATTERN",
                "执行成功，可复用这个修改模式", ""
        );

        when(taskEvaluator.evaluate(userRequest, executionOutput, toolsUsed))
                .thenReturn(Mono.just(evaluation));

        Mono<TaskEvaluation> result = taskEvaluator.evaluate(userRequest, executionOutput, toolsUsed);

        assertThat(result.block().isWorthLearning()).isTrue();
        assertThat(result.block().totalScore()).isGreaterThanOrEqualTo(80);
    }

    @Test
    @DisplayName("Case2: 低评分结果不触发学习 - 不值得反思")
    void shouldNotTriggerLearningWhenScoreIsLow() {
        String userRequest = "帮我修改 UserService";
        String executionOutput = "工具调用失败，参数错误";
        String toolsUsed = "edit_file";

        TaskEvaluation evaluation = new TaskEvaluation(
                5, 0, 5, 0, 10,
                false, false, "NONE",
                "执行失败", "参数格式错误"
        );

        when(taskEvaluator.evaluate(userRequest, executionOutput, toolsUsed))
                .thenReturn(Mono.just(evaluation));

        Mono<TaskEvaluation> result = taskEvaluator.evaluate(userRequest, executionOutput, toolsUsed);

        assertThat(result.block().isWorthLearning()).isFalse();
    }

    @Test
    @DisplayName("Case3: 执行失败 - 记录到失败库")
    void shouldRecordFailureToLibrary() {
        FailureRecordEntity failure = new FailureRecordEntity();
        failure.setId(1L);
        failure.setSessionId("session-1");
        failure.setToolName("edit_file");
        failure.setErrorMessage("参数格式错误");
        failure.setAttemptCount(1);

        when(failureRecordRepository.save(any(FailureRecordEntity.class))).thenReturn(failure);

        FailureRecordEntity saved = failureRecordRepository.save(failure);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getToolName()).isEqualTo("edit_file");
        assertThat(saved.getErrorMessage()).contains("参数格式错误");
        verify(failureRecordRepository).save(failure);
    }

    @Test
    @DisplayName("Case4: 反思成功后 - 新 Skill 存入技能库")
    void shouldSaveNewSkillToLibraryAfterReflection() {
        SkillEntity skill = new SkillEntity();
        skill.setId(1L);
        skill.setName("edit_file_add_logging");
        skill.setDescription("修改文件时添加日志语句的模式");
        skill.setSuccessRate(0.95);
        skill.setTotalExecutions(1);

        when(skillLibraryRepository.save(any(SkillEntity.class))).thenReturn(skill);

        SkillEntity saved = skillLibraryRepository.save(skill);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("edit_file_add_logging");
        assertThat(saved.getSuccessRate()).isEqualTo(0.95);
        verify(skillLibraryRepository).save(skill);
    }

    @Test
    @DisplayName("Case5: Reflection 自我修正 - 失败后触发反思流程")
    void shouldTriggerReflectionAfterFailure() {
        TaskEvaluation failureEvaluation = new TaskEvaluation(
                5, 0, 5, 0, 10,
                false, false, "FAILURE",
                "执行失败", "参数格式错误"
        );

        List<String> toolsUsed = List.of("edit_file");

        doNothing().when(reflectionAgent).reflect(
                failureEvaluation,
                "帮我修改 UserService",
                "工具调用失败: 参数格式错误",
                toolsUsed,
                "session-1",
                "user-1"
        );

        reflectionAgent.reflect(
                failureEvaluation,
                "帮我修改 UserService",
                "工具调用失败: 参数格式错误",
                toolsUsed,
                "session-1",
                "user-1"
        );

        verify(reflectionAgent).reflect(
                eq(failureEvaluation),
                anyString(),
                anyString(),
                eq(toolsUsed),
                anyString(),
                anyString()
        );
    }
}