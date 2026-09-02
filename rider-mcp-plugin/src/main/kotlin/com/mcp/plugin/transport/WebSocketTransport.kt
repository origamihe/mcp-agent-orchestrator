package com.mcp.plugin.transport

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.mcp.plugin.McpPluginSettings
import com.mcp.plugin.event.OutgoingEnvelope
import com.mcp.plugin.event.Protocol
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class WebSocketTransport(private val project: Project) : Transport {
    private val logger = Logger.getInstance(WebSocketTransport::class.java)
    private val settings = project.getService(McpPluginSettings::class.java) ?: McpPluginSettings()

    private var webSocket: WebSocket? = null
    private val httpClient: HttpClient = HttpClient.newBuilder().build()
    override val sessionId: String = "rider-${UUID.randomUUID().toString().take(8)}"

    private val messageListeners = ConcurrentLinkedQueue<(String) -> Unit>()
    private val connectionListeners = ConcurrentLinkedQueue<(Boolean) -> Unit>()

    private val reconnectExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ws-reconnect").apply { isDaemon = true }
    }
    private val reconnectScheduled = AtomicBoolean(false)
    private val shouldReconnect = AtomicBoolean(true)

    private var reconnectAttempt = 0
    private val maxReconnectAttempts = 10
    private val baseReconnectDelayMs = 1000L
    private val maxReconnectDelayMs = 60000L

    private val offlineMessageQueue = ConcurrentLinkedQueue<OutgoingEnvelope>()
    private val maxOfflineQueueSize = 200

    @Volatile
    override var isConnected: Boolean = false
        private set

    override fun connect() {
        if (isConnected) return
        shouldReconnect.set(true)
        doConnect()
    }

    private fun doConnect() {
        try {
            val baseUri = settings.gatewayUrl
            val uri = if (settings.gatewayToken.isNotBlank()) {
                URI.create("$baseUri?token=${settings.gatewayToken}")
            } else {
                logger.warn("[Transport] No gateway token configured, connection may be rejected")
                URI.create(baseUri)
            }
            logger.info("[Transport] Connecting to: $uri")
            webSocket = httpClient.newWebSocketBuilder()
                .buildAsync(uri, WebSocketListener())
                .join()
            isConnected = true
            reconnectAttempt = 0
            connectionListeners.forEach { it(true) }
            logger.info("[Transport] Connected! session=$sessionId")

            flushOfflineQueue()
        } catch (e: Exception) {
            logger.error("[Transport] Connection failed: ${e.message}")
            isConnected = false
            connectionListeners.forEach { it(false) }
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect.get()) return
        if (reconnectScheduled.getAndSet(true)) return

        if (reconnectAttempt >= maxReconnectAttempts) {
            logger.warn("[Transport] Max reconnect attempts ($maxReconnectAttempts) reached, giving up")
            reconnectScheduled.set(false)
            return
        }

        val delay = minOf(baseReconnectDelayMs * (1L shl minOf(reconnectAttempt, 10)), maxReconnectDelayMs)
        reconnectAttempt++
        logger.info("[Transport] Scheduling reconnect attempt $reconnectAttempt/$maxReconnectAttempts in ${delay}ms")

        reconnectExecutor.schedule({
            reconnectScheduled.set(false)
            if (shouldReconnect.get() && !isConnected) {
                doConnect()
            }
        }, delay, TimeUnit.MILLISECONDS)
    }

    private fun flushOfflineQueue() {
        var flushed = 0
        while (true) {
            val msg = offlineMessageQueue.poll() ?: break
            val json = Protocol.toJson(msg)
            webSocket?.sendText(json, true)
            flushed++
        }
        if (flushed > 0) {
            logger.info("[Transport] Flushed $flushed offline messages")
        }
    }

    override fun disconnect() {
        shouldReconnect.set(false)
        webSocket?.sendClose(WebSocket.NORMAL_CLOSURE, "Plugin closing")
        webSocket = null
        isConnected = false
        connectionListeners.forEach { it(false) }
    }

    override fun send(message: OutgoingEnvelope) {
        if (isConnected) {
            webSocket?.sendText(Protocol.toJson(message), true)
                ?: logger.warn("[Transport] Not connected, cannot send")
        } else {
            if (offlineMessageQueue.size < maxOfflineQueueSize) {
                offlineMessageQueue.add(message)
            } else {
                logger.warn("[Transport] Offline queue full ($maxOfflineQueueSize), dropping message")
            }
        }
    }

    override fun onMessage(listener: (String) -> Unit) {
        messageListeners.add(listener)
    }

    override fun onConnectionChange(listener: (Boolean) -> Unit) {
        connectionListeners.add(listener)
    }

    private inner class WebSocketListener : WebSocket.Listener {
        private val buffer = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                val msg = buffer.toString()
                buffer.clear()
                messageListeners.forEach { it(msg) }
            }
            webSocket.request(1)
            return null
        }

        override fun onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage<*>? {
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            logger.info("[Transport] Closed: $statusCode $reason")
            this@WebSocketTransport.webSocket = null
            isConnected = false
            connectionListeners.forEach { it(false) }
            scheduleReconnect()
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable?) {
            logger.error("[Transport] Error: ${error?.message}")
            this@WebSocketTransport.webSocket = null
            isConnected = false
            connectionListeners.forEach { it(false) }
            scheduleReconnect()
        }
    }
}