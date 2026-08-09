package com.mcp.engine.reflection;

import com.mcp.core.domain.memory.FailureEntity;
import com.mcp.core.repository.FailureLibraryRepository;
import com.mcp.llm.client.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FailureLibraryServiceTest {

    @Mock
    private FailureLibraryRepository repository;

    @Mock
    private LlmClient llmClient;

    private FailureLibraryService service;

    @BeforeEach
    void setUp() {
        service = new FailureLibraryService(repository, llmClient);
    }

    @Test
    @DisplayName("创建新 Failure")
    void shouldCreateNewFailure() {
        FailureEntity entity = buildFailure("搜索文件", "FileNotFound", "路径错误", "检查路径");
        when(repository.save(any())).thenReturn(entity);

        FailureEntity result = service.create(entity);
        assertThat(result.getTaskPattern()).isEqualTo("搜索文件");
        assertThat(result.getErrorSignature()).isEqualTo("FileNotFound");
    }

    @Test
    @DisplayName("重复 Failure 出现时递增计数")
    void shouldIncrementOccurrenceOnRepeat() {
        FailureEntity existing = buildFailure("搜索文件", "FileNotFound", "路径错误", "检查路径");
        existing.setId(1L);
        existing.setOccurrenceCount(3);

        when(repository.findUnresolvedByTaskPattern("搜索文件")).thenReturn(List.of(existing));
        doNothing().when(repository).incrementOccurrence(1L);

        FailureEntity result = service.createOrUpdate("搜索文件", "FileNotFound",
                "路径错误", "检查路径", "context");
        assertThat(result.getOccurrenceCount()).isEqualTo(4);
        verify(repository).incrementOccurrence(1L);
    }

    @Test
    @DisplayName("Error 签名匹配 — 无需 LLM")
    void shouldMatchBySignature() {
        FailureEntity failure = buildFailure("搜索文件", "FileNotFound", "路径错误", "检查路径");
        failure.setId(1L);
        failure.setOccurrenceCount(2);

        when(repository.findByIsResolvedFalseOrderByOccurrenceCountDesc())
                .thenReturn(List.of(failure));

        Mono<FailureLibraryService.FailureMatchResult> result = service.matchFailure(
                "搜索", "", "FileNotFoundException: /tmp/test");

        StepVerifier.create(result)
                .assertNext(m -> {
                    assertThat(m.matched()).isTrue();
                    assertThat(m.shouldWarn()).isTrue();
                    assertThat(m.failure().getTaskPattern()).isEqualTo("搜索文件");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("没有未解决的 Failure 时返回 noMatch")
    void shouldReturnNoMatchWhenEmpty() {
        when(repository.findByIsResolvedFalseOrderByOccurrenceCountDesc())
                .thenReturn(List.of());

        Mono<FailureLibraryService.FailureMatchResult> result = service.matchFailure(
                "搜索", "", "error");

        StepVerifier.create(result)
                .assertNext(m -> assertThat(m.matched()).isFalse())
                .verifyComplete();
    }

    @Test
    @DisplayName("LLM 匹配 — 签名不匹配但语义相似")
    void shouldMatchByLlmWhenSignatureDiffers() {
        FailureEntity failure = buildFailure("搜索文件", "FileNotFound", "路径错误", "使用绝对路径");
        failure.setId(1L);
        failure.setOccurrenceCount(2);

        when(repository.findByIsResolvedFalseOrderByOccurrenceCountDesc())
                .thenReturn(List.of(failure));

        String llmResponse = """
                {
                    "matched": true,
                    "matchedFailureId": 1,
                    "shouldWarn": true,
                    "warningMessage": "类似任务曾失败，请检查路径"
                }
                """;
        when(llmClient.generate(anyString())).thenReturn(Mono.just(llmResponse));

        Mono<FailureLibraryService.FailureMatchResult> result = service.matchFailure(
                "帮我找文件", "", "PermissionDenied");

        StepVerifier.create(result)
                .assertNext(m -> {
                    assertThat(m.matched()).isTrue();
                    assertThat(m.shouldWarn()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("markResolved 正确调用 Repository")
    void shouldMarkResolved() {
        service.markResolved(1L, 10L);
        verify(repository).markResolved(1L, 10L);
    }

    @Test
    @DisplayName("buildFailureWarning 生成正确的警告文本")
    void shouldBuildFailureWarning() {
        FailureEntity failure = buildFailure("搜索文件", "FileNotFound", "路径错误", "检查路径");
        failure.setOccurrenceCount(3);

        String warning = service.buildFailureWarning(List.of(failure));
        assertThat(warning).contains("已知失败模式");
        assertThat(warning).contains("搜索文件");
        assertThat(warning).contains("3 次");
        assertThat(warning).contains("FileNotFound");
        assertThat(warning).contains("路径错误");
        assertThat(warning).contains("检查路径");
    }

    @Test
    @DisplayName("空列表时 buildFailureWarning 返回空字符串")
    void shouldReturnEmptyForNullList() {
        assertThat(service.buildFailureWarning(null)).isEmpty();
        assertThat(service.buildFailureWarning(List.of())).isEmpty();
    }

    @Test
    @DisplayName("getUnresolvedFailures 返回未解决的 Failure")
    void shouldReturnUnresolvedFailures() {
        List<FailureEntity> failures = List.of(
                buildFailure("A", "errA", "rootA", "fixA"),
                buildFailure("B", "errB", "rootB", "fixB")
        );
        when(repository.findByIsResolvedFalseOrderByOccurrenceCountDesc()).thenReturn(failures);

        List<FailureEntity> result = service.getUnresolvedFailures();
        assertThat(result).hasSize(2);
    }

    private FailureEntity buildFailure(String taskPattern, String errorSignature,
                                        String rootCause, String correctApproach) {
        return FailureEntity.builder()
                .taskPattern(taskPattern)
                .errorSignature(errorSignature)
                .rootCause(rootCause)
                .correctApproach(correctApproach)
                .contextSnapshot("{}")
                .occurrenceCount(1)
                .isResolved(false)
                .build();
    }
}