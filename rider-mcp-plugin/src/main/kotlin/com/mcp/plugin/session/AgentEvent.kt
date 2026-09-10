package com.mcp.plugin.session

import com.google.gson.annotations.SerializedName

/**
 * Agent 执行事件模型。
 *
 * 设计原则：
 * 1. Plugin 不通过解析 Agent 文本来推断 Agent 做了什么
 * 2. 所有事件必须来源于真实执行反馈（capability_call 执行结果）
 * 3. 区分 Agent Message 和 Observed Execution Event
 * 4. 每个事件绑定 sessionId + runId，防止旧 Run 事件污染新 Run UI
 * 5. 每个事件绑定 generation，防止 reconnect 后旧 Session 事件污染新 Session
 */
sealed class AgentEvent {
    abstract val timestamp: Long
    abstract val sessionId: String
    abstract val runId: String?
    abstract val generation: Int

    data class RunStarted(
        override val sessionId: String,
        override val runId: String,
        val mode: AgentMode,
        val modelConfigId: String?,
        override val generation: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    data class RunCompleted(
        override val sessionId: String,
        override val runId: String,
        val durationMs: Long,
        override val generation: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    data class RunFailed(
        override val sessionId: String,
        override val runId: String,
        val error: String,
        override val generation: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    data class RunCancelled(
        override val sessionId: String,
        override val runId: String,
        override val generation: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    data class UserMessage(
        override val sessionId: String,
        val content: String,
        override val runId: String? = null,
        override val generation: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    data class Thinking(
        override val sessionId: String,
        override val runId: String?,
        val message: String,
        override val generation: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    data class ToolCallStarted(
        override val sessionId: String,
        override val runId: String?,
        val capability: String,
        val params: Map<String, Any?>,
        override val generation: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    data class ToolCallCompleted(
        override val sessionId: String,
        override val runId: String?,
        val capability: String,
        val success: Boolean,
        val metadata: Map<String, Any?> = emptyMap(),
        override val generation: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    data class ToolCallFailed(
        override val sessionId: String,
        override val runId: String?,
        val capability: String,
        val error: String,
        override val generation: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    data class FileRead(
        override val sessionId: String,
        override val runId: String?,
        val filePath: String,
        val lines: Int = 0,
        val bytes: Long = 0,
        val durationMs: Long = 0,
        override val generation: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    data class FileSearch(
        override val sessionId: String,
        override val runId: String?,
        val query: String,
        val matchCount: Int = 0,
        override val generation: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    data class MCPToolCall(
        override val sessionId: String,
        override val runId: String?,
        val toolName: String,
        val success: Boolean,
        val metadata: Map<String, Any?> = emptyMap(),
        override val generation: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    data class DiffCreated(
        override val sessionId: String,
        override val runId: String?,
        val filePath: String,
        override val generation: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    data class DiffApplied(
        override val sessionId: String,
        override val runId: String?,
        val filePath: String,
        val success: Boolean,
        override val generation: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    data class FinalAnswer(
        override val sessionId: String,
        override val runId: String,
        val content: String,
        override val generation: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()

    data class TokenUsage(
        override val sessionId: String,
        override val runId: String?,
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
        override val generation: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : AgentEvent()
}

/**
 * 从 Backend 接收的 Agent Event 类型。
 * 对应 Backend com.mcp.engine.trace.SessionEventType 的子集。
 */
enum class BackendEventType {
    @SerializedName("TOOL_CALL")
    TOOL_CALL,

    @SerializedName("TOOL_RESULT")
    TOOL_RESULT,

    @SerializedName("TOOL_DECISION")
    TOOL_DECISION,

    @SerializedName("LLM_CALL")
    LLM_CALL,

    @SerializedName("LLM_RESPONSE")
    LLM_RESPONSE,

    @SerializedName("AGENT_STARTED")
    AGENT_STARTED,

    @SerializedName("AGENT_ITERATION")
    AGENT_ITERATION,

    @SerializedName("EXECUTION_COMPLETED")
    EXECUTION_COMPLETED,

    @SerializedName("FINAL_RESPONSE")
    FINAL_RESPONSE
}