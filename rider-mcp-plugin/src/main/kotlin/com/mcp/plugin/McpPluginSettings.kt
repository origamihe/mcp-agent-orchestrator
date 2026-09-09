package com.mcp.plugin

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
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

        private const val TOKEN_SERVICE_NAME = "MCPGateway"
    }

    var gatewayUrl: String = "ws://localhost:8080/ws/host"
    var autoConnect: Boolean = true
    var agentName: String = "澪音"
    var agentMode: String = "CHAT"
    var agentModel: String = ""

    fun getGatewayToken(): String {
        val attributes = credentialAttributes()
        return PasswordSafe.instance.getPassword(attributes) ?: ""
    }

    fun setGatewayToken(token: String) {
        val attributes = credentialAttributes()
        if (token.isBlank()) {
            PasswordSafe.instance.setPassword(attributes, null)
        } else {
            PasswordSafe.instance.setPassword(attributes, token)
        }
    }

    fun clearGatewayToken() {
        setGatewayToken("")
    }

    private fun credentialAttributes(): CredentialAttributes {
        return CredentialAttributes(
            "${TOKEN_SERVICE_NAME}:${gatewayUrl}"
        )
    }

    override fun getState(): McpPluginSettings = this
    override fun loadState(state: McpPluginSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}