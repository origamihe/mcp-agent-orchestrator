package com.mcp.engine.artifact;

import com.mcp.common.artifact.Artifact;

/**
 * ArtifactRecallStrategy — 文档召回策略接口。
 *
 * 将召回逻辑从 ArtifactService 中解耦，未来可无缝升级到：
 * - EmbeddingRecallStrategy（向量检索）
 * - BM25RecallStrategy（BM25 检索）
 * - HybridRecallStrategy（混合检索）
 *
 * 当前默认实现：KeywordRecallStrategy（基于关键词匹配）
 */
public interface ArtifactRecallStrategy {

    /**
     * 从 Artifact 内容中召回与用户消息相关的文本片段。
     *
     * @param artifact     Artifact 对象（包含完整内容）
     * @param userMessage  用户当前消息
     * @param summaryCache 缓存的摘要（可为 null）
     * @return 召回的文本片段（应控制在 ~3000 chars 以内）
     */
    String recall(Artifact artifact, String userMessage, String summaryCache);
}