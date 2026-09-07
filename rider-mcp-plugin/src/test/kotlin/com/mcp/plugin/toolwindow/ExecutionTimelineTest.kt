package com.mcp.plugin.toolwindow

import com.mcp.plugin.session.AgentEvent
import com.mcp.plugin.session.AgentMode
import org.junit.Assert.*
import org.junit.Test
import javax.swing.JScrollPane
import javax.swing.JTextPane

class ExecutionTimelineTest {

    private fun createTimeline(): Pair<ExecutionTimeline, JTextPane> {
        val pane = JTextPane()
        val scroll = JScrollPane(pane)
        val timeline = ExecutionTimeline(pane, scroll)
        return timeline to pane
    }

    @Test
    fun `should render RunStarted event`() {
        val (timeline, pane) = createTimeline()
        val event = AgentEvent.RunStarted("s1", "r1", AgentMode.CHAT, "gpt-4")
        timeline.renderEvent(event)
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("Run started"))
        assertTrue(text.contains("Chat"))
        assertTrue(text.contains("gpt-4"))
    }

    @Test
    fun `should render RunCompleted event`() {
        val (timeline, pane) = createTimeline()
        timeline.renderEvent(AgentEvent.RunCompleted("s1", "r1", 1234))
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("Agent completed"))
        assertTrue(text.contains("1234ms"))
    }

    @Test
    fun `should render RunFailed event`() {
        val (timeline, pane) = createTimeline()
        timeline.renderEvent(AgentEvent.RunFailed("s1", "r1", "Connection timeout"))
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("Run failed"))
        assertTrue(text.contains("Connection timeout"))
    }

    @Test
    fun `should render ToolCallStarted event`() {
        val (timeline, pane) = createTimeline()
        timeline.renderEvent(AgentEvent.ToolCallStarted("s1", "r1", "read_file", mapOf("filePath" to "/test.txt")))
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("read_file"))
    }

    @Test
    fun `should render successful ToolCallCompleted event`() {
        val (timeline, pane) = createTimeline()
        timeline.renderEvent(AgentEvent.ToolCallCompleted("s1", "r1", "read_file", true, mapOf("filePath" to "/test.txt", "lines" to 42)))
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("\u2713"))
        assertTrue(text.contains("read_file"))
        assertTrue(text.contains("42 lines"))
    }

    @Test
    fun `should render failed ToolCallCompleted event`() {
        val (timeline, pane) = createTimeline()
        timeline.renderEvent(AgentEvent.ToolCallCompleted("s1", "r1", "read_file", false, emptyMap()))
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("\u2717"))
    }

    @Test
    fun `should render ToolCallFailed event`() {
        val (timeline, pane) = createTimeline()
        timeline.renderEvent(AgentEvent.ToolCallFailed("s1", "r1", "read_file", "File not found"))
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("\u2717"))
        assertTrue(text.contains("File not found"))
    }

    @Test
    fun `should render FileRead event`() {
        val (timeline, pane) = createTimeline()
        timeline.renderEvent(AgentEvent.FileRead("s1", "r1", "/project/src/CapabilityAdapter.kt", 237, 0, 15))
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("read_file"))
        assertTrue(text.contains("CapabilityAdapter.kt"))
        assertTrue(text.contains("237 lines"))
    }

    @Test
    fun `should render FileSearch event`() {
        val (timeline, pane) = createTimeline()
        timeline.renderEvent(AgentEvent.FileSearch("s1", "r1", "CapabilityAdapter", 3))
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("search_files"))
        assertTrue(text.contains("CapabilityAdapter"))
        assertTrue(text.contains("3 files"))
    }

    @Test
    fun `should render MCPToolCall event`() {
        val (timeline, pane) = createTimeline()
        timeline.renderEvent(AgentEvent.MCPToolCall("s1", "r1", "mcp_search", true, mapOf("query" to "test")))
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("MCP"))
        assertTrue(text.contains("mcp_search"))
    }

    @Test
    fun `should render DiffApplied event`() {
        val (timeline, pane) = createTimeline()
        timeline.renderEvent(AgentEvent.DiffApplied("s1", "r1", "/project/src/Test.kt", true))
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("diff applied"))
        assertTrue(text.contains("Test.kt"))
    }

    @Test
    fun `should render DiffCreated event`() {
        val (timeline, pane) = createTimeline()
        timeline.renderEvent(AgentEvent.DiffCreated("s1", "r1", "/project/src/Test.kt"))
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("diff created"))
        assertTrue(text.contains("Test.kt"))
    }

    @Test
    fun `should render Thinking event`() {
        val (timeline, pane) = createTimeline()
        timeline.renderEvent(AgentEvent.Thinking("s1", "r1", "Analyzing code..."))
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("Analyzing code"))
    }

    @Test
    fun `should not render UserMessage or FinalAnswer events`() {
        val (timeline, pane) = createTimeline()
        timeline.renderEvent(AgentEvent.UserMessage("s1", "Hello"))
        timeline.renderEvent(AgentEvent.FinalAnswer("s1", "r1", "Done"))
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertEquals("", text.trim())
    }

    @Test
    fun `should handle many events without error`() {
        val (timeline, pane) = createTimeline()
        for (i in 1..100) {
            timeline.renderEvent(AgentEvent.ToolCallCompleted("s1", "r1", "read_file", true, mapOf("filePath" to "/f$i.txt", "lines" to i)))
        }
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("read_file"))
        assertTrue(text.contains("f1.txt"))
        assertTrue(text.contains("f100.txt"))
    }

    @Test
    fun `should enforce max rendered events limit`() {
        val (timeline, _) = createTimeline()
        for (i in 1..1100) {
            timeline.renderEvent(AgentEvent.ToolCallCompleted("s1", "r1", "read_file", true, emptyMap()))
        }
        assertTrue("Should render at most 1000 events, got ${timeline.getRenderedEventCount()}",
            timeline.getRenderedEventCount() <= 1000)
    }

    @Test
    fun `should extract filename from full path`() {
        val (timeline, pane) = createTimeline()
        timeline.renderEvent(AgentEvent.FileRead("s1", "r1", "/home/user/project/src/main/File.kt", 100))
        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("File.kt"))
        assertFalse(text.contains("/home/user/project"))
    }

    @Test
    fun `should render complete agent flow`() {
        val (timeline, pane) = createTimeline()

        timeline.renderEvent(AgentEvent.RunStarted("s1", "r1", AgentMode.BUILDER_WITH_MCP, "gpt-4"))
        timeline.renderEvent(AgentEvent.ToolCallStarted("s1", "r1", "search_files", mapOf("pattern" to "CapabilityAdapter")))
        timeline.renderEvent(AgentEvent.FileSearch("s1", "r1", "CapabilityAdapter", 2))
        timeline.renderEvent(AgentEvent.ToolCallCompleted("s1", "r1", "search_files", true, emptyMap()))
        timeline.renderEvent(AgentEvent.ToolCallStarted("s1", "r1", "read_file", mapOf("filePath" to "CapabilityAdapter.kt")))
        timeline.renderEvent(AgentEvent.FileRead("s1", "r1", "CapabilityAdapter.kt", 237))
        timeline.renderEvent(AgentEvent.ToolCallCompleted("s1", "r1", "read_file", true, mapOf("filePath" to "CapabilityAdapter.kt", "lines" to 237)))
        timeline.renderEvent(AgentEvent.MCPToolCall("s1", "r1", "mcp_search", true, mapOf("query" to "WebSocket")))
        timeline.renderEvent(AgentEvent.DiffCreated("s1", "r1", "CapabilityAdapter.kt"))
        timeline.renderEvent(AgentEvent.DiffApplied("s1", "r1", "CapabilityAdapter.kt", true))
        timeline.renderEvent(AgentEvent.RunCompleted("s1", "r1", 5000))

        val text = pane.styledDocument.getText(0, pane.styledDocument.length)
        assertTrue(text.contains("Run started"))
        assertTrue(text.contains("search_files"))
        assertTrue(text.contains("CapabilityAdapter"))
        assertTrue(text.contains("read_file"))
        assertTrue(text.contains("237 lines"))
        assertTrue(text.contains("MCP"))
        assertTrue(text.contains("diff applied"))
        assertTrue(text.contains("Agent completed"))
        assertTrue(text.contains("5000ms"))
    }
}