package com.rajatmalik.commitgenerator.core.ports

import com.rajatmalik.commitgenerator.core.domain.CancelSignal
import com.rajatmalik.commitgenerator.core.domain.ChangeStatus
import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.DiffMode

data class GitRepositoryInfo(
    val rootPath: String,
    val repoName: String,
    val branch: String? = null,
    val head: String? = null,
)

data class GitStatusEntry(
    val path: String,
    val oldPath: String? = null,
    val status: ChangeStatus,
    val staged: Boolean,
    val unstaged: Boolean,
    val untracked: Boolean,
    val conflicted: Boolean,
)

data class SelectedGitEntry(
    val entry: GitStatusEntry,
    val mode: DiffMode,
)

data class UntrackedRead(
    val text: String,
    val binary: Boolean,
    val tooLarge: Boolean,
)

/**
 * Read-only Git access. Implementations must never stage, commit, restore or
 * amend anything: this port deliberately exposes no mutation methods.
 */
interface GitPort {
    val info: GitRepositoryInfo
    fun status(): CwResult<List<GitStatusEntry>>
    fun diff(path: String, mode: DiffMode): CwResult<String>
    fun readUntracked(path: String, maxBytes: Int): CwResult<UntrackedRead>
    fun recentSubjects(limit: Int, signal: CancelSignal): CwResult<List<String>>
}

data class CollectorOptions(
    val selection: com.rajatmalik.commitgenerator.core.domain.ContextSelection,
    val maxFiles: Int,
    val maxFileBytes: Int,
    val excludeGlobs: List<String>,
    val allowSensitive: Boolean,
)

interface DiffCollector {
    fun collect(options: CollectorOptions, signal: CancelSignal): CwResult<com.rajatmalik.commitgenerator.core.domain.ChangeSet>
}
