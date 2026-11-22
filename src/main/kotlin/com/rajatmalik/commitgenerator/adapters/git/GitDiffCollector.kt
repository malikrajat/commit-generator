package com.rajatmalik.commitgenerator.adapters.git

import com.rajatmalik.commitgenerator.core.context.PROTECTED_EXCLUDES
import com.rajatmalik.commitgenerator.core.context.matchesAny
import com.rajatmalik.commitgenerator.core.context.parseUnifiedDiff
import com.rajatmalik.commitgenerator.core.domain.CancelSignal
import com.rajatmalik.commitgenerator.core.domain.ChangeScope
import com.rajatmalik.commitgenerator.core.domain.ChangeSet
import com.rajatmalik.commitgenerator.core.domain.ChangeStatus
import com.rajatmalik.commitgenerator.core.domain.ChangeCounts
import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.DiffMode
import com.rajatmalik.commitgenerator.core.domain.ErrorCode
import com.rajatmalik.commitgenerator.core.domain.FileChange
import com.rajatmalik.commitgenerator.core.domain.OmissionReason
import com.rajatmalik.commitgenerator.core.domain.OmittedFile
import com.rajatmalik.commitgenerator.core.domain.Sensitivity
import com.rajatmalik.commitgenerator.core.domain.appError
import com.rajatmalik.commitgenerator.core.domain.err
import com.rajatmalik.commitgenerator.core.domain.ok
import com.rajatmalik.commitgenerator.core.ports.CollectorOptions
import com.rajatmalik.commitgenerator.core.ports.DiffCollector
import com.rajatmalik.commitgenerator.core.ports.GitPort
import com.rajatmalik.commitgenerator.core.ports.GitStatusEntry
import com.rajatmalik.commitgenerator.core.ports.LoggerPort
import com.rajatmalik.commitgenerator.core.ports.SelectedGitEntry
import com.rajatmalik.commitgenerator.core.security.classifySensitivePath

