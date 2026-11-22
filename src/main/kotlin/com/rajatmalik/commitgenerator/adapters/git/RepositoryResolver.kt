package com.rajatmalik.commitgenerator.adapters.git

import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

class RepositoryResolver(private val project: Project) {
    val repositories: List<GitRepository>
        get() = GitRepositoryManager.getInstance(project).repositories

    fun byRootPath(rootPath: String?): GitRepository? {
        if (rootPath == null) {
            return null
        }
        return repositories.firstOrNull { it.root.path == rootPath }
    }

    fun resolve(preferredRoot: String? = null): GitRepository? {
        val all = repositories
        if (all.isEmpty()) {
            return null
        }
        if (preferredRoot != null) {
            all.firstOrNull { it.root.path == preferredRoot }?.let { return it }
        }
        return all.firstOrNull { !it.isDisposed } ?: all.first()
    }
}
