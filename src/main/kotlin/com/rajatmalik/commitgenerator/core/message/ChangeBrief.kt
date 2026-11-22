package com.rajatmalik.commitgenerator.core.message

import com.rajatmalik.commitgenerator.core.context.FileCategory
import com.rajatmalik.commitgenerator.core.context.classifyFile
import com.rajatmalik.commitgenerator.core.context.groupChanges
import com.rajatmalik.commitgenerator.core.domain.FileChange

data class BriefGroup(
    val key: String,
    val files: List<String>,
    val insertions: Int,
    val deletions: Int,
    val categories: List<FileCategory>,
)

data class ChangeCounts(
    val files: Int,
    val insertions: Int,
    val deletions: Int,
    val added: Int,
    val modified: Int,
    val deleted: Int,
    val renamed: Int,
    val tests: Int,
    val docs: Int,
)

data class ChangeBrief(
    val primary: BriefGroup?,
    val secondary: List<BriefGroup>,
    /** Every analyzed path, used for grounding checks. */
    val paths: List<String>,
    /** Conventional Commit scope candidates derived from top-level areas. */
    val scopeCandidates: List<String>,
    val counts: ChangeCounts,
    val categories: Map<FileCategory, Int>,
)

private fun groupScore(files: List<FileChange>, insertions: Int, deletions: Int): Double {
    val important = files.count {
        classifyFile(it.path, it.isBinary, it.hunks.map { hunk -> hunk.lines }).important
    }
    return important * 20.0 + insertions + deletions
}

private fun scopeForGroup(key: String): String? {
    if (key == "(root)") {
        return null
    }
    return key.substringAfterLast('/').replace(Regex("[^a-zA-Z0-9._-]"), "")
}

fun buildChangeBrief(files: List<FileChange>): ChangeBrief {
    val groups = groupChanges(files)
    val summaries = groups.map { group ->
        BriefGroup(
            key = group.key,
            files = group.files.map { it.path }.sorted(),
            insertions = group.insertions,
            deletions = group.deletions,
            categories = group.categories.keys.sortedBy { it.key },
        )
    }

    val ranked = groups.mapIndexed { index, group ->
        summaries[index] to groupScore(group.files, group.insertions, group.deletions)
    }.sortedWith(
        compareByDescending<Pair<BriefGroup, Double>> { it.second }
            .thenBy { it.first.key },
    ).map { it.first }

    val categories = mutableMapOf<FileCategory, Int>()
    var insertions = 0
    var deletions = 0
    var added = 0
    var modified = 0
    var deleted = 0
    var renamed = 0
    var tests = 0
    var docs = 0

    for (file in files) {
        val classification = classifyFile(file.path, file.isBinary, file.hunks.map { it.lines })
        categories[classification.category] = (categories[classification.category] ?: 0) + 1
        insertions += file.insertions
        deletions += file.deletions
        if (file.status.isAddition) {
            added += 1
        }
        when (file.status) {
            com.rajatmalik.commitgenerator.core.domain.ChangeStatus.MODIFIED -> modified += 1
            com.rajatmalik.commitgenerator.core.domain.ChangeStatus.DELETED -> deleted += 1
            com.rajatmalik.commitgenerator.core.domain.ChangeStatus.RENAMED -> renamed += 1
            else -> Unit
        }
        if (classification.category == FileCategory.TEST) {
            tests += 1
        }
        if (classification.category == FileCategory.DOCUMENTATION) {
            docs += 1
        }
    }

    val scopeCandidates = summaries.mapNotNull { scopeForGroup(it.key) }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(8)

    return ChangeBrief(
        primary = ranked.firstOrNull(),
        secondary = ranked.drop(1).take(5),
        paths = files.map { it.path }.sorted(),
        scopeCandidates = scopeCandidates,
        counts = ChangeCounts(
            files = files.size,
            insertions = insertions,
            deletions = deletions,
            added = added,
            modified = modified,
            deleted = deleted,
            renamed = renamed,
            tests = tests,
            docs = docs,
        ),
        categories = categories,
    )
}

fun formatChangeBrief(brief: ChangeBrief): String {
    val lines = mutableListOf("<change_overview>")
    brief.primary?.let { primary ->
        lines += "Primary area: ${primary.key} (${primary.files.size} file${if (primary.files.size == 1) "" else "s"}, " +
            "+${primary.insertions} -${primary.deletions})"
    }
    if (brief.secondary.isNotEmpty()) {
        lines += "Secondary areas: ${brief.secondary.joinToString(", ") { it.key }}"
    }
    lines += "Change kinds: added ${brief.counts.added}, modified ${brief.counts.modified}, " +
        "deleted ${brief.counts.deleted}, renamed ${brief.counts.renamed}"
    if (brief.scopeCandidates.isNotEmpty()) {
        lines += "Scope candidates: ${brief.scopeCandidates.joinToString(", ")}"
    }
    lines += "Only these areas and the files in <change_manifest> may be named in the message."
    lines += "</change_overview>"
    return lines.joinToString("\n")
}