class GitDiffCollector(
    private val git: GitPort,
    private val logger: LoggerPort,
) : DiffCollector {
    override fun collect(options: CollectorOptions, signal: CancelSignal): CwResult<ChangeSet> {
        if (signal.isCancelled) {
            return err(appError(ErrorCode.CANCELLED, "Generation cancelled."))
        }
        val entries = when (val result = git.status()) {
            is CwResult.Ok -> result.value
            is CwResult.Err -> return result
        }
        val selection = if (options.explicitPaths != null) {
            selectExplicitEntries(entries, options.explicitPaths, git.info.rootPath)
        } else {
            selectEntries(entries, options.selection)
        }
        val globs = PROTECTED_EXCLUDES + options.excludeGlobs
        val omitted = mutableListOf<OmittedFile>()
        val accepted = mutableListOf<SelectedGitEntry>()
        val collectionCap = maxOf(1, options.maxFiles) * 2

        for (item in selection) {
            val matched = matchesAny(item.entry.path, globs)
            if (matched != null) {
                omitted += OmittedFile(item.entry.path, OmissionReason.GENERATED, "matched $matched")
                continue
            }
            val sensitivity = classifySensitivePath(item.entry.path)
            if (sensitivity.sensitivity == Sensitivity.BLOCKED && !options.allowSensitive) {
                omitted += OmittedFile(item.entry.path, OmissionReason.SENSITIVE, sensitivity.label)
                continue
            }
            if (accepted.size >= collectionCap) {
                omitted += OmittedFile(item.entry.path, OmissionReason.MAX_FILES)
                continue
            }
            accepted += item
        }

        val files = mutableListOf<FileChange>()
        for (item in accepted) {
            if (signal.isCancelled) {
                return err(appError(ErrorCode.CANCELLED, "Generation cancelled."))
            }
            when (val outcome = readOne(item, options)) {
                is ReadOutcome.File -> files += outcome.file
                is ReadOutcome.Omitted -> omitted += outcome.omitted
                is ReadOutcome.Failure -> {
                    if (outcome.error.cancelled) {
                        return err(outcome.error)
                    }
                    logger.warn("skipping unreadable change", mapOf("code" to outcome.error.code.name))
                }
            }
        }

        files.sortBy { it.path }
        val counts = ChangeCounts(
            files = files.size,
            insertions = files.sumOf { it.insertions },
            deletions = files.sumOf { it.deletions },
            staged = files.count { it.staged },
            untracked = files.count { it.status == ChangeStatus.UNTRACKED },
        )

        return ok(
            ChangeSet(
                repoRoot = git.info.rootPath,
                repoName = git.info.repoName,
                scope = if (options.explicitPaths != null) ChangeScope.ALL else options.selection.label(),
                head = git.info.head,
                branch = git.info.branch,
                files = files,
                omitted = omitted,
                counts = counts,
                truncatedFiles = 0,
                collectedAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Converts the absolute paths from the Commit panel into repository-relative
     * paths and diffs each matched file against HEAD. This includes both staged
     * and unstaged edits for the checked file, which is what a JetBrains commit
     * checkbox represents.
     */

    private fun readOne(item: SelectedGitEntry, options: CollectorOptions): ReadOutcome {
        val entry = item.entry
        val sensitivity = classifySensitivePath(entry.path)
        if (item.mode == DiffMode.UNTRACKED) {
            val read = when (val result = git.readUntracked(entry.path, options.maxFileBytes)) {
                is CwResult.Ok -> result.value
                is CwResult.Err -> {
                    return if (result.error.cancelled) {
                        ReadOutcome.Failure(result.error)
                    } else {
                        ReadOutcome.Omitted(OmittedFile(entry.path, OmissionReason.UNSUPPORTED, result.error.code.name))
                    }
                }
            }
            if (read.tooLarge) {
                return ReadOutcome.Omitted(OmittedFile(entry.path, OmissionReason.TOO_LARGE))
            }
            val parsed = parseUnifiedDiff(read.text)
            return ReadOutcome.File(
                buildFile(entry, item.mode, parsed.hunks, parsed.insertions, parsed.deletions, read.binary || parsed.isBinary, sensitivity),
            )
        }

        val text = when (val result = git.diff(entry.path, item.mode)) {
            is CwResult.Ok -> result.value
            is CwResult.Err -> {
                return if (result.error.cancelled) {
                    ReadOutcome.Failure(result.error)
                } else {
                    ReadOutcome.Omitted(OmittedFile(entry.path, OmissionReason.UNSUPPORTED, result.error.code.name))
                }
            }
        }
        if (text.length > options.maxFileBytes * 4) {
            return ReadOutcome.Omitted(OmittedFile(entry.path, OmissionReason.TOO_LARGE))
        }
        val parsed = parseUnifiedDiff(text)
        return ReadOutcome.File(
            buildFile(entry, item.mode, parsed.hunks, parsed.insertions, parsed.deletions, parsed.isBinary, sensitivity),
        )
    }

    private fun buildFile(
        entry: GitStatusEntry,
        mode: DiffMode,
        hunks: List<com.rajatmalik.commitgenerator.core.domain.DiffHunk>,
        insertions: Int,
        deletions: Int,
        isBinary: Boolean,
        sensitivity: com.rajatmalik.commitgenerator.core.security.SensitivityVerdict,
    ): FileChange {
        val extension = entry.path.substringAfterLast('.', "").takeIf { it.isNotEmpty() }
        return FileChange(
            path = entry.path,
            oldPath = entry.oldPath,
            status = entry.status,
            staged = entry.staged && mode != DiffMode.WORKING,
            insertions = insertions,
            deletions = deletions,
            isBinary = isBinary,
            hunks = hunks,
            omittedLines = 0,
            sensitivity = sensitivity.sensitivity,
            languageHint = extension?.takeIf { isSourceLike(entry.path) },
        )
    }

    private sealed interface ReadOutcome {
        data class File(val file: FileChange) : ReadOutcome
        data class Omitted(val omitted: OmittedFile) : ReadOutcome
        data class Failure(val error: com.rajatmalik.commitgenerator.core.domain.AppError) : ReadOutcome
    }
}
