package com.rajatmalik.commitgenerator.core.context

/**
 * Minimal glob matcher supporting `**`, `*` and `?` over POSIX paths.
 * Pure and deterministic with no dependency on platform APIs.
 */
fun globToRegex(glob: String): Regex {
    val out = StringBuilder("^")
    var i = 0
    while (i < glob.length) {
        val char = glob[i]
        when {
            char == '*' -> {
                if (i + 1 < glob.length && glob[i + 1] == '*') {
                    i += 1
                    if (i + 1 < glob.length && glob[i + 1] == '/') {
                        i += 1
                        out.append("(?:.*/)?")
                    } else {
                        out.append(".*")
                    }
                } else {
                    out.append("[^/]*")
                }
            }

            char == '?' -> out.append("[^/]")
            char in "\\^$.|+()[]{}" -> out.append('\\').append(char)
            else -> out.append(char)
        }
        i += 1
    }
    return Regex(out.append('$').toString())
}

fun matchesGlob(path: String, glob: String): Boolean {
    val normalized = path.replace('\\', '/')
    val regex = globToRegex(glob)
    if (regex.matchEntire(normalized) != null) {
        return true
    }
    // A bare directory or file pattern should also match nested occurrences.
    if (!glob.startsWith("**/") && !glob.startsWith("/") && !glob.startsWith("*")) {
        return globToRegex("**/$glob").matchEntire(normalized) != null
    }
    return false
}

fun matchesAny(path: String, globs: List<String>): String? = globs.firstOrNull { matchesGlob(path, it) }

/**
 * Safety exclusions that can never be removed by configuration: generated
 * artifacts and lockfiles carry no intent and would dominate any budget.
 */
val PROTECTED_EXCLUDES: List<String> = listOf(
    "**/package-lock.json",
    "**/npm-shrinkwrap.json",
    "**/pnpm-lock.yaml",
    "**/yarn.lock",
    "**/bun.lockb",
    "**/Cargo.lock",
    "**/cargo.lock",
    "**/poetry.lock",
    "**/Pipfile.lock",
    "**/composer.lock",
    "**/Gemfile.lock",
    "**/go.sum",
    "**/deno.lock",
    "**/mix.lock",
    "**/Podfile.lock",
    "**/packages.lock.json",
    "**/*.min.js",
    "**/*.min.css",
    "**/*.map",
    "**/*.snap",
    "**/dist/**",
    "**/build/**",
    "**/out/**",
    "**/vendor/**",
    "**/node_modules/**",
    "**/coverage/**",
)
