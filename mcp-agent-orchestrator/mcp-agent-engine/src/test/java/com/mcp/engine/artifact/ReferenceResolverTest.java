package com.mcp.engine.artifact;

import com.mcp.common.artifact.Artifact;
import com.mcp.common.artifact.ArtifactType;
import com.mcp.common.artifact.ConversationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0 验证 — ReferenceResolver 语义引用解析器测试。
 * 验证：
 * 1. containsReference 正确检测引用语义
 * 2. resolve 正确从 ConversationContext 解析
 * 3. extractTypeKeyword 正确提取类型关键词
 * 4. 兜底逻辑：无匹配时返回 lastArtifact
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReferenceResolver — P0 语义引用解析器")
class ReferenceResolverTest {

    @Mock
    private ArtifactService artifactService;

    private ReferenceResolver resolver;
    private ConversationContext ctx;

    @BeforeEach
    void setUp() {
        resolver = new ReferenceResolver();
        ctx = new ConversationContext("test-session");
    }

    @Nested
    @DisplayName("containsReference — 检测引用语义")
    class ContainsReference {

        @Test
        @DisplayName("\"修改这个\" 应检测到引用")
        void shouldDetectZheGe() {
            assertThat(resolver.containsReference("修改这个")).isTrue();
        }

        @Test
        @DisplayName("\"优化那个代码\" 应检测到引用")
        void shouldDetectNaGeCode() {
            assertThat(resolver.containsReference("优化那个代码")).isTrue();
        }

        @Test
        @DisplayName("\"刚才那个SQL\" 应检测到引用")
        void shouldDetectGangCaiSQL() {
            assertThat(resolver.containsReference("修改刚才那个SQL")).isTrue();
        }

        @Test
        @DisplayName("\"上一份报告\" 应检测到引用")
        void shouldDetectShangYiFen() {
            assertThat(resolver.containsReference("优化上一份报告")).isTrue();
        }

        @Test
        @DisplayName("\"上次的总结\" 应检测到引用")
        void shouldDetectShangCiSummary() {
            assertThat(resolver.containsReference("优化上次的总结")).isTrue();
        }

        @Test
        @DisplayName("\"它\" 应检测到引用")
        void shouldDetectIt() {
            assertThat(resolver.containsReference("修改它")).isTrue();
        }

        @Test
        @DisplayName("\"该文件\" 应检测到引用")
        void shouldDetectGaiFile() {
            assertThat(resolver.containsReference("修改该文件")).isTrue();
        }

        @Test
        @DisplayName("无引用词时不应检测到")
        void shouldNotDetectNormalRequest() {
            assertThat(resolver.containsReference("今天天气怎么样")).isFalse();
        }

