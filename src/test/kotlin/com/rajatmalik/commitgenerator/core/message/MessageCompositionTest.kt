package com.rajatmalik.commitgenerator.core.message

import com.rajatmalik.commitgenerator.core.domain.ConventionalSettings
import com.rajatmalik.commitgenerator.core.domain.GenerationMode
import com.rajatmalik.commitgenerator.core.domain.MessageProvenance
import com.rajatmalik.commitgenerator.core.domain.MessageStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageCompositionTest {
    private val settings = ConventionalSettings()
    private val provenance = MessageProvenance("deepseek", "deepseek-chat", MessageStrategy.SINGLE_PASS, GenerationMode.SMART)

    @Test
    fun `parses repair validate and format a message`() {
        val raw = """
            Here is the commit message:
            ```
            **feat(auth): add token refresh.**
            ```
            Refresh tokens are rotated before they expire.

            BREAKING CHANGE: old refresh tokens are rejected
        """.trimIndent()

        val message = composeMessage(
            ComposeInput(
                raw = raw,
                settings = settings,
                includeBody = true,
                maxSubjectLength = 72,
                provenance = provenance,
            ),
        )

        assertEquals("feat(auth): add token refresh", message.subject)
        assertTrue(message.body!!.contains("Refresh tokens"))
        assertTrue(message.footers.any { it.startsWith("BREAKING CHANGE:") })
        assertTrue(message.raw.startsWith("feat(auth): add token refresh"))
        assertTrue(message.warnings.any { it.contains("removed") })
    }

    @Test
    fun `subject only mode drops body and footers`() {
        val message = composeMessage(
            ComposeInput(
                raw = "fix: correct null check\n\nBody text\n\nRefs: ABC-1",
                settings = settings,
                includeBody = false,
                maxSubjectLength = 72,
                provenance = provenance,
            ),
        )
        assertEquals("fix: correct null check", message.subject)
        assertEquals(null, message.body)
        assertTrue(message.footers.isEmpty())
    }

    @Test
    fun `subject is truncated at a word boundary`() {
        val raw = "feat: " + "a very long description ".repeat(12)
        val tight = settings.copy(maxSubjectLength = 50)
        val message = composeMessage(
            ComposeInput(raw, tight, includeBody = false, maxSubjectLength = 50, provenance = provenance),
        )
        assertTrue(message.subject.length <= 50)
        assertTrue(message.warnings.any { it.contains("shortened") })
    }

    @Test
    fun `empty output is reported`() {
        val message = composeMessage(ComposeInput("", settings, true, 72, provenance))
        assertEquals("", message.subject)
        assertTrue(message.warnings.any { it.contains("no usable") })
    }

    @Test
    fun `non conventional output is preserved with a warning`() {
        val message = composeMessage(ComposeInput("Updated the landing page", settings, true, 72, provenance))
        assertEquals("Updated the landing page", message.subject)
        assertTrue(message.warnings.any { it.contains("does not follow") })
    }
}
