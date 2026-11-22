package com.rajatmalik.commitgenerator.core.context

import com.rajatmalik.commitgenerator.core.domain.FileChange
import com.rajatmalik.commitgenerator.core.domain.PromptSegment

data class TrimmedLines(
    val lines: List<String>,
    val omitted: Int,
)

/**
 * Keeps the head and tail of a diff, dropping the middle. Truncation is always
 * explicit: the omitted line count is reported to the user.
 */
fun trimLines(lines: List<String>, maxLines: Int): TrimmedLines {
    if (lines.size <= maxLines) {
        return TrimmedLines(lines, 0)
    }
    val keep = maxOf(4, maxLines)
    if (keep >= lines.size) {
        return TrimmedLines(lines, 0)
    }
    val head = keep / 2
    val tail = keep - head
    val omitted = lines.size - keep
    return TrimmedLines(
        listOf(
            *lines.take(head).toTypedArray(),
            "... $omitted lines omitted ...",
            *lines.takeLast(tail).toTypedArray(),
        ),
        omitted,
    )
}

fun toSegment(file: FileChange, maxLines: Int): PromptSegment {
    val flattened = file.hunks.flatMap { listOf(it.header) + it.lines }
    val trimmed = trimLines(flattened, maxLines)
    return PromptSegment(
        path = file.path,
        status = file.status,
        staged = file.staged,
        insertions = file.insertions,
        deletions = file.deletions,
        omittedLines = file.omittedLines + trimmed.omitted,
        lines = trimmed.lines,
    )
}

fun segmentText(segment: PromptSegment): String = segment.lines.joinToString("\n")
