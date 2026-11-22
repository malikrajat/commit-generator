package com.rajatmalik.commitgenerator.core.domain

data class ConventionalParts(
    val type: String,
    val scope: String? = null,
    val breaking: Boolean = false,
    val description: String,
)

enum class MessageStrategy {
    SINGLE_PASS,
    MAP_REDUCE,
    HEURISTIC,
}

data class MessageProvenance(
    val providerId: String,
    val model: String,
    val strategy: MessageStrategy,
    val mode: GenerationMode,
)

data class CommitMessage(
    val raw: String,
    val subject: String,
    val body: String? = null,
    val footers: List<String> = emptyList(),
    val conventional: ConventionalParts? = null,
    val warnings: List<String> = emptyList(),
    val provenance: MessageProvenance,
)

fun formatCommitMessage(message: CommitMessage): String {
    val parts = mutableListOf(message.subject)
    val body = message.body?.trim()
    if (!body.isNullOrEmpty()) {
        parts += ""
        parts += body
    }
    if (message.footers.isNotEmpty()) {
        parts += ""
        parts += message.footers
    }
    return parts.joinToString("\n")
}
