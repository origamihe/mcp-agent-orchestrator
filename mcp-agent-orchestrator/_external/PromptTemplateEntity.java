package com.mcp.core.entity;

import com.mcp.core.domain.prompt.PromptType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Prompt 模板实体
 */
@Entity
@Table(name = "prompt_templates", schema = "mcp_agent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PromptTemplateEntity {

    @Id
    @Column(length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PromptType type;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String templateText;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}