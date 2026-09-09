package com.mcp.plugin.util

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object PluginLogger {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
    private val logDir: File by lazy {
        val dir = File(PathManager.getLogPath(), "rider-mcp-plugin")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val logFile: File by lazy {
        val dateStr = SimpleDateFormat("yyyy-MM-dd").format(Date())
        File(logDir, "mcp-plugin-$dateStr.log")
    }

    private val writer: PrintWriter by lazy {
        PrintWriter(FileWriter(logFile, true), true)
    }

    private val logQueue = ConcurrentLinkedQueue<String>()
    private val flushExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "plugin-log-flush").apply { isDaemon = true }
    }

    init {
        flushExecutor.scheduleWithFixedDelay({
            flushQueue()
        }, 1, 1, TimeUnit.SECONDS)

        Runtime.getRuntime().addShutdownHook(Thread({
            flushQueue()
            try { writer.close() } catch (_: Exception) {}
            flushExecutor.shutdownNow()
        }, "plugin-log-shutdown"))
    }

    private fun flushQueue() {
        try {
            var line = logQueue.poll()
            while (line != null) {
                writer.println(line)
                line = logQueue.poll()
            }
            writer.flush()
        } catch (_: Exception) {
        }
    }

    fun logDirectory(): File = logDir

    fun currentLogFile(): File = logFile

    fun getLogContent(maxLines: Int = 500): String {
        flushQueue()
        return try {
            logFile.readLines().takeLast(maxLines).joinToString("\n")
        } catch (_: Exception) {
            "Unable to read log file: ${logFile.absolutePath}"
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

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        val timestamp = dateFormat.format(Date())
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

        val ideaLogger = Logger.getInstance(tag)
        when (level) {
            "ERROR" -> ideaLogger.error(message, throwable)
            "WARN" -> ideaLogger.warn(message)
            "DEBUG" -> ideaLogger.debug(message)
            else -> ideaLogger.info(message)
        }
    }
}