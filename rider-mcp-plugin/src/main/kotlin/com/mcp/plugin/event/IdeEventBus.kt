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
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

@Service(Service.Level.PROJECT)
class IdeEventBus(private val project: Project) {
    private val logger = Logger.getInstance(IdeEventBus::class.java)
    private val transport: Transport? = Transport.instance
    val workspaceId: String = "${project.name}-${UUID.randomUUID().toString().take(8)}"

    private val listeners = ConcurrentLinkedQueue<(IdeEvent) -> Unit>()

    fun init() {
        val connection = project.messageBus.connect()

        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                fire(IdeEventType.FILE_OPENED, mapOf(
                    "filePath" to file.path,
                    "fileName" to file.name,
                    "language" to detectLanguage(file)
                ))
            }

            override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                fire(IdeEventType.FILE_CLOSED, mapOf("filePath" to file.path))
            }
        })

        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                events.filter { it.file != null }.forEach { event ->
                    fire(IdeEventType.FILE_SAVED, mapOf(
                        "filePath" to event.file!!.path
                    ))
                }
            }
        })

        logger.info("[IdeEventBus] Initialized, workspaceId=$workspaceId")
    }

    fun onEvent(listener: (IdeEvent) -> Unit) {
        listeners.add(listener)
    }

    fun fire(type: IdeEventType, payload: Map<String, Any?> = emptyMap()) {
        val event = IdeEvent(type = type, payload = payload)
        listeners.forEach { it(event) }

        transport?.send(OutgoingEnvelope(
            type = "event",
            sessionId = transport.sessionId,
            workspaceId = workspaceId,
            event = event
        ))
    }

    private fun detectLanguage(file: VirtualFile): String? {
        return when (file.extension?.lowercase()) {
            "java" -> "java"
            "kt", "kts" -> "kotlin"
            "cs" -> "csharp"
            "py" -> "python"
            "js" -> "javascript"
            "ts" -> "typescript"
            "go" -> "go"
            "rs" -> "rust"
            else -> file.fileType.name.lowercase()
        }
    }
}