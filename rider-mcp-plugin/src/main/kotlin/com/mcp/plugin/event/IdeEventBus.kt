package com.mcp.plugin.event

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.mcp.plugin.transport.Transport
import com.mcp.plugin.transport.WebSocketTransport
import com.mcp.plugin.util.LanguageDetector
import java.util.UUID

@Service(Service.Level.PROJECT)
class IdeEventBus(private val project: Project) {
    private val logger = Logger.getInstance(IdeEventBus::class.java)
    private val transport: Transport? = project.getService(WebSocketTransport::class.java)
    val workspaceId: String = "${project.name}-${UUID.randomUUID().toString().take(8)}"

    fun init() {
        val connection = project.messageBus.connect()

        @Suppress("DEPRECATION")
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                fire(IdeEventType.FILE_OPENED, mapOf(
                    "filePath" to file.path,
                    "fileName" to file.name,
                    "language" to LanguageDetector.detect(file)
                ))
            }

            override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                fire(IdeEventType.FILE_CLOSED, mapOf("filePath" to file.path))
            }
        })

        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                events.mapNotNull { it.file }.forEach { file ->
                    fire(IdeEventType.FILE_SAVED, mapOf(
                        "filePath" to file.path
                    ))
                }
            }
        })

        logger.info("[IdeEventBus] Initialized, workspaceId=$workspaceId")
    }

    private fun fire(type: IdeEventType, payload: Map<String, Any?> = emptyMap()) {
        val event = IdeEvent(type = type, payload = payload)

        transport?.send(OutgoingEnvelope(
            type = "event",
            sessionId = transport.sessionId,
            workspaceId = workspaceId,
            event = event
        ))
    }
}