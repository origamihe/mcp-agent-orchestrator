package com.mcp.core.domain.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    private Long id;
    private Long sourceSkillId;
    private Long targetSkillId;
    private DependencyType dependencyType;
    private Integer coOccurrenceCount;
    private Double confidence;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void incrementCoOccurrence() {
        this.coOccurrenceCount = this.coOccurrenceCount == null ? 1 : this.coOccurrenceCount + 1;
    }

    public void updateConfidence(double newConfidence) {
        this.confidence = Math.min(1.0, Math.max(0.0, newConfidence));
    }
}