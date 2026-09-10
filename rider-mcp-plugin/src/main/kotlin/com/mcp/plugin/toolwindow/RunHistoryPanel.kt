package com.mcp.plugin.toolwindow

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.mcp.plugin.session.RunSummary
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

class RunHistoryPanel {

    private val dateFormat = SimpleDateFormat("HH:mm:ss")

    private val listPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }

    private val scrollPane = JBScrollPane(listPanel).apply {
        verticalScrollBarPolicy = javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        border = BorderFactory.createEmptyBorder()
    }

    val component: JComponent get() = scrollPane

    private val runs = mutableListOf<RunSummary>()
    private val maxEntries = 50

    var onRunSelected: ((RunSummary) -> Unit)? = null

    var onReplayRun: ((RunSummary) -> Unit)? = null

    fun addRun(summary: RunSummary) {
        runs.add(0, summary)
        while (runs.size > maxEntries) {
            runs.removeAt(runs.size - 1)
        }
        refreshList()
    }

    fun updateRunStatus(runId: String, status: String, endTime: Long = System.currentTimeMillis()) {
        val index = runs.indexOfFirst { it.runId == runId }
        if (index >= 0) {
            runs[index] = runs[index].copy(status = status, endTime = endTime)
            refreshList()
        }
    }

    fun clear() {
        runs.clear()
        refreshList()
    }

    private fun refreshList() {
        SwingUtilities.invokeLater {
            listPanel.removeAll()
            for (run in runs) {
                listPanel.add(createRunCard(run))
                listPanel.add(Box.createVerticalStrut(4))
            }
            listPanel.add(Box.createVerticalGlue())
            listPanel.revalidate()
            listPanel.repaint()
        }
    }

    private fun createRunCard(run: RunSummary): JPanel {
        val card = JPanel(BorderLayout(8, 4)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
            )
            maximumSize = Dimension(Int.MAX_VALUE, 60)
            background = when {
                run.isActive -> JBColor(0xE8F5E9, 0x1B3A1B)
                run.isFailed -> JBColor(0xFFEBEE, 0x3A1B1B)
                else -> JBColor(0xF5F5F5, 0x2D2D2D)
            }
        }

        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(JLabel("${run.mode} / ${run.model}").apply {
                font = Font("SansSerif", Font.BOLD, 11)
            }, BorderLayout.WEST)

            add(JLabel(statusBadge(run.status)).apply {
                font = Font("SansSerif", Font.BOLD, 10)
                foreground = statusColor(run.status)
            }, BorderLayout.EAST)
        }

        val messageLabel = JLabel(truncate(run.userMessage, 60)).apply {
            font = Font("SansSerif", Font.PLAIN, 11)
            foreground = JBColor(0x555555, 0xAAAAAA)
        }

        val metaPanel = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JLabel("${formatTime(run.startTime)}").apply {
                font = Font("SansSerif", Font.PLAIN, 10)
                foreground = JBColor.GRAY
            })
            add(Box.createHorizontalStrut(12))
            add(JLabel("${run.durationMs / 1000}s").apply {
                font = Font("SansSerif", Font.PLAIN, 10)
                foreground = JBColor.GRAY
            })
            add(Box.createHorizontalStrut(12))
            add(JLabel("${run.toolCallCount} tools").apply {
                font = Font("SansSerif", Font.PLAIN, 10)
                foreground = JBColor.GRAY
            })
            if (run.totalTokens > 0) {
                add(Box.createHorizontalStrut(12))
                add(JLabel("${run.totalTokens} tok").apply {
                    font = Font("SansSerif", Font.PLAIN, 10)
                    foreground = JBColor.GRAY
                })
            }
            add(Box.createHorizontalGlue())
            if (run.isCompleted || run.isFailed) {
                val replayBtn = JButton("Replay").apply {
                    font = Font("SansSerif", Font.PLAIN, 10)
                    addActionListener { onReplayRun?.invoke(run) }
                }
                add(replayBtn)
            }
        }

        val centerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(headerPanel, BorderLayout.NORTH)
            add(messageLabel, BorderLayout.CENTER)
            add(metaPanel, BorderLayout.SOUTH)
        }

        card.add(centerPanel, BorderLayout.CENTER)

        if (run.isCompleted || run.isFailed) {
            card.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    onRunSelected?.invoke(run)
                }
            })
            card.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        }

        return card
    }

    private fun statusBadge(status: String): String = when (status) {
        "RUNNING" -> "RUNNING"
        "COMPLETED" -> "OK"
        "FAILED" -> "FAILED"
        "CANCELLED" -> "CANCELLED"
        else -> status
    }

    private fun statusColor(status: String): JBColor = when (status) {
        "RUNNING" -> JBColor(0x2E7D32, 0x66BB6A)
        "COMPLETED" -> JBColor(0x1565C0, 0x64B5F6)
        "FAILED" -> JBColor(0xC62828, 0xEF5350)
        "CANCELLED" -> JBColor(0xE65100, 0xFFB74D)
        else -> JBColor.GRAY
    }

    private fun formatTime(millis: Long): String {
        if (millis == 0L) return "--:--:--"
        return try {
            dateFormat.format(Date(millis))
        } catch (_: Exception) {
            "--:--:--"
        }
    }

    private fun truncate(text: String, maxLen: Int): String {
        if (text.length <= maxLen) return text
        return text.take(maxLen - 3) + "..."
    }
}