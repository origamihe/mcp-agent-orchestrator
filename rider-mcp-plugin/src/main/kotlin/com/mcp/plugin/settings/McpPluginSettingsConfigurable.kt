package com.mcp.plugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.mcp.plugin.McpPluginSettings
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class McpPluginSettingsConfigurable : Configurable {

    private var settingsPanel: JPanel? = null
    private var gatewayUrlField: JBTextField? = null
    private var gatewayTokenField: JBTextField? = null
    private var agentNameField: JBTextField? = null
    private var autoConnectCheckbox: JCheckBox? = null

    override fun getDisplayName(): String = "MCP Agent"

    override fun createComponent(): JComponent? {
        gatewayUrlField = JBTextField()
        gatewayTokenField = JBTextField()
        agentNameField = JBTextField()
        autoConnectCheckbox = JCheckBox("Auto-connect on project open")

        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Gateway URL:"), gatewayUrlField!!, 1, false)
            .addTooltip("WebSocket endpoint of the MCP Gateway (e.g., ws://localhost:8080/ws/host)")
            .addLabeledComponent(JBLabel("Gateway Token:"), gatewayTokenField!!, 1, false)
            .addTooltip("Authentication token for WebSocket connection")
            .addLabeledComponent(JBLabel("Agent Name:"), agentNameField!!, 1, false)
            .addTooltip("Display name for your agent")
            .addComponent(autoConnectCheckbox!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        settingsPanel = panel
        return panel
    }

    override fun isModified(): Boolean {
        val settings = McpPluginSettings.instance
        return gatewayUrlField?.text != settings.gatewayUrl
                || gatewayTokenField?.text != settings.gatewayToken
                || agentNameField?.text != settings.agentName
                || autoConnectCheckbox?.isSelected != settings.autoConnect
    }

    override fun apply() {
        val settings = McpPluginSettings.instance
        settings.gatewayUrl = gatewayUrlField?.text ?: settings.gatewayUrl
        settings.gatewayToken = gatewayTokenField?.text ?: settings.gatewayToken
        settings.agentName = agentNameField?.text ?: settings.agentName
        settings.autoConnect = autoConnectCheckbox?.isSelected ?: settings.autoConnect
    }

    override fun reset() {
        val settings = McpPluginSettings.instance
        gatewayUrlField?.text = settings.gatewayUrl
        gatewayTokenField?.text = settings.gatewayToken
        agentNameField?.text = settings.agentName
        autoConnectCheckbox?.isSelected = settings.autoConnect
    }
}