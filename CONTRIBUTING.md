# Contributing

Thank you for considering a contribution to ISQU Tokenizer Engine.

## Before changing code

1. Read the architecture and compatibility documents.
2. Open or discuss an issue for changes that affect token IDs, encoding behavior, persistence, or public API.
3. Include tests for determinism, round-trip behavior, and compatibility where applicable.
4. Document behavior changes and migration implications.

## Engineering expectations

- Use idiomatic Kotlin and keep production modules independent of Android APIs.
- Preserve stable token IDs within a vocabulary lineage.
- Avoid hidden mutation during encoding and evaluation.
- Keep runtime and tokenizer-training responsibilities separate.
- Prefer explicit types and typed failures over ambiguous nullable or sentinel values.
- Add benchmarks for performance-sensitive changes.
- Do not claim production readiness without reproducible validation evidence.

## Pull requests

A pull request should explain the problem, design choice, compatibility impact, tests performed, and relevant benchmark results.

Do not include credentials, private corpora, personal data, or datasets without documented redistribution rights.
