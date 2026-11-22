package com.rajatmalik.commitgenerator.adapters.git

import com.rajatmalik.commitgenerator.core.domain.ChangeStatus
import com.rajatmalik.commitgenerator.core.domain.ContextSelection
import com.rajatmalik.commitgenerator.core.domain.DiffMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitStatusParserTest {
    @Test
    fun `parses modifications untracked and renames`() {
        val output = "M  src/a.kt\u0000?? src/new.kt\u0000RM src/renamed.kt\u0000src/original.kt\u0000"
        val entries = parseGitStatus(output)

        assertEquals(3, entries.size)
        val modified = entries.first { it.path == "src/a.kt" }
        assertEquals(ChangeStatus.MODIFIED, modified.status)
        assertTrue(modified.staged)

        val untracked = entries.first { it.path == "src/new.kt" }
        assertEquals(ChangeStatus.UNTRACKED, untracked.status)
        assertTrue(untracked.untracked)

        val renamed = entries.first { it.path == "src/renamed.kt" }
        assertEquals(ChangeStatus.RENAMED, renamed.status)
        assertEquals("src/original.kt", renamed.oldPath)
        assertTrue(renamed.staged)
        assertTrue(renamed.unstaged)
    }

    @Test
    fun `parses conflicts`() {
        val entries = parseGitStatus("UU src/conflict.kt\u0000")
        assertEquals(ChangeStatus.CONFLICTED, entries[0].status)
        assertTrue(entries[0].conflicted)
    }

    @Test
    fun `selection respects independent toggles`() {
        val entries = parseGitStatus(
            "M  staged.kt\u0000 M unstaged.kt\u0000MM both.kt\u0000?? new.kt\u0000",
        )

        val staged = selectEntries(entries, ContextSelection(true, false, false))
        assertEquals(listOf("both.kt", "staged.kt"), staged.map { it.entry.path })
        assertTrue(staged.all { it.mode == DiffMode.STAGED })

        val unstaged = selectEntries(entries, ContextSelection(false, true, false))
        assertEquals(listOf("both.kt", "unstaged.kt"), unstaged.map { it.entry.path })
        assertTrue(unstaged.all { it.mode == DiffMode.WORKING })

        val all = selectEntries(entries, ContextSelection(true, true, true))
        assertEquals(DiffMode.HEAD, all.first { it.entry.path == "both.kt" }.mode)
        assertEquals(DiffMode.UNTRACKED, all.first { it.entry.path == "new.kt" }.mode)
    }
}
