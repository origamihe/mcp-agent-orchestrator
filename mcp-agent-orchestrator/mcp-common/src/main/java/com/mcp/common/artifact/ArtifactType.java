package com.mcp.common.artifact;

/**
 * Artifact 类型枚举。
 * Artifact 是当前工作对象（临时），与 Memory（长期）完全解耦。
 * 生命周期短（分钟~天），支持 create/modify/snapshot/delete，
 * 而 Memory 支持 create/merge/compress/forget。
 */
public enum ArtifactType {
    FILE,
    CODE,
    PROMPT,
    MARKDOWN,
    SQL,
    DIFF,
    LOG,
    CONFIG,
    TEXT,
    IMAGE,
    PDF,
    EXCEL,
    WEB,
    REPORT,
    SEARCH_RESULT,
    SUMMARY,
    CONVERSATION_CONTEXT,
    TOOL_RESULT
}