package com.rajatmalik.commitgenerator.services.generation

import com.rajatmalik.commitgenerator.core.context.estimateTokens
import com.rajatmalik.commitgenerator.core.context.buildPlan
import com.rajatmalik.commitgenerator.core.context.PlanInput
import com.rajatmalik.commitgenerator.core.domain.Budget
import com.rajatmalik.commitgenerator.core.domain.CancelSignal
import com.rajatmalik.commitgenerator.core.domain.ChangeSet
import com.rajatmalik.commitgenerator.core.domain.CommitMessage
import com.rajatmalik.commitgenerator.core.domain.ContextSelection
import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.DiffPlan
import com.rajatmalik.commitgenerator.core.domain.ErrorCode
import com.rajatmalik.commitgenerator.core.domain.GenerationMode
import com.rajatmalik.commitgenerator.core.domain.MessageProvenance
import com.rajatmalik.commitgenerator.core.domain.MessageStrategy
import com.rajatmalik.commitgenerator.core.domain.ModePreset
import com.rajatmalik.commitgenerator.core.domain.ProviderProfile
import com.rajatmalik.commitgenerator.core.domain.ResolvedConfig
import com.rajatmalik.commitgenerator.core.domain.appError
import com.rajatmalik.commitgenerator.core.domain.err
import com.rajatmalik.commitgenerator.core.domain.ok
import com.rajatmalik.commitgenerator.core.domain.valueUnsafe
import com.rajatmalik.commitgenerator.core.message.ChangeBrief
import com.rajatmalik.commitgenerator.core.message.buildChangeBrief
import com.rajatmalik.commitgenerator.core.message.buildSynthesisPrompt
import com.rajatmalik.commitgenerator.core.message.buildSystemPrompt
import com.rajatmalik.commitgenerator.core.message.buildUserPrompt
import com.rajatmalik.commitgenerator.core.message.composeMessage
import com.rajatmalik.commitgenerator.core.message.ComposeInput
import com.rajatmalik.commitgenerator.core.message.PromptOptions
import com.rajatmalik.commitgenerator.core.message.applyTrailers
import com.rajatmalik.commitgenerator.core.message.issueKeyFromBranch
import com.rajatmalik.commitgenerator.core.message.TrailerOptions
import com.rajatmalik.commitgenerator.core.message.validateGrounding
import com.rajatmalik.commitgenerator.core.message.GroundingInput
import com.rajatmalik.commitgenerator.core.message.buildSummarySystemPrompt
import com.rajatmalik.commitgenerator.core.modes.resolveMode
import com.rajatmalik.commitgenerator.core.modes.resolveRequestTimeout
import com.rajatmalik.commitgenerator.core.security.detectInjectionSignals
import com.rajatmalik.commitgenerator.core.security.redactSecrets
import com.rajatmalik.commitgenerator.core.summary.ChangeSummary
import com.rajatmalik.commitgenerator.core.summary.SummaryCache
import com.rajatmalik.commitgenerator.core.summary.SummaryModel
import com.rajatmalik.commitgenerator.core.summary.SummaryModelRequest
import com.rajatmalik.commitgenerator.core.summary.SummaryOptions
import com.rajatmalik.commitgenerator.core.summary.summarizeChanges
import com.rajatmalik.commitgenerator.core.ports.AIProvider
import com.rajatmalik.commitgenerator.core.ports.ClockPort
import com.rajatmalik.commitgenerator.core.ports.CollectorOptions
import com.rajatmalik.commitgenerator.core.ports.ConfigPort
import com.rajatmalik.commitgenerator.core.ports.DiffCollector
import com.rajatmalik.commitgenerator.core.ports.GenerateRequest
import com.rajatmalik.commitgenerator.core.ports.GenerateResult
import com.rajatmalik.commitgenerator.core.ports.GitPort
import com.rajatmalik.commitgenerator.core.ports.LoggerPort
import com.rajatmalik.commitgenerator.core.ports.ProviderRegistryPort
import com.rajatmalik.commitgenerator.core.ports.SummaryProgressListener
import com.rajatmalik.commitgenerator.core.ports.Usage
import com.rajatmalik.commitgenerator.adapters.providers.resolveEffectiveProfile

data class RepoContext(
    val git: GitPort,
    val collector: DiffCollector,
)

