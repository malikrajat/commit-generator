# Commit Generator

![Commit Generator](media/banner.png)

**AI commit messages for WebStorm and JetBrains IDEs, built around the files you check in the Commit panel.**

Commit Generator reads the changes you selected, asks your model for a Conventional Commit
message, and writes it into the IDE commit message field. You review, edit, and commit.
It never commits for you.

[Features](#features) | [Install](#installation) | [Quick start](#quick-start) |
[Providers](#supported-providers) | [Commands](#commands) | [Settings](#settings-reference) |
[Help](HELP.md) | [Security](SECURITY.md) | [Privacy](PRIVACY.md) |
[Architecture](ARCHITECTURE.md) | [Best practices](BEST_PRACTICES.md) |
[Troubleshooting](#troubleshooting) | [FAQ](#faq) | [Marketplace metadata](MARKETPLACE.md)

---

## What it does

- Turns a Git change set into one meaningful commit message.
- Uses the files checked in the Commit panel, so JetBrains commit checkboxes work without
  staging files into the Git index first.
- Uses the AI provider and model you choose, including local models with no account.
- Follows Conventional Commits: `feat`, `fix`, `refactor`, `perf`, `docs`, `test`, `build`,
  `ci`, `chore`, `style`, `revert`.
- Handles repositories from one file to thousands, with explicit limits you control.
- Shows exactly what will be sent, and what was left out.
- Works in WebStorm, IntelliJ IDEA, PhpStorm, PyCharm, GoLand, RubyMine, CLion, Rider,
  DataGrip, DataSpell, and RustRover.

## Trust and safety

| Guarantee | How it is enforced |
| --- | --- |
| **Never commits** | Commit Generator only writes text into the commit message field. No commit, stage, amend, checkout, reset, or restore operation is called. |
| **API keys stay secret** | Keys are stored in the OS keychain through JetBrains PasswordSafe, never in project files, XML settings, logs, or telemetry. |
| **Secrets are redacted** | Request bodies are scrubbed for API keys, tokens, private keys, JWTs, passwords, and connection strings before they leave the machine. |
| **Sensitive files are blocked** | `.env`, key material, `.ssh`, `.aws`, credential stores, and similar paths are excluded by default, with a one-run override. |
| **No telemetry** | Version 1 has no telemetry, no analytics, and no background network calls. |
| **Local-only option** | Local-only mode rejects every non-loopback endpoint, so only Ollama, LM Studio, llama.cpp, vLLM, or a local OpenCode server can be reached. |
| **TLS is never weakened** | HTTPS is required for remote endpoints. There is no certificate-validation bypass and redirects are not followed. |
| **No arbitrary shell execution** | Commit Generator never starts a shell or executes arbitrary commands. Git access uses Git4Idea's command API with a fixed set of read-only commands. |
| **Repository cannot hijack credentials** | Provider profiles are application-level, not project-level. A repository cannot add, redirect, or replace a profile. |
| **Diff content is data** | Diff text is wrapped in explicit delimiters and treated as untrusted input, never as instructions. |

Read the full [security policy](SECURITY.md) and [privacy statement](PRIVACY.md).

## Features

### Commit messages

- Conventional Commit types with configurable scopes and subject length.
- Optional body and breaking-change footers.
- Grounding checks that keep the message tied to the real files and change kinds.
- Draft protection: Replace, Append, or Cancel; Undo restores the previous message.
- Regenerate with the same change snapshot.
- Deterministic local fallback when the model fails.

### Providers and models

- OpenAI, Anthropic (Claude), Google Gemini, DeepSeek, OpenRouter.
- Ollama, LM Studio, llama.cpp server, and vLLM for local inference.
- OpenCode local server, reusing its models and auth.
- Any custom OpenAI-compatible endpoint, with configurable API format, auth header, and base URL.
- Multiple saved profiles; switch without touching other settings.
- Model discovery with a manual fallback.

### Context control

- Include staged, unstaged, and untracked changes independently.
- Exclude files and directories with globs.
- Cap file size, file count, diff lines, and total token budget.
- Per-mode overrides for power users (stored in plugin state).

### Large repositories

- File classification (source, tests, docs, config, lockfiles, generated, binary, assets).
- Directory and monorepo package grouping.
- Token-budgeted diff selection with an always-sent manifest.
- Chunked map-reduce summarization, retries, and caching.
- Explicit reports for omitted, truncated, or failed files - nothing is silently dropped.

### JetBrains integration

- Commit-panel toolbar action.
- Commit Generator tool window with provider, scope, message, privacy, and result controls.
- Settings page under **Settings | Tools | Commit Generator**.
- Status bar widget showing the active mode and profile.
- All actions are available from **Find Action** (`Ctrl+Shift+A` / `Cmd+Shift+A`).

## Requirements

- WebStorm 2024.1 through 2026.3 (builds 241 through 263.*), or a compatible JetBrains IDE
  in the same build range. Verified with WebStorm 2024.1, 2025.1, 2026.1, and 2026.3,
  plus IntelliJ IDEA 2026.3.
- The bundled **Git** plugin enabled.
- A Git repository opened in the IDE.
- Network access to your provider, or a local Ollama / LM Studio / llama.cpp / vLLM /
  OpenCode server.
- A provider account and API key, except for local servers.

### Compatibility range

The plugin declares `since-build="241"` and `until-build="263.*"`, which means:

- **Supported:** every JetBrains IDE release from **2024.1 (241)** through **2026.3
  (263.*)**, including all patch builds in those release branches. Your WebStorm
  `WS-263.7307.202` is a 2026.3 build, so it is inside the range.
- **Not supported:** releases older than 2024.1, and releases newer than 2026.3
  (for example a future 2026.4 / 264 build) until the upper bound is raised and verified.
- The `241` and `263` values are release branches, not a specific IDE installation or
  product ID. The same numeric build exists across WebStorm, IntelliJ IDEA, PhpStorm,
  and the other JetBrains IDEs.

It is not WebStorm-only. The plugin depends only on the platform, VCS, and bundled Git
plugin, so it loads in any IntelliJ Platform IDE that ships Git. The 1.1.0 build is
verified against WebStorm 2024.1, 2025.1, 2026.1, 2026.3, and IntelliJ IDEA 2026.3.

## Installation

**From JetBrains Marketplace:**

1. Open **Settings | Plugins | Marketplace**.
2. Search for **Commit Generator**.
3. Install and restart the IDE.

**From a ZIP:**

1. Build the plugin with `.\gradlew.bat buildPlugin` or download a release.
2. Open **Settings | Plugins**.
3. Click the gear icon and choose **Install Plugin from Disk...**.
4. Select `commit-generator-1.1.0.zip`.
5. Restart the IDE.

A ready-to-upload copy of the archive is also placed at the repository root as
`commit-generator-1.1.0.zip`.

## Quick start

1. Open a Git repository in WebStorm or another JetBrains IDE.
2. Check the files you want to commit in the **Commit** panel. No staging step is required:
   Commit Generator uses the checked files directly. Unstaged changes are also enabled by
   default when you start from the tool window.
3. Open the **Commit Generator** tool window on the right side, or run
   **Commit Generator: Manage Provider Profiles** from **Find Action**.
4. Add a provider, enter the API key, and choose a model.
5. Press **Generate commit message**, or use the Commit Generator button in the Commit tool
   window toolbar.
6. Review and edit the message in the commit message field, then commit yourself.

For a fully local setup, start Ollama (or LM Studio / llama.cpp / vLLM), add the matching local
provider, and enable **Local-only mode** in the tool window or settings page.

> **JetBrains staging note:** checked files in the Commit panel are included in the coming
> commit, not staged in the Git index. Commit Generator reads the checked files directly when
> it is launched from the Commit panel. Unstaged changes are enabled by default for tool-window
> generation too.

## Supported providers

| Provider | API format | Default endpoint | Key required |
| --- | --- | --- | --- |
| OpenAI | Chat completions | `https://api.openai.com/v1` | Yes |
| Anthropic (Claude) | Messages API | `https://api.anthropic.com` | Yes |
| Google Gemini | `generateContent` | `https://generativelanguage.googleapis.com` | Yes |
| DeepSeek | Chat completions | `https://api.deepseek.com/v1` | Yes |
| OpenRouter | Chat completions | `https://openrouter.ai/api/v1` | Yes |
| Ollama | Native `/api/chat` | `http://127.0.0.1:11434` | No |
| LM Studio | Chat completions | `http://127.0.0.1:1234/v1` | No |
| llama.cpp server | Chat completions | `http://127.0.0.1:8080/v1` | No |
| vLLM | Chat completions | `http://127.0.0.1:8000/v1` | No |
| OpenCode | Local session server | `http://127.0.0.1:4096` | Optional |
| Custom | OpenAI, Anthropic, Gemini, or Ollama format | Your URL | Configurable |

## Generation modes

| Mode | Behaviour | Use it when |
| --- | --- | --- |
| **Quick** | One request, subject line only. | Small changes, fast feedback. |
| **Smart** | Chooses single-pass or chunked map-reduce based on the diff. | Everyday use. |
| **Detailed** | Always summarizes files in chunks before writing, with the largest budget. | Large or mixed change sets. |

Set the mode in the Commit Generator tool window or the settings page.

## Choosing what to analyse

Staged, unstaged, and untracked changes are independent toggles. When generation starts from
the Commit panel, the checked files take precedence and are diffed against `HEAD`.

- **Staged changes** (default on) - the Git index (`HEAD` to index). Used when the Commit
  panel selection is not available.
- **Unstaged changes** (default on) - the working tree, including merge conflicts.
- **Untracked files** (default off) - files Git does not track yet.

Additional controls: exclusion globs, maximum file size, maximum file count, maximum diff
lines per file, and total diff token budget.

## Commands

Every action is available in **Find Action** under the `Commit Generator` prefix.

| Action | What it does |
| --- | --- |
| `Commit Generator: Generate Commit Message` | Generate from the configured change selection |
| `Commit Generator: Generate Commit Message (Staged Only)` | Force the Git index only |
| `Commit Generator: Regenerate Commit Message` | Retry with the same change snapshot |
| `Commit Generator: Cancel Generation` | Stop an in-flight request |
| `Commit Generator: Undo Last Generated Message` | Restore the commit message from before generation |
| `Commit Generator: Preview Payload To Be Sent` | Inspect the redacted prompt |
| `Commit Generator: Copy Last Generated Message` | Copy the last message to the clipboard |
| `Commit Generator: Open Commit Generator` | Open the tool window |
| `Commit Generator: Select Repository` | Choose between open Git repositories |
| `Commit Generator: Manage Provider Profiles` | Add, edit, remove, switch, test, validate |
| `Commit Generator: Add Provider Profile` | Guided provider setup |
| `Commit Generator: Edit Provider Profile` | Edit an existing profile |
| `Commit Generator: Remove Provider Profile` | Remove a profile and its credential |
| `Commit Generator: Select Provider Profile` | Switch the active profile |
| `Commit Generator: Select Model` | Pick from the provider model list |
| `Commit Generator: Test Provider Connection` | Check endpoint, key, and model list |
| `Commit Generator: Validate Provider Configuration` | Validate every saved profile |
| `Commit Generator: Set API Key For Profile` | Store a key in PasswordSafe |
| `Commit Generator: Clear API Key For Profile` | Remove a stored credential |
| `Commit Generator: Allow Sensitive Files For Next Generation` | One-run override with a warning |
| `Commit Generator: Revoke Egress Consent` | Clear all network consent records |
| `Commit Generator: Show Diagnostics` | Environment and capability summary |
| `Commit Generator: Open Commit Generator Settings` | Open the settings page |

## Settings reference

Open **Settings | Tools | Commit Generator**.

| Setting | Default | Description |
| --- | --- | --- |
| Mode | `smart` | Quick, Smart, or Detailed |
| Staged changes | `true` | Include the Git index |
| Unstaged changes | `true` | Include working-tree changes |
| Untracked files | `false` | Include untracked files |
| Max diff tokens | `12000` | Diff token budget |
| Max files | `200` | Maximum files analysed |
| Max bytes per file | `262144` | Maximum size per file |
| Max diff lines per file | `400` | Maximum diff lines per file |
| Additional exclusion globs | empty | One glob per line; built-in safety exclusions cannot be disabled |
| Learn style from recent commits | `true` | Sample recent commit subjects |
| History samples | `30` | History sample size |
| Conventional Commits | `true` | Enforce the Conventional Commits contract |
| Allowed types | `feat, fix, ...` | Allowed types |
| Max subject length | `72` | Subject limit |
| Require a scope | `false` | Require a scope |
| Allowed scopes | empty | Empty means any scope |
| Language | `en` | Output language, or `auto` |
| Allow a commit body | `true` | Allow a body |
| Add issue key from branch | `false` | Add `Refs: KEY` from the branch |
| Attribution trailer | `none` | Optional `Generated-by` trailer |
| Existing draft | `ask` | `ask`, `replace`, or `append` |
| Request timeout (ms) | `120000` | Global timeout ceiling |
| Max retries | `2` | Retry budget |
| Local-only mode | `false` | Reject non-loopback endpoints |
| Show status bar widget | `true` | Show the status bar item |
| Log level | `warn` | `off`, `error`, `warn`, `info`, `debug` |

Each profile also supports: label, provider, API format, base URL, model, fallback model,
context window, temperature, max output tokens, request timeout, custom headers, auth style,
and auth header name. Credentials are separate and live in PasswordSafe. Custom headers are
stored in plugin state, so do not put secrets in them.

## How it works

```
selected changes
  -> Git status + bounded diffs (read-only)
  -> metadata, classification, grouping, ranking
  -> manifest + token budget
  -> optional chunk summarization (map-reduce)
  -> prompt with untrusted-diff delimiters
  -> redacted provider request (timeout, cancel, no redirects)
  -> parse, repair, validate Conventional Commits
  -> commit message field
```

No unchanged files are sent. The manifest always lists every analysed file, and every
omission is reported to you.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| Action is missing | Reload the IDE after installing. Enable the bundled Git plugin. |
| "No staged changes" | Commit Generator uses checked files when launched from the Commit panel. If you start from the tool window, enable **Unstaged changes** or choose **Analyze all pending changes**. |
| Message is generated but not inserted | Open the **Commit** tool window and press **Insert** in Commit Generator, or run generation from the Commit panel toolbar. |
| 401 or 403 | Re-enter the API key for the active profile. |
| 429 or quota errors | Wait, reduce the token budget, or use another profile. |
| "Model not found" | Run **Select Model** or correct the model id. |
| Local model connection refused | Start the server and check its port; enable Local-only mode for safety. |
| Message is too generic | Use Detailed mode, stage fewer files, or increase the token budget. |
| Very large repository is slow | Use Smart/Quick, lower the max file count, or add exclusion globs. |
| Proxy required | Version 1 uses the JDK HTTP client and does not read IDE proxy settings; use a local model or a reachable endpoint. |

## FAQ

**Does Commit Generator commit for me?** No. It only writes into the commit message field.

**Does it send my whole repository?** No. Only the selected changed files, within the
configured budget.

**Does it store my code?** No. There is no telemetry and no server operated by Commit Generator.
Your provider's retention policy applies.

**Can I use it fully offline?** Yes, with a local provider and Local-only mode.

**Which model should I use?** Any chat model the provider offers. Local coding models work
well for small diffs.

**Does it work in remote development?** The plugin runs in the IDE backend and uses the
backend's Git, network, and keychain. It is not designed for browser-only clients.

**Can I keep several providers?** Yes. Profiles are independent, and switching only changes
the active one.

**Does it work with monorepos?** Yes. Files are grouped by package or directory.

**Who pays for the API usage?** You, through your provider account.

## Privacy

See [PRIVACY.md](PRIVACY.md) for the exact data flow, what is redacted, and what is never
collected.

## Security

See [SECURITY.md](SECURITY.md) for guarantees, the threat model, and how to report a
vulnerability.

## Development

```bash
./gradlew test            # unit tests
./gradlew buildPlugin     # build the installable ZIP
./gradlew verifyPlugin    # compatibility verification
```

The product is implemented in Kotlin. `src/main/kotlin/.../core` has no IntelliJ dependency
and is covered by unit tests. Architecture tests enforce the security invariants.

See [DEVELOPMENT.md](DEVELOPMENT.md), [CONTRIBUTING.md](CONTRIBUTING.md), and
[ARCHITECTURE.md](ARCHITECTURE.md).

## Documentation

| Document | Contents |
| --- | --- |
| [README](README.md) | Features, install, providers, commands, settings, FAQ |
| [HELP](HELP.md) | Task-oriented usage and common problems |
| [SUPPORT](SUPPORT.md) | Where to get help and how to report issues |
| [ARCHITECTURE](ARCHITECTURE.md) | Layers, data flow, contracts, invariants |
| [BEST_PRACTICES](BEST_PRACTICES.md) | Safe, cost-effective usage and contributor rules |
| [SECURITY](SECURITY.md) | Security model, threat model, reporting |
| [PRIVACY](PRIVACY.md) | Data flow, redaction, consent, storage |
| [DEVELOPMENT](DEVELOPMENT.md) | Setup, layout, adding providers, testing |
| [CONTRIBUTING](CONTRIBUTING.md) | Contribution workflow |
| [CODE_OF_CONDUCT](CODE_OF_CONDUCT.md) | Community standards |
| [CHANGELOG](CHANGELOG.md) | Release history |
| [RELEASE](RELEASE.md) | Maintainer release checklist |
| [MARKETPLACE](MARKETPLACE.md) | Copy-paste JetBrains Marketplace listing |
| [NOTICE](NOTICE.md) | Third-party notices and trademarks |
| [LICENSE](LICENSE) | MIT license |

## License

MIT. See [LICENSE](LICENSE).
