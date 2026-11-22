package com.rajatmalik.commitgenerator.core.summary

import com.rajatmalik.commitgenerator.core.domain.ChangeStatus
import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.DiffPlan
import com.rajatmalik.commitgenerator.core.domain.ErrorCode
import com.rajatmalik.commitgenerator.core.domain.NeverCancelled
import com.rajatmalik.commitgenerator.core.domain.PlanStrategy
import com.rajatmalik.commitgenerator.core.domain.valueUnsafe
import com.rajatmalik.commitgenerator.core.domain.PromptSegment
import com.rajatmalik.commitgenerator.core.domain.appError
import com.rajatmalik.commitgenerator.core.domain.err
import com.rajatmalik.commitgenerator.core.domain.ok
import com.rajatmalik.commitgenerator.core.ports.ClockPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SummaryTest {
    private val plan = DiffPlan(
        strategy = PlanStrategy.MAP_REDUCE,
        segments = listOf(
            PromptSegment("src/a/A.kt", ChangeStatus.MODIFIED, true, 3, 1, listOf("@@ -1 +1 @@", "+a"), 0),
            PromptSegment("lib/b/B.kt", ChangeStatus.MODIFIED, true, 2, 1, listOf("@@ -1 +1 @@", "+b"), 0),
        ),
        omitted = emptyList(),
        manifest = listOf("M src/a/A.kt +3 -1", "M lib/b/B.kt +2 -1"),
        estimatedTokens = 100,
        manifestTokens = 30,
        budgetTokens = 1_000,
        chunkCount = 2,
    )

    @Test
    fun `map reduce summarizes and aggregates`() {
        val model = object : SummaryModel {
            override val cacheKey = "test"
            override fun summarize(request: SummaryModelRequest, signal: com.rajatmalik.commitgenerator.core.domain.CancelSignal) =
                ok("summary: ${request.user.substringAfter("path=\"").substringBefore("\"")}")
        }
        val result = summarizeChanges(
            plan,
            model,
            SummaryOptions(system = "s", maxTokens = 100, chunkTokens = 500, maxAttempts = 1),
            NeverCancelled,
        )
        assertTrue(result is CwResult.Ok)
        assertEquals(2, result.valueUnsafe().chunks.size)
        assertTrue(result.valueUnsafe().consolidated.contains("Analyzed 2 of 2 files"))
        assertTrue(result.valueUnsafe().limitations.isEmpty())
    }

    @Test
    fun `failed chunks become limitations and remain visible`() {
        val model = object : SummaryModel {
            override val cacheKey = "test"
            override fun summarize(request: SummaryModelRequest, signal: com.rajatmalik.commitgenerator.core.domain.CancelSignal): CwResult<String> =
                err(appError(ErrorCode.AUTH, "no key"))
        }
        val result = summarizeChanges(
            plan,
            model,
            SummaryOptions(system = "s", maxTokens = 100, chunkTokens = 500, maxAttempts = 1),
            NeverCancelled,
        )
        assertTrue(result is CwResult.Ok)
        assertTrue(result.valueUnsafe().limitations.any { it.contains("could not be summarized") })
        assertTrue(result.valueUnsafe().consolidated.contains("not analyzed"))
    }

    @Test
    fun `cache respects ttl and lru`() {
        var now = 0L
        val clock = object : ClockPort {
            override fun now(): Long = now
        }
        val cache = SummaryCache(clock, maxEntries = 2, ttlMs = 1_000)
        cache.set("a", "1")
        cache.set("b", "2")
        assertEquals("1", cache.get("a"))
        cache.set("c", "3")
        assertEquals(null, cache.get("b"))
        now = 2_000
        assertEquals(null, cache.get("a"))
    }
}
