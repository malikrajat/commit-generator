package com.rajatmalik.commitgenerator.core.security

data class RedactionResult(
    val text: String,
    val redactions: Int,
)

private data class RedactionRule(
    val name: String,
    val pattern: Regex,
    val replacement: String,
)

private val RULES: List<RedactionRule> = listOf(
    RedactionRule(
        "private-key",
        Regex("-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z0-9 ]*PRIVATE KEY-----"),
        "[REDACTED:private-key]",
    ),
    RedactionRule(
        "aws-access-key",
        Regex("\\b(?:AKIA|ASIA|AGPA|AIDA|AROA)[0-9A-Z]{16}\\b"),
        "[REDACTED:aws-key]",
    ),
    RedactionRule(
        "github-token",
        Regex("\\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{40,})\\b"),
        "[REDACTED:github-token]",
    ),
    RedactionRule(
        "openai-style-key",
        Regex("\\bsk-(?:ant-)?[A-Za-z0-9_-]{20,}\\b"),
        "[REDACTED:api-key]",
    ),
    RedactionRule(
        "slack-token",
        Regex("\\bxox[abprs]-[A-Za-z0-9-]{10,}\\b"),
        "[REDACTED:slack-token]",
    ),
    RedactionRule(
        "google-api-key",
        Regex("\\bAIza[0-9A-Za-z_-]{35}\\b"),
        "[REDACTED:google-key]",
    ),
    RedactionRule(
        "jwt",
        Regex("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b"),
        "[REDACTED:jwt]",
    ),
    RedactionRule(
        "bearer",
        Regex("(\\bBearer\\s+)[A-Za-z0-9\\-._~+/]{16,}=*", RegexOption.IGNORE_CASE),
        "$1[REDACTED:token]",
    ),
    RedactionRule(
        "assignment",
        Regex(
            "((?:[\"']?(?:api[_-]?key|secret[_-]?access[_-]?key|access[_-]?key|secret[_-]?key|" +
                "access[_-]?token|auth[_-]?token|client[_-]?secret|private[_-]?key|password|passwd|pwd)[\"']?)" +
                "\\s*[:=]\\s*['\"]?)[^\\s'\",;]{4,}",
            RegexOption.IGNORE_CASE,
        ),
        "$1[REDACTED:secret]",
    ),
    RedactionRule(
        "connection-string",
        Regex("\\b([a-z][a-z0-9+.-]*://[^\\s:/@]+:)([^\\s@/]{3,})(@)", RegexOption.IGNORE_CASE),
        "$1[REDACTED]@",
    ),
)

private val HIGH_ENTROPY = Regex("\\b[A-Za-z0-9+/_=-]{40,}={0,2}\\b")

private fun isMixedSecret(token: String): Boolean {
    if (token.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        return false // git SHAs and hex digests
    }
    if (token.all { it.isLetter() } || token.all { it.isDigit() }) {
        return false
    }
    val hasUpper = token.any { it.isUpperCase() }
    val hasLower = token.any { it.isLowerCase() }
    val hasDigit = token.any { it.isDigit() }
    val uniqueRatio = token.toSet().size.toDouble() / token.length
    return hasUpper && hasLower && hasDigit && uniqueRatio > 0.4
}

/**
 * Removes credential-shaped content from any text that is about to leave the
 * process or be persisted. Conservative by design: over-redaction is
 * acceptable, under-redaction is not.
 */
fun redactSecrets(input: String): RedactionResult {
    var text = input
    var redactions = 0

    for (rule in RULES) {
        val matches = rule.pattern.findAll(text).count()
        if (matches == 0) {
            continue
        }
        redactions += matches
        text = rule.pattern.replace(text, rule.replacement)
    }

    text = HIGH_ENTROPY.replace(text) { match ->
        if (isMixedSecret(match.value)) {
            redactions += 1
            "[REDACTED:high-entropy]"
        } else {
            match.value
        }
    }

    return RedactionResult(text, redactions)
}

fun containsSecretShape(input: String): Boolean = redactSecrets(input).redactions > 0
