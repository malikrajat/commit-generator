package com.rajatmalik.commitgenerator.core.message

import com.rajatmalik.commitgenerator.core.Product
import com.rajatmalik.commitgenerator.core.domain.CommitMessage
import com.rajatmalik.commitgenerator.core.domain.formatCommitMessage

private val ISSUE_KEY = Regex("[A-Z][A-Z0-9]{1,9}-\\d+")

fun issueKeyFromBranch(branch: String?): String? {
    if (branch == null) {
        return null
    }
    return ISSUE_KEY.find(branch.uppercase())?.value
}

data class TrailerOptions(
    val issueKey: String? = null,
    val attribution: Boolean = false,
)

/**
 * Adds optional footers after validation. Pure and deterministic; never
 * rewrites the subject or body.
 */
fun applyTrailers(message: CommitMessage, options: TrailerOptions): CommitMessage {
    val footers = message.footers.toMutableList()

    options.issueKey?.let { issueKey ->
        val alreadyReferenced = message.subject.contains(issueKey) || footers.any { it.contains(issueKey) }
        if (!alreadyReferenced) {
            footers += "Refs: $issueKey"
        }
    }

    if (options.attribution && footers.none { it.startsWith("Generated-by:", ignoreCase = true) }) {
        footers += "Generated-by: ${Product.NAME}"
    }

    if (footers.size == message.footers.size) {
        return message
    }
    val updated = message.copy(footers = footers)
    return updated.copy(raw = formatCommitMessage(updated))
}
