package com.rajatmalik.commitgenerator.core.message

import com.rajatmalik.commitgenerator.core.domain.CommitMessage
import com.rajatmalik.commitgenerator.core.domain.ConventionalParts
import com.rajatmalik.commitgenerator.core.domain.DiffPlan
import com.rajatmalik.commitgenerator.core.domain.GenerationMode
import com.rajatmalik.commitgenerator.core.domain.MessageProvenance
import com.rajatmalik.commitgenerator.core.domain.MessageStrategy

private fun commonScope(paths: List<String>): String? {
    if (paths.isEmpty()) {
        return null
    }
    val segments = paths.map { it.split('/') }
    val first = segments.firstOrNull() ?: return null
    if (first.size < 2) {
        return null
    }
    val candidate = first[0]
    return if (segments.all { it.size >= 2 && it[0] == candidate }) candidate else null
}

/**
 * Deterministic, offline message used when the model output cannot be repaired.
 * It is offered to the user, never written automatically.
 */
fun heuristicMessage(plan: DiffPlan, mode: GenerationMode): CommitMessage {
    val fileCount = maxOf(plan.segments.size, plan.manifest.size, 1)
    val paths = plan.segments.map { it.path }
    val scope = commonScope(paths)
    val added = plan.segments.count { it.status.isAddition }
    val deleted = plan.segments.count { it.status == com.rajatmalik.commitgenerator.core.domain.ChangeStatus.DELETED }

    val type = if (added > 0 && added == fileCount) "feat" else "chore"
    val verb = when {
        added > 0 && added == fileCount -> "add"
        deleted > 0 && deleted == fileCount -> "remove"
        else -> "update"
    }
    val noun = if (fileCount == 1) "file" else "files"
    val head = scope?.let { "$type($it):" } ?: "$type:"
    val subject = "$head $verb $fileCount $noun"

    return CommitMessage(
        raw = subject,
        subject = subject,
        conventional = ConventionalParts(type, scope, false, "$verb $fileCount $noun"),
        warnings = listOf("generated locally from the file manifest (no AI output was used)"),
        provenance = MessageProvenance("heuristic", "manifest", MessageStrategy.HEURISTIC, mode),
    )
}
