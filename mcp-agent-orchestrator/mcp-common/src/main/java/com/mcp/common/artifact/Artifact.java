package com.mcp.common.artifact;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Artifact — 当前工作对象。
 * 与 Memory 完全解耦：Memory 存长期知识，Artifact 存临时可编辑对象。
 *
 * 生命周期：create → modify → snapshot → delete
 * （不同于 Memory 的 create → merge → compress → forget）
 *
 * P0 增强：新增 title、mimeType、metadata、createdBy 字段，
 * 支持 ReferenceResolver 进行语义引用解析。
 */
public class Artifact {
    private String id;
    private ArtifactType type;
    private String title;
    private String path;
    private String content;
    private String mimeType;
    private String encoding;
    private Map<String, Object> metadata;
    private String createdBy;
    private Instant createdAt;
    private Instant modifiedAt;
    private long size;
    private int version;
    private boolean dirty;

    public Artifact() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.modifiedAt = Instant.now();
        this.version = 1;
        this.dirty = false;
        this.metadata = new LinkedHashMap<>();
    }

    public Artifact(String path, ArtifactType type, String content, String encoding, long size) {
        this();
        this.path = path;
        this.type = type;
        this.content = content;
        this.encoding = encoding;
        this.size = size;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public ArtifactType getType() { return type; }
    public void setType(ArtifactType type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getContent() { return content; }
    public void setContent(String content) {
        this.content = content;
        this.modifiedAt = Instant.now();
        this.size = content != null ? content.length() : 0;
        this.dirty = true;
    }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getEncoding() { return encoding; }
    public void setEncoding(String encoding) { this.encoding = encoding; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public void addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new LinkedHashMap<>();
        }
        this.metadata.put(key, value);
    }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(Instant modifiedAt) { this.modifiedAt = modifiedAt; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public boolean isDirty() { return dirty; }
    public void setDirty(boolean dirty) { this.dirty = dirty; }

    public void incrementVersion() {
        this.version++;
        this.modifiedAt = Instant.now();
    }

    public void markClean() {
        this.dirty = false;
    }
}