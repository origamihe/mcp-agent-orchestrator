package com.mcp.engine.reflection;

import com.mcp.core.domain.memory.FailureEntity;
import com.mcp.core.domain.memory.SkillEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptEnricherTest {

    @Mock
    private SkillLibraryService skillLibraryService;

    @Mock
    private FailureLibraryService failureLibraryService;

    @Mock
    private SkillGraphService skillGraphService;

    private PromptEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new PromptEnricher(skillLibraryService, failureLibraryService, skillGraphService);
    }

    @Test
    @DisplayName("null 请求返回空结果")
    void shouldReturnEmptyForNull() {
        StepVerifier.create(enricher.enrich(null))
                .assertNext(r -> {
                    assertThat(r.isEmpty()).isTrue();
                    assertThat(r.matchedSkills()).isEmpty();
                    assertThat(r.matchedFailures()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("空白请求返回空结果")
    void shouldReturnEmptyForBlank() {
        StepVerifier.create(enricher.enrich("   "))
                .assertNext(r -> assertThat(r.isEmpty()).isTrue())
                .verifyComplete();
    }

    @Test
    @DisplayName("匹配到 Skill 和 Failure 时生成完整 Prompt")
    void shouldEnrichWithSkillsAndFailures() {
        SkillEntity skill = new SkillEntity();
        skill.setId(1L);
        skill.setName("搜索文件");
        skill.setSuccessRate(85.0);
        skill.setSteps("步骤1: search");
        when(skillLibraryService.retrieveRelevantSkills("搜索文件"))
                .thenReturn(List.of(skill));
        when(skillLibraryService.buildSkillPrompt(anyList()))
                .thenReturn("## 可复用技能\n搜索文件\n");

        when(skillGraphService.getRelatedSkills(anyList(), anyInt()))
                .thenReturn(List.of());
        when(skillGraphService.buildRelatedSkillPrompt(anyList()))
                .thenReturn("");

        FailureEntity failure = new FailureEntity();
        failure.setId(100L);
        failure.setTaskPattern("搜索文件");
        failure.setErrorSignature("FileNotFound");
        failure.setRootCause("路径错误");
        failure.setCorrectApproach("检查路径");
        failure.setOccurrenceCount(2);

        when(failureLibraryService.matchFailure(anyString(), anyString(), isNull()))
                .thenReturn(reactor.core.publisher.Mono.just(
                        new FailureLibraryService.FailureMatchResult(true, failure, "警告")));
        when(failureLibraryService.buildFailureWarning(anyList()))
                .thenReturn("## 失败警告\nFileNotFound");

        StepVerifier.create(enricher.enrich("搜索文件"))
                .assertNext(r -> {
                    assertThat(r.hasSkills()).isTrue();
                    assertThat(r.hasFailures()).isTrue();
                    assertThat(r.promptText()).contains("可复用技能");
                    assertThat(r.promptText()).contains("失败警告");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("只有 Skill 匹配时只生成 Skill Prompt")
    void shouldEnrichWithSkillsOnly() {
        SkillEntity skill = new SkillEntity();
        skill.setId(1L);
        skill.setName("搜索文件");
        skill.setSuccessRate(85.0);
        when(skillLibraryService.retrieveRelevantSkills("搜索文件"))
                .thenReturn(List.of(skill));
        when(skillLibraryService.buildSkillPrompt(anyList()))
                .thenReturn("## 可复用技能\n搜索文件\n");
        when(skillGraphService.getRelatedSkills(anyList(), anyInt()))
                .thenReturn(List.of());
        when(skillGraphService.buildRelatedSkillPrompt(anyList()))
                .thenReturn("");

        when(failureLibraryService.matchFailure(anyString(), anyString(), isNull()))
                .thenReturn(reactor.core.publisher.Mono.just(
                        FailureLibraryService.FailureMatchResult.noMatch()));

        StepVerifier.create(enricher.enrich("搜索文件"))
                .assertNext(r -> {
                    assertThat(r.hasSkills()).isTrue();
                    assertThat(r.hasFailures()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("关联技能被包含在 matchedSkills 中")
    void shouldIncludeRelatedSkills() {
        SkillEntity skill = new SkillEntity();
        skill.setId(1L);
        skill.setName("搜索文件");
        skill.setSuccessRate(85.0);
        when(skillLibraryService.retrieveRelevantSkills("搜索文件"))
                .thenReturn(List.of(skill));
        when(skillLibraryService.buildSkillPrompt(anyList()))
                .thenReturn("## 可复用技能\n");

        SkillEntity related = new SkillEntity();
        related.setId(2L);
        related.setName("文件操作");
        when(skillGraphService.getRelatedSkills(anyList(), anyInt()))
                .thenReturn(List.of(related));
        when(skillGraphService.buildRelatedSkillPrompt(anyList()))
                .thenReturn("## 关联技能\n");

        when(failureLibraryService.matchFailure(anyString(), anyString(), isNull()))
                .thenReturn(reactor.core.publisher.Mono.just(
                        FailureLibraryService.FailureMatchResult.noMatch()));

        StepVerifier.create(enricher.enrich("搜索文件"))
                .assertNext(r -> {
                    assertThat(r.matchedSkills()).hasSize(2);
                    assertThat(r.matchedSkills().get(0).getName()).isEqualTo("搜索文件");
                    assertThat(r.matchedSkills().get(1).getName()).isEqualTo("文件操作");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("enrichSync 同步方法可用")
    void shouldSupportSyncEnrich() {
        when(skillLibraryService.retrieveRelevantSkills("搜索文件"))
                .thenReturn(List.of());
        when(skillGraphService.getRelatedSkills(anyList(), anyInt()))
                .thenReturn(List.of());
        when(skillGraphService.buildRelatedSkillPrompt(anyList()))
                .thenReturn("");

        when(failureLibraryService.matchFailure(anyString(), anyString(), isNull()))
                .thenReturn(reactor.core.publisher.Mono.just(
                        FailureLibraryService.FailureMatchResult.noMatch()));

        PromptEnricher.EnrichmentResult result = enricher.enrichSync("搜索文件");
        assertThat(result.isEmpty()).isTrue();
    }
}