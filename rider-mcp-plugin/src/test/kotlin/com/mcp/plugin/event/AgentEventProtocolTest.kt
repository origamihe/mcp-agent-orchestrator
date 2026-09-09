package com.mcp.plugin.event

import com.mcp.plugin.session.AgentEvent
import com.mcp.plugin.session.AgentMode
import org.junit.Assert.*
import org.junit.Test

/**
 * AgentEvent 协议与 IncomingEnvelope 测试。
 *
 * 验证：
 * 1. IncomingEnvelope 序列化/反序列化
 * 2. Protocol.fromJson() 完整字段
 * 3. Protocol.fromJson() 缺失可选字段
 * 4. OutgoingEnvelope 完整字段
 */
class AgentEventProtocolTest {

    @Test
    fun `IncomingEnvelope should parse capability_call`() {
        val json = """{
            "type": "capability_call",
            "callId": "call-123",
            "capability": "read_file",
            "params": {"filePath": "/test.txt"}
        }"""
        val envelope = Protocol.fromJson(json)
        assertEquals("capability_call", envelope.type)
        assertEquals("call-123", envelope.callId)
        assertEquals("read_file", envelope.capability)
        assertEquals("/test.txt", envelope.params?.get("filePath"))
    }

    @Test
    fun `IncomingEnvelope should parse reply`() {
        val json = """{
            "type": "reply",
            "content": "Here is the answer",
            "generation": 2,
            "runId": "run-abc"
        }"""
        val envelope = Protocol.fromJson(json)
        assertEquals("reply", envelope.type)
        assertEquals("Here is the answer", envelope.content)
        assertEquals(2, envelope.generation)
        assertEquals("run-abc", envelope.runId)
    }

    @Test
    fun `IncomingEnvelope should parse agent_event`() {
        val json = """{
            "type": "agent_event",
            "eventType": "TOOL_CALL",
            "runId": "run-xyz",
            "generation": 1,
            "payload": {"toolName": "mcp_search", "success": true}
        }"""
        val envelope = Protocol.fromJson(json)
        assertEquals("agent_event", envelope.type)
        assertEquals("TOOL_CALL", envelope.eventType)
        assertEquals("run-xyz", envelope.runId)
        assertEquals(1, envelope.generation)
        assertEquals("mcp_search", envelope.payload?.get("toolName"))
        assertEquals(true, envelope.payload?.get("success"))
    }

    @Test
    fun `IncomingEnvelope should parse cancel_run_ack`() {
        val json = """{
            "type": "cancel_run_ack",
            "runId": "run-cancel"
        }"""
        val envelope = Protocol.fromJson(json)
        assertEquals("cancel_run_ack", envelope.type)
        assertEquals("run-cancel", envelope.runId)
    }

    @Test
    fun `IncomingEnvelope should handle missing optional fields`() {
        val json = """{"type": "reply"}"""
        val envelope = Protocol.fromJson(json)
        assertEquals("reply", envelope.type)
        assertNull(envelope.callId)
        assertNull(envelope.capability)
        assertNull(envelope.params)
        assertNull(envelope.content)
        assertEquals(0, envelope.generation)
        assertNull(envelope.runId)
        assertNull(envelope.eventType)
        assertNull(envelope.payload)
    }

    @Test
    fun `IncomingEnvelope should handle null actions field`() {
        val json = """{"type": "reply", "content": "test"}"""
        val envelope = Protocol.fromJson(json)
        assertNull(envelope.actions)
    }

    @Test
    fun `IncomingEnvelope should handle generation as number`() {
        val json = """{"type": "reply", "generation": 5}"""
        val envelope = Protocol.fromJson(json)
        assertEquals(5, envelope.generation)
    }

    @Test
    fun `IncomingEnvelope should handle generation as string`() {
        val json = """{"type": "reply", "generation": "3"}"""
        val envelope = Protocol.fromJson(json)
        assertEquals(3, envelope.generation)
    }

    @Test
    fun `IncomingEnvelope should default generation to 0 when missing`() {
        val json = """{"type": "reply", "content": "test"}"""
        val envelope = Protocol.fromJson(json)
        assertEquals(0, envelope.generation)
    }

