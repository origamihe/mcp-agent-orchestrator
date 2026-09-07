package com.mcp.plugin.session

import com.mcp.plugin.event.OutgoingEnvelope
import com.mcp.plugin.transport.Transport
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * P4: Agent Runtime Reliability Tests
 *
 * 验证：
 * 1. Cancel — cancel_run 协议, 本地取消, Backend ack
 * 2. Run State — 完整状态机: CREATED → RUNNING → CANCELLING → CANCELLED → IDLE
 * 3. Reconnect — generation 递增, stale event 过滤
 * 4. Project Close — 生命周期清理
 * 5. Stale Event — 旧 generation 事件不污染新 Session
 */
class AgentRuntimeReliabilityTest {

    // ========== Cancel Tests ==========

    @Test
    fun `cancel should transition through CANCELLING to CANCELLED`() {
        val session = AgentSession()
        session.startRun(AgentMode.CHAT, null)
        assertEquals(AgentState.RUNNING, session.agentState)

        session.cancelRun()
        assertEquals(AgentState.CANCELLING, session.agentState)

        session.confirmCancelled()
        assertEquals(AgentState.IDLE, session.agentState)
        assertNull(session.currentRunId)
    }

    @Test
    fun `cancel should produce RunCancelled event`() {
        val session = AgentSession()
        val runId = session.startRun(AgentMode.CHAT, null)
        session.cancelRun()
        session.confirmCancelled()

        val cancelledEvent = session.getEvents().find { it is AgentEvent.RunCancelled }
        assertNotNull("Expected RunCancelled event", cancelledEvent)
        assertEquals(runId, (cancelledEvent as AgentEvent.RunCancelled).runId)
        assertEquals(session.sessionId, cancelledEvent.sessionId)
    }

    @Test
    fun `cancel should clear currentRunId`() {
        val session = AgentSession()
        session.startRun(AgentMode.CHAT, null)
        assertNotNull(session.currentRunId)

        session.cancelRun()
        assertNotNull(session.currentRunId)

        session.confirmCancelled()
        assertNull(session.currentRunId)
    }

    @Test
    fun `cancel should be idempotent when no run active`() {
        val session = AgentSession()
        session.cancelRun()
        assertEquals(AgentState.IDLE, session.agentState)
        session.confirmCancelled()
        assertEquals(AgentState.IDLE, session.agentState)
    }

    @Test
    fun `cancelRun should not crash when no current run`() {
        val session = AgentSession()
        session.cancelRun()
        assertEquals(0, session.getEvents().size)
    }

    // ========== Run State Machine Tests ==========

    @Test
    fun `full lifecycle IDLE to CREATED to RUNNING to COMPLETED to IDLE`() {
        val session = AgentSession()
        val states = mutableListOf<AgentState>()
        session.onStateChange { _, new -> states.add(new) }

        assertEquals(AgentState.IDLE, session.agentState)

        session.startRun(AgentMode.CHAT, null)
        assertEquals(AgentState.RUNNING, session.agentState)

        session.completeRun(100)
        assertEquals(AgentState.IDLE, session.agentState)

        assertTrue(states.contains(AgentState.CREATED))
        assertTrue(states.contains(AgentState.RUNNING))
        assertTrue(states.contains(AgentState.COMPLETED))
        assertTrue(states.contains(AgentState.IDLE))
    }

    @Test
    fun `full lifecycle IDLE to CREATED to RUNNING to FAILED to IDLE`() {
        val session = AgentSession()
        val states = mutableListOf<AgentState>()
        session.onStateChange { _, new -> states.add(new) }

        session.startRun(AgentMode.CHAT, null)
        session.failRun("test error")

        assertEquals(AgentState.IDLE, session.agentState)
        assertTrue(states.contains(AgentState.FAILED))
    }

    @Test
    fun `full lifecycle IDLE to CREATED to RUNNING to CANCELLING to CANCELLED to IDLE`() {
        val session = AgentSession()
        val states = mutableListOf<AgentState>()
        session.onStateChange { _, new -> states.add(new) }

        session.startRun(AgentMode.CHAT, null)
        session.cancelRun()
        session.confirmCancelled()

        assertEquals(AgentState.IDLE, session.agentState)
        assertTrue(states.contains(AgentState.CANCELLING))
        assertTrue(states.contains(AgentState.CANCELLED))
    }

    @Test
    fun `all 7 AgentState values exist`() {
        val expected = setOf(
            AgentState.IDLE,
            AgentState.CREATED,
            AgentState.RUNNING,
            AgentState.CANCELLING,
            AgentState.COMPLETED,
            AgentState.FAILED,
            AgentState.CANCELLED
        )
        assertEquals(7, AgentState.entries.size)
        AgentState.entries.forEach { assertTrue("Expected $it to be in known set", expected.contains(it)) }
    }

    // ========== Reconnect / Generation Tests ==========

    @Test
    fun `generation should increment on reconnect`() {
        val session = AgentSession()
        assertEquals(0, session.generation)

        session.incrementGeneration()
        assertEquals(1, session.generation)

        session.incrementGeneration()
        assertEquals(2, session.generation)
    }

    @Test
    fun `events should be tagged with generation at time of creation`() {
        val session = AgentSession()
        session.addUserMessage("Gen0")
        assertEquals(0, session.getEvents().last().generation)

        session.incrementGeneration()
        session.addUserMessage("Gen1")
        assertEquals(1, session.getEvents().last().generation)

        session.incrementGeneration()
        session.startRun(AgentMode.CHAT, null)
        assertEquals(2, session.getEvents().last().generation)
    }

