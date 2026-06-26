package com.mcp.core.domain.memory;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "skill_dependencies", schema = "mcp_agent")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDependencyEntity {

    public enum DependencyType {
        PREREQUISITE,
        FOLLOWS,
        COMPOSITION,
        ALTERNATIVE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_skill_id", nullable = false)
    private Long sourceSkillId;

    @Column(name = "target_skill_id", nullable = false)
    private Long targetSkillId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dependency_type", nullable = false, length = 20)
    @Builder.Default
    private DependencyType dependencyType = DependencyType.FOLLOWS;

    @Column(name = "co_occurrence_count", nullable = false)
    @Builder.Default
    private Integer coOccurrenceCount = 1;

    @Column(name = "confidence", nullable = false)
    @Builder.Default
    private Double confidence = 0.5;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (coOccurrenceCount == null) coOccurrenceCount = 1;
        if (confidence == null) confidence = 0.5;
        if (dependencyType == null) dependencyType = DependencyType.FOLLOWS;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void incrementCoOccurrence() {
        this.coOccurrenceCount = this.coOccurrenceCount == null ? 1 : this.coOccurrenceCount + 1;
    }

    public void updateConfidence(double newConfidence) {
        this.confidence = Math.min(1.0, Math.max(0.0, newConfidence));
    }
}