data class PrepareOptions(
    val config: ResolvedConfig,
    val profile: ProviderProfile,
    val selection: ContextSelection,
    val allowSensitive: Boolean,
    /** Absolute paths checked in the Commit panel, or null for toggle-driven selection. */
    val explicitPaths: Set<String>? = null,
)

data class PreparedGeneration(
    val changeSet: ChangeSet,
    val plan: DiffPlan,
    val mode: ModePreset,
    val profile: ProviderProfile,
    val system: String,
    val user: String,
    val options: PromptOptions,
    val brief: ChangeBrief,
    val includeBody: Boolean,
    val injectionSignals: List<String>,
    val histories: List<String>,
)

data class GenerationOutcome(
    val message: CommitMessage,
    val prepared: PreparedGeneration,
    val usage: Usage? = null,
    val durationMs: Long,
    val payloadPreview: String,
    val summary: ChangeSummary? = null,
)

data class GenerateServiceDeps(
    val config: ConfigPort,
    val registry: ProviderRegistryPort,
    val logger: LoggerPort,
    val clock: ClockPort,
    val summaryCache: SummaryCache,
)

class GenerateCommitMessageService(private val deps: GenerateServiceDeps) {

    fun prepare(
        repo: RepoContext,
        options: PrepareOptions,
        signal: CancelSignal,
    ): CwResult<PreparedGeneration> {
        val effective = resolveEffectiveProfile(options.profile, options.config.privacy.localOnly)
        if (effective is CwResult.Err) {
            return effective
        }
        val profile = effective.valueUnsafe()
        val config = options.config

        if (options.explicitPaths == null && !options.selection.hasSelection()) {
            return err(
                appError(
                    ErrorCode.CONFIG,
                    "No change sources are selected. Enable staged, unstaged or untracked changes in Commit Generator settings.",
                ),
            )
        }

        val mode = resolveMode(
            config.mode,
            config.modeOverrides[config.mode],
            com.rajatmalik.commitgenerator.core.domain.ModeCeilings(
                config.context.maxDiffTokens,
                profile.contextWindow,
            ),
        )
        val budget = Budget(
            maxDiffTokens = mode.maxDiffTokens,
            maxFiles = minOf(mode.maxFiles, config.context.maxFiles),
            maxFileDiffLines = minOf(mode.maxFileDiffLines, config.context.maxFileDiffLines),
            allowMapReduce = mode.useMapReduce,
            forceMapReduce = mode.depth == com.rajatmalik.commitgenerator.core.domain.GenerationDepth.DEEP,
        )

        val collected = repo.collector.collect(
            CollectorOptions(
                selection = options.selection,
                maxFiles = budget.maxFiles,
                maxFileBytes = config.context.maxFileBytes,
                excludeGlobs = config.context.excludeGlobs,
                allowSensitive = options.allowSensitive,
                explicitPaths = options.explicitPaths,
            ),
            signal,
        )
        if (collected is CwResult.Err) {
            return collected
        }
        val changeSet = collected.valueUnsafe()
        if (changeSet.files.isEmpty()) {
            val scopeText = when {
                options.explicitPaths != null ->
                    "No changed files were found for the selection in the Commit panel."
                options.selection.includeStaged &&
                    !options.selection.includeUnstaged &&
                    !options.selection.includeUntracked ->
                    "No staged changes to describe."
                else -> "No changed files to describe."
            }
            val omissionText = if (changeSet.omitted.isNotEmpty()) {
                " ${changeSet.omitted.size} file${if (changeSet.omitted.size == 1) " was" else "s were"} " +
                    "excluded by ignore, size or sensitive-file rules."
            } else {
                ""
            }
            return err(appError(ErrorCode.NO_CHANGES, "$scopeText$omissionText"))
        }

        val historyLimit = if (mode.useStyleHistory && config.style.learnFromHistory) {
            minOf(mode.historySamples, config.style.historySamples)
        } else {
            0
        }
        val histories = if (historyLimit > 0) {
            when (val history = repo.git.recentSubjects(historyLimit, signal)) {
                is CwResult.Ok -> history.value
                is CwResult.Err -> {
                    if (history.error.cancelled) {
                        return history
                    }
                    deps.logger.warn("history unavailable", mapOf("code" to history.error.code.name))
                    emptyList()
                }
            }
        } else {
            emptyList()
        }

        val brief = buildChangeBrief(changeSet.files)
        val system = buildSystemPrompt(mode, config.conventional, config.output, histories, brief)
        val includeBody = mode.includeBody && config.output.includeBody
        val promptOptions = PromptOptions(includeBody, config.output.language, brief)
        val overheadTokens = estimateTokens(system) + 220

        val plan = buildPlan(
            PlanInput(
                files = changeSet.files,
                omitted = changeSet.omitted,
                budget = budget,
                overheadTokens = overheadTokens,
            ),
        )
        val user = buildUserPrompt(plan, promptOptions)

        return ok(
            PreparedGeneration(
                changeSet = changeSet,
                plan = plan,
                mode = mode,
                profile = profile,
                system = system,
                user = user,
                options = promptOptions,
                brief = brief,
                includeBody = includeBody,
                injectionSignals = detectInjectionSignals(user),
                histories = histories,
            ),
        )
    }

