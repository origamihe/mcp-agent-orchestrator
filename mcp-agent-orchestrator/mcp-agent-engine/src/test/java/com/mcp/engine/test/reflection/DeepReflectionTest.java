package com.mcp.engine.test.reflection;

import com.mcp.core.domain.memory.FailureEntity;
import com.mcp.core.domain.memory.SkillEntity;
import com.mcp.core.entity.FailureRecordEntity;
import com.mcp.core.repository.FailureRecordRepository;
import com.mcp.core.repository.SkillLibraryRepository;
import com.mcp.engine.reflection.LearningBudgetManager;
import com.mcp.engine.reflection.ReflectionAgent;
import com.mcp.engine.reflection.TaskEvaluator;
import com.mcp.engine.reflection.TaskEvaluator.TaskEvaluation;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("T12 Deep Reflection - 深度反思测试")
class DeepReflectionTest {

    @Mock
    private TaskEvaluator taskEvaluator;

    @Mock
    private ReflectionAgent reflectionAgent;

    @Mock
    private SkillLibraryRepository skillLibraryRepository;

    @Mock
    private FailureRecordRepository failureRecordRepository;

    private LearningBudgetManager budgetManager;

    @BeforeEach
    void setUp() {
        budgetManager = new LearningBudgetManager();
    }

    @Nested
    @DisplayName("Critic → Planner → Executor → Judge → Retry 完整链路")
    class FullReflectionCycle {

        @Test
        @DisplayName("Case1: Critic 评估失败 → Planner 规划修正 → Executor 重试 → Judge 判定通过")
        void shouldCompleteFullReflectionCycleWithSuccess() {
            TaskEvaluation criticEval = new TaskEvaluation(
                    5, 0, 5, 0, 10,
                    false, false, "FAILURE",
                    "执行失败", "参数格式错误"
            );

            assertThat(criticEval.totalScore()).isLessThan(80);
            assertThat(criticEval.isWorthLearning()).isFalse();

            doNothing().when(reflectionAgent).reflect(
                    criticEval, "修复 UserService", "失败: 参数格式错误",
                    List.of("edit_file"), "session-1", "user-1");

            reflectionAgent.reflect(criticEval, "修复 UserService",
                    "失败: 参数格式错误", List.of("edit_file"),
                    "session-1", "user-1");

            verify(reflectionAgent).reflect(
                    eq(criticEval), anyString(), anyString(), anyList(), anyString(), anyString());
        }

        @Test
        @DisplayName("Case2: Judge 判定通过 → 生成 Skill 存入技能库")
        void shouldSaveSkillWhenJudgeApproves() {
            SkillEntity skill = new SkillEntity();
            skill.setId(1L);
            skill.setName("edit_file_param_fix");
            skill.setDescription("修正 edit_file 参数格式错误");
            skill.setSuccessRate(0.95);
            skill.setTotalExecutions(1);

            when(skillLibraryRepository.save(any(SkillEntity.class))).thenReturn(skill);

            SkillEntity saved = skillLibraryRepository.save(skill);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getName()).isEqualTo("edit_file_param_fix");
            assertThat(saved.getSuccessRate()).isGreaterThan(0.9);
            verify(skillLibraryRepository).save(skill);
        }

