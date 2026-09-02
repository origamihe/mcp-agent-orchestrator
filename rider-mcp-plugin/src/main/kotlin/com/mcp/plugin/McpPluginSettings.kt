package com.mcp.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "McpPluginSettings",
    storages = [Storage("mcp-agent-plugin.xml")]
)
class McpPluginSettings : PersistentStateComponent<McpPluginSettings> {
    companion object {
        val instance: McpPluginSettings
            get() = ApplicationManager.getApplication().getService(McpPluginSettings::class.java)
    }

    var gatewayUrl: String = "ws://localhost:8080/ws/host"
    var gatewayToken: String = ""
    var autoConnect: Boolean = true
    var agentName: String = "澪音"

    override fun getState(): McpPluginSettings = this
    override fun loadState(state: McpPluginSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}