package com.rajatmalik.commitgenerator.core.message

import com.rajatmalik.commitgenerator.core.context.FileCategory
import com.rajatmalik.commitgenerator.core.domain.ChangeStatus
import com.rajatmalik.commitgenerator.core.domain.ConventionalParts
import com.rajatmalik.commitgenerator.core.domain.DiffHunk
import com.rajatmalik.commitgenerator.core.domain.FileChange
import com.rajatmalik.commitgenerator.core.domain.FileChange as DomainFileChange
import com.rajatmalik.commitgenerator.core.domain.GenerationMode
import com.rajatmalik.commitgenerator.core.domain.MessageProvenance
import com.rajatmalik.commitgenerator.core.domain.MessageStrategy
import com.rajatmalik.commitgenerator.core.domain.OmittedFile
import com.rajatmalik.commitgenerator.core.domain.OmissionReason
import com.rajatmalik.commitgenerator.core.domain.PlanStrategy
import com.rajatmalik.commitgenerator.core.domain.PromptSegment
import com.rajatmalik.commitgenerator.core.domain.DiffPlan
import kotlin.test.Test
import kotlin.test.assertTrue

class GroundingAndFallbackTest {
    private fun file(path: String, status: ChangeStatus = ChangeStatus.MODIFIED) = DomainFileChange(
        path = path,
        status = status,
        staged = true,
        insertions = 5,
        deletions = 2,
        isBinary = false,
        hunks = listOf(DiffHunk("@@ -1 +1 @@", 1, 1, 1, 1, listOf("+x"))),
    )

    @Test
    fun `grounding flags paths outside the change`() {
        val brief = buildChangeBrief(listOf(file("src/auth/Token.kt")))
        val warnings = validateGrounding(
            GroundingInput(
                subject = "feat(auth): update src/auth/Token.kt and src/billing/Stripe.kt",
                conventional = ConventionalParts("feat", "auth", false, "update token"),
                body = null,
                brief = brief,
            ),
        )
        assertTrue(warnings.any { it.contains("src/billing/Stripe.kt") })
        assertTrue(warnings.none { it.contains("src/auth/Token.kt") })
    }

    @Test
    fun `grounding flags docs type without docs`() {
        val brief = buildChangeBrief(listOf(file("src/auth/Token.kt")))
        val warnings = validateGrounding(
            GroundingInput("docs: update authentication guide", ConventionalParts("docs", null, false, "x"), null, brief),
        )
        assertTrue(warnings.any { it.contains("no documentation") })
    }

    @Test
    fun `heuristic fallback is deterministic and scoped`() {
        val plan = DiffPlan(
            strategy = PlanStrategy.SINGLE_PASS,
            segments = listOf(
                PromptSegment("src/auth/A.kt", ChangeStatus.ADDED, true, 3, 0, listOf("+a"), 0),
                PromptSegment("src/auth/B.kt", ChangeStatus.ADDED, true, 2, 0, listOf("+b"), 0),
            ),
            omitted = listOf(OmittedFile("package-lock.json", OmissionReason.GENERATED)),
            manifest = listOf("A src/auth/A.kt +3 -0", "A src/auth/B.kt +2 -0"),
            estimatedTokens = 100,
            manifestTokens = 20,
            budgetTokens = 1000,
            chunkCount = 1,
        )
        val message = heuristicMessage(plan, GenerationMode.SMART)
        assertTrue(message.subject.startsWith("feat(src):"))
        assertTrue(message.warnings.isNotEmpty())
    }

    @Test
    fun `change brief counts categories and scopes`() {
        val brief = buildChangeBrief(
            listOf(file("src/auth/A.kt"), file("src/auth/A.test.kt"), file("docs/README.md", ChangeStatus.ADDED)),
        )
        assertTrue(brief.counts.files == 3)
        assertTrue(brief.paths.contains("src/auth/A.kt"))
        assertTrue(brief.scopeCandidates.contains("src"))
        assertTrue((brief.categories[FileCategory.TEST] ?: 0) == 1)
    }
}
