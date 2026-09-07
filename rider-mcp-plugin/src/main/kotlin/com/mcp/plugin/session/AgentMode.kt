package com.mcp.plugin.session

/**
 * Agent 运行时模式枚举。
 *
 * 与 Backend com.mcp.common.channel.AgentMode 对应：
 *   CHAT → CHAT
 *   BUILDER → CODING (Backend 的代码 Agent 模式)
 *   BUILDER_WITH_MCP → CODING (带 MCP Tool/Capability 执行)
 *
 * 注意：Backend 的 AgentMode 目前有 CHAT/GAME/NPC/COMPANION/CODING/WORKFLOW，
 * Plugin 仅需要 CHAT 和 CODING 两个语义。BUILDER_WITH_MCP 在 Plugin 端是 UI 模式，
 * 后端映射为 CODING + 启用 Tool Calling。
 */
enum class AgentMode(val displayName: String, val description: String) {
    CHAT(
        "Chat",
        "普通对话模式 — 不主动执行代码修改"
    ),
    BUILDER(
        "Builder",
        "Agent 驱动的代码构建/修改模式 — 可分析、读取、搜索、提出修改"
    ),
    BUILDER_WITH_MCP(
        "Builder + MCP",
        "Builder + MCP Tool/Capability 执行 — 可读取文件、搜索、诊断、终端、Diff"
    );

    fun toBackendMode(): String = when (this) {
        CHAT -> "CHAT"
        BUILDER -> "CODING"
        BUILDER_WITH_MCP -> "CODING"
    }

    companion object {
        fun fromBackendMode(mode: String?): AgentMode = when (mode?.uppercase()) {
            "CODING", "WORKFLOW" -> BUILDER
            "CHAT" -> CHAT
            else -> CHAT
        }
    }
}