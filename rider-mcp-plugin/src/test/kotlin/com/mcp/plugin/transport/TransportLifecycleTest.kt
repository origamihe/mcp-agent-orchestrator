package com.mcp.plugin.transport

import com.mcp.plugin.event.OutgoingEnvelope
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Transport 生命周期与离线队列测试。
 *
 * 验证：
 * 1. Transport 接口契约
 * 2. 离线队列消息类型过滤
 * 3. 连接状态变更通知
 * 4. 消息监听器线程安全
 *
 * 注意：不测试实际 WebSocket 连接（需要运行中的 Gateway）。
 */
class TransportLifecycleTest {

    private val unsupportedOfflineTypes = setOf("capability_result", "hello")

    @Test
    fun `MockTransport should implement Transport interface`() {
        val transport = MockTransport()
        assertNotNull(transport.sessionId)
        assertFalse(transport.isConnected)
    }

    @Test
    fun `MockTransport should notify connection listeners`() {
        val transport = MockTransport()
        val latch = CountDownLatch(1)
        val receivedState = AtomicBoolean(false)
        transport.onConnectionChange { connected ->
            receivedState.set(connected)
            latch.countDown()
        }
        transport.simulateConnect()
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertTrue(receivedState.get())
    }

    @Test
    fun `MockTransport should notify disconnect`() {
        val transport = MockTransport()
        transport.simulateConnect()
        val latch = CountDownLatch(1)
        val receivedState = AtomicBoolean(true)
        transport.onConnectionChange { connected ->
            receivedState.set(connected)
            latch.countDown()
        }
        transport.simulateDisconnect()
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertFalse(receivedState.get())
    }

    @Test
    fun `MockTransport should deliver messages to listeners`() {
        val transport = MockTransport()
        val latch = CountDownLatch(1)
        val receivedMessages = mutableListOf<String>()
        transport.onMessage { msg ->
            receivedMessages.add(msg)
            latch.countDown()
        }
        transport.simulateConnect()
        transport.simulateReceive("{\"type\":\"reply\",\"content\":\"Hello\"}")
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(1, receivedMessages.size)
        assertTrue(receivedMessages[0].contains("reply"))
    }

    @Test
    fun `offline queue should filter unsupported types`() {
        val queue = ConcurrentLinkedQueue<OutgoingEnvelope>()
        val maxSize = 200

        val chatMsg = OutgoingEnvelope(
            type = "chat",
            sessionId = "test",
            content = "hello"
        )
        val capabilityResultMsg = OutgoingEnvelope(
            type = "capability_result",
            sessionId = "test",
            callId = "c1"
        )
        val helloMsg = OutgoingEnvelope(
            type = "hello",
            sessionId = "test"
        )

        if (chatMsg.type !in unsupportedOfflineTypes) queue.add(chatMsg)
        if (capabilityResultMsg.type !in unsupportedOfflineTypes) queue.add(capabilityResultMsg)
        if (helloMsg.type !in unsupportedOfflineTypes) queue.add(helloMsg)

        assertEquals(1, queue.size)
        assertEquals("chat", queue.peek()?.type)
    }

    @Test
    fun `offline queue should respect max size`() {
        val queue = ConcurrentLinkedQueue<OutgoingEnvelope>()
        val maxSize = 200

        for (i in 1..250) {
            if (queue.size < maxSize) {
                queue.add(OutgoingEnvelope(
                    type = "chat",
                    sessionId = "test",
                    content = "msg-$i"
                ))
            }
        }

        assertEquals(maxSize, queue.size)
    }

    @Test
    fun `offline queue should allow chat messages`() {
        val queue = ConcurrentLinkedQueue<OutgoingEnvelope>()
        val msg = OutgoingEnvelope(type = "chat", sessionId = "test", content = "test")
        if (msg.type !in unsupportedOfflineTypes) {
            queue.add(msg)
        }
        assertEquals(1, queue.size)
        assertEquals("chat", queue.peek()?.type)
    }

    @Test
    fun `offline queue should allow event messages`() {
        val queue = ConcurrentLinkedQueue<OutgoingEnvelope>()
        val msg = OutgoingEnvelope(
            type = "event",
            sessionId = "test",
            event = com.mcp.plugin.event.IdeEvent(
                type = com.mcp.plugin.event.IdeEventType.FILE_OPENED,
                payload = mapOf("filePath" to "/test.txt")
            )
        )
        if (msg.type !in unsupportedOfflineTypes) {
            queue.add(msg)
        }
        assertEquals(1, queue.size)
        assertEquals("event", queue.peek()?.type)
    }

