package com.rajatmalik.commitgenerator.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.rajatmalik.commitgenerator.adapters.providers.presetFor
import com.rajatmalik.commitgenerator.config.CommitGeneratorSettings
import com.rajatmalik.commitgenerator.core.Product
import com.rajatmalik.commitgenerator.core.domain.ProviderId
import com.rajatmalik.commitgenerator.core.domain.ProviderProfile
import com.rajatmalik.commitgenerator.core.domain.ProfileAuthStyle
import com.rajatmalik.commitgenerator.core.domain.WireDialect
import com.rajatmalik.commitgenerator.core.domain.validateProfile
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

private data class AuthChoice(val style: ProfileAuthStyle?, val label: String) {
    override fun toString(): String = label
}

class ProfileDialog(
    project: Project,
    private val existing: ProviderProfile?,
) : DialogWrapper(project, true) {

    private val settings = service<CommitGeneratorSettings>()

    private val providerCombo = ComboBox(ProviderId.entries.toTypedArray())
    private val dialectCombo = ComboBox(
        WireDialect.entries.filterNot { it == WireDialect.OPENCODE_SESSION }.toTypedArray(),
    )
    private val authCombo = ComboBox(
        arrayOf(
            AuthChoice(null, "Preset (recommended)"),
            AuthChoice(ProfileAuthStyle.BEARER, "Bearer token"),
            AuthChoice(ProfileAuthStyle.API_KEY_HEADER, "API key header"),
            AuthChoice(ProfileAuthStyle.NONE, "No authentication"),
        ),
    )
    private val labelField = JBTextField()
    private val idField = JBTextField()
    private val baseUrlField = JBTextField()
    private val modelField = JBTextField()
    private val fallbackField = JBTextField()
    private val contextField = JBTextField()
    private val temperatureField = JBTextField()
    private val maxOutputField = JBTextField()
    private val timeoutField = JBTextField()
    private val authHeaderField = JBTextField()
    private val headersArea = JBTextArea(5, 50)
    private val secretField = JBPasswordField()
    private var autoId: String = existing?.id ?: ""
    private var loading = true
    private var lastValidationError: String? = null

    init {
        title = if (existing == null) "${Product.NAME}: Add provider profile" else "${Product.NAME}: Edit provider profile"
        setSize(760, 700)
        isResizable = true

        providerCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
            ): java.awt.Component = super.getListCellRendererComponent(
                list,
                (value as? ProviderId)?.label ?: "",
                index,
                isSelected,
                cellHasFocus,
            )
        }
        dialectCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
            ): java.awt.Component = super.getListCellRendererComponent(
                list,
                (value as? WireDialect)?.key ?: "",
                index,
                isSelected,
                cellHasFocus,
            )
        }

        headersArea.font = JBUI.Fonts.create("JetBrains Mono", 12)
        timeoutField.emptyText.text = "0 = global default"
        temperatureField.emptyText.text = "0.2"
        contextField.emptyText.text = "128000"
        maxOutputField.emptyText.text = "512"
        secretField.emptyText.text = "Leave blank to keep the stored credential"

        loadExisting()
        providerCombo.addActionListener {
            if (!loading) {
                dialectCombo.isEnabled = providerCombo.item == ProviderId.CUSTOM
                idField.isEnabled = existing == null
            }
        }
        labelField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateAutoId()
            override fun removeUpdate(e: DocumentEvent) = updateAutoId()
            override fun changedUpdate(e: DocumentEvent) = updateAutoId()
        })

        init()
        loading = false
        if (existing == null) {
            updateAutoId()
        }
    }

    override fun createCenterPanel(): JComponent {
        val useDefaults = JButton("Use preset defaults").apply {
            addActionListener { applyPresetDefaults(overwrite = true) }
        }
        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent("Provider", providerCombo)
            .addComponent(useDefaults)
            .addSeparator()
            .addLabeledComponent("Display label", labelField)
            .addLabeledComponent("Profile id", idField)
            .addLabeledComponent("Base URL", baseUrlField)
            .addLabeledComponent("API format", dialectCombo)
            .addLabeledComponent("Model", modelField)
            .addLabeledComponent("Fallback model (optional)", fallbackField)
            .addLabeledComponent("Context window (tokens)", contextField)
            .addLabeledComponent("Temperature", temperatureField)
            .addLabeledComponent("Max output tokens", maxOutputField)
            .addLabeledComponent("Request timeout (ms, optional)", timeoutField)
            .addLabeledComponent("Authentication", authCombo)
            .addLabeledComponent("API key header name", authHeaderField)
            .addLabeledComponent(
                "Custom headers",
                JBScrollPane(headersArea).apply { preferredSize = Dimension(520, 100) },
            )
            .addComponent(JBLabel("One header per line: Header-Name=value. Authorization headers are not allowed here. Do not put credentials in custom headers."))
            .addSeparator()
            .addLabeledComponent("API key / password", secretField)
            .addComponent(JBLabel("Credentials are stored in the OS keychain through PasswordSafe, never in project files."))
            .panel

        val scroll = JBScrollPane(form).apply {
            border = JBUI.Borders.empty(4)
            preferredSize = Dimension(700, 620)
            verticalScrollBar.unitIncrement = 16
        }
        return JPanel(BorderLayout()).apply { add(scroll, BorderLayout.CENTER) }
    }

    override fun doValidate(): ValidationInfo? {
        val profile = buildProfile() ?: return ValidationInfo(lastValidationError ?: "Invalid profile.", providerCombo)
        val problems = validateProfile(profile).problems
        return if (problems.isEmpty()) {
            null
        } else {
            ValidationInfo(problems.joinToString(" "), providerCombo)
        }
    }

    fun toProfile(): ProviderProfile? = buildProfile()

    fun newSecret(): String? {
        val value = String(secretField.password)
        return value.ifEmpty { null }
    }

    private fun buildProfile(): ProviderProfile? {
        val provider = providerCombo.item ?: return null
        val preset = presetFor(provider)
        val label = labelField.text.trim().ifBlank { preset.label }
        val id = idField.text.trim().ifEmpty {
            if (existing != null) existing.id else settings.nextProfileId(label)
        }
        val context = contextField.text.trim().toIntOrNull() ?: preset.defaultContextWindow
        val temperature = temperatureField.text.trim().toDoubleOrNull() ?: 0.2
        val maxOutput = maxOutputField.text.trim().toIntOrNull() ?: 512
        val timeout = timeoutField.text.trim().toIntOrNull()?.takeIf { it > 0 }
        val auth = authCombo.item

        if (label.isEmpty()) {
            lastValidationError = "Display label must not be empty."
            return null
        }
        if (id.isEmpty()) {
            lastValidationError = "Profile id must not be empty."
            return null
        }
        if (context < 1024) {
            lastValidationError = "Context window must be at least 1024 tokens."
            return null
        }
        if (temperature < 0.0 || temperature > 2.0 || !temperature.isFinite()) {
            lastValidationError = "Temperature must be between 0 and 2."
            return null
        }
        if (maxOutput !in 32..16_384) {
            lastValidationError = "Max output tokens must be between 32 and 16384."
            return null
        }
        if (timeout != null && timeout !in 5_000..300_000) {
            lastValidationError = "Request timeout must be between 5000 and 300000 ms."
            return null
        }

        val headers = parseHeaders(headersArea.text)
        val profile = ProviderProfile(
            id = id,
            label = label,
            provider = provider,
            dialect = if (provider == ProviderId.CUSTOM) {
                dialectCombo.item ?: WireDialect.OPENAI_CHAT
            } else {
                preset.dialect
            },
            baseUrl = baseUrlField.text.trim().ifEmpty { preset.baseUrl },
            model = modelField.text.trim(),
            fallbackModel = fallbackField.text.trim().ifEmpty { null },
            contextWindow = context,
            temperature = temperature,
            maxOutputTokens = maxOutput,
            timeoutMs = timeout,
            headers = headers,
            authStyle = auth?.style,
            authHeaderName = authHeaderField.text.trim().ifEmpty { null },
        )
        return profile
    }

    private fun loadExisting() {
        val profile = existing
        if (profile == null) {
            providerCombo.item = ProviderId.DEEPSEEK
            val preset = presetFor(ProviderId.DEEPSEEK)
            idField.isEnabled = true
            labelField.text = preset.label
            baseUrlField.text = preset.baseUrl
            modelField.text = preset.modelHint
            contextField.text = preset.defaultContextWindow.toString()
            temperatureField.text = "0.2"
            maxOutputField.text = "512"
            authCombo.item = authCombo.getItemAt(0)
            authHeaderField.text = "x-api-key"
            dialectCombo.item = preset.dialect
            dialectCombo.isEnabled = false
            updateAutoId()
        } else {
            providerCombo.item = profile.provider
            labelField.text = profile.label
            idField.text = profile.id
            idField.isEnabled = false
            baseUrlField.text = profile.baseUrl
            modelField.text = profile.model
            fallbackField.text = profile.fallbackModel ?: ""
            contextField.text = profile.contextWindow.toString()
            temperatureField.text = profile.temperature.toString()
            maxOutputField.text = profile.maxOutputTokens.toString()
            timeoutField.text = profile.timeoutMs?.toString() ?: ""
            headersArea.text = profile.headers.entries.joinToString("\n") { "${it.key}=${it.value}" }
            authCombo.item = authCombo.getItemAt(
                (0 until authCombo.itemCount).firstOrNull { index ->
                    authCombo.getItemAt(index)?.style == profile.authStyle
                } ?: 0,
            )
            authHeaderField.text = profile.authHeaderName ?: "x-api-key"
            dialectCombo.item = profile.dialect
            dialectCombo.isEnabled = profile.provider == ProviderId.CUSTOM
        }
        autoId = idField.text
    }

    private fun applyPresetDefaults(overwrite: Boolean) {
        val provider = providerCombo.item ?: return
        val preset = presetFor(provider)
        if (overwrite || baseUrlField.text.isBlank()) {
            baseUrlField.text = preset.baseUrl
        }
        if (overwrite || modelField.text.isBlank()) {
            modelField.text = preset.modelHint
        }
        if (overwrite || contextField.text.isBlank()) {
            contextField.text = preset.defaultContextWindow.toString()
        }
        if (overwrite || labelField.text.isBlank()) {
            labelField.text = preset.label
        }
        dialectCombo.item = preset.dialect
        dialectCombo.isEnabled = provider == ProviderId.CUSTOM
        if (overwrite) {
            authCombo.item = authCombo.getItemAt(0)
        }
        updateAutoId()
    }

    private fun updateAutoId() {
        if (existing != null || loading) {
            return
        }
        val label = labelField.text.trim().ifEmpty { "profile" }
        val generated = settings.nextProfileId(label)
        if (idField.text.isBlank() || idField.text == autoId) {
            idField.text = generated
            autoId = generated
        }
    }

    private fun parseHeaders(text: String): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue
            }
            val index = trimmed.indexOf('=')
            if (index <= 0) {
                continue
            }
            val name = trimmed.substring(0, index).trim()
            val value = trimmed.substring(index + 1).trim()
            if (name.isNotEmpty() && value.isNotEmpty()) {
                headers[name] = value
            }
        }
        return headers
    }
}
