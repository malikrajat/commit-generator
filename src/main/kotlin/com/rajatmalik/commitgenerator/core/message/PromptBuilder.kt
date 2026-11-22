package com.rajatmalik.commitgenerator.core.message

import com.rajatmalik.commitgenerator.core.context.segmentManifestLine
import com.rajatmalik.commitgenerator.core.domain.DiffPlan
import com.rajatmalik.commitgenerator.core.domain.PromptSegment
import com.rajatmalik.commitgenerator.core.security.wrapUntrustedDiff

data class PromptOptions(
    val includeBody: Boolean,
    val language: String,
    val brief: ChangeBrief? = null,
)

private fun manifestBlock(plan: DiffPlan): String =
    "<change_manifest>\n${plan.manifest.joinToString("\n")}\n</change_manifest>"

private fun overviewBlock(brief: ChangeBrief?): String? = brief?.let(::formatChangeBrief)

private fun segmentBlock(segment: PromptSegment): String =
    wrapUntrustedDiff(segment.path, segment.lines)

private fun closingInstruction(options: PromptOptions): String =
    if (options.includeBody) {
        "Write the commit message now. Subject first, optional body, optional BREAKING CHANGE footer."
    } else {
        "Write the commit subject now. One line only."
    }

fun buildUserPrompt(plan: DiffPlan, options: PromptOptions): String {
    val blocks = mutableListOf(manifestBlock(plan))
    overviewBlock(options.brief)?.let { blocks += it }
    blocks += plan.segments.map(::segmentBlock)
    blocks += closingInstruction(options)
    if (plan.omitted.isNotEmpty()) {
        blocks.add(
            1,
            "<omitted_files>\n${plan.omitted.joinToString("\n") { "${it.path} (${it.reason.name.lowercase().replace('_', '-')})" }}\n</omitted_files>",
        )
    }
    return blocks.joinToString("\n\n")
}

fun buildChunkPrompt(segments: List<PromptSegment>, chunkIndex: Int, chunkCount: Int): String {
    val chunkManifest = "<chunk_manifest>\n${segments.joinToString("\n") { segmentManifestLine(it) }}\n</chunk_manifest>"
    return buildString {
        append(chunkManifest)
        append("\n\n<chunk index=\"${chunkIndex + 1}\" of=\"$chunkCount\">\n\n")
        append(segments.joinToString("\n\n") { segmentBlock(it) })
        append("\n\n</chunk>\n\nSummarize each file in this chunk as \"path: what changed\".")
    }
}

fun buildSynthesisPrompt(
    plan: DiffPlan,
    summaries: List<String>,
    options: PromptOptions,
): String {
    val blocks = mutableListOf(manifestBlock(plan))
    overviewBlock(options.brief)?.let { blocks += it }
    blocks += "<file_summaries>\n${summaries.joinToString("\n")}\n</file_summaries>"
    blocks += closingInstruction(options)
    return blocks.joinToString("\n\n")
}
