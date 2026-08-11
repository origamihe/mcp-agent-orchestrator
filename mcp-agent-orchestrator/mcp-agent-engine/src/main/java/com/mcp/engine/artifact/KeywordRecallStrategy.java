package com.mcp.engine.artifact;

import com.mcp.common.artifact.Artifact;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * KeywordRecallStrategy — 默认的关键词召回策略。
 *
 * 策略：
 * 1. 文档 ≤ 3000 chars → 全文返回
 * 2. 文档 > 3000 chars → Summary + 关键词匹配段落
 *
 * 通过 @Qualifier("keyword") 可与 EmbeddingRecallStrategy 切换。
 */
@Slf4j
@Component("keywordRecallStrategy")
@Qualifier("keyword")
public class KeywordRecallStrategy implements ArtifactRecallStrategy {

    private static final int SMALL_DOC_THRESHOLD = 3000;
    private static final int SUMMARY_MAX_LEN = 500;
    private static final int STRUCTURED_SUMMARY_MAX_LEN = 2000;
    private static final int CHUNK_MAX_LEN = 1500;
    private static final int MAX_SECTIONS = 6;

    @Override
    public String recall(Artifact artifact, String userMessage, String summaryCache) {
        String content = artifact.getContent();
        if (content == null || content.isBlank()) {
            return "";
        }

        if (content.length() <= SMALL_DOC_THRESHOLD) {
            return content;
        }

        StringBuilder result = new StringBuilder();

        String summary = (summaryCache != null && !summaryCache.isBlank())
                ? summaryCache
                : generateSummary(content);
        result.append("【文档摘要】\n").append(summary).append("\n");

        String chunk = recallChunk(content, userMessage);
        if (chunk != null && !chunk.isBlank()) {
            result.append("\n【相关段落】\n").append(chunk);
        }

        return result.toString();
    }

    private String generateSummary(String content) {
        boolean structured = hasMarkdownHeadings(content);
        log.debug("[KeywordRecall] Generating {} summary for {} chars doc",
                structured ? "structured" : "plain", content.length());
        return structured
                ? generateStructuredSummary(content)
                : generatePlainSummary(content);
    }

    private boolean hasMarkdownHeadings(String content) {
        return content.contains("\n# ") || content.startsWith("# ");
    }

    private String generateStructuredSummary(String content) {
        StringBuilder sb = new StringBuilder();
        sb.append("文档结构概览 (").append(content.length()).append(" 字符):\n");

        String[] lines = content.split("\n");
        int sectionsFound = 0;
        boolean inRelevantSection = content.startsWith("# ");

        for (int i = 0; i < lines.length && sectionsFound < MAX_SECTIONS; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("## ") || line.startsWith("# ")) {
                sectionsFound++;
                sb.append("\n").append(line).append("\n");
                inRelevantSection = true;
            } else if (inRelevantSection && !line.startsWith("#") && !line.startsWith("```")) {
                sb.append(limitLength(line, 200)).append("\n");
                inRelevantSection = false;
            }
            if (sb.length() >= STRUCTURED_SUMMARY_MAX_LEN) {
                break;
            }
        }
        return sb.toString().trim();
    }

    private String generatePlainSummary(String content) {
        int end = Math.min(content.length(), SUMMARY_MAX_LEN);
        int lastNewline = content.lastIndexOf('\n', end);
        if (lastNewline > end / 2) {
            end = lastNewline;
        }
        return content.substring(0, end) + "\n...(文档共 " + content.length() + " 字符)";
    }

    private String recallChunk(String content, String userMessage) {
        String[] paragraphs = content.split("\n\n|\n(?=\\S)");
        if (paragraphs.length <= 1) {
            return null;
        }

        List<String> keywords = Arrays.stream(userMessage.split("[\\s，。！？、；：\"'（）《》\\[\\]\\-]+"))
                .filter(k -> k.length() >= 2)
                .toList();

        if (keywords.isEmpty()) {
            return paragraphs.length > 0 && paragraphs[0].length() > 50
                    ? limitLength(paragraphs[0], CHUNK_MAX_LEN)
                    : null;
        }

        ParagraphScore best = null;
        for (String para : paragraphs) {
            if (para.length() < 20) continue;
            int score = 0;
            for (String kw : keywords) {
                if (para.contains(kw)) score += kw.length();
            }
            if (best == null || score > best.score) {
                best = new ParagraphScore(para, score);
            }
        }

        if (best != null && best.score > 0) {
            return limitLength(best.paragraph, CHUNK_MAX_LEN);
        }

        return null;
    }

    private String limitLength(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private record ParagraphScore(String paragraph, int score) {}
}