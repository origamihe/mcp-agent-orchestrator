package com.mcp.core.domain.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化记忆包 - 按分类组织的记忆集合
 * 遵循用户推荐的 JSON 结构格式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuredMemory {
    @Builder.Default
    private List<MemoryEntry> userPreferences = new ArrayList<>();
    @Builder.Default
    private List<MemoryEntry> projectContext = new ArrayList<>();
    @Builder.Default
    private List<MemoryEntry> openTasks = new ArrayList<>();
    @Builder.Default
    private List<MemoryEntry> confirmedFacts = new ArrayList<>();
    @Builder.Default
    private List<MemoryEntry> decisionHistory = new ArrayList<>();
    @Builder.Default
    private List<MemoryEntry> importantConstraints = new ArrayList<>();

    /**
     * 转换为便于 LLM 读取的 Markdown 格式
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 长期记忆摘要\n\n");

        if (!userPreferences.isEmpty()) {
            sb.append("## 用户偏好\n");
            for (MemoryEntry entry : userPreferences) {
                sb.append("- **").append(entry.getConclusion()).append("**");
                if (entry.getBasis() != null && !entry.getBasis().isEmpty()) {
                    sb.append("\n  - 依据：").append(entry.getBasis());
                }
                if (entry.getScope() != null && !entry.getScope().isEmpty()) {
                    sb.append("\n  - 适用：").append(entry.getScope());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (!projectContext.isEmpty()) {
            sb.append("## 项目背景\n");
            for (MemoryEntry entry : projectContext) {
                sb.append("- **").append(entry.getConclusion()).append("**");
                if (entry.getBasis() != null && !entry.getBasis().isEmpty()) {
                    sb.append("\n  - 依据：").append(entry.getBasis());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (!confirmedFacts.isEmpty()) {
            sb.append("## 已确定事实\n");
            for (MemoryEntry entry : confirmedFacts) {
                sb.append("- **").append(entry.getConclusion()).append("**\n");
            }
            sb.append("\n");
        }

        if (!openTasks.isEmpty()) {
            sb.append("## 待办事项\n");
            for (MemoryEntry entry : openTasks) {
                sb.append("- ").append(entry.getConclusion()).append("\n");
            }
            sb.append("\n");
        }

        if (!importantConstraints.isEmpty()) {
            sb.append("## 约束条件\n");
            for (MemoryEntry entry : importantConstraints) {
                sb.append("- **").append(entry.getConclusion()).append("**");
                if (entry.getScope() != null && !entry.getScope().isEmpty()) {
                    sb.append("（").append(entry.getScope()).append("）");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (!decisionHistory.isEmpty()) {
            sb.append("## 决策历史\n");
            for (MemoryEntry entry : decisionHistory) {
                sb.append("- **结论**：").append(entry.getConclusion()).append("\n");
                if (entry.getBasis() != null && !entry.getBasis().isEmpty()) {
                    sb.append("  - 原因：").append(entry.getBasis()).append("\n");
                }
            }
        }

        return sb.toString().trim();
    }

    /**
     * 计算总 token 估算
     */
    public int estimatedTokens() {
        int total = 0;
        total += countTokens(userPreferences);
        total += countTokens(projectContext);
        total += countTokens(confirmedFacts);
        total += countTokens(openTasks);
        total += countTokens(decisionHistory);
        total += countTokens(importantConstraints);
        return total;
    }

    private int countTokens(List<MemoryEntry> entries) {
        return entries.stream()
                .mapToInt(e -> (e.getConclusion() + (e.getBasis() != null ? e.getBasis() : "")).length() / 4)
                .sum();
    }
}