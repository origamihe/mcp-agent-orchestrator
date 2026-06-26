package com.mcp.core.domain.memory;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "failure_library", schema = "mcp_agent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailureEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200, nullable = false)
    private String taskPattern;             // 任务模式: "Read PDF"

    @Column(columnDefinition = "TEXT", nullable = false)
    private String errorSignature;          // 错误签名: "MalformedInputException"

    @Column(columnDefinition = "TEXT", nullable = false)
    private String rootCause;               // 根因: "Used Files.readString() on binary PDF"

    @Column(columnDefinition = "TEXT", nullable = false)
    private String correctApproach;         // 正确做法: "Use PDFBox library instead"

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String contextSnapshot;         // 失败时的上下文快照

    @Column(nullable = false)
    private int occurrenceCount;            // 发生次数

    @Column(nullable = false)
    private LocalDateTime firstOccurredAt;

    @Column(nullable = false)
    private LocalDateTime lastOccurredAt;

    @Column(nullable = false)
    private boolean isResolved;             // 是否已解决

    @Column
    private Long resolvedBySkillId;         // 由哪个 Skill 解决

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (firstOccurredAt == null) firstOccurredAt = createdAt;
        if (lastOccurredAt == null) lastOccurredAt = createdAt;
    }

    public void recordOccurrence() {
        this.occurrenceCount++;
        this.lastOccurredAt = LocalDateTime.now();
    }
}