package com.rajatmalik.commitgenerator.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.rajatmalik.commitgenerator.core.Product
import com.rajatmalik.commitgenerator.core.domain.GenerationMode
import com.rajatmalik.commitgenerator.services.CommitGeneratorProjectService
import com.rajatmalik.commitgenerator.services.GenerationController
import com.rajatmalik.commitgenerator.services.commitGeneratorProject
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Rectangle
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.Scrollable
import javax.swing.SwingConstants

/**
 * Main tool window.
 *
 * The layout deliberately uses a single full-width grid. Every section is
 * stretched to the viewport width, every label and checkbox is left aligned,
 * and action rows are split into small groups so controls cannot escape the
 * window at narrow widths. The content panel tracks the viewport width, which
 * keeps the tool window usable in both the narrow right sidebar and a wide
 * detached window.
 */
class CommitGeneratorToolWindowPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val service: CommitGeneratorProjectService = commitGeneratorProject(project)
    private val controller: GenerationController = service.generationController
    private val settings = service.app.settings

    private val providerValue = valueLabel(bold = true)
    private val providerDetail = hintLabel()
    private val repositoryValue = valueLabel(bold = false)
    private val statusValue = hintLabel()
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
    private val statsLabel = hintLabel()
    private val warningsArea = JBTextArea()
    private val generateButton = JButton("Generate commit message")
    private val generateStagedButton = JButton("Staged only")
    private val cancelButton = JButton("Cancel")
    private val regenerateButton = JButton("Regenerate")
    private val undoButton = JButton("Undo")
    private val previewButton = JButton("Preview")
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
        add(
            JBScrollPane(content()).apply {
                border = JBUI.Borders.empty()
                horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                verticalScrollBar.unitIncrement = 16
                viewport.isOpaque = false
                isOpaque = false
            },
            BorderLayout.CENTER,
        )
    }

    private fun header(): JPanel {
        val title = JBLabel(Product.NAME).apply {
            font = JBUI.Fonts.label().deriveFont(Font.BOLD, 17f)
            horizontalAlignment = SwingConstants.LEFT
            minimumSize = Dimension(0, 0)
            toolTipText = Product.DESCRIPTION
        }
        val subtitle = hintLabel("AI commit messages from your checked changes.")
        val subtitle2 = hintLabel("Review, then commit. Nothing is staged automatically.")
        val text = StackPanel()
            .row(title)
            .row(subtitle, 3)
            .row(subtitle2, 1)

        val icon = productIcon()
        val heading = JPanel(BorderLayout(10, 0)).apply {
            isOpaque = false
            if (icon != null) {
                add(JBLabel(icon).apply { verticalAlignment = SwingConstants.TOP }, BorderLayout.WEST)
            }
            add(text, BorderLayout.CENTER)
        }
        val inner = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12, 14, 11, 14)
            isOpaque = false
            add(heading, BorderLayout.CENTER)
        }
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(inner, BorderLayout.CENTER)
            add(JSeparator(), BorderLayout.SOUTH)
        }
    }

    private fun content(): JComponent {
        val content = ContentPanel()
        content.border = JBUI.Borders.empty(12, 12, 18, 12)

        content.addCard(providerCard(), 0)
        content.addCard(repositoryCard(), 10)
        content.addCard(changesCard(), 10)
        content.addCard(messageCard(), 10)
        content.addCard(privacyCard(), 10)

        generateButton.apply {
            font = JBUI.Fonts.label().deriveFont(Font.BOLD, 13f)
            toolTipText = "Analyse the files checked in the Commit panel, or the selected change sources."
            addActionListener {
                persistMessageFields()
                controller.generate(DataContext.EMPTY_CONTEXT)
            }
        }
        content.row(generateButton, 14)
        content.row(
            buttonRow(
                generateStagedButton,
                cancelButton,
                undoButton,
            ),
            7,
        )
        content.row(
            buttonRow(
                regenerateButton,
                previewButton,
            ),
            6,
        )
        content.row(buttonRow(settingsButton()), 6)

        messageArea.apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            rows = 7
            font = JBUI.Fonts.create("JetBrains Mono", 12)
            border = JBUI.Borders.empty(8)
        }
        warningsArea.apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            rows = 2
            foreground = JBColor(0xB26B00, 0xE0A030)
            isOpaque = false
            border = JBUI.Borders.empty()
            isVisible = false
        }
        statsLabel.foreground = JBColor.GRAY
        statusValue.foreground = JBColor.GRAY

        val resultActions = buttonRow(copyButton, insertButton, detailsButton)
        val resultBody = StackPanel()
            .row(
                JBScrollPane(messageArea).apply {
                    preferredSize = Dimension(400, 170)
                    minimumSize = Dimension(0, 120)
                    border = BorderFactory.createLineBorder(JBColor.border())
                    horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                },
                8,
            )
            .row(statsLabel, 6)
            .row(warningsArea, 4)
            .row(resultActions, 6)
            .row(statusValue, 6)
        content.addCard(card("Latest message", "Review before inserting into the commit field.", resultBody), 12)
        content.row(
            StackPanel()
                .row(hintLabel("Keys stay in the OS keychain. No telemetry."))
                .row(hintLabel("Nothing is staged or committed automatically."), 2),
            12,
        )

        generateStagedButton.apply {
            toolTipText = "Generate from staged changes only, ignoring the Commit panel selection."
            addActionListener {
                persistMessageFields()
                controller.generate(DataContext.EMPTY_CONTEXT, stagedOnly = true)
            }
        }
        cancelButton.addActionListener { controller.cancel() }
        regenerateButton.addActionListener { controller.regenerate() }
        undoButton.addActionListener { controller.undoLast() }
        previewButton.apply {
            toolTipText = "Preview the redacted payload that would be sent."
            addActionListener { controller.showPreview() }
        }
        copyButton.addActionListener { controller.copyLast() }
        insertButton.addActionListener { controller.insertLast() }
        detailsButton.addActionListener { controller.showLastDetails() }

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

    private fun providerCard(): JPanel {
        val body = StackPanel()
            .row(providerValue)
            .row(providerDetail, 2)
            .row(
                buttonRow(
                    actionButton("Switch") { controller.selectProfile() },
                    actionButton("Add") { controller.addProfile() },
                    actionButton("Manage") { controller.manageProfiles() },
                ),
                9,
            )
            .row(
                buttonRow(
                    actionButton("Test") { controller.testConnection() },
                    actionButton("Validate") { controller.validateProfiles() },
                    actionButton("Set key") { controller.setApiKey() },
                ),
                5,
            )
        return card("Provider", "Profile used for the next generation.", body)
    }

    private fun repositoryCard(): JPanel {
        val body = StackPanel()
            .row(repositoryValue)
            .row(
                buttonRow(
                    actionButton("Switch repo") { controller.selectRepository() },
                    actionButton("Refresh") { refresh() },
                ),
                9,
            )
        return card("Repository", "Read-only Git access.", body)
    }

    private fun changesCard(): JPanel {
        val body = StackPanel()
            .row(initCheckBox(staged, "Include changes staged in the Git index."))
            .row(initCheckBox(unstaged, "Include working-tree changes, including conflicts."), 4)
            .row(initCheckBox(untracked, "Include files that Git does not track yet."), 4)
        return card("Changes to include", "Only the selected sources are analysed.", body)
    }

    private fun messageCard(): JPanel {
        val form = FormPanel()
            .field("Mode", modeCombo)
            .field("Max diff tokens", budgetField)
            .field("Max files", maxFilesField)
            .field("Timeout (ms)", timeoutField)
            .field("Language", languageField)
            .check(conventional, 9)
            .check(includeBody, 4)
        return card("Message options", "Depth, limits, and formatting.", form)
    }

    private fun privacyCard(): JPanel {
        val body = StackPanel()
            .row(initCheckBox(localOnly, "Reject every non-loopback provider endpoint."))
            .row(
                buttonRow(
                    actionButton("Allow sensitive once") { controller.allowSensitiveOnce() },
                    actionButton("Diagnostics") { controller.showDiagnostics() },
                ),
                9,
            )
            .row(buttonRow(actionButton("Revoke consent") { controller.revokeConsent() }), 5)
        return card("Privacy", "Control what may leave this machine.", body)
    }

    private fun card(title: String, description: String, body: JComponent): JPanel {
        val head = StackPanel()
            .row(JBLabel(title).apply {
                font = JBUI.Fonts.label().deriveFont(Font.BOLD, 13f)
                horizontalAlignment = SwingConstants.LEFT
                minimumSize = Dimension(0, 0)
            })
            .row(hintLabel(description), 2)
        val inner = JPanel(BorderLayout(0, 8)).apply {
            border = JBUI.Borders.empty(10, 12, 12, 12)
            isOpaque = false
            add(head, BorderLayout.NORTH)
            add(body, BorderLayout.CENTER)
        }
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.customLine(JBColor.border(), 1)
            add(inner, BorderLayout.CENTER)
        }
    }

    private fun initCheckBox(box: JBCheckBox, tooltip: String): JBCheckBox = box.apply {
        horizontalAlignment = SwingConstants.LEFT
        toolTipText = tooltip
    }

    private fun actionButton(text: String, action: () -> Unit): JButton = JButton(text).apply {
        addActionListener { action() }
    }

    private fun settingsButton(): JButton = actionButton("Settings") { controller.openSettings() }

    private fun buttonRow(vararg buttons: JButton): JPanel {
        val row = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply { isOpaque = false }
        buttons.forEach(row::add)
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
            if (active == null) {
                providerValue.text = if (service.profiles().isEmpty()) {
                    "No provider configured"
                } else {
                    "No active provider"
                }
                providerDetail.text = if (service.profiles().isEmpty()) {
                    "Add a profile to begin."
                } else {
                    "Choose a profile to begin."
                }
            } else {
                providerValue.text = active.label
                providerDetail.text = "${active.provider.label} / ${active.model}"
            }
            providerValue.toolTipText = providerValue.text
            providerDetail.toolTipText = providerDetail.text
            repositoryValue.text = service.repositoryLabel()
            repositoryValue.toolTipText = repositoryValue.text
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
                warningsArea.isVisible = false
                statusValue.text = if (generating) "Generating..." else ""
            } else {
                val prepared = service.lastResult?.prepared
                statsLabel.text = if (outcome != null && prepared != null) {
                    "${prepared.changeSet.counts.files} file(s) \u00B7 ${prepared.mode.id.title} \u00B7 " +
                        "~${prepared.plan.estimatedTokens} tokens \u00B7 ${outcome.durationMs} ms" +
                        if (prepared.plan.omitted.isNotEmpty()) " \u00B7 ${prepared.plan.omitted.size} omitted" else ""
                } else {
                    "Generated message"
                }
                val notes = buildList {
                    outcome?.message?.warnings?.forEach { add("Warning: $it") }
                    outcome?.summary?.limitations?.forEach { add("Limitation: $it") }
                }
                warningsArea.text = notes.joinToString("\n")
                warningsArea.isVisible = notes.isNotEmpty()
                statusValue.text = when {
                    generating -> "Generating..."
                    service.lastResult?.writtenToCommitPanel == true -> "Inserted into the commit message field."
                    else -> "Ready. Open the Commit panel and choose Insert, or copy the message."
                }
            }
        } finally {
            refreshing = false
            revalidate()
            repaint()
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

    private fun productIcon(): Icon? = runCatching {
        IconLoader.getIcon("/icons/commit-generator.svg", javaClass)
    }.getOrNull()

    private fun valueLabel(bold: Boolean): JBLabel = JBLabel().apply {
        horizontalAlignment = SwingConstants.LEFT
        minimumSize = Dimension(0, 0)
        if (bold) {
            font = JBUI.Fonts.label().deriveFont(Font.BOLD, 13f)
        }
    }

    private fun hintLabel(text: String = ""): JBLabel = JBLabel(text).apply {
        horizontalAlignment = SwingConstants.LEFT
        foreground = JBColor.GRAY
        font = JBUI.Fonts.label().deriveFont(11f)
        minimumSize = Dimension(0, 0)
    }
}

