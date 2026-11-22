package com.rajatmalik.commitgenerator.core.domain

data class ContextSelection(
    val includeStaged: Boolean,
    val includeUnstaged: Boolean,
    val includeUntracked: Boolean,
) {
    fun hasSelection(): Boolean = includeStaged || includeUnstaged || includeUntracked

    fun label(): ChangeScope {
        if (includeStaged && (includeUnstaged || includeUntracked)) {
            return ChangeScope.ALL
        }
        if (includeStaged) {
            return ChangeScope.STAGED
        }
        return ChangeScope.UNSTAGED
    }
}

enum class DiffMode {
    STAGED,
    WORKING,
    HEAD,
    UNTRACKED,
}

enum class GenerationMode(val key: String, val title: String) {
    QUICK("quick", "Quick"),
    SMART("smart", "Smart"),
    DETAILED("detailed", "Detailed");

    companion object {
        fun fromKey(value: String?): GenerationMode =
            entries.firstOrNull { it.key == value?.lowercase() } ?: SMART
    }
}

enum class GenerationDepth {
    SINGLE_PASS,
    ADAPTIVE,
    DEEP,
}

data class ModeOverride(
    val maxDiffTokens: Int? = null,
    val maxFiles: Int? = null,
    val maxOutputTokens: Int? = null,
    val includeBody: Boolean? = null,
    val useMapReduce: Boolean? = null,
    val timeoutMs: Int? = null,
)

data class ModePreset(
    val id: GenerationMode,
    val depth: GenerationDepth,
    val maxDiffTokens: Int,
    val maxFiles: Int,
    val maxFileDiffLines: Int,
    val maxOutputTokens: Int,
    val includeBody: Boolean,
    val includeFooters: Boolean,
    val useMapReduce: Boolean,
    val useStyleHistory: Boolean,
    val historySamples: Int,
    val temperature: Double,
    val timeoutMs: Int,
    val maxRetries: Int,
)

data class ModeCeilings(
    val maxDiffTokens: Int,
    val contextWindow: Int,
)

data class Budget(
    val maxDiffTokens: Int,
    val maxFiles: Int,
    val maxFileDiffLines: Int,
    val allowMapReduce: Boolean,
    val forceMapReduce: Boolean = false,
)

data class PromptSegment(
    val path: String,
    val status: ChangeStatus,
    val staged: Boolean,
    val insertions: Int,
    val deletions: Int,
    val lines: List<String>,
    val omittedLines: Int,
)

enum class PlanStrategy {
    SINGLE_PASS,
    MAP_REDUCE,
}

data class DiffPlan(
    val strategy: PlanStrategy,
    val segments: List<PromptSegment>,
    val omitted: List<OmittedFile>,
    val manifest: List<String>,
    val estimatedTokens: Int,
    val manifestTokens: Int,
    val budgetTokens: Int,
    val chunkCount: Int,
)
