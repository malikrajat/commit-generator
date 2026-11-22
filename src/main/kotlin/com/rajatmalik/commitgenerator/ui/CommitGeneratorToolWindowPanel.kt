package com.rajatmalik.commitgenerator.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.rajatmalik.commitgenerator.core.Product
import com.rajatmalik.commitgenerator.core.domain.GenerationMode
import com.rajatmalik.commitgenerator.core.domain.OverwriteDraft
import com.rajatmalik.commitgenerator.services.CommitGeneratorProjectService
import com.rajatmalik.commitgenerator.services.GenerationController
import com.rajatmalik.commitgenerator.services.commitGeneratorProject
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants

class CommitGeneratorToolWindowPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val service: CommitGeneratorProjectService = commitGeneratorProject(project)
    private val controller: GenerationController = service.generationController
    private val settings = service.app.settings

    private val providerValue = JBLabel()
    private val repositoryValue = JBLabel()
    private val statusValue = JBLabel()
    private val modeCombo = enumCombo(GenerationMode.entries) { it.title }
    private val staged = JBCheckBox("Staged changes")
    private val unstaged = JBCheckBox("Unstaged changes")
    private val untracked = JBCheckBox("Untracked files")
    private val conventional = JBCheckBox("Conventional Commits")
    private val includeBody = JBCheckBox("Allow a commit body")
    private val localOnly = JBCheckBox("Local-only mode")
    private val budgetField = JBTextField()
    private val maxFilesField = JBTextField()
    private val timeoutField = JBTextField()
    private val languageField = JBTextField()
    private val messageArea = JBTextArea()
    private val statsLabel = JBLabel()
    private val warningsArea = JBTextArea()
    private val generateButton = JButton("Generate commit message")
    private val generateStagedButton = JButton("Staged only")
    private val cancelButton = JButton("Cancel")
    private val regenerateButton = JButton("Regenerate")
    private val undoButton = JButton("Undo")
    private val previewButton = JButton("Preview payload")
    private val copyButton = JButton("Copy")
    private val insertButton = JButton("Insert")
    private val detailsButton = JButton("Details")
    private var refreshing = false
    private var disposed = false

    private val uiListener: () -> Unit = {
        ApplicationManager.getApplication().invokeLater {
            if (!disposed) {
                refresh()
            }
        }
    }
    private val settingsListener: () -> Unit = uiListener

    init {
        buildUi()
        service.addUiListener(uiListener)
        settings.addListener(settingsListener)
        refresh()
    }

    override fun dispose() {
        disposed = true
        service.removeUiListener(uiListener)
        settings.removeListener(settingsListener)
    }

    private fun buildUi() {
        add(header(), BorderLayout.NORTH)
        add(JBScrollPane(content()).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = 16
        }, BorderLayout.CENTER)
    }

    private fun header(): JPanel {
        val title = JBLabel("<html><span style='font-size:16px;font-weight:600'>${Product.NAME}</span></html>")
        val subtitle = JBLabel(
            "<html>AI commit messages from your changes. Review, then commit yourself. " +
                "Commit Generator never stages or commits.</html>",
        )
        subtitle.foreground = JBColor.GRAY
        val heading = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(title)
            add(Box.createVerticalStrut(3))
            add(subtitle)
        }
        val inner = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12, 14, 10, 14)
            add(heading, BorderLayout.CENTER)
        }
        val outer = JPanel(BorderLayout()).apply {
            add(inner, BorderLayout.CENTER)
            add(JSeparator(), BorderLayout.SOUTH)
        }
        return outer
    }

    private fun content(): JPanel {
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(10, 12, 16, 12)
            isOpaque = false
        }

        content.add(card(
            "Provider",
            "Choose the profile used for the next generation.",
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(providerValue)
                add(Box.createVerticalStrut(6))
                add(buttonRow(
                    "Switch" to { controller.selectProfile() },
                    "Add" to { controller.addProfile() },
                    "Manage" to { controller.manageProfiles() },
                    "Test" to { controller.testConnection() },
                    "Validate" to { controller.validateProfiles() },
                    "Set key" to { controller.setApiKey() },
                ))
            },
        ))
        content.add(Box.createVerticalStrut(10))

        content.add(card(
            "Repository",
            "Read-only Git access. The selected change sources are analysed.",
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(repositoryValue)
            },
        ))
        content.add(Box.createVerticalStrut(10))

        content.add(card(
            "What to analyse",
            "Only selected changes are read. Nothing is sent until you generate.",
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(staged)
                add(unstaged)
                add(untracked)
                add(Box.createVerticalStrut(4))
                add(buttonRow(
                    "Switch repo" to { controller.selectRepository() },
                    "Refresh repository" to { refresh() },
                    "Allow sensitive once" to { controller.allowSensitiveOnce() },
                ))
            },
        ))
        content.add(Box.createVerticalStrut(10))

        content.add(card(
            "Message",
            "Depth, limits and formatting for the generated message.",
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(labelled("Mode", modeCombo))
                add(labelled("Max diff tokens", budgetField))
                add(labelled("Max files", maxFilesField))
                add(labelled("Timeout (ms)", timeoutField))
                add(labelled("Language", languageField))
                add(Box.createVerticalStrut(4))
                add(conventional)
                add(includeBody)
            },
        ))
        content.add(Box.createVerticalStrut(10))

        content.add(card(
            "Privacy",
            "Control what may leave this machine.",
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(localOnly)
                add(Box.createVerticalStrut(4))
                add(buttonRow(
                    "Revoke consent" to { controller.revokeConsent() },
                    "Diagnostics" to { controller.showDiagnostics() },
                ))
            },
        ))
        content.add(Box.createVerticalStrut(12))

        generateButton.addActionListener {
            persistMessageFields()
            controller.generate(com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT)
        }
        generateStagedButton.addActionListener {
            persistMessageFields()
            controller.generate(com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT, stagedOnly = true)
        }
        cancelButton.addActionListener { controller.cancel() }
        regenerateButton.addActionListener { controller.regenerate() }
        undoButton.addActionListener { controller.undoLast() }
        previewButton.addActionListener { controller.showPreview() }
        copyButton.addActionListener { controller.copyLast() }
        insertButton.addActionListener { controller.insertLast() }
        detailsButton.addActionListener { controller.showLastDetails() }

        val generateRow = JPanel(BorderLayout(6, 0)).apply {
            isOpaque = false
            add(generateButton, BorderLayout.CENTER)
            add(generateStagedButton, BorderLayout.EAST)
        }
        content.add(generateRow)
        content.add(Box.createVerticalStrut(6))
        val actionRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { isOpaque = false }
        actionRow.add(cancelButton)
        actionRow.add(regenerateButton)
        actionRow.add(undoButton)
        actionRow.add(previewButton)
        actionRow.add(JButton("Settings").apply { addActionListener { controller.openSettings() } })
        content.add(actionRow)
        content.add(Box.createVerticalStrut(12))

        messageArea.apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            rows = 8
            font = JBUI.Fonts.create("JetBrains Mono", 12)
            border = JBUI.Borders.empty(6)
        }
        warningsArea.apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            rows = 3
            foreground = JBColor(0xB26B00, 0xE0A030)
            border = JBUI.Borders.empty(2, 6, 4, 6)
            isOpaque = false
        }
        statsLabel.foreground = JBColor.GRAY
        statusValue.foreground = JBColor.GRAY

        val resultPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JBScrollPane(messageArea).apply { preferredSize = Dimension(520, 190) })
            add(Box.createVerticalStrut(6))
            add(statsLabel)
            add(warningsArea)
            add(Box.createVerticalStrut(6))
            val resultActions = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { isOpaque = false }
            resultActions.add(copyButton)
            resultActions.add(insertButton)
            resultActions.add(detailsButton)
            add(resultActions)
            add(Box.createVerticalStrut(4))
            add(statusValue)
        }
        content.add(card("Latest message", "Review and edit before committing.", resultPanel))
        content.add(Box.createVerticalStrut(10))
        content.add(JBLabel(
            "<html><span style='color:gray'>Keys stay in the OS keychain. No telemetry. " +
                "No automatic commits.</span></html>",
        ).apply { horizontalAlignment = SwingConstants.LEFT })

        staged.addActionListener { persistSelection() }
        unstaged.addActionListener { persistSelection() }
        untracked.addActionListener { persistSelection() }
        conventional.addActionListener {
            if (!refreshing) updateSettings { it.conventionalEnabled = conventional.isSelected }
        }
        includeBody.addActionListener {
            if (!refreshing) updateSettings { it.includeBody = includeBody.isSelected }
        }
        localOnly.addActionListener {
            if (!refreshing) updateSettings { it.localOnly = localOnly.isSelected }
        }
        modeCombo.addActionListener {
            if (!refreshing) {
                val mode = modeCombo.item ?: GenerationMode.SMART
                updateSettings { it.mode = mode.key }
            }
        }
        fieldCommit(budgetField) {
            it.maxDiffTokens = budgetField.text.trim().toIntOrNull() ?: 12_000
        }
        fieldCommit(maxFilesField) {
            it.maxFiles = maxFilesField.text.trim().toIntOrNull() ?: 200
        }
        fieldCommit(timeoutField) {
            it.providerTimeoutMs = timeoutField.text.trim().toIntOrNull() ?: 120_000
        }
        fieldCommit(languageField) {
            it.outputLanguage = languageField.text.trim().ifEmpty { "en" }
        }

        return content
    }

    private fun card(title: String, description: String, body: JPanel): JPanel {
        val head = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(JBLabel("<html><b>$title</b></html>"))
            add(JBLabel("<html><span style='color:gray;font-size:11px'>$description</span></html>"))
        }
        val inner = JPanel(BorderLayout(0, 6)).apply {
            border = JBUI.Borders.empty(9, 10, 10, 10)
            add(head, BorderLayout.NORTH)
            add(body, BorderLayout.CENTER)
        }
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.customLine(JBColor.border(), 1)
            add(inner, BorderLayout.CENTER)
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
            alignmentX = LEFT_ALIGNMENT
        }
    }

    private fun labelled(label: String, field: JBTextField): JPanel {
        field.columns = 18
        return JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(4)
            add(JBLabel(label), BorderLayout.WEST)
            add(field, BorderLayout.CENTER)
            maximumSize = Dimension(Int.MAX_VALUE, 30)
        }
    }

    private fun labelled(label: String, combo: ComboBox<*>): JPanel = JPanel(BorderLayout(8, 0)).apply {
        isOpaque = false
        border = JBUI.Borders.emptyBottom(4)
        add(JBLabel(label), BorderLayout.WEST)
        add(combo, BorderLayout.CENTER)
        maximumSize = Dimension(Int.MAX_VALUE, 30)
    }

    private fun buttonRow(vararg buttons: Pair<String, () -> Unit>): JPanel {
        val row = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { isOpaque = false }
        for ((text, action) in buttons) {
            row.add(JButton(text).apply {
                addActionListener { action() }
            })
        }
        return row
    }

    private fun fieldCommit(field: JBTextField, apply: (CommitGeneratorSettingsPatch) -> Unit) {
        val commit = {
            if (!refreshing) {
                updateSettings { apply(it) }
            }
        }
        field.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) = commit()
        })
        field.addActionListener { commit() }
    }

    private fun persistSelection() {
        if (refreshing) {
            return
        }
        updateSettings {
            it.includeStaged = staged.isSelected
            it.includeUnstaged = unstaged.isSelected
            it.includeUntracked = untracked.isSelected
        }
    }

    private fun persistMessageFields() {
        if (refreshing) {
            return
        }
        updateSettings {
            it.maxDiffTokens = budgetField.text.trim().toIntOrNull() ?: 12_000
            it.maxFiles = maxFilesField.text.trim().toIntOrNull() ?: 200
            it.providerTimeoutMs = timeoutField.text.trim().toIntOrNull() ?: 120_000
            it.outputLanguage = languageField.text.trim().ifEmpty { "en" }
        }
    }

    private fun updateSettings(block: (CommitGeneratorSettingsPatch) -> Unit) {
        settings.update { state ->
            block(CommitGeneratorSettingsPatch(state))
        }
    }

    private fun refresh() {
        if (disposed) {
            return
        }
        refreshing = true
        try {
            val config = settings.read()
            val active = service.activeProfile()
            providerValue.text = if (active == null) {
                if (service.profiles().isEmpty()) {
                    "No provider configured. Add one to begin."
                } else {
                    "No active provider. Choose one to begin."
                }
            } else {
                "${active.label}  ·  ${active.provider.label}  ·  ${active.model}"
            }
            repositoryValue.text = service.repositoryLabel()
            modeCombo.item = config.mode
            staged.isSelected = config.context.includeStaged
            unstaged.isSelected = config.context.includeUnstaged
            untracked.isSelected = config.context.includeUntracked
            conventional.isSelected = config.conventional.enabled
            includeBody.isSelected = config.output.includeBody
            localOnly.isSelected = config.privacy.localOnly
            budgetField.text = config.context.maxDiffTokens.toString()
            maxFilesField.text = config.context.maxFiles.toString()
            timeoutField.text = config.provider.timeoutMs.toString()
            languageField.text = config.output.language

            val generating = service.isGenerating
            generateButton.isEnabled = !generating && active != null
            generateStagedButton.isEnabled = !generating && active != null
            cancelButton.isEnabled = generating
            regenerateButton.isEnabled = !generating && service.lastResult != null
            undoButton.isEnabled = !generating && service.lastResult?.writtenToCommitPanel == true
            previewButton.isEnabled = !generating && active != null
            copyButton.isEnabled = service.lastResult != null || service.pendingMessage != null
            insertButton.isEnabled = service.lastResult != null || service.pendingMessage != null

            val outcome = service.lastResult?.outcome
            val pending = service.pendingMessage
            messageArea.text = outcome?.message?.raw ?: pending.orEmpty()
            if (messageArea.text.isBlank()) {
                statsLabel.text = "No message generated yet."
                warningsArea.text = ""
                statusValue.text = if (generating) "Generating…" else ""
            } else {
                val prepared = service.lastResult?.prepared
                statsLabel.text = if (outcome != null && prepared != null) {
                    "${prepared.changeSet.counts.files} file(s) · ${prepared.mode.id.title} · " +
                        "~${prepared.plan.estimatedTokens} tokens · ${outcome.durationMs} ms" +
                        if (prepared.plan.omitted.isNotEmpty()) " · ${prepared.plan.omitted.size} omitted" else ""
                } else {
                    "Generated message"
                }
                val notes = buildList {
                    outcome?.message?.warnings?.forEach { add("Warning: $it") }
                    outcome?.summary?.limitations?.forEach { add("Limitation: $it") }
                }
                warningsArea.text = notes.joinToString("\n")
                statusValue.text = when {
                    generating -> "Generating…"
                    service.lastResult?.writtenToCommitPanel == true -> "Inserted into the commit message field."
                    else -> "Ready in Commit Generator. Open the Commit tool window and choose Insert, or copy it."
                }
            }
        } finally {
            refreshing = false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> enumCombo(values: List<T>, label: (T) -> String): ComboBox<T> {
        val model = javax.swing.DefaultComboBoxModel<T>()
        values.forEach { model.addElement(it) }
        val combo = ComboBox(model)
        combo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
            ): java.awt.Component = super.getListCellRendererComponent(
                list,
                if (value == null) "" else label(value as T),
                index,
                isSelected,
                cellHasFocus,
            )
        }
        return combo
    }
}

