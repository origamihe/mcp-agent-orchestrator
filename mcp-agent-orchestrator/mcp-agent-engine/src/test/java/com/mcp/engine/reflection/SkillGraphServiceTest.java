package com.mcp.engine.reflection;

import com.mcp.core.domain.memory.SkillDependencyEntity;
import com.mcp.core.domain.memory.SkillEntity;
import com.mcp.core.repository.SkillDependencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillGraphServiceTest {

    @Mock
    private SkillDependencyRepository dependencyRepository;

    @Mock
    private SkillLibraryService skillLibraryService;

    private SkillGraphService service;

    @BeforeEach
    void setUp() {
        service = new SkillGraphService(dependencyRepository, skillLibraryService);
    }

    @Test
    @DisplayName("记录新共现 — 创建双向依赖")
    void shouldRecordNewCoOccurrence() {
        when(dependencyRepository.findDependencyBetween(1L, 2L)).thenReturn(List.of());
        when(dependencyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordCoOccurrence(1L, 2L);

        verify(dependencyRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("已存在的共现 — 递增计数")
    void shouldIncrementExistingCoOccurrence() {
        SkillDependencyEntity existing = new SkillDependencyEntity();
        existing.setId(100L);
        existing.setCoOccurrenceCount(3);
        existing.setConfidence(0.7);

        when(dependencyRepository.findDependencyBetween(1L, 2L)).thenReturn(List.of(existing));
        when(dependencyRepository.incrementCoOccurrence(100L)).thenReturn(1);

        service.recordCoOccurrence(1L, 2L);

        verify(dependencyRepository).incrementCoOccurrence(100L);
        verify(dependencyRepository, never()).save(any());
    }

    @Test
    @DisplayName("相同 ID 不记录共现")
    void shouldNotRecordSelfCoOccurrence() {
        service.recordCoOccurrence(1L, 1L);
        verify(dependencyRepository, never()).findDependencyBetween(anyLong(), anyLong());
        verify(dependencyRepository, never()).save(any());
    }

    @Test
    @DisplayName("记录多个 Skill 的共现组合")
    void shouldRecordMultipleCoOccurrences() {
        when(dependencyRepository.findDependencyBetween(anyLong(), anyLong()))
                .thenReturn(List.of());
        when(dependencyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordCoOccurrences(List.of(1L, 2L, 3L));

        verify(dependencyRepository, times(6)).save(any());
    }

    @Test
    @DisplayName("1 个 Skill 不触发共现记录")
    void shouldNotRecordCoOccurrencesForSingleSkill() {
        service.recordCoOccurrences(List.of(1L));
        verify(dependencyRepository, never()).findDependencyBetween(anyLong(), anyLong());
    }

    @Test
    @DisplayName("获取关联 Skill — 过滤低置信度")
    void shouldGetRelatedSkillsWithHighConfidence() {
        SkillDependencyEntity dep1 = new SkillDependencyEntity();
        dep1.setTargetSkillId(2L);
        dep1.setCoOccurrenceCount(5);
        dep1.setConfidence(0.8);

        SkillDependencyEntity dep2 = new SkillDependencyEntity();
        dep2.setTargetSkillId(3L);
        dep2.setCoOccurrenceCount(1);
        dep2.setConfidence(0.5);

        when(dependencyRepository.findBySourceSkillIds(List.of(1L)))
                .thenReturn(List.of(dep1, dep2));

        SkillEntity skill2 = new SkillEntity();
        skill2.setId(2L);
        skill2.setName("Skill B");
        skill2.setSuccessRate(90.0);
        when(skillLibraryService.getById(2L)).thenReturn(skill2);

        List<SkillEntity> result = service.getRelatedSkills(List.of(1L), 5);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Skill B");
    }

    @Test
    @DisplayName("buildRelatedSkillPrompt 生成正确的提示文本")
    void shouldBuildRelatedSkillPrompt() {
        SkillEntity skill = new SkillEntity();
        skill.setName("Skill B");
        skill.setSuccessRate(85.0);
        skill.setSteps("使用 search 工具");

        String prompt = service.buildRelatedSkillPrompt(List.of(skill));
        assertThat(prompt).contains("关联技能推荐");
        assertThat(prompt).contains("Skill B");
        assertThat(prompt).contains("85%");
        assertThat(prompt).contains("使用 search 工具");
    }

    @Test
    @DisplayName("addPrerequisite 创建前置依赖")
    void shouldAddPrerequisite() {
        when(dependencyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.addPrerequisite(1L, 2L);

        verify(dependencyRepository).save(argThat(dep ->
                dep.getSourceSkillId().equals(1L)
                && dep.getTargetSkillId().equals(2L)
                && dep.getDependencyType() == SkillDependencyEntity.DependencyType.PREREQUISITE));
    }

    @Test
    @DisplayName("addAlternative 创建替代依赖")
    void shouldAddAlternative() {
        when(dependencyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.addAlternative(1L, 2L);

        verify(dependencyRepository).save(argThat(dep ->
                dep.getSourceSkillId().equals(1L)
                && dep.getTargetSkillId().equals(2L)
                && dep.getDependencyType() == SkillDependencyEntity.DependencyType.ALTERNATIVE));
    }
}