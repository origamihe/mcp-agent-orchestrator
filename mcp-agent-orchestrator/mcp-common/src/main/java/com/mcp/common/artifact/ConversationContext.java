package com.mcp.common.artifact;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * ConversationContext — 对话上下文，记录当前会话中最近的工作对象。
 *
 * 这是 P0 的核心组件，解决 Follow-up 引用问题：
 * "这个"、"它"、"上一份"、"刚才那个" → 精确解析为具体的 Artifact。
 *
 * 不存放 Memory（长期记忆），只存放当前会话的临时工作对象引用。
 *
 * 设计原则：
 * - 每个类型只保留最近一个（lastXxx）
 * - 提供统一的 resolve(reference) 接口
 * - 持久化到 Artifact（type=CONVERSATION_CONTEXT）中
 */
public class ConversationContext {

    private String sessionId;

    private ArtifactRef lastArtifact;
    private ArtifactRef lastCode;
    private ArtifactRef lastMarkdown;
    private ArtifactRef lastImage;
    private ArtifactRef lastSQL;
    private ArtifactRef lastPrompt;
    private ArtifactRef lastReport;
    private ArtifactRef lastSummary;
    private ArtifactRef lastSearchResult;
    private ArtifactRef lastToolResult;

    private Instant updatedAt;

    public ConversationContext() {
        this.updatedAt = Instant.now();
    }

