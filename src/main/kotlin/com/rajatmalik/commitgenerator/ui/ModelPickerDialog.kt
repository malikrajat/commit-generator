package com.rajatmalik.commitgenerator.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.rajatmalik.commitgenerator.core.Product
import com.rajatmalik.commitgenerator.core.domain.ProviderProfile
import com.rajatmalik.commitgenerator.core.ports.ModelInfo
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ModelPickerDialog(
    project: Project,
    private val profile: ProviderProfile,
    private val models: List<ModelInfo>,
) : DialogWrapper(project, true) {

    private val searchField = JBTextField()
    private val manualField = JBTextField()
    private val listModel = CollectionListModel<ModelInfo>()
    private val list = JBList(listModel)
    private var selectedModel: String? = null

    init {
        title = "${Product.NAME}: Select model for ${profile.label}"
        setSize(680, 520)
        init()
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): java.awt.Component {
                val model = value as? ModelInfo
                val label = if (model == null) "" else {
                    if (model.label != model.id) "${model.id}  —  ${model.label}" else model.id
                }
                return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus)
            }
        }
        searchField.emptyText.text = "Filter models"
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = filter()
            override fun removeUpdate(e: DocumentEvent) = filter()
            override fun changedUpdate(e: DocumentEvent) = filter()
        })
        manualField.emptyText.text = "Or enter a model id manually"
        filter()
        list.selectedIndex = 0
    }

    override fun createCenterPanel(): JComponent {
        val top = JPanel(BorderLayout(0, 6)).apply {
            border = JBUI.Borders.emptyBottom(6)
            add(JBLabel("Models reported by this endpoint"), BorderLayout.NORTH)
            add(searchField, BorderLayout.CENTER)
        }
        val center = JPanel(BorderLayout(0, 8)).apply {
            border = JBUI.Borders.empty(4)
            add(top, BorderLayout.NORTH)
            add(JBScrollPane(list), BorderLayout.CENTER)
            add(manualField, BorderLayout.SOUTH)
        }
        center.preferredSize = Dimension(640, 430)
        return center
    }

    override fun doValidate(): ValidationInfo? {
        if (manualField.text.trim().isEmpty() && list.selectedValue == null) {
            return ValidationInfo("Choose a model or enter a model id.", manualField)
        }
        return null
    }

    fun showAndGetModel(): String? {
        if (!showAndGet()) {
            return null
        }
        return manualField.text.trim().ifEmpty {
            (list.selectedValue as? ModelInfo)?.id
        }
    }

    private fun filter() {
        val query = searchField.text.trim().lowercase()
        val filtered = if (query.isEmpty()) {
            models
        } else {
            models.filter {
                it.id.lowercase().contains(query) || it.label.lowercase().contains(query)
            }
        }
        list.model = CollectionListModel(filtered)
        if (filtered.isNotEmpty()) {
            list.selectedIndex = 0
        }
    }
}
