package com.rajatmalik.commitgenerator.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.rajatmalik.commitgenerator.core.Product
import com.rajatmalik.commitgenerator.core.domain.ContextSettings
import com.rajatmalik.commitgenerator.core.domain.ConventionalSettings
import com.rajatmalik.commitgenerator.core.domain.EgressConsent
import com.rajatmalik.commitgenerator.core.domain.GenerationMode
import com.rajatmalik.commitgenerator.core.domain.LogLevel
import com.rajatmalik.commitgenerator.core.domain.ModeOverride
import com.rajatmalik.commitgenerator.core.domain.OutputAttribution
import com.rajatmalik.commitgenerator.core.domain.OutputSettings
import com.rajatmalik.commitgenerator.core.domain.OverwriteDraft
import com.rajatmalik.commitgenerator.core.domain.PrivacySettings
import com.rajatmalik.commitgenerator.core.domain.ProviderId
import com.rajatmalik.commitgenerator.core.domain.ProviderProfile
import com.rajatmalik.commitgenerator.core.domain.ProviderSettings
import com.rajatmalik.commitgenerator.core.domain.ProfileAuthStyle
import com.rajatmalik.commitgenerator.core.domain.ResolvedConfig
import com.rajatmalik.commitgenerator.core.domain.StyleSettings
import com.rajatmalik.commitgenerator.core.domain.WireDialect
import com.rajatmalik.commitgenerator.core.domain.validateProfile
import com.rajatmalik.commitgenerator.core.ports.ConfigPort
import com.rajatmalik.commitgenerator.adapters.providers.presetFor
import java.util.concurrent.CopyOnWriteArrayList

class StoredProfile {
    var id: String = ""
    var label: String = ""
    var provider: String = ProviderId.DEEPSEEK.key
    var dialect: String = WireDialect.OPENAI_CHAT.key
    var baseUrl: String = ""
    var model: String = ""
    var fallbackModel: String = ""
    var contextWindow: Int = 64_000
    var temperature: Double = 0.2
    var maxOutputTokens: Int = 512
    var timeoutMs: Int = 0
    var headers: MutableMap<String, String> = linkedMapOf()
    var authStyle: String = ""
    var authHeaderName: String = ""
}

class StoredModeOverride {
    var maxDiffTokens: Int? = null
    var maxFiles: Int? = null
    var maxOutputTokens: Int? = null
    var includeBody: Boolean? = null
    var useMapReduce: Boolean? = null
    var timeoutMs: Int? = null

    fun toDomain(): ModeOverride = ModeOverride(
        maxDiffTokens = maxDiffTokens,
        maxFiles = maxFiles,
        maxOutputTokens = maxOutputTokens,
        includeBody = includeBody,
        useMapReduce = useMapReduce,
        timeoutMs = timeoutMs,
    )
}

class StoredConsent {
    var origin: String = ""
    var providerId: String = ""
    var routeProvider: String = ""
    var routeModel: String = ""
    var grantedAt: Long = 0
}

@State(
    name = Product.STATE_NAME,
    storages = [Storage(value = Product.STORAGE_FILE, roamingType = RoamingType.DISABLED)],
)
@Service(Service.Level.APP)
class CommitGeneratorSettings : PersistentStateComponent<CommitGeneratorSettings.SettingsState>, ConfigPort {

    class SettingsState {
        var profiles: MutableList<StoredProfile> = mutableListOf()
        var activeProfileId: String = ""
        var mode: String = GenerationMode.SMART.key

        var includeStaged: Boolean = true
        var includeUnstaged: Boolean = false
        var includeUntracked: Boolean = false
        var maxDiffTokens: Int = 12_000
        var maxFiles: Int = 200
        var maxFileBytes: Int = 262_144
        var maxFileDiffLines: Int = 400
        var excludeGlobs: MutableList<String> = mutableListOf()
        var additionalExcludeGlobs: MutableList<String> = mutableListOf()

        var learnFromHistory: Boolean = true
        var historySamples: Int = 30

        var conventionalEnabled: Boolean = true
        var conventionalTypes: MutableList<String> = mutableListOf(
            "feat", "fix", "refactor", "perf", "docs", "test", "build", "ci", "chore", "style", "revert",
        )
        var maxSubjectLength: Int = 72
        var requireScope: Boolean = false
        var allowedScopes: MutableList<String> = mutableListOf()

        var outputLanguage: String = "en"
        var includeBody: Boolean = true
        var includeIssueKeyFromBranch: Boolean = false
        var attribution: String = OutputAttribution.NONE.key
        var overwriteDraft: String = OverwriteDraft.ASK.key