    fun execute(
        prepared: PreparedGeneration,
        signal: CancelSignal,
        onProgress: SummaryProgressListener? = null,
    ): CwResult<GenerationOutcome> {
        val started = deps.clock.now()
        val strategy = if (prepared.plan.strategy == com.rajatmalik.commitgenerator.core.domain.PlanStrategy.MAP_REDUCE) {
            MessageStrategy.MAP_REDUCE
        } else {
            MessageStrategy.SINGLE_PASS
        }

        var text: String
        var usage: Usage? = null
        var summary: ChangeSummary? = null

        if (strategy == MessageStrategy.MAP_REDUCE) {
            val reduced = runMapReduce(prepared, signal, onProgress)
            if (reduced is CwResult.Err) {
                return reduced
            }
            val reducedValue = reduced.valueUnsafe()
            text = reducedValue.text
            usage = reducedValue.usage
            summary = reducedValue.summary
        } else {
            val single = callProvider(prepared, prepared.user, signal)
            if (single is CwResult.Err) {
                return single
            }
            val singleValue = single.valueUnsafe()
            text = singleValue.text
            usage = singleValue.usage
        }

        val config = deps.config.read()
        val message = composeMessage(
            ComposeInput(
                raw = text,
                settings = config.conventional,
                includeBody = prepared.includeBody,
                maxSubjectLength = config.conventional.maxSubjectLength,
                provenance = MessageProvenance(
                    providerId = prepared.profile.provider.key,
                    model = prepared.profile.model,
                    strategy = strategy,
                    mode = prepared.mode.id,
                ),
            ),
        )

        if (message.subject.isBlank()) {
            return err(
                appError(
                    ErrorCode.INVALID_RESPONSE,
                    "The model did not return a usable commit message.",
                    providerId = prepared.profile.provider.key,
                    model = prepared.profile.model,
                ),
            )
        }

        val warnings = message.warnings.toMutableList()
        warnings += validateGrounding(
            GroundingInput(
                subject = message.subject,
                conventional = message.conventional,
                body = message.body,
                brief = prepared.brief,
            ),
        )
        if (prepared.injectionSignals.isNotEmpty()) {
            warnings += "diff content contained instruction-like text; it was treated as data"
        }
        val finalMessage = message.copy(warnings = warnings)

        val withTrailers = applyTrailers(
            finalMessage,
            TrailerOptions(
                issueKey = if (config.output.includeIssueKeyFromBranch) {
                    issueKeyFromBranch(prepared.changeSet.branch)
                } else {
                    null
                },
                attribution = config.output.attribution == com.rajatmalik.commitgenerator.core.domain.OutputAttribution.TRAILER,
            ),
        )

        return ok(
            GenerationOutcome(
                message = withTrailers,
                prepared = prepared,
                usage = usage,
                durationMs = deps.clock.now() - started,
                payloadPreview = redactSecrets("${prepared.system}\n\n${prepared.user}").text,
                summary = summary,
            ),
        )
    }

    fun payloadPreview(prepared: PreparedGeneration): String =
        redactSecrets("${prepared.system}\n\n${prepared.user}").text

    private data class ReducedResult(
        val text: String,
        val usage: Usage?,
        val summary: ChangeSummary,
    )

