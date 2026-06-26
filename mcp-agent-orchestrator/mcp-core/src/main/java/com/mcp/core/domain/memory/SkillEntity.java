package com.mcp.core.domain.memory;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "skill_library", schema = "mcp_agent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200, nullable = false)
    private String name;                    // Skill 名称: "Search Latest News"

    @Column(columnDefinition = "TEXT")
    private String description;             // 描述

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String triggers;                // 触发词 JSON: ["新闻", "today", "最新"]

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String steps;                   // 执行步骤 JSON: [{"tool":"webSearch","params":{"engine":"Google"}}, ...]

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String fallbackSteps;           // 降级步骤 JSON

    @Column(nullable = false)
    private int version;                    // 版本号

    @Column(nullable = false)
    private double successRate;             // 成功率 0.0-100.0

    @Column(nullable = false)
    private int totalExecutions;            // 总执行次数

    @Column(nullable = false)
    private int successCount;               // 成功次数

    @Column(nullable = false)
    private int failureCount;               // 失败次数

    @Column
    private Long evolvedFromId;             // 从哪个 Skill 进化而来

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String dependencies;            // 依赖的 Skill ID 列表

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;                // 额外元数据

    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void recordExecution(boolean success) {
        this.totalExecutions++;
        if (success) {
            this.successCount++;
        } else {
            this.failureCount++;
        }
        this.successRate = totalExecutions > 0
                ? (double) successCount / totalExecutions * 100.0
                : 0.0;
    }

    public SkillEntity evolve(String newName, String improvedSteps) {
        return SkillEntity.builder()
                .name(newName)
                .description(this.description)
                .triggers(this.triggers)
                .steps(improvedSteps)
                .fallbackSteps(this.fallbackSteps)
                .version(this.version + 1)
                .successRate(0.0)
                .evolvedFromId(this.id)
                .dependencies(this.dependencies)
                .isActive(true)
                .build();
    }
}