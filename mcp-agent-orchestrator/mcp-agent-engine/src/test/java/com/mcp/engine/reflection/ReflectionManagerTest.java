package com.mcp.engine.reflection;

import com.mcp.common.reflection.ReflectionContext;
import com.mcp.core.domain.memory.FailureEntity;
import com.mcp.core.domain.memory.ReflectionLogEntity;
import com.mcp.core.domain.memory.SkillEntity;
import com.mcp.core.repository.FailureLibraryRepository;
import com.mcp.core.repository.ReflectionLogRepository;
import com.mcp.core.repository.SkillLibraryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * P7 验证 — ReflectionManager / ReflectionContext 测试。
 * 验证：
 * 1. ReflectionContext 模型与 Prompt 片段生成
 * 2. ReflectionManager 反思上下文构建
 * 3. ReflectionManager 统计功能
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P7 — Reflection 重构")
class ReflectionManagerTest {

    @Mock
    private SkillLibraryRepository skillLibraryRepository;
    @Mock
    private FailureLibraryRepository failureLibraryRepository;
    @Mock
    private ReflectionLogRepository reflectionLogRepository;

    private ReflectionManager reflectionManager;

    @BeforeEach
    void setUp() {
        reflectionManager = new ReflectionManager(null, null, null, null,
                reflectionLogRepository, failureLibraryRepository, skillLibraryRepository);
    }

    // ==================== ReflectionContext 模型 ====================

    @Nested
    @DisplayName("ReflectionContext 模型")
    class ReflectionContextModel {

        @Test
        @DisplayName("空上下文应返回空字符串")
        void shouldReturnEmptyForEmptyContext() {
            ReflectionContext ctx = ReflectionContext.builder().build();

            assertThat(ctx.isEmpty()).isTrue();
            assertThat(ctx.toPromptFragment()).isEmpty();
        }

        @Test
        @DisplayName("应生成正确的 Prompt 片段")
        void shouldGeneratePromptFragment() {
            ReflectionContext ctx = ReflectionContext.builder()
                    .relevantSkills(List.of(
                            ReflectionContext.ReflectionEntry.of(1L, "搜索新闻", "使用 web_search 工具搜索新闻", "SKILL")
                    ))
                    .relevantFailures(List.of(
                            failEntry(2L, "PDF 读取", "二进制文件读取失败", "使用 PDFBox")
                    ))
                    .recentReflections(List.of(
                            ReflectionContext.ReflectionEntry.of(3L, "SAVED", "上次任务成功提炼了技能", "REFLECTION")
                    ))
                    .totalReflections(5)
                    .build();

            assertThat(ctx.isEmpty()).isFalse();
            String fragment = ctx.toPromptFragment();
            assertThat(fragment).contains("可用技能");
            assertThat(fragment).contains("搜索新闻");
            assertThat(fragment).contains("已知问题模式");
            assertThat(fragment).contains("PDF 读取");
            assertThat(fragment).contains("使用 PDFBox");
            assertThat(fragment).contains("最近反思");
            assertThat(fragment).contains("上次任务成功提炼了技能");
        }

        @Test
        @DisplayName("ReflectionEntry 应正确构建")
        void shouldBuildReflectionEntry() {
            ReflectionContext.ReflectionEntry entry = ReflectionContext.ReflectionEntry.of(
                    1L, "测试技能", "描述", "SKILL");

            assertThat(entry.getId()).isEqualTo(1L);
            assertThat(entry.getName()).isEqualTo("测试技能");
            assertThat(entry.getDescription()).isEqualTo("描述");
            assertThat(entry.getType()).isEqualTo("SKILL");
        }
    }

    // ==================== ReflectionManager ====================

    @Nested
    @DisplayName("ReflectionManager")
    class ReflectionManagerTests {

