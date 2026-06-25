package com.mcp.core.entity;

import com.mcp.core.domain.memory.MemoryCategory;
import com.mcp.core.domain.memory.MemoryScope;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 压缩记忆包实体
 * 三层记忆架构：原始记录 → 压缩记忆 → 工作上下文
 */
@Entity
@Table(name = "memory_packages", schema = "mcp_agent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemoryPackageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64, nullable = false)
    private String sessionId;

    @Column(length = 64)
    private String userId;

    @Column(length = 64)
    private String groupId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private MemoryCategory category;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private MemoryScope scope = MemoryScope.USER;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private int accessCount;

    @Column(nullable = false)
    private double weight;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastAccessedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastAccessedAt = createdAt;
        accessCount = 0;
        weight = 1.0;
    }

    @PreUpdate
    protected void onUpdate() {
        lastAccessedAt = LocalDateTime.now();
    }

    public void incrementAccess() {
        this.accessCount++;
        this.lastAccessedAt = LocalDateTime.now();
    }
}