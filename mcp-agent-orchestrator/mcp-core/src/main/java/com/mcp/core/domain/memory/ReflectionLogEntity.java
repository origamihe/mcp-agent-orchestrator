package com.mcp.core.domain.memory;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "reflection_logs", schema = "mcp_agent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReflectionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64, nullable = false)
    private String sessionId;

    @Column(length = 64)
    private String userId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String userRequest;             // 用户原始请求

    @Column(columnDefinition = "TEXT", nullable = false)
    private String agentExecution;          // Agent 执行过程摘要

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> toolsUsed;         // 使用的工具列表（JSON 数组）

    @Column(nullable = false)
    private boolean taskSuccess;            // 任务是否成功

    @Column(columnDefinition = "TEXT")
    private String failureReason;           // 失败原因

    @Column(columnDefinition = "TEXT")
    private String reflection;              // Reflection 结果

    @Column(nullable = false)
    private boolean worthLearning;          // 是否值得学习

    @Column
    private Long generatedSkillId;          // 生成的 Skill ID

    @Column
    private Long generatedFailureId;        // 生成的 Failure ID

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ReflectionOutcome outcome = ReflectionOutcome.DISCARDED;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum ReflectionOutcome {
        DISCARDED,          // 不值得学习，丢弃
        SKILL_GENERATED,    // 生成了新 Skill
        SKILL_UPDATED,      // 更新了已有 Skill
        FAILURE_RECORDED,   // 记录了 Failure
        FAILURE_RESOLVED    // Failure 被解决
    }
}