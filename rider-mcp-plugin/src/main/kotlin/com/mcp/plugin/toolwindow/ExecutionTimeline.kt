package com.mcp.plugin.toolwindow

import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.mcp.plugin.session.AgentEvent
import java.awt.Font
import java.awt.event.AdjustmentEvent
import java.awt.event.AdjustmentListener
import javax.swing.JComponent
import javax.swing.JTextPane
import javax.swing.SwingUtilities
import javax.swing.text.BadLocationException
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

class ExecutionTimeline {

    private val logger = Logger.getInstance(ExecutionTimeline::class.java)

    private val textPane = JTextPane().apply {
        isEditable = false
    }

    private val scrollPane = JBScrollPane(textPane).apply {
        verticalScrollBarPolicy = javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
    }

    val component: JComponent get() = scrollPane

    private val doc: StyledDocument get() = textPane.styledDocument

    @Volatile
    private var autoFollow = true

    private var renderedEventCount = 0
    private val maxRenderedEvents = 1000

    private var lastGeneration = 0

    init {
        setupScrollTracking()
    }

    private fun setupScrollTracking() {
        scrollPane.verticalScrollBar.addAdjustmentListener(object : AdjustmentListener {
            override fun adjustmentValueChanged(e: AdjustmentEvent) {
                if (e.valueIsAdjusting) {
                    val sb = scrollPane.verticalScrollBar
                    autoFollow = sb.value + sb.visibleAmount >= sb.maximum - 20
                }
            }
        })
    }

    fun addEvent(event: AgentEvent) {
        if (renderedEventCount >= maxRenderedEvents) {
            logger.warn("[ExecutionTimeline] Event limit reached ($maxRenderedEvents), trimming old events")
            trimOldEvents()
        }

        if (event.generation > 0 && event.generation != lastGeneration) {
            lastGeneration = event.generation
            appendLine("", normalStyle)
            appendLine("--- Reconnected ---", systemStyle)
        }

        when (event) {
            is AgentEvent.RunStarted -> renderRunStarted(event)
            is AgentEvent.RunCompleted -> renderRunCompleted(event)
            is AgentEvent.RunFailed -> renderRunFailed(event)
            is AgentEvent.RunCancelled -> renderRunCancelled(event)
            is AgentEvent.UserMessage -> renderUserMessage(event)
            is AgentEvent.ToolCallStarted -> renderToolCallStarted(event)
            is AgentEvent.ToolCallCompleted -> renderToolCallCompleted(event)
            is AgentEvent.ToolCallFailed -> renderToolCallFailed(event)
            is AgentEvent.FileRead -> renderFileRead(event)
            is AgentEvent.FileSearch -> renderFileSearch(event)
            is AgentEvent.MCPToolCall -> renderMCPToolCall(event)
            is AgentEvent.DiffApplied -> renderDiffApplied(event)
            is AgentEvent.DiffCreated -> renderDiffCreated(event)
            is AgentEvent.Thinking -> renderThinking(event)
            is AgentEvent.TokenUsage -> renderTokenUsage(event)
            is AgentEvent.FinalAnswer -> { /* rendered by ChatPanel */ }
        }

        renderedEventCount++
    }

    fun clear() {
        renderedEventCount = 0
        lastGeneration = 0
        autoFollow = true
        try {
            doc.remove(0, doc.length)
        } catch (_: BadLocationException) {
        }
    }

    fun getEventCount(): Int = renderedEventCount

    private fun trimOldEvents() {
        try {
            val text = doc.getText(0, doc.length)
            val lines = text.lines()
            val toRemove = lines.size / 4
            if (toRemove > 0) {
                var pos = 0
                repeat(toRemove) {
                    pos = text.indexOf('\n', pos) + 1
                    if (pos <= 0) return
                }
                doc.remove(0, pos)
                renderedEventCount = maxRenderedEvents / 2
            }
        } catch (e: BadLocationException) {
            logger.warn("[ExecutionTimeline] Failed to trim old events: ${e.message}")
        }
    }

    private fun renderRunStarted(event: AgentEvent.RunStarted) {
        val modelDisplay = event.modelConfigId ?: "default"
        appendLine("", normalStyle)
        appendLine("━━━ ${event.mode.displayName} | $modelDisplay ━━━", runStyle)
    }

    private fun renderRunCompleted(event: AgentEvent.RunCompleted) {
        appendLine("→ completed (${event.durationMs}ms)", completedStyle)
        appendLine("", normalStyle)
    }

    private fun renderRunFailed(event: AgentEvent.RunFailed) {
        appendLine("✗ failed: ${event.error}", errorStyle)
        appendLine("", normalStyle)
    }

    private fun renderRunCancelled(event: AgentEvent.RunCancelled) {
        appendLine("⊘ cancelled", cancelledStyle)
        appendLine("", normalStyle)
    }

    private fun renderUserMessage(event: AgentEvent.UserMessage) {
        appendLine("You:", userLabelStyle)
        appendLine("  ${event.content}", userTextStyle)
    }

    private fun renderToolCallStarted(event: AgentEvent.ToolCallStarted) {
        appendLine("  ${event.capability} ...", pendingStyle)
    }

    private fun renderToolCallCompleted(event: AgentEvent.ToolCallCompleted) {
        val check = if (event.success) "\u2713" else "\u2717"
        val meta = buildMetadata(event.capability, event.metadata)
        val style = if (event.success) successStyle else errorStyle
        appendLine("$check ${event.capability}$meta", style)
    }

