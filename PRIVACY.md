# Privacy

Commit Generator is designed for private, review-first commit messages. This document explains
exactly what leaves your machine, what stays local, and what is never collected.

## Summary

- **No telemetry.** No analytics, crash reporting, usage pings, or background network calls.
- **No Commit Generator server.** Requests go directly from your IDE to the provider endpoint you configure.
- **Credentials stay in the OS keychain.** PasswordSafe stores them; they are not written to project files or plugin XML.
- **Diff content is redacted and bounded.** Only selected changed files, within configured limits, are sent.
- **You approve the destination.** The first send to an origin requires consent.
- **Local-only mode is available.** It rejects every non-loopback endpoint.

## What is sent

When you press Generate, Commit Generator may send:

1. The system prompt (instructions and repository style samples).
2. A change manifest with file paths, status letters, and insertion/deletion counts.
3. A change overview with the primary area, scope candidates, and change counts.
4. Bounded diff segments for selected files.
5. Optional chunk summaries when map-reduce is used.

The request also contains the provider model name, temperature, and maximum output token
count. The HTTP request necessarily includes the provider endpoint, headers, and your API key
for authentication.

## What is not sent

- Unchanged files.
- Files excluded by built-in safety rules, globs, size, or file-count limits.
- Binary content (binary files may appear in the manifest without their bytes).
- Files classified as sensitive unless you explicitly enable the one-run override.
- Credentials to any model provider other than the one you configured; the key is only used
  as an auth header.
- Your IDE settings, project name, machine name, or usage statistics.
- Telemetry of any kind.

Every omission is reported in the result details and in the payload preview.

## Redaction

Before a request body is serialized, Commit Generator replaces credential-shaped content with
`[REDACTED:...]` markers. This includes:

- PEM private keys
- AWS access keys
- GitHub tokens
- OpenAI-style API keys
- Slack tokens
- Google API keys
- JWTs
- Bearer tokens
- Password and secret assignments
- Credential-bearing connection strings
- High-entropy mixed tokens

Redaction is conservative: it may remove content that merely looks like a secret. That is
intentional. Review the payload preview if you need to know exactly what will be sent.

## Consent

The first request to an origin (for example `https://api.openai.com`) requires confirmation.
The dialog shows the origin, profile, model, mode, file count, and estimated token count. You
can choose:

- **Send and remember** - store consent for this origin + provider (+ OpenCode routed model).
- **Send once** - send without storing consent.
- **Cancel** - no request is made.

Consent records are stored in plugin state, not in the repository. They can be cleared with
**Commit Generator: Revoke Egress Consent**.

## Local-only mode

Local-only mode rejects any endpoint that is not loopback (`localhost`, `127.0.0.1`, `::1`).
Use it with Ollama, LM Studio, llama.cpp, vLLM, or a local OpenCode server. Remote providers
are rejected before a request is made.

## Credential storage

| Data | Storage |
| --- | --- |
| API keys and OpenCode passwords | OS keychain via JetBrains PasswordSafe |
| Provider profiles | Plugin state XML, without credentials |
| Consent records | Plugin state XML |
| Last generated message | In memory only; it is not persisted |
| Change snapshot for Regenerate | In memory only until the next generation or project close |
| Map-reduce summaries | In memory only, 30-minute cache |
| Logs | IDE log, metadata only, redacted |

There is no cloud sync of profiles or credentials.

## Provider retention

Your provider's privacy policy governs what happens after the request reaches it. Commit Generator
cannot control provider retention, logging, or model training. Use a local model if the code
must not leave your machine.

## Data subject requests

Because Commit Generator operates no server and collects no personal data, there is no central
data store to delete. To remove local data:

1. Clear stored credentials with **Clear API Key For Profile**.
2. Clear consent with **Revoke Egress Consent**.
3. Remove profiles in **Manage Provider Profiles**.
4. Uninstall the plugin.

## Contact

Privacy questions: open an issue at <https://github.com/malikrajat/commit-generator/issues>.
