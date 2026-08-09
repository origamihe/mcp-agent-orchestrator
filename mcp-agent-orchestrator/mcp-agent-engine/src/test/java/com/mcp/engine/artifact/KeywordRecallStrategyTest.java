package com.mcp.engine.artifact;

import com.mcp.common.artifact.Artifact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KeywordRecallStrategy — 结构化摘要生成")
class KeywordRecallStrategyTest {

    private KeywordRecallStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new KeywordRecallStrategy();
    }

    @Nested
    @DisplayName("structured summary — Markdown 标题文档")
    class StructuredSummary {

        @Test
        @DisplayName("应提取章节标题和首段作为摘要")
        void shouldExtractSectionHeadingsAndFirstParagraphs() {
            String content = """
                    # CoC 模组：暗影猎手

                    本模组适用于 3-5 名调查员，推荐使用标准角色创建规则。

                    ## 角色创建

                    调查员必须使用以下规则创建角色：力量 3D6×5，体质 3D6×5...

                    ## 怪物数据

                    暗影猎手：STR 80, CON 75, DEX 90...

                    ## 结局分支

                    根据调查员的行动，模组有以下结局...
                    """.repeat(50);

            Artifact artifact = new Artifact();
            artifact.setContent(content);

            String result = strategy.recall(artifact, "创建角色", null);

            assertThat(result)
                    .contains("文档结构概览")
                    .contains("# CoC 模组：暗影猎手")
                    .contains("## 角色创建")
                    .contains("## 怪物数据")
                    .contains("## 结局分支");
        }

        @Test
        @DisplayName("文档 ≤ 3000 字符时全文返回，不生成摘要")
        void shouldReturnFullContentForSmallDoc() {
            String content = "短文档内容，只有几行字。";

            Artifact artifact = new Artifact();
            artifact.setContent(content);

            String result = strategy.recall(artifact, "任意问题", null);

            assertThat(result).isEqualTo(content);
        }

        @Test
        @DisplayName("章节数超过 MAX_SECTIONS 时截断")
        void shouldTruncateWhenExceedingMaxSections() {
            StringBuilder sb = new StringBuilder();
            sb.append("# 标题\n\n这里是一段很长的描述文字用来填充文档内容确保超过阈值。\n\n".repeat(100));
            for (int i = 1; i <= 10; i++) {
                sb.append("## 章节").append(i).append("\n\n章节内容第").append(i).append("段，这里是更多的填充文字。\n\n");
            }
            Artifact artifact = new Artifact();
            artifact.setContent(sb.toString());

            String result = strategy.recall(artifact, "查询", null);

            assertThat(result).contains("文档结构概览");
            long sectionCount = result.lines().filter(l -> l.startsWith("## ")).count();
            assertThat(sectionCount).isLessThanOrEqualTo(6);
        }
    }

    @Nested
    @DisplayName("plain summary — 无 Markdown 标题文档")
    class PlainSummary {

        @Test
        @DisplayName("无标题文档使用前 500 字符截断摘要")
        void shouldUsePlainSummaryForNonMarkdownDoc() {
            String content = "这是一段很长的纯文本内容，没有任何Markdown标题。".repeat(200);

            Artifact artifact = new Artifact();
            artifact.setContent(content);

            String result = strategy.recall(artifact, "查询", null);

            assertThat(result).contains("【文档摘要】")
                    .contains("...(文档共")
                    .doesNotContain("文档结构概览");
        }
    }

    @Nested
    @DisplayName("summaryCache — 摘要缓存复用")
    class SummaryCache {

        @Test
        @DisplayName("提供 summaryCache 时跳过摘要生成")
        void shouldUseCachedSummary() {
            String content = "# 标题\n\n第一段内容确保超过阈值。\n\n## 章节1\n\n章节1内容。\n\n".repeat(200);
            Artifact artifact = new Artifact();
            artifact.setContent(content);

            String result = strategy.recall(artifact, "查询", "这是缓存摘要");

            assertThat(result).contains("【文档摘要】")
                    .contains("这是缓存摘要")
                    .doesNotContain("文档结构概览");
        }

        @Test
        @DisplayName("summaryCache 为空白时正常生成摘要")
        void shouldGenerateWhenCacheIsBlank() {
            String content = "# 标题\n\n描述确保超过阈值。\n\n## 章节\n\n内容。\n\n".repeat(200);
            Artifact artifact = new Artifact();
            artifact.setContent(content);

            String result = strategy.recall(artifact, "查询", "   ");

            assertThat(result).contains("文档结构概览")
                    .contains("【文档摘要】");
        }
    }
}