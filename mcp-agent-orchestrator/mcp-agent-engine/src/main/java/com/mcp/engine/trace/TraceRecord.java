package com.mcp.engine.trace;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Trace 记录 — 每次 Agent 调用的完整运行时追踪数据。
 *
 * 涵盖用户共识中要求的六个维度：
 * - Latency (elapsedMs)
 * - Token (promptTokens, completionTokens, totalTokens)
 * - Memory (memoryContext)
 * - Prompt (systemPrompt, userMessage, renderedPrompt)
 * - Tool (toolCalls)
 * - Reasoning (llmOutput)
 */
public record TraceRecord(
        String traceId,
        String sessionId,
        String userId,
        String modelName,
        LocalDateTime startTime,
        long elapsedMs,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        String systemPrompt,
        String userMessage,
        String renderedPrompt,
        String llmOutput,
        List<ToolCallRecord> toolCalls,
        String memoryContext,
        String workspaceState,
        int layerCount,
        String version,
        LocalDateTime timestamp
) {

    public static TraceRecordBuilder builder() {
        return new TraceRecordBuilder();
    }

    public static class TraceRecordBuilder {
        private String traceId = UUID.randomUUID().toString();
        private String sessionId;
        private String userId;
        private String modelName;
        private LocalDateTime startTime = LocalDateTime.now();
        private long elapsedMs;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private String systemPrompt;
        private String userMessage;
        private String renderedPrompt;
        private String llmOutput;
        private List<ToolCallRecord> toolCalls = List.of();
        private String memoryContext;
        private String workspaceState;
        private int layerCount;
        private String version = "0.0.1-SNAPSHOT";
        private LocalDateTime timestamp = LocalDateTime.now();

        public TraceRecordBuilder traceId(String traceId) { this.traceId = traceId; return this; }
        public TraceRecordBuilder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public TraceRecordBuilder userId(String userId) { this.userId = userId; return this; }
        public TraceRecordBuilder modelName(String modelName) { this.modelName = modelName; return this; }
        public TraceRecordBuilder startTime(LocalDateTime startTime) { this.startTime = startTime; return this; }
        public TraceRecordBuilder elapsedMs(long elapsedMs) { this.elapsedMs = elapsedMs; return this; }
        public TraceRecordBuilder promptTokens(int promptTokens) { this.promptTokens = promptTokens; return this; }
        public TraceRecordBuilder completionTokens(int completionTokens) { this.completionTokens = completionTokens; return this; }
        public TraceRecordBuilder totalTokens(int totalTokens) { this.totalTokens = totalTokens; return this; }
        public TraceRecordBuilder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public TraceRecordBuilder userMessage(String userMessage) { this.userMessage = userMessage; return this; }
        public TraceRecordBuilder renderedPrompt(String renderedPrompt) { this.renderedPrompt = renderedPrompt; return this; }
        public TraceRecordBuilder llmOutput(String llmOutput) { this.llmOutput = llmOutput; return this; }
        public TraceRecordBuilder toolCalls(List<ToolCallRecord> toolCalls) { this.toolCalls = toolCalls; return this; }
        public TraceRecordBuilder memoryContext(String memoryContext) { this.memoryContext = memoryContext; return this; }
        public TraceRecordBuilder workspaceState(String workspaceState) { this.workspaceState = workspaceState; return this; }
        public TraceRecordBuilder layerCount(int layerCount) { this.layerCount = layerCount; return this; }
        public TraceRecordBuilder version(String version) { this.version = version; return this; }
        public TraceRecordBuilder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }

        public TraceRecord build() {
            return new TraceRecord(traceId, sessionId, userId, modelName,
                    startTime, elapsedMs, promptTokens, completionTokens, totalTokens,
                    systemPrompt, userMessage, renderedPrompt, llmOutput, toolCalls,
                    memoryContext, workspaceState, layerCount, version, timestamp);
        }
    }

    /**
     * 单次工具调用的追踪记录。
     */
    public record ToolCallRecord(
            String toolName,
            String arguments,
            boolean success,
            String output,
            long durationMs
    ) {}
}