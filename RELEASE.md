# Release

Maintainer checklist for publishing Commit Generator.

## 1. Prepare

1. Update the version in:
   - `gradle.properties` (`pluginVersion`)
   - `src/main/resources/META-INF/plugin.xml` (`<version>`)
   - `CHANGELOG.md`
2. Update `MARKETPLACE.md` release notes if the listing copy changed.
3. Confirm the until-build range is still correct and verified.
4. Confirm the privacy and security documents still match behavior.

## 2. Verify locally

```powershell
.\gradlew.bat clean test buildPlugin
.\gradlew.bat verifyPluginProjectConfiguration
```

Optional compatibility verification:

```powershell
.\gradlew.bat verifyPlugin
```

The build verifies against WebStorm 2024.1, 2025.1, 2026.1, and 2026.3. Add newer IDEs to
`build.gradle.kts` before raising the until-build range.

The archive is written to:

```
build/distributions/commit-generator-1.0.0.zip
```

A ready-to-upload copy is also placed at the repository root as
`commit-generator-1.0.0.zip`.

Inspect the archive:

```powershell
Expand-Archive build\distributions\commit-generator-1.0.0.zip -DestinationPath build\release-check
Get-ChildItem build\release-check -Recurse
```

Confirm it contains `META-INF/plugin.xml`, both plugin icons, and the plugin JAR.

## 3. Sign (recommended)

JetBrains Marketplace accepts unsigned uploads, but signing removes warning banners.

Create signing certificates in your JetBrains Marketplace vendor profile. Then set:

```powershell
$env:CERTIFICATE_CHAIN = Get-Content .\certificate-chain.pem -Raw
$env:PRIVATE_KEY = Get-Content .\private-key.pem -Raw
$env:PRIVATE_KEY_PASSWORD = "<password>"
.\gradlew.bat signPlugin
```

Signed archives are written under `build/distributions`.

## 4. Publish with Gradle

Create a Marketplace token in the vendor profile, then:

```powershell
$env:PUBLISH_TOKEN = "<marketplace-token>"
$env:CERTIFICATE_CHAIN = Get-Content .\certificate-chain.pem -Raw
$env:PRIVATE_KEY = Get-Content .\private-key.pem -Raw
$env:PRIVATE_KEY_PASSWORD = "<password>"
.\gradlew.bat publishPlugin
```

The default channel is `default`. A pre-release version such as `1.1.0-beta.1` publishes to the
`beta` channel because of the channel mapping in `build.gradle.kts`.

## 5. Publish manually

If you prefer the web UI:

1. Open <https://plugins.jetbrains.com/author/me>.
2. Choose **Upload plugin**.
3. Upload `build/distributions/commit-generator-1.0.0.zip`.
4. Copy the fields from `MARKETPLACE.md`.
5. Upload screenshots.
6. Select compatible products and the build range.
7. Choose **Free** pricing and **MIT** license.
8. Paste the release notes.
9. Submit for review.

## 6. After publishing

1. Verify the Marketplace page renders the description, icon, and screenshots.
2. Install the published build in a clean WebStorm profile.
3. Test both commit UIs:
   - Commit tool window (non-modal).
   - Modal commit dialog.
4. Test staged-only, unstaged, and untracked selection.
5. Test a cloud provider and a local provider.
6. Test cancel, undo, regenerate, preview, and local fallback.
7. Confirm keys are stored in PasswordSafe and absent from plugin XML.
8. Create a Git tag and GitHub release.

## 7. Version policy

- Patch: bug fixes, provider fixes, documentation corrections.
- Minor: new providers, settings, or features.
- Major: breaking state format or removed behavior.

Raise `untilBuild` only after `verifyPlugin` passes against the new IDE releases.

## 8. Rollback

If a release is broken:

1. Mark the version as hidden/obsolete in the Marketplace vendor portal.
2. Publish a fixed patch release.
3. Document the incident in `CHANGELOG.md`.
4. Revoke any exposed credentials immediately.
