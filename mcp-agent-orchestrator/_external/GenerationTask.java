package com.mcp.gateway.channel;

import com.mcp.common.channel.IntentType;

public record GenerationTask(
        IntentType type,
        String topic,
        String language,
        String style,
        int sectionsOrSlides
) {
    public static GenerationTask of(IntentType type, String topic) {
        return new GenerationTask(type, topic, "zh", "formal", 5);
    }

    public GenerationTask withLanguage(String language) {
        return new GenerationTask(type, topic, language, style, sectionsOrSlides);
    }
}