        @Test
        @DisplayName("Case3: Judge 判定失败 → 记录到 FailureLibrary")
        void shouldRecordToFailureLibraryWhenJudgeRejects() {
            FailureRecordEntity failure = new FailureRecordEntity();
            failure.setId(1L);
            failure.setSessionId("session-1");
            failure.setToolName("search_file");
            failure.setErrorMessage("查询超时");
            failure.setAttemptCount(1);

            when(failureRecordRepository.save(any(FailureRecordEntity.class))).thenReturn(failure);

            FailureRecordEntity saved = failureRecordRepository.save(failure);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getToolName()).isEqualTo("search_file");
            verify(failureRecordRepository).save(failure);
        }

        @Test
        @DisplayName("Case4: 达到最大重试次数 → 放弃并输出当前结果")
        void shouldAbortWhenMaxRetryReached() {
            String sessionId = "session-max";
            for (int i = 0; i < 3; i++) {
                budgetManager.recordReflection(sessionId);
            }

            assertThat(budgetManager.shouldReflect(sessionId, "修复代码")).isFalse();
        }

        @Test
        @DisplayName("Case5: 连续失败学习 — 失败模式被正确归类")
        void shouldClassifyFailurePatternsCorrectly() {
            TaskEvaluation eval1 = new TaskEvaluation(
                    5, 0, 5, 0, 10,
                    false, false, "FAILURE",
                    "工具调用失败", "参数格式错误"
            );

            TaskEvaluation eval2 = new TaskEvaluation(
                    10, 0, 5, 0, 15,
                    false, false, "FAILURE",
                    "文件读取失败", "文件不存在"
            );

            TaskEvaluation eval3 = new TaskEvaluation(
                    30, 20, 10, 0, 60,
                    true, false, "PARTIAL_SUCCESS",
                    "部分成功", "部分文件未找到"
            );

            assertThat(eval1.failureReason()).contains("参数格式错误");
            assertThat(eval2.failureReason()).contains("文件不存在");
            assertThat(eval3.learningType()).isEqualTo("PARTIAL_SUCCESS");
            assertThat(eval3.isWorthLearning()).isFalse();
        }
    }

    @Nested
    @DisplayName("Reflection 评分细化")
    class ReflectionScoring {

        @Test
        @DisplayName("Case6: 成功执行 + 新工具组合 → 高分 + 值得学习")
        void shouldScoreHighForNovelToolCombination() {
            TaskEvaluation evaluation = new TaskEvaluation(
                    40, 30, 15, 5, 90,
                    true, true, "SUCCESS_PATTERN",
                    "新颖工具组合成功", ""
            );

            assertThat(evaluation.totalScore()).isEqualTo(90);
            assertThat(evaluation.isWorthLearning()).isTrue();
            assertThat(evaluation.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Case7: 成功但常规模式 → 中等分 + 不值得学习")
        void shouldNotLearnFromRoutineSuccess() {
            TaskEvaluation evaluation = new TaskEvaluation(
                    20, 10, 5, 0, 35,
                    true, false, "NONE",
                    "常规操作成功", ""
            );

            assertThat(evaluation.totalScore()).isLessThan(80);
            assertThat(evaluation.isWorthLearning()).isFalse();
        }

        @Test
        @DisplayName("Case8: 失败但接近成功 → 中低分 + 值得学习")
        void shouldLearnFromNearSuccessFailure() {
            TaskEvaluation evaluation = new TaskEvaluation(
                    15, 5, 10, 5, 35,
                    false, true, "FAILURE",
                    "几乎成功但最后一步失败", "最后一步参数错误"
            );

            assertThat(evaluation.isSuccess()).isFalse();
            assertThat(evaluation.learningType()).isEqualTo("FAILURE");
        }

        @Test
        @DisplayName("Case9: 严重失败 → 低分 + 不值得学习")
        void shouldNotLearnFromCatastrophicFailure() {
            TaskEvaluation evaluation = new TaskEvaluation(
                    0, 0, 0, 0, 0,
                    false, false, "NONE",
                    "完全无法执行", "系统异常"
            );

            assertThat(evaluation.totalScore()).isEqualTo(0);
            assertThat(evaluation.isWorthLearning()).isFalse();
        }
    }

    @Nested
    @DisplayName("LearningBudget 管理")
    class LearningBudgetManagement {

        @Test
        @DisplayName("Case10: 正常任务允许 Reflection")
        void shouldAllowReflectionForNormalTask() {
            assertThat(budgetManager.shouldReflect("session-1", "帮我搜索 Java 文件")).isTrue();
        }

        @Test
        @DisplayName("Case11: 闲聊被过滤")
        void shouldFilterChitchat() {
            assertThat(budgetManager.shouldReflect("session-1", "你好")).isFalse();
            assertThat(budgetManager.shouldReflect("session-1", "谢谢")).isFalse();
            assertThat(budgetManager.shouldReflect("session-1", "好的")).isFalse();
        }

        @Test
        @DisplayName("Case12: 每会话最多 3 次 Reflection")
        void shouldLimitToMaxReflectionsPerSession() {
            String sessionId = "session-max";
            for (int i = 0; i < 3; i++) {
                budgetManager.recordReflection(sessionId);
            }
            assertThat(budgetManager.shouldReflect(sessionId, "帮我修复代码")).isFalse();
        }
    }
}