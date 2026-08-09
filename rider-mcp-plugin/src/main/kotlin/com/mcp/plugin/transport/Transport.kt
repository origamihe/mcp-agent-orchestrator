package com.mcp.plugin.transport

import com.mcp.plugin.event.OutgoingEnvelope

interface Transport {
    val sessionId: String
    val isConnected: Boolean

    fun connect()
    fun disconnect()
    fun send(message: OutgoingEnvelope)
    fun onMessage(listener: (String) -> Unit)
    fun onConnectionChange(listener: (Boolean) -> Unit)

    companion object {
        @Volatile
        var instance: Transport? = null
    }
}