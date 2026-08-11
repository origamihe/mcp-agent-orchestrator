package com.mcp.engine.artifact;

import com.mcp.common.artifact.Artifact;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EmbeddingRecallStrategy — 基于 TF-IDF 向量化 + 余弦相似度的文档召回策略。
 *
 * 策略：
 * 1. 文档 ≤ 3000 chars → 全文返回
 * 2. 文档 > 3000 chars → 分块后计算 TF-IDF 相似度，返回最相关的 ~3000 chars
 *
 * 可替换为基于 Spring AI Embedding 或 Hybrid（BM25 + Embedding）的更高级实现。
 * 通过 @Qualifier("embedding") 可替换默认的 KeywordRecallStrategy。
 */
@Slf4j
@Component("embeddingRecallStrategy")
@Qualifier("embedding")
public class EmbeddingRecallStrategy implements ArtifactRecallStrategy {

    private static final int SMALL_DOC_THRESHOLD = 3000;
    private static final int CHUNK_SIZE = 500;
    private static final int MAX_RESULT_CHARS = 3000;
    private static final int TOP_K = 6;

    @Override
    public String recall(Artifact artifact, String userMessage, String summaryCache) {
        String content = artifact.getContent();
        if (content == null || content.isBlank()) {
            return "";
        }

        if (content.length() <= SMALL_DOC_THRESHOLD) {
            return content;
        }

        List<String> chunks = chunkDocument(content, CHUNK_SIZE);
        if (chunks.size() <= 1) {
            return content;
        }

        List<String> userTokens = tokenize(userMessage);
        Map<String, Double> userVector = computeTfIdf(userTokens, chunks);

        List<ChunkScore> scoredChunks = new ArrayList<>();
        for (String chunk : chunks) {
            List<String> chunkTokens = tokenize(chunk);
            Map<String, Double> chunkVector = computeTfIdf(chunkTokens, List.of(chunk));
            double similarity = cosineSimilarity(userVector, chunkVector);
            scoredChunks.add(new ChunkScore(chunk, similarity));
        }

        scoredChunks.sort(Comparator.comparingDouble(ChunkScore::score).reversed());

        StringBuilder result = new StringBuilder();

        if (summaryCache != null && !summaryCache.isBlank()) {
            result.append("【文档摘要】\n").append(summaryCache).append("\n\n");
        }

        result.append("【相关段落（语义相似度排序）】\n");
        int totalChars = 0;
        for (int i = 0; i < Math.min(TOP_K, scoredChunks.size()); i++) {
            ChunkScore cs = scoredChunks.get(i);
            if (cs.score < 0.05) {
                break;
            }
            String chunk = cs.chunk;
            if (totalChars + chunk.length() > MAX_RESULT_CHARS) {
                int remaining = MAX_RESULT_CHARS - totalChars;
                if (remaining > 50) {
                    result.append(chunk, 0, remaining).append("...");
                }
                break;
            }
            result.append(chunk).append("\n---\n");
            totalChars += chunk.length();
        }

        log.debug("[EmbeddingRecall] Recalled {} chunks (top score={}) for {} chars doc",
                Math.min(TOP_K, scoredChunks.size()),
                String.format("%.3f", scoredChunks.isEmpty() ? 0.0 : scoredChunks.get(0).score),
                content.length());

        return result.toString();
    }

    private List<String> chunkDocument(String content, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = content.split("\n\n");
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            if (current.length() + para.length() > chunkSize && current.length() > 0) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(para);
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        String lower = text.toLowerCase();

        StringBuilder word = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                word.append(c);
            } else {
                if (word.length() >= 2) {
                    tokens.add(word.toString());
                }
                word = new StringBuilder();
            }
        }
        if (word.length() >= 2) {
            tokens.add(word.toString());
        }

        for (int i = 0; i < lower.length() - 1; i++) {
            if (Character.isLetterOrDigit(lower.charAt(i)) && Character.isLetterOrDigit(lower.charAt(i + 1))) {
                tokens.add(lower.substring(i, i + 2));
            }
        }

        return tokens;
    }

    private Map<String, Double> computeTfIdf(List<String> queryTokens, List<String> documents) {
        Map<String, Double> tfIdf = new HashMap<>();
        int docCount = documents.size();

        Map<String, Integer> termFreq = new HashMap<>();
        for (String token : queryTokens) {
            termFreq.merge(token, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            String term = entry.getKey();
            double tf = entry.getValue() / (double) queryTokens.size();

            int containingDocs = 0;
            for (String doc : documents) {
                if (doc.toLowerCase().contains(term)) {
                    containingDocs++;
                }
            }
            double idf = Math.log((docCount + 1.0) / (containingDocs + 1.0)) + 1.0;
            tfIdf.put(term, tf * idf);
        }

        return tfIdf;
    }

    private double cosineSimilarity(Map<String, Double> vec1, Map<String, Double> vec2) {
        if (vec1.isEmpty() || vec2.isEmpty()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (Map.Entry<String, Double> entry : vec1.entrySet()) {
            double v1 = entry.getValue();
            double v2 = vec2.getOrDefault(entry.getKey(), 0.0);
            dotProduct += v1 * v2;
            norm1 += v1 * v1;
        }

        for (double v : vec2.values()) {
            norm2 += v * v;
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    private record ChunkScore(String chunk, double score) {}
}