        @Test
        @DisplayName("空输入不应检测到")
        void shouldNotDetectEmptyInput() {
            assertThat(resolver.containsReference("")).isFalse();
            assertThat(resolver.containsReference(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("resolve — 解析引用")
    class ResolveReference {

        @Test
        @DisplayName("解析到代码引用时应从 DB 加载完整内容")
        void shouldResolveAndLoadFullContent() {
            Artifact code = createArtifact("test-code", ArtifactType.CODE, "public class Test {}");
            ctx.trackArtifact(code);
            when(artifactService.findById(code.getId())).thenReturn(Optional.of(code));

            Optional<Artifact> result = resolver.resolve("修改这个代码", ctx, artifactService);

            assertThat(result).isPresent();
            assertThat(result.get().getContent()).isEqualTo("public class Test {}");
            verify(artifactService).findById(code.getId());
        }

        @Test
        @DisplayName("解析到 SQL 引用时应从 DB 加载")
        void shouldResolveSQL() {
            Artifact sql = createArtifact("test-sql", ArtifactType.SQL, "SELECT * FROM users");
            ctx.trackArtifact(sql);
            when(artifactService.findById(sql.getId())).thenReturn(Optional.of(sql));

            Optional<Artifact> result = resolver.resolve("修改刚才那个SQL", ctx, artifactService);

            assertThat(result).isPresent();
            assertThat(result.get().getType()).isEqualTo(ArtifactType.SQL);
        }

        @Test
        @DisplayName("解析到报告引用时应从 DB 加载")
        void shouldResolveReport() {
            Artifact report = createArtifact("分析报告", ArtifactType.REPORT, "报告内容...");
            ctx.trackArtifact(report);
            when(artifactService.findById(report.getId())).thenReturn(Optional.of(report));

            Optional<Artifact> result = resolver.resolve("优化上一份报告", ctx, artifactService);

            assertThat(result).isPresent();
            assertThat(result.get().getType()).isEqualTo(ArtifactType.REPORT);
        }

        @Test
        @DisplayName("解析到总结引用时应从 DB 加载")
        void shouldResolveSummary() {
            Artifact summary = createArtifact("总结", ArtifactType.SUMMARY, "总结内容...");
            ctx.trackArtifact(summary);
            when(artifactService.findById(summary.getId())).thenReturn(Optional.of(summary));

            Optional<Artifact> result = resolver.resolve("优化上次的总结", ctx, artifactService);

            assertThat(result).isPresent();
            assertThat(result.get().getType()).isEqualTo(ArtifactType.SUMMARY);
        }

        @Test
        @DisplayName("解析到搜索引用时应从 DB 加载")
        void shouldResolveSearchResult() {
            Artifact searchResult = createArtifact("搜索结果", ArtifactType.SEARCH_RESULT, "[]");
            ctx.trackArtifact(searchResult);
            when(artifactService.findById(searchResult.getId())).thenReturn(Optional.of(searchResult));

            Optional<Artifact> result = resolver.resolve("优化上次的搜索", ctx, artifactService);

            assertThat(result).isPresent();
            assertThat(result.get().getType()).isEqualTo(ArtifactType.SEARCH_RESULT);
        }

        @Test
        @DisplayName("模糊引用（无类型关键词）应返回 lastArtifact")
        void shouldFallbackToLastArtifact() {
            Artifact artifact = createArtifact("test", ArtifactType.TEXT, "content");
            ctx.trackArtifact(artifact);
            when(artifactService.findById(artifact.getId())).thenReturn(Optional.of(artifact));

            Optional<Artifact> result = resolver.resolve("修改这个", ctx, artifactService);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(artifact.getId());
        }

        @Test
        @DisplayName("DB 中找不到 Artifact 时应返回 empty")
        void shouldReturnEmptyWhenNotFoundInDB() {
            Artifact artifact = createArtifact("test", ArtifactType.CODE, "code");
            ctx.trackArtifact(artifact);
            when(artifactService.findById(anyString())).thenReturn(Optional.empty());

            Optional<Artifact> result = resolver.resolve("修改这个代码", ctx, artifactService);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("空上下文应返回 empty")
        void shouldReturnEmptyOnEmptyContext() {
            Optional<Artifact> result = resolver.resolve("修改这个", ctx, artifactService);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("无引用语义的请求应返回 empty")
        void shouldReturnEmptyOnNoReference() {
            ctx.trackArtifact(createArtifact("test", ArtifactType.CODE, "code"));
            Optional<Artifact> result = resolver.resolve("今天天气怎么样", ctx, artifactService);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("extractTypeKeyword — 提取类型关键词")
    class ExtractTypeKeyword {

        @Test
        @DisplayName("应提取\"代码\"")
        void shouldExtractCode() {
            assertThat(resolver.extractTypeKeyword("修改那个代码")).hasValue("代码");
        }

        @Test
        @DisplayName("应提取\"SQL\"")
        void shouldExtractSQL() {
            assertThat(resolver.extractTypeKeyword("优化那个SQL")).hasValue("sql");
        }

        @Test
        @DisplayName("应提取\"报告\"")
        void shouldExtractReport() {
            assertThat(resolver.extractTypeKeyword("修改那份报告")).hasValue("报告");
        }

        @Test
        @DisplayName("应提取\"markdown\"")
        void shouldExtractMarkdown() {
            assertThat(resolver.extractTypeKeyword("优化那个markdown")).hasValue("markdown");
        }

        @Test
        @DisplayName("应提取\"图片\"")
        void shouldExtractImage() {
            assertThat(resolver.extractTypeKeyword("修改那个图片")).hasValue("图片");
        }

        @Test
        @DisplayName("无关键词时应返回 empty")
        void shouldReturnEmptyOnNoKeyword() {
            assertThat(resolver.extractTypeKeyword("修改这个")).isEmpty();
        }
    }

    private Artifact createArtifact(String title, ArtifactType type, String content) {
        Artifact artifact = new Artifact();
        artifact.setTitle(title);
        artifact.setType(type);
        artifact.setContent(content);
        artifact.setCreatedBy("test");
        return artifact;
    }
}