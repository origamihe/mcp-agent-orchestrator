package com.mcp.plugin.transport

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.mcp.plugin.McpPluginSettings
import com.mcp.plugin.event.OutgoingEnvelope
import com.mcp.plugin.event.Protocol
import com.mcp.plugin.util.PluginLogger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

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
    private val tokenRefreshExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ws-token-refresh").apply { isDaemon = true }
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
    private var tokenExpiry: Long = 0

    private val tokenValidityDurationMs = TimeUnit.MINUTES.toMillis(30)
    private val tokenRefreshFraction = 0.8f

    @Volatile
    private var scheduledTokenRefresh: ScheduledFuture<*>? = null

    private val unsupportedOfflineTypes = setOf("capability_result", "hello")

    @Volatile
    override var isConnected: Boolean = false
        private set

    override fun connect() {
        if (isConnected) return
        shouldReconnect.set(true)
        Thread {
            doConnect()
        }.apply {
            isDaemon = true
            name = "ws-connect"
        }.start()
    }

    private fun doConnect() {
        try {
            val baseUri = settings.gatewayUrl
            val token = resolveToken(baseUri)
            val uri = if (token.isNotBlank()) {
                URI.create("$baseUri?token=$token")
            } else {
                logger.warn("[Transport] No gateway token available, connection may be rejected")
                URI.create(baseUri)
            }
            logger.info("[Transport] Connecting to: ${uri.toASCIIString().replace(token, "***")}")
            webSocket = httpClient.newWebSocketBuilder()
                .buildAsync(uri, WebSocketListener())
                .join()
            isConnected = true
            reconnectAttempt = 0
            SwingUtilities.invokeLater {
                connectionListeners.forEach { it(true) }
            }
            logger.info("[Transport] Connected! session=$sessionId")

            flushOfflineQueue()
        } catch (e: Exception) {
            logger.error("[Transport] Connection failed: ${e.message}")
            PluginLogger.error("Transport", "Connection failed: ${e.message}", e)
            isConnected = false
            SwingUtilities.invokeLater {
                connectionListeners.forEach { it(false) }
            }
            scheduleReconnect()
        }
    }

    private fun resolveToken(baseUri: String): String {
        if (reconnectAttempt > 0) {
            settings.clearGatewayToken()
            tokenExpiry = 0
            cancelScheduledTokenRefresh()
        }

        if (isTokenValid()) {
            val cachedToken = settings.getGatewayToken()
            if (cachedToken.isNotBlank()) {
                return cachedToken
            }
        }

        val cachedToken = settings.getGatewayToken()
        if (cachedToken.isNotBlank()) {
            return cachedToken
        }
        return try {
            val tokenUrl = deriveHttpUrl(baseUri) + "/api/hosts/token"
            logger.info("[Transport] Fetching token from: $tokenUrl")
            val request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val tokenResp = Gson().fromJson(response.body(), TokenResponse::class.java)
                val token = tokenResp.token
                if (token.isNotBlank()) {
                    logger.info("[Transport] Token fetched successfully")
                    settings.setGatewayToken(token)
                    tokenExpiry = System.currentTimeMillis() + tokenValidityDurationMs
                    scheduleTokenRefresh()
                    token
                } else {
                    logger.warn("[Transport] Token response was empty")
                    ""
                }
            } else {
                logger.warn("[Transport] Token fetch failed: HTTP ${response.statusCode()}")
                ""
            }
        } catch (e: Exception) {
            logger.warn("[Transport] Token fetch error: ${e.message}")
            ""
        }
    }

    private fun isTokenValid(): Boolean {
        return tokenExpiry > 0 && System.currentTimeMillis() < tokenExpiry
    }

    private fun scheduleTokenRefresh() {
        cancelScheduledTokenRefresh()
        val delayMs = ((tokenValidityDurationMs * tokenRefreshFraction).toLong())
        logger.info("[Transport] Scheduling token refresh in ${delayMs / 1000}s")
        scheduledTokenRefresh = tokenRefreshExecutor.schedule({
            logger.info("[Transport] Proactively refreshing token before expiry")
            val baseUri = settings.gatewayUrl
            try {
                val tokenUrl = deriveHttpUrl(baseUri) + "/api/hosts/token"
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() == 200) {
                    val tokenResp = Gson().fromJson(response.body(), TokenResponse::class.java)
                    val token = tokenResp.token
                    if (token.isNotBlank()) {
                        logger.info("[Transport] Token refreshed proactively")
                        settings.setGatewayToken(token)
                        tokenExpiry = System.currentTimeMillis() + tokenValidityDurationMs
                        scheduleTokenRefresh()
                    }
                } else {
                    logger.warn("[Transport] Token refresh failed: HTTP ${response.statusCode()}")
                }
            } catch (e: Exception) {
                logger.warn("[Transport] Token refresh error: ${e.message}")
            }
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun cancelScheduledTokenRefresh() {
        scheduledTokenRefresh?.cancel(false)
        scheduledTokenRefresh = null
    }

    private fun deriveHttpUrl(wsUrl: String): String {
        return wsUrl
            .replace("ws://", "http://")
            .replace("wss://", "https://")
            .replaceAfterLast("/", "")
            .trimEnd('/')
    }

    private data class TokenResponse(@SerializedName("token") val token: String)

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
                clearOfflineQueue()
                doConnect()
            }
        }, delay, TimeUnit.MILLISECONDS)
    }

    private fun flushOfflineQueue() {
        var flushed = 0
        var skipped = 0
        while (true) {
            val msg = offlineMessageQueue.poll() ?: break
            if (msg.type in unsupportedOfflineTypes) {
                skipped++
                logger.info("[Transport] Skipping offline message type=${msg.type}")
                continue
            }
            val json = Protocol.toJson(msg)
            webSocket?.sendText(json, true)
            flushed++
        }
        if (flushed > 0 || skipped > 0) {
            logger.info("[Transport] Flushed $flushed offline messages, skipped $skipped")
        }
    }

    /**
     * 清除离线消息队列。
     * 在 reconnect 时调用，防止旧 Session 的消息污染新连接。
     */
    fun clearOfflineQueue() {
        val count = offlineMessageQueue.size
        offlineMessageQueue.clear()
        if (count > 0) {
            logger.info("[Transport] Cleared $count offline messages for reconnection")
        }
    }

    override fun disconnect() {
        shouldReconnect.set(false)
        webSocket?.sendClose(WebSocket.NORMAL_CLOSURE, "Plugin closing")
        webSocket = null
        isConnected = false
        SwingUtilities.invokeLater {
            connectionListeners.forEach { it(false) }
        }
    }

    fun dispose() {
        disconnect()
        cancelScheduledTokenRefresh()
        reconnectExecutor.shutdownNow()
        tokenRefreshExecutor.shutdownNow()
        messageListeners.clear()
        connectionListeners.clear()
        offlineMessageQueue.clear()
        logger.info("[Transport] Disposed, session=$sessionId")
    }

    override fun send(message: OutgoingEnvelope) {
        if (isConnected) {
            webSocket?.sendText(Protocol.toJson(message), true)
                ?: logger.warn("[Transport] Not connected, cannot send")
        } else {
            if (message.type in unsupportedOfflineTypes) {
                logger.info("[Transport] Skipping offline queue for type=${message.type}")
                return
            }
            if (offlineMessageQueue.size < maxOfflineQueueSize) {
                offlineMessageQueue.add(message)
            } else {
                logger.error("[Transport] Offline queue full ($maxOfflineQueueSize), dropping message")
            }
        }
    }

    override fun onMessage(listener: (String) -> Unit) {
        messageListeners.add(listener)
    }

    override fun onConnectionChange(listener: (Boolean) -> Unit) {
        connectionListeners.add(listener)
    }

    private fun notifyConnectionState(connected: Boolean) {
        SwingUtilities.invokeLater {
            connectionListeners.forEach { it(connected) }
        }
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
            logger.warn("[Transport] Received unexpected binary message (${data.remaining()} bytes), ignoring")
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            if (statusCode == WebSocket.NORMAL_CLOSURE || statusCode == 1001) {
                logger.info("[Transport] Closed normally: $statusCode $reason")
            } else {
                logger.error("[Transport] Closed abnormally: $statusCode $reason")
                PluginLogger.error("Transport", "WebSocket closed abnormally: $statusCode $reason", null)
                cancelScheduledTokenRefresh()
                if (isAuthFailure(statusCode, reason)) {
                    logger.warn("[Transport] Auth failure detected, clearing token")
                    settings.clearGatewayToken()
                    tokenExpiry = 0
                } else {
                    settings.clearGatewayToken()
                    tokenExpiry = 0
                }
            }
            this@WebSocketTransport.webSocket = null
            isConnected = false
            notifyConnectionState(false)
            scheduleReconnect()
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable?) {
            logger.error("[Transport] Error: ${error?.message}")
            PluginLogger.error("Transport", "WebSocket error: ${error?.message}", error)
            cancelScheduledTokenRefresh()
            settings.clearGatewayToken()
            tokenExpiry = 0
            this@WebSocketTransport.webSocket = null
            isConnected = false
            notifyConnectionState(false)
            scheduleReconnect()
        }

        private fun isAuthFailure(statusCode: Int, reason: String): Boolean {
            val reasonLower = reason.lowercase()
            return statusCode == 4001
                || statusCode == 4003
                || reasonLower.contains("auth")
                || reasonLower.contains("unauthorized")
                || reasonLower.contains("401")
                || reasonLower.contains("403")
                || reasonLower.contains("token")
        }
    }
}