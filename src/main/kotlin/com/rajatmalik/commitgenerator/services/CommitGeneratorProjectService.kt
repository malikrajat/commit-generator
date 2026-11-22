package com.rajatmalik.commitgenerator.services

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.rajatmalik.commitgenerator.adapters.git.RepositoryResolver
import com.rajatmalik.commitgenerator.adapters.providers.resolveEffectiveProfile
import com.rajatmalik.commitgenerator.core.Product
import com.rajatmalik.commitgenerator.core.domain.CancelSignal
import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.ErrorCode
import com.rajatmalik.commitgenerator.core.domain.PrivacySettings
import com.rajatmalik.commitgenerator.core.domain.ProviderId
import com.rajatmalik.commitgenerator.core.domain.ProviderProfile
import com.rajatmalik.commitgenerator.core.domain.appError
import com.rajatmalik.commitgenerator.core.domain.err
import com.rajatmalik.commitgenerator.core.domain.ok
import com.rajatmalik.commitgenerator.core.domain.validateProfile
import com.rajatmalik.commitgenerator.core.domain.valueUnsafe
import com.rajatmalik.commitgenerator.core.ports.ModelInfo
import com.rajatmalik.commitgenerator.core.ports.SecretKind
import com.rajatmalik.commitgenerator.core.security.evaluateUrl
import git4idea.repo.GitRepository
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

data class LastGeneratedMessage(
    val repoRoot: String,
    val previous: String,
    val outcome: com.rajatmalik.commitgenerator.services.generation.GenerationOutcome,
    val prepared: com.rajatmalik.commitgenerator.services.generation.PreparedGeneration,
    val writtenToCommitPanel: Boolean,
)

@Service(Service.Level.PROJECT)
class CommitGeneratorProjectService(private val project: Project) {
    private val properties: PropertiesComponent
        get() = PropertiesComponent.getInstance(project)

    val app: CommitGeneratorApplicationService
        get() = service()

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val generatingFlag = AtomicBoolean(false)

    @Volatile
    var activeIndicator: com.intellij.openapi.progress.ProgressIndicator? = null

    @Volatile
    var lastResult: LastGeneratedMessage? = null

    @Volatile
    var allowSensitiveNext: Boolean = false

    @Volatile
    var pendingMessage: String? = null

    val generationController: GenerationController by lazy { GenerationController(project, this) }

    val isGenerating: Boolean
        get() = generatingFlag.get()

    fun beginGeneration(): Boolean {
        val started = generatingFlag.compareAndSet(false, true)
        if (started) {
            notifyUi()
        }
        return started
    }

    fun endGeneration() {
        generatingFlag.set(false)
        activeIndicator = null
        notifyUi()
    }

    fun cancelGeneration() {
        activeIndicator?.cancel()
    }

    fun selectedRepositoryRoot(): String? =
        properties.getValue(KEY_REPOSITORY_ROOT)?.takeIf { it.isNotBlank() }

    fun setSelectedRepositoryRoot(root: String?) {
        properties.setValue(KEY_REPOSITORY_ROOT, root, "")
    }

    fun repositories(): List<GitRepository> = RepositoryResolver(project).repositories

    fun selectedRepository(): GitRepository? =
        RepositoryResolver(project).resolve(selectedRepositoryRoot())

    fun profiles(): List<ProviderProfile> = app.settings.listProfiles()

    fun activeProfile(): ProviderProfile? = app.settings.activeProfile()

    fun profileById(profileId: String): ProviderProfile? = profiles().firstOrNull { it.id == profileId }

    fun selectProfile(profileId: String): Boolean {
        val selected = app.settings.selectProfile(profileId)
        if (selected) {
            notifyUi()
        }
        return selected
    }

    fun saveProfile(profile: ProviderProfile): CwResult<ProviderProfile> {
        val validation = validateProfile(profile)
        if (!validation.valid) {
            return err(
                appError(
                    ErrorCode.PROFILE_INVALID,
                    validation.problems.joinToString(" "),
                    providerId = profile.provider.key,
                ),
            )
        }
        val effective = resolveEffectiveProfile(profile, app.settings.read().privacy.localOnly)
        if (effective is CwResult.Err) {
            return effective
        }
        app.settings.upsertProfile(effective.valueUnsafe())
        app.models.invalidate(profile.id)
        notifyUi()
        return ok(effective.valueUnsafe())
    }

    fun removeProfile(profileId: String): Boolean {
        app.secrets.clear(profileId, SecretKind.API_KEY)
        app.secrets.clear(profileId, SecretKind.BASIC_PASSWORD)
        val removed = app.settings.removeProfile(profileId)
        if (removed) {
            app.models.invalidate(profileId)
            notifyUi()
        }
        return removed
    }

