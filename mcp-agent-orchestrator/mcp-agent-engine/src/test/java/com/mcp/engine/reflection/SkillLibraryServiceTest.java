package com.mcp.engine.reflection;

import com.mcp.core.domain.memory.SkillEntity;
import com.mcp.core.repository.SkillLibraryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillLibraryServiceTest {

    @Mock
    private SkillLibraryRepository repository;

    private SkillLibraryService service;

    @BeforeEach
    void setUp() {
        service = new SkillLibraryService(repository, null);
    }

    @Test
    @DisplayName("创建新 Skill")
    void shouldCreateNewSkill() {
        SkillEntity entity = buildSkill("搜索文件", "触发词:搜索,查找", "步骤1: search");
        when(repository.save(any())).thenReturn(entity);

        SkillEntity result = service.create(entity);
        assertThat(result.getName()).isEqualTo("搜索文件");
        assertThat(result.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("createOrUpdate: 已有 Skill 且成功率低 → 进化")
    void shouldEvolveWhenSuccessRateLow() {
        SkillEntity existing = buildSkill("搜索文件", "搜索", "旧步骤");
        existing.setId(1L);
        existing.setSuccessRate(50.0);

        when(repository.findActiveByName("搜索文件")).thenReturn(List.of(existing));
        when(repository.deactivate(existing.getId())).thenReturn(1);

        SkillEntity evolved = buildSkill("搜索文件 v2", "搜索", "新步骤");
        evolved.setId(2L);
        evolved.setVersion(2);
        when(repository.save(any())).thenReturn(evolved);

        SkillEntity result = service.createOrUpdate("搜索文件", "描述", "搜索", "新步骤", "降级步骤");
        assertThat(result.getVersion()).isEqualTo(2);
        verify(repository).deactivate(1L);
    }

    @Test
    @DisplayName("根据触发词检索 Skill")
    void shouldRetrieveSkillsByTrigger() {
        SkillEntity skill1 = buildSkill("搜索文件", "搜索,查找", "步骤");
        skill1.setId(1L);
        skill1.setSuccessRate(90.0);
        SkillEntity skill2 = buildSkill("重构代码", "重构,优化", "步骤");
        skill2.setId(2L);
        skill2.setSuccessRate(70.0);

        when(repository.findByIsActiveTrueOrderBySuccessRateDesc())
                .thenReturn(List.of(skill1, skill2));

        List<SkillEntity> result = service.retrieveRelevantSkills("帮我搜索所有 Java 文件");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("搜索文件");
    }

    @Test
    @DisplayName("没有匹配的触发词时返回空列表")
    void shouldReturnEmptyWhenNoTriggerMatch() {
        SkillEntity skill = buildSkill("搜索文件", "搜索,查找", "步骤");
        skill.setId(1L);
        when(repository.findByIsActiveTrueOrderBySuccessRateDesc()).thenReturn(List.of(skill));

        List<SkillEntity> result = service.retrieveRelevantSkills("帮我写代码");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 查询返回空列表")
    void shouldReturnEmptyForNullQuery() {
        assertThat(service.retrieveRelevantSkills(null)).isEmpty();
        assertThat(service.retrieveRelevantSkills("")).isEmpty();
    }

    @Test
    @DisplayName("buildSkillPrompt 生成正确的 Prompt 文本")
    void shouldBuildSkillPrompt() {
        SkillEntity skill = buildSkill("搜索文件", "搜索", "使用 search 工具");
        skill.setSuccessRate(85.0);

        String prompt = service.buildSkillPrompt(List.of(skill));
        assertThat(prompt).contains("可复用技能");
        assertThat(prompt).contains("搜索文件");
        assertThat(prompt).contains("85%");
        assertThat(prompt).contains("使用 search 工具");
    }

    @Test
    @DisplayName("空列表时 buildSkillPrompt 返回空字符串")
    void shouldReturnEmptyPromptForEmptyList() {
        assertThat(service.buildSkillPrompt(null)).isEmpty();
        assertThat(service.buildSkillPrompt(List.of())).isEmpty();
    }

    @Test
    @DisplayName("getById 返回存在的 Skill")
    void shouldGetById() {
        SkillEntity skill = buildSkill("搜索", "搜索", "步骤");
        skill.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(skill));

        SkillEntity result = service.getById(1L);
        assertThat(result.getName()).isEqualTo("搜索");
    }

    @Test
    @DisplayName("getById 不存在时抛异常")
    void shouldThrowWhenNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("recordExecution 正确调用 Repository")
    void shouldRecordExecution() {
        service.recordExecution(1L, true);
        verify(repository).recordExecution(1L, 1, 0);

        service.recordExecution(1L, false);
        verify(repository).recordExecution(1L, 0, 1);
    }

    private SkillEntity buildSkill(String name, String triggers, String steps) {
        return SkillEntity.builder()
                .name(name)
                .description("测试技能")
                .triggers(triggers)
                .steps(steps)
                .fallbackSteps("降级")
                .version(1)
                .successRate(0.0)
                .isActive(true)
                .build();
    }
}