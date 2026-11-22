package com.rajatmalik.commitgenerator.core.summary

import com.rajatmalik.commitgenerator.core.context.classifyPath
import com.rajatmalik.commitgenerator.core.context.estimateLines
import com.rajatmalik.commitgenerator.core.context.groupKeyFor
import com.rajatmalik.commitgenerator.core.domain.DiffPlan
import com.rajatmalik.commitgenerator.core.domain.PromptSegment

private fun categoriesOf(segments: List<PromptSegment>): List<com.rajatmalik.commitgenerator.core.context.FileCategory> =
    segments.map { classifyPath(it.path).category }.distinct()

/**
 * Deterministic chunking that keeps directory groups together where possible
 * and never exceeds the per-chunk token limit unless a single file is larger
 * (the budgeter has already trimmed it).
 */
fun planSummaryChunks(plan: DiffPlan, chunkTokens: Int): ChunkPlan {
    val limit = maxOf(500, chunkTokens)
    val ordered = plan.segments.sortedWith(
        compareBy<PromptSegment> { groupKeyFor(it.path) }.thenBy { it.path },
    )

    val chunks = mutableListOf<SummaryChunk>()
    var current = mutableListOf<PromptSegment>()
    var currentTokens = 0

    fun flush() {
        if (current.isEmpty()) {
            return
        }
        val key = groupKeyFor(current.firstOrNull()?.path ?: "")
        chunks += SummaryChunk(
            index = chunks.size,
            groupKey = key,
            segments = current.toList(),
            files = current.map { it.path },
            tokens = currentTokens,
            categories = categoriesOf(current),
        )
        current = mutableListOf()
        currentTokens = 0
    }

    for (segment in ordered) {
        val segmentTokens = estimateLines(segment.lines) + 24
        val currentGroup = current.firstOrNull()?.let { groupKeyFor(it.path) }
        val nextGroup = groupKeyFor(segment.path)
        if (current.isNotEmpty() && (currentGroup != nextGroup || currentTokens + segmentTokens > limit)) {
            flush()
        }
        current += segment
        currentTokens += segmentTokens
    }
    flush()

    return ChunkPlan(
        chunks = chunks,
        totalTokens = chunks.sumOf { it.tokens },
        tokenLimit = limit,
    )
}