    private fun renderToolCallFailed(event: AgentEvent.ToolCallFailed) {
        appendLine("\u2717 ${event.capability}", errorStyle)
        appendLine("     error: ${event.error}", errorDetailStyle)
    }

    private fun renderFileRead(event: AgentEvent.FileRead) {
        val fileName = event.filePath.substringAfterLast("/").substringAfterLast("\\")
        appendLine("     $fileName", fileDetailStyle)
        if (event.lines > 0) {
            appendLine("     ${event.lines} lines", fileDetailStyle)
        }
    }

    private fun renderFileSearch(event: AgentEvent.FileSearch) {
        appendLine("     ${event.matchCount} files found", fileDetailStyle)
    }

    private fun renderMCPToolCall(event: AgentEvent.MCPToolCall) {
        val check = if (event.success) "\u2713" else "\u2717"
        val style = if (event.success) successStyle else errorStyle
        appendLine("$check [MCP] ${event.toolName}", style)
        event.metadata.forEach { (key, value) ->
            if (key != "toolName" && key != "success") {
                appendLine("     $key: $value", fileDetailStyle)
            }
        }
    }

    private fun renderDiffApplied(event: AgentEvent.DiffApplied) {
        val fileName = event.filePath.substringAfterLast("/").substringAfterLast("\\")
        val check = if (event.success) "\u2713" else "\u2717"
        val style = if (event.success) successStyle else errorStyle
        appendLine("$check diff applied", style)
        appendLine("     $fileName", fileDetailStyle)
    }

    private fun renderDiffCreated(event: AgentEvent.DiffCreated) {
        val fileName = event.filePath.substringAfterLast("/").substringAfterLast("\\")
        appendLine("\u270E diff created", successStyle)
        appendLine("     $fileName", fileDetailStyle)
    }

    private fun renderThinking(event: AgentEvent.Thinking) {
        appendLine("  \u2026 ${event.message}", thinkingStyle)
    }

    private fun renderTokenUsage(event: AgentEvent.TokenUsage) {
        appendLine("  \u2139 Tokens: ${event.totalTokens} (${event.promptTokens} prompt + ${event.completionTokens} completion)", tokenStyle)
    }

    private fun buildMetadata(capability: String, metadata: Map<String, Any?>): String {
        return when (capability) {
            "read_file" -> {
                val filePath = metadata["filePath"] as? String ?: ""
                val fileName = filePath.substringAfterLast("/").substringAfterLast("\\")
                val lines = metadata["lines"] as? Int ?: 0
                if (lines > 0) " — $fileName ($lines lines)" else " — $fileName"
            }
            "search_files" -> {
                val matches = (metadata["matches"] as? List<*>)?.size ?: 0
                " — $matches files"
            }
            "get_diagnostics" -> " — ${metadata["count"] ?: "?"} issues"
            "read_directory" -> {
                val entries = (metadata["entries"] as? List<*>)?.size ?: 0
                " — $entries entries"
            }
            else -> {
                val durationMs = metadata["durationMs"] as? Long
                if (durationMs != null) " — ${durationMs}ms" else ""
            }
        }
    }

    private fun appendLine(text: String, attr: SimpleAttributeSet) {
        try {
            doc.insertString(doc.length, "$text\n", attr)
            if (autoFollow) {
                SwingUtilities.invokeLater {
                    textPane.caretPosition = doc.length
                }
            }
        } catch (_: BadLocationException) {
        } catch (_: NullPointerException) {
        }
    }

    private val runStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setBold(this, true)
            StyleConstants.setForeground(this, JBColor(0x6666CC, 0x8888FF))
            StyleConstants.setFontSize(this, 11)
        }

    private val successStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, JBColor(0x3B8C3B, 0x50B86C))
            StyleConstants.setFontSize(this, 11)
        }

    private val errorStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setBold(this, true)
            StyleConstants.setForeground(this, JBColor(0xCC3333, 0xFF5555))
            StyleConstants.setFontSize(this, 11)
        }

    private val errorDetailStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, JBColor(0xCC6666, 0xCC6666))
            StyleConstants.setFontSize(this, 11)
        }

    private val pendingStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, JBColor(0x999999, 0x888888))
            StyleConstants.setFontSize(this, 11)
        }

    private val thinkingStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setItalic(this, true)
            StyleConstants.setForeground(this, JBColor(0x888888, 0x888888))
            StyleConstants.setFontSize(this, 11)
        }

    private val tokenStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, JBColor(0x6A5ACD, 0x9B8EC4))
            StyleConstants.setFontSize(this, 11)
        }

    private val fileDetailStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, JBColor(0x777777, 0x999999))
            StyleConstants.setFontSize(this, 11)
        }

    private val completedStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setBold(this, true)
            StyleConstants.setForeground(this, JBColor(0x3B8C3B, 0x50B86C))
            StyleConstants.setFontSize(this, 11)
        }

    private val cancelledStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setBold(this, true)
            StyleConstants.setForeground(this, JBColor(0xCC8800, 0xFFAA00))
            StyleConstants.setFontSize(this, 11)
        }

    private val userLabelStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setBold(this, true)
            StyleConstants.setForeground(this, JBColor(0x4A90D9, 0x5A9FDF))
            StyleConstants.setFontSize(this, 11)
        }

    private val userTextStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, JBColor(0x333333, 0xCCCCCC))
            StyleConstants.setFontSize(this, 11)
        }

    private val systemStyle: SimpleAttributeSet
        get() = SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, JBColor.GRAY)
            StyleConstants.setFontSize(this, 10)
        }

    private val normalStyle: SimpleAttributeSet
        get() = SimpleAttributeSet()
}