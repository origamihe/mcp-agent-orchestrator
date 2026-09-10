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
    private val logger = Logger.getInstance(McpPlugin::class.java)

    override suspend fun execute(project: Project) {
        logger.info("[McpPlugin] Initializing for project: ${project.name}")

        try {
            val eventBus = project.getService(IdeEventBus::class.java)
            eventBus.init()

            ProjectManager.getInstance().addProjectManagerListener(
                project,
                ProjectCloseListener()
            )

            val transport: Transport? = project.getService(WebSocketTransport::class.java)
            if (transport != null) {
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
            } else {
                logger.error("[McpPlugin] WebSocketTransport not available, PROJECT_OPENED event not sent")
            }
        } catch (e: Exception) {
            logger.error("[McpPlugin] Initialization failed: ${e.message}", e)
        }
    }
}

class ProjectCloseListener : ProjectManagerListener {
    private val logger = Logger.getInstance(ProjectCloseListener::class.java)

    override fun projectClosed(project: Project) {
        logger.info("[ProjectCloseListener] Project closing: ${project.name}")

        try {
            val sessionController: AgentSessionController? = project.getService(AgentSessionController::class.java)
            val transport: WebSocketTransport? = project.getService(WebSocketTransport::class.java)
            val eventBus: IdeEventBus? = project.getService(IdeEventBus::class.java)

            if (sessionController != null) {
                val runId = sessionController.session.currentRunId
                if (runId != null && sessionController.session.agentState == AgentState.RUNNING) {
                    logger.info("[ProjectCloseListener] Cancelling active run: $runId")
                    sessionController.session.cancelRun()
                    sessionController.session.confirmCancelled()
                }
                sessionController.dispose()
            } else {
                logger.warn("[ProjectCloseListener] AgentSessionController not available")
            }

            transport?.dispose()

            eventBus?.dispose()

            logger.info("[ProjectCloseListener] Project closed: ${project.name}")
        } catch (e: Exception) {
            logger.error("[ProjectCloseListener] Error during project close: ${e.message}", e)
        }
    }
}