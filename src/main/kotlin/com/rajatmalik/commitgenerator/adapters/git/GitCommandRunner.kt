package com.rajatmalik.commitgenerator.adapters.git

import com.intellij.openapi.project.Project
import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.ErrorCode
import com.rajatmalik.commitgenerator.core.domain.appError
import com.rajatmalik.commitgenerator.core.domain.err
import com.rajatmalik.commitgenerator.core.domain.ok
import com.rajatmalik.commitgenerator.core.security.redactSecrets
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository

/**
 * Read-only Git command runner. Only the explicit read commands used by this
 * product are exposed; there is no generic mutation entry point.
 */
class GitCommandRunner(
    private val project: Project,
    private val repository: GitRepository,
) {
    fun status(): CwResult<String> = run(
        GitCommand.STATUS,
        "--porcelain=v1",
        "-z",
        "--untracked-files=all",
        "--ignore-submodules=dirty",
    )

    fun diff(mode: com.rajatmalik.commitgenerator.core.domain.DiffMode, path: String): CwResult<String> {
        val params = mutableListOf<String>()
        when (mode) {
            com.rajatmalik.commitgenerator.core.domain.DiffMode.STAGED -> params += "--cached"
            com.rajatmalik.commitgenerator.core.domain.DiffMode.WORKING,
            com.rajatmalik.commitgenerator.core.domain.DiffMode.UNTRACKED,
            -> Unit

            com.rajatmalik.commitgenerator.core.domain.DiffMode.HEAD -> params += "HEAD"
        }
        params += listOf("--unified=3", "--no-color", "--no-ext-diff", "--no-textconv", "--", path)
        return run(GitCommand.DIFF, *params.toTypedArray())
    }

    fun recentSubjects(limit: Int): CwResult<String> = run(
        GitCommand.LOG,
        "--no-merges",
        "-n",
        limit.coerceAtMost(100).toString(),
        "--pretty=format:%s",
    )

    private fun run(command: GitCommand, vararg params: String): CwResult<String> {
        val handler = GitLineHandler(project, repository.root, command)
        handler.addParameters(*params)
        handler.setSilent(true)
        handler.setStdoutSuppressed(true)
        handler.setStderrSuppressed(true)
        handler.setTerminationTimeout(30_000)

        return try {
            val result = Git.getInstance().runCommand(handler)
            if (result.success()) {
                ok(result.output.joinToString("\n"))
            } else {
                val cause = redactSecrets(result.errorOutput.joinToString("\n").ifBlank { result.output.joinToString("\n") }).text
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(200)
                err(
                    appError(
                        ErrorCode.INTERNAL,
                        "Git command failed.",
                        cause = cause.ifEmpty { null },
                    ),
                )
            }
        } catch (error: com.intellij.openapi.progress.ProcessCanceledException) {
            err(appError(ErrorCode.CANCELLED, "Generation cancelled."))
        } catch (error: IllegalStateException) {
            err(
                appError(
                    ErrorCode.PROJECT_NOT_TRUSTED,
                    "Git commands cannot run until this project is trusted.",
                    cause = error.message?.take(160),
                ),
            )
        } catch (error: Exception) {
            err(
                appError(
                    ErrorCode.INTERNAL,
                    "Git command could not be executed.",
                    cause = error.javaClass.simpleName,
                ),
            )
        }
    }
}
