package com.mcp.plugin.diff

import com.intellij.diff.DiffManager
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.contents.DocumentContentImpl
import com.intellij.diff.merge.MergeRequest
import com.intellij.diff.merge.MergeResult
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

@Service(Service.Level.PROJECT)
class DiffApplier(private val project: Project) {
    private val logger = Logger.getInstance(DiffApplier::class.java)

    fun applyDiff(filePath: String, diff: String, callback: (Boolean) -> Unit = {}) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    logger.warn("[DiffApplier] File not found: $filePath")
                    callback(false)
                    return@invokeLater
                }

                val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file) ?: run {
                    callback(false)
                    return@invokeLater
                }

                val originalDoc = FileDocumentManager.getInstance().getDocument(vf) ?: run {
                    callback(false)
                    return@invokeLater
                }

                val patchedContent = applyPatchInMemory(originalDoc.text, diff)
                if (patchedContent == null) {
                    callback(false)
                    return@invokeLater
                }

                WriteCommandAction.runWriteCommandAction(project) {
                    originalDoc.setText(patchedContent)
                    FileDocumentManager.getInstance().saveDocument(originalDoc)
                }
                logger.info("[DiffApplier] Applied diff to: $filePath")
                callback(true)
            } catch (e: Exception) {
                logger.error("[DiffApplier] Failed: ${e.message}")
                callback(false)
            }
        }
    }

    fun applyFullContent(filePath: String, newContent: String) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    file.parentFile?.mkdirs()
                    file.writeText(newContent)
                    LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
                    logger.info("[DiffApplier] Created new file: $filePath")
                    return@invokeLater
                }

                val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file) ?: return@invokeLater
                val doc = FileDocumentManager.getInstance().getDocument(vf) ?: return@invokeLater

                WriteCommandAction.runWriteCommandAction(project) {
                    doc.setText(newContent)
                    FileDocumentManager.getInstance().saveDocument(doc)
                }
                logger.info("[DiffApplier] Applied full content to: $filePath")
            } catch (e: Exception) {
                logger.error("[DiffApplier] Failed: ${e.message}")
            }
        }
    }

    private fun applyPatchInMemory(original: String, diff: String): String? {
        return try {
            val lines = original.lines().toMutableList()
            val diffLines = diff.lines()
            var currentLine = 0
            var i = 0

            while (i < diffLines.size) {
                val line = diffLines[i]
                when {
                    line.startsWith("@@") -> {
                        val match = Regex("@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@").find(line)
                        currentLine = (match?.groupValues?.get(2)?.toInt() ?: 1) - 1
                        i++
                    }
                    line.startsWith("+") && !line.startsWith("+++") -> {
                        lines.add(currentLine, line.removePrefix("+"))
                        currentLine++
                        i++
                    }
                    line.startsWith("-") && !line.startsWith("---") -> {
                        if (currentLine < lines.size) {
                            lines.removeAt(currentLine)
                        }
                        i++
                    }
                    line.startsWith(" ") -> {
                        currentLine++
                        i++
                    }
                    else -> i++
                }
            }
            lines.joinToString("\n")
        } catch (e: Exception) {
            logger.error("[DiffApplier] Patch failed: ${e.message}")
            null
        }
    }
}