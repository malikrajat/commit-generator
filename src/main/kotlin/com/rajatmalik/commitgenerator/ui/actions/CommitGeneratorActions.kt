package com.rajatmalik.commitgenerator.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.rajatmalik.commitgenerator.services.CommitGeneratorProjectService
import com.rajatmalik.commitgenerator.services.commitGeneratorProject

abstract class CommitGeneratorAction(
    text: String,
) : AnAction(text), DumbAware {

    final override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    final override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && isEnabled(commitGeneratorProject(project), e)
    }

    final override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        perform(project, commitGeneratorProject(project), e)
    }

    protected open fun isEnabled(service: CommitGeneratorProjectService, e: AnActionEvent): Boolean = true

    protected abstract fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent)
}

class GenerateCommitMessageAction : CommitGeneratorAction("Generate Commit Message") {
    override fun isEnabled(service: CommitGeneratorProjectService, e: AnActionEvent): Boolean =
        !service.isGenerating && service.activeProfile() != null

    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.generate(e.dataContext)
    }
}

class GenerateStagedCommitMessageAction : CommitGeneratorAction("Generate Commit Message (Staged Only)") {
    override fun isEnabled(service: CommitGeneratorProjectService, e: AnActionEvent): Boolean =
        !service.isGenerating && service.activeProfile() != null

    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.generate(e.dataContext, stagedOnly = true)
    }
}

class CancelGenerationAction : CommitGeneratorAction("Cancel Generation") {
    override fun isEnabled(service: CommitGeneratorProjectService, e: AnActionEvent): Boolean = service.isGenerating

    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.cancel()
    }
}

class RegenerateCommitMessageAction : CommitGeneratorAction("Regenerate Commit Message") {
    override fun isEnabled(service: CommitGeneratorProjectService, e: AnActionEvent): Boolean =
        !service.isGenerating && service.lastResult != null

    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.regenerate(e.dataContext)
    }
}

class UndoCommitMessageAction : CommitGeneratorAction("Undo Last Generated Message") {
    override fun isEnabled(service: CommitGeneratorProjectService, e: AnActionEvent): Boolean =
        service.lastResult != null

    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.undoLast()
    }
}

class CopyLastMessageAction : CommitGeneratorAction("Copy Last Generated Message") {
    override fun isEnabled(service: CommitGeneratorProjectService, e: AnActionEvent): Boolean =
        service.lastResult != null || service.pendingMessage != null

    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.copyLast()
    }
}

class PreviewPayloadAction : CommitGeneratorAction("Preview Payload To Be Sent") {
    override fun isEnabled(service: CommitGeneratorProjectService, e: AnActionEvent): Boolean =
        !service.isGenerating && service.activeProfile() != null

    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.showPreview()
    }
}

class OpenToolWindowAction : CommitGeneratorAction("Open Commit Generator") {
    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.openToolWindow()
    }
}

class SelectRepositoryAction : CommitGeneratorAction("Select Repository") {
    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.selectRepository()
    }
}

class ManageProfilesAction : CommitGeneratorAction("Manage Provider Profiles") {
    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.manageProfiles()
    }
}

class AddProfileAction : CommitGeneratorAction("Add Provider Profile") {
    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.addProfile()
    }
}

class EditProfileAction : CommitGeneratorAction("Edit Provider Profile") {
    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.editProfile()
    }
}

class RemoveProfileAction : CommitGeneratorAction("Remove Provider Profile") {
    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.removeProfile()
    }
}

class SelectProfileAction : CommitGeneratorAction("Select Provider Profile") {
    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.selectProfile()
    }
}

class SelectModelAction : CommitGeneratorAction("Select Model") {
    override fun isEnabled(service: CommitGeneratorProjectService, e: AnActionEvent): Boolean =
        service.activeProfile() != null

    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.selectModel()
    }
}

class TestConnectionAction : CommitGeneratorAction("Test Provider Connection") {
    override fun isEnabled(service: CommitGeneratorProjectService, e: AnActionEvent): Boolean =
        service.activeProfile() != null

    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.testConnection()
    }
}

class ValidateProfilesAction : CommitGeneratorAction("Validate Provider Configuration") {
    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.validateProfiles()
    }
}

class SetApiKeyAction : CommitGeneratorAction("Set API Key For Profile") {
    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.setApiKey()
    }
}

class ClearApiKeyAction : CommitGeneratorAction("Clear API Key For Profile") {
    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.clearApiKey()
    }
}

class AllowSensitiveOnceAction : CommitGeneratorAction("Allow Sensitive Files For Next Generation") {
    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.allowSensitiveOnce()
    }
}

class RevokeConsentAction : CommitGeneratorAction("Revoke Egress Consent") {
    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.revokeConsent()
    }
}

class ShowDiagnosticsAction : CommitGeneratorAction("Show Diagnostics") {
    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.showDiagnostics()
    }
}

class OpenSettingsAction : CommitGeneratorAction("Open Commit Generator Settings") {
    override fun perform(project: Project, service: CommitGeneratorProjectService, e: AnActionEvent) {
        service.generationController.openSettings()
    }
}
