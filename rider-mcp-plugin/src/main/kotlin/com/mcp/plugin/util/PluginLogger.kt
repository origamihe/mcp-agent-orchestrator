package com.mcp.plugin.util

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.mcp.plugin.McpPluginSettings
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

object PluginLogger {

    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())
    private val ideaLogger = Logger.getInstance("McpPlugin")

    private val maxFileSizeBytes: Long
        get() {
            return try {
                McpPluginSettings.instance.logMaxFileSize.toLong() * 1024 * 1024
            } catch (_: Exception) {
                20L * 1024 * 1024
            }
        }

    private val maxRetentionDays: Int
        get() {
            return try {
                McpPluginSettings.instance.logRetentionDays
            } catch (_: Exception) {
                7
            }
        }

    private val logDir: File by lazy {
        val dir = File(PathManager.getLogPath(), "rider-mcp-plugin")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val currentLogFile = AtomicReference<File>()
    private val writer = AtomicReference<PrintWriter>()

    private val logQueue = ConcurrentLinkedQueue<String>()
    private val flushExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "plugin-log-flush").apply { isDaemon = true }
    }

    private val flushFailureCount = AtomicInteger(0)
    private val flushFailureListeners = mutableListOf<(String) -> Unit>()

    private fun ensureWriter(): PrintWriter? {
        val now = Instant.now()
        val dateStr = dateFormat.format(now)
        val fileName = "mcp-plugin-$dateStr.log"
        val targetFile = File(logDir, fileName)

        val existing = currentLogFile.get()
        val existingWriter = writer.get()

        if (existing != null && existing == targetFile) {
            if (targetFile.length() > maxFileSizeBytes) {
                try { existingWriter?.close() } catch (_: Exception) {}
                rotateLogFile(targetFile)
                val newWriter = PrintWriter(FileWriter(targetFile, true), true)
                currentLogFile.set(targetFile)
                writer.set(newWriter)
                return newWriter
            }
            return existingWriter
        }

        try { existingWriter?.close() } catch (_: Exception) {}
        val newWriter = PrintWriter(FileWriter(targetFile, true), true)
        currentLogFile.set(targetFile)
        writer.set(newWriter)
        cleanupOldLogs()
        return newWriter
    }

    private fun rotateLogFile(file: File) {
        var index = 1
        var rotated: File
        do {
            rotated = File(logDir, "${file.nameWithoutExtension}.$index.${file.extension}")
            index++
        } while (rotated.exists())
        try {
            file.renameTo(rotated)
        } catch (_: Exception) {}
    }

    private fun cleanupOldLogs() {
        try {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(maxRetentionDays.toLong())
            logDir.listFiles()?.filter { it.isFile && it.name.startsWith("mcp-plugin-") }?.forEach { file ->
                if (file.lastModified() < cutoff) {
                    try { file.delete() } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    init {
        flushExecutor.scheduleWithFixedDelay({
            flushQueue()
        }, 1, 1, TimeUnit.SECONDS)

        Runtime.getRuntime().addShutdownHook(Thread({
            flushQueue()
            try { writer.get()?.close() } catch (_: Exception) {}
            flushExecutor.shutdownNow()
        }, "plugin-log-shutdown"))
    }

    private fun flushQueue() {
        try {
            val w = ensureWriter() ?: return
            var line = logQueue.poll()
            while (line != null) {
                w.println(line)
                line = logQueue.poll()
            }
            w.flush()
            if (flushFailureCount.getAndSet(0) > 0) {
                ideaLogger.info("PluginLogger flush recovered after failures")
            }
        } catch (e: Exception) {
            val failures = flushFailureCount.incrementAndGet()
            ideaLogger.warn("PluginLogger flush failed (#$failures): ${e.message}", e)
            if (failures >= 3) {
                val msg = "Plugin log write failed $failures times: ${e.message}"
                flushFailureListeners.forEach { it(msg) }
            }
        }
    }

    fun logDirectory(): File = logDir

    fun onFlushFailure(listener: (String) -> Unit) {
        flushFailureListeners.add(listener)
    }

    fun currentLogFile(): File = currentLogFile.get() ?: File(logDir, "mcp-plugin-${dateFormat.format(Instant.now())}.log")

    fun getLogContent(maxLines: Int = 500): String {
        flushQueue()
        val file = currentLogFile.get() ?: return "Log file not yet created"
        return try {
            file.readLines().takeLast(maxLines).joinToString("\n")
        } catch (e: Exception) {
            "Unable to read log file: ${file.absolutePath} (${e.message})"
        }
    }

    fun info(tag: String, message: String) {
        log("INFO", tag, message, null)
    }

    fun warn(tag: String, message: String) {
        log("WARN", tag, message, null)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        log("ERROR", tag, message, throwable)
    }

    fun debug(tag: String, message: String) {
        log("DEBUG", tag, message, null)
    }

    fun infoContext(tag: String, message: String, sessionId: String? = null, runId: String? = null, generation: Int = 0) {
        log("INFO", tag, buildContextMessage(message, sessionId, runId, generation), null)
    }

    fun warnContext(tag: String, message: String, sessionId: String? = null, runId: String? = null, generation: Int = 0) {
        log("WARN", tag, buildContextMessage(message, sessionId, runId, generation), null)
    }

    fun errorContext(tag: String, message: String, sessionId: String? = null, runId: String? = null, generation: Int = 0, throwable: Throwable? = null) {
        log("ERROR", tag, buildContextMessage(message, sessionId, runId, generation), throwable)
    }

    private fun buildContextMessage(message: String, sessionId: String?, runId: String?, generation: Int): String {
        val parts = mutableListOf<String>()
        if (sessionId != null) parts.add("session=$sessionId")
        if (runId != null) parts.add("run=$runId")
        if (generation > 0) parts.add("gen=$generation")
        return if (parts.isEmpty()) message else "$message [${parts.joinToString(" ")}]"
    }

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        if (!isLevelEnabled(level)) return

        val now = Instant.now()
        val timestamp = timestampFormat.format(now)
        val threadName = Thread.currentThread().name
        val sb = StringBuilder()
        sb.append("$timestamp [$level] [$threadName] $tag - $message")
        if (throwable != null) {
            sb.append("\n")
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            sb.append(sw.toString())
        }
        val line = sb.toString()
        logQueue.add(line)

        when (level) {
            "ERROR" -> ideaLogger.error(message, throwable)
            "WARN" -> ideaLogger.warn(message)
            "DEBUG" -> ideaLogger.debug(message)
            else -> ideaLogger.info(message)
        }
    }

    private fun isLevelEnabled(level: String): Boolean {
        val configuredLevel = try {
            McpPluginSettings.instance.logLevel
        } catch (_: Exception) {
            "INFO"
        }
        val levels = listOf("DEBUG", "INFO", "WARN", "ERROR")
        val configuredIndex = levels.indexOf(configuredLevel).takeIf { it >= 0 } ?: 1
        val levelIndex = levels.indexOf(level).takeIf { it >= 0 } ?: 1
        return levelIndex >= configuredIndex
    }
}