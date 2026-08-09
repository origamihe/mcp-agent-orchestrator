package com.mcp.engine.test.benchmark;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkResult {

    private String taskId;
    private String taskName;
    private String category;
    private String prompt;
    private String systemPrompt;
    private String llmOutput;
    private List<ToolCallRecord> toolCalls;
    private String memoryUsed;
    private String workspaceState;
    private long elapsedTimeMs;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private boolean success;
    private String errorMessage;
    private String modelName;
    private LocalDateTime executedAt;
    private String version;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallRecord {
        private String toolName;
        private String arguments;
        private boolean success;
        private String output;
        private long durationMs;
    }
}