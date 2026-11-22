package com.rajatmalik.commitgenerator.core.context

import com.rajatmalik.commitgenerator.core.domain.Budget
import com.rajatmalik.commitgenerator.core.domain.ChangeStatus
import com.rajatmalik.commitgenerator.core.domain.DiffHunk
import com.rajatmalik.commitgenerator.core.domain.FileChange
import com.rajatmalik.commitgenerator.core.domain.OmissionReason
import com.rajatmalik.commitgenerator.core.domain.OmittedFile
import com.rajatmalik.commitgenerator.core.domain.PlanStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenBudgeterTest {
    private fun file(path: String, lines: Int, status: ChangeStatus = ChangeStatus.MODIFIED): FileChange {
        val hunkLines = (1..lines).map { "+line $it of reasonably long content" }
        return FileChange(
            path = path,
            status = status,
            staged = true,
            insertions = lines,
            deletions = 0,
            isBinary = false,
            hunks = listOf(
                DiffHunk(
                    header = "@@ -1,0 +1,$lines @@",
                    oldStart = 1,
                    oldLines = 0,
                    newStart = 1,
                    newLines = lines,
                    lines = hunkLines,
                ),
            ),
        )
    }

    @Test
    fun `manifest is always present and budget is explicit`() {
        val files = listOf(file("src/A.kt", 100), file("src/B.kt", 100), file("src/C.kt", 100))
        val plan = buildPlan(
            PlanInput(
                files = files,
                omitted = listOf(OmittedFile("secret/.env", OmissionReason.SENSITIVE)),
                budget = Budget(
                    maxDiffTokens = 900,
                    maxFiles = 3,
                    maxFileDiffLines = 100,
                    allowMapReduce = false,
                ),
                overheadTokens = 100,
            ),
        )
        assertEquals(3, plan.manifest.size)
        assertTrue(plan.segments.isNotEmpty())
        assertTrue(plan.omitted.any { it.reason == OmissionReason.BUDGET })
        assertTrue(plan.estimatedTokens <= 900)
        assertEquals(PlanStrategy.SINGLE_PASS, plan.strategy)
    }

    @Test
    fun `detailed mode can request map reduce`() {
        val files = (1..20).map { file("src/File$it.kt", 20) }
        val plan = buildPlan(
            PlanInput(
                files = files,
                omitted = emptyList(),
                budget = Budget(
                    maxDiffTokens = 2_000,
                    maxFiles = 20,
                    maxFileDiffLines = 20,
                    allowMapReduce = true,
                    forceMapReduce = true,
                ),
                overheadTokens = 200,
            ),
        )
        assertEquals(PlanStrategy.MAP_REDUCE, plan.strategy)
        assertTrue(plan.chunkCount >= 2)
    }

    @Test
    fun `max files omission is reported`() {
        val files = (1..5).map { file("src/File$it.kt", 2) }
        val plan = buildPlan(
            PlanInput(
                files = files,
                omitted = emptyList(),
                budget = Budget(12_000, maxFiles = 2, maxFileDiffLines = 100, allowMapReduce = false),
                overheadTokens = 100,
            ),
        )
        assertEquals(3, plan.omitted.count { it.reason == OmissionReason.MAX_FILES })
    }
}
