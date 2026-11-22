package com.rajatmalik.commitgenerator.core.summary

import com.rajatmalik.commitgenerator.core.context.classifyPath
import com.rajatmalik.commitgenerator.core.domain.CancelSignal
import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.DiffPlan
import com.rajatmalik.commitgenerator.core.domain.ErrorCode
import com.rajatmalik.commitgenerator.core.domain.appError
import com.rajatmalik.commitgenerator.core.domain.cancelledError
import com.rajatmalik.commitgenerator.core.domain.err
import com.rajatmalik.commitgenerator.core.domain.ok
import com.rajatmalik.commitgenerator.core.message.buildChunkPrompt
import com.rajatmalik.commitgenerator.core.ports.SummaryPhase
import com.rajatmalik.commitgenerator.core.ports.SummaryProgress

const val SUMMARY_PROMPT_VERSION = 1

fun summaryCacheKey(modelKey: String, system: String, user: String): String =
    hashText("$SUMMARY_PROMPT_VERSION\u0000$modelKey\u0000$system\u0000$user")

private fun retryable(code: ErrorCode): Boolean =
    code == ErrorCode.INVALID_RESPONSE || code == ErrorCode.TIMEOUT ||
        code == ErrorCode.NETWORK || code == ErrorCode.RATE_LIMIT || code == ErrorCode.SERVER

private data class Aggregate(
    val groups: List<GroupSummary>,
    val consolidated: String,
)

private fun aggregate(
    plan: DiffPlan,
    chunkPlan: ChunkPlan,
    reports: List<SummaryChunkReport>,
): Aggregate {
    data class Bucket(
        val files: MutableList<String> = mutableListOf(),
        val texts: MutableList<String> = mutableListOf(),
        val notAnalyzed: MutableList<String> = mutableListOf(),
        var analyzed: Boolean = false,
    )

    val byGroup = LinkedHashMap<String, Bucket>()
    for (chunk in chunkPlan.chunks) {
        val report = reports.firstOrNull { it.index == chunk.index }
        val bucket = byGroup.getOrPut(chunk.groupKey) { Bucket() }
        bucket.files += chunk.files
        if (report != null && !report.text.isNullOrEmpty()) {
            bucket.texts += report.text
            bucket.analyzed = true
        } else {
            // Never let a failed chunk disappear from the consolidated summary.
            bucket.notAnalyzed += chunk.files
        }
    }

    val groups = byGroup.entries.sortedBy { it.key }.map { (key, value) ->
        val parts = mutableListOf<String>()
        if (value.texts.isNotEmpty()) {
            parts += value.texts.joinToString("\n")
        }
        if (value.notAnalyzed.isNotEmpty()) {
            parts += "[not analyzed: ${value.notAnalyzed.size} file${if (value.notAnalyzed.size == 1) "" else "s"} - " +
                "${value.notAnalyzed.take(5).joinToString(", ")}${if (value.notAnalyzed.size > 5) ", ..." else ""}]"
        }
        GroupSummary(
            key = key,
            files = value.files.sorted(),
            text = parts.joinToString("\n"),
            analyzed = value.analyzed,
            notAnalyzed = value.notAnalyzed.toList(),
        )
    }

    val files = plan.segments.map { it.path }.toSet().size
    val analyzedChunks = reports.filter { it.text != null }
    val analyzedFiles = analyzedChunks.sumOf { it.files.size }
    val header = "Analyzed $analyzedFiles of $files file${if (files == 1) "" else "s"} in " +
        "${groups.size} group${if (groups.size == 1) "" else "s"}."

    val sections = groups.map { group ->
        val body = group.text.ifEmpty { "[not analyzed: see limitations]" }
        "## ${group.key} (${group.files.size} file${if (group.files.size == 1) "" else "s"})\n$body"
    }

    return Aggregate(groups, (listOf(header) + sections).joinToString("\n\n"))
}

private fun limitationsFor(plan: DiffPlan, reports: List<SummaryChunkReport>, truncatedFiles: Int): List<String> {
    val limitations = mutableListOf<String>()
    val importantOmitted = plan.omitted.filter { classifyPath(it.path).important }
    if (importantOmitted.isNotEmpty()) {
        val sample = importantOmitted.take(5).joinToString(", ") { "${it.path} (${it.reason.name.lowercase()})" }
        limitations += "${importantOmitted.size} file${if (importantOmitted.size == 1) " was" else "s were"} not analyzed " +
            "because of the token budget or exclusion rules: $sample" +
            if (importantOmitted.size > 5) ", ..." else ""
    }

    val failed = reports.filter { it.text == null }
    if (failed.isNotEmpty()) {
        limitations += "${failed.size} chunk${if (failed.size == 1) "" else "s"} could not be summarized; " +
            "their files are listed but not described."
    }
    if (truncatedFiles > 0) {
        limitations += "$truncatedFiles file diff${if (truncatedFiles == 1) " was" else "s were"} truncated to fit the budget."
    }
    if (plan.omitted.isNotEmpty() && importantOmitted.isEmpty()) {
        limitations += "${plan.omitted.size} generated, binary or sensitive file(s) were excluded."
    }
    if (plan.segments.isEmpty()) {
        limitations += "No analyzable changes were found in the selected scope."
    }
    return limitations
}

