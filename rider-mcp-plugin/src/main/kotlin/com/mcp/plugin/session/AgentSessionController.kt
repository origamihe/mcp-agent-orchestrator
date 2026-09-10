package com.mcp.plugin.session

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.mcp.plugin.McpPluginSettings
import com.mcp.plugin.capability.ALL_CAPABILITIES
import com.mcp.plugin.capability.CapabilityAdapter
import com.mcp.plugin.event.IdeEventBus
import com.mcp.plugin.event.OutgoingEnvelope
import com.mcp.plugin.event.Protocol
import com.mcp.plugin.transport.Transport
import com.mcp.plugin.transport.WebSocketTransport
import com.mcp.plugin.util.PluginLogger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.swing.SwingUtilities

/**
 * Agent 会话控制�?�?UI �?Transport/Capability 之间的中间层�?
 *
 * 职责�?
 * 1. startSession() / sendMessage() / cancelRun() / changeMode() / changeModel()
 * 2. 处理 Agent 事件，推送到 AgentSession
 * 3. 协调 Capability 执行（在后台线程�?
 * 4. 模型列表获取（从 Backend REST API�?
 * 5. 管理 cancel_run 协议�?Backend 通信
 * 6. 处理 WebSocket disconnect 时的 Run 状�?
 * 7. 过滤 stale events（generation 不匹配）
 *
 * UI 只负�?render() �?user input�?
 */
@Service(Service.Level.PROJECT)
class AgentSessionController(private val project: Project) {

    private val logger = Logger.getInstance(AgentSessionController::class.java)
    private val transport: Transport? = project.getService(WebSocketTransport::class.java)
    private val capabilityAdapter = project.getService(CapabilityAdapter::class.java)
    private val eventBus: IdeEventBus? = project.getService(IdeEventBus::class.java)
    private val settings = ApplicationManager.getApplication().getService(McpPluginSettings::class.java) ?: McpPluginSettings()

    val session = AgentSession(transport?.sessionId)

    private val resolvedWorkspaceId: String
        get() = eventBus?.workspaceId ?: "workspace-${project.name}"

