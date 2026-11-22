# Changelog

All notable changes are documented here. The format follows Keep a Changelog and the
project uses semantic versioning.

## [1.0.0] - 2026-11-22

### Added

- First release for WebStorm and JetBrains IDEs.
- AI commit message generation from staged, unstaged, and untracked Git changes.
- Reads the files checked in the JetBrains Commit panel, so commit checkboxes work without
  staging files into the Git index first.
- Eleven provider presets: DeepSeek, OpenAI, Anthropic, Google Gemini, OpenRouter, Ollama,
  LM Studio, llama.cpp, vLLM, OpenCode, and custom OpenAI-compatible endpoints.
- Multiple provider profiles, model discovery, manual model entry, connection tests, and
  profile validation.
- Quick, Smart, and Detailed modes with token budgeting, file ranking, explicit omissions,
  and map-reduce summarization for large change sets.
- Conventional Commits parsing, repair, validation, grounding checks, optional body,
  breaking-change footers, issue keys from branches, attribution trailers, and output
  language selection.
- PasswordSafe credential storage, secret redaction, sensitive-path blocking with a one-run
  override, egress consent, local-only mode, URL policy, no redirects, and TLS validation.
- Commit Generator tool window, settings page, commit-panel action, status bar widget, and
  command palette actions.
- Payload preview, undo, regenerate, cancel, copy, insert, local heuristic fallback, and
  result details.
- Unit, contract, security, and architecture tests.
- Complete documentation and JetBrains Marketplace metadata.

### Changed

- Redesigned the tool window with full-width sections, a consistent label column, and
  left-aligned checkboxes. Controls no longer drift to the right or overflow narrow
  sidebars.
- Split action rows into compact groups so every button stays inside the window at any
  width.
- Polished the provider profile manager.
- Verified compatibility through WebStorm 2024.1-2026.3 and IntelliJ IDEA 2026.3.

### Fixed

- Checked files from the JetBrains Commit panel are analysed correctly, including both
  staged and unstaged edits in each checked file.
- Removed the recursive commit-message read and window traversal that could cause a
  `StackOverflowError` when locating the commit message field.

### Security

- No telemetry.
- No Git mutation APIs.
- No arbitrary shell execution.
- Credentials never written to plugin state or logs.
- Diff content treated as untrusted data with injection detection.

### Known limitations

- No streaming UI.
- No IDE proxy integration.
- The classic JetBrains commit tool window does not stage files. Commit Generator reads the
  files checked there, so no staging step is required. Staged-only generation still uses the
  Git index.
- Verified against WebStorm 2024.1, 2025.1, 2026.1, and 2026.3; newer builds are supported
  through the declared range but should be verified before widening it.
