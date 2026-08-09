package com.mcp.plugin.capability

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

@Service(Service.Level.PROJECT)
class CapabilityAdapter(private val project: Project) {
    private val logger = Logger.getInstance(CapabilityAdapter::class.java)

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
        return try {
            val file = File(filePath)
            if (!file.exists()) return mapOf("error" to "File not found: $filePath")
            mapOf("content" to file.readText(), "filePath" to filePath)
        } catch (e: Exception) {
            mapOf("error" to e.message)
        }
    }

    private fun writeFile(params: Map<String, Any?>): Map<String, Any?> {
        val filePath = params["filePath"] as? String ?: return mapOf("error" to "filePath required")
        val content = params["content"] as? String ?: return mapOf("error" to "content required")
        return try {
            ApplicationManager.getApplication().invokeAndWait {
                com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                    val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
                    if (vf != null) {
                        val doc = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(vf)
                        doc?.setText(content)
                    } else {
                        File(filePath).writeText(content)
                        LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
                    }
                }
            }
            mapOf("success" to true, "filePath" to filePath)
        } catch (e: Exception) {
            mapOf("error" to e.message)
        }
    }

    private fun readDirectory(params: Map<String, Any?>): Map<String, Any?> {
        val path = params["path"] as? String ?: project.basePath ?: return mapOf("error" to "path required")
        val depth = (params["depth"] as? Number)?.toInt() ?: 2
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return mapOf("error" to "Not a directory: $path")

        val result = mutableListOf<Map<String, Any?>>()
        dir.walkTopDown()
            .maxDepth(depth)
            .filter { !it.path.contains("\\.git\\") && !it.path.contains("\\.idea\\") }
            .filter { !it.path.contains("\\node_modules\\") && !it.path.contains("\\target\\") }
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
            "language" to (currentFile?.let { detectLanguage(it) }),
            "openFiles" to FileEditorManager.getInstance(project).openFiles.map { it.path }
        )
    }

    private fun getDiagnostics(params: Map<String, Any?>): Map<String, Any?> {
        return mapOf("diagnostics" to emptyList<Any>())
    }

    private fun getGitStatus(params: Map<String, Any?>): Map<String, Any?> {
        val path = params["path"] as? String ?: project.basePath ?: return mapOf("error" to "path required")
        return try {
            val changeListManager = ChangeListManager.getInstance(project)
            val changes = changeListManager.allChanges
            val result = changes.map { change ->
                val vf = if (change.afterRevision != null) {
                    change.afterRevision!!.file
                } else {
                    change.beforeRevision?.file
                }
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
        val cwd = params["cwd"] as? String ?: project.basePath
        return try {
            val process = ProcessBuilder(command.split(" "))
                .directory(cwd?.let { File(it) })
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            mapOf("output" to output, "exitCode" to process.exitValue())
        } catch (e: Exception) {
            mapOf("error" to e.message)
        }
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