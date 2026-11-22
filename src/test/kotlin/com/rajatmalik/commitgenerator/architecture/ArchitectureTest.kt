package com.rajatmalik.commitgenerator.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchitectureTest {
    private val sourceRoots = listOf(
        File(System.getProperty("user.dir"), "src/main/kotlin/com/rajatmalik/commitgenerator"),
        File(System.getProperty("user.dir"), "src/main/java/com/rajatmalik/commitgenerator"),
    )

    private fun filesUnder(relative: String): List<File> = sourceRoots.flatMap { root ->
        File(root, relative).walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .toList()
    }

    @Test
    fun `core does not depend on the IDE, network or subprocesses`() {
        val forbidden = listOf(
            "import com.intellij",
            "import git4idea",
            "import java.net.http",
            "ProcessBuilder",
            "Runtime.getRuntime",
            "kotlinx.coroutines",
        )
        for (file in filesUnder("core")) {
            val text = file.readText()
            for (token in forbidden) {
                assertFalse(text.contains(token), "${file.path} must not contain $token")
            }
        }
    }

    @Test
    fun `git adapter exposes read commands only`() {
        val forbidden = listOf(
            "GitCommand.ADD",
            "GitCommand.COMMIT",
            "GitCommand.RESET",
            "GitCommand.RESTORE",
            "GitCommand.CHECKOUT",
            "GitCommand.MERGE",
            "GitCommand.REBASE",
            "GitCommand.STASH",
            "GitCommand.UPDATE_INDEX",
            "GitCommand.UPDATE_REF",
            "GitCommand.RM",
            "GitCommand.MV",
        )
        for (file in filesUnder("adapters/git")) {
            val text = file.readText()
            for (token in forbidden) {
                assertFalse(text.contains(token), "${file.path} must not contain $token")
            }
        }
    }

    @Test
    fun `source contains no shell execution or telemetry`() {
        val forbidden = listOf(
            "ProcessBuilder",
            "Runtime.getRuntime().exec",
            "com.intellij.telemetry",
            "TelemetryManager",
        )
        for (file in filesUnder("")) {
            val text = file.readText().lowercase()
            for (token in forbidden) {
                assertFalse(text.contains(token.lowercase()), "${file.path} must not contain $token")
            }
        }
    }

    @Test
    fun `provider profiles never contain credentials`() {
        val settings = File(sourceRoots[0], "config/CommitGeneratorSettings.kt").readText()
        val profiles = File(sourceRoots[0], "core/domain/ProviderProfile.kt").readText()
        val secretStore = File(sourceRoots[0], "secrets/PasswordSafeSecretStore.kt").readText()
        assertFalse(Regex("\\bvar\\s+apiKey\\b").containsMatchIn(settings))
        assertFalse(Regex("\\bval\\s+apiKey\\b").containsMatchIn(profiles))
        assertTrue(secretStore.contains("PasswordSafe"))
    }
}
