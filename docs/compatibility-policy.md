# Compatibility Policy

## Current status

The project is in early development. Public APIs, tokenizer algorithms, and persisted formats are not stable. No production API or tokenizer format compatibility guarantee is made before the first stable release.

The build and consumer validation baseline is documented in [Build and Ecosystem Compatibility](build-and-compatibility.md).

## API versioning

The project intends to follow Semantic Versioning after the 1.0.0 release:

- MAJOR: incompatible public API or behavioral changes.
- MINOR: backward-compatible functionality.
- PATCH: backward-compatible fixes.

Before 1.0.0, breaking changes may occur and will be documented in the changelog.

## Build JDK and production JVM target

The current build and test environment uses JDK 25 LTS, Gradle Wrapper 9.9.1, and Kotlin Gradle Plugin 2.4.21.

Production artifacts target Java 11 bytecode and Java 11 JDK APIs. This is the selected deployment floor for the Android API 33 consumer target, not the JDK used to build the library. Java 21 and Java 25 JVM consumers are supported by the Java 11 artifact compatibility direction and are covered by the Java 21 consumer fixture.

Kotlin and Java production targets must remain aligned:
- Kotlin `jvmTarget = 11` and `-Xjdk-release=11`.
- Java `sourceCompatibility`, `targetCompatibility`, and `options.release` = 11.
- Gradle Module Metadata advertises `org.gradle.jvm.version = 11`.

Do not use JDK APIs newer than Java 11 in the platform-independent production modules unless the minimum consumer platform is intentionally raised or a separately validated compatibility mechanism is introduced.

## Test runtime

Tests use JDK 25, JVM 25 test bytecode, JUnit Jupiter 6.1.3, and JUnit Platform 6.1.3. JUnit 6 requires Java 17 or newer at runtime. Test compilation and runtime dependency attributes are configured separately from production artifacts; test dependencies must not leak into published API/runtime dependencies.

## Android compatibility

The Android packaging consumer uses AGP 9.4.0, compileSdk 36, minSdk 33, targetSdk 36, and Java 11 source/target compatibility. CI runs D8/R8 debug and minified-release packaging.

This is not yet a device-runtime guarantee. The project must execute its tokenizer conformance suite on an Android API 33 emulator or device before making an on-device runtime support claim.

## Gradle and Kotlin compatibility

KGP 2.4.21 is the current stable Kotlin release at this review. Its fully-tested Gradle range currently ends at 9.7.0, while ISQU deliberately uses security-patched Gradle 9.9.1. CI validates this exact pair; it is project-tested, not vendor-fully-tested. Reassess after the next stable Kotlin release. Do not use a Kotlin release candidate in the production build by default.

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
