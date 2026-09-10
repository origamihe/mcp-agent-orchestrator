package com.mcp.plugin.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.mcp.plugin.McpPluginSettings
import com.mcp.plugin.session.AgentEvent
import com.mcp.plugin.session.AgentMode
import com.mcp.plugin.session.AgentSessionController
import com.mcp.plugin.session.ModelInfo
import com.mcp.plugin.session.RunSummary
import com.mcp.plugin.util.PluginLogger
import java.awt.BorderLayout
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.text.BadLocationException
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

class ChatPanel(
    private val project: Project,
    private val toolWindow: ToolWindow
) : JPanel(BorderLayout()) {

    companion object {
        private const val FONT_FAMILY = "SansSerif"
        private const val WELCOME_MESSAGE = "Welcome to MCP Agent. Type your message and press Ctrl+Enter to send."
    }

    private val logger = Logger.getInstance(ChatPanel::class.java)
    private val settings = ApplicationManager.getApplication().getService(McpPluginSettings::class.java) ?: McpPluginSettings()
    private val sessionController: AgentSessionController = project.getService(AgentSessionController::class.java)

    private val timeline = ExecutionTimeline()
    private val runHistory = RunHistoryPanel()

    private val timelineTabs = JTabbedPane().apply {
        addTab("Timeline", timeline.component)
        addTab("History", runHistory.component)
        font = Font(FONT_FAMILY, Font.PLAIN, 11)
    }

    private val finalAnswerPane = JTextPane().apply {
        isEditable = false
    }

    private val finalAnswerScroll = JBScrollPane(finalAnswerPane).apply {
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
    }

    private val conversationPanel = JPanel(BorderLayout()).apply {
        val headerPanel = JPanel(BorderLayout()).apply {
            add(JLabel("Agent Response").apply {
                font = Font(FONT_FAMILY, Font.BOLD, 12)
                foreground = JBColor.GRAY
                border = BorderFactory.createEmptyBorder(2, 4, 2, 0)
            }, BorderLayout.WEST)
            val clearBtn = JButton("Clear").apply {
                font = Font(FONT_FAMILY, Font.PLAIN, 10)
                margin = Insets(1, 6, 1, 6)
                addActionListener {
                    try {
                        val doc = finalAnswerPane.styledDocument
                        doc.remove(0, doc.length)
                    } catch (e: BadLocationException) {
                        logger.error("[ChatPanel] Failed to clear response: ${e.message}")
                    }
                }
            }
            add(clearBtn, BorderLayout.EAST)
            border = BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.LIGHT_GRAY)
        }
        add(headerPanel, BorderLayout.NORTH)
        add(finalAnswerScroll, BorderLayout.CENTER)
    }

    private val splitter = JBSplitter(true, 0.55f).apply {
        firstComponent = timelineTabs
        secondComponent = conversationPanel
    }

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

    private val newSessionButton = JButton("New Session").apply {
        font = Font(FONT_FAMILY, Font.PLAIN, 11)
        addActionListener { startNewSession() }
    }

    private val viewLogsButton = JButton("Logs").apply {
        font = Font(FONT_FAMILY, Font.PLAIN, 11)
        addActionListener { showLogs() }
    }

    private val statusLabel = JLabel("Disconnected").apply {
        foreground = JBColor.RED
        font = Font(FONT_FAMILY, Font.PLAIN, 11)
    }

    private val statusDot = JLabel("\u25CF").apply {
        foreground = JBColor.RED
        font = Font(FONT_FAMILY, Font.PLAIN, 10)
    }

    private val modeCombo = JComboBox(AgentMode.entries.toTypedArray()).apply {
        selectedItem = AgentMode.fromBackendMode(settings.agentMode)
        font = Font(FONT_FAMILY, Font.PLAIN, 12)
        addActionListener {
            val mode = selectedItem as? AgentMode ?: return@addActionListener
            sessionController.changeMode(mode)
        }
    }

    private val modelCombo = JComboBox<ModelInfo>().apply {
        font = Font(FONT_FAMILY, Font.PLAIN, 12)
        toolTipText = "Select an AI model for the agent"
        setRenderer { _, value, _, _, _ ->
            JLabel(value?.displayName ?: "Default")
        }
        addActionListener {
            val model = selectedItem as? ModelInfo
            toolTipText = if (model != null && model.configId.isNotEmpty()) {
                "Provider: ${model.provider ?: "N/A"} | Model: ${model.modelName ?: "N/A"} | Config: ${model.configId}"
            } else {
                "Select an AI model for the agent"
            }
            sessionController.changeModel(model?.configId)
        }
    }

    init {
        layout = BorderLayout(5, 5)
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        val topPanel = buildTopPanel()
        val inputPanel = buildInputPanel()

        add(topPanel, BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)
        add(inputPanel, BorderLayout.SOUTH)

        setupInputKeyListener()
        setupControllerListeners()
        setupSessionListeners()

        PluginLogger.onFlushFailure { error ->
            SwingUtilities.invokeLater {
                val tw = ToolWindowManager.getInstance(project).getToolWindow("MCP Agent")
                tw?.setIcon(com.intellij.icons.AllIcons.General.BalloonWarning)
                logger.error("[ChatPanel] Plugin log failure: $error")
            }
        }

        sessionController.init()

        if (settings.autoConnect) {
            logger.info("[ChatPanel] Auto-connecting to: ${settings.gatewayUrl}")
            sessionController.startSession()
        }

        timeline.addEvent(AgentEvent.Thinking("", null, WELCOME_MESSAGE, 0, System.currentTimeMillis()))
    }

    private fun buildTopPanel(): JPanel {
        val headerPanel = JPanel(BorderLayout()).apply {
            val titleRow = JPanel(BorderLayout()).apply {
                add(JLabel(settings.agentName).apply {
                    font = Font(FONT_FAMILY, Font.BOLD, 15)
                }, BorderLayout.WEST)

                val statusRow = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                    add(statusDot)
                    add(statusLabel)
                }
                add(statusRow, BorderLayout.EAST)
            }
            add(titleRow, BorderLayout.NORTH)

            val modeModelPanel = JPanel(GridBagLayout()).apply {
                val gbc = GridBagConstraints().apply {
                    fill = GridBagConstraints.HORIZONTAL
                    insets = Insets(2, 4, 2, 8)
                }

                gbc.gridx = 0
                gbc.gridy = 0
                gbc.weightx = 0.0
                add(JLabel("Mode:").apply {
                    font = Font(FONT_FAMILY, Font.PLAIN, 11)
                    foreground = JBColor.GRAY
                }, gbc)

                gbc.gridx = 1
                gbc.weightx = 1.0
                add(modeCombo, gbc)

                gbc.gridx = 2
                gbc.weightx = 0.0
                add(JLabel("Model:").apply {
                    font = Font(FONT_FAMILY, Font.PLAIN, 11)
                    foreground = JBColor.GRAY
                }, gbc)

                gbc.gridx = 3
                gbc.weightx = 1.0
                add(modelCombo, gbc)

                border = BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.LIGHT_GRAY)
            }
            add(modeModelPanel, BorderLayout.SOUTH)
        }

        return headerPanel
    }

    private fun buildInputPanel(): JPanel {
        val inputPanel = JPanel(BorderLayout(5, 5)).apply {
            add(JBScrollPane(inputField).apply {
                preferredSize = Dimension(300, 60)
            }, BorderLayout.CENTER)

            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0)).apply {
                add(viewLogsButton)
                add(newSessionButton)
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

    private fun setupControllerListeners() {
        sessionController.onConnectionStateChange { connected ->
            SwingUtilities.invokeLater {
                if (connected) {
                    statusDot.foreground = JBColor(0x00AA00, 0x00AA00)
                    statusLabel.text = "Connected"
                    statusLabel.foreground = JBColor(0x00AA00, 0x00AA00)
                    logger.info("[ChatPanel] Connection established")
                } else {
                    statusDot.foreground = JBColor.RED
                    statusLabel.text = "Disconnected"
                    statusLabel.foreground = JBColor.RED
                    logger.warn("[ChatPanel] Connection lost")
                }
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
                if (currentSelection != null && currentSelection.configId.isNotEmpty()) {
                    val idx = models.indexOfFirst { it.configId == currentSelection.configId }
                    if (idx >= 0) modelCombo.selectedIndex = idx + 1
                } else if (models.isNotEmpty()) {
                    modelCombo.selectedIndex = 1
                }
            }
        }

        sessionController.onDiffPreview = { filePath, diff, isFullContent ->
            val dialog = DiffPreviewDialog(project, filePath, diff, isFullContent)
            dialog.show()
            dialog.isApproved
        }

        runHistory.onReplayRun = { run ->
            val message = run.userMessage
            if (message.isNotEmpty()) {
                inputField.text = message
                sendChat()
            }
        }

        sessionController.onUiEvent { event ->
            SwingUtilities.invokeLater {
                if (sessionController.isStaleEvent(event.generation)) {
                    logger.debug("[ChatPanel] Stale UI event ignored (gen=${event.generation})")
                    return@invokeLater
                }
                renderEvent(event)
            }
        }
    }

    private fun sendChat() {
        val text = inputField.text.trim()
        if (text.isEmpty()) return

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

    private fun startNewSession() {
        timeline.clear()
        runHistory.clear()
        try {
            val doc = finalAnswerPane.styledDocument
            doc.remove(0, doc.length)
        } catch (e: BadLocationException) {
            logger.error("[ChatPanel] Failed to clear final answer: ${e.message}")
        }
        sessionController.startNewSession()
        logger.info("[ChatPanel] New session started")
    }

    private fun showLogs() {
        val logContent = PluginLogger.getLogContent(500)
        val logFile = PluginLogger.currentLogFile()
        val dialog = JDialog(SwingUtilities.getWindowAncestor(this), "MCP Plugin Logs", Dialog.ModalityType.MODELESS)
        dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE

        val logPane = JTextPane().apply {
            isEditable = false
            font = Font(FONT_FAMILY, Font.PLAIN, 11)
            text = logContent
        }

        val headerPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JLabel("Log file: ${logFile.absolutePath}").apply {
                font = Font(FONT_FAMILY, Font.PLAIN, 10)
                foreground = JBColor.GRAY
            })
        }

        val refreshButton = JButton("Refresh").apply {
            font = Font(FONT_FAMILY, Font.PLAIN, 11)
            addActionListener {
                logPane.text = PluginLogger.getLogContent(500)
            }
        }

        val openFolderButton = JButton("Open Log Folder").apply {
            font = Font(FONT_FAMILY, Font.PLAIN, 11)
            addActionListener {
                try {
                    val logDir = PluginLogger.logDirectory()
                    java.awt.Desktop.getDesktop().open(logDir)
                } catch (e: Exception) {
                    logger.error("[ChatPanel] Failed to open log folder: ${e.message}")
                }
            }
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(openFolderButton)
            add(refreshButton)
        }

        val contentPane = JPanel(BorderLayout(5, 5)).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            add(headerPanel, BorderLayout.NORTH)
            add(JBScrollPane(logPane).apply {
                preferredSize = Dimension(750, 450)
            }, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }

        dialog.contentPane = contentPane
        dialog.pack()
        dialog.setLocationRelativeTo(this)
        dialog.isVisible = true
    }

    private fun renderEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.FinalAnswer -> {
                renderFinalAnswer(event.content)
                SwingUtilities.invokeLater {
                    cancelButton.isVisible = false
                    sendButton.isEnabled = true
                }
            }

            is AgentEvent.RunStarted -> {
                if (event.generation > 0) {
                    timeline.addEvent(event)
                }
                val summary = RunSummary(
                    runId = event.runId,
                    sessionId = event.sessionId,
                    mode = event.mode.toBackendMode(),
                    model = event.modelConfigId ?: "default",
                    userMessage = "",
                    status = "RUNNING",
                    startTime = System.currentTimeMillis(),
                    endTime = 0L,
                    generation = event.generation,
                    toolCallCount = 0,
                    fileChanges = emptyList()
                )
                runHistory.addRun(summary)
            }

            is AgentEvent.RunCompleted -> {
                timeline.addEvent(event)
                runHistory.updateRunStatus(event.runId, "COMPLETED", System.currentTimeMillis())
                SwingUtilities.invokeLater {
                    cancelButton.isVisible = false
                    sendButton.isEnabled = true
                }
            }

            is AgentEvent.RunFailed -> {
                timeline.addEvent(event)
                runHistory.updateRunStatus(event.runId, "FAILED", System.currentTimeMillis())
                SwingUtilities.invokeLater {
                    cancelButton.isVisible = false
                    sendButton.isEnabled = true
                }
            }

            is AgentEvent.RunCancelled -> {
                timeline.addEvent(event)
                runHistory.updateRunStatus(event.runId, "CANCELLED", System.currentTimeMillis())
                SwingUtilities.invokeLater {
                    cancelButton.isVisible = false
                    sendButton.isEnabled = true
                }
            }

            is AgentEvent.UserMessage -> {
                timeline.addEvent(event)
                renderUserMessageToAnswer(event)
            }

            is AgentEvent.Thinking,
            is AgentEvent.ToolCallStarted,
            is AgentEvent.ToolCallCompleted,
            is AgentEvent.ToolCallFailed,
            is AgentEvent.FileRead,
            is AgentEvent.FileSearch,
            is AgentEvent.MCPToolCall,
            is AgentEvent.DiffCreated,
            is AgentEvent.DiffApplied,
            is AgentEvent.TokenUsage -> {
                timeline.addEvent(event)
            }
        }
    }

    private fun renderUserMessageToAnswer(event: AgentEvent.UserMessage) {
        try {
            val doc = finalAnswerPane.styledDocument
            if (doc.length > 0) {
                doc.insertString(doc.length, "\n\n", userLabelStyle)
            }
            doc.insertString(doc.length, "You: ", userLabelStyle)
            doc.insertString(doc.length, event.content, userTextStyle)
            finalAnswerPane.caretPosition = doc.length
        } catch (e: BadLocationException) {
            logger.error("[ChatPanel] Failed to render user message: ${e.message}")
        }
    }

    private fun renderFinalAnswer(content: String) {
        try {
            val doc = finalAnswerPane.styledDocument
            doc.insertString(doc.length, "\n\nAgent:\n", agentLabelStyle)
            doc.insertString(doc.length, content, finalAnswerStyle)
            finalAnswerPane.caretPosition = doc.length
        } catch (e: BadLocationException) {
            logger.error("[ChatPanel] Failed to render final answer: ${e.message}")
        }
    }

    private val finalAnswerStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setFontFamily(this, FONT_FAMILY)
            StyleConstants.setFontSize(this, 12)
        }

    private val agentLabelStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setFontFamily(this, FONT_FAMILY)
            StyleConstants.setFontSize(this, 12)
            StyleConstants.setBold(this, true)
            StyleConstants.setForeground(this, JBColor(0x0066CC, 0x6699FF))
        }

    private val userLabelStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setFontFamily(this, FONT_FAMILY)
            StyleConstants.setFontSize(this, 12)
            StyleConstants.setBold(this, true)
            StyleConstants.setForeground(this, JBColor(0x009933, 0x66CC66))
        }

    private val userTextStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setFontFamily(this, FONT_FAMILY)
            StyleConstants.setFontSize(this, 12)
        }
}