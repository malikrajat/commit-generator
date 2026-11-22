package com.rajatmalik.commitgenerator.services

import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ExceptionUtil
import com.rajatmalik.commitgenerator.adapters.git.CommitMessageTarget
import com.rajatmalik.commitgenerator.adapters.git.CommitSelection
import com.rajatmalik.commitgenerator.adapters.git.commitSelectionFrom
import com.rajatmalik.commitgenerator.adapters.git.GitCommandRunner
import com.rajatmalik.commitgenerator.adapters.git.GitDiffCollector
import com.rajatmalik.commitgenerator.adapters.git.GitPortAdapter
import com.rajatmalik.commitgenerator.adapters.git.RepositoryResolver
import com.rajatmalik.commitgenerator.adapters.providers.resolveEffectiveProfile
import com.rajatmalik.commitgenerator.config.CommitGeneratorConfigurable
import com.rajatmalik.commitgenerator.core.Product
import com.rajatmalik.commitgenerator.core.domain.AppError
import com.rajatmalik.commitgenerator.core.domain.CancelSignal
import com.rajatmalik.commitgenerator.core.domain.CancellationToken
import com.rajatmalik.commitgenerator.core.domain.ConsentRequest
import com.rajatmalik.commitgenerator.core.domain.ContextSelection
import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.ErrorCode
import com.rajatmalik.commitgenerator.core.domain.GenerationMode
import com.rajatmalik.commitgenerator.core.domain.NeverCancelled
import com.rajatmalik.commitgenerator.core.domain.ProviderProfile
import com.rajatmalik.commitgenerator.core.domain.appError
import com.rajatmalik.commitgenerator.core.domain.consentMatches
import com.rajatmalik.commitgenerator.core.domain.err
import com.rajatmalik.commitgenerator.core.domain.ok
import com.rajatmalik.commitgenerator.core.domain.valueUnsafe
import com.rajatmalik.commitgenerator.services.generation.GenerationOutcome
import com.rajatmalik.commitgenerator.services.generation.PreparedGeneration
import com.rajatmalik.commitgenerator.services.generation.PrepareOptions
import com.rajatmalik.commitgenerator.services.generation.RepoContext
import com.rajatmalik.commitgenerator.core.message.heuristicMessage
import com.rajatmalik.commitgenerator.core.ports.MessageWriterPort
import com.rajatmalik.commitgenerator.core.ports.ModelInfo
import com.rajatmalik.commitgenerator.core.ports.SecretKind
import com.rajatmalik.commitgenerator.core.ports.WritePolicy
import com.rajatmalik.commitgenerator.core.ports.SummaryProgress
import com.rajatmalik.commitgenerator.core.security.evaluateUrl
import com.rajatmalik.commitgenerator.core.security.originOf
import com.rajatmalik.commitgenerator.ui.ManageProfilesDialog
import com.rajatmalik.commitgenerator.ui.ModelPickerDialog
import com.rajatmalik.commitgenerator.ui.ProfileDialog
import com.rajatmalik.commitgenerator.ui.SelectionDialog
import com.rajatmalik.commitgenerator.ui.TextPreviewDialog
import git4idea.repo.GitRepository
import java.awt.datatransfer.StringSelection
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GenerationController(
    private val project: Project,
    private val state: CommitGeneratorProjectService,
) {
    private val app: CommitGeneratorApplicationService
        get() = state.app

    fun generate(dataContext: DataContext?, stagedOnly: Boolean = false, useAllPending: Boolean = false) {
        val actionTarget = CommitMessageTarget.fromDataContext(dataContext)
        val initialTarget = actionTarget ?: locateTarget()
        val explicitSelection = if (stagedOnly || useAllPending) {
            null
        } else {
            commitSelectionFrom(dataContext)
                ?: initialTarget?.dataContext()?.let(::commitSelectionFrom)
        }
        if (explicitSelection != null && explicitSelection.absolutePaths.isEmpty()) {
            info(
                "No files are checked in the Commit panel.",
                actions = listOf(
                    "Analyze all pending changes" to { analyzeAllPendingChanges() },
                    "Open Settings" to { openSettings() },
                ),
            )
            return
        }

        if (!state.beginGeneration()) {
            info("Generation already in progress.")
            return
        }
        val repository = state.selectedRepository()
        if (repository == null) {
            state.endGeneration()
            error("No Git repository is open in this project.", actions = listOf("Open Commit Settings" to { openSettings() }))
            return
        }
        val profile = state.activeProfile()
        if (profile == null) {
            state.endGeneration()
            error("No provider profile is configured.", actions = listOf("Manage Profiles" to { manageProfiles() }))
            return
        }
        val config = app.settings.read()
        val selection = when {
            useAllPending -> ContextSelection(includeStaged = true, includeUnstaged = true, includeUntracked = true)
            stagedOnly -> ContextSelection(includeStaged = true, includeUnstaged = false, includeUntracked = false)
            else -> config.context.selection()
        }
        val allowSensitive = state.allowSensitiveNext
        state.allowSensitiveNext = false

        val task = object : Task.Backgroundable(project, "${Product.NAME}: generating commit message", true) {
            override fun run(indicator: ProgressIndicator) {
                val token = CancellationToken()
                state.activeIndicator = indicator
                val watcher = cancellationWatcher(indicator, token)
                try {
                    indicator.isIndeterminate = false
                    indicator.fraction = 0.05
                    indicator.text = "Collecting changes..."
                    val repo = repoContext(repository)
                    val prepared = app.generation.prepare(
                        repo,
                        PrepareOptions(
                            config,
                            profile,
                            selection,
                            allowSensitive,
                            explicitSelection?.absolutePaths,
                        ),
                        token,
                    )
                    if (prepared is CwResult.Err) {
                        handleError(prepared.error, repository, null, initialTarget)
                        return
                    }
                    if (!ensureConsent(prepared.valueUnsafe(), token)) {
                        return
                    }

                    indicator.fraction = 0.35
                    indicator.text = "Generating commit message..."
                    val outcome = app.generation.execute(prepared.valueUnsafe(), token) { progress ->
                        indicator.fraction = progressFraction(progress)
                        indicator.text = progressText(progress)
                    }
                    if (outcome is CwResult.Err) {
                        handleError(outcome.error, repository, prepared.valueUnsafe(), initialTarget)
                        return
                    }
                    indicator.fraction = 0.95
                    indicator.text = "Writing commit message..."
                    writeResult(repository, outcome.valueUnsafe(), prepared.valueUnsafe(), initialTarget)
                } catch (error: Throwable) {
                    handleThrowable(error)
                } finally {
                    watcher.close()
                    state.endGeneration()
                }
            }
        }
        ProgressManager.getInstance().run(task)
    }

    private fun analyzeAllPendingChanges() {
        app.settings.update {
            it.includeStaged = true
            it.includeUnstaged = true
            it.includeUntracked = true
        }
        ApplicationManager.getApplication().invokeLater {
            generate(null, useAllPending = true)
        }
    }

    fun regenerate(dataContext: DataContext? = null) {
        val last = state.lastResult
        if (last == null) {
            info("There is no previous generation to repeat.")
            return
        }
        if (!state.beginGeneration()) {
            info("Generation already in progress.")
            return
        }
        val repository = RepositoryResolver(project).byRootPath(last.repoRoot)
        if (repository == null) {
            state.endGeneration()
            error("The repository used for the previous generation is no longer open.")
            return
        }
        val actionTarget = CommitMessageTarget.fromDataContext(dataContext)
        val task = object : Task.Backgroundable(project, "${Product.NAME}: regenerating commit message", true) {
            override fun run(indicator: ProgressIndicator) {
                val token = CancellationToken()
                state.activeIndicator = indicator
                val watcher = cancellationWatcher(indicator, token)
                try {
                    if (!ensureConsent(last.prepared, token)) {
                        return
                    }
                    indicator.isIndeterminate = false
                    indicator.fraction = 0.35
                    indicator.text = "Regenerating commit message..."
                    val outcome = app.generation.execute(last.prepared, token) { progress ->
                        indicator.fraction = progressFraction(progress)
                        indicator.text = progressText(progress)
                    }
                    if (outcome is CwResult.Err) {
                        handleError(outcome.error, repository, last.prepared, actionTarget)
                        return
                    }
                    writeResult(repository, outcome.valueUnsafe(), last.prepared, actionTarget ?: locateTarget())
                } catch (error: Throwable) {
                    handleThrowable(error)
                } finally {
                    watcher.close()
                    state.endGeneration()
                }
            }
        }
        ProgressManager.getInstance().run(task)
    }

    fun showPreview(dataContext: DataContext? = null) {
        val initialTarget = CommitMessageTarget.fromDataContext(dataContext) ?: locateTarget()
        val explicitSelection = commitSelectionFrom(dataContext)
            ?: initialTarget?.dataContext()?.let(::commitSelectionFrom)
        if (explicitSelection != null && explicitSelection.absolutePaths.isEmpty()) {
            info("No files are checked in the Commit panel.")
            return
        }
        if (!state.beginGeneration()) {
            info("A generation is already in progress.")
            return
        }
        val repository = state.selectedRepository()
        val profile = state.activeProfile()
        if (repository == null || profile == null) {
            state.endGeneration()
            error(if (repository == null) "No Git repository is open." else "No provider profile is configured.")
            return
        }
        val config = app.settings.read()
        val task = object : Task.Backgroundable(project, "${Product.NAME}: building payload preview", true) {
            override fun run(indicator: ProgressIndicator) {
                val token = CancellationToken()
                state.activeIndicator = indicator
                val watcher = cancellationWatcher(indicator, token)
                try {
                    indicator.isIndeterminate = true
                    indicator.text = "Collecting changes..."
                    val prepared = app.generation.prepare(
                        repoContext(repository),
                        PrepareOptions(
                            config,
                            profile,
                            config.context.selection(),
                            false,
                            explicitSelection?.absolutePaths,
                        ),
                        token,
                    )
                    if (prepared is CwResult.Err) {
                        handleError(prepared.error, repository, null, initialTarget)
                        return
                    }
                    val value = prepared.valueUnsafe()
                    val details = buildString {
                        append("Payload preview (redacted)\n")
                        append("Provider: ${value.profile.provider.key} / ${value.profile.model}\n")
                        append("Mode: ${value.mode.id.key}\n")
                        append("Files: ${value.changeSet.counts.files}, omitted: ${value.changeSet.omitted.size}\n")
                        append("Estimated tokens: ${value.plan.estimatedTokens}\n")
                        append("Strategy: ${value.plan.strategy.name.lowercase()}\n")
                        append("Injection signals: ${value.injectionSignals.size}\n\n")
                        append(app.generation.payloadPreview(value))
                    }
                    onEdt { TextPreviewDialog(project, "Commit Generator payload preview", details).show() }
                } catch (error: Throwable) {
                    handleThrowable(error)
                } finally {
                    watcher.close()
                    state.endGeneration()
                }
            }
        }
        ProgressManager.getInstance().run(task)
    }

    fun cancel() {
        state.cancelGeneration()
        info("Generation cancelled.")
    }

    fun undoLast() {
        val last = state.lastResult
        if (last == null) {
            info("There is no generated message to undo.")
            return
        }
        if (!last.writtenToCommitPanel) {
            info("The last message was not inserted into the commit field, so there is nothing to undo.")
            return
        }
        val target = locateTarget()
        if (target == null) {
            error("Open the Commit tool window so Commit Generator can restore the previous message.")
            return
        }
        val restored = runCatching { target.restore(last.previous) }
        if (restored.isFailure) {
            error("Could not restore the previous message. The commit panel may have been closed.")
            return
        }
        state.lastResult = null
        state.pendingMessage = null
        state.notifyUi()
        info("Previous commit message restored.")
    }

    fun copyLast() {
        val message = state.lastResult?.outcome?.message?.raw ?: state.pendingMessage
        if (message.isNullOrEmpty()) {
            info("There is no generated message to copy.")
            return
        }
        onEdt { CopyPasteManager.getInstance().setContents(StringSelection(message)) }
        info("Commit message copied to the clipboard.")
    }

    fun insertLast() {
        val message = state.lastResult?.outcome?.message?.raw ?: state.pendingMessage
        if (message.isNullOrEmpty()) {
            info("There is no generated message to insert.")
            return
        }
        val target = locateTarget()
        if (target == null) {
            error("Open the Commit tool window first, then try again.")
            return
        }
        val written = runCatching { target.write(message, WritePolicy.REPLACE) }.getOrElse {
            error("Could not insert into the commit field. The commit panel may have been closed.")
            return
        }
        state.pendingMessage = null
        lastIfAvailable()?.let {
            state.lastResult = it.copy(previous = written.previous, writtenToCommitPanel = true)
        }
        state.notifyUi()
        info("Commit message inserted.")
    }

    fun setApiKey() {
        val profile = chooseProfile("Set API key") ?: return
        val kind = state.secretKindFor(profile)
        val label = if (kind == SecretKind.BASIC_PASSWORD) "password" else "API key"
        val value = onEdt {
            Messages.showPasswordDialog(
                project,
                "Enter the $label for \"${profile.label}\". It is stored in the OS keychain and never written to project files.",
                "${Product.NAME}: $label",
                Messages.getQuestionIcon(),
            )
        }
        if (value.isNullOrEmpty()) {
            return
        }
        state.setSecret(profile, value)
        info("$label stored for \"${profile.label}\".")
    }

    fun clearApiKey() {
        val profile = chooseProfile("Clear API key") ?: return
        val confirmed = onEdt {
            Messages.showYesNoDialog(
                project,
                "Remove the stored credential for \"${profile.label}\"?",
                "${Product.NAME}: Clear credential",
                Messages.getQuestionIcon(),
            ) == Messages.YES
        }
        if (!confirmed) {
            return
        }
        state.clearSecret(profile)
        info("Stored credential removed for \"${profile.label}\".")
    }

    fun selectProfile() {
        val profiles = state.profiles()
        if (profiles.isEmpty()) {
            info("No provider profiles are configured.")
            return
        }
        val active = state.activeProfile()
        val selected = onEdt {
            SelectionDialog(
                project,
                "${Product.NAME}: Provider profile",
                "Choose the provider profile used for the next generation.",
                profiles,
                { "${it.label} — ${it.model}" },
                active,
            ).showAndGetItem()
        } ?: return
        state.selectProfile(selected.id)
        info("Active provider is \"${selected.label}\".")
    }

    fun selectModel() {
        val profile = state.activeProfile()
        if (profile == null) {
            info("No provider profile is configured.")
            return
        }
        runModelLoad(profile, showPicker = true)
    }

    fun testConnection() {
        val profile = state.activeProfile()
        if (profile == null) {
            info("No provider profile is configured.")
            return
        }
        runModelLoad(profile, showPicker = false)
    }

    private fun runModelLoad(profile: ProviderProfile, showPicker: Boolean) {
        if (!state.beginGeneration()) {
            info("Another Commit Generator operation is already running.")
            return
        }
        val task = object : Task.Backgroundable(project, "${Product.NAME}: loading models", true) {
            override fun run(indicator: ProgressIndicator) {
                val token = CancellationToken()
                state.activeIndicator = indicator
                val watcher = cancellationWatcher(indicator, token)
                try {
                    indicator.isIndeterminate = true
                    indicator.text = "Contacting ${profile.label}..."
                    val result = state.listModels(profile, token)
                    if (result is CwResult.Err) {
                        handleError(result.error, null, null, null)
                        return
                    }
                    val models = result.valueUnsafe()
                    if (showPicker) {
                        onEdt {
                            val picked = ModelPickerDialog(project, profile, models).showAndGetModel()
                            if (picked != null) {
                                state.app.settings.updateModel(profile.id, picked)
                                state.app.models.invalidate(profile.id)
                                state.notifyUi()
                                info("Model for \"${profile.label}\" is now \"$picked\".")
                            }
                        }
                    } else {
                        success(
                            "Connection to ${profile.label} succeeded.",
                            "${models.size} model${if (models.size == 1) "" else "s"} available.",
                            listOf("Select Model" to { selectModel() }),
                        )
                    }
                } catch (error: Throwable) {
                    handleThrowable(error)
                } finally {
                    watcher.close()
                    state.endGeneration()
                }
            }
        }
        ProgressManager.getInstance().run(task)
    }

    fun validateProfiles() {
        onEdt { TextPreviewDialog(project, "${Product.NAME}: provider validation", state.validationReport()).show() }
    }

    fun showDiagnostics() {
        onEdt { TextPreviewDialog(project, "${Product.NAME}: diagnostics", state.diagnostics()).show() }
    }

    fun showLastDetails() {
        val last = state.lastResult
        if (last == null) {
            info("There is no generated message to describe yet.")
            return
        }
        val details = buildDetails(last.outcome, last.prepared)
        onEdt { TextPreviewDialog(project, "${Product.NAME}: generation details", details).show() }
    }

    fun manageProfiles() {
        onEdt { ManageProfilesDialog(project, state).show() }
        state.notifyUi()
    }

    fun addProfile() {
        onEdt {
            val dialog = ProfileDialog(project, null)
            if (dialog.showAndGet()) {
                val profile = dialog.toProfile() ?: return@onEdt
                when (val result = state.saveProfile(profile)) {
                    is CwResult.Ok -> {
                        state.selectProfile(result.value.id)
                        dialog.newSecret()?.let { state.setSecret(result.value, it) }
                        info("Provider profile \"${result.value.label}\" added.")
                    }

                    is CwResult.Err -> error(result.error.message)
                }
            }
        }
    }

    fun editProfile() {
        val profile = chooseProfile("Edit provider profile") ?: return
        onEdt {
            val dialog = ProfileDialog(project, profile)
            if (dialog.showAndGet()) {
                val updated = dialog.toProfile() ?: return@onEdt
                when (val result = state.saveProfile(updated)) {
                    is CwResult.Ok -> {
                        dialog.newSecret()?.let { state.setSecret(result.value, it) }
                        info("Provider profile \"${result.value.label}\" updated.")
                    }

                    is CwResult.Err -> error(result.error.message)
                }
            }
        }
    }

    fun removeProfile() {
        val profile = chooseProfile("Remove provider profile") ?: return
        val confirmed = onEdt {
            Messages.showYesNoDialog(
                project,
                "Remove provider profile \"${profile.label}\" and its stored credential?",
                "${Product.NAME}: Remove profile",
                Messages.getWarningIcon(),
            ) == Messages.YES
        }
        if (!confirmed) {
            return
        }
        if (state.removeProfile(profile.id)) {
            info("Provider profile removed.")
        }
    }

    fun allowSensitiveOnce() {
        state.allowSensitiveNext = true
        warning("Sensitive files will be included in the NEXT generation only. Secrets are still redacted before sending.")
    }

    fun revokeConsent() {
        val confirmed = onEdt {
            Messages.showYesNoDialog(
                project,
                "Revoke all stored network consent records? Commit Generator will ask again before sending changes.",
                "${Product.NAME}: Revoke consent",
                Messages.getQuestionIcon(),
            ) == Messages.YES
        }
        if (confirmed) {
            app.consent.revokeAll()
            info("Egress consent revoked.")
        }
    }

    fun openSettings() {
        onEdt {
            com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, CommitGeneratorConfigurable::class.java)
        }
    }

    fun openToolWindow() {
        onEdt {
            ToolWindowManager.getInstance(project).getToolWindow(Product.TOOL_WINDOW_ID)?.show()
        }
    }

    fun selectRepository() {
        val repositories = state.repositories()
        if (repositories.isEmpty()) {
            info("No Git repository is open in this project.")
            return
        }
        val current = state.selectedRepository()
        val selected = onEdt {
            SelectionDialog(
                project,
                "${Product.NAME}: Select repository",
                "Choose the repository Commit Generator analyses.",
                repositories,
                { repo ->
                    val branch = repo.currentBranch?.name
                    if (branch != null) "${repo.root.name} — $branch (${repo.root.path})" else repo.root.name
                },
                current,
            ).showAndGetItem()
        } ?: return
        state.setSelectedRepositoryRoot(selected.root.path)
        state.notifyUi()
        info("Repository set to \"${selected.root.name}\".")
    }

    fun openDocumentation() {
        BrowserUtil.browse(Product.HOMEPAGE)
    }

    private fun ensureConsent(prepared: PreparedGeneration, token: CancellationToken): Boolean {
        val url = evaluateUrl(
            prepared.profile.baseUrl,
            prepared.profile.provider.key,
            app.settings.read().privacy.localOnly,
        )
        if (url is CwResult.Err) {
            handleError(url.error, null, prepared, null)
            return false
        }
        val request = ConsentRequest(
            origin = originOf(url.valueUnsafe().uri),
            providerId = prepared.profile.provider.key,
            model = prepared.profile.model,
            mode = prepared.mode.id,
            estimatedTokens = prepared.plan.estimatedTokens,
            routedVia = routeFor(prepared.profile),
        )
        if (app.consent.isGranted(request)) {
            return true
        }
        val decision = onEdt {
            val message = buildString {
                append("Send this change set to ${prepared.profile.provider.label}?\n\n")
                append("Origin: ${request.origin}\n")
                append("Profile: ${prepared.profile.label}\n")
                append("Model: ${prepared.profile.model}\n")
                append("Mode: ${prepared.mode.id.title}\n")
                append("Files: ${prepared.changeSet.counts.files}\n")
                append("Estimated payload: ~${prepared.plan.estimatedTokens} tokens\n\n")
                append("Secrets are redacted, sensitive paths are excluded by default, and diff content is treated as data.\n")
                append("Commit Generator never commits or stages anything.")
            }
            Messages.showDialog(
                project,
                message,
                "${Product.NAME}: Network consent",
                arrayOf("Send and remember", "Send once", "Cancel"),
                0,
                Messages.getQuestionIcon(),
            )
        }
        when (decision) {
            0 -> {
                app.consent.record(request)
                return true
            }

            1 -> return true
            else -> return false
        }
    }

    private fun routeFor(profile: ProviderProfile): com.rajatmalik.commitgenerator.core.domain.ConsentRoute? {
        if (profile.provider != com.rajatmalik.commitgenerator.core.domain.ProviderId.OPENCODE) {
            return null
        }
        val index = profile.model.indexOf('/')
        if (index <= 0) {
            return null
        }
        return com.rajatmalik.commitgenerator.core.domain.ConsentRoute(
            profile.model.substring(0, index),
            profile.model.substring(index + 1),
        )
    }

    private fun repoContext(repository: GitRepository): RepoContext {
        val git = GitPortAdapter(GitCommandRunner(project, repository), repository)
        return RepoContext(git, GitDiffCollector(git, app.logger))
    }

    private fun writeResult(
        repository: GitRepository,
        outcome: GenerationOutcome,
        prepared: PreparedGeneration,
        initialTarget: CommitMessageTarget?,
    ) {
        val target = initialTarget ?: locateTarget()
        val config = app.settings.read()
        if (target == null) {
            state.pendingMessage = outcome.message.raw
            state.lastResult = LastGeneratedMessage(
                repoRoot = repository.root.path,
                previous = "",
                outcome = outcome,
                prepared = prepared,
                writtenToCommitPanel = false,
            )
            state.notifyUi()
            warning(
                "Commit message generated. Open the Commit tool window and choose Insert.",
                "The message is available in the Commit Generator tool window.",
                listOf("Open Commit Tool Window" to { openToolWindow() }, "Copy" to { copyLast() }),
            )
            return
        }

        val current = target.readCurrent() ?: ""
        var policy = WritePolicy.REPLACE
        if (current.trim().isNotEmpty()) {
            policy = when (config.output.overwriteDraft) {
                com.rajatmalik.commitgenerator.core.domain.OverwriteDraft.APPEND -> WritePolicy.APPEND
                com.rajatmalik.commitgenerator.core.domain.OverwriteDraft.REPLACE -> WritePolicy.REPLACE
                com.rajatmalik.commitgenerator.core.domain.OverwriteDraft.ASK -> {
                    val choice = onEdt {
                        Messages.showDialog(
                            project,
                            "The commit message field already contains text.\n\nReplace it with the generated message or append?",
                            "${Product.NAME}: Existing draft",
                            arrayOf("Replace", "Append", "Cancel"),
                            0,
                            Messages.getQuestionIcon(),
                        )
                    }
                    when (choice) {
                        0 -> WritePolicy.REPLACE
                        1 -> WritePolicy.APPEND
                        else -> {
                            state.pendingMessage = outcome.message.raw
                            state.lastResult = LastGeneratedMessage(
                                repository.root.path, current, outcome, prepared, false,
                            )
                            state.notifyUi()
                            warning(
                                "Generated message kept in Commit Generator. The existing draft was not changed.",
                                "Use Insert, Copy, or Discard from the Commit Generator tool window.",
                            )
                            return
                        }
                    }
                }
            }
        }

        val written = runCatching { target.write(outcome.message.raw, policy) }.getOrElse {
            val fresh = locateTarget()
            if (fresh != null) {
                fresh.write(outcome.message.raw, policy)
            } else {
                com.rajatmalik.commitgenerator.core.ports.WriteOutcome(
                    written = false,
                    previous = current,
                    failure = "The commit panel is no longer available. The message was copied to the clipboard.",
                )
            }
        }
        if (!written.written) {
            state.pendingMessage = outcome.message.raw
            state.lastResult = LastGeneratedMessage(
                repository.root.path, written.previous, outcome, prepared, false,
            )
            state.notifyUi()
            error(written.failure ?: "Could not write to the commit message field. The message is available in Commit Generator.")
            return
        }

        state.pendingMessage = null
        state.lastResult = LastGeneratedMessage(
            repository.root.path, written.previous, outcome, prepared, true,
        )
        state.notifyUi()

        val details = buildDetails(outcome, prepared)
        val summary = buildString {
            append("${prepared.changeSet.counts.files} file${if (prepared.changeSet.counts.files == 1) "" else "s"}")
            append(" · ${prepared.mode.id.title}")
            append(" · ~${prepared.plan.estimatedTokens} tokens")
            append(" · ${outcome.durationMs} ms")
            if (prepared.plan.omitted.isNotEmpty()) {
                append(" · ${prepared.plan.omitted.size} omitted")
            }
            if (outcome.message.warnings.isNotEmpty()) {
                append(" · ${outcome.message.warnings.size} warning(s)")
            }
            val limitations = outcome.summary?.limitations?.size ?: 0
            if (limitations > 0) {
                append(" · $limitations limitation(s)")
            }
        }
        success(
            "Commit message written to the commit panel.",
            summary,
            listOf(
                "Undo" to { undoLast() },
                "Regenerate" to { regenerate() },
                "Copy" to { copyLast() },
                "Details" to { onEdt { TextPreviewDialog(project, "${Product.NAME}: generation details", details).show() } },
            ),
        )
        app.logger.info(
            "commit message generated",
            mapOf(
                "files" to prepared.changeSet.counts.files,
                "mode" to prepared.mode.id.key,
                "strategy" to prepared.plan.strategy.name,
                "tokens" to prepared.plan.estimatedTokens,
                "durationMs" to outcome.durationMs,
                "warnings" to outcome.message.warnings.size,
                "omitted" to prepared.plan.omitted.size,
            ),
        )
    }

    private fun buildDetails(outcome: GenerationOutcome, prepared: PreparedGeneration): String = buildString {
        append("Generated commit message\n\n")
        append(outcome.message.raw)
        append("\n\n---\n")
        append("Provider: ${prepared.profile.provider.key} / ${prepared.profile.model}\n")
        append("Mode: ${prepared.mode.id.title} (${prepared.plan.strategy.name.lowercase()})\n")
        append("Files: ${prepared.changeSet.counts.files}\n")
        append("Insertions: ${prepared.changeSet.counts.insertions}\n")
        append("Deletions: ${prepared.changeSet.counts.deletions}\n")
        append("Estimated tokens: ${prepared.plan.estimatedTokens}\n")
        append("Duration: ${outcome.durationMs} ms\n")
        outcome.usage?.let { append("Usage: ${it.input} input / ${it.output} output tokens\n") }
        append("Omitted files: ${prepared.plan.omitted.size}\n")
        prepared.plan.omitted.take(20).forEach {
            append("  - ${it.path}: ${it.reason.name.lowercase().replace('_', '-')}\n")
        }
        if (outcome.message.warnings.isNotEmpty()) {
            append("\nWarnings:\n")
            outcome.message.warnings.forEach { append("  - $it\n") }
        }
        val limitations = outcome.summary?.limitations.orEmpty()
        if (limitations.isNotEmpty()) {
            append("\nLimitations:\n")
            limitations.forEach { append("  - $it\n") }
        }
        append("\nCommit Generator never commits, stages or amends. Review the message before committing.")
    }

    private fun handleError(
        error: AppError,
        repository: GitRepository?,
        prepared: PreparedGeneration?,
        target: CommitMessageTarget?,
    ) {
        app.logger.warn("generation failed", mapOf("code" to error.code.name, "httpStatus" to error.httpStatus))
        when (error.code) {
            ErrorCode.CANCELLED -> Unit
            ErrorCode.NO_CHANGES -> info(
                error.message,
                listOf(
                    "Analyze all pending changes" to { analyzeAllPendingChanges() },
                    "Open Settings" to { openSettings() },
                ),
            )

            ErrorCode.PROJECT_NOT_TRUSTED -> info(error.message)

            ErrorCode.SECRET_MISSING,
            ErrorCode.AUTH,
            -> error(
                describe(error),
                actions = listOf(
                    "Set API Key" to { setApiKey() },
                    "Manage Profiles" to { manageProfiles() },
                ),
            )

            ErrorCode.PROFILE_MISSING,
            ErrorCode.PROFILE_INVALID,
            ErrorCode.URL_REJECTED,
            -> error(
                describe(error),
                actions = listOf(
                    "Manage Profiles" to { manageProfiles() },
                    "Open Settings" to { openSettings() },
                ),
            )

            ErrorCode.CONTEXT_OVERFLOW,
            ErrorCode.INVALID_RESPONSE,
            ErrorCode.NETWORK,
            ErrorCode.TIMEOUT,
            ErrorCode.RATE_LIMIT,
            ErrorCode.QUOTA,
            ErrorCode.SERVER,
            ErrorCode.BAD_REQUEST,
            -> error(
                describe(error),
                actions = buildList {
                    if (prepared != null && repository != null) {
                        add("Use Local Fallback" to { writeFallback(repository, prepared, target) })
                    }
                    add("Details" to { onEdt { TextPreviewDialog(project, "${Product.NAME}: error details", describeDetailed(error)).show() } })
                    add("Regenerate" to { regenerate() })
                },
            )

            else -> error(
                describe(error),
                actions = listOf(
                    "Details" to { onEdt { TextPreviewDialog(project, "${Product.NAME}: error details", describeDetailed(error)).show() } },
                    "Regenerate" to { regenerate() },
                ),
            )
        }
    }

    private fun writeFallback(repository: GitRepository, prepared: PreparedGeneration, target: CommitMessageTarget?) {
        val writer = target ?: locateTarget()
        val fallback = heuristicMessage(prepared.plan, prepared.mode.id)
        val written = writer?.write(fallback.raw, WritePolicy.REPLACE)
        state.lastResult = LastGeneratedMessage(
            repoRoot = repository.root.path,
            previous = written?.previous ?: "",
            outcome = GenerationOutcome(
                message = fallback,
                prepared = prepared,
                durationMs = 0,
                payloadPreview = "",
            ),
            prepared = prepared,
            writtenToCommitPanel = written?.written == true,
        )
        state.pendingMessage = if (written?.written == true) null else fallback.raw
        state.notifyUi()
        success(
            "Local fallback message written from the changed file list.",
            if (written?.written == true) "No AI output was used. Review the message before committing."
            else "No AI output was used. Use Insert or Copy from the Commit Generator tool window.",
            buildList {
                if (written?.written == true) {
                    add("Undo" to { undoLast() })
                }
                add("Regenerate" to { regenerate() })
            },
        )
    }

    private fun handleThrowable(error: Throwable) {
        if (error is com.intellij.openapi.progress.ProcessCanceledException) {
            return
        }
        app.logger.error("unexpected generation failure", mapOf("type" to error.javaClass.simpleName))
        com.intellij.openapi.diagnostic.Logger.getInstance("Commit Generator")
            .error("Unexpected generation failure", error)
        val details = buildString {
            append("Unexpected error\n\n")
            append("Type: ${error.javaClass.name}\n")
            error.message?.let {
                append("Message: ${com.rajatmalik.commitgenerator.core.security.redactSecrets(it).text.take(500)}\n")
            }
            append("\nNo prompt, diff, header or credential is included in this report.")
        }
        error(
            "An unexpected error occurred: ${error.javaClass.simpleName}",
            actions = listOf(
                "Details" to { onEdt { TextPreviewDialog(project, "${Product.NAME}: error details", details).show() } },
            ),
        )
    }

    private fun locateTarget(): CommitMessageTarget? = onEdt { CommitMessageTarget.locate(project) }

    private fun chooseProfile(title: String): ProviderProfile? {
        val profiles = state.profiles()
        if (profiles.isEmpty()) {
            info("No provider profiles are configured. Add one first.")
            return null
        }
        if (profiles.size == 1) {
            return profiles[0]
        }
        val active = state.activeProfile()
        return onEdt {
            SelectionDialog(
                project,
                "${Product.NAME}: $title",
                "Choose a provider profile.",
                profiles,
                { "${it.label} — ${it.model}" },
                active,
            ).showAndGetItem()
        }
    }

    private fun cancellationWatcher(indicator: ProgressIndicator, token: CancellationToken): AutoCloseable {
        val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "Commit Generator-cancel-watcher").apply { isDaemon = true }
        }
        val future = executor.scheduleAtFixedRate(
            {
                if (indicator.isCanceled) {
                    token.cancel()
                }
            },
            100,
            100,
            TimeUnit.MILLISECONDS,
        )
        return AutoCloseable {
            future.cancel(true)
            executor.shutdownNow()
        }
    }

    private fun progressFraction(progress: SummaryProgress): Double = when (progress.phase) {
        com.rajatmalik.commitgenerator.core.ports.SummaryPhase.SUMMARIZING -> {
            val ratio = if (progress.total > 0) progress.completed.toDouble() / progress.total else 0.0
            0.35 + 0.5 * ratio
        }

        com.rajatmalik.commitgenerator.core.ports.SummaryPhase.AGGREGATING -> 0.88
    }

    private fun progressText(progress: SummaryProgress): String = when (progress.phase) {
        com.rajatmalik.commitgenerator.core.ports.SummaryPhase.SUMMARIZING ->
            "Summarizing ${progress.label} (${progress.completed}/${progress.total})"

        com.rajatmalik.commitgenerator.core.ports.SummaryPhase.AGGREGATING -> "Aggregating file summaries..."
    }

    private fun describe(error: AppError): String {
        val model = error.model?.let { "/$it" }.orEmpty()
        val provider = if (error.providerId != null && !error.message.contains(error.providerId)) {
            " (${error.providerId}$model)"
        } else {
            ""
        }
        return error.message + provider
    }

    private fun describeDetailed(error: AppError): String = buildString {
        append(error.message)
        append("\n\nCode: ${error.code.name}")
        error.httpStatus?.let { append("\nHTTP status: $it") }
        error.providerId?.let { append("\nProvider: $it") }
        error.model?.let { append("\nModel: $it") }
        error.requestId?.let { append("\nRequest id: $it") }
        error.cause?.let { append("\nDetail: $it") }
        append("\n\nNo prompt, diff, header or credential is included in this report.")
    }

    private fun copyToClipboard(text: String) {
        onEdt { CopyPasteManager.getInstance().setContents(StringSelection(text)) }
    }

    private fun lastIfAvailable(): LastGeneratedMessage? = state.lastResult

    private fun <T> onEdt(action: () -> T): T {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            return action()
        }
        val task = java.util.concurrent.FutureTask<T> { action() }
        application.invokeAndWait(task, ModalityState.any())
        return try {
            task.get()
        } catch (error: java.util.concurrent.ExecutionException) {
            throw error.cause ?: error
        }
    }

    private fun notify(type: NotificationType, title: String, content: String, actions: List<Pair<String, () -> Unit>> = emptyList()) {
        onEdt {
            val notification = NotificationGroupManager.getInstance()
                .getNotificationGroup(Product.NOTIFICATION_GROUP)
                .createNotification(content, type)
                .setTitle(title)
            for ((text, handler) in actions) {
                notification.addAction(object : NotificationAction(text) {
                    override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent, notification: Notification) {
                        handler()
                    }
                })
            }
            notification.notify(project)
        }
    }

    private fun info(message: String, actions: List<Pair<String, () -> Unit>> = emptyList()) =
        notify(NotificationType.INFORMATION, Product.NAME, message, actions)

    private fun success(title: String, content: String = "", actions: List<Pair<String, () -> Unit>> = emptyList()) =
        notify(NotificationType.INFORMATION, title, content, actions)

    private fun warning(message: String, content: String = "", actions: List<Pair<String, () -> Unit>> = emptyList()) =
        notify(NotificationType.WARNING, message, content, actions)

    private fun error(message: String, content: String = "", actions: List<Pair<String, () -> Unit>> = emptyList()) =
        notify(NotificationType.ERROR, message, content, actions)
}
