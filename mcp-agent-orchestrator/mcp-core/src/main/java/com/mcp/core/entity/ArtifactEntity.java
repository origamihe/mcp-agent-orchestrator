package com.mcp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "artifacts", schema = "mcp_agent",
        indexes = {
                @Index(name = "idx_artifacts_session", columnList = "session_id"),
                @Index(name = "idx_artifacts_type", columnList = "session_id, artifact_type"),
                @Index(name = "idx_artifacts_path", columnList = "session_id, file_path")
        })
public class ArtifactEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "session_id", length = 128, nullable = false)
    private String sessionId;

    @Column(name = "artifact_type", length = 32, nullable = false)
    private String artifactType;

    @Column(name = "title", length = 512)
    private String title;

    @Column(name = "file_path", length = 1024)
    private String filePath;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "mime_type", length = 128)
    private String mimeType;

    @Column(name = "encoding", length = 32)
    private String encoding;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "version")
    private Integer version;

    @Column(name = "is_dirty")
    private Boolean isDirty;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "modified_at")
    private Instant modifiedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}