package com.mcp.plugin.diff

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.mcp.plugin.util.PathValidator
import java.io.File

@Service(Service.Level.PROJECT)
class DiffApplier(private val project: Project) {
    private val logger = Logger.getInstance(DiffApplier::class.java)

    fun applyDiff(filePath: String, diff: String): Boolean {
        if (!PathValidator.isPathInWorkspace(filePath, project.basePath)) {
            logger.warn("[DiffApplier] applyDiff blocked: path outside workspace: $filePath")
            return false
        }
        if (PathValidator.isSensitivePath(filePath)) {
            logger.warn("[DiffApplier] applyDiff blocked: sensitive path: $filePath")
            return false
        }

        return try {
            var success = false
            ApplicationManager.getApplication().invokeAndWait {
                try {
                    val file = File(filePath)
                    if (!file.exists()) {
                        logger.warn("[DiffApplier] File not found: $filePath")
                        return@invokeAndWait
                    }

                    val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file) ?: run {
                        logger.warn("[DiffApplier] VirtualFile not found for: $filePath")
                        return@invokeAndWait
                    }

                    val originalDoc = FileDocumentManager.getInstance().getDocument(vf) ?: run {
                        logger.warn("[DiffApplier] Document not found for: $filePath")
                        return@invokeAndWait
                    }

                    val patchedContent = applyPatchInMemory(originalDoc.text, diff)
                    if (patchedContent == null) {
                        return@invokeAndWait
                    }

                    WriteCommandAction.runWriteCommandAction(project) {
                        originalDoc.setText(patchedContent)
                        FileDocumentManager.getInstance().saveDocument(originalDoc)
                    }
                    logger.info("[DiffApplier] Applied diff to: $filePath")
                    success = true
                } catch (e: Exception) {
                    logger.error("[DiffApplier] Failed: ${e.message}")
                }
            }
            success
        } catch (e: Exception) {
            logger.error("[DiffApplier] Failed: ${e.message}")
            false
        }
    }

    fun applyFullContent(filePath: String, newContent: String): Boolean {
        if (!PathValidator.isPathInWorkspace(filePath, project.basePath)) {
            logger.warn("[DiffApplier] applyFullContent blocked: path outside workspace: $filePath")
            return false
        }
        if (PathValidator.isSensitivePath(filePath)) {
            logger.warn("[DiffApplier] applyFullContent blocked: sensitive path: $filePath")
            return false
        }

        return try {
            var success = false
            ApplicationManager.getApplication().invokeAndWait {
                try {
                    val file = File(filePath)
                    if (!file.exists()) {
                        file.parentFile?.mkdirs()
                        file.writeText(newContent)
                        LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
                        logger.info("[DiffApplier] Created new file: $filePath")
                        success = true
                        return@invokeAndWait
                    }

                    val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file) ?: run {
                        logger.warn("[DiffApplier] VirtualFile not found for full content: $filePath")
                        return@invokeAndWait
                    }
                    val doc = FileDocumentManager.getInstance().getDocument(vf) ?: run {
                        logger.warn("[DiffApplier] Document not found for full content: $filePath")
                        return@invokeAndWait
                    }

                    WriteCommandAction.runWriteCommandAction(project) {
                        doc.setText(newContent)
                        FileDocumentManager.getInstance().saveDocument(doc)
                    }
                    logger.info("[DiffApplier] Applied full content to: $filePath")
                    success = true
                } catch (e: Exception) {
                    logger.error("[DiffApplier] Failed: ${e.message}")
                }
            }
            success
        } catch (e: Exception) {
            logger.error("[DiffApplier] Failed: ${e.message}")
            false
        }
    }

    private fun applyPatchInMemory(original: String, diff: String): String? {
        return try {
            val lines = original.lines().toMutableList()
            val diffLines = diff.lines().filter { it.isNotBlank() }
            var currentLine = 0
            var i = 0

            while (i < diffLines.size) {
                val line = diffLines[i]
                when {
                    line.startsWith("@@") -> {
                        val match = Regex("@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@").find(line)
                        if (match != null) {
                            currentLine = (match.groupValues[2].toIntOrNull() ?: 1) - 1
                        }
                        i++
                    }
                    line.startsWith("+") && !line.startsWith("+++") -> {
                        val content = line.removePrefix("+")
                        if (currentLine <= lines.size) {
                            lines.add(currentLine, content)
                        } else {
                            lines.add(content)
                        }
                        currentLine++
                        i++
                    }
                    line.startsWith("-") && !line.startsWith("---") -> {
                        if (currentLine < lines.size) {
                            lines.removeAt(currentLine)
                        }
                        i++
                    }
                    line.startsWith(" ") || line.startsWith("\\") -> {
                        currentLine++
                        i++
                    }
                    else -> i++
                }
            }

            val result = lines.joinToString("\n")
            if (result.isEmpty() && original.isNotEmpty()) {
                logger.warn("[DiffApplier] Patch produced empty result from non-empty original")
                return null
            }
            result
        } catch (e: IndexOutOfBoundsException) {
            logger.error("[DiffApplier] Patch failed (index error): ${e.message}")
            null
        } catch (e: Exception) {
            logger.error("[DiffApplier] Patch failed: ${e.message}")
            null
        }
    }
}