/**
 * Vertical stack that always stretches its children to the full width. This
 * replaces BoxLayout, whose per-component alignment is the reason checkboxes
 * and labels drifted to the middle or right of the old tool window.
 */
private class StackPanel : JPanel(GridBagLayout()) {
    private var nextRow = 0

    init {
        isOpaque = false
    }

    fun row(component: JComponent, topGap: Int = 0): StackPanel {
        val constraints = GridBagConstraints().apply {
            gridx = 0
            gridy = nextRow++
            weightx = 1.0
            weighty = 0.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTHWEST
            insets = Insets(JBUI.scale(topGap), 0, 0, 0)
        }
        add(component, constraints)
        return this
    }
}

/**
 * Two-column form with a stable label column and a field column that absorbs
 * all remaining width. Labels never move the fields around, and the fields
 * cannot grow past the card boundary.
 */
private class FormPanel : JPanel(GridBagLayout()) {
    private var nextRow = 0

    init {
        isOpaque = false
    }

    fun field(label: String, component: JComponent): FormPanel {
        val caption = JBLabel(label).apply {
            horizontalAlignment = SwingConstants.LEFT
            minimumSize = Dimension(0, 0)
            labelFor = component
        }
        val captionConstraints = GridBagConstraints().apply {
            gridx = 0
            gridy = nextRow
            weightx = 0.0
            weighty = 0.0
            fill = GridBagConstraints.NONE
            anchor = GridBagConstraints.WEST
            insets = Insets(JBUI.scale(7), 0, 0, JBUI.scale(8))
        }
        val fieldConstraints = GridBagConstraints().apply {
            gridx = 1
            gridy = nextRow++
            weightx = 1.0
            weighty = 0.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = Insets(JBUI.scale(7), 0, 0, 0)
        }
        add(caption, captionConstraints)
        add(component, fieldConstraints)
        return this
    }