        var providerTimeoutMs: Int = 120_000
        var maxRetries: Int = 2
        var localOnly: Boolean = false
        var statusBarEnabled: Boolean = true
        var logLevel: String = LogLevel.WARN.key

        var quickOverride: StoredModeOverride? = null
        var smartOverride: StoredModeOverride? = null
        var detailedOverride: StoredModeOverride? = null

        var consentRecords: MutableList<StoredConsent> = mutableListOf()
    }

    private var myState = SettingsState()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    override fun getState(): SettingsState = myState

    override fun loadState(state: SettingsState) {
        myState = state
        notifyChanged()
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChanged() {
        listeners.forEach { runCatching(it) }
    }

    @Synchronized
    fun update(block: (SettingsState) -> Unit) {
        block(myState)
        notifyChanged()
        ApplicationManager.getApplication().messageBus
            .syncPublisher(CommitGeneratorSettingsListener.TOPIC)
            .settingsChanged()
    }

    @Synchronized
    override fun read(): ResolvedConfig {
        val state = myState
        val overrides = mutableMapOf<GenerationMode, ModeOverride>()
        state.quickOverride?.let { overrides[GenerationMode.QUICK] = it.toDomain() }
        state.smartOverride?.let { overrides[GenerationMode.SMART] = it.toDomain() }
        state.detailedOverride?.let { overrides[GenerationMode.DETAILED] = it.toDomain() }

        return ResolvedConfig(
            mode = GenerationMode.fromKey(state.mode),
            modeOverrides = overrides,
            context = ContextSettings(
                includeStaged = state.includeStaged,
                includeUnstaged = state.includeUnstaged,
                includeUntracked = state.includeUntracked,
                maxDiffTokens = clamp(state.maxDiffTokens, 12_000, 500, 200_000),
                maxFiles = clamp(state.maxFiles, 200, 1, 2_000),
                maxFileBytes = clamp(state.maxFileBytes, 262_144, 1_024, 2_097_152),
                maxFileDiffLines = clamp(state.maxFileDiffLines, 400, 20, 5_000),
                excludeGlobs = (state.excludeGlobs + state.additionalExcludeGlobs)
                    .filter { it.isNotBlank() }
                    .map { it.trim() },
            ),
            style = StyleSettings(
                learnFromHistory = state.learnFromHistory,
                historySamples = clamp(state.historySamples, 30, 0, 100),
            ),
            conventional = ConventionalSettings(
                enabled = state.conventionalEnabled,
                types = state.conventionalTypes.map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .ifEmpty { DEFAULT_CONVENTIONAL_TYPES },
                maxSubjectLength = clamp(state.maxSubjectLength, 72, 20, 120),
                requireScope = state.requireScope,
                allowedScopes = state.allowedScopes.map { it.trim() }.filter { it.isNotEmpty() },
            ),
            output = OutputSettings(
                language = state.outputLanguage.trim().ifEmpty { "en" },
                includeBody = state.includeBody,
                includeIssueKeyFromBranch = state.includeIssueKeyFromBranch,
                attribution = OutputAttribution.fromKey(state.attribution),
                overwriteDraft = OverwriteDraft.fromKey(state.overwriteDraft),
            ),
            provider = ProviderSettings(
                timeoutMs = clamp(state.providerTimeoutMs, 120_000, 5_000, 300_000),
                maxRetries = clamp(state.maxRetries, 2, 0, 5),
            ),
            privacy = PrivacySettings(localOnly = state.localOnly),
            statusBarEnabled = state.statusBarEnabled,
            logLevel = LogLevel.fromKey(state.logLevel),
        )
    }

    @Synchronized
    fun listProfiles(): List<ProviderProfile> = myState.profiles.mapNotNull { raw -> toDomain(raw) }

    @Synchronized
    fun activeProfile(): ProviderProfile? {
        val profiles = listProfiles()
        val configuredId = myState.activeProfileId
        if (configuredId.isNotBlank()) {
            return profiles.firstOrNull { it.id == configuredId }
        }
        return profiles.singleOrNull()
    }

    fun selectProfile(profileId: String): Boolean {
        if (myState.profiles.none { it.id == profileId }) {
            return false
        }
        update { it.activeProfileId = profileId }
        return true
    }

    fun upsertProfile(profile: ProviderProfile): Boolean {
        val existing = myState.profiles.indexOfFirst { it.id == profile.id }
        val stored = fromDomain(profile)
        update { state ->
            if (existing >= 0) {
                state.profiles[existing] = stored
            } else {
                state.profiles.add(stored)
            }
            if (state.activeProfileId.isBlank()) {
                state.activeProfileId = profile.id
            }
        }
        return true
    }

    fun removeProfile(profileId: String): Boolean {
        val removed = myState.profiles.removeIf { it.id == profileId }
        if (removed) {
            update { state ->
                if (state.activeProfileId == profileId) {
                    state.activeProfileId = ""
                }
            }
        }
        return removed
    }

    fun updateModel(profileId: String, model: String): Boolean {
        val index = myState.profiles.indexOfFirst { it.id == profileId }
        if (index < 0) {
            return false
        }
        update { it.profiles[index].model = model }
        return true
    }

    fun nextProfileId(label: String): String {
        var base = label.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "profile" }
            .take(24)
        if (base.length < 2) {
            base = "p-$base"
        }
        var candidate = base
        var suffix = 2
        val ids = myState.profiles.map { it.id }.toSet()
        while (candidate in ids) {
            candidate = "$base-$suffix"
            suffix += 1
        }
        return candidate
    }

