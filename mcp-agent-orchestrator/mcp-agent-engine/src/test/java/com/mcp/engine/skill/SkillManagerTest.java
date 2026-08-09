package com.mcp.engine.skill;

import com.mcp.common.skill.SkillContext;
import com.mcp.core.domain.memory.SkillEntity;
import com.mcp.core.repository.SkillLibraryRepository;
import com.mcp.engine.reflection.SkillLibraryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * P8 验证 — SkillManager / SkillContext 测试。
 * 验证：
 * 1. SkillContext 模型与 Prompt 片段生成
 * 2. SkillManager 技能上下文构建
 * 3. SkillManager 统计功能
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P8 — Skill Evolution")
class SkillManagerTest {

    @Mock
    private SkillLibraryService skillLibraryService;
    @Mock
    private SkillLibraryRepository skillLibraryRepository;

    private SkillManager skillManager;

    @BeforeEach
    void setUp() {
        skillManager = new SkillManager(skillLibraryService, null, null, skillLibraryRepository);
    }

    // ==================== SkillContext 模型 ====================

    @Nested
    @DisplayName("SkillContext 模型")
    class SkillContextModel {

        @Test
        @DisplayName("空上下文应返回空字符串")
        void shouldReturnEmptyForEmptyContext() {
            SkillContext ctx = SkillContext.builder().build();

            assertThat(ctx.isEmpty()).isTrue();
            assertThat(ctx.toPromptFragment()).isEmpty();
        }

        @Test
        @DisplayName("应生成正确的 Prompt 片段")
        void shouldGeneratePromptFragment() {
            SkillContext.SkillEntry s1 = SkillContext.SkillEntry.of(
                    1L, "搜索新闻", "使用 web_search 工具搜索最新新闻", 85.0, 2);
            s1.setSteps("web_search → extract → format");

            SkillContext.SkillEntry s2 = SkillContext.SkillEntry.of(
                    2L, "生成报告", "根据搜索结果生成 Markdown 报告", 70.0, 1);

            SkillContext ctx = SkillContext.builder()
                    .matchedSkills(List.of(s1))
                    .relatedSkills(List.of(s2))
                    .totalActiveSkills(5)
                    .build();

            assertThat(ctx.isEmpty()).isFalse();
            String fragment = ctx.toPromptFragment();
            assertThat(fragment).contains("可复用技能");
            assertThat(fragment).contains("搜索新闻");
            assertThat(fragment).contains("85%");
            assertThat(fragment).contains("关联技能推荐");
            assertThat(fragment).contains("生成报告");
            assertThat(fragment).contains("70%");
        }

        @Test
        @DisplayName("SkillEntry 应正确构建")
        void shouldBuildSkillEntry() {
            SkillContext.SkillEntry entry = SkillContext.SkillEntry.of(
                    1L, "测试技能", "描述", 80.0, 3);

            assertThat(entry.getId()).isEqualTo(1L);
            assertThat(entry.getName()).isEqualTo("测试技能");
            assertThat(entry.getDescription()).isEqualTo("描述");
            assertThat(entry.getSuccessRate()).isEqualTo(80.0);
            assertThat(entry.getVersion()).isEqualTo(3);
        }
    }

    // ==================== SkillManager ====================

    @Nested
    @DisplayName("SkillManager")
    class SkillManagerTests {

        @Test
        @DisplayName("应构建技能上下文")
        void shouldBuildSkillContext() {
            SkillEntity skill = new SkillEntity();
            skill.setId(1L);
            skill.setName("搜索新闻");
            skill.setDescription("使用 web_search 工具");
            skill.setSteps("web_search → extract");
            skill.setSuccessRate(85.0);
            skill.setVersion(2);

            when(skillLibraryService.retrieveRelevantSkills(anyString()))
                    .thenReturn(List.of(skill));
            when(skillLibraryService.getHighSuccessSkills())
                    .thenReturn(List.of(skill));
            when(skillLibraryRepository.findByIsActiveTrueOrderBySuccessRateDesc())
                    .thenReturn(List.of(skill));

            SkillContext ctx = skillManager.buildSkillContext("搜索新闻");

            assertThat(ctx.getMatchedSkills()).hasSize(1);
            assertThat(ctx.getMatchedSkills().get(0).getName()).isEqualTo("搜索新闻");
            assertThat(ctx.getHighSuccessSkills()).hasSize(1);
            assertThat(ctx.getTotalActiveSkills()).isEqualTo(1);
        }

        @Test
        @DisplayName("无匹配时应返回空上下文")
        void shouldReturnEmptyContextWhenNoMatch() {
            when(skillLibraryService.retrieveRelevantSkills(anyString()))
                    .thenReturn(List.of());
            when(skillLibraryService.getHighSuccessSkills())
                    .thenReturn(List.of());
            when(skillLibraryRepository.findByIsActiveTrueOrderBySuccessRateDesc())
                    .thenReturn(List.of());

            SkillContext ctx = skillManager.buildSkillContext("未知查询");

            assertThat(ctx.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("应正确统计技能数据")
        void shouldReturnCorrectStats() {
            SkillEntity active = new SkillEntity();
            active.setId(1L);
            active.setActive(true);
            active.setSuccessRate(90.0);
            active.setTotalExecutions(100);
            active.setEvolvedFromId(5L);

            SkillEntity inactive = new SkillEntity();
            inactive.setId(2L);
            inactive.setActive(false);

            when(skillLibraryRepository.findAll())
                    .thenReturn(List.of(active, inactive));

            SkillManager.SkillStats stats = skillManager.getStats();

            assertThat(stats.activeSkills()).isEqualTo(1);
            assertThat(stats.inactiveSkills()).isEqualTo(1);
            assertThat(stats.evolvedSkills()).isEqualTo(1);
            assertThat(stats.avgSuccessRate()).isEqualTo(90.0);
            assertThat(stats.totalExecutions()).isEqualTo(100);
            assertThat(stats.evolutionRate()).isEqualTo(50.0);
        }
    }
}