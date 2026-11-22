package com.rajatmalik.commitgenerator.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListSelectionModel

/**
 * Small modal list picker used for profiles and repositories. Replaces the
 * deprecated `Messages.showChooseDialog` helper with a consistent UI.
 */
class SelectionDialog<T>(
    project: Project,
    private val dialogTitle: String,
    private val message: String,
    items: List<T>,
    private val label: (T) -> String,
    private val initial: T? = null,
) : DialogWrapper(project, true) {

    private val list = JBList<T>(CollectionListModel(items))

    init {
        title = dialogTitle
        setSize(680, 460)
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): java.awt.Component {
                val text = value?.let { label(it as T) } ?: ""
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
            }
        }
        val initialIndex = items.indexOfFirst { it == initial }
        if (initialIndex >= 0) {
            list.selectedIndex = initialIndex
        } else if (items.isNotEmpty()) {
            list.selectedIndex = 0
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val center = JBListPanel(message, list)
        center.preferredSize = Dimension(640, 380)
        return center
    }

    fun showAndGetItem(): T? {
        if (!showAndGet()) {
            return null
        }
        return list.selectedValue
    }

    private class JBListPanel(message: String, list: JBList<*>) : javax.swing.JPanel(BorderLayout(0, 8)) {
        init {
            border = JBUI.Borders.empty(8)
            add(JBLabel(message), BorderLayout.NORTH)
            add(JBScrollPane(list), BorderLayout.CENTER)
        }
    }
}
