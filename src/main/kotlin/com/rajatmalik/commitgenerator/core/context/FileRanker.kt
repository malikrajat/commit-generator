package com.rajatmalik.commitgenerator.core.context

import com.rajatmalik.commitgenerator.core.domain.ChangeStatus
import com.rajatmalik.commitgenerator.core.domain.FileChange

private val STATUS_WEIGHT: Map<ChangeStatus, Int> = mapOf(
    ChangeStatus.CONFLICTED to 8,
    ChangeStatus.ADDED to 6,
    ChangeStatus.UNTRACKED to 6,
    ChangeStatus.MODIFIED to 5,
    ChangeStatus.RENAMED to 4,
    ChangeStatus.COPIED to 3,
    ChangeStatus.DELETED to 3,
    ChangeStatus.TYPECHANGE to 1,
)

private val SOURCE_HINT = Regex(
    "\\.(ts|tsx|js|jsx|mjs|cjs|py|go|rs|java|kt|kts|cs|cpp|cc|c|h|hpp|rb|php|swift|scala|sh|ps1|sql|" +
        "vue|svelte|astro|lua|dart|ex|exs|erl|clj|hs|ml|nim|zig)$",
    RegexOption.IGNORE_CASE,
)
private val TEST_HINT = Regex("(^|/)(test|tests|__tests__|spec|e2e)/|\\.(test|spec)\\.[a-z]+$", RegexOption.IGNORE_CASE)
private val DOC_HINT = Regex("\\.(md|mdx|txt|rst|adoc)$", RegexOption.IGNORE_CASE)

private fun depth(path: String): Int = path.split('/').size

/**
 * Deterministic relevance score. Higher is more likely to carry the intent of
 * the change. Ties break on path so the plan is byte-identical across runs.
 */
fun scoreFile(file: FileChange): Double {
    var score = (STATUS_WEIGHT[file.status] ?: 1) * 10.0
    score += minOf(file.insertions + file.deletions, 300) / 15.0
    score -= depth(file.path) * 1.5
    if (SOURCE_HINT.containsMatchIn(file.path)) {
        score += 8
    }
    if (TEST_HINT.containsMatchIn(file.path)) {
        score -= 6
    }
    if (DOC_HINT.containsMatchIn(file.path)) {
        score -= 3
    }
    if (file.path.startsWith(".github/") || file.path.startsWith(".idea/")) {
        score -= 4
    }
    return score
}

fun rankFiles(files: List<FileChange>): List<FileChange> =
    files.sortedWith(compareByDescending<FileChange> { scoreFile(it) }.thenBy { it.path })
