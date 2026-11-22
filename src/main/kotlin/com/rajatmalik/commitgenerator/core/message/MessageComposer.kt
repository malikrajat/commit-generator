package com.rajatmalik.commitgenerator.core.message

import com.rajatmalik.commitgenerator.core.domain.CommitMessage
import com.rajatmalik.commitgenerator.core.domain.ConventionalSettings
import com.rajatmalik.commitgenerator.core.domain.MessageProvenance
import com.rajatmalik.commitgenerator.core.domain.formatCommitMessage

data class ComposeInput(
    val raw: String,
    val settings: ConventionalSettings,
    val includeBody: Boolean,
    val maxSubjectLength: Int,
    val provenance: MessageProvenance,
)

const val EMPTY_MESSAGE_WARNING = "the model returned no usable commit message"
private const val MAX_BODY_LINES = 20

/**
 * Pure composition step: parse -> repair -> validate. No IO, fully testable.
 */
fun composeMessage(input: ComposeInput): CommitMessage {
    val parsed = parseMessage(input.raw, input.maxSubjectLength)
    val repaired = repairMessage(parsed)

    var body = if (input.includeBody) repaired.message.body else null
    var bodyTruncated = false
    if (body != null) {
        val lines = body.split('\n')
        if (lines.size > MAX_BODY_LINES) {
            body = lines.take(MAX_BODY_LINES).joinToString("\n")
            bodyTruncated = true
        }
    }
    val footers = if (input.includeBody) repaired.message.footers else emptyList()

    val validation = validateConventional(
        ValidationInput(
            subject = repaired.message.subject,
            body = body,
            footers = footers,
            settings = input.settings,
        ),
    )

    val warnings = mutableListOf<String>()
    warnings += repaired.repairs.map { "output repaired: $it" }
    warnings += validation.warnings
    if (bodyTruncated) {
        warnings += "body truncated to $MAX_BODY_LINES lines to keep the message concise"
    }
    if (repaired.message.subject.isBlank()) {
        warnings += EMPTY_MESSAGE_WARNING
    }

    val message = CommitMessage(
        raw = "",
        subject = validation.subject,
        body = body,
        footers = footers,
        conventional = validation.conventional,
        warnings = warnings,
        provenance = input.provenance,
    )
    return message.copy(raw = formatCommitMessage(message))
}
