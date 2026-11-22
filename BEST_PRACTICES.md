# Best practices

## Use a small, intentional change set

The best commit messages come from a focused change. Stage related files, keep unrelated
refactors out of the same commit, and let Commit Generator describe one coherent intent.

## Review before committing

AI output is a draft. Check:

- the Conventional Commit type and scope,
- that the subject describes the actual change,
- that the body does not invent behavior,
- that breaking-change footers are accurate,
- that no sensitive path slipped through.

## Prefer staged-only for normal use

The default staged-only posture matches the reference extension and keeps the message focused.
Enable unstaged analysis only when your JetBrains workflow does not use the Git index.

## Keep sensitive paths excluded

The built-in sensitive-path rules cannot be disabled. Use **Allow Sensitive Files For Next
Generation** only when you understand what will be sent and have reviewed the payload preview.

## Use local models for confidential code

Enable **Local-only mode** and use Ollama, LM Studio, llama.cpp, vLLM, or a trusted local
OpenCode server. Local-only mode rejects non-loopback endpoints before any request.

## Protect credentials

- Use a dedicated API key with a spending limit.
- Rotate keys if they may have been exposed.
- Do not paste keys into project files or commit messages.
- Use **Clear API Key** before sharing a machine profile.

## Control cost

- Quick mode uses fewer tokens and one request.
- Smart mode adapts; use it for everyday work.
- Detailed mode can make several requests for large changes.
- Lower the max file count and diff token budget for large repositories.
- Add exclusion globs for generated code, snapshots, and vendored dependencies.

## Use the payload preview

Before using an unfamiliar provider or a confidential repository, open **Preview Payload** and
check the redaction and the file list. The preview makes no network request.

## Keep profiles machine-local

Profiles are application-level state. Do not try to commit them or share them through the
repository. Each developer should configure their own provider and key.

## Contributor rules

- Keep `core` pure: no IntelliJ imports, no network, no process execution.
- Never add a Git mutation command.
- Never log prompts, diffs, headers, or credentials.
- Report every omission and truncation.
- Add tests for security-sensitive changes.
- Update the documentation when behavior changes.
- Keep user-facing strings ASCII where possible to avoid encoding issues.
