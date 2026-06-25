package com.mcp.core.domain.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 记忆条目 - 领域模型
 * 每条记忆包含：结论 + 依据 + 适用范围 + 失效条件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryEntry {
    private String userId;
    private String groupId;
    private MemoryType type;
    private String conclusion;
    private String basis;
    private String scope;
    private String expirationCondition;
    private double weight;
}