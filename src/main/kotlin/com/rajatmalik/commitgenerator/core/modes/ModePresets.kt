package com.rajatmalik.commitgenerator.core.modes

import com.rajatmalik.commitgenerator.core.domain.GenerationDepth
import com.rajatmalik.commitgenerator.core.domain.GenerationMode
import com.rajatmalik.commitgenerator.core.domain.ModeCeilings
import com.rajatmalik.commitgenerator.core.domain.ModeOverride
import com.rajatmalik.commitgenerator.core.domain.ModePreset

/**
 * Built-in ceilings. Overrides may only lower these numbers.
 */
val MODE_PRESETS: Map<GenerationMode, ModePreset> = mapOf(
    GenerationMode.QUICK to ModePreset(
        id = GenerationMode.QUICK,
        depth = GenerationDepth.SINGLE_PASS,
        maxDiffTokens = 2_500,
        maxFiles = 40,
        maxFileDiffLines = 120,
        maxOutputTokens = 96,
        includeBody = false,
        includeFooters = false,
        useMapReduce = false,
        useStyleHistory = false,
        historySamples = 0,
        temperature = 0.1,
        timeoutMs = 20_000,
        maxRetries = 1,
    ),
    GenerationMode.SMART to ModePreset(
        id = GenerationMode.SMART,
        depth = GenerationDepth.ADAPTIVE,
        maxDiffTokens = 12_000,
        maxFiles = 200,
        maxFileDiffLines = 400,
        maxOutputTokens = 512,
        includeBody = true,
        includeFooters = true,
        useMapReduce = true,
        useStyleHistory = true,
        historySamples = 30,
        temperature = 0.2,
        timeoutMs = 60_000,
        maxRetries = 2,
    ),
    GenerationMode.DETAILED to ModePreset(
        id = GenerationMode.DETAILED,
        depth = GenerationDepth.DEEP,
        maxDiffTokens = 48_000,
        maxFiles = 600,
        maxFileDiffLines = 1_200,
        maxOutputTokens = 1_536,
        includeBody = true,
        includeFooters = true,
        useMapReduce = true,
        useStyleHistory = true,
        historySamples = 50,
        temperature = 0.3,
        timeoutMs = 120_000,
        maxRetries = 2,
    ),
)

private fun lowerOf(base: Int, candidate: Int?): Int = candidate?.let { minOf(base, it) } ?: base

fun resolveMode(
    mode: GenerationMode,
    override: ModeOverride?,
    ceilings: ModeCeilings,
): ModePreset {
    val preset = MODE_PRESETS.getValue(mode)
    val contextCeiling = maxOf(500, ceilings.contextWindow / 2)
    val maxDiffTokens = minOf(
        lowerOf(preset.maxDiffTokens, override?.maxDiffTokens),
        ceilings.maxDiffTokens,
        contextCeiling,
    )
    return preset.copy(
        maxDiffTokens = maxDiffTokens.coerceAtLeast(500),
        maxFiles = lowerOf(preset.maxFiles, override?.maxFiles).coerceAtLeast(1),
        maxOutputTokens = lowerOf(preset.maxOutputTokens, override?.maxOutputTokens).coerceAtLeast(32),
        includeBody = if (override?.includeBody == false) false else preset.includeBody,
        useMapReduce = if (override?.useMapReduce == false) false else preset.useMapReduce,
        timeoutMs = lowerOf(preset.timeoutMs, override?.timeoutMs).coerceAtLeast(1),
    )
}

/**
 * Effective timeout = the smallest of the mode budget, the global ceiling and
 * an optional per-profile override.
 */
fun resolveRequestTimeout(modeTimeoutMs: Int, globalCeilingMs: Int, profileTimeoutMs: Int?): Int {
    val ceiling = minOf(modeTimeoutMs, globalCeilingMs)
    return profileTimeoutMs?.let { minOf(ceiling, it) } ?: ceiling
}
