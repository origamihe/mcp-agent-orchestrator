package com.mcp.plugin.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.mcp.plugin.McpPluginSettings
import com.mcp.plugin.capability.ALL_CAPABILITIES
import com.mcp.plugin.event.IdeEventBus
import com.mcp.plugin.event.OutgoingEnvelope
import com.mcp.plugin.session.AgentEvent
import com.mcp.plugin.session.AgentMode
import com.mcp.plugin.session.AgentSessionController
import com.mcp.plugin.session.ModelInfo
import com.mcp.plugin.transport.Transport
import com.mcp.plugin.transport.WebSocketTransport
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.AdjustmentEvent
import java.awt.event.AdjustmentListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.text.BadLocationException
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

class ChatPanel(
    private val project: Project,
    private val toolWindow: ToolWindow
) : JPanel(BorderLayout()) {

    companion object {
        private const val FONT_FAMILY = "SansSerif"
        private const val WELCOME_MESSAGE = "欢迎使用 MCP Agent！输入 Ctrl+Enter 发送消息。"
    }

    private val logger = Logger.getInstance(ChatPanel::class.java)
    private val settings = ApplicationManager.getApplication().getService(McpPluginSettings::class.java) ?: McpPluginSettings()
    private val transport: Transport? = project.getService(WebSocketTransport::class.java)
    private val eventBus = project.getService(IdeEventBus::class.java)
    private val sessionController: AgentSessionController = project.getService(AgentSessionController::class.java)

    private val chatArea = JTextPane().apply {
        isEditable = false
    }

    private val chatScroll = JBScrollPane(chatArea).apply {
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
    }

    private val executionTimeline = ExecutionTimeline(chatArea, chatScroll)

    private val inputField = JTextArea(3, 30).apply {
        lineWrap = true
        wrapStyleWord = true
        font = Font(FONT_FAMILY, Font.PLAIN, 13)
    }

    private val sendButton = JButton("Send").apply {
        addActionListener { sendChat() }
    }

    private val cancelButton = JButton("Stop").apply {
        isVisible = false
        addActionListener { cancelRun() }
    }

    private val statusLabel = JLabel("Disconnected").apply { foreground = JBColor.RED }

    private val modeCombo = JComboBox(AgentMode.entries.toTypedArray()).apply {
        selectedItem = AgentMode.fromBackendMode(settings.agentMode)
        addActionListener {
            val mode = selectedItem as? AgentMode ?: return@addActionListener
            sessionController.changeMode(mode)
        }
    }

    private val modelCombo = JComboBox<ModelInfo>().apply {
        setRenderer { _, value, _, _, _ ->
            JLabel(value?.displayName ?: "Default")
        }
        addActionListener {
            val model = selectedItem as? ModelInfo
            sessionController.changeModel(model?.configId)
        }
    }

    init {
        layout = BorderLayout(5, 5)
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        val header = buildHeader()
        val inputPanel = buildInputPanel()

        add(header, BorderLayout.NORTH)
        add(chatScroll, BorderLayout.CENTER)
        add(inputPanel, BorderLayout.SOUTH)

        setupInputKeyListener()
        setupTransportListeners()
        setupSessionListeners()

        sessionController.init()

        if (settings.autoConnect) {
            transport?.connect()
            sendHello()
        }

        appendSystem(WELCOME_MESSAGE)
    }

    private fun buildHeader(): JPanel {
        val topRow = JPanel(BorderLayout()).apply {
            add(JLabel(settings.agentName).apply { font = Font(FONT_FAMILY, Font.BOLD, 16) }, BorderLayout.WEST)
            add(statusLabel, BorderLayout.EAST)
            border = BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.LIGHT_GRAY)
        }

        val modePanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 2)).apply {
            add(JLabel("Mode:").apply { font = Font(FONT_FAMILY, Font.PLAIN, 11) })
            add(modeCombo.apply { font = Font(FONT_FAMILY, Font.PLAIN, 11) })
            add(JLabel("Model:").apply { font = Font(FONT_FAMILY, Font.PLAIN, 11) })
            add(modelCombo.apply { font = Font(FONT_FAMILY, Font.PLAIN, 11) })
        }

        val header = JPanel(BorderLayout()).apply {
            add(topRow, BorderLayout.NORTH)
            add(modePanel, BorderLayout.SOUTH)
        }
        return header
    }

    private fun buildInputPanel(): JPanel {
        val inputPanel = JPanel(BorderLayout(5, 5)).apply {
            add(JBScrollPane(inputField).apply { preferredSize = Dimension(300, 60) }, BorderLayout.CENTER)

            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0)).apply {
                add(cancelButton)
                add(sendButton)
            }
            add(buttonPanel, BorderLayout.EAST)
            border = BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.LIGHT_GRAY)
        }
        return inputPanel
    }

    private fun setupInputKeyListener() {
        inputField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && e.isControlDown) {
                    sendChat()
                    e.consume()
                }
            }
        })
    }

    private fun setupTransportListeners() {
        transport?.onMessage { json -> handleIncoming(json) }
        transport?.onConnectionChange { connected ->
            SwingUtilities.invokeLater {
                statusLabel.text = if (connected) "Connected" else "Disconnected"
                statusLabel.foreground = if (connected) JBColor(0x00AA00, 0x00AA00) else JBColor.RED
            }
        }
    }

    private fun setupSessionListeners() {
        sessionController.onModelListChanged { models ->
            SwingUtilities.invokeLater {
                val currentSelection = modelCombo.selectedItem as? ModelInfo
                modelCombo.removeAllItems()
                modelCombo.addItem(ModelInfo("", "Default", null))
                models.forEach { modelCombo.addItem(it) }
                if (currentSelection != null) {
                    val idx = models.indexOfFirst { it.configId == currentSelection.configId }
                    if (idx >= 0) modelCombo.selectedIndex = idx + 1
                }
            }
        }
    }

    private fun sendHello() {
        val t = transport ?: return
        t.send(OutgoingEnvelope(
            type = "hello",
            sessionId = t.sessionId,
            workspaceId = eventBus?.workspaceId,
            capabilities = ALL_CAPABILITIES.map {
                mapOf("name" to it.name, "description" to it.description, "params" to it.params)
            }
        ))
    }

    private fun sendChat() {
        val text = inputField.text.trim()
        if (text.isEmpty()) return

        appendUser(text)
        inputField.text = ""
        cancelButton.isVisible = true
        sendButton.isEnabled = false

        sessionController.sendChat(text) { runId ->
            logger.info("[ChatPanel] Run started: $runId")
        }
    }

    private fun cancelRun() {
        sessionController.cancelRun()
        cancelButton.isVisible = false
        sendButton.isEnabled = true
    }

    private fun handleIncoming(json: String) {
        sessionController.handleMessage(json) { event ->
            SwingUtilities.invokeLater {
                if (sessionController.isStaleEvent(event.generation)) {
                    logger.debug("[ChatPanel] Stale event ignored (gen=${event.generation})")
                    return@invokeLater
                }
                renderEvent(event)
            }
        }
    }

    /**
     * 渲染事件分发。
     *
     * 核心原则：
     * - 用户消息和 Agent 回复由 ChatPanel 直接渲染
     * - 所有执行事件（ToolCall/FileRead/Search/MCP/Diff 等）委托给 ExecutionTimeline
     * - ExecutionTimeline 只渲染来自真实 capability_call → execute → capability_result 链路的事件
     */
    private fun renderEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.UserMessage -> appendUser(event.content)

            is AgentEvent.FinalAnswer -> {
                appendAgent(event.content)
                SwingUtilities.invokeLater {
                    cancelButton.isVisible = false
                    sendButton.isEnabled = true
                }
            }

            is AgentEvent.RunStarted -> {
                appendSystem("Run started — ${event.mode.displayName}")
                executionTimeline.renderEvent(event)
            }

            is AgentEvent.RunCompleted -> {
                executionTimeline.renderEvent(event)
                SwingUtilities.invokeLater {
                    cancelButton.isVisible = false
                    sendButton.isEnabled = true
                }
            }

            is AgentEvent.RunFailed -> {
                executionTimeline.renderEvent(event)
                SwingUtilities.invokeLater {
                    cancelButton.isVisible = false
                    sendButton.isEnabled = true
                }
            }

            is AgentEvent.RunCancelled -> {
                appendSystem("Run cancelled")
                executionTimeline.renderEvent(event)
                SwingUtilities.invokeLater {
                    cancelButton.isVisible = false
                    sendButton.isEnabled = true
                }
            }

            is AgentEvent.Thinking -> executionTimeline.renderEvent(event)

            is AgentEvent.ToolCallStarted,
            is AgentEvent.ToolCallCompleted,
            is AgentEvent.ToolCallFailed,
            is AgentEvent.FileRead,
            is AgentEvent.FileSearch,
            is AgentEvent.MCPToolCall,
            is AgentEvent.DiffCreated,
            is AgentEvent.DiffApplied -> {
                executionTimeline.renderEvent(event)
            }
        }
    }

    private fun appendUser(text: String) {
        appendDoc("You", text, userStyle)
    }

    private fun appendAgent(text: String) {
        appendDoc(settings.agentName, text, agentStyle)
    }

    private fun appendSystem(text: String) {
        appendToDoc("$text\n", systemStyle)
    }

    private fun appendDoc(sender: String, text: String, style: SimpleAttributeSet) {
        appendToDoc("$sender:\n", style)
        appendToDoc("$text\n\n", normalStyle)
    }

    private fun appendToDoc(text: String, attr: SimpleAttributeSet) {
        try {
            val doc = chatArea.styledDocument
            doc.insertString(doc.length, text, attr)
        } catch (e: BadLocationException) {
            logger.error("[ChatPanel] Failed to append to document: ${e.message}")
        }
    }

    private val userStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setBold(this, true)
            StyleConstants.setForeground(this, JBColor(0x4A90D9, 0x4A90D9))
        }

    private val agentStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setBold(this, true)
            StyleConstants.setForeground(this, JBColor(0x50B86C, 0x50B86C))
        }

    private val systemStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, JBColor.GRAY)
            StyleConstants.setFontSize(this, 11)
        }

    private val normalStyle: SimpleAttributeSet
        get() = SimpleAttributeSet()
}