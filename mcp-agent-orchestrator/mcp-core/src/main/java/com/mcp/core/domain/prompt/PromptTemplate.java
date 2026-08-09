package com.mcp.core.domain.prompt;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt 模板 - 领域模型
 */
public class PromptTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

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
        Matcher matcher = PLACEHOLDER.matcher(templateText);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = variables.get(key);
            String replacement = value != null ? String.valueOf(value) : matcher.group(0);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // Getters
    public String getName() { return name; }
    public PromptType getType() { return type; }
    public String getTemplateText() { return templateText; }
    public String getDescription() { return description; }
    public int getVersion() { return version; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}