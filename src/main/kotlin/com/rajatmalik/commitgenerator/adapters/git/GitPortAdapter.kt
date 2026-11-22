package com.rajatmalik.commitgenerator.adapters.git

import com.rajatmalik.commitgenerator.core.domain.CancelSignal
import com.rajatmalik.commitgenerator.core.domain.ChangeStatus
import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.DiffMode
import com.rajatmalik.commitgenerator.core.domain.ErrorCode
import com.rajatmalik.commitgenerator.core.domain.appError
import com.rajatmalik.commitgenerator.core.domain.err
import com.rajatmalik.commitgenerator.core.domain.ok
import com.rajatmalik.commitgenerator.core.ports.GitPort
import com.rajatmalik.commitgenerator.core.ports.GitRepositoryInfo
import com.rajatmalik.commitgenerator.core.ports.GitStatusEntry
import com.rajatmalik.commitgenerator.core.ports.UntrackedRead
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import git4idea.repo.GitRepository

class GitPortAdapter(
    private val runner: GitCommandRunner,
    private val repository: GitRepository,
) : GitPort {
    override val info: GitRepositoryInfo = GitRepositoryInfo(
        rootPath = repository.root.path,
        repoName = repository.root.name,
        branch = repository.currentBranch?.name,
        head = repository.currentRevision,
    )

    override fun status(): CwResult<List<GitStatusEntry>> = when (val result = runner.status()) {
        is CwResult.Ok -> ok(parseGitStatus(result.value))
        is CwResult.Err -> result
    }

    override fun diff(path: String, mode: DiffMode): CwResult<String> = runner.diff(mode, path)

    override fun readUntracked(path: String, maxBytes: Int): CwResult<UntrackedRead> {
        if (path.isBlank() || path.startsWith('/') || path.split('/').any { it == ".." }) {
            return err(appError(ErrorCode.INTERNAL, "Refusing to read an unsafe path.", cause = path.take(120)))
        }
        val root: Path = repository.root.toNioPath().toAbsolutePath().normalize()
        val absolute = root.resolve(path.replace('/', java.io.File.separatorChar)).normalize()
        if (!absolute.startsWith(root)) {
            return err(appError(ErrorCode.INTERNAL, "Refusing to read a path outside the repository."))
        }
        if (!Files.isRegularFile(absolute)) {
            return err(appError(ErrorCode.INTERNAL, "The untracked file is not readable.", cause = path.take(120)))
        }
        if (Files.isSymbolicLink(absolute)) {
            // Never follow an untracked symlink: it can point outside the repository.
            return ok(UntrackedRead("", binary = true, tooLarge = false))
        }
        val rootReal = runCatching { root.toRealPath() }.getOrDefault(root)
        val absoluteReal = runCatching { absolute.toRealPath() }.getOrNull()
        if (absoluteReal == null || !absoluteReal.startsWith(rootReal)) {
            return err(appError(ErrorCode.INTERNAL, "Refusing to read a path outside the repository."))
        }
        val size = runCatching { Files.size(absolute) }.getOrDefault(0L)
        if (size > maxBytes) {
            return ok(UntrackedRead("", false, true))
        }
        val bytes = runCatching {
            Files.newInputStream(absolute).use { it.readNBytes(maxBytes + 1) }
        }.getOrElse {
            return err(appError(ErrorCode.INTERNAL, "Could not read an untracked file.", cause = path.take(120)))
        }
        if (bytes.size > maxBytes) {
            return ok(UntrackedRead("", false, true))
        }
        val probe = bytes.take(8_000)
        if (probe.any { it == 0.toByte() }) {
            return ok(UntrackedRead("", true, false))
        }
        val content = String(bytes, StandardCharsets.UTF_8)
        val lines = content.split(Regex("\\r?\\n")).toMutableList()
        if (lines.isNotEmpty() && lines.last().isEmpty()) {
            lines.removeAt(lines.lastIndex)
        }
        if (lines.size > MAX_UNTRACKED_LINES) {
            return ok(UntrackedRead("", false, true))
        }
        return ok(UntrackedRead(synthesizeUntracked(path, lines), false, false))
    }

    override fun recentSubjects(limit: Int, signal: CancelSignal): CwResult<List<String>> {
        if (limit <= 0) {
            return ok(emptyList())
        }
        if (signal.isCancelled) {
            return err(appError(ErrorCode.CANCELLED, "Generation cancelled."))
        }
        val result = runner.recentSubjects(limit)
        if (result is CwResult.Err) {
            // An empty repository has no history; that is not a failure.
            return ok(emptyList())
        }
        val output = (result as CwResult.Ok).value
        return ok(
            output.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.length <= 120 }
                .toList(),
        )
    }

    private fun synthesizeUntracked(path: String, lines: List<String>): String {
        val body = lines.joinToString("\n") { "+$it" }
        return listOf(
            "diff --git a/$path b/$path",
            "new file mode 100644",
            "--- /dev/null",
            "+++ b/$path",
            "@@ -0,0 +1,${lines.size} @@",
            body,
        ).joinToString("\n")
    }

    private companion object {
        const val MAX_UNTRACKED_LINES = 4_000
    }
}

fun isSourceLike(path: String): Boolean {
    val lower = path.lowercase()
    return listOf(
        ".lock", ".min.js", ".min.css", ".map", ".snap", ".svg", ".png", ".jpg", ".jpeg",
        ".gif", ".webp", ".ico", ".pdf", ".woff", ".woff2", ".ttf",
    ).none { lower.endsWith(it) }
}
