package com.mcp.plugin.toolwindow

import com.intellij.ui.JBColor
import com.mcp.plugin.session.AgentEvent
import java.awt.Font
import java.awt.event.AdjustmentEvent
import java.awt.event.AdjustmentListener
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.SwingUtilities
import javax.swing.text.BadLocationException
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

/**
 * 独立的执行时间线组件。
 *
 * 职责：
 * 1. 接收 AgentEvent 并渲染为时间线条目
 * 2. 管理自动滚动（autoFollow）
 * 3. 增量渲染（使用 StyledDocument.insertString）
 * 4. 支持大量事件（限制最大渲染条目数）
 *
 * 此组件不依赖 ChatPanel，不通过 LLM 文本推断 Agent 行为。
 * 所有渲染的事件必须来源于真实的 capability_call → execute → capability_result 链路。
 */
class ExecutionTimeline(
    private val chatPane: JTextPane,
    private val scrollPane: JScrollPane
) {
    private val doc: StyledDocument get() = chatPane.styledDocument

    @Volatile
    private var autoFollow = true

    private var renderedEventCount = 0
    private val maxRenderedEvents = 1000

    private var currentRunId: String? = null

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

    fun renderEvent(event: AgentEvent) {
        if (renderedEventCount >= maxRenderedEvents) {
            return
        }

        when (event) {
            is AgentEvent.RunStarted -> {
                currentRunId = event.runId
                renderRunStarted(event)
            }
            is AgentEvent.RunCompleted -> renderRunCompleted(event)
            is AgentEvent.RunFailed -> renderRunFailed(event)
            is AgentEvent.RunCancelled -> renderRunCancelled(event)
            is AgentEvent.ToolCallStarted -> renderToolCallStarted(event)
            is AgentEvent.ToolCallCompleted -> renderToolCallCompleted(event)
            is AgentEvent.ToolCallFailed -> renderToolCallFailed(event)
            is AgentEvent.FileRead -> renderFileRead(event)
            is AgentEvent.FileSearch -> renderFileSearch(event)
            is AgentEvent.MCPToolCall -> renderMCPToolCall(event)
            is AgentEvent.DiffApplied -> renderDiffApplied(event)
            is AgentEvent.DiffCreated -> renderDiffCreated(event)
            is AgentEvent.Thinking -> renderThinking(event)
            else -> { /* UserMessage and FinalAnswer are handled by ChatPanel */ }
        }

        renderedEventCount++
    }

    private fun renderRunStarted(event: AgentEvent.RunStarted) {
        val modelDisplay = event.modelConfigId ?: "default"
        appendLine("━━━ Run started — ${event.mode.displayName} | $modelDisplay | ${event.runId} ━━━", runStyle)
        appendLine("", normalStyle)
    }

    private fun renderRunCompleted(event: AgentEvent.RunCompleted) {
        appendLine("", normalStyle)
        appendLine("→ Agent completed (${event.durationMs}ms)", completedStyle)
        appendLine("", normalStyle)
        currentRunId = null
    }

    private fun renderRunFailed(event: AgentEvent.RunFailed) {
        appendLine("", normalStyle)
        appendLine("✗ Run failed: ${event.error}", errorStyle)
        appendLine("", normalStyle)
        currentRunId = null
    }

    private fun renderRunCancelled(event: AgentEvent.RunCancelled) {
        appendLine("", normalStyle)
        appendLine("⊘ Run cancelled — ${event.runId}", cancelledStyle)
        appendLine("", normalStyle)
        currentRunId = null
    }

    private fun renderToolCallStarted(event: AgentEvent.ToolCallStarted) {
        appendLine("  ${event.capability} ...", pendingStyle)
    }

    private fun renderToolCallCompleted(event: AgentEvent.ToolCallCompleted) {
        val check = if (event.success) "\u2713" else "\u2717"
        val meta = buildMetadataDisplay(event.capability, event.metadata)
        val style = if (event.success) successStyle else errorStyle
        appendLine("$check ${event.capability}$meta", style)
    }

    private fun renderToolCallFailed(event: AgentEvent.ToolCallFailed) {
        appendLine("\u2717 ${event.capability}", errorStyle)
        appendLine("     error: ${event.error}", errorDetailStyle)
    }

    private fun renderFileRead(event: AgentEvent.FileRead) {
        val fileName = event.filePath.substringAfterLast("/").substringAfterLast("\\")
        appendLine("\u2713 read_file", successStyle)
        appendLine("     $fileName", fileDetailStyle)
        if (event.lines > 0) {
            appendLine("     ${event.lines} lines", fileDetailStyle)
        }
    }

    private fun renderFileSearch(event: AgentEvent.FileSearch) {
        appendLine("\u2713 search_files", successStyle)
        appendLine("     query: ${event.query}", fileDetailStyle)
        if (event.matchCount > 0) {
            appendLine("     ${event.matchCount} files found", fileDetailStyle)
        }
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

    private fun buildMetadataDisplay(capability: String, metadata: Map<String, Any?>): String {
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
                    chatPane.caretPosition = doc.length
                }
            }
        } catch (e: BadLocationException) {
            // Ignore: document was modified concurrently
        } catch (e: NullPointerException) {
            // Ignore: DefaultStyledDocument ElementBuffer corruption in headless mode
            // In real IDE, the document is always properly initialized
        }
    }

    fun getRenderedEventCount(): Int = renderedEventCount

    fun reset() {
        renderedEventCount = 0
        currentRunId = null
        autoFollow = true
    }

    companion object {
        private const val FONT_FAMILY = "SansSerif"

        private val runStyle: SimpleAttributeSet
            get() = SimpleAttributeSet().apply {
                StyleConstants.setBold(this, true)
                StyleConstants.setForeground(this, JBColor(0x6666CC, 0x8888FF))
                StyleConstants.setFontSize(this, 11)
            }

        private val successStyle: SimpleAttributeSet
            get() = SimpleAttributeSet().apply {
                StyleConstants.setForeground(this, JBColor(0x50B86C, 0x50B86C))
                StyleConstants.setFontSize(this, 11)
            }

        private val errorStyle: SimpleAttributeSet
            get() = SimpleAttributeSet().apply {
                StyleConstants.setBold(this, true)
                StyleConstants.setForeground(this, JBColor.RED)
                StyleConstants.setFontSize(this, 11)
            }

        private val errorDetailStyle: SimpleAttributeSet
            get() = SimpleAttributeSet().apply {
                StyleConstants.setForeground(this, JBColor(0xCC6666, 0xCC6666))
                StyleConstants.setFontSize(this, 11)
            }

        private val pendingStyle: SimpleAttributeSet
            get() = SimpleAttributeSet().apply {
                StyleConstants.setForeground(this, JBColor(0xAAAAAA, 0xAAAAAA))
                StyleConstants.setFontSize(this, 11)
            }

        private val thinkingStyle: SimpleAttributeSet
            get() = SimpleAttributeSet().apply {
                StyleConstants.setItalic(this, true)
                StyleConstants.setForeground(this, JBColor(0x888888, 0x888888))
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
                StyleConstants.setForeground(this, JBColor(0x50B86C, 0x50B86C))
                StyleConstants.setFontSize(this, 11)
            }

        private val cancelledStyle: SimpleAttributeSet
            get() = SimpleAttributeSet().apply {
                StyleConstants.setBold(this, true)
                StyleConstants.setForeground(this, JBColor(0xCC8800, 0xFFAA00))
                StyleConstants.setFontSize(this, 11)
            }

        private val normalStyle: SimpleAttributeSet
            get() = SimpleAttributeSet()
    }
}