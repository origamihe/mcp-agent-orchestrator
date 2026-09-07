package com.mcp.plugin.session

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AgentSessionTest {

    @Test
    fun `should create session with unique sessionId`() {
        val session1 = AgentSession()
        val session2 = AgentSession()
        assertNotEquals(session1.sessionId, session2.sessionId)
    }

    @Test
    fun `should start with DISCONNECTED state`() {
        val session = AgentSession()
        assertEquals(ConnectionState.DISCONNECTED, session.connectionState)
    }

    @Test
    fun `should start with IDLE agent state`() {
        val session = AgentSession()
        assertEquals(AgentState.IDLE, session.agentState)
    }

    @Test
    fun `should default to CHAT mode`() {
        val session = AgentSession()
        assertEquals(AgentMode.CHAT, session.mode)
    }

    @Test
    fun `should set connection state`() {
        val session = AgentSession()
        session.connectionState = ConnectionState.CONNECTING
        assertEquals(ConnectionState.CONNECTING, session.connectionState)
    }

    @Test
    fun `should set agent state`() {
        val session = AgentSession()
        session.agentState = AgentState.RUNNING
        assertEquals(AgentState.RUNNING, session.agentState)
    }

    @Test
    fun `should set mode`() {
        val session = AgentSession()
        session.mode = AgentMode.BUILDER
        assertEquals(AgentMode.BUILDER, session.mode)
    }

    @Test
    fun `should set model config id`() {
        val session = AgentSession()
        session.modelConfigId = "gpt-4"
        assertEquals("gpt-4", session.modelConfigId)
    }

    @Test
    fun `should start run and generate runId`() {
        val session = AgentSession()
        val runId = session.startRun(AgentMode.CHAT, null)
        assertNotNull(runId)
        assertTrue(runId.startsWith("run-"))
        assertEquals(runId, session.currentRunId)
        assertEquals(AgentState.RUNNING, session.agentState)
    }

    @Test
    fun `should complete run and return to IDLE`() {
        val session = AgentSession()
        session.startRun(AgentMode.CHAT, null)
        session.completeRun(100)
        assertNull(session.currentRunId)
        assertEquals(AgentState.IDLE, session.agentState)
    }

    @Test
    fun `should fail run and return to IDLE`() {
        val session = AgentSession()
        session.startRun(AgentMode.CHAT, null)
        session.failRun("test error")
        assertNull(session.currentRunId)
        assertEquals(AgentState.IDLE, session.agentState)
    }

    @Test
    fun `should cancel run and set CANCELLING state`() {
        val session = AgentSession()
        session.startRun(AgentMode.CHAT, null)
        session.cancelRun()
        assertEquals(AgentState.CANCELLING, session.agentState)
    }

    @Test
    fun `should add user message event`() {
        val session = AgentSession()
        session.addUserMessage("Hello")
        val events = session.getEvents()
        assertEquals(1, events.size)
        assertTrue(events[0] is AgentEvent.UserMessage)
        assertEquals("Hello", (events[0] as AgentEvent.UserMessage).content)
        assertEquals(session.sessionId, events[0].sessionId)
    }

    @Test
    fun `events should bind sessionId`() {
        val session = AgentSession()
        session.addUserMessage("Hello")
        session.addToolCallStarted("read_file", mapOf("filePath" to "/test.txt"))
        session.addToolCallCompleted("read_file", true, emptyMap())
        session.addFileRead("/test.txt", 100)
        session.addFileSearch("*.kt", 5)
        session.addMCPToolCall("mcp_search", true, emptyMap())
        val events = session.getEvents()
        events.forEach { event ->
            assertEquals(session.sessionId, event.sessionId)
        }
    }

    @Test
    fun `events should bind runId when inside a run`() {
        val session = AgentSession()
        val runId = session.startRun(AgentMode.CHAT, null)
        session.addUserMessage("Hello")
        session.addToolCallStarted("read_file", mapOf("filePath" to "/test.txt"))
        session.addToolCallCompleted("read_file", true, emptyMap())
        session.addFileRead("/test.txt", 100)
        session.addFileSearch("*.kt", 5)
        session.addMCPToolCall("mcp_search", true, emptyMap())
        session.completeRun(100)

        val events = session.getEvents()
        val toolEvents = events.filter { it !is AgentEvent.RunStarted && it !is AgentEvent.RunCompleted }
        toolEvents.forEach { event ->
            assertEquals("Event $event should have runId $runId", runId, event.runId)
        }
    }

    @Test
    fun `should filter events by runId with proper isolation`() {
        val session = AgentSession()
        val runId1 = session.startRun(AgentMode.CHAT, null)
        session.addUserMessage("Msg1")
        session.addToolCallStarted("read_file", mapOf("filePath" to "/a.txt"))
        session.addToolCallCompleted("read_file", true, emptyMap())
        session.completeRun(100)

        val runId2 = session.startRun(AgentMode.BUILDER, null)
        session.addUserMessage("Msg2")
        session.addToolCallStarted("search_files", mapOf("pattern" to "*.kt"))
        session.addToolCallCompleted("search_files", true, emptyMap())
        session.completeRun(200)

        val eventsForRun1 = session.getEventsForRun(runId1)
        assertTrue(eventsForRun1.isNotEmpty())
        eventsForRun1.forEach { event ->
            val eventRunId = event.runId
            if (eventRunId != null) {
                assertEquals("Run 1 events should not contain runId2 events", runId1, eventRunId)
            }
        }

        val eventsForRun2 = session.getEventsForRun(runId2)
        assertTrue(eventsForRun2.isNotEmpty())
        eventsForRun2.forEach { event ->
            val eventRunId = event.runId
            if (eventRunId != null) {
                assertEquals("Run 2 events should not contain runId1 events", runId2, eventRunId)
            }
        }
    }

    @Test
    fun `should add tool call events`() {
        val session = AgentSession()
        session.addToolCallStarted("read_file", mapOf("filePath" to "/test.txt"))
        session.addToolCallCompleted("read_file", true, mapOf("lines" to 10))
        val events = session.getEvents()
        assertEquals(2, events.size)
        assertTrue(events[0] is AgentEvent.ToolCallStarted)
        assertTrue(events[1] is AgentEvent.ToolCallCompleted)
    }

    @Test
    fun `should add tool call failed event`() {
        val session = AgentSession()
        session.addToolCallFailed("read_file", "File not found")
        val events = session.getEvents()
        assertEquals(1, events.size)
        val event = events[0] as AgentEvent.ToolCallFailed
        assertEquals("read_file", event.capability)
        assertEquals("File not found", event.error)
    }

    @Test
    fun `should add file read event`() {
        val session = AgentSession()
        session.addFileRead("/test.txt", 100, 1024, 50)
        val events = session.getEvents()
        assertEquals(1, events.size)
        val event = events[0] as AgentEvent.FileRead
        assertEquals("/test.txt", event.filePath)
        assertEquals(100, event.lines)
        assertEquals(1024, event.bytes)
        assertEquals(50, event.durationMs)
    }

    @Test
    fun `should add file search event`() {
        val session = AgentSession()
        session.addFileSearch("*.kt", 5)
        val events = session.getEvents()
        assertEquals(1, events.size)
        val event = events[0] as AgentEvent.FileSearch
        assertEquals("*.kt", event.query)
        assertEquals(5, event.matchCount)
    }

    @Test
    fun `should add final answer event`() {
        val session = AgentSession()
        session.addFinalAnswer("Done", "run-123")
        val events = session.getEvents()
        assertEquals(1, events.size)
        val event = events[0] as AgentEvent.FinalAnswer
        assertEquals("Done", event.content)
        assertEquals("run-123", event.runId)
    }

    @Test
    fun `should evict oldest events when exceeding max size`() {
        val session = AgentSession()
        for (i in 1..600) {
            session.addUserMessage("Message $i")
        }
        val events = session.getEvents()
        assertTrue(events.size <= 500)
        val firstEvent = events.first() as AgentEvent.UserMessage
        assertTrue(firstEvent.content.startsWith("Message"))
    }

    @Test
    fun `should notify event listeners`() {
        val session = AgentSession()
        val latch = CountDownLatch(1)
        val receivedEvents = mutableListOf<AgentEvent>()
        session.onEvent { event ->
            receivedEvents.add(event)
            latch.countDown()
        }
        session.addUserMessage("Test")
        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(1, receivedEvents.size)
    }

    @Test
    fun `should notify state change listeners`() {
        val session = AgentSession()
        val latch = CountDownLatch(2)
        val stateChanges = mutableListOf<Pair<AgentState, AgentState>>()
        session.onStateChange { old, new ->
            stateChanges.add(old to new)
            latch.countDown()
        }
        session.startRun(AgentMode.CHAT, null)
        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(2, stateChanges.size)
        assertEquals(AgentState.IDLE, stateChanges[0].first)
        assertEquals(AgentState.CREATED, stateChanges[0].second)
        assertEquals(AgentState.CREATED, stateChanges[1].first)
        assertEquals(AgentState.RUNNING, stateChanges[1].second)
    }

    @Test
    fun `should clear all state on dispose`() {
        val session = AgentSession()
        session.startRun(AgentMode.CHAT, null)
        session.addUserMessage("Hello")
        session.clear()
        assertEquals(0, session.getEvents().size)
        assertNull(session.currentRunId)
        assertEquals(AgentState.IDLE, session.agentState)
        assertEquals(ConnectionState.DISCONNECTED, session.connectionState)
    }

    @Test
    fun `should produce correct RunStarted event`() {
        val session = AgentSession()
        val runId = session.startRun(AgentMode.BUILDER, "gpt-4")
        val events = session.getEvents()
        assertEquals(1, events.size)
        val event = events[0] as AgentEvent.RunStarted
        assertEquals(runId, event.runId)
        assertEquals(AgentMode.BUILDER, event.mode)
        assertEquals("gpt-4", event.modelConfigId)
    }

    @Test
    fun `should produce correct RunCompleted event`() {
        val session = AgentSession()
        val runId = session.startRun(AgentMode.CHAT, null)
        session.completeRun(1234)
        val events = session.getEvents()
        val completedEvent = events.find { it is AgentEvent.RunCompleted } as? AgentEvent.RunCompleted
        assertNotNull(completedEvent)
        assertEquals(runId, completedEvent!!.runId)
        assertEquals(1234, completedEvent!!.durationMs)
    }

    @Test
    fun `should produce correct RunFailed event`() {
        val session = AgentSession()
        val runId = session.startRun(AgentMode.CHAT, null)
        session.failRun("Connection timeout")
        val events = session.getEvents()
        val failedEvent = events.find { it is AgentEvent.RunFailed } as? AgentEvent.RunFailed
        assertNotNull(failedEvent)
        assertEquals(runId, failedEvent!!.runId)
        assertEquals("Connection timeout", failedEvent!!.error)
    }

    @Test
    fun `should filter events by runId`() {
        val session = AgentSession()
        val runId1 = session.startRun(AgentMode.CHAT, null)
        session.addUserMessage("Msg1")
        session.completeRun(100)

        session.startRun(AgentMode.BUILDER, null)
        session.addUserMessage("Msg2")
        session.completeRun(200)

        val eventsForRun1 = session.getEventsForRun(runId1)
        assertTrue(eventsForRun1.isNotEmpty())
        val runStarted = eventsForRun1.find { it is AgentEvent.RunStarted && it.runId == runId1 }
        assertNotNull(runStarted)
    }

    @Test
    fun `should handle concurrent event addition`() {
        val session = AgentSession()
        val threadCount = 10
        val eventsPerThread = 100
        val latch = CountDownLatch(threadCount)
        val threads = (1..threadCount).map { t ->
            Thread {
                repeat(eventsPerThread) { i ->
                    session.addUserMessage("Thread-$t-Msg-$i")
                }
                latch.countDown()
            }
        }
        threads.forEach { it.start() }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        val events = session.getEvents()
        assertTrue(events.size <= 500)
        assertTrue(events.size >= threadCount)
    }

    // ========== P4: Agent Runtime Reliability Tests ==========

    @Test
    fun `should transition through all run states CREATED to RUNNING to COMPLETED to IDLE`() {
        val session = AgentSession()
        val stateLog = mutableListOf<AgentState>()
        session.onStateChange { _, new -> stateLog.add(new) }

        val runId = session.startRun(AgentMode.CHAT, null)
        assertEquals(AgentState.RUNNING, session.agentState)

        session.completeRun(100)
        assertEquals(AgentState.IDLE, session.agentState)

        assertTrue(stateLog.contains(AgentState.CREATED))
        assertTrue(stateLog.contains(AgentState.RUNNING))
        assertTrue(stateLog.contains(AgentState.COMPLETED))
        assertTrue(stateLog.contains(AgentState.IDLE))
    }

    @Test
    fun `should transition to FAILED and back to IDLE`() {
        val session = AgentSession()
        session.startRun(AgentMode.CHAT, null)
        session.failRun("test error")
        assertNull(session.currentRunId)
        assertEquals(AgentState.IDLE, session.agentState)
    }

    @Test
    fun `should transition to CANCELLING then CANCELLED then IDLE`() {
        val session = AgentSession()
        session.startRun(AgentMode.CHAT, null)
        session.cancelRun()
        assertEquals(AgentState.CANCELLING, session.agentState)
        session.confirmCancelled()
        assertNull(session.currentRunId)
        assertEquals(AgentState.IDLE, session.agentState)
    }

    @Test
    fun `should produce RunCancelled event`() {
        val session = AgentSession()
        val runId = session.startRun(AgentMode.CHAT, null)
        session.cancelRun()
        session.confirmCancelled()
        val cancelledEvent = session.getEvents().find { it is AgentEvent.RunCancelled }
        assertNotNull(cancelledEvent)
        assertEquals(runId, (cancelledEvent as AgentEvent.RunCancelled).runId)
    }

    @Test
    fun `should start with generation 0`() {
        val session = AgentSession()
        assertEquals(0, session.generation)
    }

    @Test
    fun `should increment generation`() {
        val session = AgentSession()
        session.incrementGeneration()
        assertEquals(1, session.generation)
        session.incrementGeneration()
        assertEquals(2, session.generation)
    }

    @Test
    fun `should filter events by generation`() {
        val session = AgentSession()
        session.addUserMessage("Gen0-Msg")
        session.incrementGeneration()
        session.addUserMessage("Gen1-Msg")
        session.incrementGeneration()
        session.addUserMessage("Gen2-Msg")

        val gen0Events = session.getEventsForGeneration(0)
        assertEquals(1, gen0Events.size)
        assertEquals("Gen0-Msg", (gen0Events[0] as AgentEvent.UserMessage).content)

        val gen1Events = session.getEventsForGeneration(1)
        assertEquals(1, gen1Events.size)
        assertEquals("Gen1-Msg", (gen1Events[0] as AgentEvent.UserMessage).content)

        val gen2Events = session.getEventsForGeneration(2)
        assertEquals(1, gen2Events.size)
        assertEquals("Gen2-Msg", (gen2Events[0] as AgentEvent.UserMessage).content)
    }

    @Test
    fun `should tag events with current generation`() {
        val session = AgentSession()
        session.addUserMessage("Gen0")
        assertEquals(0, session.getEvents()[0].generation)

        session.incrementGeneration()
        session.addUserMessage("Gen1")
        assertEquals(1, session.getEvents().last().generation)
    }

    @Test
    fun `should clear generation to 0 on dispose`() {
        val session = AgentSession()
        session.incrementGeneration()
        session.incrementGeneration()
        assertEquals(2, session.generation)
        session.clear()
        assertEquals(0, session.generation)
    }

    @Test
    fun `should include RunCancelled in getEventsForRun`() {
        val session = AgentSession()
        val runId = session.startRun(AgentMode.CHAT, null)
        session.cancelRun()
        session.confirmCancelled()

        val runEvents = session.getEventsForRun(runId)
        val cancelledEvent = runEvents.find { it is AgentEvent.RunCancelled }
        assertNotNull(cancelledEvent)
    }

    @Test
    fun `confirmCancelled should do nothing if no active run`() {
        val session = AgentSession()
        session.confirmCancelled()
        assertEquals(AgentState.IDLE, session.agentState)
        assertEquals(0, session.getEvents().size)
    }
}