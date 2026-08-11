package com.mcp.core.domain.prompt;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt 模板 - 领域模型，支持版本管理与 A/B 变体。
 */
public class PromptTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private final String name;              // 模板唯一标识
    private final String variant;           // 变体标识 (default/a/b)
    private final PromptType type;
    private final String templateText;      // 包含 {{variable}} 的模板
    private final String description;
    private final int version;
    private final double weight;            // A/B 分配权重
    private final boolean enabled;
    private final LocalDateTime updatedAt;

    public PromptTemplate(String name, String variant, PromptType type, String templateText,
                          String description, int version, double weight, boolean enabled,
                          LocalDateTime updatedAt) {
        this.name = name;
        this.variant = variant;
        this.type = type;
        this.templateText = templateText;
        this.description = description;
        this.version = version;
        this.weight = weight;
        this.enabled = enabled;
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

    public String getName() { return name; }
    public String getVariant() { return variant; }
    public PromptType getType() { return type; }
    public String getTemplateText() { return templateText; }
    public String getDescription() { return description; }
    public int getVersion() { return version; }
    public double getWeight() { return weight; }
    public boolean isEnabled() { return enabled; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}