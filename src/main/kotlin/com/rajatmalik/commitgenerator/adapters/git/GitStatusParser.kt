package com.rajatmalik.commitgenerator.adapters.git

import com.rajatmalik.commitgenerator.core.domain.ChangeStatus
import com.rajatmalik.commitgenerator.core.domain.ContextSelection
import com.rajatmalik.commitgenerator.core.domain.DiffMode
import com.rajatmalik.commitgenerator.core.ports.GitStatusEntry
import com.rajatmalik.commitgenerator.core.ports.SelectedGitEntry

/**
 * Parses `git status --porcelain=v1 -z --untracked-files=all` output.
 *
 * With `-z`, rename and copy entries are followed by the original path as a
 * separate NUL-terminated token: `XY <new path>\0<old path>\0`.
 */
fun parseGitStatus(output: String): List<GitStatusEntry> {
    val tokens = output.split('\u0000')
    val entries = mutableListOf<GitStatusEntry>()
    var index = 0
    while (index < tokens.size) {
        var token = tokens[index]
        index += 1
        if (token.isEmpty()) {
            continue
        }
        token = token.trimStart('\n', '\r')
        if (token.length < 3) {
            continue
        }
        val x = token[0]
        val y = token[1]
        if (x == '!' || (x == '?' && y != '?')) {
            continue
        }
        if (token[2] != ' ') {
            continue
        }
        val path = token.substring(3)
        if (path.isEmpty()) {
            continue
        }

        var oldPath: String? = null
        if (x == 'R' || x == 'C' || y == 'R' || y == 'C') {
            if (index < tokens.size) {
                oldPath = tokens[index].trimStart('\n', '\r').ifEmpty { null }
                index += 1
            }
        }

        val untracked = x == '?' && y == '?'
        val conflicted = "$x$y" in CONFLICT_PAIRS || x == 'U' || y == 'U'
        val status = when {
            untracked -> ChangeStatus.UNTRACKED
            conflicted -> ChangeStatus.CONFLICTED
            x == 'R' || y == 'R' -> ChangeStatus.RENAMED
            x == 'C' || y == 'C' -> ChangeStatus.COPIED
            x == 'A' || y == 'A' -> ChangeStatus.ADDED
            x == 'D' || y == 'D' -> ChangeStatus.DELETED
            x == 'T' || y == 'T' -> ChangeStatus.TYPECHANGE
            else -> ChangeStatus.MODIFIED
        }

        entries += GitStatusEntry(
            path = path,
            oldPath = oldPath,
            status = status,
            staged = !untracked && x != ' ' && x != '?',
            unstaged = !untracked && y != ' ' && y != '?',
            untracked = untracked,
            conflicted = conflicted,
        )
    }
    return entries.sortedBy { it.path }
}

private val CONFLICT_PAIRS = setOf("DD", "AU", "UD", "UA", "DU", "AA", "UU")

/**
 * Pure selection step driven by three independent user controls.
 *
 * When both staged and unstaged are enabled, a path present in both is diffed
 * against HEAD so a single patch covers both sides.
 */
fun selectEntries(entries: List<GitStatusEntry>, selection: ContextSelection): List<SelectedGitEntry> {
    val selected = LinkedHashMap<String, SelectedGitEntry>()

    if (selection.includeStaged) {
        for (entry in entries.filter { it.staged }) {
            selected[entry.path] = SelectedGitEntry(entry, DiffMode.STAGED)
        }
    }

    if (selection.includeUnstaged) {
        for (entry in entries.filter { it.unstaged || it.conflicted }) {
            val mode = if (entry.staged && selection.includeStaged) DiffMode.HEAD else DiffMode.WORKING
            selected[entry.path] = SelectedGitEntry(entry, mode)
        }
    }

    if (selection.includeUntracked) {
        for (entry in entries.filter { it.untracked }) {
            selected.putIfAbsent(entry.path, SelectedGitEntry(entry, DiffMode.UNTRACKED))
        }
    }

    return selected.values.sortedBy { it.entry.path }
}
