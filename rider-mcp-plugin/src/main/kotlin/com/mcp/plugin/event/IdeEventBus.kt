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
import com.intellij.util.messages.MessageBusConnection
import com.mcp.plugin.transport.Transport
import com.mcp.plugin.transport.WebSocketTransport
import com.mcp.plugin.util.LanguageDetector
import com.mcp.plugin.util.PluginLogger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class IdeEventBus(private val project: Project) {
    private val logger = Logger.getInstance(IdeEventBus::class.java)
    private val transport: Transport? = project.getService(WebSocketTransport::class.java)
    val workspaceId: String = "${project.name}-${UUID.randomUUID().toString().take(8)}"

    private var connection: MessageBusConnection? = null

    private val debounceExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ide-event-debounce").apply { isDaemon = true }
    }

    private val vfsEventBuffer = ConcurrentHashMap.newKeySet<String>()
    private val vfsEventCount = java.util.concurrent.atomic.AtomicInteger(0)
    private var pendingFlush: java.util.concurrent.ScheduledFuture<*>? = null

    private val vfsEventLock = Any()

    fun init() {
        connection = project.messageBus.connect()

        connection?.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
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

        connection?.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val paths = events.mapNotNull { it.file?.path }.toSet()
                if (paths.isEmpty()) return

                synchronized(vfsEventLock) {
                    vfsEventBuffer.addAll(paths)
                    vfsEventCount.addAndGet(paths.size)
                }

                pendingFlush?.cancel(false)
                val delayMs = if (vfsEventCount.get() > 50) 500L else 200L
                pendingFlush = debounceExecutor.schedule({
                    flushVfsEvents()
                }, delayMs, TimeUnit.MILLISECONDS)
            }
        })

        logger.info("[IdeEventBus] Initialized, workspaceId=$workspaceId")
    }

    private fun flushVfsEvents() {
        val paths: Set<String>
        val count: Int
        synchronized(vfsEventLock) {
            paths = vfsEventBuffer.toSet()
            count = vfsEventCount.get()
            vfsEventBuffer.clear()
            vfsEventCount.set(0)
        }
        if (paths.isEmpty()) return

        if (paths.size <= 5) {
            paths.forEach { filePath ->
                fire(IdeEventType.FILE_SAVED, mapOf("filePath" to filePath))
            }
        } else {
            fire(IdeEventType.FILE_SAVED, mapOf(
                "filePaths" to paths.toList(),
                "count" to paths.size,
                "totalEvents" to count,
                "coalesced" to (count - paths.size)
            ))
        }
        logger.debug("[IdeEventBus] Flushed $count VFS events → ${paths.size} unique paths")
    }

    fun dispose() {
        debounceExecutor.shutdownNow()
        connection?.disconnect()
        connection = null
        logger.info("[IdeEventBus] Disposed, workspaceId=$workspaceId")
    }

    private fun fire(type: IdeEventType, payload: Map<String, Any?> = emptyMap()) {
        try {
            val event = IdeEvent(type = type, payload = payload)

            transport?.send(OutgoingEnvelope(
                type = "event",
                sessionId = transport.sessionId,
                workspaceId = workspaceId,
                event = event
            )) ?: logger.error("[IdeEventBus] Failed to send event: transport is null (type=$type)")
        } catch (e: Exception) {
            logger.error("[IdeEventBus] Failed to send event (type=$type): ${e.message}", e)
            PluginLogger.error("IdeEventBus", "Failed to send event (type=$type): ${e.message}", e)
        }
    }
}