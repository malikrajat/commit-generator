package com.rajatmalik.commitgenerator.core.security

private val SIGNALS = listOf(
    Regex("ignore (all )?(previous|prior|above) instructions", RegexOption.IGNORE_CASE),
    Regex("disregard (the )?(system|previous) prompt", RegexOption.IGNORE_CASE),
    Regex("you are now", RegexOption.IGNORE_CASE),
    Regex("new instructions:", RegexOption.IGNORE_CASE),
    Regex("system message:", RegexOption.IGNORE_CASE),
    Regex("reveal (your|the) (system )?prompt", RegexOption.IGNORE_CASE),
)

/**
 * Wraps diff content so the model can be told, unambiguously, that everything
 * inside is data. Content can never terminate the wrapper early.
 */
fun wrapUntrustedDiff(path: String, lines: List<String>): String {
    val safePath = path.replace(Regex("[\"<>\\r\\n]"), "_")
    val body = lines.joinToString("\n") { line ->
        line.replace(Regex("</?untrusted_diff", RegexOption.IGNORE_CASE), "[diff-tag]")
    }
    return "<untrusted_diff path=\"$safePath\">\n$body\n</untrusted_diff>"
}

/** Detection only; never alters the payload. Used for user-visible warnings. */
fun detectInjectionSignals(text: String): List<String> =
    SIGNALS.filter { it.containsMatchIn(text) }.map { it.pattern }
