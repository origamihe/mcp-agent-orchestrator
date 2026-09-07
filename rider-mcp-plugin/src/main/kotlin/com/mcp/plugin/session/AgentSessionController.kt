package com.mcp.plugin.session

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.mcp.plugin.McpPluginSettings
import com.mcp.plugin.capability.CapabilityAdapter
import com.mcp.plugin.event.OutgoingEnvelope
import com.mcp.plugin.transport.Transport
import com.mcp.plugin.transport.WebSocketTransport
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import javax.swing.SwingUtilities

/**
 * Agent 会话控制器 — UI 与 Transport/Capability 之间的中间层。
 *
 * 职责：
 * 1. startSession() / sendMessage() / cancelRun() / changeMode() / changeModel()
 * 2. 处理 Agent 事件，推送到 AgentSession
 * 3. 协调 Capability 执行（在后台线程）
 * 4. 模型列表获取（从 Backend REST API）
 * 5. 管理 cancel_run 协议与 Backend 通信
 * 6. 处理 WebSocket disconnect 时的 Run 状态
 * 7. 过滤 stale events（generation 不匹配）
 *
 * UI 只负责 render() 和 user input。
 */
@Service(Service.Level.PROJECT)
class AgentSessionController(private val project: Project) {

    private val logger = Logger.getInstance(AgentSessionController::class.java)
    private val transport: Transport? = project.getService(WebSocketTransport::class.java)
    private val capabilityAdapter = project.getService(CapabilityAdapter::class.java)
    private val settings = ApplicationManager.getApplication().getService(McpPluginSettings::class.java) ?: McpPluginSettings()

    val session = AgentSession()

