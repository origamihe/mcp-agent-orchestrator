package com.mcp.core.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 执行失败记录实体
 */
@Entity
@Table(name = "failure_record", schema = "mcp_agent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailureRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    private String sessionId;

    @Column(length = 100, nullable = false)
    private String toolName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String errorMessage;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}