/**
 * Small typed adapter so UI controls can mutate persisted state without
 * exposing the whole state class to every listener.
 */
private class CommitGeneratorSettingsPatch(
    private val state: com.rajatmalik.commitgenerator.config.CommitGeneratorSettings.SettingsState,
) {
    var mode: String
        get() = state.mode
        set(value) { state.mode = value }
    var includeStaged: Boolean
        get() = state.includeStaged
        set(value) { state.includeStaged = value }
    var includeUnstaged: Boolean
        get() = state.includeUnstaged
        set(value) { state.includeUnstaged = value }
    var includeUntracked: Boolean
        get() = state.includeUntracked
        set(value) { state.includeUntracked = value }
    var conventionalEnabled: Boolean
        get() = state.conventionalEnabled
        set(value) { state.conventionalEnabled = value }
    var includeBody: Boolean
        get() = state.includeBody
        set(value) { state.includeBody = value }
    var localOnly: Boolean
        get() = state.localOnly
        set(value) { state.localOnly = value }
    var maxDiffTokens: Int
        get() = state.maxDiffTokens
        set(value) { state.maxDiffTokens = value }
    var maxFiles: Int
        get() = state.maxFiles
        set(value) { state.maxFiles = value }
    var providerTimeoutMs: Int
        get() = state.providerTimeoutMs
        set(value) { state.providerTimeoutMs = value }
    var outputLanguage: String
        get() = state.outputLanguage
        set(value) { state.outputLanguage = value }
}
