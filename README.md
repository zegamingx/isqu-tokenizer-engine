# ISQU Tokenizer Engine

A standalone Kotlin/JVM tokenizer library designed for local, deterministic, and reproducible text tokenization.

> **Project status: Early development.** This repository currently contains project structure and design contracts only. There is no stable public API, production tokenizer implementation, or published Maven artifact yet.

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
| `tokenizer-core` | Runtime encoding/decoding, vocabulary snapshots, special tokens, normalization contracts, and tokenizer identity. |
| `tokenizer-training` | Corpus processing and controlled vocabulary/model training workflows. |
| `tokenizer-testkit` | Shared test fixtures, compatibility vectors, and contract-test utilities. |

Algorithms and public interfaces are not frozen. BPE, Unigram, byte fallback, normalization, and pre-tokenization remain research candidates until their behavior and compatibility requirements are accepted.

## Design invariants

1. Existing token IDs must never be silently reassigned or recycled within a vocabulary lineage.
2. Encoding must be deterministic for a given tokenizer snapshot and configuration.
3. A tokenizer snapshot must have an explicit format version and verifiable fingerprint.
4. Vocabulary evolution must be explicit; restoring neural weights must not silently erase newer tokenizer state.
5. Model checkpoints must identify the tokenizer snapshot used to create them.
6. Core runtime APIs must not depend on ArgusA, Android UI, storage frameworks, or a specific neural engine.
7. Evaluation must not mutate tokenizer state.

## Build environment

- JDK 25 LTS for the Gradle runtime, Java compilation, and CI tests.
- Gradle Wrapper 9.9.1, with the distribution SHA-256 pinned.
- Kotlin Gradle Plugin 2.4.21 (latest stable release at this review).
- Production bytecode and JDK API baseline: Java 11, aligned with Android API 33 core-library availability.
- Test compilation target and test runtime: JDK 25.
- JUnit Jupiter and JUnit Platform 6.1.3.
- Published Gradle Module Metadata target: `org.gradle.jvm.version = 11`.
- JVM consumer target: Java 21; Android packaging consumer: compileSdk/targetSdk 36, minSdk 33.

Java 11 is the deployment compatibility floor for Android 13/API 33; it is not the development JDK. Modern JVM consumers are validated separately at Java 21. JDK 25 is selected because it is the current LTS line; Java 27 is a newer non-LTS feature release. The Kotlin 2.4.21 + Gradle 9.9.1 pair is project-tested in CI but remains outside Kotlin's currently documented fully-tested Gradle range. See [Build and Ecosystem Compatibility](docs/build-and-compatibility.md).

Build and test:

```bash
./gradlew clean build
```

Publish snapshot artifacts to Maven Local:

```bash
./gradlew publishToMavenLocal
```

Run the modern Kotlin/JVM consumer fixture after publishing:

```bash
./gradlew -p compatibility/consumer clean verifyPublishedArtifacts build
```

Run the Android 13/API 33 packaging fixture (D8/R8):

```bash
./gradlew -p compatibility/android-consumer clean verifyAndroidArtifacts assembleDebug assembleRelease
```

## Dependency coordinates

No artifacts have been published. Maven coordinates and the first release version will be announced after the API, serialization format, compatibility tests, and publication metadata are ready.

The intended group ID is `io.github.zegamingx`; it remains provisional until namespace verification with the publishing service.

The local snapshot artifact IDs are:
- `io.github.zegamingx:isqu-tokenizer-core:0.1.0-SNAPSHOT`
- `io.github.zegamingx:isqu-tokenizer-training:0.1.0-SNAPSHOT`
- `io.github.zegamingx:isqu-tokenizer-testkit:0.1.0-SNAPSHOT`

## Documentation

### Architecture and contracts

- [Architecture](docs/architecture.md)
- [Tokenization contract](docs/tokenization-contract.md)
- [Tokenizer format and persistence](docs/tokenizer-format.md)
- [Compatibility policy](docs/compatibility-policy.md)
- [Build and ecosystem compatibility](docs/build-and-compatibility.md)
- [Architecture Decision Records](docs/adr/README.md)
- [Modern build and Android API 33 baseline (ADR 0002)](docs/adr/0002-modern-build-and-android-baseline.md)
- [Initial byte-level BPE runtime profile (ADR 0003)](docs/adr/0003-initial-runtime-profile.md)
- [Implementation Readiness Audit (G7)](docs/implementation-readiness-audit.md)

### Research

- [Algorithm research and selection](docs/algorithm-research.md)
- [Corpus provenance, rights, and data governance](docs/corpus-provenance.md)
- [Vocabulary sizing strategy and evaluation protocol (ADR 0001)](docs/adr/0001-vocabulary-sizing-and-evaluation.md)

### Project maintenance

- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)
- [Changelog](CHANGELOG.md)

## License

This project is licensed under the [Apache License, Version 2.0](LICENSE) (`Apache-2.0`).

When redistributing this project or derivative works, include a copy of the license. Modified files must carry prominent notices stating that they were changed, and applicable copyright, patent, trademark, and attribution notices must be retained. If a `NOTICE` file is included in a distribution, its applicable attribution notices must also be preserved as required by the license.

## Scope boundary

ISQU Tokenizer Engine is a tokenizer library, not a language model, neural-network runtime, embedding manager, or model-weight training framework. Integrations may connect token IDs to model embeddings, but that responsibility belongs to the consuming model system.
