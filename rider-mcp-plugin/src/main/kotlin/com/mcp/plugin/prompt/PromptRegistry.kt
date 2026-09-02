package com.mcp.plugin.prompt

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.mcp.plugin.event.IdeEventBus
import com.mcp.plugin.event.OutgoingEnvelope
import com.mcp.plugin.util.LanguageDetector
import com.mcp.plugin.transport.WebSocketTransport

data class PromptDef(
    val id: String,
    val label: String,
    val description: String,
    val prompt: String
)

class PromptRegistry : DefaultActionGroup("MCP Agent", true) {

    companion object {
        val prompts: List<PromptDef> = listOf(
            PromptDef(
                id = "explain",
                label = "MCP: Explain Code",
                description = "让 Agent 解释选中的代码",
                prompt = "请详细解释这段代码的功能、逻辑和执行流程："
            ),
            PromptDef(
                id = "optimize",
                label = "MCP: Optimize Code",
                description = "让 Agent 优化选中的代码",
                prompt = "请优化这段代码，提高性能、可读性和可维护性。请直接给出优化后的代码："
            ),
            PromptDef(
                id = "refactor",
                label = "MCP: Refactor Code",
                description = "让 Agent 重构选中的代码",
                prompt = "请重构这段代码，遵循 SOLID 原则和设计模式，提高代码质量。请直接给出重构后的代码："
            ),
            PromptDef(
                id = "generate_test",
                label = "MCP: Generate Test",
                description = "让 Agent 生成单元测试",
                prompt = "请为这段代码生成完整的单元测试，覆盖所有主要分支和边界条件："
            ),
            PromptDef(
                id = "review",
                label = "MCP: Code Review",
                description = "让 Agent 审查代码",
                prompt = "请审查这段代码，找出潜在的问题、安全漏洞和代码异味："
            ),
            PromptDef(
                id = "fix",
                label = "MCP: Fix Issues",
                description = "让 Agent 修复代码问题",
                prompt = "请修复这段代码中的所有问题，包括 bug、性能问题和代码异味："
            ),
            PromptDef(
                id = "generate_doc",
                label = "MCP: Generate Documentation",
                description = "让 Agent 生成文档注释",
                prompt = "请为这段代码生成完整的 JavaDoc/KDoc 文档注释："
            ),
            PromptDef(
                id = "generate_commit",
                label = "MCP: Generate Commit Message",
                description = "让 Agent 生成提交信息",
                prompt = "请基于当前 git diff 生成一条规范的 commit message："
            )
        )
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return prompts.map { prompt ->
            object : AnAction(prompt.label, prompt.description, null) {
                override fun actionPerformed(e: AnActionEvent) {
                    val project = e.project ?: return
                    val editor = e.getData(CommonDataKeys.EDITOR)
                    val selectedCode = editor?.selectionModel?.selectedText?.takeIf { it.isNotBlank() }
                        ?: editor?.document?.text ?: ""

                    val transport = project.getService(WebSocketTransport::class.java)
                    val eventBus = project.getService(IdeEventBus::class.java)

                    val fullPrompt = "${prompt.prompt}\n\n```\n$selectedCode\n```"

                    transport.send(OutgoingEnvelope(
                        type = "chat",
                        sessionId = transport.sessionId,
                        workspaceId = eventBus?.workspaceId,
                        content = fullPrompt,
                        context = eventBus?.let { collectContext(project) }
                    ))
                }
            }
        }.toTypedArray()
    }

    private fun collectContext(project: com.intellij.openapi.project.Project): Map<String, Any?> {
        val editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedTextEditor
        val currentFile = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        return mapOf(
            "currentFilePath" to (currentFile?.path),
            "cursorLine" to (editor?.let { it.document.getLineNumber(it.caretModel.primaryCaret.offset) + 1 } ?: 0),
            "selectedCode" to (editor?.selectionModel?.selectedText?.takeIf { it.isNotBlank() }),
            "language" to (currentFile?.let { LanguageDetector.detect(it) })
        )
    }
}