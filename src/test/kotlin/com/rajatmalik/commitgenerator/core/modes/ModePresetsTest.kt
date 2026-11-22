package com.rajatmalik.commitgenerator.core.modes

import com.rajatmalik.commitgenerator.core.domain.GenerationMode
import com.rajatmalik.commitgenerator.core.domain.ModeCeilings
import com.rajatmalik.commitgenerator.core.domain.ModeOverride
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModePresetsTest {
    @Test
    fun `mode ceilings cap diff and output tokens`() {
        val mode = resolveMode(
            GenerationMode.DETAILED,
            ModeOverride(maxDiffTokens = 999_999, maxOutputTokens = 99_999),
            ModeCeilings(maxDiffTokens = 6_000, contextWindow = 8_000),
        )
        assertEquals(4_000, mode.maxDiffTokens)
        assertEquals(1_536, mode.maxOutputTokens)
    }

    @Test
    fun `overrides may lower but not raise`() {
        val lowered = resolveMode(
            GenerationMode.SMART,
            ModeOverride(maxDiffTokens = 2_000, includeBody = false),
            ModeCeilings(200_000, 200_000),
        )
        assertEquals(2_000, lowered.maxDiffTokens)
        assertTrue(!lowered.includeBody)
    }

    @Test
    fun `request timeout is the smallest ceiling`() {
        assertEquals(5_000, resolveRequestTimeout(20_000, 15_000, 5_000))
        assertEquals(15_000, resolveRequestTimeout(60_000, 15_000, null))
        assertEquals(20_000, resolveRequestTimeout(20_000, 120_000, null))
    }
}
