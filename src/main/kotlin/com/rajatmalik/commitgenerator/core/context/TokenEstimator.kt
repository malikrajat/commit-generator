package com.rajatmalik.commitgenerator.core.context

import kotlin.math.ceil

private const val CHARS_PER_TOKEN = 3.6
private const val MESSAGE_OVERHEAD = 4

/**
 * Provider-agnostic estimate. Deliberately conservative (chars / 3.6 rather
 * than chars / 4) plus fixed per-message overhead.
 */
fun estimateTokens(text: String): Int {
    if (text.isEmpty()) {
        return 0
    }
    return ceil(text.length / CHARS_PER_TOKEN).toInt() + MESSAGE_OVERHEAD
}

fun estimateLines(lines: List<String>): Int = estimateTokens(lines.joinToString("\n"))

fun formatTokens(tokens: Int): String =
    if (tokens >= 1000) String.format("%.1fk", tokens / 1000.0) else tokens.toString()
