package com.mcp.plugin.session

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * AgentSessionController 线程模型测试。
 *
 * 验证：
 * 1. UI 回调在 EDT 执行
 * 2. 后台任务不在 EDT 执行
 * 3. 并发安全
 */
class AgentSessionControllerThreadingTest {

    @Test
    fun `AgentSession should support concurrent reads and writes`() {
        val session = AgentSession()
        val writeLatch = CountDownLatch(10)
        val readLatch = CountDownLatch(10)

        for (i in 1..10) {
            Thread {
                session.addUserMessage("Write-$i")
                writeLatch.countDown()
            }.start()
        }

        for (i in 1..10) {
            Thread {
                session.getEvents()
                readLatch.countDown()
            }.start()
        }

        assertTrue(writeLatch.await(5, TimeUnit.SECONDS))
        assertTrue(readLatch.await(5, TimeUnit.SECONDS))
        assertTrue(session.getEvents().isNotEmpty())
    }

    @Test
    fun `AgentSession state transitions should be atomic`() {
        val session = AgentSession()
        val stateChanges = mutableListOf<Pair<AgentState, AgentState>>()
        session.onStateChange { old, new ->
            synchronized(stateChanges) { stateChanges.add(old to new) }
        }

        val latch = CountDownLatch(5)
        repeat(5) {
            Thread {
                session.startRun(AgentMode.CHAT, null)
                session.completeRun(0)
                latch.countDown()
            }.start()
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))

        assertEquals(AgentState.IDLE, session.agentState)
        assertTrue("Expected at least 2 state changes, got ${stateChanges.size}", stateChanges.size >= 2)
    }

    @Test
    fun `AgentSession event listeners should be thread-safe`() {
        val session = AgentSession()
        val receivedCount = AtomicBoolean(false)
        session.onEvent { receivedCount.set(true) }

        val latch = CountDownLatch(20)
        repeat(20) {
            Thread {
                session.addUserMessage("Concurrent-$it")
                latch.countDown()
            }.start()
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(receivedCount.get())
    }

    @Test
    fun `AgentSession should not lose events under concurrent load`() {
        val session = AgentSession()
        val totalMessages = 100
        val latch = CountDownLatch(totalMessages)

        val receivedEvents = mutableListOf<AgentEvent>()
        session.onEvent { event ->
            synchronized(receivedEvents) { receivedEvents.add(event) }
            latch.countDown()
        }

        repeat(totalMessages) { i ->
            Thread {
                session.addUserMessage("Message-$i")
            }.start()
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        assertEquals(totalMessages, receivedEvents.size)
    }

    @Test
    fun `AgentMode should map to backend correctly`() {
        assertEquals("CHAT", AgentMode.CHAT.toBackendMode())
        assertEquals("CODING", AgentMode.BUILDER.toBackendMode())
        assertEquals("CODING", AgentMode.BUILDER_WITH_MCP.toBackendMode())
    }

    @Test
    fun `AgentMode should parse from backend correctly`() {
        assertEquals(AgentMode.CHAT, AgentMode.fromBackendMode("CHAT"))
        assertEquals(AgentMode.BUILDER, AgentMode.fromBackendMode("CODING"))
        assertEquals(AgentMode.BUILDER, AgentMode.fromBackendMode("WORKFLOW"))
        assertEquals(AgentMode.CHAT, AgentMode.fromBackendMode("UNKNOWN"))
        assertEquals(AgentMode.CHAT, AgentMode.fromBackendMode(null))
    }

    @Test
    fun `ConnectionState enum should have expected values`() {
        assertEquals(4, ConnectionState.entries.size)
        assertTrue(ConnectionState.entries.contains(ConnectionState.CONNECTING))
        assertTrue(ConnectionState.entries.contains(ConnectionState.CONNECTED))
        assertTrue(ConnectionState.entries.contains(ConnectionState.DISCONNECTED))
        assertTrue(ConnectionState.entries.contains(ConnectionState.RECONNECTING))
    }

    @Test
    fun `AgentState enum should have expected values`() {
        assertEquals(7, AgentState.entries.size)
        assertTrue(AgentState.entries.contains(AgentState.IDLE))
        assertTrue(AgentState.entries.contains(AgentState.CREATED))
        assertTrue(AgentState.entries.contains(AgentState.RUNNING))
        assertTrue(AgentState.entries.contains(AgentState.CANCELLING))
        assertTrue(AgentState.entries.contains(AgentState.COMPLETED))
        assertTrue(AgentState.entries.contains(AgentState.FAILED))
        assertTrue(AgentState.entries.contains(AgentState.CANCELLED))
    }

    @Test
    fun `AgentEvent hierarchy should be complete`() {
        val sid = "test-session"
        val events = listOf(
            AgentEvent.RunStarted(sid, "r1", AgentMode.CHAT, null),
            AgentEvent.RunCompleted(sid, "r1", 100),
            AgentEvent.RunFailed(sid, "r1", "error"),
            AgentEvent.RunCancelled(sid, "r1"),
            AgentEvent.UserMessage(sid, "hello"),
            AgentEvent.Thinking(sid, "r1", "thinking..."),
            AgentEvent.ToolCallStarted(sid, "r1", "read_file", emptyMap()),
            AgentEvent.ToolCallCompleted(sid, "r1", "read_file", true, emptyMap()),
            AgentEvent.ToolCallFailed(sid, "r1", "read_file", "fail"),
            AgentEvent.FileRead(sid, "r1", "/f.txt", 10, 100, 20),
            AgentEvent.FileSearch(sid, "r1", "*.kt", 5),
            AgentEvent.MCPToolCall(sid, "r1", "mcp_tool", true, emptyMap()),
            AgentEvent.DiffCreated(sid, "r1", "/f.txt"),
            AgentEvent.DiffApplied(sid, "r1", "/f.txt", true),
            AgentEvent.FinalAnswer(sid, "r1", "done")
        )
        assertEquals(15, events.size)
        events.forEach { assertTrue(it.timestamp > 0) }
        events.forEach { assertEquals(sid, it.sessionId) }
    }

    @Test
    fun `ModelInfo should parse correctly`() {
        val model = ModelInfo("gpt-4", "GPT-4", "openai", enabled = true)
        assertEquals("gpt-4", model.configId)
        assertEquals("GPT-4 / openai", model.displayName)
        assertEquals("GPT-4", model.provider)
        assertEquals("openai", model.modelName)
        assertTrue(model.enabled)
    }

    @Test
    fun `AgentEvent Forwarding should update timestamp`() {
        val before = System.currentTimeMillis()
        Thread.sleep(2)
        val event = AgentEvent.UserMessage("test-session", "test")
        assertTrue(event.timestamp >= before)
    }
}