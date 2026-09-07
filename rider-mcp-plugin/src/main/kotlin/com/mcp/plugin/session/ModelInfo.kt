package com.mcp.plugin.session

import com.google.gson.annotations.SerializedName

/**
 * 模型信息 — 从 Backend GET /api/llm/configs 获取。
 */
data class ModelInfo(
    @SerializedName("configId")
    val configId: String,

    @SerializedName("provider")
    val provider: String? = null,

    @SerializedName("modelName")
    val modelName: String? = null,

    @SerializedName("temperature")
    val temperature: Double? = null,

    @SerializedName("maxTokens")
    val maxTokens: Int? = null,

    @SerializedName("enabled")
    val enabled: Boolean = true
) {
    val displayName: String
        get() = if (provider != null && modelName != null) {
            "$provider / $modelName"
        } else {
            configId
        }
}