    private fun runMapReduce(
        prepared: PreparedGeneration,
        signal: CancelSignal,
        onProgress: SummaryProgressListener?,
    ): CwResult<ReducedResult> {
        var input = 0
        var output = 0

        val model = object : SummaryModel {
            override val cacheKey: String =
                "${prepared.profile.provider.key}:${prepared.profile.model}:${prepared.profile.dialect.key}"

            override fun summarize(request: SummaryModelRequest, signal: CancelSignal): CwResult<String> {
                val result = callProvider(prepared, request.user, signal, request.system, request.maxTokens)
                if (result is CwResult.Ok) {
                    input += result.value.usage?.input ?: 0
                    output += result.value.usage?.output ?: 0
                    return ok(result.value.text)
                }
                return result as CwResult.Err
            }
        }

        val contextAllowance = prepared.profile.contextWindow / 2
        val chunkTokens = maxOf(
            1_000,
            minOf(
                prepared.plan.budgetTokens / 3,
                contextAllowance - estimateTokens(prepared.system) - 400,
            ),
        )

        val summary = summarizeChanges(
            plan = prepared.plan,
            model = model,
            options = SummaryOptions(
                system = buildSummarySystemPrompt(),
                maxTokens = minOf(prepared.mode.maxOutputTokens, 1_024),
                chunkTokens = chunkTokens,
                maxAttempts = 2,
                cache = deps.summaryCache,
                onProgress = onProgress,
            ),
            signal = signal,
        )
        if (summary is CwResult.Err) {
            return summary
        }

        val summaryValue = summary.valueUnsafe()
        val complete = summaryValue.chunks.isNotEmpty() && summaryValue.texts.size == summaryValue.chunks.size
        val summaryTexts = when {
            summaryValue.chunks.isEmpty() -> emptyList()
            complete -> summaryValue.texts
            else -> listOf(summaryValue.consolidated)
        }

        val synthesis = callProvider(
            prepared,
            buildSynthesisPrompt(prepared.plan, summaryTexts, prepared.options),
            signal,
        )
        if (synthesis is CwResult.Err) {
            return synthesis
        }

        if (summaryValue.limitations.isNotEmpty()) {
            deps.logger.warn(
                "change summary incomplete",
                mapOf(
                    "limitations" to summaryValue.limitations.size,
                    "failedChunks" to summaryValue.coverage.failed,
                    "omitted" to summaryValue.coverage.omitted,
                ),
            )
        }

        val synthesisValue = synthesis.valueUnsafe()
        return ok(
            ReducedResult(
                text = synthesisValue.text,
                usage = Usage(
                    input + (synthesisValue.usage?.input ?: 0),
                    output + (synthesisValue.usage?.output ?: 0),
                ),
                summary = summaryValue,
            ),
        )
    }

    private fun callProvider(
        prepared: PreparedGeneration,
        user: String,
        signal: CancelSignal,
        system: String = prepared.system,
        maxOutputTokens: Int = prepared.mode.maxOutputTokens,
    ): CwResult<GenerateResult> {
        val config = deps.config.read()
        val provider: AIProvider = deps.registry.get(prepared.profile)

        fun buildRequest(model: String): GenerateRequest = GenerateRequest(
            model = model,
            system = system,
            user = user,
            maxOutputTokens = maxOutputTokens,
            temperature = minOf(prepared.mode.temperature, 0.3),
            timeoutMs = resolveRequestTimeout(
                prepared.mode.timeoutMs,
                config.provider.timeoutMs,
                prepared.profile.timeoutMs,
            ),
            maxRetries = minOf(prepared.mode.maxRetries, config.provider.maxRetries),
            signal = signal,
        )

        val first = runCatching { provider.generate(prepared.profile, buildRequest(prepared.profile.model)) }
            .getOrElse {
                return err(
                    appError(
                        ErrorCode.INTERNAL,
                        "The provider call failed unexpectedly.",
                        providerId = prepared.profile.provider.key,
                    ),
                )
            }
        if (first is CwResult.Ok || (first as CwResult.Err).error.cancelled) {
            return first
        }

        val firstError = (first as CwResult.Err).error
        val fallback = prepared.profile.fallbackModel
        if (!fallback.isNullOrBlank() && fallback != prepared.profile.model && firstError.retryable) {
            deps.logger.warn("primary model failed, trying fallback model", mapOf("code" to firstError.code.name))
            val second = runCatching {
                provider.generate(prepared.profile.copy(model = fallback), buildRequest(fallback))
            }.getOrNull()
            if (second is CwResult.Ok) {
                return second
            }
        }
        return first
    }
}
