package com.rajatmalik.commitgenerator.core.context

import com.rajatmalik.commitgenerator.core.domain.DiffHunk

data class ParsedDiff(
    val hunks: List<DiffHunk>,
    val insertions: Int,
    val deletions: Int,
    val isBinary: Boolean,
)

private val HUNK_HEADER = Regex("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@ ?(.*)$")
private val BINARY_MARKER = Regex("GIT binary patch|^Binary files .* differ$", RegexOption.MULTILINE)

/**
 * Parses a unified diff into hunks. Tolerant of extra git headers
 * (`diff --git`, `index`, `---`, `+++`, rename metadata).
 */
fun parseUnifiedDiff(text: String): ParsedDiff {
    if (text.isBlank()) {
        return ParsedDiff(emptyList(), 0, 0, false)
    }

    val isBinary = BINARY_MARKER.containsMatchIn(text)
    val lines = text.split(Regex("\\r?\\n"))
    val hunks = mutableListOf<DiffHunk>()
    var insertions = 0
    var deletions = 0
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val match = HUNK_HEADER.matchEntire(line)
        if (match == null) {
            index += 1
            continue
        }

        val body = mutableListOf<String>()
        index += 1
        while (index < lines.size) {
            val bodyLine = lines[index]
            if (HUNK_HEADER.matches(bodyLine) || bodyLine.startsWith("diff --git ")) {
                break
            }
            if (bodyLine.startsWith("\\")) {
                index += 1
                continue
            }
            if (bodyLine.isEmpty() && index == lines.lastIndex) {
                break
            }
            body += bodyLine
            if (bodyLine.startsWith("+")) {
                insertions += 1
            } else if (bodyLine.startsWith("-")) {
                deletions += 1
            }
            index += 1
        }

        hunks += DiffHunk(
            header = line,
            oldStart = match.groupValues[1].toInt(),
            oldLines = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 1,
            newStart = match.groupValues[3].toInt(),
            newLines = match.groupValues[4].takeIf { it.isNotEmpty() }?.toInt() ?: 1,
            lines = body.toList(),
        )
    }

    return ParsedDiff(hunks, insertions, deletions, isBinary)
}
