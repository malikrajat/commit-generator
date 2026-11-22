package com.rajatmalik.commitgenerator.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.rajatmalik.commitgenerator.core.Product
import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.ProviderProfile
import com.rajatmalik.commitgenerator.services.CommitGeneratorProjectService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

class ManageProfilesDialog(
    private val project: Project,
    private val state: CommitGeneratorProjectService,
) : DialogWrapper(project, true) {

    private val list = JBList<ProviderProfile>(CollectionListModel<ProviderProfile>())
    private val addButton = JButton("Add")
    private val editButton = JButton("Edit")
    private val removeButton = JButton("Remove")
    private val activateButton = JButton("Set Active")
    private val keyButton = JButton("Set Key")
    private val clearKeyButton = JButton("Clear Key")
    private val testButton = JButton("Test")
    private val modelButton = JButton("Select Model")
    private val closeButton = JButton("Close")

    init {
        title = "${Product.NAME}: Provider profiles"
        setSize(860, 520)
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): java.awt.Component {
                val profile = value as? ProviderProfile
                val text = if (profile == null) "" else buildString {
                    append(profile.label)
                    append("  —  ")
                    append(profile.provider.label)
                    append(" / ")
                    append(profile.model)
                    if (profile.id == state.activeProfile()?.id) {
                        append("   [active]")
                    }
                }
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus)
            }
        }

        addButton.addActionListener { addProfile() }
        editButton.addActionListener { editProfile() }
        removeButton.addActionListener { removeProfile() }
        activateButton.addActionListener { activateProfile() }
        keyButton.addActionListener { setKey() }
        clearKeyButton.addActionListener { clearKey() }
        testButton.addActionListener { testConnection() }
        modelButton.addActionListener { selectModel() }
        closeButton.addActionListener { close(OK_EXIT_CODE) }

        list.addListSelectionListener { updateButtons() }
        init()
        refresh()
    }

    override fun createCenterPanel(): JComponent {
        val buttons = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.emptyLeft(12)
            add(addButton)
            add(javax.swing.Box.createVerticalStrut(4))
            add(editButton)
            add(javax.swing.Box.createVerticalStrut(4))
            add(removeButton)
            add(javax.swing.Box.createVerticalStrut(12))
            add(activateButton)
            add(javax.swing.Box.createVerticalStrut(4))
            add(keyButton)
            add(javax.swing.Box.createVerticalStrut(4))
            add(clearKeyButton)
            add(javax.swing.Box.createVerticalStrut(12))
            add(testButton)
            add(javax.swing.Box.createVerticalStrut(4))
            add(modelButton)
            add(javax.swing.Box.createVerticalGlue())
            add(closeButton)
        }
        buttons.components.filterIsInstance<JButton>().forEach { button ->
            button.alignmentX = java.awt.Component.LEFT_ALIGNMENT
            button.maximumSize = Dimension(JBUI.scale(150), button.preferredSize.height)
        }
        val header = JPanel(BorderLayout(0, 3)).apply {
            isOpaque = false
            add(JBLabel("Provider profiles"), BorderLayout.NORTH)
            add(
                JBLabel("Choose a profile, then edit it or set its OS keychain credential."),
                BorderLayout.CENTER,
            )
        }
        val footer = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(JBLabel("Credentials live in the OS keychain. Commit Generator never stages or commits."))
        }
        val center = JPanel(BorderLayout(0, 8)).apply {
            border = JBUI.Borders.empty(8)
            add(header, BorderLayout.NORTH)
            add(JBScrollPane(list), BorderLayout.CENTER)
            add(footer, BorderLayout.SOUTH)
            preferredSize = Dimension(760, 460)
        }
        return JPanel(BorderLayout()).apply {
            add(center, BorderLayout.CENTER)
            add(buttons, BorderLayout.EAST)
        }
    }

    private fun selectedProfile(): ProviderProfile? = list.selectedValue

    private fun refresh() {
        val selectedId = selectedProfile()?.id
        val profiles = state.profiles()
        list.model = CollectionListModel<ProviderProfile>(profiles)
        val index = profiles.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }
            ?: profiles.indexOfFirst { it.id == state.activeProfile()?.id }
        if (index >= 0) {
            list.selectedIndex = index
        } else if (profiles.isNotEmpty()) {
            list.selectedIndex = 0
        }
        list.repaint()
        updateButtons()
    }

    private fun updateButtons() {
        val hasSelection = selectedProfile() != null
        editButton.isEnabled = hasSelection
        removeButton.isEnabled = hasSelection
        activateButton.isEnabled = hasSelection
        keyButton.isEnabled = hasSelection
        clearKeyButton.isEnabled = hasSelection
        testButton.isEnabled = hasSelection
        modelButton.isEnabled = hasSelection
    }

    private fun addProfile() {
        val dialog = ProfileDialog(project, null)
        if (!dialog.showAndGet()) {
            return
        }
        val profile = dialog.toProfile() ?: return
        when (val result = state.saveProfile(profile)) {
            is CwResult.Ok -> {
                state.selectProfile(result.value.id)
                dialog.newSecret()?.let { state.setSecret(result.value, it) }
                refresh()
            }

            is CwResult.Err -> Messages.showErrorDialog(project, result.error.message, Product.NAME)
        }
    }

    private fun editProfile() {
        val profile = selectedProfile() ?: return
        val dialog = ProfileDialog(project, profile)
        if (!dialog.showAndGet()) {
            return
        }
        val updated = dialog.toProfile() ?: return
        when (val result = state.saveProfile(updated)) {
            is CwResult.Ok -> {
                dialog.newSecret()?.let { state.setSecret(result.value, it) }
                refresh()
            }

            is CwResult.Err -> Messages.showErrorDialog(project, result.error.message, Product.NAME)
        }
    }

    private fun removeProfile() {
        val profile = selectedProfile() ?: return
        val answer = Messages.showYesNoDialog(
            project,
            "Remove provider profile \"${profile.label}\" and its stored credential?",
            "${Product.NAME}: Remove profile",
            Messages.getWarningIcon(),
        )
        if (answer == Messages.YES) {
            state.removeProfile(profile.id)
            refresh()
        }
    }

    private fun activateProfile() {
        val profile = selectedProfile() ?: return
        state.selectProfile(profile.id)
        refresh()
    }

    private fun setKey() {
        val profile = selectedProfile() ?: return
        val value = Messages.showPasswordDialog(
            project,
            "Enter the credential for \"${profile.label}\". It is stored in the OS keychain.",
            "${Product.NAME}: Set credential",
            Messages.getQuestionIcon(),
        )
        if (!value.isNullOrEmpty()) {
            state.setSecret(profile, value)
            refresh()
        }
    }

    private fun clearKey() {
        val profile = selectedProfile() ?: return
        if (Messages.showYesNoDialog(
                project,
                "Remove the stored credential for \"${profile.label}\"?",
                "${Product.NAME}: Clear credential",
                Messages.getQuestionIcon(),
            ) == Messages.YES
        ) {
            state.clearSecret(profile)
            refresh()
        }
    }

    private fun testConnection() {
        val profile = selectedProfile() ?: return
        state.selectProfile(profile.id)
        close(OK_EXIT_CODE)
        state.generationController.testConnection()
    }

    private fun selectModel() {
        val profile = selectedProfile() ?: return
        state.selectProfile(profile.id)
        close(OK_EXIT_CODE)
        state.generationController.selectModel()
    }
}
