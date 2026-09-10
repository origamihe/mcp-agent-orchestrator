package com.mcp.plugin.toolwindow

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.*

class DiffPreviewDialog(
    private val project: Project,
    private val filePath: String,
    private val diff: String,
    private val isFullContent: Boolean
) : DialogWrapper(project, true) {

    private val logger = Logger.getInstance(DiffPreviewDialog::class.java)
    private var approved = false

    init {
        title = if (isFullContent) "Apply Full Content" else "Apply Diff"
        init()
    }

    val isApproved: Boolean get() = approved

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(8, 8)).apply {
            preferredSize = Dimension(600, 400)
        }

        val headerPanel = JPanel(BorderLayout()).apply {
            add(JLabel("File: $filePath").apply {
                font = Font("SansSerif", Font.BOLD, 12)
            }, BorderLayout.WEST)
            add(JLabel(if (isFullContent) "Full Content Replacement" else "Unified Diff").apply {
                font = Font("SansSerif", Font.PLAIN, 11)
                foreground = JBColor.GRAY
            }, BorderLayout.EAST)
        }

        val diffPane = JTextPane().apply {
            isEditable = false
            font = Font("Monospaced", Font.PLAIN, 11)
            text = diff
        }

        val diffScroll = JBScrollPane(diffPane).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        }

        panel.add(headerPanel, BorderLayout.NORTH)
        panel.add(diffScroll, BorderLayout.CENTER)

        return panel
    }

    override fun createActions(): Array<Action> {
        val applyAction = object : DialogWrapperAction("Apply") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                approved = true
                close(OK_EXIT_CODE)
            }
        }

        val cancelAction = object : DialogWrapperAction("Cancel") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                approved = false
                close(CANCEL_EXIT_CODE)
            }
        }

        return arrayOf(applyAction, cancelAction)
    }
}