    fun check(component: JComponent, topGap: Int): FormPanel {
        val constraints = GridBagConstraints().apply {
            gridx = 0
            gridy = nextRow++
            gridwidth = 2
            weightx = 1.0
            weighty = 0.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = Insets(JBUI.scale(topGap), 0, 0, 0)
        }
        add(component, constraints)
        return this
    }
}

/**
 * Scrollable content that follows the viewport width and only scrolls
 * vertically. Without this, Swing keeps the content's preferred width and the
 * horizontal scrollbar (or clipped controls) appears in narrow tool windows.
 */
private class ContentPanel : JPanel(GridBagLayout()), Scrollable {
    private var nextRow = 0

    init {
        isOpaque = false
    }

    fun row(component: JComponent, topGap: Int = 0): ContentPanel {
        val constraints = GridBagConstraints().apply {
            gridx = 0
            gridy = nextRow++
            weightx = 1.0
            weighty = 0.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTHWEST
            insets = Insets(JBUI.scale(topGap), 0, 0, 0)
        }
        add(component, constraints)
        return this
    }

    fun addCard(component: JComponent, topGap: Int): ContentPanel = row(component, topGap)

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = 16

    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        if (orientation == SwingConstants.VERTICAL) visibleRect.height else visibleRect.width

    override fun getScrollableTracksViewportWidth(): Boolean = true

    override fun getScrollableTracksViewportHeight(): Boolean = false
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
