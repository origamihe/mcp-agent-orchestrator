package com.mcp.plugin.session

data class RunSummary(
    val runId: String,
    val sessionId: String,
    val mode: String,
    val model: String,
    val userMessage: String,
    val status: String,
    val startTime: Long,
    val endTime: Long,
    val generation: Int,
    val toolCallCount: Int,
    val fileChanges: List<String>,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0
) {
    val durationMs: Long get() = if (endTime > 0) endTime - startTime else 0L

    val totalTokens: Int get() = promptTokens + completionTokens

    val isActive: Boolean get() = status == "RUNNING"

    val isCompleted: Boolean get() = status == "COMPLETED"

    val isFailed: Boolean get() = status == "FAILED"

    val isCancelled: Boolean get() = status == "CANCELLED"

    companion object {
        fun from(session: AgentSession, startTime: Long, endTime: Long, status: String): RunSummary {
            return RunSummary(
                runId = session.currentRunId ?: "unknown",
                sessionId = session.sessionId,
                mode = session.mode.name,
                model = session.modelConfigId ?: "default",
                userMessage = session.lastUserMessage ?: "",
                status = status,
                startTime = startTime,
                endTime = endTime,
                generation = session.generation,
                toolCallCount = session.toolCallCount,
                fileChanges = session.fileChanges.toList(),
                promptTokens = session.totalPromptTokens,
                completionTokens = session.totalCompletionTokens
            )
        }
    }
}