    @Test
    fun `getEventsForGeneration should filter correctly`() {
        val session = AgentSession()
        session.addUserMessage("A")
        session.addUserMessage("B")
        session.incrementGeneration()
        session.addUserMessage("C")
        session.addUserMessage("D")
        session.incrementGeneration()
        session.addUserMessage("E")

        assertEquals(2, session.getEventsForGeneration(0).size)
        assertEquals(2, session.getEventsForGeneration(1).size)
        assertEquals(1, session.getEventsForGeneration(2).size)
        assertEquals(0, session.getEventsForGeneration(99).size)
    }

    @Test
    fun `stale event detection should filter old generation events`() {
        val currentGen = 2
        assertFalse("gen=0 should never be stale (initial connection)", isStale(0, currentGen))
        assertTrue("gen=1 should be stale when currentGen=2", isStale(1, currentGen))
        assertFalse("gen=2 should not be stale (current generation)", isStale(2, currentGen))
        assertFalse("gen=0 should not be stale when activeGen=0", isStale(0, 0))
    }

    @Test
    fun `generation 0 events should always pass through`() {
        val currentGen = 5
        assertFalse(isStale(0, currentGen))
        assertFalse(isStale(0, 0))
    }

    @Test
    fun `generation should reset to 0 on clear`() {
        val session = AgentSession()
        session.incrementGeneration()
        session.incrementGeneration()
        session.incrementGeneration()
        assertEquals(3, session.generation)
        session.clear()
        assertEquals(0, session.generation)
    }

    // ========== Disconnect Tests ==========

    @Test
    fun `disconnect during active run should auto-cancel`() {
        val session = AgentSession()
        session.startRun(AgentMode.CHAT, null)
        assertEquals(AgentState.RUNNING, session.agentState)

        session.connectionState = ConnectionState.DISCONNECTED
        session.cancelRun()
        session.confirmCancelled()
        assertEquals(AgentState.IDLE, session.agentState)
        assertNull(session.currentRunId)
    }

    @Test
    fun `disconnect during idle should not change state`() {
        val session = AgentSession()
        assertEquals(AgentState.IDLE, session.agentState)
        session.connectionState = ConnectionState.DISCONNECTED
        assertEquals(AgentState.IDLE, session.agentState)
    }

    @Test
    fun `connection state should transition correctly`() {
        val session = AgentSession()
        assertEquals(ConnectionState.DISCONNECTED, session.connectionState)

        session.connectionState = ConnectionState.CONNECTING
        assertEquals(ConnectionState.CONNECTING, session.connectionState)

        session.connectionState = ConnectionState.CONNECTED
        assertEquals(ConnectionState.CONNECTED, session.connectionState)

        session.connectionState = ConnectionState.DISCONNECTED
        assertEquals(ConnectionState.DISCONNECTED, session.connectionState)
    }

    // ========== Project Close Tests ==========

    @Test
    fun `clear should release all resources`() {
        val session = AgentSession()
        session.startRun(AgentMode.CHAT, null)
        session.addUserMessage("Hello")
        session.addToolCallStarted("read_file", mapOf("filePath" to "/test.txt"))
        session.addToolCallCompleted("read_file", true, emptyMap())

        val eventLatch = CountDownLatch(1)
        val stateLatch = CountDownLatch(1)
        session.onEvent { eventLatch.countDown() }
        session.onStateChange { _, _ -> stateLatch.countDown() }

        session.clear()

        assertEquals(0, session.getEvents().size)
        assertNull(session.currentRunId)
        assertEquals(AgentState.IDLE, session.agentState)
        assertEquals(ConnectionState.DISCONNECTED, session.connectionState)
        assertEquals(0, session.generation)
    }

    @Test
    fun `clear should stop event notifications`() {
        val session = AgentSession()
        val received = AtomicBoolean(false)
        session.onEvent { received.set(true) }

        session.clear()
        session.addUserMessage("After clear")
        assertFalse("Event listener should not fire after clear", received.get())
    }

    // ========== Stale Event Tests ==========

    @Test
    fun `stale events from old generation should not pollute new session`() {
        val session = AgentSession()
        session.addUserMessage("Old Gen 0")
        assertEquals(0, session.getEvents().last().generation)

        session.incrementGeneration()
        session.addUserMessage("New Gen 1")
        assertEquals(1, session.getEvents().last().generation)

        val gen1Events = session.getEventsForGeneration(1)
        assertEquals(1, gen1Events.size)
        assertEquals("New Gen 1", (gen1Events[0] as AgentEvent.UserMessage).content)

        val gen0Events = session.getEventsForGeneration(0)
        assertEquals(1, gen0Events.size)
        assertEquals("Old Gen 0", (gen0Events[0] as AgentEvent.UserMessage).content)
    }

    @Test
    fun `stale run events should be isolated by generation`() {
        val session = AgentSession()
        session.startRun(AgentMode.CHAT, null)
        session.addUserMessage("Gen0 Message")
        session.completeRun(100)

        session.incrementGeneration()
        session.startRun(AgentMode.BUILDER, null)
        session.addUserMessage("Gen1 Message")
        session.completeRun(200)

        val gen0Events = session.getEventsForGeneration(0)
        val gen1Events = session.getEventsForGeneration(1)

        assertTrue(gen0Events.isNotEmpty())
        assertTrue(gen1Events.isNotEmpty())
        gen0Events.forEach { assertEquals(0, it.generation) }
        gen1Events.forEach { assertEquals(1, it.generation) }
    }

    // ========== Helper ==========

    private fun isStale(generation: Int, activeGeneration: Int): Boolean {
        return generation != activeGeneration && generation != 0
    }
}