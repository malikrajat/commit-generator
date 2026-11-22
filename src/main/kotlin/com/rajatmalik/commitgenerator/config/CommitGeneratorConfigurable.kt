package com.rajatmalik.commitgenerator.config

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.rajatmalik.commitgenerator.core.Product
import com.rajatmalik.commitgenerator.core.domain.GenerationMode
import com.rajatmalik.commitgenerator.core.domain.LogLevel
import com.rajatmalik.commitgenerator.core.domain.OutputAttribution
import com.rajatmalik.commitgenerator.core.domain.OverwriteDraft
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class CommitGeneratorConfigurable : Configurable {
    private var panel: CommitGeneratorSettingsPanel? = null

    override fun getDisplayName(): String = Product.NAME

    override fun createComponent(): JComponent {
        val created = CommitGeneratorSettingsPanel()
        panel = created
        created.reset()
        return created
    }

    override fun isModified(): Boolean = panel?.isModified() ?: false

    override fun apply() {
        panel?.apply()
    }

    override fun reset() {
        panel?.reset()
    }

    override fun disposeUIResources() {
        panel = null
    }
}

class CommitGeneratorSettingsPanel : JPanel(BorderLayout()) {
    private val settings = service<CommitGeneratorSettings>()

    private val modeCombo = enumCombo(GenerationMode.entries) { it.title }
    private val includeStaged = JBCheckBox("Staged changes")
    private val includeUnstaged = JBCheckBox("Unstaged changes")
    private val includeUntracked = JBCheckBox("Untracked files")
    private val maxDiffTokens = JBTextField()
    private val maxFiles = JBTextField()
    private val maxFileBytes = JBTextField()
    private val maxFileDiffLines = JBTextField()
    private val excludeGlobs = JBTextArea(5, 50)
    private val learnHistory = JBCheckBox("Learn style from recent commits")
    private val historySamples = JBTextField()
    private val conventionalEnabled = JBCheckBox("Enforce Conventional Commits")
    private val conventionalTypes = JBTextField()
    private val maxSubjectLength = JBTextField()
    private val requireScope = JBCheckBox("Require a scope")
    private val allowedScopes = JBTextField()
    private val language = JBTextField()
    private val includeBody = JBCheckBox("Allow a commit body")
    private val includeIssueKey = JBCheckBox("Add issue key from branch")
    private val attribution = enumCombo(OutputAttribution.entries) { it.key }
    private val overwriteDraft = enumCombo(OverwriteDraft.entries) { it.key }
    private val providerTimeout = JBTextField()
    private val maxRetries = JBTextField()
    private val localOnly = JBCheckBox("Local-only mode (reject non-loopback endpoints)")
    private val statusBar = JBCheckBox("Show status bar widget")
    private val logLevel = enumCombo(LogLevel.entries) { it.key }

    private var modified = false
    private var loading = false

