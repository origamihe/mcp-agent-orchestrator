package com.mcp.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Prompt A/B 测试效果统计实体。
 */
@Entity
@Table(name = "prompt_ab_results", schema = "mcp_agent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PromptABResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String promptName;

    @Column(nullable = false, length = 50)
    private String variant;

    @Column
    private Long callCount = 0L;

    @Column
    private Long totalDurationMs = 0L;

    @Column
    private Long totalTokens = 0L;

    @Column
    private Long successCount = 0L;

    @Column
    private Long failureCount = 0L;

    @Column
    private Double avgRating = 0.0;

    @Column
    private Long ratingCount = 0L;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}