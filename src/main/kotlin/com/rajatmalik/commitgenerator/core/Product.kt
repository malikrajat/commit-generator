package com.rajatmalik.commitgenerator.core

/**
 * Single source of truth for user-visible product identity.
 *
 * Keeping these values in one place makes it impossible for two layers to
 * advertise different names, command ids, or storage locations.
 */
object Product {
    const val ID: String = "com.rajatmalik.commitgenerator"
    const val NAME: String = "Commit Generator"
    const val SHORT_NAME: String = "Commit Generator"
    const val DESCRIPTION: String =
        "Generate AI commit messages from your staged Git diff. Works with cloud and local models. Never commits."
    const val VERSION: String = "1.0.0"
    const val VENDOR: String = "Rajat Malik"
    const val HOMEPAGE: String = "https://github.com/malikrajat/commit-generator"
    const val SUPPORT_URL: String = "$HOMEPAGE/issues"
    const val PRIVACY_URL: String = "$HOMEPAGE/blob/main/PRIVACY.md"
    const val SECURITY_URL: String = "$HOMEPAGE/blob/main/SECURITY.md"
    const val LICENSE_NAME: String = "MIT"

    const val TOOL_WINDOW_ID: String = "Commit Generator"
    const val NOTIFICATION_GROUP: String = "Commit Generator Notifications"
    const val STATUS_WIDGET_ID: String = "CommitGeneratorStatus"

    const val STATE_NAME: String = "CommitGeneratorSettings"
    const val STORAGE_FILE: String = "commit-generator.xml"
    const val CREDENTIAL_SERVICE: String = "Commit Generator"

    const val PROFILE_ID_PATTERN: String = "^[a-z0-9][a-z0-9-]{1,30}$"

    object Actions {
        const val GENERATE = "CommitGenerator.Generate"
        const val GENERATE_STAGED = "CommitGenerator.GenerateStaged"
        const val CANCEL = "CommitGenerator.Cancel"
        const val REGENERATE = "CommitGenerator.Regenerate"
        const val UNDO = "CommitGenerator.Undo"
        const val PREVIEW = "CommitGenerator.PreviewPayload"
        const val COPY_LAST = "CommitGenerator.CopyLast"
        const val OPEN_TOOL_WINDOW = "CommitGenerator.OpenToolWindow"
        const val MANAGE_PROFILES = "CommitGenerator.ManageProfiles"
        const val ADD_PROFILE = "CommitGenerator.AddProfile"
        const val EDIT_PROFILE = "CommitGenerator.EditProfile"
        const val REMOVE_PROFILE = "CommitGenerator.RemoveProfile"
        const val SELECT_PROFILE = "CommitGenerator.SelectProfile"
        const val SELECT_MODEL = "CommitGenerator.SelectModel"
        const val TEST_CONNECTION = "CommitGenerator.TestConnection"
        const val VALIDATE_PROFILES = "CommitGenerator.ValidateProfiles"
        const val SET_API_KEY = "CommitGenerator.SetApiKey"
        const val CLEAR_API_KEY = "CommitGenerator.ClearApiKey"
        const val ALLOW_SENSITIVE_ONCE = "CommitGenerator.AllowSensitiveOnce"
        const val REVOKE_CONSENT = "CommitGenerator.RevokeConsent"
        const val SHOW_DIAGNOSTICS = "CommitGenerator.ShowDiagnostics"
        const val OPEN_SETTINGS = "CommitGenerator.OpenSettings"
    }
}