    init {
        border = JBUI.Borders.empty(8)
        excludeGlobs.font = JBUI.Fonts.create("JetBrains Mono", 12)
        maxDiffTokens.emptyText.text = "12000"
        maxFiles.emptyText.text = "200"
        maxFileBytes.emptyText.text = "262144"
        maxFileDiffLines.emptyText.text = "400"
        historySamples.emptyText.text = "30"
        maxSubjectLength.emptyText.text = "72"
        language.emptyText.text = "en or auto"
        providerTimeout.emptyText.text = "120000"
        maxRetries.emptyText.text = "2"

        fun mark() {
            if (!loading) {
                modified = true
            }
        }

        fun watched(field: JBTextField) {
            field.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = mark()
                override fun removeUpdate(e: DocumentEvent) = mark()
                override fun changedUpdate(e: DocumentEvent) = mark()
            })
        }

        listOf(
            maxDiffTokens, maxFiles, maxFileBytes, maxFileDiffLines, historySamples,
            conventionalTypes, maxSubjectLength, allowedScopes, language, providerTimeout, maxRetries,
        ).forEach(::watched)
        excludeGlobs.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = mark()
            override fun removeUpdate(e: DocumentEvent) = mark()
            override fun changedUpdate(e: DocumentEvent) = mark()
        })
        listOf(
            includeStaged, includeUnstaged, includeUntracked, learnHistory, conventionalEnabled,
            requireScope, includeBody, includeIssueKey, localOnly, statusBar,
        ).forEach { it.addActionListener { mark() } }
        listOf(modeCombo, attribution, overwriteDraft, logLevel).forEach { it.addActionListener { mark() } }

        val form = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("<html><b>Generation</b></html>"))
            .addLabeledComponent("Mode", modeCombo)
            .addComponent(includeStaged)
            .addComponent(includeUnstaged)
            .addComponent(includeUntracked)
            .addLabeledComponent("Max diff tokens", maxDiffTokens)
            .addLabeledComponent("Max files", maxFiles)
            .addLabeledComponent("Max bytes per file", maxFileBytes)
            .addLabeledComponent("Max diff lines per file", maxFileDiffLines)
            .addLabeledComponent(
                "Additional exclusion globs",
                JBScrollPane(excludeGlobs).apply { preferredSize = Dimension(520, 100) },
            )
            .addComponent(JBLabel("One glob per line. Built-in safety exclusions cannot be disabled."))
            .addComponent(learnHistory)
            .addLabeledComponent("History samples", historySamples)
            .addSeparator()
            .addComponent(JBLabel("<html><b>Conventional Commits</b></html>"))
            .addComponent(conventionalEnabled)
            .addLabeledComponent("Allowed types (comma separated)", conventionalTypes)
            .addLabeledComponent("Max subject length", maxSubjectLength)
            .addComponent(requireScope)
            .addLabeledComponent("Allowed scopes (comma separated, empty = any)", allowedScopes)
            .addSeparator()
            .addComponent(JBLabel("<html><b>Output</b></html>"))
            .addLabeledComponent("Language", language)
            .addComponent(includeBody)
            .addComponent(includeIssueKey)
            .addLabeledComponent("Attribution trailer", attribution)
            .addLabeledComponent("Existing draft", overwriteDraft)
            .addSeparator()
            .addComponent(JBLabel("<html><b>Provider and privacy</b></html>"))
            .addLabeledComponent("Request timeout (ms)", providerTimeout)
            .addLabeledComponent("Max retries", maxRetries)
            .addComponent(localOnly)
            .addSeparator()
            .addComponent(JBLabel("<html><b>Interface</b></html>"))
            .addComponent(statusBar)
            .addLabeledComponent("Log level", logLevel)
            .addComponent(JBLabel("Provider profiles, API keys, and connection tests are managed in the Commit Generator tool window."))
            .panel

        val scroll = JBScrollPane(form).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(760, 700)
            verticalScrollBar.unitIncrement = 16
        }
        add(scroll, BorderLayout.CENTER)
    }

    fun isModified(): Boolean = modified

    fun apply() {
        settings.update { state ->
            state.mode = (modeCombo.item ?: GenerationMode.SMART).key
            state.includeStaged = includeStaged.isSelected
            state.includeUnstaged = includeUnstaged.isSelected
            state.includeUntracked = includeUntracked.isSelected
            state.maxDiffTokens = intOr(maxDiffTokens, 12_000)
            state.maxFiles = intOr(maxFiles, 200)
            state.maxFileBytes = intOr(maxFileBytes, 262_144)
            state.maxFileDiffLines = intOr(maxFileDiffLines, 400)
            state.excludeGlobs = mutableListOf()
            state.additionalExcludeGlobs = excludeGlobs.text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableList()
            state.learnFromHistory = learnHistory.isSelected
            state.historySamples = intOr(historySamples, 30)
            state.conventionalEnabled = conventionalEnabled.isSelected
            state.conventionalTypes = conventionalTypes.text.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableList()
            state.maxSubjectLength = intOr(maxSubjectLength, 72)
            state.requireScope = requireScope.isSelected
            state.allowedScopes = allowedScopes.text.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableList()
            state.outputLanguage = language.text.trim().ifEmpty { "en" }
            state.includeBody = includeBody.isSelected
            state.includeIssueKeyFromBranch = includeIssueKey.isSelected
            state.attribution = (attribution.item ?: OutputAttribution.NONE).key
            state.overwriteDraft = (overwriteDraft.item ?: OverwriteDraft.ASK).key
            state.providerTimeoutMs = intOr(providerTimeout, 120_000)
            state.maxRetries = intOr(maxRetries, 2)
            state.localOnly = localOnly.isSelected
            state.statusBarEnabled = statusBar.isSelected
            state.logLevel = (logLevel.item ?: LogLevel.WARN).key
        }
        modified = false
    }

    fun reset() {
        loading = true
        val config = settings.read()
        modeCombo.item = config.mode
        includeStaged.isSelected = config.context.includeStaged
        includeUnstaged.isSelected = config.context.includeUnstaged
        includeUntracked.isSelected = config.context.includeUntracked
        maxDiffTokens.text = config.context.maxDiffTokens.toString()
        maxFiles.text = config.context.maxFiles.toString()
        maxFileBytes.text = config.context.maxFileBytes.toString()
        maxFileDiffLines.text = config.context.maxFileDiffLines.toString()
        excludeGlobs.text = config.context.excludeGlobs.joinToString("\n")
        learnHistory.isSelected = config.style.learnFromHistory
        historySamples.text = config.style.historySamples.toString()
        conventionalEnabled.isSelected = config.conventional.enabled
        conventionalTypes.text = config.conventional.types.joinToString(", ")
        maxSubjectLength.text = config.conventional.maxSubjectLength.toString()
        requireScope.isSelected = config.conventional.requireScope
        allowedScopes.text = config.conventional.allowedScopes.joinToString(", ")
        language.text = config.output.language
        includeBody.isSelected = config.output.includeBody
        includeIssueKey.isSelected = config.output.includeIssueKeyFromBranch
        attribution.item = config.output.attribution
        overwriteDraft.item = config.output.overwriteDraft
        providerTimeout.text = config.provider.timeoutMs.toString()
        maxRetries.text = config.provider.maxRetries.toString()
        localOnly.isSelected = config.privacy.localOnly
        statusBar.isSelected = config.statusBarEnabled
        logLevel.item = config.logLevel
        loading = false
        modified = false
    }

    private fun intOr(field: JBTextField, fallback: Int): Int = field.text.trim().toIntOrNull() ?: fallback

    @Suppress("UNCHECKED_CAST")
    private fun <T> enumCombo(values: List<T>, label: (T) -> String): ComboBox<T> {
        val model = javax.swing.DefaultComboBoxModel<T>()
        values.forEach { model.addElement(it) }
        val combo = ComboBox(model)
        combo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
            ): java.awt.Component {
                val display = if (value == null) "" else label(value as T)
                return super.getListCellRendererComponent(list, display, index, isSelected, cellHasFocus)
            }
        }
        return combo
    }
}
