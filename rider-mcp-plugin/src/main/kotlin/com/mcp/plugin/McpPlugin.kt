package com.mcp.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.ProjectActivity
import com.mcp.plugin.event.IdeEventBus
import com.mcp.plugin.event.IdeEventType
import com.mcp.plugin.event.OutgoingEnvelope
import com.mcp.plugin.transport.Transport

class McpPlugin : ProjectActivity {
    override suspend fun execute(project: Project) {
        val eventBus = project.getService(IdeEventBus::class.java)
        eventBus.init()

        val transport = Transport.instance
        if (transport != null && transport.isConnected) {
            transport.send(OutgoingEnvelope(
                type = "event",
                sessionId = transport.sessionId,
                workspaceId = eventBus.workspaceId,
                event = com.mcp.plugin.event.IdeEvent(
                    type = IdeEventType.PROJECT_OPENED,
                    payload = mapOf(
                        "projectName" to project.name,
                        "projectPath" to (project.basePath ?: "")
                    )
                )
            ))
        }
    }
}

class ProjectCloseListener : ProjectManagerListener {
    override fun projectClosed(project: Project) {
        Transport.instance?.disconnect()
    }
}