/**
 * Map-reduce engine: chunk -> summarize (with retry and cache) -> aggregate.
 * Chunk failures never abort the run; they are recorded in coverage and
 * surfaced as user-visible limitations.
 */
fun summarizeChanges(
    plan: DiffPlan,
    model: SummaryModel,
    options: SummaryOptions,
    signal: CancelSignal,
): CwResult<ChangeSummary> {
    val chunkPlan = planSummaryChunks(plan, options.chunkTokens)
    val reports = mutableListOf<SummaryChunkReport>()
    val texts = mutableListOf<String>()

    fun report(completed: Int, label: String) {
        options.onProgress?.onProgress(
            SummaryProgress(SummaryPhase.SUMMARIZING, completed, chunkPlan.chunks.size, label),
        )
    }

    var completed = 0
    for (chunk in chunkPlan.chunks) {
        if (signal.isCancelled) {
            return err(cancelledError())
        }
        report(completed, chunk.groupKey)

        val user = buildChunkPrompt(chunk.segments, chunk.index, chunkPlan.chunks.size)
        val cacheKey = summaryCacheKey(model.cacheKey, options.system, user)
        val cached = options.cache?.get(cacheKey)
        if (!cached.isNullOrBlank()) {
            reports += SummaryChunkReport(
                index = chunk.index,
                groupKey = chunk.groupKey,
                files = chunk.files,
                tokens = chunk.tokens,
                text = cached,
                cached = true,
                attempts = 0,
            )
            texts += cached
            completed += 1
            report(completed, chunk.groupKey)
            continue
        }

        var attempts = 0
        var text: String? = null
        var lastError: com.rajatmalik.commitgenerator.core.domain.AppError? = null

        while (attempts < options.maxAttempts && !signal.isCancelled) {
            attempts += 1
            val result = model.summarize(
                SummaryModelRequest(options.system, user, options.maxTokens),
                signal,
            )
            when (result) {
                is CwResult.Ok -> {
                    val trimmed = result.value.trim()
                    if (trimmed.isNotEmpty()) {
                        text = trimmed
                    } else {
                        lastError = appError(ErrorCode.INVALID_RESPONSE, "The model returned an empty summary.")
                    }
                    break
                }

                is CwResult.Err -> {
                    lastError = result.error
                    if (result.error.cancelled) {
                        return err(result.error)
                    }
                    if (!retryable(result.error.code)) {
                        break
                    }
                }
            }
        }

        if (text != null) {
            options.cache?.set(cacheKey, text)
            reports += SummaryChunkReport(
                index = chunk.index,
                groupKey = chunk.groupKey,
                files = chunk.files,
                tokens = chunk.tokens,
                text = text,
                cached = false,
                attempts = attempts,
            )
            texts += text
        } else {
            reports += SummaryChunkReport(
                index = chunk.index,
                groupKey = chunk.groupKey,
                files = chunk.files,
                tokens = chunk.tokens,
                text = null,
                cached = false,
                attempts = attempts,
                errorCode = lastError?.code?.name,
                errorMessage = lastError?.message,
            )
        }
        completed += 1
        report(completed, chunk.groupKey)
    }

    options.onProgress?.onProgress(
        SummaryProgress(SummaryPhase.AGGREGATING, chunkPlan.chunks.size, chunkPlan.chunks.size, "aggregating"),
    )

    val aggregated = aggregate(plan, chunkPlan, reports)
    val failed = reports.filter { it.text == null }
    val analyzed = reports.filter { it.text != null }
    val truncatedFiles = plan.segments.count { it.omittedLines > 0 }

    return ok(
        ChangeSummary(
            chunks = reports,
            groups = aggregated.groups,
            texts = texts,
            consolidated = aggregated.consolidated,
            coverage = SummaryCoverage(
                files = plan.segments.map { it.path }.toSet().size + plan.omitted.size,
                analyzed = analyzed.sumOf { it.files.size },
                failed = failed.sumOf { it.files.size },
                omitted = plan.omitted.size,
                truncatedFiles = truncatedFiles,
            ),
            limitations = limitationsFor(plan, reports, truncatedFiles),
        ),
    )
}
