package com.mcp.plugin

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.ProjectActivity
import com.mcp.plugin.event.IdeEventBus
import com.mcp.plugin.event.IdeEventType
import com.mcp.plugin.event.OutgoingEnvelope
import com.mcp.plugin.session.AgentSessionController
import com.mcp.plugin.session.AgentState
import com.mcp.plugin.transport.Transport
import com.mcp.plugin.transport.WebSocketTransport

class McpPlugin : ProjectActivity {
    override suspend fun execute(project: Project) {
        val eventBus = project.getService(IdeEventBus::class.java)
        eventBus.init()

        ProjectManager.getInstance().addProjectManagerListener(
            project,
            ProjectCloseListener()
        )

        val transport: Transport? = project.getService(WebSocketTransport::class.java)
        transport?.send(OutgoingEnvelope(
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

class ProjectCloseListener : ProjectManagerListener {
    private val logger = Logger.getInstance(ProjectCloseListener::class.java)

    override fun projectClosed(project: Project) {
        logger.info("[ProjectCloseListener] Project closing: ${project.name}")

        val sessionController: AgentSessionController? = project.getService(AgentSessionController::class.java)
        val transport: WebSocketTransport? = project.getService(WebSocketTransport::class.java)
        val eventBus: IdeEventBus? = project.getService(IdeEventBus::class.java)

        // 1. Cancel active run if any
        if (sessionController != null) {
            val runId = sessionController.session.currentRunId
            if (runId != null && sessionController.session.agentState == AgentState.RUNNING) {
                logger.info("[ProjectCloseListener] Cancelling active run: $runId")
                sessionController.session.cancelRun()
                sessionController.session.confirmCancelled()
            }
        }

        // 2. Stop reconnect — transport.dispose() handles this
        // 3. Stop callbacks — transport.dispose() clears listeners
        // 4. Close transport
        transport?.dispose()

        // 5. Release session listeners
        sessionController?.dispose()

        // 6. Release event bus listeners
        eventBus?.dispose()

        logger.info("[ProjectCloseListener] Project closed: ${project.name}")
    }
}