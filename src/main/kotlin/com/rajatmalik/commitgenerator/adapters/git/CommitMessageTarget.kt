package com.rajatmalik.commitgenerator.adapters.git

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CommitMessageI
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.ui.Refreshable
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.EditorTextField
import com.rajatmalik.commitgenerator.core.Product
import com.rajatmalik.commitgenerator.core.ports.MessageWriterPort
import com.rajatmalik.commitgenerator.core.ports.WriteOutcome
import com.rajatmalik.commitgenerator.core.ports.WritePolicy
import java.awt.Component
import java.awt.Container
import java.awt.Window

/**
 * The only writer of the IDE commit message field.
 *
 * IntelliJ exposes two different commit UIs (modal dialog and non-modal commit
 * tool window). This class resolves the correct [CommitMessageI] from the action
 * data context when available, otherwise walks the open windows for a commit
 * message editor. It has no access to commit/stage/amend APIs.
 */
class CommitMessageTarget private constructor(
    private val target: CommitMessageI,
    private val readCurrent: () -> String?,
) : MessageWriterPort {

    override fun isAvailable(): Boolean = true

    override fun readCurrent(): String? = onEdt { readCurrent() }

    override fun write(message: String, policy: WritePolicy): WriteOutcome = onEdt {
        val previous = readCurrent() ?: ""
        val next = if (policy == WritePolicy.APPEND && previous.trim().isNotEmpty()) {
            "${previous.trimEnd()}\n\n${message.trim()}"
        } else {
            message.trim()
        }
        target.setCommitMessage(next)
        WriteOutcome(written = true, previous = previous)
    }

    override fun restore(previous: String) {
        onEdt { target.setCommitMessage(previous) }
    }

    companion object {
        fun fromDataContext(dataContext: DataContext?): CommitMessageTarget? {
            if (dataContext == null) {
                return null
            }
            val panel = Refreshable.PANEL_KEY.getData(dataContext) as? CommitMessageI
            val control = VcsDataKeys.COMMIT_MESSAGE_CONTROL.getData(dataContext)
            val target = control ?: panel ?: return null
            return CommitMessageTarget(target) {
                runCatching { VcsDataKeys.COMMIT_MESSAGE_DOCUMENT.getData(dataContext)?.text }.getOrNull()
            }
        }

        /**
         * Best-effort resolution for generations started from the toolbar or
         * Commit Generator tool window. Must be called on the EDT.
         */
        fun locate(project: Project): CommitMessageTarget? {
            val active = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
            if (active != null) {
                findCommitMessage(active)?.let { return wrapLocated(it) }
            }
            for (window in Window.getWindows()) {
                if (!window.isShowing) {
                    continue
                }
                findCommitMessage(window)?.let { return wrapLocated(it) }
            }
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Commit")
            toolWindow?.component?.let { component ->
                findCommitMessage(component)?.let { return wrapLocated(it) }
            }
            return null
        }

        private fun wrapLocated(component: Component): CommitMessageTarget {
            val control = component as CommitMessageI
            return CommitMessageTarget(control) {
                findEditor(component)?.document?.text
            }
        }

        private fun findCommitMessage(component: Component): Component? {
            if (component is CommitMessageI) {
                return component
            }
            if (component is Container) {
                for (child in component.components) {
                    findCommitMessage(child)?.let { return it }
                }
            }
            return null
        }

        private fun findEditor(component: Component): EditorTextField? {
            if (component is EditorTextField) {
                return component
            }
            if (component is Container) {
                for (child in component.components) {
                    findEditor(child)?.let { return it }
                }
            }
            return null
        }

        private fun <T> onEdt(action: () -> T): T {
            if (ApplicationManager.getApplication().isDispatchThread) {
                return action()
            }
            var result: Result<T>? = null
            ApplicationManager.getApplication().invokeAndWait(
                { result = runCatching(action) },
                ModalityState.any(),
            )
            return result?.getOrThrow() ?: error("${Product.NAME}: commit message update was cancelled.")
        }
    }
}
