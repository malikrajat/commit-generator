package com.rajatmalik.commitgenerator.core.security

private val ANSI = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")
private val CONTROL = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")
private val FENCE = Regex("^\\s*```.*$", setOf(RegexOption.MULTILINE))
private val GIT_SCISSORS = Regex("^\\s*# -{2,} ?>{2,} ?8\\s*$", setOf(RegexOption.MULTILINE))

/** Removes terminal escapes, control characters and markdown fences. */
fun sanitizeText(input: String): String = input
    .replace(ANSI, "")
    .replace(CONTROL, "")
    .replace(Regex("\\r\\n?"), "\n")
    .replace(FENCE, "")
    .replace(GIT_SCISSORS, "")

/** Normalizes a commit message: trimmed lines and at most one blank line in a row. */
fun sanitizeCommitText(input: String): String {
    val lines = sanitizeText(input).split('\n').map { it.replace(Regex("[ \\t]+$"), "") }
    val output = mutableListOf<String>()
    for (line in lines) {
        if (line.isBlank()) {
            if (output.isNotEmpty() && output.last() != "") {
                output += ""
            }
            continue
        }
        output += line
    }
    while (output.isNotEmpty() && output.last() == "") {
        output.removeAt(output.lastIndex)
    }
    return output.joinToString("\n")
}
