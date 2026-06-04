package com.mcp.core.entity;

import com.mcp.core.domain.llm.LlmProviderType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * LLM 配置实体
 */
@Entity
@Table(name = "llm_config", schema = "mcp_agent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LlmConfigEntity {

    @Id
    @Column(length = 100)
    private String configId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private LlmProviderType provider;

    @Column(nullable = false, length = 100)
    private String modelName;

    @Column(columnDefinition = "decimal(4,2) default 0.7")
    private Double temperature = 0.7;

    @Column
    private Integer maxTokens = 2048;

    @Column(columnDefinition = "jsonb")
    private String parameters;  // JSON 字符串存储扩展参数

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}