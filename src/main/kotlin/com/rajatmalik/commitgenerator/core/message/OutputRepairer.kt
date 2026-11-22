package com.rajatmalik.commitgenerator.core.message

data class RepairResult(
    val message: ParsedMessage,
    val repairs: List<String>,
)

private val VERBOSE_PREFIX = Regex("^(here(?:'s| is)[^:]*:\\s*)", RegexOption.IGNORE_CASE)

/**
 * Mechanical fixes only: never invents content, only removes common model
 * decoration (preamble, code fences, quotes, trailing periods).
 */
fun repairMessage(parsed: ParsedMessage): RepairResult {
    val repairs = mutableListOf<String>()
    var subject = parsed.subject.trim()

    VERBOSE_PREFIX.find(subject)?.let { match ->
        subject = subject.substring(match.range.last + 1).trim()
        repairs += "removed preamble"
    }

    val hadFence = subject.startsWith("```") || subject.endsWith("```")
    subject = subject.replace(Regex("^```[a-z]*\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*```$"), "")
        .trim()
    if (hadFence) {
        repairs += "removed code fence"
    }

    val unquoted = subject.replace(Regex("^[\"'`]+|[\"'`]+$"), "").trim()
    if (unquoted != subject) {
        subject = unquoted
        repairs += "removed surrounding quotes"
    }

    if (Regex("[.!]$").containsMatchIn(subject) && !subject.endsWith("...")) {
        subject = subject.replace(Regex("[.!]+$"), "")
        repairs += "removed trailing punctuation"
    }

    val body = parsed.body
        ?.split('\n')
        ?.filterNot { it.trim().startsWith("```") }
        ?.joinToString("\n")
        ?.trim()

    return RepairResult(
        ParsedMessage(
            subject = subject,
            body = body?.ifEmpty { null },
            footers = parsed.footers,
        ),
        repairs,
    )
}
