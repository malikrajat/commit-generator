package com.rajatmalik.commitgenerator.core.domain

enum class OutputAttribution(val key: String) {
    NONE("none"),
    TRAILER("trailer");

    companion object {
        fun fromKey(value: String?): OutputAttribution = entries.firstOrNull { it.key == value } ?: NONE
    }
}

enum class OverwriteDraft(val key: String) {
    ASK("ask"),
    REPLACE("replace"),
    APPEND("append");

    companion object {
        fun fromKey(value: String?): OverwriteDraft = entries.firstOrNull { it.key == value } ?: ASK
    }
}

enum class LogLevel(val key: String) {
    OFF("off"),
    ERROR("error"),
    WARN("warn"),
    INFO("info"),
    DEBUG("debug");

    companion object {
        fun fromKey(value: String?): LogLevel = entries.firstOrNull { it.key == value } ?: WARN
    }
}

data class ConventionalSettings(
    val enabled: Boolean = true,
    val types: List<String> = listOf(
        "feat", "fix", "refactor", "perf", "docs", "test", "build", "ci", "chore", "style", "revert",
    ),
    val maxSubjectLength: Int = 72,
    val requireScope: Boolean = false,
    val allowedScopes: List<String> = emptyList(),
)

data class OutputSettings(
    val language: String = "en",
    val includeBody: Boolean = true,
    val includeIssueKeyFromBranch: Boolean = false,
    val attribution: OutputAttribution = OutputAttribution.NONE,
    val overwriteDraft: OverwriteDraft = OverwriteDraft.ASK,
)

data class ContextSettings(
    val includeStaged: Boolean = true,
    val includeUnstaged: Boolean = false,
    val includeUntracked: Boolean = false,
    val maxDiffTokens: Int = 12_000,
    val maxFiles: Int = 200,
    val maxFileBytes: Int = 262_144,
    val maxFileDiffLines: Int = 400,
    val excludeGlobs: List<String> = emptyList(),
) {
    fun selection(): ContextSelection = ContextSelection(includeStaged, includeUnstaged, includeUntracked)
}

data class StyleSettings(
    val learnFromHistory: Boolean = true,
    val historySamples: Int = 30,
)

data class ProviderSettings(
    val timeoutMs: Int = 120_000,
    val maxRetries: Int = 2,
)

data class PrivacySettings(
    val localOnly: Boolean = false,
)

data class ResolvedConfig(
    val mode: GenerationMode = GenerationMode.SMART,
    val modeOverrides: Map<GenerationMode, ModeOverride> = emptyMap(),
    val context: ContextSettings = ContextSettings(),
    val style: StyleSettings = StyleSettings(),
    val conventional: ConventionalSettings = ConventionalSettings(),
    val output: OutputSettings = OutputSettings(),
    val provider: ProviderSettings = ProviderSettings(),
    val privacy: PrivacySettings = PrivacySettings(),
    val statusBarEnabled: Boolean = true,
    val logLevel: LogLevel = LogLevel.WARN,
)
