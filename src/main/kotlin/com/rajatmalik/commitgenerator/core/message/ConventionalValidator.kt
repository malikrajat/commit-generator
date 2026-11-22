package com.rajatmalik.commitgenerator.core.message

import com.rajatmalik.commitgenerator.core.domain.ConventionalParts
import com.rajatmalik.commitgenerator.core.domain.ConventionalSettings

data class ValidationInput(
    val subject: String,
    val body: String?,
    val footers: List<String>,
    val settings: ConventionalSettings,
)

data class ValidationResult(
    val subject: String,
    val conventional: ConventionalParts?,
    val warnings: List<String>,
    val valid: Boolean,
)

private fun truncateAtWord(subject: String, max: Int): String {
    if (subject.length <= max) {
        return subject
    }
    val cut = subject.take(max)
    val lastSpace = cut.lastIndexOf(' ')
    return (if (lastSpace > max * 0.6) cut.take(lastSpace) else cut).trimEnd()
}

/**
 * Enforces the Conventional Commits contract. Mechanical problems are repaired
 * (length, punctuation); semantic problems are reported as warnings.
 */
fun validateConventional(input: ValidationInput): ValidationResult {
    val warnings = mutableListOf<String>()
    val settings = input.settings

    if (!settings.enabled) {
        return ValidationResult(input.subject, null, emptyList(), input.subject.trim().isNotEmpty())
    }

    val parts = splitSubject(input.subject)
        ?: return ValidationResult(
            input.subject,
            null,
            listOf("subject does not follow \"type(scope): description\""),
            false,
        )

    var subject = input.subject.trim()
    val type = parts.type.lowercase()

    if (type !in settings.types) {
        warnings += "type \"$type\" is not in the configured type list"
    }
    if (settings.requireScope && parts.scope == null) {
        warnings += "a scope is required by configuration"
    }
    if (parts.scope != null && settings.allowedScopes.isNotEmpty() && parts.scope !in settings.allowedScopes) {
        warnings += "scope \"${parts.scope}\" is not in the configured scope list"
    }
    if (settings.maxSubjectLength > 0 && subject.length > settings.maxSubjectLength) {
        subject = truncateAtWord(subject, settings.maxSubjectLength)
        warnings += "subject shortened to ${settings.maxSubjectLength} characters"
    }

    val footerHasBreaking = input.footers.any { Regex("^BREAKING[ -]CHANGE:", RegexOption.IGNORE_CASE).containsMatchIn(it) }
    if (parts.breaking && !footerHasBreaking) {
        warnings += "subject is marked breaking but no \"BREAKING CHANGE:\" footer was produced"
    }
    if (!parts.breaking && footerHasBreaking) {
        warnings += "\"BREAKING CHANGE:\" footer is present but the subject is not marked with \"!\""
    }

    val firstWord = parts.description.split(Regex("\\s+")).firstOrNull() ?: ""
    if (Regex("(ed|ing)$", RegexOption.IGNORE_CASE).containsMatchIn(firstWord) && firstWord.length > 4) {
        warnings += "description may not be imperative (\"$firstWord\")"
    }
    if (parts.description.endsWith(".")) {
        subject = subject.replace(Regex("\\.+$"), "")
        warnings += "removed trailing period from subject"
    }

    val reparsed = splitSubject(subject)
    return ValidationResult(
        subject = subject,
        conventional = reparsed?.let {
            ConventionalParts(
                type = it.type.lowercase(),
                scope = it.scope,
                breaking = it.breaking,
                description = it.description,
            )
        },
        warnings = warnings,
        valid = warnings.isEmpty(),
    )
}
