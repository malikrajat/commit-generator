# Contributing

Thanks for improving Commit Generator.

## Before you start

- Search existing issues before opening a new one.
- For a security problem, follow [SECURITY.md](SECURITY.md) instead of opening a public issue.
- For a large feature, open an issue first so the design can be discussed.

## Development workflow

1. Fork the repository.
2. Create a focused branch.
3. Make the change.
4. Run `./gradlew test`.
5. Run `./gradlew buildPlugin`.
6. Open a pull request with a clear description and test notes.

## What makes a good change

- It respects the architecture rules in [ARCHITECTURE.md](ARCHITECTURE.md).
- It never adds Git mutation, arbitrary shell execution, telemetry, or a TLS bypass.
- It includes tests for new logic and security-sensitive behavior.
- It updates the documentation when user-facing behavior changes.
- It keeps the plugin usable in both modal and non-modal commit UIs.
- It does not add a runtime dependency without a strong reason.

## Pull request checklist

- [ ] `./gradlew test` passes.
- [ ] `./gradlew buildPlugin` succeeds.
- [ ] New behavior is documented.
- [ ] Security invariants are preserved.
- [ ] No credentials, private code, or personal data are included in screenshots or fixtures.
- [ ] Commit messages follow Conventional Commits.

## Reporting bugs

Include:

- WebStorm/IDE version and build number.
- Plugin version.
- Provider and model (without the API key).
- Steps to reproduce.
- Expected and actual behavior.
- Relevant metadata-only log lines or diagnostics output.

Never include API keys, private repository content, or raw prompts.

## Feature requests

Describe the problem first, then the proposed solution. Explain how it fits the safety model and
whether it affects both commit UIs.

## Code of conduct

By participating, you agree to follow [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
