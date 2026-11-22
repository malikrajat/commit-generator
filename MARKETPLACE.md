# JetBrains Marketplace listing kit

This file contains copy-paste metadata for publishing **Commit Generator** on
<https://plugins.jetbrains.com>. It is intentionally separate from the README so the
Marketplace fields can be pasted without editing the technical documentation.

> The repository, support, and privacy URLs below assume
> `https://github.com/malikrajat/commit-generator`. Replace them if you publish from a different
> repository.

## Listing summary

| Field | Value |
| --- | --- |
| Plugin name | `Commit Generator` |
| Plugin ID | `com.rajatmalik.commitgenerator` |
| Version | `1.0.0` |
| Vendor | `Rajat Malik` |
| Vendor URL | `https://github.com/malikrajat` |
| Plugin home page | `https://github.com/malikrajat/commit-generator` |
| Source code | `https://github.com/malikrajat/commit-generator` |
| Support | `https://github.com/malikrajat/commit-generator/issues` |
| Privacy policy | `https://github.com/malikrajat/commit-generator/blob/main/PRIVACY.md` |
| Security policy | `https://github.com/malikrajat/commit-generator/blob/main/SECURITY.md` |
| License | MIT |
| Pricing | Free |
| Category | Version Control |
| Secondary category | AI / Machine Learning |
| Release channel | `default` |
| Since build | `241` (2024.1) |
| Until build | `263.*` (2026.3) |
| Plugin archive | `build/distributions/commit-generator-1.0.0.zip` |
| Plugin icon | `src/main/resources/META-INF/pluginIcon.svg` (archive), `media/plugin-icon.png` (Marketplace upload) |

## Short description

> AI commit messages from the files you check in the Commit panel. Works with OpenAI, Claude,
> Gemini, DeepSeek, Ollama, and more. Keys stay in the OS keychain. Never commits.

Shorter variant (for fields with a tight limit):

> AI commit messages from the files you check in the Commit panel. Cloud and local models.
> Never commits.

## Tagline

> Review, edit, commit. Commit Generator writes the message, you stay in control.

## Full description

Paste the content below into the Marketplace description field. It is valid HTML and
renders in both the light and dark Marketplace themes.

```html
<h2>AI commit messages for JetBrains IDEs, built around the files you check in the Commit panel.</h2>

<p>Commit Generator reads the changes you selected, asks your model for a
Conventional Commit message, and writes it into the IDE commit message field. You review,
edit, and commit. It never commits for you.</p>

<h3>What it does</h3>
<ul>
  <li>Generates one meaningful commit message from the files checked in the Commit panel,
      or from staged, unstaged, and untracked changes when started from the tool window.</li>
  <li>Works with OpenAI, Anthropic Claude, Google Gemini, DeepSeek, OpenRouter, Ollama,
      LM Studio, llama.cpp, vLLM, OpenCode, and any OpenAI-compatible endpoint.</li>
  <li>Multiple provider profiles, model discovery, and connection tests.</li>
  <li>Uses the files checked in the Commit panel, so JetBrains commit checkboxes work
      without staging files with Git first.</li>
  <li>Quick, Smart, and Detailed modes with token budgeting and map-reduce analysis for
      large change sets.</li>
  <li>Conventional Commits validation, optional body, breaking-change footers, issue keys,
      output language, and repository style learning.</li>
  <li>Shows exactly what will be sent, and reports every omitted or truncated file.</li>
</ul>

<h3>Built for WebStorm and JetBrains IDEs</h3>
<ul>
  <li>Commit-panel toolbar action.</li>
  <li>Dedicated tool window with provider, scope, message, privacy, and result controls.</li>
  <li>Settings page under Settings | Tools | Commit Generator.</li>
  <li>Status bar widget with the active mode and profile.</li>
  <li>All actions are available from Find Action.</li>
</ul>

<h3>Trust and safety</h3>
<ul>
  <li><b>Never commits.</b> It only writes text into the commit message field.</li>
  <li><b>Keys stay in the OS keychain</b> through JetBrains PasswordSafe, never in project
      files or XML settings.</li>
  <li><b>Secrets are redacted</b> before any request leaves the machine.</li>
  <li><b>Sensitive paths are blocked</b> by default, with a one-run override.</li>
  <li><b>No telemetry.</b> No analytics and no background network calls.</li>
  <li><b>Local-only mode</b> rejects every non-loopback endpoint.</li>
  <li><b>Diff content is data</b> and is never treated as instructions.</li>
  <li><b>No arbitrary shell execution.</b> Git access is read-only through Git4Idea.</li>
</ul>

<h3>Supported IDEs</h3>
<p>WebStorm, IntelliJ IDEA, PhpStorm, PyCharm, GoLand, RubyMine, CLion, Rider, DataGrip,
DataSpell, and RustRover 2024.1 through 2026.3, with the bundled Git plugin enabled.</p>

<h3>Requirements</h3>
<ul>
  <li>WebStorm 2024.1 through 2026.3, or a compatible JetBrains IDE in the same build range.</li>
  <li>The bundled Git plugin and an open Git repository.</li>
  <li>A provider account and API key, except for local servers.</li>
</ul>

<h3>How it works</h3>
<ol>
  <li>Stage or select the changes you want to describe.</li>
  <li>Press the Commit Generator action in the Commit panel or tool window.</li>
  <li>Review the generated message in the commit field.</li>
  <li>Edit if needed, then commit yourself.</li>
</ol>

<p>Commit Generator never stages, commits, amends, or rewrites history.</p>

<h3>Documentation</h3>
<p>Usage, providers, settings, troubleshooting, architecture, security, and privacy
documentation are included with the plugin and in the repository.</p>
```