    @Test
    fun `OutgoingEnvelope should serialize chat message`() {
        val envelope = OutgoingEnvelope(
            type = "chat",
            sessionId = "session-1",
            userId = "user1",
            workspaceId = "workspace-1",
            content = "Hello",
            hostContext = mapOf("files" to listOf("a.kt")),
            mode = "CHAT",
            model = "gpt-4"
        )
        val json = Protocol.toJson(envelope)
        assertTrue(json.contains("\"type\":\"chat\""))
        assertTrue(json.contains("\"sessionId\":\"session-1\""))
        assertTrue(json.contains("\"content\":\"Hello\""))
        assertTrue(json.contains("\"mode\":\"CHAT\""))
        assertTrue(json.contains("\"model\":\"gpt-4\""))
    }

    @Test
    fun `OutgoingEnvelope should serialize cancel_run`() {
        val envelope = OutgoingEnvelope(
            type = "cancel_run",
            sessionId = "session-1",
            workspaceId = "workspace-1",
            runId = "run-1"
        )
        val json = Protocol.toJson(envelope)
        assertTrue(json.contains("\"type\":\"cancel_run\""))
        assertTrue(json.contains("\"runId\":\"run-1\""))
    }

    @Test
    fun `OutgoingEnvelope should serialize capability_result`() {
        val envelope = OutgoingEnvelope(
            type = "capability_result",
            sessionId = "session-1",
            callId = "call-1",
            capability = "read_file",
            result = mapOf("content" to "file content")
        )
        val json = Protocol.toJson(envelope)
        assertTrue(json.contains("\"type\":\"capability_result\""))
        assertTrue(json.contains("\"callId\":\"call-1\""))
        assertTrue(json.contains("\"capability\":\"read_file\""))
        assertTrue(json.contains("file content"))
    }

    @Test
    fun `OutgoingEnvelope should serialize hello message`() {
        val envelope = OutgoingEnvelope(
            type = "hello",
            sessionId = "session-1",
            workspaceId = "workspace-1",
            capabilities = listOf(
                mapOf("name" to "read_file", "description" to "Read a file")
            )
        )
        val json = Protocol.toJson(envelope)
        assertTrue(json.contains("\"type\":\"hello\""))
        assertTrue(json.contains("\"name\":\"read_file\""))
    }

    @Test
    fun `OutgoingEnvelope should serialize event message`() {
        val envelope = OutgoingEnvelope(
            type = "event",
            sessionId = "session-1",
            workspaceId = "workspace-1",
            event = IdeEvent(IdeEventType.FILE_OPENED, mapOf("filePath" to "/test.kt"))
        )
        val json = Protocol.toJson(envelope)
        assertTrue(json.contains("\"type\":\"event\""))
        assertTrue(json.contains("FILE_OPENED"))
        assertTrue(json.contains("/test.kt"))
    }

    @Test
    fun `Protocol should handle malformed JSON gracefully`() {
        try {
            Protocol.fromJson("{invalid json}")
            fail("Expected exception for malformed JSON")
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun `Protocol should handle empty JSON`() {
        try {
            Protocol.fromJson("{}")
            fail("Expected exception or null type for empty JSON")
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun `IncomingEnvelope equality should work`() {
        val a = IncomingEnvelope(
            type = "reply",
            content = "test",
            generation = 1,
            runId = "run-1"
        )
        val b = IncomingEnvelope(
            type = "reply",
            content = "test",
            generation = 1,
            runId = "run-1"
        )
        assertEquals(a, b)
    }

    @Test
    fun `AgentEvent FinalAnswer should carry generation`() {
        val event = AgentEvent.FinalAnswer("session-1", "run-1", "answer", 3)
        assertEquals(3, event.generation)
        assertEquals("session-1", event.sessionId)
        assertEquals("run-1", event.runId)
        assertEquals("answer", event.content)
    }

    @Test
    fun `AgentEvent DiffCreated should have correct fields`() {
        val event = AgentEvent.DiffCreated("session-1", "run-1", "/test.kt")
        assertEquals("session-1", event.sessionId)
        assertEquals("run-1", event.runId)
        assertEquals("/test.kt", event.filePath)
    }

    @Test
    fun `AgentEvent DiffApplied should have correct fields`() {
        val event = AgentEvent.DiffApplied("session-1", "run-1", "/test.kt", true)
        assertEquals("session-1", event.sessionId)
        assertEquals("run-1", event.runId)
        assertEquals("/test.kt", event.filePath)
        assertTrue(event.success)
    }

    @Test
    fun `AgentEvent RunCancelled should have correct fields`() {
        val event = AgentEvent.RunCancelled("session-1", "run-1", 2)
        assertEquals("session-1", event.sessionId)
        assertEquals("run-1", event.runId)
        assertEquals(2, event.generation)
    }
}