package com.rajatmalik.commitgenerator.core.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiffNormalizerTest {
    private val diff = """
        diff --git a/src/App.kt b/src/App.kt
        index 1234567..89abcde 100644
        --- a/src/App.kt
        +++ b/src/App.kt
        @@ -1,4 +1,5 @@
         fun main() {
        -    println("old")
        +    println("new")
        +    println("extra")
         }
        \ No newline at end of file
    """.trimIndent()

    @Test
    fun `parses hunks and counts changes`() {
        val parsed = parseUnifiedDiff(diff)
        assertEquals(1, parsed.hunks.size)
        assertEquals(2, parsed.insertions)
        assertEquals(1, parsed.deletions)
        assertFalse(parsed.isBinary)
        assertEquals(5, parsed.hunks[0].newLines)
    }

    @Test
    fun `detects binary diffs`() {
        val parsed = parseUnifiedDiff("Binary files a/logo.png and b/logo.png differ")
        assertTrue(parsed.isBinary)
        assertEquals(0, parsed.hunks.size)
    }

    @Test
    fun `empty input is safe`() {
        assertEquals(0, parseUnifiedDiff("").hunks.size)
    }
}