    public ConversationContext(String sessionId) {
        this();
        this.sessionId = sessionId;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public ArtifactRef getLastArtifact() { return lastArtifact; }
    public ArtifactRef getLastCode() { return lastCode; }
    public ArtifactRef getLastMarkdown() { return lastMarkdown; }
    public ArtifactRef getLastImage() { return lastImage; }
    public ArtifactRef getLastSQL() { return lastSQL; }
    public ArtifactRef getLastPrompt() { return lastPrompt; }
    public ArtifactRef getLastReport() { return lastReport; }
    public ArtifactRef getLastSummary() { return lastSummary; }
    public ArtifactRef getLastSearchResult() { return lastSearchResult; }
    public ArtifactRef getLastToolResult() { return lastToolResult; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /**
     * 根据 Artifact 类型更新对应的 lastXxx 引用。
     */
    public void trackArtifact(Artifact artifact) {
        if (artifact == null || artifact.getType() == null) return;
        this.updatedAt = Instant.now();

        ArtifactRef ref = ArtifactRef.from(artifact);
        this.lastArtifact = ref;

        switch (artifact.getType()) {
            case CODE -> this.lastCode = ref;
            case MARKDOWN -> this.lastMarkdown = ref;
            case IMAGE -> this.lastImage = ref;
            case SQL -> this.lastSQL = ref;
            case PROMPT -> this.lastPrompt = ref;
            case REPORT -> this.lastReport = ref;
            case SUMMARY -> this.lastSummary = ref;
            case SEARCH_RESULT -> this.lastSearchResult = ref;
            case TOOL_RESULT -> this.lastToolResult = ref;
            default -> { /* 仅更新 lastArtifact */ }
        }
    }

    /**
     * 根据语义引用词解析到具体的 ArtifactRef。
     * 支持："这个"、"它"、"上一份"、"刚才那个"、"上次的代码"、"那个SQL"等。
     */
    public Optional<ArtifactRef> resolve(String reference) {
        if (reference == null || reference.isBlank()) return Optional.empty();
        String lower = reference.toLowerCase().trim();

        if (lower.contains("代码") || lower.contains("code")) return Optional.ofNullable(lastCode);
        if (lower.contains("markdown") || lower.contains("md")) return Optional.ofNullable(lastMarkdown);
        if (lower.contains("图片") || lower.contains("图像") || lower.contains("image")) return Optional.ofNullable(lastImage);
        if (lower.contains("sql") || lower.contains("查询")) return Optional.ofNullable(lastSQL);
        if (lower.contains("prompt") || lower.contains("提示")) return Optional.ofNullable(lastPrompt);
        if (lower.contains("报告") || lower.contains("report")) return Optional.ofNullable(lastReport);
        if (lower.contains("总结") || lower.contains("摘要") || lower.contains("summary")) return Optional.ofNullable(lastSummary);
        if (lower.contains("搜索") || lower.contains("search")) return Optional.ofNullable(lastSearchResult);
        if (lower.contains("结果") || lower.contains("工具") || lower.contains("tool")) return Optional.ofNullable(lastToolResult);

        if (lower.contains("这个") || lower.contains("那个") || lower.contains("它")
                || lower.contains("上一份") || lower.contains("刚才") || lower.contains("上次")
                || lower.contains("这份") || lower.contains("那份")) {
            return Optional.ofNullable(lastArtifact);
        }

        return Optional.empty();
    }

    /**
     * 获取所有非空引用的 Map，用于 Prompt 注入。
     */
    public Map<String, ArtifactRef> getAllRefs() {
        Map<String, ArtifactRef> refs = new LinkedHashMap<>();
        putIfNotNull(refs, "lastArtifact", lastArtifact);
        putIfNotNull(refs, "lastCode", lastCode);
        putIfNotNull(refs, "lastMarkdown", lastMarkdown);
        putIfNotNull(refs, "lastImage", lastImage);
        putIfNotNull(refs, "lastSQL", lastSQL);
        putIfNotNull(refs, "lastPrompt", lastPrompt);
        putIfNotNull(refs, "lastReport", lastReport);
        putIfNotNull(refs, "lastSummary", lastSummary);
        putIfNotNull(refs, "lastSearchResult", lastSearchResult);
        putIfNotNull(refs, "lastToolResult", lastToolResult);
        return refs;
    }

    /**
     * 构建用于 Prompt 注入的上下文文本。
     */
    public String buildContextPrompt() {
        Map<String, ArtifactRef> refs = getAllRefs();
        if (refs.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("【当前对话上下文 — ConversationContext】\n");
        sb.append("以下是本会话中最近的工作对象。当用户说\"这个\"、\"它\"、\"上一份\"、\"刚才那个\"时，\n");
        sb.append("请优先从以下对象中匹配：\n\n");

        for (var entry : refs.entrySet()) {
            ArtifactRef ref = entry.getValue();
            sb.append("- ").append(ref.toDisplayString()).append("\n");
        }

        return sb.toString();
    }

    public boolean isEmpty() {
        return lastArtifact == null && lastCode == null && lastMarkdown == null
                && lastImage == null && lastSQL == null && lastPrompt == null
                && lastReport == null && lastSummary == null && lastSearchResult == null
                && lastToolResult == null;
    }

    private static void putIfNotNull(Map<String, ArtifactRef> map, String key, ArtifactRef value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    /**
     * ArtifactRef — 轻量级 Artifact 引用，不包含完整内容。
     * 用于 ConversationContext 中快速引用，避免内存膨胀。
     */
    public static class ArtifactRef {
        private String artifactId;
        private ArtifactType type;
        private String title;
        private String path;
        private String mimeType;
        private int version;
        private Instant createdAt;

        public ArtifactRef() {}

        public static ArtifactRef from(Artifact artifact) {
            ArtifactRef ref = new ArtifactRef();
            ref.artifactId = artifact.getId();
            ref.type = artifact.getType();
            ref.title = artifact.getTitle();
            ref.path = artifact.getPath();
            ref.mimeType = artifact.getMimeType();
            ref.version = artifact.getVersion();
            ref.createdAt = artifact.getCreatedAt();
            return ref;
        }

        public String toDisplayString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(type != null ? type.name() : "UNKNOWN").append("]");
            if (title != null && !title.isBlank()) {
                sb.append(" \"").append(title).append("\"");
            } else if (path != null && !path.isBlank()) {
                sb.append(" ").append(path);
            }
            sb.append(" (v").append(version).append(", id=").append(artifactId).append(")");
            return sb.toString();
        }

        public String getArtifactId() { return artifactId; }
        public void setArtifactId(String artifactId) { this.artifactId = artifactId; }
        public ArtifactType getType() { return type; }
        public void setType(ArtifactType type) { this.type = type; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    }
}