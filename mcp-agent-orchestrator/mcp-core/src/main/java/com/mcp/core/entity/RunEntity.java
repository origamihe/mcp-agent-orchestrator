package com.mcp.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "runs", schema = "mcp_agent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RunEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(length = 100, nullable = false)
    private String agentId;

    @Column(length = 200)
    private String agentName;

    @Column(length = 64, nullable = false)
    private String sessionId;

    @Column(length = 200)
    private String intent;

    @Column(length = 20, nullable = false)
    private String status = "PENDING";

    @Column
    private Long durationMs;

    @Column
    private Integer toolCallCount = 0;

    @Column
    private Integer promptTokens = 0;

    @Column
    private Integer completionTokens = 0;

    @Column
    private Integer totalTokens = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}