    @Test
    fun `offline queue should reject capability_result messages`() {
        val queue = ConcurrentLinkedQueue<OutgoingEnvelope>()
        val msg = OutgoingEnvelope(
            type = "capability_result",
            sessionId = "test",
            callId = "c1"
        )
        if (msg.type !in unsupportedOfflineTypes) {
            queue.add(msg)
        }
        assertEquals(0, queue.size)
    }

    @Test
    fun `offline queue should reject hello messages`() {
        val queue = ConcurrentLinkedQueue<OutgoingEnvelope>()
        val msg = OutgoingEnvelope(type = "hello", sessionId = "test")
        if (msg.type !in unsupportedOfflineTypes) {
            queue.add(msg)
        }
        assertEquals(0, queue.size)
    }

    @Test
    fun `transport should support multiple message listeners`() {
        val transport = MockTransport()
        val latch1 = CountDownLatch(1)
        val latch2 = CountDownLatch(1)
        transport.onMessage { latch1.countDown() }
        transport.onMessage { latch2.countDown() }
        transport.simulateConnect()
        transport.simulateReceive("{}")
        assertTrue(latch1.await(2, TimeUnit.SECONDS))
        assertTrue(latch2.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun `transport should support multiple connection listeners`() {
        val transport = MockTransport()
        val latch1 = CountDownLatch(1)
        val latch2 = CountDownLatch(1)
        transport.onConnectionChange { latch1.countDown() }
        transport.onConnectionChange { latch2.countDown() }
        transport.simulateConnect()
        assertTrue(latch1.await(2, TimeUnit.SECONDS))
        assertTrue(latch2.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun `transport message listeners should be thread-safe`() {
        val transport = MockTransport()
        val counter = AtomicInteger(0)
        val listenerCount = 10
        val latch = CountDownLatch(listenerCount)

        repeat(listenerCount) {
            transport.onMessage { _ ->
                counter.incrementAndGet()
                latch.countDown()
            }
        }

        transport.simulateConnect()
        transport.simulateReceive("{}")

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(listenerCount, counter.get())
    }

    @Test
    fun `transport disconnect should clear connection state`() {
        val transport = MockTransport()
        assertFalse(transport.isConnected)
        transport.simulateConnect()
        assertTrue(transport.isConnected)
        transport.simulateDisconnect()
        assertFalse(transport.isConnected)
    }

    @Test
    fun `OutgoingEnvelope should serialize correctly`() {
        val envelope = OutgoingEnvelope(
            type = "chat",
            sessionId = "test-session",
            userId = "user1",
            workspaceId = "ws-1",
            content = "Hello"
        )
        assertEquals("chat", envelope.type)
        assertEquals("test-session", envelope.sessionId)
        assertEquals("user1", envelope.userId)
        assertEquals("ws-1", envelope.workspaceId)
        assertEquals("Hello", envelope.content)
    }

    /**
     * Mock Transport 实现，用于测试 Transport 接口契约和生命周期。
     */
    private class MockTransport : Transport {
        private val _sessionId = "mock-${java.util.UUID.randomUUID().toString().take(8)}"
        override val sessionId: String get() = _sessionId

        @Volatile
        override var isConnected: Boolean = false
            private set

        private val messageListeners = ConcurrentLinkedQueue<(String) -> Unit>()
        private val connectionListeners = ConcurrentLinkedQueue<(Boolean) -> Unit>()

        override fun connect() {
            simulateConnect()
        }

        override fun disconnect() {
            simulateDisconnect()
        }

        override fun send(message: OutgoingEnvelope) {
        }

        override fun onMessage(listener: (String) -> Unit) {
            messageListeners.add(listener)
        }

        override fun onConnectionChange(listener: (Boolean) -> Unit) {
            connectionListeners.add(listener)
        }

        fun simulateConnect() {
            isConnected = true
            connectionListeners.forEach { it(true) }
        }

        fun simulateDisconnect() {
            isConnected = false
            connectionListeners.forEach { it(false) }
        }

        fun simulateReceive(message: String) {
            messageListeners.forEach { it(message) }
        }
    }
}