    private val gson = Gson()
    private val httpClient = HttpClient.newBuilder().build()
    private val backgroundExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "agent-session-controller").apply { isDaemon = true }
    }

    private val modelListListeners = mutableListOf<(List<ModelInfo>) -> Unit>()

    @Volatile
    private var availableModels: List<ModelInfo> = emptyList()

    @Volatile
    private var activeGeneration: Int = 0

    fun init() {
        transport?.onConnectionChange { connected ->
            val wasDisconnected = session.connectionState == ConnectionState.DISCONNECTED
            session.connectionState = if (connected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED

            if (connected) {
                if (wasDisconnected) {
                    session.incrementGeneration()
                    activeGeneration = session.generation
                    logger.info("[AgentSessionController] Reconnected, new generation=${session.generation}")
                }
                fetchModels()
            } else {
                handleDisconnect()
            }
        }
    }

    fun startSession() {
        session.connectionState = ConnectionState.CONNECTING
        transport?.connect()
    }

    fun sendMessage(text: String): String {
        val runId = session.startRun(session.mode, session.modelConfigId)
        session.addUserMessage(text)

        backgroundExecutor.submit {
            val t = transport ?: return@submit
            val hostContext = getEditorContext()

            val envelope = OutgoingEnvelope(
                type = "chat",
                sessionId = t.sessionId,
                userId = System.getProperty("user.name"),
                workspaceId = "workspace-${project.name}",
                content = text,
                hostContext = hostContext,
                mode = session.mode.toBackendMode(),
                model = session.modelConfigId
            )
            t.send(envelope)
        }
        return runId
    }

    fun sendChat(text: String, onRunStarted: (String) -> Unit) {
        val runId = session.startRun(session.mode, session.modelConfigId)
        session.addUserMessage(text)

        SwingUtilities.invokeLater { onRunStarted(runId) }

        backgroundExecutor.submit {
            val t = transport ?: return@submit
            val hostContext = getEditorContext()

            val envelope = OutgoingEnvelope(
                type = "chat",
                sessionId = t.sessionId,
                userId = System.getProperty("user.name"),
                workspaceId = "workspace-${project.name}",
                content = text,
                hostContext = hostContext,
                mode = session.mode.toBackendMode(),
                model = session.modelConfigId
            )
            t.send(envelope)
        }
    }

    fun sendMessageWithMode(text: String, mode: AgentMode, modelConfigId: String?): String {
        val runId = session.startRun(mode, modelConfigId)
        session.addUserMessage(text)

        backgroundExecutor.submit {
            val t = transport ?: return@submit
            val hostContext = getEditorContext()

            val envelope = OutgoingEnvelope(
                type = "chat",
                sessionId = t.sessionId,
                userId = System.getProperty("user.name"),
                workspaceId = "workspace-${project.name}",
                content = text,
                hostContext = hostContext,
                mode = mode.toBackendMode(),
                model = modelConfigId
            )

            t.send(envelope)
        }
        return runId
    }

    fun cancelRun() {
        val runId = session.currentRunId ?: return
        session.cancelRun()

        val t = transport
        if (t != null && t.isConnected) {
            t.send(OutgoingEnvelope(
                type = "cancel_run",
                sessionId = t.sessionId,
                workspaceId = "workspace-${project.name}",
                runId = runId
            ))
            logger.info("[AgentSessionController] Sent cancel_run for runId=$runId")
        } else {
            session.confirmCancelled()
            logger.info("[AgentSessionController] Transport disconnected, confirmed cancelled locally for runId=$runId")
        }
    }

    /**
     * 处理 WebSocket 断开连接。
     * 如果有活跃的 Run，自动取消它。
     */
    private fun handleDisconnect() {
        val runId = session.currentRunId
        if (runId != null && session.agentState == AgentState.RUNNING) {
            logger.warn("[AgentSessionController] Disconnect during active run $runId, auto-cancelling")
            session.cancelRun()
            session.confirmCancelled()
        }
        session.connectionState = ConnectionState.DISCONNECTED
    }

    /**
     * 检查事件是否属于当前 generation，过滤 stale events。
     */
    fun isStaleEvent(generation: Int): Boolean {
        return generation != activeGeneration && generation != 0
    }

    fun changeMode(mode: AgentMode) {
        session.mode = mode
        settings.agentMode = mode.name
    }

    fun changeModel(modelConfigId: String?) {
        session.modelConfigId = modelConfigId
        if (modelConfigId != null) {
            settings.agentModel = modelConfigId
        }
    }

    /**
     * 处理从 Transport 收到的消息。
     * 在后台线程执行 Capability，通过回调将结果推送回 UI。
     *
     * 事件来源保证：
     * - capability_call → CapabilityAdapter.execute() → capability_result
     *   只有真实执行才会产生 ToolCallStarted/Completed/Failed 事件
     * - agent_event → Backend 的 Agent 内部事件（MCP Tool Call 等）
     * - reply → Agent 最终回复
     *
     * Stale event 过滤：
     * - 如果事件 generation 与当前 activeGeneration 不匹配，丢弃该事件
     * - generation=0 的事件（首次连接前的）总是允许通过
     */
    fun handleMessage(json: String, onUiUpdate: (AgentEvent) -> Unit) {
        backgroundExecutor.submit {
            try {
                val msg = gson.fromJson(json, Map::class.java) as? Map<String, Any?> ?: return@submit
                val type = msg["type"] as? String ?: return@submit
                val sessionId = session.sessionId
                val runId = session.currentRunId
                val currentGen = activeGeneration

                when (type) {
                    "capability_call" -> {
                        val callId = msg["callId"] as? String ?: return@submit
                        val capability = msg["capability"] as? String ?: return@submit
                        @Suppress("UNCHECKED_CAST")
                        val params = (msg["params"] as? Map<String, Any?>) ?: emptyMap()

                        val startTime = System.currentTimeMillis()
                        session.addToolCallStarted(capability, params)

                        SwingUtilities.invokeLater {
                            onUiUpdate(AgentEvent.ToolCallStarted(sessionId, runId, capability, params))
                        }

                        val result = capabilityAdapter.execute(capability, params)
                        val durationMs = System.currentTimeMillis() - startTime

                        val success = result["error"] == null
                        val metadata = mutableMapOf<String, Any?>(
                            "durationMs" to durationMs
                        )

                        when (capability) {
                            "read_file" -> {
                                val filePath = params["filePath"] as? String ?: ""
                                val lines = (result["content"] as? String)?.lines()?.size ?: 0
                                metadata["filePath"] = filePath
                                metadata["lines"] = lines
                                session.addFileRead(filePath, lines, 0, durationMs)

                                SwingUtilities.invokeLater {
                                    onUiUpdate(AgentEvent.FileRead(sessionId, runId, filePath, lines, 0, durationMs))
                                }
                            }
                            "search_files" -> {
                                val pattern = params["pattern"] as? String ?: ""
                                @Suppress("UNCHECKED_CAST")
                                val matches = (result["matches"] as? List<String>) ?: emptyList()
                                session.addFileSearch(pattern, matches.size)

                                SwingUtilities.invokeLater {
                                    onUiUpdate(AgentEvent.FileSearch(sessionId, runId, pattern, matches.size))
                                }
                            }
                            "apply_diff" -> {
                                val filePath = params["filePath"] as? String ?: ""
                                session.addEvent(AgentEvent.DiffApplied(sessionId, runId, filePath, success))
                                SwingUtilities.invokeLater {
                                    onUiUpdate(AgentEvent.DiffApplied(sessionId, runId, filePath, success))
                                }
                            }
                            "apply_full_content" -> {
                                val filePath = params["filePath"] as? String ?: ""
                                session.addEvent(AgentEvent.DiffApplied(sessionId, runId, filePath, success))
                                SwingUtilities.invokeLater {
                                    onUiUpdate(AgentEvent.DiffApplied(sessionId, runId, filePath, success))
                                }
                            }
                        }

                        if (success) {
                            session.addToolCallCompleted(capability, true, metadata)
                            SwingUtilities.invokeLater {
                                onUiUpdate(AgentEvent.ToolCallCompleted(sessionId, runId, capability, true, metadata))
                            }
                        } else {
                            val error = result["error"] as? String ?: "Unknown error"
                            session.addToolCallFailed(capability, error)
                            SwingUtilities.invokeLater {
                                onUiUpdate(AgentEvent.ToolCallFailed(sessionId, runId, capability, error))
                            }
                        }

                        val t = transport ?: return@submit
                        t.send(OutgoingEnvelope(
                            type = "capability_result",
                            sessionId = t.sessionId,
                            callId = callId,
                            capability = capability,
                            result = result
                        ))
                    }

                    "reply" -> {
                        val content = msg["content"] as? String ?: ""
                        val replyRunId = runId ?: "unknown"
                        val replyGen = (msg["generation"] as? Number)?.toInt() ?: 0

                        if (isStaleEvent(replyGen)) {
                            logger.warn("[AgentSessionController] Stale reply ignored (gen=$replyGen, current=$currentGen)")
                            return@submit
                        }

                        session.addFinalAnswer(content, replyRunId)
                        session.completeRun()

                        SwingUtilities.invokeLater {
                            onUiUpdate(AgentEvent.FinalAnswer(sessionId, replyRunId, content, currentGen))
                        }
                    }

                    "agent_event" -> {
                        val eventType = msg["eventType"] as? String
                        val eventRunId = msg["runId"] as? String ?: runId
                        val eventGen = (msg["generation"] as? Number)?.toInt() ?: 0

                        if (isStaleEvent(eventGen)) {
                            logger.warn("[AgentSessionController] Stale agent_event ignored (type=$eventType, gen=$eventGen, current=$currentGen)")
                            return@submit
                        }

                        when (eventType) {
                            "TOOL_CALL" -> {
                                @Suppress("UNCHECKED_CAST")
                                val payload = msg["payload"] as? Map<String, Any?>
                                val toolName = payload?.get("toolName") as? String ?: "unknown"
                                session.addMCPToolCall(toolName, false, payload ?: emptyMap())
                                SwingUtilities.invokeLater {
                                    onUiUpdate(AgentEvent.MCPToolCall(sessionId, eventRunId, toolName, false, payload ?: emptyMap(), currentGen))
                                }
                            }
                            "TOOL_RESULT" -> {
                                @Suppress("UNCHECKED_CAST")
                                val payload = msg["payload"] as? Map<String, Any?>
                                val toolName = payload?.get("toolName") as? String ?: "unknown"
                                val success = payload?.get("success") as? Boolean ?: true
                                session.addMCPToolCall(toolName, success, payload ?: emptyMap())
                                SwingUtilities.invokeLater {
                                    onUiUpdate(AgentEvent.MCPToolCall(sessionId, eventRunId, toolName, success, payload ?: emptyMap(), currentGen))
                                }
                            }
                            "TOOL_DECISION" -> {
                                @Suppress("UNCHECKED_CAST")
                                val payload = msg["payload"] as? Map<String, Any?>
                                val decision = payload?.get("decision") as? String ?: ""
                                session.addEvent(AgentEvent.Thinking(sessionId, eventRunId, "Decision: $decision", currentGen))
                                SwingUtilities.invokeLater {
                                    onUiUpdate(AgentEvent.Thinking(sessionId, eventRunId, "Decision: $decision", currentGen))
                                }
                            }
                            "AGENT_STARTED" -> {
                                if (eventRunId != null && session.currentRunId == null) {
                                    session.startRun(session.mode, session.modelConfigId)
                                }
                                SwingUtilities.invokeLater {
                                    onUiUpdate(AgentEvent.Thinking(sessionId, eventRunId, "Agent started", currentGen))
                                }
                            }
                            "EXECUTION_COMPLETED" -> {
                                session.completeRun()
                                SwingUtilities.invokeLater {
                                    onUiUpdate(AgentEvent.RunCompleted(sessionId, eventRunId ?: "unknown", 0, currentGen))
                                }
                            }
                            else -> {
                                logger.debug("[AgentSessionController] Unhandled agent_event type: $eventType")
                            }
                        }
                    }

                    "cancel_run_ack" -> {
                        val ackRunId = msg["runId"] as? String ?: runId
                        logger.info("[AgentSessionController] Backend acknowledged cancel for runId=$ackRunId")
                        session.confirmCancelled()
                        SwingUtilities.invokeLater {
                            onUiUpdate(AgentEvent.RunCancelled(sessionId, ackRunId ?: "unknown", currentGen))
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("[AgentSessionController] Error handling message: ${e.message}")
            }
        }
    }

    fun getEditorContext(): Map<String, Any?> {
        return capabilityAdapter.execute("get_editor_state", emptyMap())
    }

    fun fetchModels() {
        backgroundExecutor.submit {
            try {
                val baseUrl = settings.gatewayUrl
                    .replace("ws://", "http://")
                    .replace("wss://", "https://")
                    .replace("/ws/host", "")
                    .trimEnd('/')

                val request = HttpRequest.newBuilder()
                    .uri(URI.create("$baseUrl/api/llm/configs"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() == 200) {
                    val type = object : TypeToken<List<ModelInfo>>() {}.type
                    val models: List<ModelInfo> = gson.fromJson(response.body(), type)
                    availableModels = models.filter { it.enabled }
                    val enabledModels = availableModels
                    SwingUtilities.invokeLater {
                        modelListListeners.forEach { it(enabledModels) }
                    }
                } else {
                    logger.warn("[AgentSessionController] Failed to fetch models: HTTP ${response.statusCode()}")
                }
            } catch (e: Exception) {
                logger.warn("[AgentSessionController] Failed to fetch models: ${e.message}")
            }
        }
    }

    fun getAvailableModels(): List<ModelInfo> = availableModels

    fun onModelListChanged(listener: (List<ModelInfo>) -> Unit) {
        modelListListeners.add(listener)
        if (availableModels.isNotEmpty()) {
            listener(availableModels)
        }
    }

    fun dispose() {
        backgroundExecutor.shutdownNow()
        modelListListeners.clear()
        session.clear()
    }
}