    fun secretKindFor(profile: ProviderProfile): SecretKind =
        if (profile.provider == ProviderId.OPENCODE && profile.authStyle == null) {
            SecretKind.BASIC_PASSWORD
        } else {
            SecretKind.API_KEY
        }

    fun hasSecret(profile: ProviderProfile): Boolean =
        app.secrets.has(profile.id, secretKindFor(profile))

    fun setSecret(profile: ProviderProfile, value: String) {
        app.secrets.set(profile.id, secretKindFor(profile), value)
        notifyUi()
    }

    fun clearSecret(profile: ProviderProfile) {
        app.secrets.clear(profile.id, SecretKind.API_KEY)
        app.secrets.clear(profile.id, SecretKind.BASIC_PASSWORD)
        notifyUi()
    }

    fun listModels(profile: ProviderProfile, signal: CancelSignal): CwResult<List<ModelInfo>> {
        val effective = resolveEffectiveProfile(profile, app.settings.read().privacy.localOnly)
        if (effective is CwResult.Err) {
            return effective
        }
        val provider = app.registry.get(effective.valueUnsafe())
        return app.models.list(effective.valueUnsafe(), provider, signal)
    }

    fun repositoryLabel(): String {
        val repo = selectedRepository() ?: return "No Git repository"
        val branch = repo.currentBranch?.name
        return if (branch != null) "${repo.root.name} · $branch" else repo.root.name
    }

    fun validationReport(): String {
        val config = app.settings.read()
        val lines = mutableListOf("Commit Generator provider configuration", "")
        val profiles = profiles()
        if (profiles.isEmpty()) {
            lines += "No provider profiles are configured."
        }
        for (profile in profiles) {
            val problems = mutableListOf<String>()
            val validation = validateProfile(profile)
            problems += validation.problems
            val url = evaluateUrl(profile.baseUrl, profile.provider.key, config.privacy.localOnly)
            if (url is CwResult.Err) {
                problems += url.error.message
            }
            val preset = com.rajatmalik.commitgenerator.adapters.providers.presetFor(profile.provider)
            val requiresCredential = when (profile.authStyle) {
                com.rajatmalik.commitgenerator.core.domain.ProfileAuthStyle.NONE -> false
                com.rajatmalik.commitgenerator.core.domain.ProfileAuthStyle.BEARER,
                com.rajatmalik.commitgenerator.core.domain.ProfileAuthStyle.API_KEY_HEADER,
                -> true

                null -> preset.auth != com.rajatmalik.commitgenerator.adapters.providers.PresetAuth.NONE &&
                    preset.auth != com.rajatmalik.commitgenerator.adapters.providers.PresetAuth.BASIC
            }
            if (!hasSecret(profile) && requiresCredential) {
                problems += "no credential stored"
            }
            if (profile.model.isBlank()) {
                problems += "no model configured"
            }
            val marker = if (problems.isEmpty()) "OK" else "ISSUE"
            lines += "[$marker] ${profile.label} (${profile.provider.key}, ${profile.model})"
            if (profile.id == app.settings.activeProfile()?.id) {
                lines += "       active profile"
            }
            problems.forEach { lines += "       - $it" }
        }
        lines += ""
        lines += "Local-only mode: ${if (config.privacy.localOnly) "on" else "off"}"
        lines += "Credentials are stored in the OS keychain through PasswordSafe."
        return lines.joinToString("\n")
    }

    fun diagnostics(): String {
        val config = app.settings.read()
        val repos = repositories()
        val lines = mutableListOf(
            "Commit Generator diagnostics",
            "IDE: ${ApplicationInfo.getInstance().fullApplicationName} (${ApplicationInfo.getInstance().build.asString()})",
            "Plugin: ${Product.VERSION}",
            "Git repositories: ${repos.size}",
            "Selected repository: ${selectedRepository()?.root?.path ?: "(none)"}",
            "Profiles: ${profiles().size}",
            "Active profile: ${activeProfile()?.label ?: "(none)"}",
            "Mode: ${config.mode.key}",
            "Local-only mode: ${if (config.privacy.localOnly) "on" else "off"}",
            "Egress consent records: ${app.consent.list().size}",
            "Secret storage: PasswordSafe",
            "HTTP transport: JDK HttpClient, redirects disabled, TLS validation enabled",
        )
        return lines.joinToString("\n")
    }

    fun addUiListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeUiListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyUi() {
        listeners.forEach { runCatching(it) }
    }

    fun privacy(): PrivacySettings = app.settings.read().privacy

    private companion object {
        const val KEY_REPOSITORY_ROOT = "commit-generator.repositoryRoot"
    }
}

fun commitGeneratorProject(project: Project): CommitGeneratorProjectService =
    project.getService(CommitGeneratorProjectService::class.java)
