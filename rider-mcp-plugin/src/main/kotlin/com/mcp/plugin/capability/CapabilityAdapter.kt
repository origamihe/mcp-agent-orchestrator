package com.mcp.plugin.capability

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.mcp.plugin.util.LanguageDetector
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class CapabilityAdapter(private val project: Project) {
    private val logger = Logger.getInstance(CapabilityAdapter::class.java)

    companion object {
        private val SENSITIVE_DIRS = setOf(
            ".ssh", ".aws", ".config", ".gnupg", ".docker",
            "AppData", "Windows", "System32", "/etc", "/root", "/home"
        )
        private val SENSITIVE_FILES = setOf(
            "id_rsa", "id_ed25519", "authorized_keys", "known_hosts",
            "credentials", ".env", ".bashrc", ".zshrc", ".profile"
        )
    }

    fun execute(capability: String, params: Map<String, Any?>): Map<String, Any?> {
        return when (capability) {
            "read_file" -> readFile(params)
            "write_file" -> writeFile(params)
            "read_directory" -> readDirectory(params)
            "get_editor_state" -> getEditorState(params)
            "get_diagnostics" -> getDiagnostics(params)
            "get_git_status" -> getGitStatus(params)
            "get_git_diff" -> getGitDiff(params)
            "open_file" -> openFile(params)
            "search_files" -> searchFiles(params)
            "run_terminal" -> runTerminal(params)
            else -> mapOf("error" to "Unknown capability: $capability")
        }
    }

    private fun readFile(params: Map<String, Any?>): Map<String, Any?> {
        val filePath = params["filePath"] as? String ?: return mapOf("error" to "filePath required")

        if (!isPathInWorkspace(filePath)) {
            logger.warn("[CapabilityAdapter] readFile blocked: path outside workspace: $filePath")
            return mapOf("error" to "Path outside workspace", "filePath" to filePath)
        }

        if (isSensitivePath(filePath)) {
            logger.warn("[CapabilityAdapter] readFile blocked: sensitive path: $filePath")
            return mapOf("error" to "Access to sensitive path denied", "filePath" to filePath)
        }

        return try {
            val file = File(filePath)
            if (!file.exists()) return mapOf("error" to "File not found: $filePath")
            mapOf("content" to file.readText(), "filePath" to filePath)
        } catch (e: Exception) {
            logger.error("[CapabilityAdapter] readFile error: ${e.message}")
            mapOf("error" to e.message)
        }
    }

    private fun writeFile(params: Map<String, Any?>): Map<String, Any?> {
        val filePath = params["filePath"] as? String ?: return mapOf("error" to "filePath required")
        val content = params["content"] as? String ?: return mapOf("error" to "content required")

        if (!isPathInWorkspace(filePath)) {
            logger.warn("[CapabilityAdapter] writeFile blocked: path outside workspace: $filePath")
            return mapOf("error" to "Path outside workspace", "filePath" to filePath)
        }

        if (isSensitivePath(filePath)) {
            logger.warn("[CapabilityAdapter] writeFile blocked: sensitive path: $filePath")
            return mapOf("error" to "Access to sensitive path denied", "filePath" to filePath)
        }

        return try {
            ApplicationManager.getApplication().invokeAndWait {
                com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                    val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
                    if (vf != null) {
                        val doc = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(vf)
                        doc?.setText(content)
                    } else {
                        val normalizedPath = normalizePath(filePath)
                        File(normalizedPath).writeText(content)
                        LocalFileSystem.getInstance().refreshAndFindFileByPath(normalizedPath)
                    }
                }
            }
            mapOf("success" to true, "filePath" to filePath)
        } catch (e: Exception) {
            logger.error("[CapabilityAdapter] writeFile error: ${e.message}")
            mapOf("error" to e.message)
        }
    }

    private fun readDirectory(params: Map<String, Any?>): Map<String, Any?> {
        val path = params["path"] as? String ?: project.basePath ?: return mapOf("error" to "path required")
        val depth = (params["depth"] as? Number)?.toInt() ?: 2

        if (!isPathInWorkspace(path)) {
            logger.warn("[CapabilityAdapter] readDirectory blocked: path outside workspace: $path")
            return mapOf("error" to "Path outside workspace", "path" to path)
        }

        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return mapOf("error" to "Not a directory: $path")

        val result = mutableListOf<Map<String, Any?>>()
        val excludedDirs = listOf(".git", ".idea", "node_modules", "target", "__pycache__", ".svn", ".hg")
        val sep = File.separator
        dir.walkTopDown()
            .maxDepth(depth)
            .filter { file ->
                excludedDirs.none { excluded ->
                    file.path.contains("$sep$excluded$sep") || file.path.endsWith("$sep$excluded")
                }
            }
            .take(100)
            .forEach {
                result.add(mapOf(
                    "path" to it.relativeTo(dir).path,
                    "name" to it.name,
                    "isDirectory" to it.isDirectory,
                    "size" to (if (it.isFile) it.length() else 0)
                ))
            }
        return mapOf("path" to path, "entries" to result)
    }

    private fun getEditorState(params: Map<String, Any?>): Map<String, Any?> {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val currentFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()

        return mapOf(
            "currentFilePath" to (currentFile?.path),
            "cursorLine" to (editor?.let { editor.document.getLineNumber(editor.caretModel.primaryCaret.offset) + 1 } ?: 0),
            "cursorColumn" to (editor?.let {
                val caret = it.caretModel.primaryCaret
                caret.offset - it.document.getLineStartOffset(it.document.getLineNumber(caret.offset)) + 1
            } ?: 0),
            "selectedCode" to (editor?.selectionModel?.selectedText?.takeIf { s -> !s.isNullOrBlank() }),
            "language" to (currentFile?.let { LanguageDetector.detect(it) }),
            "openFiles" to FileEditorManager.getInstance(project).openFiles.map { it.path }
        )
    }

    private fun getDiagnostics(params: Map<String, Any?>): Map<String, Any?> {
        val filePath = params["filePath"] as? String

        return try {
            val diagnostics = mutableListOf<Map<String, Any?>>()

            if (filePath != null) {
                val vf = LocalFileSystem.getInstance().findFileByPath(filePath)
                if (vf != null) {
                    collectDiagnosticsForFile(vf, diagnostics)
                }
            } else {
                val openFiles = FileEditorManager.getInstance(project).openFiles
                for (vf in openFiles) {
                    collectDiagnosticsForFile(vf, diagnostics)
                    if (diagnostics.size >= 100) break
                }
            }

            mapOf(
                "diagnostics" to diagnostics,
                "filePath" to filePath,
                "totalCount" to diagnostics.size,
                "errorCount" to diagnostics.count { it["severity"] == "ERROR" },
                "warningCount" to diagnostics.count { it["severity"] == "WARNING" }
            )
        } catch (e: Exception) {
            logger.error("[CapabilityAdapter] getDiagnostics error: ${e.message}")
            mapOf("error" to e.message, "diagnostics" to emptyList<Any>())
        }
    }

    private fun collectDiagnosticsForFile(vf: VirtualFile, diagnostics: MutableList<Map<String, Any?>>) {
        try {
            val doc = FileDocumentManager.getInstance().getDocument(vf) ?: return
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(doc) ?: return

            ReadAction.run<RuntimeException> {
                val holder = HighlightInfoHolder(psiFile)
                val highlights = com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
                    .getHighlights(doc, HighlightInfoType.ERROR, project)
                    ?: com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
                        .getHighlights(doc, HighlightInfoType.WARNING, project)

                if (highlights != null) {
                    for (info in highlights) {
                        diagnostics.add(mapOf(
                            "filePath" to vf.path,
                            "line" to (doc.getLineNumber(info.startOffset) + 1),
                            "column" to (info.startOffset - doc.getLineStartOffset(doc.getLineNumber(info.startOffset)) + 1),
                            "severity" to when {
                                info.type.severity == HighlightInfoType.ERROR.severity -> "ERROR"
                                info.type.severity == HighlightInfoType.WARNING.severity -> "WARNING"
                                info.type.severity == HighlightInfoType.WEAK_WARNING.severity -> "WEAK_WARNING"
                                else -> "INFO"
                            },
                            "message" to info.description,
                            "tooltip" to info.toolTip
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("[CapabilityAdapter] collectDiagnosticsForFile: ${e.message}")
        }
    }

    private fun getGitStatus(params: Map<String, Any?>): Map<String, Any?> {
        val path = params["path"] as? String ?: project.basePath ?: return mapOf("error" to "path required")
        return try {
            val changeListManager = ChangeListManager.getInstance(project)
            val changes = changeListManager.allChanges
            val result = changes.map { change ->
                val vf = change.afterRevision?.file
                    ?: change.beforeRevision?.file
                mapOf(
                    "filePath" to (vf?.path ?: "unknown"),
                    "status" to change.fileStatus.toString()
                )
            }
            mapOf("path" to path, "changes" to result)
        } catch (e: Exception) {
            mapOf("error" to e.message)
        }
    }

    private fun getGitDiff(params: Map<String, Any?>): Map<String, Any?> {
        return try {
            val changes = ChangeListManager.getInstance(project).allChanges
            val diffs = changes.map { change ->
                mapOf(
                    "filePath" to (change.afterRevision?.file?.path ?: change.beforeRevision?.file?.path),
                    "status" to change.fileStatus.toString()
                )
            }
            mapOf("diffs" to diffs)
        } catch (e: Exception) {
            mapOf("error" to e.message)
        }
    }

    private fun openFile(params: Map<String, Any?>): Map<String, Any?> {
        val filePath = params["filePath"] as? String ?: return mapOf("error" to "filePath required")
        val line = (params["line"] as? Number)?.toInt() ?: 0

        if (!isPathInWorkspace(filePath)) {
            logger.warn("[CapabilityAdapter] openFile blocked: path outside workspace: $filePath")
            return mapOf("error" to "Path outside workspace", "filePath" to filePath)
        }

        return try {
            ApplicationManager.getApplication().invokeAndWait {
                val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
                if (vf != null) {
                    FileEditorManager.getInstance(project).openFile(vf, true)
                    if (line > 0) {
                        val editor = FileEditorManager.getInstance(project).selectedTextEditor
                        if (editor != null) {
                            val offset = editor.document.getLineStartOffset(line - 1)
                            editor.caretModel.moveToOffset(offset)
                        }
                    }
                }
            }
            mapOf("success" to true, "filePath" to filePath, "line" to line)
        } catch (e: Exception) {
            mapOf("error" to e.message)
        }
    }

    private fun searchFiles(params: Map<String, Any?>): Map<String, Any?> {
        val pattern = params["pattern"] as? String ?: return mapOf("error" to "pattern required")
        val basePath = project.basePath ?: return mapOf("error" to "no project")
        val dir = File(basePath)
        val results = mutableListOf<String>()
        dir.walkTopDown()
            .filter { it.isFile }
            .filter { it.name.contains(pattern, ignoreCase = true) }
            .take(20)
            .forEach { results.add(it.relativeTo(dir).path) }
        return mapOf("pattern" to pattern, "matches" to results)
    }

    private fun runTerminal(params: Map<String, Any?>): Map<String, Any?> {
        val command = params["command"] as? String ?: return mapOf("error" to "command required")

        val cwd = (params["cwd"] as? String)?.let {
            if (!isPathInWorkspace(it)) {
                logger.warn("[CapabilityAdapter] runTerminal blocked: cwd outside workspace: $it")
                return mapOf("error" to "Working directory outside workspace", "cwd" to it)
            }
            it
        } ?: project.basePath

        val timeoutSeconds = (params["_timeout"] as? String)?.toIntOrNull()
            ?: (params["_timeout"] as? Number)?.toInt()
            ?: 30

        val outputLimit = (params["_outputLimit"] as? String)?.toIntOrNull()
            ?: (params["_outputLimit"] as? Number)?.toInt()
            ?: 1048576

        return try {
            val process = ProcessBuilder(command.split(" "))
                .directory(cwd?.let { File(it) })
                .redirectErrorStream(true)
                .start()

            val output = readWithLimit(process.inputStream, outputLimit)
            val finished = process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)

            if (!finished) {
                process.destroyForcibly()
                logger.warn("[CapabilityAdapter] runTerminal timed out after ${timeoutSeconds}s: $command")
                return mapOf(
                    "error" to "Command timed out after ${timeoutSeconds}s",
                    "output" to output,
                    "exitCode" to -1
                )
            }

            mapOf("output" to output, "exitCode" to process.exitValue())
        } catch (e: Exception) {
            logger.error("[CapabilityAdapter] runTerminal error: ${e.message}")
            mapOf("error" to e.message)
        }
    }

    private fun isPathInWorkspace(filePath: String): Boolean {
        val workspacePath = project.basePath ?: return true
        return try {
            val normalized = normalizePath(filePath)
            val normalizedWorkspace = normalizePath(workspacePath)
            normalized.startsWith(normalizedWorkspace)
        } catch (e: Exception) {
            false
        }
    }

    private fun isSensitivePath(filePath: String): Boolean {
        val normalized = normalizePath(filePath).lowercase()
        val parts = normalized.split(File.separator, "/", "\\").filter { it.isNotBlank() }

        return SENSITIVE_DIRS.any { sensitive ->
            parts.any { part -> part.lowercase() == sensitive.lowercase() }
        } || SENSITIVE_FILES.any { sensitive ->
            parts.lastOrNull()?.lowercase() == sensitive.lowercase()
        }
    }

    private fun normalizePath(filePath: String): String {
        return try {
            Path.of(filePath).normalize().toString()
        } catch (e: Exception) {
            filePath
        }
    }

    private fun readWithLimit(inputStream: InputStream, limit: Int): String {
        val buffer = ByteArray(8192)
        val output = StringBuilder()
        var totalRead = 0
        var bytesRead: Int

        while (totalRead < limit && inputStream.read(buffer).also { bytesRead = it } != -1) {
            val toRead = minOf(bytesRead, limit - totalRead)
            output.append(String(buffer, 0, toRead, Charsets.UTF_8))
            totalRead += toRead
        }

        if (totalRead >= limit) {
            output.append("\n... [output truncated at ${limit / 1024}KB]")
        }

        return output.toString()
    }
}