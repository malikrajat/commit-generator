package com.rajatmalik.commitgenerator.adapters.git

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change

/**
 * The files checked in the Commit panel.
 *
 * JetBrains commit checkboxes mean "included in this commit", not "added to the
 * Git index". Reading the index alone therefore reports "no staged changes"
 * even when files are visibly checked. This adapter reads the commit workflow's
 * included changes/unversioned files through the public data key, using
 * reflection for the workflow UI type (the interface itself is marked internal).
 */
data class CommitSelection(
    val absolutePaths: Set<String>,
    val explicit: Boolean = true,
)

fun commitSelectionFrom(dataContext: DataContext?): CommitSelection? {
    if (dataContext == null) {
        return null
    }
    val workflowUi = runCatching {
        val key = VcsDataKeys::class.java
            .getField("COMMIT_WORKFLOW_UI")
            .get(null) as? DataKey<Any?>
        key?.getData(dataContext)
    }.getOrNull() ?: return null

    val changes = invokeList(workflowUi, "getIncludedChanges")
    val unversioned = invokeList(workflowUi, "getIncludedUnversionedFiles")
    if (changes == null && unversioned == null) {
        return null
    }

    val paths = linkedSetOf<String>()
    for (change in changes.orEmpty()) {
        val typed = change as? Change ?: continue
        typed.afterRevision?.file?.path?.let(paths::add)
        typed.beforeRevision?.file?.path?.let(paths::add)
    }
    for (entry in unversioned.orEmpty()) {
        val typed = entry as? FilePath ?: continue
        typed.path.let(paths::add)
    }
    return CommitSelection(paths, explicit = true)
}

fun commitSelectionFromCommitPanel(project: Project): CommitSelection? =
    CommitMessageTarget.locate(project)?.dataContext()?.let(::commitSelectionFrom)

private fun invokeList(target: Any, method: String): List<*>? = runCatching {
    target.javaClass.methods
        .firstOrNull { it.name == method && it.parameterCount == 0 }
        ?.apply { isAccessible = true }
        ?.invoke(target) as? List<*>
}.getOrNull()
