# Architecture

Commit Generator is a JetBrains IDE plugin that turns a Git change set into a commit message
with an AI provider. This document describes the structure, data flow, and the rules that keep
it safe. The architecture intentionally mirrors the original VS Code extension.

## Design goals

1. Keep commit logic independent of the IDE so it can be tested in plain Kotlin.
2. Treat all repository content as untrusted input.
3. Never mutate Git state.
4. Make every network request pass through one auditable layer.
5. Degrade explicitly: report omissions instead of hiding them.
6. Keep credentials in the operating-system keychain.
7. Ship no unnecessary runtime dependencies.

## Layers

```
+------------------------------------------------------------------+
| ui/  services/  config/  adapters/                               |
| tool window, actions, dialogs, settings, Git, HTTP, PasswordSafe |
+----------------------------------+-------------------------------+
| core/                                                             |
| domain  ports  context  security  message  summary  modes  json   |
| pure Kotlin, no IntelliJ imports, no network, no IDE UI          |
+------------------------------------------------------------------+
```

### `core`

- `domain`: change sets, files, hunks, messages, profiles, errors, selection, settings.
- `ports`: interfaces for Git, providers, HTTP, secrets, config, consent, logging, clock,
  message writing.
- `context`: diff normalization, classification, grouping, ranking, token budgeting,
  trimming, exclusion policy.
- `security`: redaction, sensitive paths, URL policy, output sanitizing, injection guards.
- `message`: prompts, parsing, repair, Conventional Commits validation, grounding, fallback,
  trailers.
- `summary`: chunk planning, summarization, aggregation, cache.
- `json`: a small dependency-free JSON parser and writer for provider payloads.
- `modes`: built-in generation ceilings.

Core is covered by unit tests. An architecture test fails if a core file imports IntelliJ,
Git4Idea, `java.net.http`, coroutines, or starts a process.

### Adapters

| Folder | Responsibility |
| --- | --- |
| `adapters/git` | Git4Idea repository resolution, read-only status/diff/log commands, commit message writer |
| `adapters/providers` | provider presets, wire dialects, authentication, HTTP client, retries, model catalog |
| `secrets` | PasswordSafe credential store |
| `config` | persistent settings, profile storage, consent records, settings UI |
| `services` | composition root and generation orchestration |
| `ui` | tool window, settings page, dialogs, status bar, actions |
| `logging` | metadata-only logger |

## Data flow

```
action or tool window
  -> resolve repository (selected or first open)
  -> resolve active provider profile
  -> collect selected changes with git status/diff (read-only)
  -> classify, group, rank
  -> budget and trim diffs
  -> build manifest + change overview
  -> optional chunk summaries (map-reduce)
  -> prompt with untrusted diff delimiters
  -> consent check for origin/provider
  -> one HTTP choke point (redaction, URL policy, timeout, abort, no redirects)
  -> parse, repair, validate Conventional Commits
  -> grounding warnings
  -> commit message field (never commit)
```

## Key contracts

- `GitPort` produces a `ChangeSet` and reports omissions. It has no mutation methods.
- `AIProvider` hides the wire format behind `generate` and `listModels`.
- `HttpClientPort` is the only transport. It enforces URL policy, redirects, timeouts, and
  cancellation.
- `MessageWriterPort` is the only writer of the IDE commit message field.
- `SecretStorePort` is the only credential source.
- `ConsentPort` gates first contact with a provider origin.
- `ConfigPort` returns an immutable snapshot for one invocation.

## Provider abstraction

Four wire dialects plus a session-based adapter:

| Dialect | Used by |
| --- | --- |
| `openai-chat` | OpenAI, DeepSeek, OpenRouter, LM Studio, llama.cpp, vLLM, custom |
| `anthropic-messages` | Anthropic, custom |
| `gemini-generate` | Google Gemini, custom |
| `ollama-native` | Ollama |
| `opencode-session` | OpenCode local server |

Authentication is resolved once into headers by `AuthResolver`; adapters never see raw keys.
Errors are normalized into one taxonomy so retry and UI behavior are consistent.

## Context engine

1. **Classification**: source, test, docs, config, lockfile, generated, binary, asset, other.
2. **Grouping**: monorepo package first, then top-level directory.
3. **Ranking**: status weight, churn, source over generated, tests lower.
4. **Budgeting**: manifest always included; per-file water-filling; explicit `lines omitted`
   markers.
