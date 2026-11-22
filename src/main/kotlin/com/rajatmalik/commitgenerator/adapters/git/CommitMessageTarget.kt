package com.rajatmalik.commitgenerator.adapters.git

import com.intellij.ide.DataManager
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
import java.awt.Dialog
import java.awt.Window
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap

/**
 * The only writer of the IDE commit message field.
 *
 * IntelliJ exposes two different commit UIs (modal dialog and non-modal commit
 * tool window). This class resolves the correct [CommitMessageI] from the action
 * data context when available, otherwise walks the Commit tool window and modal
 * dialogs. Traversal is iterative and bounded: a recursive walk of the entire
 * IDE component tree can overflow the stack in a large IDE.
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

    /** Data context of the commit panel component, used for its included changes. */
    fun dataContext(): DataContext? = onEdt {
        (target as? Component)?.let { DataManager.getInstance().getDataContext(it) }
    }

    companion object {
        private const val MAX_COMPONENTS = 25_000
        private const val MAX_EDITOR_COMPONENTS = 5_000

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
            // Non-modal Commit tool window.
            ToolWindowManager.getInstance(project).getToolWindow("Commit")
                ?.component
                ?.let { component -> findCommitMessage(component)?.let { return wrapLocated(it) } }

            // Modal commit dialog, or a commit dialog that is the active window.
            val active = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
            if (active is Dialog && active.isShowing) {
                findCommitMessage(active)?.let { return wrapLocated(it) }
            }
            for (window in Window.getWindows()) {
                if (window === active || !window.isShowing || window !is Dialog) {
                    continue
                }
                findCommitMessage(window)?.let { return wrapLocated(it) }
            }
            return null
        }

        private fun wrapLocated(component: Component): CommitMessageTarget {
            val control = component as CommitMessageI
            return CommitMessageTarget(control) {
                findEditor(component)?.document?.text
            }
        }

        /**
         * Iterative breadth-first search. The visited identity set protects
         * against unusual component graphs and the cap protects against very
         * large IDE component trees.
         */
        private fun findCommitMessage(root: Component): Component? {
            val queue = ArrayDeque<Component>()
            val visited = Collections.newSetFromMap(IdentityHashMap<Component, Boolean>())
            queue.add(root)
            var seen = 0
            while (queue.isNotEmpty() && seen < MAX_COMPONENTS) {
                val component = queue.removeFirst()
                if (!visited.add(component)) {
                    continue
                }
                seen += 1
                if (component is CommitMessageI) {
                    return component
                }
                if (component is Container) {
                    for (child in component.components) {
                        queue.addLast(child)
                    }
                }
            }
            return null
        }

        private fun findEditor(root: Component): EditorTextField? {
            val queue = ArrayDeque<Component>()
            val visited = Collections.newSetFromMap(IdentityHashMap<Component, Boolean>())
            queue.add(root)
            var seen = 0
            while (queue.isNotEmpty() && seen < MAX_EDITOR_COMPONENTS) {
                val component = queue.removeFirst()
                if (!visited.add(component)) {
                    continue
                }
                seen += 1
                if (component is EditorTextField) {
                    return component
                }
                if (component is Container) {
                    for (child in component.components) {
                        queue.addLast(child)
                    }
                }
            }
            return null
        }

        private fun <T> onEdt(action: () -> T): T {
            val application = ApplicationManager.getApplication()
            if (application.isDispatchThread) {
                return action()
            }
            val task = java.util.concurrent.FutureTask<T> { action() }
            application.invokeAndWait(task, ModalityState.any())
            return try {
                task.get()
            } catch (error: java.util.concurrent.ExecutionException) {
                throw error.cause ?: error
            }
        }
    }
}
