package com.rajatmalik.commitgenerator.core.domain

enum class ChangeStatus {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED,
    COPIED,
    TYPECHANGE,
    UNTRACKED,
    CONFLICTED;

    val manifestLetter: String
        get() = when (this) {
            ADDED, UNTRACKED -> "A"
            MODIFIED -> "M"
            DELETED -> "D"
            RENAMED -> "R"
            COPIED -> "C"
            TYPECHANGE -> "T"
            CONFLICTED -> "U"
        }

    val isAddition: Boolean
        get() = this == ADDED || this == UNTRACKED
}

enum class Sensitivity {
    NONE,
    SUSPICIOUS,
    BLOCKED,
}

data class DiffHunk(
    val header: String,
    val oldStart: Int,
    val oldLines: Int,
    val newStart: Int,
    val newLines: Int,
    val lines: List<String>,
)

data class FileChange(
    val path: String,
    val oldPath: String? = null,
    val status: ChangeStatus,
    val staged: Boolean,
    val insertions: Int,
    val deletions: Int,
    val isBinary: Boolean,
    val hunks: List<DiffHunk>,
    val omittedLines: Int = 0,
    val sensitivity: Sensitivity = Sensitivity.NONE,
    val languageHint: String? = null,
) {
    val changedLines: Int
        get() = insertions + deletions
}

enum class OmissionReason {
    BINARY,
    GENERATED,
    SENSITIVE,
    BUDGET,
    MAX_FILES,
    TOO_LARGE,
    UNSUPPORTED,
}

data class OmittedFile(
    val path: String,
    val reason: OmissionReason,
    val detail: String? = null,
)

enum class ChangeScope {
    STAGED,
    UNSTAGED,
    ALL,
}

data class ChangeCounts(
    val files: Int,
    val insertions: Int,
    val deletions: Int,
    val staged: Int,
    val untracked: Int,
)

data class ChangeSet(
    val repoRoot: String,
    val repoName: String,
    val scope: ChangeScope,
    val head: String?,
    val branch: String?,
    val files: List<FileChange>,
    val omitted: List<OmittedFile>,
    val counts: ChangeCounts,
    val truncatedFiles: Int,
    val collectedAt: Long,
)
