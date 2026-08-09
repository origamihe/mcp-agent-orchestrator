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

@Service(Service.Level.PROJECT)
class WebSocketTransport(private val project: Project) : Transport {
    private val logger = Logger.getInstance(WebSocketTransport::class.java)
    private val settings = project.getService(McpPluginSettings::class.java) ?: McpPluginSettings()

    private var webSocket: WebSocket? = null
    private val httpClient: HttpClient = HttpClient.newBuilder().build()
    override val sessionId: String = "rider-${UUID.randomUUID().toString().take(8)}"

    private val messageListeners = ConcurrentLinkedQueue<(String) -> Unit>()
    private val connectionListeners = ConcurrentLinkedQueue<(Boolean) -> Unit>()

    @Volatile
    override var isConnected: Boolean = false
        private set

    init {
        Transport.instance = this
    }

    override fun connect() {
        if (isConnected) return
        try {
            val uri = URI.create(settings.gatewayUrl)
            logger.info("[Transport] Connecting to: $uri")
            webSocket = httpClient.newWebSocketBuilder()
                .buildAsync(uri, WebSocketListener())
                .join()
            isConnected = true
            connectionListeners.forEach { it(true) }
            logger.info("[Transport] Connected! session=$sessionId")
        } catch (e: Exception) {
            logger.error("[Transport] Connection failed: ${e.message}")
            isConnected = false
            connectionListeners.forEach { it(false) }
        }
    }

    override fun disconnect() {
        webSocket?.sendClose(WebSocket.NORMAL_CLOSURE, "Plugin closing")
        webSocket = null
        isConnected = false
        connectionListeners.forEach { it(false) }
    }

    override fun send(message: OutgoingEnvelope) {
        val json = Protocol.toJson(message)
        if (isConnected) {
            webSocket?.sendText(json, true)
                ?: logger.warn("[Transport] Not connected, cannot send")
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
            isConnected = false
            connectionListeners.forEach { it(false) }
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable?) {
            logger.error("[Transport] Error: ${error?.message}")
            isConnected = false
            connectionListeners.forEach { it(false) }
        }
    }
}