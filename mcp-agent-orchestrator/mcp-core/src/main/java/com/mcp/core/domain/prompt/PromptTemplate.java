package com.mcp.core.domain.prompt;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Prompt 模板 - 领域模型
 */
public class PromptTemplate {

    private final String name;              // 模板唯一标识
    private final PromptType type;
    private final String templateText;      // 包含 {{variable}} 的模板
    private final String description;
    private final int version;
    private final LocalDateTime updatedAt;

    public PromptTemplate(String name, PromptType type, String templateText,
                          String description, int version, LocalDateTime updatedAt) {
        this.name = name;
        this.type = type;
        this.templateText = templateText;
        this.description = description;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    public String render(Map<String, Object> variables) {
        String result = templateText;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            result = result.replace(placeholder, String.valueOf(entry.getValue()));
        }
        return result;
    }

    // Getters
    public String getName() { return name; }
    public PromptType getType() { return type; }
    public String getTemplateText() { return templateText; }
    public String getDescription() { return description; }
    public int getVersion() { return version; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}