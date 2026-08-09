package com.mcp.plugin.event

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

enum class IdeEventType {
    PROJECT_OPENED,
    PROJECT_CLOSED,
    FILE_OPENED,
    FILE_CLOSED,
    FILE_SAVED,
    CURSOR_MOVED,
    SELECTION_CHANGED,
    BUILD_STARTED,
    BUILD_FINISHED,
    BUILD_FAILED,
    RUN_STARTED,
    RUN_FINISHED,
    TEST_FINISHED,
    GIT_COMMIT,
    GIT_BRANCH_CHANGED,
    VCS_CHANGED,
    TERMINAL_OUTPUT,
    USER_CHAT,
    USER_ACTION
}

data class IdeEvent(
    @SerializedName("type") val type: IdeEventType,
    @SerializedName("payload") val payload: Map<String, Any?> = emptyMap()
)

data class OutgoingEnvelope(
    @SerializedName("type") val type: String,           // "event" | "chat" | "capability_result" | "hello"
    @SerializedName("hostType") val hostType: String = "ide",
    @SerializedName("ideType") val ideType: String = "Rider",
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("workspaceId") val workspaceId: String? = null,
    @SerializedName("event") val event: IdeEvent? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("context") val context: Map<String, Any?>? = null,
    @SerializedName("callId") val callId: String? = null,
    @SerializedName("capability") val capability: String? = null,
    @SerializedName("result") val result: Map<String, Any?>? = null,
    @SerializedName("capabilities") val capabilities: List<Map<String, Any?>>? = null
)

data class IncomingEnvelope(
    @SerializedName("type") val type: String? = null,
    @SerializedName("callId") val callId: String? = null,
    @SerializedName("capability") val capability: String? = null,
    @SerializedName("params") val params: Map<String, Any?>? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("actions") val actions: List<Map<String, Any?>>? = null
)

object Protocol {
    val gson = Gson()

    fun toJson(msg: OutgoingEnvelope): String = gson.toJson(msg)

    fun fromJson(json: String): IncomingEnvelope {
        return gson.fromJson(json, IncomingEnvelope::class.java)
    }
}