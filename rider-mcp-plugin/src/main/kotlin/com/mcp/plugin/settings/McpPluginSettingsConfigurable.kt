package com.mcp.plugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.mcp.plugin.McpPluginSettings
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

class McpPluginSettingsConfigurable : Configurable {

    private var settingsPanel: JPanel? = null
    private var gatewayUrlField: JBTextField? = null
    private var gatewayHttpUrlField: JBTextField? = null
    private var gatewayTokenField: JBTextField? = null
    private var autoConnectCheckbox: JCheckBox? = null
    private var agentNameField: JBTextField? = null
    private var agentModeCombo: JComboBox<String>? = null
    private var maxRetriesField: JBTextField? = null
    private var capabilityTimeoutField: JBTextField? = null
    private var logLevelCombo: JComboBox<String>? = null
    private var logMaxFileSizeField: JBTextField? = null
    private var logRetentionDaysField: JBTextField? = null

    override fun getDisplayName(): String = "MCP Agent"

    override fun createComponent(): JComponent? {
        gatewayUrlField = JBTextField()
        gatewayHttpUrlField = JBTextField()
        gatewayTokenField = JBTextField()
        autoConnectCheckbox = JCheckBox("Auto-connect on project open")
        agentNameField = JBTextField()
        agentModeCombo = JComboBox(arrayOf("CHAT", "MAKER", "PATROL"))
        maxRetriesField = JBTextField()
        capabilityTimeoutField = JBTextField()
        logLevelCombo = JComboBox(arrayOf("DEBUG", "INFO", "WARN", "ERROR"))
        logMaxFileSizeField = JBTextField()
        logRetentionDaysField = JBTextField()

        val panel = FormBuilder.createFormBuilder()
            .addComponent(TitledSeparator("Connection"))
            .addLabeledComponent(JBLabel("WebSocket URL:"), gatewayUrlField!!, 1, false)
            .addTooltip("WebSocket endpoint of the MCP Gateway (e.g., ws://localhost:8080/ws/host)")
            .addLabeledComponent(JBLabel("HTTP API URL:"), gatewayHttpUrlField!!, 1, false)
            .addTooltip("HTTP API base URL for token/auth requests (e.g., http://localhost:8080)")
            .addLabeledComponent(JBLabel("Gateway Token:"), gatewayTokenField!!, 1, false)
            .addTooltip("Authentication token for WebSocket connection")
            .addComponent(autoConnectCheckbox!!)

            .addComponent(TitledSeparator("Agent"))
            .addLabeledComponent(JBLabel("Agent Name:"), agentNameField!!, 1, false)
            .addTooltip("Display name for your agent")
            .addLabeledComponent(JBLabel("Default Mode:"), agentModeCombo!!, 1, false)
            .addTooltip("Default agent execution mode (CHAT / MAKER / PATROL)")

            .addComponent(TitledSeparator("Runtime"))
            .addLabeledComponent(JBLabel("Max Retries:"), maxRetriesField!!, 1, false)
            .addTooltip("Maximum retry attempts for failed capability calls (1-10)")
            .addLabeledComponent(JBLabel("Capability Timeout (s):"), capabilityTimeoutField!!, 1, false)
            .addTooltip("Timeout in seconds for capability execution (5-300)")

            .addComponent(TitledSeparator("Logging"))
            .addLabeledComponent(JBLabel("Log Level:"), logLevelCombo!!, 1, false)
            .addTooltip("Minimum log level for the plugin log file")
            .addLabeledComponent(JBLabel("Max File Size (MB):"), logMaxFileSizeField!!, 1, false)
            .addTooltip("Maximum log file size before rotation (1-100)")
            .addLabeledComponent(JBLabel("Retention Days:"), logRetentionDaysField!!, 1, false)
            .addTooltip("Number of days to keep rotated log files (1-30)")

            .addComponentFillVertically(JPanel(), 0)
            .panel

        settingsPanel = panel
        return panel
    }

    override fun isModified(): Boolean {
        val settings = McpPluginSettings.instance
        return gatewayUrlField?.text != settings.gatewayUrl
                || gatewayHttpUrlField?.text != settings.gatewayHttpUrl
                || gatewayTokenField?.text != settings.getGatewayToken()
                || agentNameField?.text != settings.agentName
                || autoConnectCheckbox?.isSelected != settings.autoConnect
                || agentModeCombo?.selectedItem?.toString() != settings.agentMode
                || maxRetriesField?.text?.toIntOrNull() != settings.maxRetries
                || capabilityTimeoutField?.text?.toIntOrNull() != settings.capabilityTimeout
                || logLevelCombo?.selectedItem?.toString() != settings.logLevel
                || logMaxFileSizeField?.text?.toIntOrNull() != settings.logMaxFileSize
                || logRetentionDaysField?.text?.toIntOrNull() != settings.logRetentionDays
    }

    override fun apply() {
        val settings = McpPluginSettings.instance
        settings.gatewayUrl = gatewayUrlField?.text ?: settings.gatewayUrl
        settings.gatewayHttpUrl = gatewayHttpUrlField?.text ?: settings.gatewayHttpUrl
        settings.setGatewayToken(gatewayTokenField?.text ?: "")
        settings.agentName = agentNameField?.text ?: settings.agentName
        settings.autoConnect = autoConnectCheckbox?.isSelected ?: settings.autoConnect
        settings.agentMode = agentModeCombo?.selectedItem?.toString() ?: settings.agentMode
        settings.maxRetries = maxRetriesField?.text?.toIntOrNull()?.coerceIn(1, 10) ?: settings.maxRetries
        settings.capabilityTimeout = capabilityTimeoutField?.text?.toIntOrNull()?.coerceIn(5, 300) ?: settings.capabilityTimeout
        settings.logLevel = logLevelCombo?.selectedItem?.toString() ?: settings.logLevel
        settings.logMaxFileSize = logMaxFileSizeField?.text?.toIntOrNull()?.coerceIn(1, 100) ?: settings.logMaxFileSize
        settings.logRetentionDays = logRetentionDaysField?.text?.toIntOrNull()?.coerceIn(1, 30) ?: settings.logRetentionDays
    }

    override fun reset() {
        val settings = McpPluginSettings.instance
        gatewayUrlField?.text = settings.gatewayUrl
        gatewayHttpUrlField?.text = settings.gatewayHttpUrl
        gatewayTokenField?.text = settings.getGatewayToken()
        agentNameField?.text = settings.agentName
        autoConnectCheckbox?.isSelected = settings.autoConnect
        agentModeCombo?.selectedItem = settings.agentMode
        maxRetriesField?.text = settings.maxRetries.toString()
        capabilityTimeoutField?.text = settings.capabilityTimeout.toString()
        logLevelCombo?.selectedItem = settings.logLevel
        logMaxFileSizeField?.text = settings.logMaxFileSize.toString()
        logRetentionDaysField?.text = settings.logRetentionDays.toString()
    }
}