# Security Policy

## Supported versions

| Version | Supported |
| --- | --- |
| 1.1.x | Yes |

## Reporting a vulnerability

Please do not open a public issue for security problems. Use a private security advisory:

`https://github.com/malikrajat/commit-generator/security/advisories/new`

Include the affected version, a short description, reproduction steps, and the impact you
observed. You will receive an acknowledgement, and a fix will be prepared before any public
disclosure.

## Security model

Commit Generator handles source code, credentials, and outbound network requests, so the following
guarantees are treated as invariants. They are enforced by code structure and automated tests.

| Invariant | Enforcement |
| --- | --- |
| The plugin never executes a Git commit, stage, amend, checkout, reset, or restore | Commit Generator only calls `CommitMessageI.setCommitMessage`; Git access exposes only status/diff/log; architecture tests reject mutation command constants |
| API keys are never stored in plugin settings | Keys live in JetBrains PasswordSafe; the profile state schema has no credential field |
| Only the commit-message writer can modify the commit field | Single-writer architecture rule with an automated check |
| All external traffic goes through one HTTP layer | `JdkHttpTransport` is the only `java.net.http` call site and is wrapped by `RedactingHttpClient` |
| Diff content is untrusted data | Diffs are wrapped in `<untrusted_diff>` delimiters, closing tags are neutralized, and instruction-like content is reported as a warning |
| No arbitrary shell execution | No `ProcessBuilder`, `Runtime.exec`, or shell invocation. Git4Idea runs Git with fixed read-only arguments |
| No telemetry | No telemetry or analytics APIs are used |
| TLS validation is never disabled | No trust-all manager, hostname verifier override, or `NODE_TLS_REJECT_UNAUTHORIZED` equivalent |
| Redirects are not followed | The JDK client is built with `Redirect.NEVER`; a 3xx response is treated as an error |
| Credentials never appear in logs | The logger accepts metadata only and redacts stray string values |
| Consent is required before the first send to an origin | `CommitGeneratorConsentService` records origin + provider + routed model |

## Threat model

### Credential theft through project settings

A malicious repository could try to add a profile that points at an attacker-controlled
endpoint and capture the stored key.

Mitigation: provider profiles are application-level plugin state, not project settings. A
repository cannot add or redirect a profile. Changing a base URL invalidates consent for the
new origin because consent is keyed by origin and provider.

### Credential leakage through logs or errors

Provider error bodies, prompts, headers, and diffs are never written to logs. Error messages
expose only normalized codes, status codes, provider, and model ids. Provider error details
are redacted and truncated before they are retained as an error cause.

### Secret disclosure to a third-party model

Before any request is serialized, the payload passes through a redaction stage that removes
private keys, cloud keys, GitHub and Slack tokens, JWTs, bearer tokens, password assignments,
credential-bearing connection strings, and high-entropy mixed tokens. Sensitive paths
(`.env`, key material, `.ssh`, `.aws`, credential stores) are excluded by default.

### Prompt injection from diff content

Repository content is data. The prompt wraps each diff in `<untrusted_diff>` delimiters,
neutralizes closing tags inside the content, and the output is parsed and validated as a
commit message, never executed. Detection warnings are shown to the user.

### Network interception or redirection

Remote endpoints must use HTTPS. Plain HTTP is accepted only for loopback addresses used by
local model servers. Redirects are not followed, and certificate validation cannot be
disabled. The URL policy also blocks link-local and cloud metadata addresses.

### Local model server compromise

A local server can return malicious text. The output is parsed and validated as a commit
message and never executed. The user reviews the message before committing. A compromised
local server can still read the redacted prompt, so use Local-only mode only with servers you
trust.

### Supply chain

The plugin ships with no third-party runtime libraries. JSON handling is implemented in the
repository and tested. Kotlin standard-library classes are supplied by the IDE. The build uses
the Gradle wrapper and pinned plugin versions.

## Out of scope

- Retention, logging, or model training performed by the AI provider you configure.
- A compromised local machine, IDE installation, or other installed plugins.
- Denial of service through extremely large repositories beyond configured limits.
- Credentials deliberately pasted into a diff that redaction cannot recognize.
- A malicious provider that ignores the prompt and returns arbitrary commit text; the user
  still reviews the message before committing.

## User responsibilities

- Protect your provider API keys and rotate them if exposed.
- Review the generated message before committing.
- Use Local-only mode for confidential code when appropriate.
- Keep WebStorm and the plugin updated.
- Do not enable **Allow sensitive files once** unless you understand the risk.

## Security-sensitive files

- `core/security/SecretRedactor.kt`
- `core/security/UrlPolicy.kt`
- `core/security/SensitivePathPolicy.kt`
- `core/security/InjectionGuard.kt`
- `adapters/providers/http/JdkHttpTransport.kt`
- `adapters/providers/http/RedactingHttpClient.kt`
- `adapters/providers/AuthResolver.kt`
- `secrets/PasswordSafeSecretStore.kt`
- `adapters/git/CommitMessageTarget.kt`