    private val gson = Gson()
    private val httpClient = HttpClient.newBuilder().build()
    private val backgroundExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "agent-session-controller").apply { isDaemon = true }
    }

    private val capabilityExecutor = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "agent-capability").apply { isDaemon = true }
    }

    private val modelListListeners = mutableListOf<(List<ModelInfo>) -> Unit>()
    private val uiEventListeners = mutableListOf<(AgentEvent) -> Unit>()
    private val connectionStateListeners = mutableListOf<(Boolean) -> Unit>()

    var onDiffPreview: ((filePath: String, diff: String, isFullContent: Boolean) -> Boolean)? = null

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
                sendHello()
            } else {
                handleDisconnect()
            }

            connectionStateListeners.forEach { it(connected) }
        }

        transport?.onMessage { json -> handleMessageInternal(json) }
    }

    fun startSession() {
        session.connectionState = ConnectionState.CONNECTING
        transport?.connect()
    }

    private fun sendChatInternal(text: String, mode: AgentMode, modelConfigId: String?, onRunStarted: ((String) -> Unit)? = null): String {
        val resolvedModel = modelConfigId ?: availableModels.firstOrNull()?.configId
        if (resolvedModel == null) {
            logger.warn("[AgentSessionController] No model available, cannot send chat")
            pushUiEvent(AgentEvent.Thinking(session.sessionId, null, "No model available. Please check backend configuration.", activeGeneration))
            return ""
        }
        if (modelConfigId == null) {
            session.modelConfigId = resolvedModel
            settings.agentModel = resolvedModel
            logger.info("[AgentSessionController] Auto-resolved model to: $resolvedModel")
        }
        val runId = session.startRun(mode, resolvedModel)
        val currentGen = activeGeneration
        session.addUserMessage(text)

        PluginLogger.infoContext("AgentSession", "Chat sent: mode=$mode model=$resolvedModel", session.sessionId, runId, currentGen)

        pushUiEvent(AgentEvent.UserMessage(session.sessionId, text, runId, currentGen))
        pushUiEvent(AgentEvent.RunStarted(session.sessionId, runId, mode, modelConfigId, currentGen))

        onRunStarted?.let { SwingUtilities.invokeLater { it(runId) } }

        backgroundExecutor.submit {
            val t = transport ?: return@submit
            val hostContext = getEditorContext()

            val envelope = OutgoingEnvelope(
                type = "chat",
                sessionId = t.sessionId,
                userId = System.getProperty("user.name"),
                workspaceId = resolvedWorkspaceId,
                content = text,
                hostContext = hostContext,
                mode = mode.toBackendMode(),
                model = resolvedModel,
                generation = currentGen
            )
            t.send(envelope)
        }
        return runId
    }

    fun sendMessage(text: String): String {
        return sendChatInternal(text, session.mode, session.modelConfigId)
    }

    fun sendChat(text: String, onRunStarted: (String) -> Unit) {
        sendChatInternal(text, session.mode, session.modelConfigId, onRunStarted)
    }

    fun sendMessageWithMode(text: String, mode: AgentMode, modelConfigId: String?): String {
        return sendChatInternal(text, mode, modelConfigId)
    }

    fun cancelRun() {
        val runId = session.currentRunId ?: return
        session.cancelRun()

        val t = transport
        if (t != null && t.isConnected) {
            t.send(OutgoingEnvelope(
                type = "cancel_run",
                sessionId = t.sessionId,
                workspaceId = resolvedWorkspaceId,
                runId = runId,
                generation = activeGeneration
            ))
            logger.info("[AgentSessionController] Sent cancel_run for runId=$runId")
            PluginLogger.warnContext("AgentSession", "Cancel run requested", session.sessionId, runId, activeGeneration)
        } else {
            session.confirmCancelled()
            logger.info("[AgentSessionController] Transport disconnected, confirmed cancelled locally for runId=$runId")
        }
    }

    /**
     * 处理 WebSocket 断开连接�?
     * 如果有活跃的 Run，自动取消它�?
     */
    private fun handleDisconnect() {
        val runId = session.currentRunId
        if (runId != null && session.agentState == AgentState.RUNNING) {
            logger.warn("[AgentSessionController] Disconnect during active run $runId, auto-cancelling")
            PluginLogger.warnContext("AgentSession", "Disconnect during active run $runId, auto-cancelling", session.sessionId, runId, activeGeneration)
            session.cancelRun()
            session.confirmCancelled()
        }
        session.connectionState = ConnectionState.DISCONNECTED
    }

    private fun sendHello() {
        val t = transport ?: return
        t.send(OutgoingEnvelope(
            type = "hello",
            sessionId = t.sessionId,
            workspaceId = resolvedWorkspaceId,
            capabilities = ALL_CAPABILITIES.map {
                mapOf("name" to it.name, "description" to it.description, "params" to it.params)
            },
            generation = activeGeneration
        ))
        logger.info("[AgentSessionController] Hello sent, sessionId=${t.sessionId}")
        PluginLogger.infoContext("AgentSession", "Hello sent with ${ALL_CAPABILITIES.size} capabilities", session.sessionId, null, activeGeneration)
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
        PluginLogger.infoContext("AgentSession", "Mode changed to $mode", session.sessionId, session.currentRunId, activeGeneration)
    }

    fun changeModel(modelConfigId: String?) {
        session.modelConfigId = modelConfigId
        if (modelConfigId != null) {
            settings.agentModel = modelConfigId
        }
        PluginLogger.infoContext("AgentSession", "Model changed to $modelConfigId", session.sessionId, session.currentRunId, activeGeneration)
    }

    /**
     * 处理�?Transport 收到的消息�?
     * 在后台线程执�?Capability，通过回调将结果推送回 UI�?
     *
     * 事件来源保证�?
     * - capability_call �?CapabilityAdapter.execute() �?capability_result
     *   只有真实执行才会产生 ToolCallStarted/Completed/Failed 事件
     * - agent_event �?Backend �?Agent 内部事件（MCP Tool Call 等）
     * - reply �?Agent 最终回�?
     *
     * Stale event 过滤�?
     * - 如果事件 generation 与当�?activeGeneration 不匹配，丢弃该事�?
     * - generation=0 的事件（首次连接前的）总是允许通过
     */
    fun handleMessage(json: String, onUiUpdate: (AgentEvent) -> Unit) {
        backgroundExecutor.submit {
            try {
                val envelope = Protocol.fromJson(json)
                val type = envelope.type ?: return@submit
                val sessionId = session.sessionId
                val runId = session.currentRunId
                val currentGen = activeGeneration

                when (type) {
                    "capability_call" -> {
                        val callId = envelope.callId ?: return@submit
                        val capability = envelope.capability ?: return@submit
                        val params = envelope.params ?: emptyMap()

                        val startTime = System.currentTimeMillis()
                        session.addToolCallStarted(capability, params)

                        SwingUtilities.invokeLater {
                            onUiUpdate(AgentEvent.ToolCallStarted(sessionId, runId, capability, params))
                        }

                        val diffApproved = when (capability) {
                            "apply_diff" -> {
                                val filePath = params["filePath"] as? String ?: ""
                                val diff = params["diff"] as? String ?: ""
                                val callback = onDiffPreview
                                if (callback != null) {
                                    callback(filePath, diff, false)
                                } else {
                                    true
                                }
                            }
                            "apply_full_content" -> {
                                val filePath = params["filePath"] as? String ?: ""
                                val content = params["content"] as? String ?: ""
                                val callback = onDiffPreview
                                if (callback != null) {
                                    callback(filePath, content, true)
                                } else {
                                    true
                                }
                            }
                            else -> true
                        }

                        if (!diffApproved) {
                            session.addToolCallFailed(capability, "User rejected the change")
                            SwingUtilities.invokeLater {
                                onUiUpdate(AgentEvent.ToolCallFailed(sessionId, runId, capability, "User rejected the change"))
                            }

                            val tReject = transport ?: return@submit
                            tReject.send(OutgoingEnvelope(
                                type = "capability_result",
                                sessionId = tReject.sessionId,
                                callId = callId,
                                capability = capability,
                                result = mapOf("error" to "User rejected the change", "filePath" to (params["filePath"] ?: "")),
                                generation = currentGen
                            ))
                            return@submit
                        }

                        capabilityExecutor.submit capability@{
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
                                    session.addEvent(AgentEvent.DiffCreated(sessionId, runId, filePath))
                                    SwingUtilities.invokeLater {
                                        onUiUpdate(AgentEvent.DiffCreated(sessionId, runId, filePath))
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
                                logger.error("[AgentSessionController] Capability '$capability' failed: $error")
                                session.addToolCallFailed(capability, error)
                                SwingUtilities.invokeLater {
                                    onUiUpdate(AgentEvent.ToolCallFailed(sessionId, runId, capability, error))
                                }
                            }

                            val t = transport ?: return@capability
                            t.send(OutgoingEnvelope(
                                type = "capability_result",
                                sessionId = t.sessionId,
                                callId = callId,
                                capability = capability,
                                result = result,
                                generation = currentGen
                            ))
                        }
                    }

                    "reply" -> {
                        val content = envelope.content ?: ""
                        val replyRunId = envelope.runId ?: runId ?: "unknown"
                        val replyGen = envelope.generation

                        if (isStaleEvent(replyGen)) {
                            logger.warn("[AgentSessionController] Stale reply ignored (gen=$replyGen, current=$currentGen)")
                            return@submit
                        }

                        if (replyRunId != runId) {
                            logger.warn("[AgentSessionController] Reply runId mismatch: envelope=$replyRunId, current=$runId, using envelope.runId")
                        }

                        session.addFinalAnswer(content, replyRunId)
                        session.completeRun(replyRunId)

                        val estimatedTokens = estimateTokens(content)
                        session.totalCompletionTokens = estimatedTokens
                        session.totalPromptTokens = estimateTokens(envelope.params?.get("prompt") as? String ?: "")

                        if (estimatedTokens > 0) {
                            onUiUpdate(AgentEvent.TokenUsage(
                                sessionId = sessionId,
                                runId = replyRunId,
                                promptTokens = session.totalPromptTokens,
                                completionTokens = session.totalCompletionTokens,
                                totalTokens = session.totalPromptTokens + session.totalCompletionTokens,
                                generation = currentGen
                            ))
                        }

                        SwingUtilities.invokeLater {
                            onUiUpdate(AgentEvent.FinalAnswer(sessionId, replyRunId, content, currentGen))
                        }
                    }

                    "agent_event" -> {
                        val eventType = envelope.eventType
                        val eventRunId = envelope.runId ?: runId
                        val eventGen = envelope.generation

                        if (isStaleEvent(eventGen)) {
                            logger.warn("[AgentSessionController] Stale agent_event ignored (type=$eventType, gen=$eventGen, current=$currentGen)")
                            return@submit
                        }

                        val payload = envelope.payload

                        when (eventType) {
                            "TOOL_CALL" -> {
                                val toolName = payload?.get("toolName") as? String ?: "unknown"
                                session.addMCPToolCall(toolName, false, payload ?: emptyMap())
                                SwingUtilities.invokeLater {
                                    onUiUpdate(AgentEvent.MCPToolCall(sessionId, eventRunId, toolName, false, payload ?: emptyMap(), currentGen))
                                }
                            }
                            "TOOL_RESULT" -> {
                                val toolName = payload?.get("toolName") as? String ?: "unknown"
                                val toolSuccess = payload?.get("success") as? Boolean ?: true
                                session.addMCPToolCall(toolName, toolSuccess, payload ?: emptyMap())
                                SwingUtilities.invokeLater {
                                    onUiUpdate(AgentEvent.MCPToolCall(sessionId, eventRunId, toolName, toolSuccess, payload ?: emptyMap(), currentGen))
                                }
                            }
                            "TOOL_DECISION" -> {
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
                                session.completeRun(eventRunId)
                                SwingUtilities.invokeLater {
                                    onUiUpdate(AgentEvent.RunCompleted(sessionId, eventRunId ?: "unknown", 0, currentGen))
                                }
                            }
                            "LLM_CALL" -> {
                                val modelName = payload?.get("model") as? String ?: "unknown"
                                val promptLen = (payload?.get("promptLength") as? Number)?.toInt() ?: 0
                                session.addEvent(AgentEvent.Thinking(sessionId, eventRunId, "LLM call: $modelName (${promptLen} chars)", currentGen))
                                SwingUtilities.invokeLater {
                                    onUiUpdate(AgentEvent.Thinking(sessionId, eventRunId, "LLM call: $modelName (${promptLen} chars)", currentGen))
                                }
                            }
                            "LLM_RESPONSE" -> {
                                val modelName = payload?.get("model") as? String ?: "unknown"
                                val responseLen = (payload?.get("responseLength") as? Number)?.toInt() ?: 0
                                val tokenCount = (payload?.get("tokenCount") as? Number)?.toInt()
                                session.addEvent(AgentEvent.Thinking(sessionId, eventRunId, "LLM response: $modelName (${responseLen} chars)", currentGen))
                                SwingUtilities.invokeLater {
                                    onUiUpdate(AgentEvent.Thinking(sessionId, eventRunId, "LLM response: $modelName (${responseLen} chars)", currentGen))
                                }
                                if (tokenCount != null && tokenCount > 0) {
                                    session.totalCompletionTokens += tokenCount
                                    onUiUpdate(AgentEvent.TokenUsage(
                                        sessionId = sessionId,
                                        runId = eventRunId,
                                        promptTokens = session.totalPromptTokens,
                                        completionTokens = session.totalCompletionTokens,
                                        totalTokens = session.totalPromptTokens + session.totalCompletionTokens,
                                        generation = currentGen
                                    ))
                                }
                            }
                            "AGENT_ITERATION" -> {
                                val iteration = (payload?.get("iteration") as? Number)?.toInt() ?: 0
                                val action = payload?.get("action") as? String ?: ""
                                val msg = if (action.isNotBlank()) "Iteration $iteration: $action" else "Iteration $iteration"
                                session.addEvent(AgentEvent.Thinking(sessionId, eventRunId, msg, currentGen))
                                SwingUtilities.invokeLater {
                                    onUiUpdate(AgentEvent.Thinking(sessionId, eventRunId, msg, currentGen))
                                }
                            }
                            "FINAL_RESPONSE" -> {
                                val content = payload?.get("content") as? String ?: envelope.content ?: ""
                                if (content.isNotBlank()) {
                                    session.addFinalAnswer(content, eventRunId ?: "unknown")
                                    SwingUtilities.invokeLater {
                                        onUiUpdate(AgentEvent.FinalAnswer(sessionId, eventRunId ?: "unknown", content, currentGen))
                                    }
                                }
                            }
                            else -> {
                                logger.debug("[AgentSessionController] Unhandled agent_event type: $eventType")
                            }
                        }
                    }

                    "cancel_run_ack" -> {
                        val ackRunId = envelope.runId ?: runId
                        logger.info("[AgentSessionController] Backend acknowledged cancel for runId=$ackRunId")
                        session.confirmCancelled()
                        SwingUtilities.invokeLater {
                            onUiUpdate(AgentEvent.RunCancelled(sessionId, ackRunId ?: "unknown", currentGen))
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("[AgentSessionController] Error handling message: ${e.message}")
                PluginLogger.errorContext("AgentSession", "Error handling message: ${e.message}", session.sessionId, session.currentRunId, activeGeneration, e)
            }
        }
    }

    private fun handleMessageInternal(json: String) {
        handleMessage(json) { event -> pushUiEvent(event) }
    }

    fun getEditorContext(): Map<String, Any?> {
        return capabilityAdapter.execute("get_editor_state", emptyMap())
    }

    private fun estimateTokens(text: String?): Int {
        if (text.isNullOrEmpty()) return 0
        return text.length / 4
    }

    fun fetchModels() {
        backgroundExecutor.submit {
            try {
                val baseUrl = settings.gatewayHttpUrl.trimEnd('/')

                val models = mutableListOf<ModelInfo>()

                val dbRequest = HttpRequest.newBuilder()
                    .uri(URI.create("$baseUrl/api/llm/configs"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build()

                val dbResponse = httpClient.send(dbRequest, HttpResponse.BodyHandlers.ofString())
                if (dbResponse.statusCode() == 200) {
                    val type = object : TypeToken<List<ModelInfo>>() {}.type
                    val dbModels: List<ModelInfo> = gson.fromJson(dbResponse.body(), type)
                    models.addAll(dbModels.filter { it.enabled })
                    logger.info("[AgentSessionController] Fetched ${dbModels.size} models from /api/llm/configs, ${models.size} enabled")
                } else {
                    logger.warn("[AgentSessionController] Failed to fetch DB models: HTTP ${dbResponse.statusCode()}")
                }

                try {
                    val mcpRequest = HttpRequest.newBuilder()
                        .uri(URI.create("$baseUrl/mcp/configs"))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build()

                    val mcpResponse = httpClient.send(mcpRequest, HttpResponse.BodyHandlers.ofString())
                    if (mcpResponse.statusCode() == 200) {
                        val type = object : TypeToken<List<ModelInfo>>() {}.type
                        val mcpModels: List<ModelInfo> = gson.fromJson(mcpResponse.body(), type)
                        val existingIds = models.map { it.configId }.toSet()
                        val newModels = mcpModels.filter { it.configId !in existingIds }
                        models.addAll(newModels)
                        logger.info("[AgentSessionController] Discovered ${newModels.size} additional models from /mcp/configs (Ollama auto-discovery)")
                    }
                } catch (e: Exception) {
                    logger.warn("[AgentSessionController] Failed to discover Ollama models: ${e.message}")
                }

                availableModels = models
                SwingUtilities.invokeLater {
                    modelListListeners.forEach { it(models) }
                }

                if (models.isNotEmpty() && session.modelConfigId == null) {
                    val persistedModel = settings.agentModel
                    val matchedModel = if (persistedModel.isNotBlank()) {
                        models.firstOrNull { it.configId == persistedModel }
                    } else null

                    val defaultModel = matchedModel?.configId ?: models.firstOrNull()?.configId
                    if (defaultModel != null) {
                        session.modelConfigId = defaultModel
                        settings.agentModel = defaultModel
                        if (matchedModel != null) {
                            logger.info("[AgentSessionController] Restored persisted model: $defaultModel")
                        } else {
                            logger.info("[AgentSessionController] Auto-selected default model: $defaultModel")
                        }
                    }
                }

                logger.info("[AgentSessionController] Total available models: ${models.size}")
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

    fun onUiEvent(listener: (AgentEvent) -> Unit) {
        uiEventListeners.add(listener)
    }

    fun onConnectionStateChange(listener: (Boolean) -> Unit) {
        connectionStateListeners.add(listener)
    }

    private fun pushUiEvent(event: AgentEvent) {
        SwingUtilities.invokeLater {
            uiEventListeners.forEach { it(event) }
        }
    }

    fun startNewSession() {
        val currentGen = session.generation
        session.clear()
        session.incrementGeneration()
        activeGeneration = session.generation
        pushUiEvent(AgentEvent.Thinking(session.sessionId, null, "New session started", activeGeneration))
        logger.info("[AgentSessionController] New session started, generation=${session.generation} (was $currentGen)")
        PluginLogger.infoContext("AgentSession", "New session started (gen=${session.generation}, was $currentGen)", session.sessionId, null, activeGeneration)
    }

    fun getSessionEventHistory(): List<AgentEvent> = session.getEvents()

    fun dispose() {
        backgroundExecutor.shutdownNow()
        capabilityExecutor.shutdownNow()
        modelListListeners.clear()
        uiEventListeners.clear()
        connectionStateListeners.clear()
        session.clear()
    }
}