## SEO keywords

Use these as Marketplace tags and in the SEO description. Do not stuff all of them into the
short description.

**Primary**

`git`, `commit`, `commit message`, `commit message generator`, `AI commit message`,
`Conventional Commits`, `staged changes`, `diff`, `source control`, `SCM`, `WebStorm`,
`IntelliJ`, `JetBrains`

**Providers and models**

`OpenAI`, `ChatGPT`, `Claude`, `Anthropic`, `Gemini`, `Google Gemini`, `DeepSeek`,
`OpenRouter`, `Ollama`, `LM Studio`, `llama.cpp`, `vLLM`, `OpenCode`, `local LLM`,
`offline`, `privacy`, `LLM`, `AI`

**Long-tail searches**

- AI commit message generator for WebStorm
- generate commit message from staged changes JetBrains
- Conventional Commits plugin for IntelliJ
- Ollama commit message plugin
- local LLM commit message WebStorm
- private AI commit message generator
- commit message helper for JetBrains IDEs
- OpenAI commit message plugin
- Claude commit message plugin
- DeepSeek commit message plugin
- Gemini commit message plugin
- Git commit generator plugin
- commit message from diff
- offline commit message generation

## Product compatibility

Select these products in the Marketplace compatibility matrix:

- WebStorm
- IntelliJ IDEA Ultimate
- IntelliJ IDEA Community
- PhpStorm
- PyCharm Professional
- PyCharm Community
- GoLand
- RubyMine
- CLion
- Rider
- DataGrip
- DataSpell
- RustRover

The plugin does not require Java or Kotlin language plugins. It depends only on the platform,
VCS, and bundled Git plugin. The plugin verifier reports compatibility with WebStorm 2024.1,
2025.1, 2026.1, and 2026.3, plus IntelliJ IDEA 2026.3.

## Screenshots

Marketplace listings with screenshots convert substantially better. Capture these five
images at 1440x900 or larger, PNG, under 5 MB each.

1. **Commit panel action** - open the Commit tool window with checked files and show the
   Commit Generator button in the message toolbar. Caption: `Generate from the Commit panel`.
2. **Tool window** - show the provider, staged/unstaged toggles, mode, Generate button, and a
   generated Conventional Commit message. Caption: `One tool window for every control`.
3. **Provider profiles** - show the Manage Provider Profiles dialog with OpenAI and Ollama
   profiles. Caption: `Cloud or local, switch in one click`.
4. **Payload preview** - show the redacted preview dialog with a manifest and diff delimiters.
   Caption: `See exactly what will be sent`.
5. **Settings** - show the settings page with Conventional Commits, budgets, and local-only
   mode. Caption: `Limits, privacy, and output style`.

Do not include real API keys, private repository names, customer code, or personal data in
screenshots. Use a synthetic demo repository.

## Release notes for 1.0.0

```html
<h3>1.0.0</h3>
<ul>
  <li>First release for WebStorm and JetBrains IDEs.</li>
  <li>Responsive tool window with full-width sections, consistent left alignment, and
      controls that stay inside the window at every width.</li>
  <li>AI commit message generation from staged, unstaged, and untracked Git changes.</li>
  <li>Reads the files checked in the JetBrains Commit panel, so commit checkboxes work
      without staging files first.</li>
  <li>Eleven provider presets, multiple profiles, model discovery, and connection tests.</li>
  <li>Quick, Smart, and Detailed modes with token budgeting and map-reduce summaries.</li>
  <li>PasswordSafe credentials, secret redaction, sensitive-path blocking, consent tracking,
      local-only mode, and payload preview.</li>
  <li>Tool window, settings page, status bar widget, and commit-panel action.</li>
  <li>Verified from WebStorm 2024.1 through WebStorm 2026.3, plus IntelliJ IDEA 2026.3.</li>
</ul>
```

## Upload checklist

1. Confirm `gradle.properties` and `src/main/resources/META-INF/plugin.xml` both say
   `1.0.0`.
2. Run `.\gradlew.bat clean test buildPlugin`.
3. Run `.\gradlew.bat verifyPluginProjectConfiguration`.
4. Optionally run `.\gradlew.bat verifyPlugin` and fix any reported compatibility problems.
5. Sign the ZIP with `.\gradlew.bat signPlugin` if you have JetBrains signing certificates.
6. Open <https://plugins.jetbrains.com/author/me> and choose **Upload plugin**.
7. Upload `build/distributions/commit-generator-1.0.0.zip`.
8. Paste the name, short description, full description, tags, category, vendor, and URLs
   from this document.
9. Upload `media/plugin-icon.png` as the plugin icon if the portal asks separately (the plugin
   archive already contains `META-INF/pluginIcon.svg`).
10. Upload the screenshots.
11. Select the compatible products and build range.
12. Choose **Free** pricing and **MIT** license.
13. Paste the release notes.
14. Submit for review. Respond to any automated verifier findings in the plugin repository.

## Marketplace review notes

Commit Generator performs network requests only after an explicit user action. It stores
credentials in PasswordSafe, never in the plugin state. It uses the bundled Git plugin for
read-only Git commands and never stages, commits, or changes repository state. No telemetry is
collected. These points match the Marketplace guidelines for AI and VCS plugins.

## Claims to avoid

Do not claim that Commit Generator:

- writes perfect messages,
- replaces code review,
- works without a provider or local model,
- commits automatically,
- supports browser-only clients, or
- guarantees compatibility with every future IDE build.

The until-build range is intentionally capped at `263.*` and should be raised only after
verification against newer releases.
