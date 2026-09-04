package com.mcp.llm.client;

import java.util.List;
import java.util.Map;

/**
 * LLM 工具调用响应 — Provider 无关的通用 DTO
 * 替代 SimpleReActAgent 中 Ollama 专属的 OllamaChatResponse / OllamaToolCall
 */
public class LlmToolResponse {

    private final String content;
    private final List<ToolCall> toolCalls;

    public LlmToolResponse(String content, List<ToolCall> toolCalls) {
        this.content = content;
        this.toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
    }

    public String getContent() {
        return content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public static class ToolCall {
        private final String id;
        private final String name;
        private final Map<String, Object> arguments;

        public ToolCall(String id, String name, Map<String, Object> arguments) {
            this.id = id != null ? id : java.util.UUID.randomUUID().toString();
            this.name = name;
            this.arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Map<String, Object> getArguments() {
            return arguments;
        }
    }
}