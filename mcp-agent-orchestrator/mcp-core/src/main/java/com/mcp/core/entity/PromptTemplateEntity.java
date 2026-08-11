package com.mcp.core.entity;

import com.mcp.core.domain.prompt.PromptType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Prompt 模板实体 — 支持版本管理与 A/B 变体。
 *
 * 主键：(name, variant, version)
 * 变体说明：
 *   - variant="default" → 默认版本
 *   - variant="a" / "b"  → A/B 测试变体
 *   - weight 越大 → 分配概率越高
 */
@Entity
@Table(name = "prompt_templates", schema = "mcp_agent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(PromptTemplateId.class)
public class PromptTemplateEntity {

    @Id
    @Column(length = 100)
    private String name;

    @Id
    @Column(length = 50)
    private String variant = "default";

    @Id
    @Column(nullable = false)
    private Integer version = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PromptType type;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String templateText;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Double weight = 1.0;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime createdAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}