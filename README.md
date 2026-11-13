# ISQU Tokenizer Engine

A standalone Kotlin/JVM tokenizer library designed for local, deterministic, and reproducible text tokenization.

> **Project status: Early development.** This repository currently contains project structure and design contracts only. There is no stable public API, production implementation, or published Maven artifact yet.

## Goals

- Provide a reusable tokenizer runtime independent of any application or neural-network architecture.
- Separate tokenizer inference (encoding and decoding) from vocabulary training and evolution.
- Preserve token identity across vocabulary extensions and checkpoint recovery.
- Make tokenizer snapshots versioned, reproducible, verifiable, and portable.
- Support Android and general Kotlin/JVM applications without depending on Android APIs.
- Keep core operation local and offline.

## Planned modules

| Module | Responsibility |
| --- | --- |
| \`tokenizer-core\` | Runtime encoding/decoding, vocabulary snapshots, special tokens, normalization contracts, and tokenizer identity. |
| \`tokenizer-training\` | Corpus processing and controlled vocabulary/model training workflows. |
| \`tokenizer-testkit\` | Shared test fixtures, compatibility vectors, and contract-test utilities. |

Algorithms and public interfaces are not frozen. BPE, unigram, byte fallback, normalization, and pre-tokenization strategies will be evaluated before becoming compatibility commitments.

## Design invariants

1. Existing token IDs must never be silently reassigned or recycled within a vocabulary lineage.
2. Encoding must be deterministic for a given tokenizer snapshot and configuration.
3. A tokenizer snapshot must have an explicit format version and verifiable fingerprint.
4. Vocabulary evolution must be explicit; restoring neural weights must not silently erase newer tokenizer state.
5. Model checkpoints must identify the tokenizer snapshot used to create them.
6. Core runtime APIs must not depend on ArgusA, Android UI, storage frameworks, or a specific neural engine.
7. Evaluation must not mutate tokenizer state.

## Build environment

- JDK 17 or newer to run the build.
- Kotlin 2.4.21.
- Gradle 9.7.0 (selected for the Kotlin Gradle plugin's fully supported compatibility range).

Generate the Gradle wrapper and build:

\`\`\`bash
gradle wrapper --gradle-version 9.7.0
./gradlew clean build
\`\`\`

## Dependency coordinates

No artifacts have been published. Maven coordinates and the first release version will be announced after the API, serialization format, compatibility tests, and publication metadata are ready.

The intended group ID is \`io.github.zegamingx\`; it remains provisional until namespace verification with the publishing service.

## Documentation

- [Architecture](docs/architecture.md)
- [Tokenizer format and persistence](docs/tokenizer-format.md)
- [Compatibility policy](docs/compatibility-policy.md)
- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)
- [Changelog](CHANGELOG.md)

## License

This project is licensed under the [Apache License, Version 2.0](LICENSE) (\`Apache-2.0\`).

When redistributing this project or derivative works, include a copy of the license. Modified files must carry prominent notices stating that they were changed, and applicable copyright, patent, trademark, and attribution notices must be retained. If a \`NOTICE\` file is included in a distribution, its applicable attribution notices must also be preserved as required by the license.

## Scope boundary

ISQU Tokenizer Engine is a tokenizer library, not a language model, neural-network runtime, embedding manager, or model-weight training framework. Integrations may connect token IDs to model embeddings, but that responsibility belongs to the consuming model system.
