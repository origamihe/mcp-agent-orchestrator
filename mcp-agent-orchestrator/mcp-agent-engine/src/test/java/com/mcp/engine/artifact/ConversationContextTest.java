package com.mcp.engine.artifact;

import com.mcp.common.artifact.Artifact;
import com.mcp.common.artifact.ArtifactType;
import com.mcp.common.artifact.ConversationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0 验证 — ConversationContext 模型测试。
 * 验证：
 * 1. trackArtifact 正确更新各类型 lastXxx 引用
 * 2. resolve 正确解析语义引用
 * 3. isEmpty / buildContextPrompt 正确性
 */
@DisplayName("ConversationContext — P0 对话上下文模型")
class ConversationContextTest {

    private ConversationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new ConversationContext("test-session");
    }

    @Nested
    @DisplayName("trackArtifact — 追踪 Artifact")
    class TrackArtifact {

        @Test
        @DisplayName("追踪 CODE 类型应同时更新 lastArtifact 和 lastCode")
        void shouldTrackCodeArtifact() {
            Artifact code = createArtifact("test-code", ArtifactType.CODE, "public class Test {}");
            ctx.trackArtifact(code);

            assertThat(ctx.getLastArtifact()).isNotNull();
            assertThat(ctx.getLastArtifact().getType()).isEqualTo(ArtifactType.CODE);
            assertThat(ctx.getLastCode()).isNotNull();
            assertThat(ctx.getLastCode().getArtifactId()).isEqualTo(code.getId());
        }

        @Test
        @DisplayName("追踪 SQL 类型应同时更新 lastArtifact 和 lastSQL")
        void shouldTrackSQLArtifact() {
            Artifact sql = createArtifact("test-sql", ArtifactType.SQL, "SELECT * FROM users");
            ctx.trackArtifact(sql);

            assertThat(ctx.getLastSQL()).isNotNull();
            assertThat(ctx.getLastSQL().getType()).isEqualTo(ArtifactType.SQL);
        }

        @Test
        @DisplayName("追踪 MARKDOWN 类型应同时更新 lastArtifact 和 lastMarkdown")
        void shouldTrackMarkdownArtifact() {
            Artifact md = createArtifact("test-md", ArtifactType.MARKDOWN, "# Title\nContent");
            ctx.trackArtifact(md);

            assertThat(ctx.getLastMarkdown()).isNotNull();
            assertThat(ctx.getLastMarkdown().getArtifactId()).isEqualTo(md.getId());
        }

        @Test
        @DisplayName("追踪 REPORT 类型应更新 lastReport")
        void shouldTrackReportArtifact() {
            Artifact report = createArtifact("分析报告", ArtifactType.REPORT, "报告内容...");
            ctx.trackArtifact(report);

            assertThat(ctx.getLastReport()).isNotNull();
            assertThat(ctx.getLastReport().getTitle()).isEqualTo("分析报告");
        }

        @Test
        @DisplayName("追踪 SUMMARY 类型应更新 lastSummary")
        void shouldTrackSummaryArtifact() {
            Artifact summary = createArtifact("总结", ArtifactType.SUMMARY, "总结内容...");
            ctx.trackArtifact(summary);

            assertThat(ctx.getLastSummary()).isNotNull();
            assertThat(ctx.getLastSummary().getType()).isEqualTo(ArtifactType.SUMMARY);
        }

        @Test
        @DisplayName("追踪 SEARCH_RESULT 类型应更新 lastSearchResult")
        void shouldTrackSearchResultArtifact() {
            Artifact searchResult = createArtifact("搜索结果", ArtifactType.SEARCH_RESULT, "[]");
            ctx.trackArtifact(searchResult);

            assertThat(ctx.getLastSearchResult()).isNotNull();
            assertThat(ctx.getLastSearchResult().getType()).isEqualTo(ArtifactType.SEARCH_RESULT);
        }

        @Test
        @DisplayName("追踪 TOOL_RESULT 类型应更新 lastToolResult")
        void shouldTrackToolResultArtifact() {
            Artifact toolResult = createArtifact("工具结果", ArtifactType.TOOL_RESULT, "{}");
            ctx.trackArtifact(toolResult);

            assertThat(ctx.getLastToolResult()).isNotNull();
            assertThat(ctx.getLastToolResult().getType()).isEqualTo(ArtifactType.TOOL_RESULT);
        }

        @Test
        @DisplayName("追踪 PROMPT 类型应更新 lastPrompt")
        void shouldTrackPromptArtifact() {
            Artifact prompt = createArtifact("系统提示", ArtifactType.PROMPT, "You are a helpful assistant");
            ctx.trackArtifact(prompt);

            assertThat(ctx.getLastPrompt()).isNotNull();
            assertThat(ctx.getLastPrompt().getArtifactId()).isEqualTo(prompt.getId());
        }

        @Test
        @DisplayName("追踪 IMAGE 类型应更新 lastImage")
        void shouldTrackImageArtifact() {
            Artifact image = createArtifact("截图", ArtifactType.IMAGE, "base64...");
            ctx.trackArtifact(image);

            assertThat(ctx.getLastImage()).isNotNull();
            assertThat(ctx.getLastImage().getType()).isEqualTo(ArtifactType.IMAGE);
        }
    }

    @Nested
    @DisplayName("resolve — 语义引用解析")
    class ResolveReference {

        @Test
        @DisplayName("\"这个\" 应返回 lastArtifact")
        void shouldResolveZheGe() {
            Artifact code = createArtifact("test", ArtifactType.CODE, "code");
            ctx.trackArtifact(code);

            Optional<ConversationContext.ArtifactRef> result = ctx.resolve("修改这个");
            assertThat(result).isPresent();
            assertThat(result.get().getArtifactId()).isEqualTo(code.getId());
        }

        @Test
        @DisplayName("\"那个代码\" 应返回 lastCode")
        void shouldResolveNaGeCode() {
            Artifact code = createArtifact("test", ArtifactType.CODE, "code");
            ctx.trackArtifact(code);

            Optional<ConversationContext.ArtifactRef> result = ctx.resolve("优化那个代码");
            assertThat(result).isPresent();
            assertThat(result.get().getType()).isEqualTo(ArtifactType.CODE);
        }

        @Test
        @DisplayName("\"刚才那个SQL\" 应返回 lastSQL")
        void shouldResolveGangCaiSQL() {
            Artifact sql = createArtifact("test", ArtifactType.SQL, "SELECT 1");
            ctx.trackArtifact(sql);

            Optional<ConversationContext.ArtifactRef> result = ctx.resolve("修改刚才那个SQL");
            assertThat(result).isPresent();
            assertThat(result.get().getType()).isEqualTo(ArtifactType.SQL);
        }

        @Test
        @DisplayName("\"上一份报告\" 应返回 lastReport")
        void shouldResolveShangYiFenReport() {
            Artifact report = createArtifact("test", ArtifactType.REPORT, "report");
            ctx.trackArtifact(report);

            Optional<ConversationContext.ArtifactRef> result = ctx.resolve("优化上一份报告");
            assertThat(result).isPresent();
            assertThat(result.get().getType()).isEqualTo(ArtifactType.REPORT);
        }

        @Test
        @DisplayName("\"上次的总结\" 应返回 lastSummary")
        void shouldResolveShangCiSummary() {
            Artifact summary = createArtifact("test", ArtifactType.SUMMARY, "summary");
            ctx.trackArtifact(summary);

            Optional<ConversationContext.ArtifactRef> result = ctx.resolve("优化上次的总结");
            assertThat(result).isPresent();
            assertThat(result.get().getType()).isEqualTo(ArtifactType.SUMMARY);
        }

        @Test
        @DisplayName("无匹配时应返回 empty")
        void shouldReturnEmptyOnNoMatch() {
            ctx.trackArtifact(createArtifact("test", ArtifactType.CODE, "code"));

            Optional<ConversationContext.ArtifactRef> result = ctx.resolve("今天天气怎么样");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("空上下文应返回 empty")
        void shouldReturnEmptyOnEmptyContext() {
            Optional<ConversationContext.ArtifactRef> result = ctx.resolve("这个");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("isEmpty / buildContextPrompt")
    class UtilityMethods {

        @Test
        @DisplayName("空上下文 isEmpty 应返回 true")
        void shouldReturnTrueWhenEmpty() {
            assertThat(ctx.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("有 Artifact 后 isEmpty 应返回 false")
        void shouldReturnFalseAfterTracking() {
            ctx.trackArtifact(createArtifact("test", ArtifactType.CODE, "code"));
            assertThat(ctx.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("buildContextPrompt 应包含所有非空引用")
        void shouldBuildContextPrompt() {
            ctx.trackArtifact(createArtifact("代码", ArtifactType.CODE, "code"));
            ctx.trackArtifact(createArtifact("SQL", ArtifactType.SQL, "SELECT 1"));

            String prompt = ctx.buildContextPrompt();
            assertThat(prompt).isNotEmpty();
            assertThat(prompt).contains("ConversationContext");
            assertThat(prompt).contains("CODE");
            assertThat(prompt).contains("SQL");
        }

        @Test
        @DisplayName("空上下文 buildContextPrompt 应返回空字符串")
        void shouldReturnEmptyPromptWhenEmpty() {
            assertThat(ctx.buildContextPrompt()).isEmpty();
        }
    }

    @Nested
    @DisplayName("ArtifactRef")
    class ArtifactRefTest {

        @Test
        @DisplayName("from 应正确复制 Artifact 字段")
        void shouldCreateFromArtifact() {
            Artifact artifact = createArtifact("测试标题", ArtifactType.CODE, "content");
            artifact.setMimeType("text/x-code");
            artifact.setCreatedBy("agent");

            ConversationContext.ArtifactRef ref = ConversationContext.ArtifactRef.from(artifact);

            assertThat(ref.getArtifactId()).isEqualTo(artifact.getId());
            assertThat(ref.getType()).isEqualTo(ArtifactType.CODE);
            assertThat(ref.getTitle()).isEqualTo("测试标题");
            assertThat(ref.getMimeType()).isEqualTo("text/x-code");
            assertThat(ref.getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("toDisplayString 应包含类型和标题")
        void shouldDisplayCorrectly() {
            Artifact artifact = createArtifact("我的代码", ArtifactType.CODE, "code");
            ConversationContext.ArtifactRef ref = ConversationContext.ArtifactRef.from(artifact);

            String display = ref.toDisplayString();
            assertThat(display).contains("CODE");
            assertThat(display).contains("我的代码");
            assertThat(display).contains("v1");
        }

        @Test
        @DisplayName("toDisplayString 无标题时应使用 path")
        void shouldUsePathWhenNoTitle() {
            Artifact artifact = new Artifact("/path/to/file.java", ArtifactType.CODE, "code", "UTF-8", 100);
            ConversationContext.ArtifactRef ref = ConversationContext.ArtifactRef.from(artifact);

            String display = ref.toDisplayString();
            assertThat(display).contains("/path/to/file.java");
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