package com.mcp.plugin.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.mcp.plugin.McpPluginSettings
import com.mcp.plugin.capability.ALL_CAPABILITIES
import com.mcp.plugin.capability.CapabilityAdapter
import com.mcp.plugin.event.IdeEventBus
import com.mcp.plugin.event.OutgoingEnvelope
import com.mcp.plugin.event.Protocol
import com.mcp.plugin.transport.Transport
import com.mcp.plugin.transport.WebSocketTransport
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.text.DefaultCaret

class ChatPanel(
    private val project: Project,
    private val toolWindow: ToolWindow
) : JPanel(BorderLayout()) {

    companion object {
        private const val HTML_CONTENT_TYPE = "text/html"
        private const val FONT_FAMILY = "SansSerif"
        private const val WELCOME_MESSAGE = "欢迎使用 MCP Agent！输入 Ctrl+Enter 发送消息。"

        private const val ACTION_APPLY_DIFF = "apply_diff"
        private const val ACTION_APPLY_FULL = "apply_full"
        private const val ACTION_NOTIFY = "notify"
    }

    private val logger = Logger.getInstance(ChatPanel::class.java)
    private val settings = ApplicationManager.getApplication().getService(McpPluginSettings::class.java) ?: McpPluginSettings()
    private val transport: Transport? = project.getService(WebSocketTransport::class.java)
    private val eventBus = project.getService(IdeEventBus::class.java)
    private val capabilityAdapter = project.getService(CapabilityAdapter::class.java)

    private val chatArea = JTextPane().apply {
        isEditable = false
        contentType = HTML_CONTENT_TYPE
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        (caret as DefaultCaret).updatePolicy = DefaultCaret.ALWAYS_UPDATE
    }

    private val inputField = JTextArea(3, 30).apply {
        lineWrap = true
        wrapStyleWord = true
        font = Font(FONT_FAMILY, Font.PLAIN, 13)
    }

    private val sendButton = JButton("发送").apply { addActionListener { sendChat() } }
    private val statusLabel = JLabel("● 未连接").apply { foreground = JBColor.RED }

    init {
        layout = BorderLayout(5, 5)
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        val header = JPanel(BorderLayout()).apply {
            add(JLabel(settings.agentName).apply { font = Font(FONT_FAMILY, Font.BOLD, 16) }, BorderLayout.WEST)
            add(statusLabel, BorderLayout.EAST)
            border = BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.LIGHT_GRAY)
        }

        val chatScroll = JBScrollPane(chatArea).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }

        val inputPanel = JPanel(BorderLayout(5, 5)).apply {
            add(JBScrollPane(inputField).apply { preferredSize = Dimension(300, 60) }, BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
            border = BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.LIGHT_GRAY)
        }

        add(header, BorderLayout.NORTH)
        add(chatScroll, BorderLayout.CENTER)
        add(inputPanel, BorderLayout.SOUTH)

        inputField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && e.isControlDown) {
                    sendChat()
                    e.consume()
                }
            }
        })

        transport?.onMessage { json -> handleIncoming(json) }
        transport?.onConnectionChange { connected ->
            SwingUtilities.invokeLater {
                statusLabel.text = if (connected) "● 已连接" else "● 未连接"
                statusLabel.foreground = if (connected) JBColor(0x00AA00, 0x00AA00) else JBColor.RED
            }
        }

        if (settings.autoConnect) {
            transport?.connect()
            sendHello()
        }

        appendSystem(WELCOME_MESSAGE)
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

        val t = transport ?: return
        val hostContext = capabilityAdapter.execute("get_editor_state", emptyMap())

        t.send(OutgoingEnvelope(
            type = "chat",
            sessionId = t.sessionId,
            userId = System.getProperty("user.name"),
            workspaceId = eventBus?.workspaceId,
            content = text,
            hostContext = hostContext
        ))

        appendUser(text)
        inputField.text = ""
    }

    private fun handleIncoming(json: String) {
        SwingUtilities.invokeLater {
            try {
                val msg = Protocol.fromJson(json)
                when (msg.type) {
                    "capability_call" -> {
                        val callId = msg.callId ?: return@invokeLater
                        val capability = msg.capability ?: return@invokeLater
                        val params = msg.params ?: emptyMap()
                        val result = capabilityAdapter.execute(capability, params)
                        val t = transport ?: return@invokeLater
                        t.send(OutgoingEnvelope(
                            type = "capability_result",
                            sessionId = t.sessionId,
                            callId = callId,
                            capability = capability,
                            result = result
                        ))
                    }
                    "reply" -> {
                        appendAgent(msg.content ?: "")
                        msg.actions?.forEach { action ->
                            when (action["type"]) {
                                ACTION_APPLY_DIFF -> {
                                    val filePath = action["filePath"] as? String ?: return@forEach
                                    val diff = action["diff"] as? String ?: return@forEach
                                    logger.warn("[ChatPanel] apply_diff via reply action — should use capability_call for full security audit")
                                    val result = JOptionPane.showConfirmDialog(
                                        this, "Agent 建议修改: $filePath\n\n是否应用？",
                                        "Apply Diff", JOptionPane.YES_NO_OPTION
                                    )
                                    if (result == JOptionPane.YES_OPTION) {
                                        capabilityAdapter.execute("apply_diff", mapOf("filePath" to filePath, "diff" to diff))
                                    }
                                }
                                ACTION_APPLY_FULL -> {
                                    val filePath = action["filePath"] as? String ?: return@forEach
                                    val content = action["content"] as? String ?: return@forEach
                                    logger.warn("[ChatPanel] apply_full via reply action — should use capability_call for full security audit")
                                    capabilityAdapter.execute("apply_full_content", mapOf("filePath" to filePath, "content" to content))
                                }
                                ACTION_NOTIFY -> {
                                    val title = action["title"] as? String ?: ""
                                    val body = action["body"] as? String ?: ""
                                    JOptionPane.showMessageDialog(this, body, title, JOptionPane.INFORMATION_MESSAGE)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("[ChatPanel] Error handling message: ${e.message}")
            }
        }
    }

    private fun appendUser(text: String) {
        val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
        appendHtml("<div style='margin:8px 0;'><b style='color:#4A90D9;'>👤 你</b><br>$escaped</div>")
    }

    private fun appendAgent(text: String) {
        val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
        appendHtml("<div style='margin:8px 0;'><b style='color:#50B86C;'>🤖 ${settings.agentName}</b><br>$escaped</div>")
    }

    private fun appendSystem(text: String) {
        appendHtml("<div style='margin:4px 0; color:#888; font-size:11px;'>$text</div>")
    }

    private fun appendHtml(html: String) {
        val current = chatArea.text ?: "<html><body></body></html>"
        val bodyClose = current.indexOf("</body>")
        val newContent = if (bodyClose > 0) {
            current.substring(0, bodyClose) + html + "\n" + current.substring(bodyClose)
        } else {
            "<html><body>$html</body></html>"
        }
        chatArea.text = newContent
    }
}