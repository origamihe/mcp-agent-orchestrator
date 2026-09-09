package com.mcp.plugin.toolwindow

import com.mcp.plugin.session.AgentEvent
import com.mcp.plugin.session.AgentMode
import org.junit.Assert.*
import org.junit.Test
import javax.swing.JScrollPane
import javax.swing.JTextPane

class ExecutionTimelineTest {

    private fun createTimeline(): ExecutionTimeline {
        return ExecutionTimeline()
    }

    private fun getText(timeline: ExecutionTimeline): String {
        val scrollPane = timeline.component as JScrollPane
        val viewport = scrollPane.viewport
        val pane = viewport.view as JTextPane
        return pane.styledDocument.getText(0, pane.styledDocument.length)
    }

    @Test
    fun `should render RunStarted event`() {
        val timeline = createTimeline()
        val event = AgentEvent.RunStarted("s1", "r1", AgentMode.CHAT, "gpt-4")
        timeline.addEvent(event)
        val text = getText(timeline)
        assertTrue(text.contains("Chat"))
        assertTrue(text.contains("gpt-4"))
    }

    @Test
    fun `should render RunCompleted event`() {
        val timeline = createTimeline()
        timeline.addEvent(AgentEvent.RunCompleted("s1", "r1", 1234))
        val text = getText(timeline)
        assertTrue(text.contains("completed"))
        assertTrue(text.contains("1234ms"))
    }

    @Test
    fun `should render RunFailed event`() {
        val timeline = createTimeline()
        timeline.addEvent(AgentEvent.RunFailed("s1", "r1", "Connection timeout"))
        val text = getText(timeline)
        assertTrue(text.contains("failed"))
        assertTrue(text.contains("Connection timeout"))
    }

    @Test
    fun `should render RunCancelled event`() {
        val timeline = createTimeline()
        timeline.addEvent(AgentEvent.RunCancelled("s1", "r1"))
        val text = getText(timeline)
        assertTrue(text.contains("cancelled"))
    }

    @Test
    fun `should render UserMessage event`() {
        val timeline = createTimeline()
        timeline.addEvent(AgentEvent.UserMessage("s1", "Hello, Agent!"))
        val text = getText(timeline)
        assertTrue(text.contains("You:"))
        assertTrue(text.contains("Hello, Agent!"))
    }

    @Test
    fun `should render ToolCallStarted event`() {
        val timeline = createTimeline()
        timeline.addEvent(AgentEvent.ToolCallStarted("s1", "r1", "read_file", mapOf("filePath" to "/test.txt")))
        val text = getText(timeline)
        assertTrue(text.contains("read_file"))
    }

    @Test
    fun `should render successful ToolCallCompleted event`() {
        val timeline = createTimeline()
        timeline.addEvent(AgentEvent.ToolCallCompleted("s1", "r1", "read_file", true, mapOf("filePath" to "/test.txt", "lines" to 42)))
        val text = getText(timeline)
        assertTrue(text.contains("\u2713"))
        assertTrue(text.contains("read_file"))
        assertTrue(text.contains("42 lines"))
    }

    @Test
    fun `should render failed ToolCallCompleted event`() {
        val timeline = createTimeline()
        timeline.addEvent(AgentEvent.ToolCallCompleted("s1", "r1", "read_file", false, emptyMap()))
        val text = getText(timeline)
        assertTrue(text.contains("\u2717"))
    }

    @Test
    fun `should render ToolCallFailed event`() {
        val timeline = createTimeline()
        timeline.addEvent(AgentEvent.ToolCallFailed("s1", "r1", "read_file", "File not found"))
        val text = getText(timeline)
        assertTrue(text.contains("\u2717"))
        assertTrue(text.contains("File not found"))
    }

    @Test
    fun `should render FileRead event`() {
        val timeline = createTimeline()
        timeline.addEvent(AgentEvent.FileRead("s1", "r1", "/project/src/CapabilityAdapter.kt", 237, 0, 15))
        val text = getText(timeline)
        assertTrue(text.contains("CapabilityAdapter.kt"))
        assertTrue(text.contains("237 lines"))
    }

    @Test
    fun `should render FileSearch event`() {
        val timeline = createTimeline()
        timeline.addEvent(AgentEvent.FileSearch("s1", "r1", "CapabilityAdapter", 3))
        val text = getText(timeline)
        assertTrue(text.contains("3 files found"))
    }

    @Test
    fun `should render MCPToolCall event`() {
        val timeline = createTimeline()
        timeline.addEvent(AgentEvent.MCPToolCall("s1", "r1", "mcp_search", true, mapOf("query" to "test")))
        val text = getText(timeline)
        assertTrue(text.contains("MCP"))
        assertTrue(text.contains("mcp_search"))
    }

    @Test
    fun `should render DiffApplied event`() {
        val timeline = createTimeline()
        timeline.addEvent(AgentEvent.DiffApplied("s1", "r1", "/project/src/Test.kt", true))
        val text = getText(timeline)
        assertTrue(text.contains("diff applied"))
        assertTrue(text.contains("Test.kt"))
    }

