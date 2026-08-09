package com.mcp.core.domain.llm;

/**
 * LLM 提供商类型
 */
public enum LlmProviderType {
    GOOGLE_GENAI("google"),
    OPENAI("openai"),
    DEEPSEEK("deepseek"),
    LOCAL_OLLAMA("ollama");

    private final String code;

    LlmProviderType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static LlmProviderType fromCode(String code) {
        for (LlmProviderType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown LLM provider: " + code);
    }
}