package com.rajatmalik.commitgenerator.core.message

import com.rajatmalik.commitgenerator.core.security.sanitizeCommitText
import com.rajatmalik.commitgenerator.core.security.sanitizeText

data class ParsedMessage(
    val subject: String,
    val body: String? = null,
    val footers: List<String> = emptyList(),
)

private val SUBJECT = Regex("^([a-zA-Z][a-zA-Z0-9-]*)(?:\\(([^)]+)\\))?(!)?:\\s+(.+)$")
private val FOOTER = Regex(
    "^(BREAKING[ -]CHANGE|Refs?|Closes|Fixes|Resolves|See also|Co-authored-by|Signed-off-by|" +
        "Reviewed-by|[A-Z][A-Za-z-]+):\\s",
)
private val PREAMBLE = Regex(
    "^(here(?:'s| is)|sure[,!]?|certainly[,!]?|commit message:|proposed commit message:|the commit message)",
    RegexOption.IGNORE_CASE,
)

/**
 * Extracts a subject/body/footers triple from raw model output.
 * Never throws; returns `subject = ""` when nothing usable is present.
 */
fun parseMessage(raw: String, maxSubjectLength: Int = 72): ParsedMessage {
    val cleaned = sanitizeCommitText(sanitizeText(raw).removePrefix("\uFEFF"))
    if (cleaned.isBlank()) {
        return ParsedMessage("")
    }

    val lines = cleaned.split('\n')
    var subjectIndex = -1
    for (i in lines.indices) {
        val line = lines[i].trim()
            .replace(Regex("^\\*\\*|\\*\\*$"), "")
            .replace(Regex("^[\"'`]|[\"'`]$"), "")
        if (SUBJECT.matches(line) && !PREAMBLE.containsMatchIn(line)) {
            subjectIndex = i
            break
        }
        if (i >= 6) {
            break
        }
    }

    if (subjectIndex == -1) {
        val first = lines.firstOrNull { it.trim().isNotEmpty() }
            ?: return ParsedMessage("")
        return ParsedMessage(first.trim().take(maxSubjectLength))
    }

    val subject = lines[subjectIndex].trim()
        .replace(Regex("^\\*\\*|\\*\\*$"), "")
        .replace(Regex("^[\"'`]|[\"'`]$"), "")
    val bodyLines = mutableListOf<String>()
    val footerLines = mutableListOf<String>()
    var inFooters = false

    for (line in lines.drop(subjectIndex + 1)) {
        if (!inFooters && FOOTER.containsMatchIn(line.trim())) {
            inFooters = true
        }
        if (inFooters) {
            if (line.trim().isNotEmpty()) {
                footerLines += line.trim()
            }
        } else {
            bodyLines += line
        }
    }

    val body = bodyLines.joinToString("\n").trim()
    return ParsedMessage(
        subject = subject,
        body = body.ifEmpty { null },
        footers = footerLines,
    )
}

fun looksLikeSubject(line: String): Boolean = SUBJECT.matches(line.trim())

data class ConventionalSubjectParts(
    val type: String,
    val scope: String?,
    val breaking: Boolean,
    val description: String,
)

fun splitSubject(line: String): ConventionalSubjectParts? {
    val match = SUBJECT.matchEntire(line.trim()) ?: return null
    return ConventionalSubjectParts(
        type = match.groupValues[1],
        scope = match.groupValues[2].ifEmpty { null },
        breaking = match.groupValues[3] == "!",
        description = match.groupValues[4].trim(),
    )
}