5. **Map-reduce**: chunks are group-scoped; each chunk is summarized with retry and cache;
   aggregation preserves failed chunks as `[not analyzed: ...]`.
6. **Reporting**: coverage and limitations reach the user.

Modes: Quick (single request), Smart (adaptive), Detailed (forced deep analysis for more than
one file).

## Git access

The plugin uses Git4Idea's `GitLineHandler` and `Git` service. Only four read commands are
used:

- `git status --porcelain=v1 -z`
- `git diff` (staged, working tree, or `HEAD`)
- `git log` for recent subjects
- repository metadata exposed by `GitRepository` (`branch`, `currentRevision`, `root`)

The architecture test rejects `GitCommand.ADD`, `COMMIT`, `RESET`, `RESTORE`, `CHECKOUT`,
`MERGE`, `REBASE`, `STASH`, `UPDATE_INDEX`, `UPDATE_REF`, `RM`, and `MV`.

Staged, unstaged, and untracked changes are selected independently:

- staged: `git diff --cached`
- unstaged: `git diff` (including conflicts)
- both selected for one path: `git diff HEAD`
- untracked: the file is read directly and a synthetic new-file patch is produced

This is the closest JetBrains equivalent of the VS Code extension's Git index behavior.

## Commit message writing

IntelliJ has two commit UIs: a modal dialog and a non-modal Commit tool window. The writer:

1. tries `VcsDataKeys.COMMIT_MESSAGE_CONTROL` from the action data context;
2. tries `Refreshable.PANEL_KEY`;
3. otherwise walks open AWT windows for a component implementing `CommitMessageI`;
4. if no target exists, copies the message to the clipboard and exposes **Insert** in the
   Commit Generator tool window.

Only `CommitMessageI.setCommitMessage` is called. There is no commit API usage.

## Security choke points

| Control | Location |
| --- | --- |
| Redaction | `core/security/SecretRedactor.kt`, applied by `RedactingHttpClient` |
| URL policy | `core/security/UrlPolicy.kt`, used by profile resolution and transport |
| Sensitive paths | `core/security/SensitivePathPolicy.kt`, used by the diff collector |
| Untrusted diffs | `core/security/InjectionGuard.kt` and prompt builders |
| Logging | `logging/CommitGeneratorLogger.kt` |
| Credentials | `secrets/PasswordSafeSecretStore.kt` and `AuthResolver` |
| Consent | `config/CommitGeneratorConsentService.kt` |
| No commit | writer exposes no mutation methods; architecture test enforces it |
| No shell | no `ProcessBuilder` or `Runtime.exec`; Git4Idea handles Git processes |

## Invariants

1. Never commit, stage, amend, checkout, reset, or restore.
2. Keys only in PasswordSafe.
3. One writer for the commit message field.
4. One HTTP layer.
5. Diff content is data.
6. No arbitrary shell execution.
7. No telemetry.
8. No TLS validation bypass.
9. No silent truncation.

Each invariant has an automated check in `src/test/kotlin/.../architecture` or a unit test.

## Testing

- Unit: pure core plus adapters with fakes.
- Contract: provider dialects against recorded JSON responses.
- Security: redaction, URL policy, sensitive paths, injection guards.
- Architecture: layering, read-only Git, no shell, no telemetry, credential schema.
- Manual: run the plugin in a WebStorm development instance and test both commit UIs.

## Extension points

- New provider: add a `ProviderId`, a preset, optionally a dialect, and a mocked test.
- New mode: extend `ModePreset` and the mode list; update docs.
- New output format: extend the message composer and validator.
- New UI surface: implement `MessageWriterPort`; the generation pipeline stays unchanged.

## Trade-offs

- No streaming UI: simpler, cancellable, avoids fighting the commit field.
- No automatic commit: the core safety promise.
- No Git shell execution: read-only Git4Idea commands keep behavior predictable.
- No proxy integration in 1.0: use a local model or a reachable endpoint.
- No runtime JSON library: the small parser is tested and keeps the plugin archive small.
- Kotlin standard library is provided by the IDE, not bundled.
- Target range is capped at 2026.3 until newer releases are verified.
