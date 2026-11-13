# Compatibility Policy

## Current status

The project is in early development. Public APIs, tokenizer algorithms, and persisted formats are not stable. No production compatibility guarantee is made before the first stable release.

The current build and consumer validation baseline is documented in [Build and Ecosystem Compatibility](build-and-compatibility.md).

## API versioning

The project intends to follow Semantic Versioning after the 1.0.0 release:

- MAJOR: incompatible public API or behavioral changes.
- MINOR: backward-compatible functionality.
- PATCH: backward-compatible fixes.

Before 1.0.0, breaking changes may occur and will be documented in the changelog.

## Build and JVM compatibility

The current build baseline uses Gradle Wrapper 9.9.1, Kotlin Gradle Plugin 2.4.21, and JDK 25 in CI. The build runs on JDK 17 or newer.

Published Kotlin and Java classes target Java 8 bytecode and Java 8 APIs. Java `sourceCompatibility`, `targetCompatibility`, Java `options.release`, and Kotlin `jvmTarget`/JDK API release constraints must remain aligned. The generated Gradle Module Metadata must advertise `org.gradle.jvm.version = 8`.

The current consumer smoke test uses Kotlin Gradle Plugin 2.4.10 and Java 11 target, matching the Kotlin and Java target levels observed in ArgusA. It uses a security-patched Gradle release. See the build compatibility document for the exact test matrix and the distinction between tested and vendor-fully-supported combinations.

Changing the project build JDK must not silently raise the published JVM target or the minimum Java API level.

## Tokenizer format versioning

Persisted tokenizer data has its own format version, independent of the library version.

A library release must not silently reinterpret an unknown or unsupported format version. It should fail with a clear, typed error or use an explicitly supported migration path.

## Vocabulary compatibility

Within a vocabulary lineage, existing token IDs are immutable and must never be recycled. Any change that alters tokenization behavior must result in a new snapshot identity.

A tokenizer fingerprint must cover all data that affects encoding behavior, not just vocabulary size or token strings.

## Model integration

A model checkpoint must record the tokenizer fingerprint it expects. A consumer must verify compatibility before inference, training, or evaluation.

Changing tokenizer segmentation may invalidate direct metric comparisons. Baselines and candidates must be evaluated under compatible tokenizer contracts.

## Deprecation

Once the stable API is released, deprecated APIs should be documented and retained for a reasonable migration window before removal, except where security or data-corruption risks require urgent action.