    @Synchronized
    fun consentRecords(): List<EgressConsent> = myState.consentRecords.mapNotNull { raw ->
        if (raw.origin.isBlank() || raw.providerId.isBlank()) {
            null
        } else {
            EgressConsent(
                origin = raw.origin,
                providerId = raw.providerId,
                routedVia = if (raw.routeProvider.isNotBlank() && raw.routeModel.isNotBlank()) {
                    com.rajatmalik.commitgenerator.core.domain.ConsentRoute(raw.routeProvider, raw.routeModel)
                } else {
                    null
                },
                grantedAt = raw.grantedAt,
            )
        }
    }

    fun writeConsentRecords(records: List<EgressConsent>) {
        update { state ->
            state.consentRecords = records.map { consent ->
                StoredConsent().apply {
                    origin = consent.origin
                    providerId = consent.providerId
                    routeProvider = consent.routedVia?.providerId ?: ""
                    routeModel = consent.routedVia?.modelId ?: ""
                    grantedAt = consent.grantedAt
                }
            }.toMutableList()
        }
    }

    private fun toDomain(raw: StoredProfile): ProviderProfile? {
        val provider = ProviderId.fromKey(raw.provider) ?: return null
        val preset = presetFor(provider)
        val profile = ProviderProfile(
            id = raw.id,
            label = raw.label.ifBlank { preset.label },
            provider = provider,
            dialect = WireDialect.fromKey(raw.dialect) ?: preset.dialect,
            baseUrl = raw.baseUrl.ifBlank { preset.baseUrl },
            model = raw.model,
            fallbackModel = raw.fallbackModel.ifBlank { null },
            contextWindow = if (raw.contextWindow > 0) raw.contextWindow else preset.defaultContextWindow,
            temperature = raw.temperature.coerceIn(0.0, 2.0),
            maxOutputTokens = raw.maxOutputTokens.coerceIn(32, 16_384),
            timeoutMs = raw.timeoutMs.takeIf { it in 5_000..300_000 },
            headers = raw.headers.filterValues { it.isNotBlank() },
            authStyle = ProfileAuthStyle.fromKey(raw.authStyle),
            authHeaderName = raw.authHeaderName.ifBlank { null },
        )
        return if (validateProfile(profile).valid) profile else null
    }

    private fun fromDomain(profile: ProviderProfile): StoredProfile = StoredProfile().apply {
        id = profile.id
        label = profile.label
        provider = profile.provider.key
        dialect = profile.dialect.key
        baseUrl = profile.baseUrl
        model = profile.model
        fallbackModel = profile.fallbackModel ?: ""
        contextWindow = profile.contextWindow
        temperature = profile.temperature
        maxOutputTokens = profile.maxOutputTokens
        timeoutMs = profile.timeoutMs ?: 0
        headers = profile.headers.toMutableMap()
        authStyle = profile.authStyle?.key ?: ""
        authHeaderName = profile.authHeaderName ?: ""
    }

    private fun clamp(value: Int, fallback: Int, min: Int, max: Int): Int =
        if (value < min || value > max) fallback.coerceIn(min, max) else value

    private companion object {
        val DEFAULT_CONVENTIONAL_TYPES = listOf(
            "feat", "fix", "refactor", "perf", "docs", "test", "build", "ci", "chore", "style", "revert",
        )
    }
}

interface CommitGeneratorSettingsListener {
    fun settingsChanged()

    companion object {
        val TOPIC: com.intellij.util.messages.Topic<CommitGeneratorSettingsListener> =
            com.intellij.util.messages.Topic.create(
                "CommitGeneratorSettingsChanged",
                CommitGeneratorSettingsListener::class.java,
            )
    }
}

fun commitGeneratorSettings(): CommitGeneratorSettings = service()
