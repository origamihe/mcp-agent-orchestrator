package com.mcp.core.domain.run;

import java.time.LocalDateTime;

public class Run {

    private final String id;
    private final String agentId;
    private String agentName;
    private final String sessionId;
    private String intent;
    private RunStatus status;
    private Long durationMs;
    private int toolCallCount;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private final LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public Run(String id, String agentId, String sessionId) {
        this.id = id;
        this.agentId = agentId;
        this.sessionId = sessionId;
        this.status = RunStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void start() {
        this.status = RunStatus.RUNNING;
    }

    public void complete() {
        this.status = RunStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = RunStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = RunStatus.CANCELLED;
        this.completedAt = LocalDateTime.now();
    }

    public void addToolCall() { this.toolCallCount++; }

    public void addTokenUsage(int prompt, int completion) {
        this.promptTokens += prompt;
        this.completionTokens += completion;
        this.totalTokens = this.promptTokens + this.completionTokens;
    }

    // Getters
    public String getId() { return id; }
    public String getAgentId() { return agentId; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getSessionId() { return sessionId; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public RunStatus getStatus() { return status; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public int getToolCallCount() { return toolCallCount; }
    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}