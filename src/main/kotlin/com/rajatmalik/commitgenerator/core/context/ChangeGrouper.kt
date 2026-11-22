package com.rajatmalik.commitgenerator.core.context

import com.rajatmalik.commitgenerator.core.domain.FileChange

data class ChangeGroup(
    val key: String,
    val label: String,
    val files: List<FileChange>,
    val categories: Map<FileCategory, Int>,
    val insertions: Int,
    val deletions: Int,
)

private val WORKSPACE_ROOTS = setOf("packages", "apps", "libs", "modules", "services", "crates", "plugins")

/**
 * Deterministic grouping: monorepo package first, otherwise the top-level
 * directory. Root-level files share a `(root)` group.
 */
fun groupKeyFor(path: String): String {
    val parts = path.replace('\\', '/').split('/')
    if (parts.size == 1) {
        return "(root)"
    }
    val first = parts.firstOrNull() ?: "(root)"
    if (first in WORKSPACE_ROOTS && parts.size > 2) {
        return "$first/${parts[1]}"
    }
    return first
}

fun groupChanges(files: List<FileChange>): List<ChangeGroup> {
    val buckets = LinkedHashMap<String, MutableList<FileChange>>()
    for (file in files) {
        buckets.getOrPut(groupKeyFor(file.path)) { mutableListOf() }.add(file)
    }

    return buckets.entries.sortedBy { it.key }.map { (key, groupFiles) ->
        val categories = mutableMapOf<FileCategory, Int>()
        var insertions = 0
        var deletions = 0
        for (file in groupFiles) {
            val classification = classifyFile(file.path, file.isBinary, file.hunks.map { it.lines })
            categories[classification.category] = (categories[classification.category] ?: 0) + 1
            insertions += file.insertions
            deletions += file.deletions
        }
        ChangeGroup(key, key, groupFiles.toList(), categories.toMap(), insertions, deletions)
    }
}