        @Test
        @DisplayName("应构建反思上下文")
        void shouldBuildReflectionContext() {
            SkillEntity skill = new SkillEntity();
            skill.setId(1L);
            skill.setName("搜索新闻");
            skill.setDescription("使用 web_search 工具搜索新闻");

            FailureEntity failure = new FailureEntity();
            failure.setId(2L);
            failure.setTaskPattern("PDF 读取");
            failure.setErrorSignature("二进制读取失败");
            failure.setRootCause("使用了错误的读取方式");
            failure.setCorrectApproach("使用 PDFBox");
            failure.setOccurrenceCount(3);

            ReflectionLogEntity log = new ReflectionLogEntity();
            log.setId(3L);
            log.setReflection("已保存技能");

            when(skillLibraryRepository.findByIsActiveTrueOrderBySuccessRateDesc())
                    .thenReturn(List.of(skill));
            when(failureLibraryRepository.findByIsResolvedFalseOrderByOccurrenceCountDesc())
                    .thenReturn(List.of(failure));
            when(reflectionLogRepository.findByUserIdOrderByCreatedAtDesc("user1"))
                    .thenReturn(List.of(log));

            ReflectionContext ctx = reflectionManager.buildReflectionContext("user1", null);

            assertThat(ctx.getRelevantSkills()).hasSize(1);
            assertThat(ctx.getRelevantSkills().get(0).getName()).isEqualTo("搜索新闻");
            assertThat(ctx.getRelevantFailures()).hasSize(1);
            assertThat(ctx.getRelevantFailures().get(0).getCorrectApproach()).isEqualTo("使用 PDFBox");
            assertThat(ctx.getRecentReflections()).hasSize(1);
        }

        @Test
        @DisplayName("无数据时应返回空上下文")
        void shouldReturnEmptyContextWhenNoData() {
            when(skillLibraryRepository.findByIsActiveTrueOrderBySuccessRateDesc())
                    .thenReturn(List.of());
            when(failureLibraryRepository.findByIsResolvedFalseOrderByOccurrenceCountDesc())
                    .thenReturn(List.of());
            when(reflectionLogRepository.findByUserIdOrderByCreatedAtDesc("user1"))
                    .thenReturn(List.of());

            ReflectionContext ctx = reflectionManager.buildReflectionContext("user1", null);

            assertThat(ctx.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("应正确统计反思数据")
        void shouldReturnCorrectStats() {
            SkillEntity skill = new SkillEntity();
            skill.setId(1L);

            FailureEntity unresolved = new FailureEntity();
            unresolved.setId(2L);
            unresolved.setResolved(false);

            FailureEntity resolved = new FailureEntity();
            resolved.setId(3L);
            resolved.setResolved(true);

            when(skillLibraryRepository.findByIsActiveTrueOrderBySuccessRateDesc())
                    .thenReturn(List.of(skill));
            when(failureLibraryRepository.findByIsResolvedFalseOrderByOccurrenceCountDesc())
                    .thenReturn(List.of(unresolved));
            when(failureLibraryRepository.findAll())
                    .thenReturn(List.of(unresolved, resolved));
            when(reflectionLogRepository.count()).thenReturn(10L);

            ReflectionManager.ReflectionStats stats = reflectionManager.getStats();

            assertThat(stats.totalSkills()).isEqualTo(1);
            assertThat(stats.unresolvedFailures()).isEqualTo(1);
            assertThat(stats.resolvedFailures()).isEqualTo(1);
            assertThat(stats.totalReflections()).isEqualTo(10);
            assertThat(stats.resolutionRate()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("解析率为 0 时不应除零")
        void shouldHandleZeroResolutionRate() {
            when(skillLibraryRepository.findByIsActiveTrueOrderBySuccessRateDesc())
                    .thenReturn(List.of());
            when(failureLibraryRepository.findByIsResolvedFalseOrderByOccurrenceCountDesc())
                    .thenReturn(List.of());
            when(failureLibraryRepository.findAll())
                    .thenReturn(List.of());
            when(reflectionLogRepository.count()).thenReturn(0L);

            ReflectionManager.ReflectionStats stats = reflectionManager.getStats();

            assertThat(stats.resolutionRate()).isEqualTo(0.0);
        }
    }

    private ReflectionContext.ReflectionEntry failEntry(Long id, String name,
                                                         String description, String correctApproach) {
        ReflectionContext.ReflectionEntry e = ReflectionContext.ReflectionEntry.of(
                id, name, description, "FAILURE");
        e.setCorrectApproach(correctApproach);
        return e;
    }
}