package com.rajatmalik.commitgenerator.core.context

import com.rajatmalik.commitgenerator.core.domain.Budget
import com.rajatmalik.commitgenerator.core.domain.DiffPlan
import com.rajatmalik.commitgenerator.core.domain.FileChange
import com.rajatmalik.commitgenerator.core.domain.OmissionReason
import com.rajatmalik.commitgenerator.core.domain.OmittedFile
import com.rajatmalik.commitgenerator.core.domain.PlanStrategy
import com.rajatmalik.commitgenerator.core.domain.PromptSegment
import kotlin.math.ceil
import kotlin.math.floor

data class PlanInput(
    val files: List<FileChange>,
    val omitted: List<OmittedFile>,
    val budget: Budget,
    /** Tokens consumed by the system prompt, instructions and history samples. */
    val overheadTokens: Int,
    val manifestLimit: Int = 500,
)

private const val WRAPPER_OVERHEAD = 16
private const val MIN_USEFUL_LINES = 6

/**
 * Deterministic context planner. Guarantees:
 *  - the manifest is always present,
 *  - the estimated total never exceeds the mode budget,
 *  - every excluded or truncated file is reported.
 */
fun buildPlan(input: PlanInput): DiffPlan {
    val manifest = buildManifest(input.files.sortedBy { it.path }, input.manifestLimit)
    val manifestTokens = estimateTokens(manifest.joinToString("\n"))
    val omitted = input.omitted.toMutableList()

    val ranked = rankFiles(input.files)
    val candidates = ranked.take(input.budget.maxFiles)
    for (file in ranked.drop(input.budget.maxFiles)) {
        omitted += OmittedFile(file.path, OmissionReason.MAX_FILES)
    }

    var remaining = input.budget.maxDiffTokens - manifestTokens - input.overheadTokens
    val segments = mutableListOf<PromptSegment>()

    for (file in candidates) {
        if (remaining <= MIN_USEFUL_LINES * 4) {
            omitted += OmittedFile(file.path, OmissionReason.BUDGET)
            continue
        }

        val full = toSegment(file, input.budget.maxFileDiffLines)
        val fullCost = estimateLines(full.lines) + WRAPPER_OVERHEAD
        if (fullCost <= remaining) {
            segments += full
            remaining -= fullCost
            continue
        }

        val lineCount = full.lines.size
        val perLine = if (lineCount > 0) estimateLines(full.lines).toDouble() / lineCount else 4.0
        val allowedLines = floor(maxOf(0, remaining - WRAPPER_OVERHEAD) / perLine).toInt()
        if (allowedLines < MIN_USEFUL_LINES) {
            omitted += OmittedFile(file.path, OmissionReason.BUDGET)
            continue
        }

        val trimmed = trimLines(full.lines, allowedLines)
        val cost = estimateLines(trimmed.lines) + WRAPPER_OVERHEAD
        if (cost > remaining) {
            omitted += OmittedFile(file.path, OmissionReason.BUDGET)
            continue
        }
        segments += full.copy(
            lines = trimmed.lines,
            omittedLines = full.omittedLines + trimmed.omitted,
        )
        remaining -= cost
    }

    val diffTokens = segments.sumOf { estimateLines(it.lines) + WRAPPER_OVERHEAD }
    val estimatedTokens = manifestTokens + input.overheadTokens + diffTokens
    val truncated = segments.any { it.omittedLines > 0 }
    val dropped = omitted.any { it.reason == OmissionReason.BUDGET || it.reason == OmissionReason.MAX_FILES }
    val reduceNeeded = dropped || truncated
    val shouldReduce = input.budget.allowMapReduce &&
        (if (input.budget.forceMapReduce) input.files.size > 1 else reduceNeeded && input.files.size > 10)
    val strategy = if (shouldReduce) PlanStrategy.MAP_REDUCE else PlanStrategy.SINGLE_PASS
    val chunkCount = if (strategy == PlanStrategy.MAP_REDUCE) {
        maxOf(2, ceil(diffTokens.toDouble() / maxOf(1200, input.budget.maxDiffTokens / 3)).toInt())
    } else {
        1
    }

    return DiffPlan(
        strategy = strategy,
        segments = segments,
        omitted = omitted,
        manifest = manifest,
        estimatedTokens = estimatedTokens,
        manifestTokens = manifestTokens,
        budgetTokens = input.budget.maxDiffTokens,
        chunkCount = chunkCount,
    )
}

fun planText(plan: DiffPlan): String =
    plan.segments.joinToString("\n\n") { "${it.path}\n${segmentText(it)}" }
