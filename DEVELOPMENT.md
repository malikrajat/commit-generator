# Development

## Setup

Requirements:

- JDK 21 for Gradle.
- WebStorm 2024.1 is downloaded automatically by the IntelliJ Platform Gradle Plugin.
- Internet access for the first build.

```bash
./gradlew test
./gradlew buildPlugin
```

On Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat buildPlugin
```

The installable ZIP is written to `build/distributions/commit-generator-1.0.0.zip`.

Run a development IDE:

```bash
./gradlew runIde
```

The first `runIde` may take several minutes while WebStorm starts.

## Project layout

```
src/main/kotlin/com/rajatmalik/commitgenerator/
  core/            pure Kotlin: domain, ports, context, security, message, summary, json
  adapters/git/    Git4Idea status/diff/log, repository resolution, commit-field writer
  adapters/providers/  presets, dialects, auth, HTTP, retries, model catalog
  config/          persistent settings, profiles, consent, settings UI
  secrets/         PasswordSafe credential store
  services/        composition root and generation orchestration
  ui/              tool window, dialogs, status bar, actions
  logging/         metadata-only logger
src/main/resources/META-INF/plugin.xml
src/test/kotlin/    unit, contract, security, architecture tests
```

`core` must never import `com.intellij`, `git4idea`, `java.net.http`, coroutines, or start a
process. The architecture test enforces this.

## Architecture rules

These are non-negotiable:

1. Never commit, stage, amend, checkout, reset, or restore Git state.
2. Never store credentials outside PasswordSafe.
3. Only the commit-message writer may modify the commit field.
4. All network traffic goes through the single HTTP layer.
5. Diff content is untrusted data, never instructions.
6. No arbitrary shell or subprocess execution.
7. No telemetry.
8. No TLS certificate-validation bypass.
9. No silent truncation: every omission is reported.

## Adding a provider

1. Add a `ProviderId` entry in `core/domain/ProviderProfile.kt`.
2. Add a preset in `adapters/providers/ProviderPresets.kt` (dialect, base URL, auth, model hint).
3. Reuse an existing dialect if the wire format matches. Otherwise add a dialect under
   `adapters/providers/dialect/` and register it in `ProviderRegistry.kt`.
4. Update the provider enum in the README and Marketplace metadata.
5. Add a mocked contract test under `src/test/kotlin/.../adapters/providers/`.
6. If the provider needs a custom auth style, extend `AuthResolver`.

## Adding a setting

1. Add the field to `CommitGeneratorSettings.SettingsState`.
2. Read it in `CommitGeneratorSettings.read()`.
3. Add the UI control to `CommitGeneratorSettingsPanel` (settings page) and/or the tool window.
4. Update the README settings table and `MARKETPLACE.md` if user-visible.
5. Add a unit test when the setting affects generation.

## Testing

```bash
./gradlew test
```

Tests must not touch the network. Provider tests use recorded JSON responses and fake HTTP
clients. Git parser tests are pure. Architecture tests scan the source tree for forbidden
patterns.

## Build configuration notes

- `instrumentCode = false`: the plugin has no GUI Designer forms and does not need nullability
  instrumentation.
- `buildSearchableOptions = false`: keeps CI fast and avoids launching the IDE.
- Kotlin stdlib is supplied by the IDE, so it is not bundled.
- The target platform is WebStorm 2024.1. The plugin uses only platform, VCS, and Git4Idea APIs.
  Compatibility is verified against WebStorm 2024.1, 2025.1, 2026.1, 2026.3, and IntelliJ IDEA
  2026.3.
- `untilBuild` is intentionally capped. Raise it only after verification.

## Compatibility verification

```bash
./gradlew verifyPluginProjectConfiguration
./gradlew verifyPlugin
```

Add more IDEs in `build.gradle.kts` under `pluginVerification.ides` before widening the
until-build range.

## Coding rules

- Keep the core pure and deterministic.
- Never log prompts, diffs, headers, or keys.
- Report omissions and truncation to the user.
- Prefer platform UI conventions over custom rendering.
- Keep user-facing strings ASCII unless localization is added.
- Add a test for every security-sensitive change.

## Commit messages

Use Conventional Commits. Commit Generator can generate them, which is a good way to test it.