    @Test
    fun `should render DiffCreated event`() {
        val timeline = createTimeline()
        timeline.addEvent(AgentEvent.DiffCreated("s1", "r1", "/project/src/Test.kt"))
        val text = getText(timeline)
        assertTrue(text.contains("diff created"))
        assertTrue(text.contains("Test.kt"))
    }

    @Test
    fun `should render Thinking event`() {
        val timeline = createTimeline()
        timeline.addEvent(AgentEvent.Thinking("s1", "r1", "Analyzing code..."))
        val text = getText(timeline)
        assertTrue(text.contains("Analyzing code"))
    }

    @Test
    fun `should not render FinalAnswer events`() {
        val timeline = createTimeline()
        timeline.addEvent(AgentEvent.FinalAnswer("s1", "r1", "Done"))
        val text = getText(timeline)
        assertEquals("", text.trim())
    }

    @Test
    fun `should handle many events without error`() {
        val timeline = createTimeline()
        for (i in 1..100) {
            timeline.addEvent(AgentEvent.ToolCallCompleted("s1", "r1", "read_file", true, mapOf("filePath" to "/f$i.txt", "lines" to i)))
        }
        val text = getText(timeline)
        assertTrue(text.contains("read_file"))
        assertTrue(text.contains("f1.txt"))
        assertTrue(text.contains("f100.txt"))
    }

    @Test
    fun `should enforce max rendered events limit`() {
        val timeline = createTimeline()
        for (i in 1..1100) {
            timeline.addEvent(AgentEvent.ToolCallCompleted("s1", "r1", "read_file", true, emptyMap()))
        }
        assertTrue("Should render at most 1000 events, got ${timeline.getEventCount()}",
            timeline.getEventCount() <= 1000)
    }

    @Test
    fun `should extract filename from full path`() {
        val timeline = createTimeline()
        timeline.addEvent(AgentEvent.FileRead("s1", "r1", "/home/user/project/src/main/File.kt", 100))
        val text = getText(timeline)
        assertTrue(text.contains("File.kt"))
        assertFalse(text.contains("/home/user/project"))
    }

    @Test
    fun `should render complete agent flow`() {
        val timeline = createTimeline()

        timeline.addEvent(AgentEvent.RunStarted("s1", "r1", AgentMode.BUILDER_WITH_MCP, "gpt-4"))
        timeline.addEvent(AgentEvent.ToolCallStarted("s1", "r1", "search_files", mapOf("pattern" to "CapabilityAdapter")))
        timeline.addEvent(AgentEvent.FileSearch("s1", "r1", "CapabilityAdapter", 2))
        timeline.addEvent(AgentEvent.ToolCallCompleted("s1", "r1", "search_files", true, emptyMap()))
        timeline.addEvent(AgentEvent.ToolCallStarted("s1", "r1", "read_file", mapOf("filePath" to "CapabilityAdapter.kt")))
        timeline.addEvent(AgentEvent.FileRead("s1", "r1", "CapabilityAdapter.kt", 237))
        timeline.addEvent(AgentEvent.ToolCallCompleted("s1", "r1", "read_file", true, mapOf("filePath" to "CapabilityAdapter.kt", "lines" to 237)))
        timeline.addEvent(AgentEvent.MCPToolCall("s1", "r1", "mcp_search", true, mapOf("query" to "WebSocket")))
        timeline.addEvent(AgentEvent.DiffCreated("s1", "r1", "CapabilityAdapter.kt"))
        timeline.addEvent(AgentEvent.DiffApplied("s1", "r1", "CapabilityAdapter.kt", true))
        timeline.addEvent(AgentEvent.RunCompleted("s1", "r1", 5000))

        val text = getText(timeline)
        assertTrue(text.contains("Builder"))
        assertTrue(text.contains("search_files"))
        assertTrue(text.contains("CapabilityAdapter"))
        assertTrue(text.contains("read_file"))
        assertTrue(text.contains("237 lines"))
        assertTrue(text.contains("MCP"))
        assertTrue(text.contains("diff applied"))
        assertTrue(text.contains("completed"))
        assertTrue(text.contains("5000ms"))
    }

    @Test
    fun `should show reconnect indicator on generation change`() {
        val timeline = createTimeline()
        timeline.addEvent(AgentEvent.RunStarted("s1", "r1", AgentMode.CHAT, null, 1))
        timeline.addEvent(AgentEvent.RunCompleted("s1", "r1", 100, 2))
        val text = getText(timeline)
        assertTrue(text.contains("Reconnected"))
    }

    @Test
    fun `clear should reset event count`() {
        val timeline = createTimeline()
        timeline.addEvent(AgentEvent.RunStarted("s1", "r1", AgentMode.CHAT, null))
        assertEquals(1, timeline.getEventCount())
        timeline.clear()
        assertEquals(0, timeline.getEventCount())
    }
}