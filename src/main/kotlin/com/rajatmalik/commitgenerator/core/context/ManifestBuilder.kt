package com.rajatmalik.commitgenerator.core.context

import com.rajatmalik.commitgenerator.core.domain.FileChange
import com.rajatmalik.commitgenerator.core.domain.PromptSegment

fun manifestLine(file: FileChange): String {
    val letter = file.status.manifestLetter
    val staged = if (file.staged) "" else " (unstaged)"
    val renamed = file.oldPath?.let { " <- $it" } ?: ""
    return "$letter ${file.path} +${file.insertions} -${file.deletions}$renamed$staged"
}

/** Manifest line for a budgeted segment, used by chunk-scoped prompts. */
fun segmentManifestLine(segment: PromptSegment): String {
    val letter = segment.status.manifestLetter
    val staged = if (segment.staged) "" else " (unstaged)"
    return "$letter ${segment.path} +${segment.insertions} -${segment.deletions}$staged"
}

/**
 * The manifest is always sent, even when every diff is truncated, so the model
 * can never claim a change that does not exist.
 */
fun buildManifest(files: List<FileChange>, limit: Int = 500): List<String> {
    val included = files.take(limit).map(::manifestLine).toMutableList()
    if (files.size > limit) {
        included += "(+${files.size - limit} more files not listed)"
    }
    return included
}
