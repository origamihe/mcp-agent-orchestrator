package com.mcp.core.entity;

import com.mcp.core.domain.memory.MemoryCategory;
import com.mcp.core.domain.memory.MemoryScope;
import com.mcp.core.domain.memory.MemoryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

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

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private MemoryType memoryType = MemoryType.FACT;

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

    @Column(nullable = false)
    private int importance = 50;

    @Column(nullable = false)
    private int confidence = 50;

    @Column(nullable = false)
    private int upgradeCount = 0;

    @Column(nullable = false)
    private double decayRate = 1.0;

    @Column
    private LocalDateTime ttl;

    @Column(columnDefinition = "TEXT")
    private String sourceQuote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String mergedFromIds;

    @Column
    private Long supersededById;

    @Column(nullable = false)
    private boolean isActive = true;

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
        if (memoryType == null) memoryType = MemoryType.FACT;
        if (decayRate == 0) decayRate = 1.0;
    }

    @PreUpdate
    protected void onUpdate() {
        lastAccessedAt = LocalDateTime.now();
    }

    public void incrementAccess() {
        this.accessCount++;
        this.lastAccessedAt = LocalDateTime.now();
        this.weight = Math.min(100, this.weight + 0.5);
    }

    public void applyDecay() {
        this.weight = Math.max(0, this.weight * this.decayRate);
        if (this.weight < 20) {
            this.isActive = false;
        }
    }

    public void boostWeight(double amount) {
        this.weight = Math.min(100, this.weight + amount);
        this.isActive = true;
    }

    public boolean shouldBeCollected() {
        if (isPermanent()) return false;
        if (ttl != null && LocalDateTime.now().isAfter(ttl)) return true;
        return !isActive
                || importance < 30
                || (weight < 20 && accessCount < 3);
    }

    public boolean isPermanent() {
        return memoryType != null
                && memoryType.getLifecycle() == MemoryType.Lifecycle.PERMANENT;
    }

    public boolean isUpgradable() {
        return upgradeCount >= 3
                && memoryType != null
                && memoryType.getLifecycle() != MemoryType.Lifecycle.PERMANENT;
    }

    public void recordUpgrade() {
        this.upgradeCount++;
        if (isUpgradable()) {
            this.memoryType = upgradeMemoryType(this.memoryType);
            this.decayRate = 1.0;
            this.ttl = null;
            this.importance = Math.min(100, this.importance + 20);
            this.upgradeCount = 0;
        }
    }

    private MemoryType upgradeMemoryType(MemoryType current) {
        return switch (current) {
            case TEMPORARY -> MemoryType.EVENT;
            case EVENT -> MemoryType.FACT;
            case FACT -> MemoryType.PREFERENCE;
            case PREFERENCE, HABIT -> MemoryType.PROFILE;
            case GOAL, PROJECT -> MemoryType.PROFILE;
            default -> current;
        };
    }

    public boolean isExpired() {
        return ttl != null && LocalDateTime.now().isAfter(ttl);
    }

    public int getDaysSinceLastAccess() {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(lastAccessedAt, LocalDateTime.now());
    }
}