package com.rajatmalik.commitgenerator.ui

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class TextPreviewDialog(
    project: Project,
    dialogTitle: String,
    private val text: String,
) : DialogWrapper(project, true) {

    init {
        title = dialogTitle
        setSize(960, 640)
        isResizable = true
        init()
    }

    override fun createCenterPanel(): JComponent {
        val area = JBTextArea(text).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = false
            font = JBUI.Fonts.create("JetBrains Mono", 12)
            caretPosition = 0
            border = JBUI.Borders.empty(8)
        }
        return JBScrollPane(area).apply {
            preferredSize = Dimension(920, 540)
        }
    }

    override fun createSouthPanel(): JComponent {
        val copy = JButton("Copy").apply {
            addActionListener {
                CopyPasteManager.getInstance().setContents(StringSelection(text))
            }
        }
        val panel = JPanel(BorderLayout())
        panel.add(copy, BorderLayout.WEST)
        panel.add(super.createSouthPanel(), BorderLayout.EAST)
        return panel
    }
}
