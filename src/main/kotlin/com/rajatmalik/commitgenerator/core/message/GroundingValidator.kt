package com.rajatmalik.commitgenerator.core.message

import com.rajatmalik.commitgenerator.core.domain.ConventionalParts

data class GroundingInput(
    val subject: String,
    val conventional: ConventionalParts?,
    val body: String?,
    val brief: ChangeBrief,
)

private val PATH_LIKE = Regex(
    "[A-Za-z0-9_@.-]+/[A-Za-z0-9_@./-]+|\\b[A-Za-z0-9_-]+\\.(?:ts|tsx|js|jsx|mjs|cjs|py|go|rs|java|kt|cs|" +
        "cpp|hpp|c|h|rb|php|swift|scala|sh|sql|vue|svelte|astro|md|mdx|json|ya?ml|toml|lock)\\b",
)

private fun stripSubjectPrefix(subject: String): String =
    subject.replace(Regex("^[a-zA-Z]+(?:\\([^)]+\\))?!?:\\s+"), "")

private fun referencesExistingPath(token: String, paths: List<String>): Boolean {
    val normalized = token.removePrefix("./").removeSuffix("/")
    return paths.any { path ->
        path == normalized ||
            path.endsWith("/$normalized") ||
            path.endsWith(normalized) ||
            path.startsWith("$normalized/")
    }
}

/**
 * Advisory checks that keep the message anchored to the actual change set.
 * Never rewrites content; returns human-readable warnings.
 */
fun validateGrounding(input: GroundingInput): List<String> {
    val warnings = mutableListOf<String>()
    val text = stripSubjectPrefix(input.subject) + "\n" + (input.body ?: "")
    val counts = input.brief.counts
    val categories = input.brief.categories
    val paths = input.brief.paths

    for (match in PATH_LIKE.findAll(text)) {
        val token = match.value
        if (!referencesExistingPath(token, paths)) {
            warnings += "message references \"$token\", which is not part of this change"
        }
    }

    if (input.conventional?.type == "docs" && (categories[com.rajatmalik.commitgenerator.core.context.FileCategory.DOCUMENTATION] ?: 0) == 0) {
        warnings += "type is \"docs\" but no documentation files changed"
    }
    if (input.conventional?.type == "test" && (categories[com.rajatmalik.commitgenerator.core.context.FileCategory.TEST] ?: 0) == 0) {
        warnings += "type is \"test\" but no test files changed"
    }
    if (input.conventional?.type == "perf" && counts.files == 0) {
        warnings += "type is \"perf\" but no changes were analyzed"
    }

    val description = stripSubjectPrefix(input.subject).lowercase()
    if (counts.insertions == 0 && counts.added == 0 && counts.deleted > 0 &&
        Regex("\\b(add|introduce|support|implement)\\b").containsMatchIn(description)
    ) {
        warnings += "message describes additions, but the change only deletes code"
    }
    if (counts.deletions == 0 && counts.deleted == 0 &&
        Regex("\\b(remove|delete|drop)\\b").containsMatchIn(description)
    ) {
        warnings += "message describes removals, but nothing was deleted"
    }
    if (input.conventional?.type == "feat" && counts.added == 0 && counts.insertions == 0 && counts.deleted > 0) {
        warnings += "type is \"feat\", but the change only removes code"
    }

    return warnings
}
