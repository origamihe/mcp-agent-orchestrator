package com.mcp.plugin.session

import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Agent 会话 — 管理单个 IDE Project 内的 Agent 交互状态。
 *
 * 职责：
 * 1. 维护 sessionId、mode、model、connectionState、agentState
 * 2. 管理当前 Run 和事件流
 * 3. 提供事件历史查询（按 runId 隔离）
 * 4. 会话版本管理（reconnect 时防止旧事件污染新 Session）
 *
 * 生命周期：与 Project 绑定，Project 关闭时销毁。
 */
class AgentSession {

    val sessionId: String = "rider-session-${UUID.randomUUID().toString().take(8)}"

    @Volatile
    var mode: AgentMode = AgentMode.CHAT

    @Volatile
    var modelConfigId: String? = null

    @Volatile
    var connectionState: ConnectionState = ConnectionState.DISCONNECTED

    @Volatile
    var agentState: AgentState = AgentState.IDLE

    @Volatile
    var currentRunId: String? = null
        private set

    @Volatile
    var currentTaskId: String? = null

    @Volatile
    var generation: Int = 0
        private set

    private val events = ConcurrentLinkedQueue<AgentEvent>()
    private val eventListeners = ConcurrentLinkedQueue<(AgentEvent) -> Unit>()
    private val stateListeners = ConcurrentLinkedQueue<(AgentState, AgentState) -> Unit>()

    private val maxEvents = 500

    fun startRun(mode: AgentMode, modelConfigId: String?): String {
        val runId = "run-${UUID.randomUUID().toString().take(8)}"
        this.currentRunId = runId
        this.mode = mode
        this.modelConfigId = modelConfigId
        transitionAgentState(AgentState.CREATED)
        addEvent(AgentEvent.RunStarted(sessionId, runId, mode, modelConfigId, generation))
        transitionAgentState(AgentState.RUNNING)
        return runId
    }

    fun completeRun(durationMs: Long = 0) {
        val runId = currentRunId ?: return
        addEvent(AgentEvent.RunCompleted(sessionId, runId, durationMs, generation))
        currentRunId = null
        transitionAgentState(AgentState.COMPLETED)
        transitionAgentState(AgentState.IDLE)
    }

    fun failRun(error: String) {
        val runId = currentRunId ?: return
        addEvent(AgentEvent.RunFailed(sessionId, runId, error, generation))
        currentRunId = null
        transitionAgentState(AgentState.FAILED)
        transitionAgentState(AgentState.IDLE)
    }

    fun cancelRun() {
        val runId = currentRunId ?: return
        transitionAgentState(AgentState.CANCELLING)
        addEvent(AgentEvent.Thinking(sessionId, runId, "Cancelling run $runId...", generation))
    }

    fun confirmCancelled() {
        val runId = currentRunId ?: return
        addEvent(AgentEvent.RunCancelled(sessionId, runId, generation))
        currentRunId = null
        transitionAgentState(AgentState.CANCELLED)
        transitionAgentState(AgentState.IDLE)
    }

    fun addUserMessage(content: String) {
        addEvent(AgentEvent.UserMessage(sessionId, content, currentRunId, generation))
    }

    fun addToolCallStarted(capability: String, params: Map<String, Any?>) {
        addEvent(AgentEvent.ToolCallStarted(sessionId, currentRunId, capability, params, generation))
    }

    fun addToolCallCompleted(capability: String, success: Boolean, metadata: Map<String, Any?> = emptyMap()) {
        addEvent(AgentEvent.ToolCallCompleted(sessionId, currentRunId, capability, success, metadata, generation))
    }

    fun addToolCallFailed(capability: String, error: String) {
        addEvent(AgentEvent.ToolCallFailed(sessionId, currentRunId, capability, error, generation))
    }

    fun addFileRead(filePath: String, lines: Int = 0, bytes: Long = 0, durationMs: Long = 0) {
        addEvent(AgentEvent.FileRead(sessionId, currentRunId, filePath, lines, bytes, durationMs, generation))
    }

    fun addFileSearch(query: String, matchCount: Int = 0) {
        addEvent(AgentEvent.FileSearch(sessionId, currentRunId, query, matchCount, generation))
    }

    fun addMCPToolCall(toolName: String, success: Boolean, metadata: Map<String, Any?> = emptyMap()) {
        addEvent(AgentEvent.MCPToolCall(sessionId, currentRunId, toolName, success, metadata, generation))
    }

    fun addFinalAnswer(content: String, runId: String) {
        addEvent(AgentEvent.FinalAnswer(sessionId, runId, content, generation))
    }

    fun addEvent(event: AgentEvent) {
        events.add(event)
        while (events.size > maxEvents) {
            events.poll()
        }
        eventListeners.forEach { it(event) }
    }

    fun getEvents(): List<AgentEvent> = events.toList()

    fun getEventsForGeneration(gen: Int): List<AgentEvent> =
        events.filter { it.generation == gen }

    fun getEventsForRun(runId: String): List<AgentEvent> =
        events.filter { event ->
            when (event) {
                is AgentEvent.RunStarted -> event.runId == runId
                is AgentEvent.RunCompleted -> event.runId == runId
                is AgentEvent.RunFailed -> event.runId == runId
                is AgentEvent.RunCancelled -> event.runId == runId
                is AgentEvent.FinalAnswer -> event.runId == runId
                is AgentEvent.UserMessage -> event.runId == null || event.runId == runId
                else -> event.runId == runId
            }
        }.takeLast(200)

    fun onEvent(listener: (AgentEvent) -> Unit) {
        eventListeners.add(listener)
    }

    fun onStateChange(listener: (AgentState, AgentState) -> Unit) {
        stateListeners.add(listener)
    }

    fun incrementGeneration() {
        generation++
    }

    private fun transitionAgentState(newState: AgentState) {
        val oldState = agentState
        agentState = newState
        if (oldState != newState) {
            stateListeners.forEach { it(oldState, newState) }
        }
    }

    fun clear() {
        events.clear()
        eventListeners.clear()
        stateListeners.clear()
        currentRunId = null
        currentTaskId = null
        agentState = AgentState.IDLE
        connectionState = ConnectionState.DISCONNECTED
        generation = 0
    }
}

enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    RECONNECTING
}

enum class AgentState {
    IDLE,
    CREATED,
    RUNNING,
    CANCELLING,
    COMPLETED,
    FAILED,
    CANCELLED
}