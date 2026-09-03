package com.mcp.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_documents", schema = "mcp_agent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(length = 64, nullable = false)
    private String collectionId;

    @Column(length = 500, nullable = false)
    private String title;

    @Column(length = 1000)
    private String source;

    @Column(length = 50, nullable = false)
    private String format = "text";

    @Column
    private Long size = 0L;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}