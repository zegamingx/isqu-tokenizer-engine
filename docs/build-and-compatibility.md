# Build and Ecosystem Compatibility

- **Status:** Stage 6 validation contract
- **Last reviewed:** 2026-11-13
- **Scope:** build environment, published JVM variants, local Maven publication, consumer resolution

## 1. Goals

The build must separate the JDK used to run Gradle from the Java bytecode/API baseline published by the library.

A consumer must not need to use the same JDK, Gradle version, or Kotlin Gradle Plugin version as the library author. The published artifact's JVM metadata must accurately describe the bytecode/API baseline, and the POM/module metadata must describe the correct module dependencies.

This document describes the tested project baseline. It is not a promise that every JDK, Kotlin, Gradle, Android Gradle Plugin, or Android API-level combination is supported.

## 2. Build baseline

| Component | Project baseline | Reason |
| --- | --- | --- |
| Gradle Wrapper | 9.9.1 | Security-patched Gradle 9.9 line; wrapper distribution checksum is pinned |
| Kotlin Gradle Plugin | 2.4.21 | Stable Kotlin release |
| Gradle runtime JDK | JDK 17 minimum; CI uses JDK 25 | Gradle 9.9.1 runs on supported JDK 17–27; JDK 25 is the CI reference |
| Kotlin language/API version | 2.4 | Explicit compiler language and API baseline |
| JVM bytecode target | Java 8 | Broad JVM compatibility |
| Java API release | Java 8 | Prevents accidental calls to newer JDK APIs |
| Gradle variant metadata | `org.gradle.jvm.version = 8` | Must match the published Java target |
| Test framework | JUnit Jupiter 5.14.4 | Current Java 8-compatible JUnit 5 maintenance release |

The project does not pin a Java 17 toolchain. Compilation runs on the JDK that starts Gradle, while Kotlin's `-Xjdk-release=8` and Java's `options.release = 8` restrict the published code to the Java 8 API surface and bytecode. Java source/target compatibility is also set to 8 so Gradle publishes the correct JVM variant metadata.

Tests use JUnit 5.14.4 because JUnit 6.x requires Java 17 or newer, while the project deliberately validates test dependency resolution and production artifact metadata at a Java 8 target. JUnit 5.14.4 continues to support Java 8 and remains in maintenance support.

### Kotlin/Gradle support caveat

The Kotlin compatibility table lists KGP 2.4.20–2.4.21 as fully tested with Gradle through 9.7.0. It also permits trying newer Gradle versions, while warning that deprecations or unavailable features may occur. Gradle 9.9.1 is selected because Gradle 9.9.0 and earlier in the 9.x line are affected by recent security advisories; the 9.9.1 release fixes those issues.

Therefore, KGP 2.4.21 + Gradle 9.9.1 is an intentionally validated project combination, but it is **outside the KGP vendor's fully-tested Gradle range** at this review date. The build workflow is the acceptance gate for this exact combination. The repository must not describe this pair as vendor-certified full compatibility.

Do not replace Gradle 9.9.1 with 9.7.0 merely to fit the KGP fully-tested table: Gradle 9.7.0 falls in the affected range for the daemon-registry security advisory. Do not switch the build to Kotlin 2.5.0-RC to resolve the mismatch; a release candidate is not the stable production baseline.

## 3. Consumer compatibility target

The consumer fixture under `compatibility/consumer` models the Kotlin/JVM side of the current ArgusA dependency environment:

| Component | Consumer fixture |
| --- | --- |
| JDK running Gradle | 25 |
| Gradle | 9.9.1 security-patched release |
| Kotlin Gradle Plugin | 2.4.10 (the version observed in ArgusA) |
| Consumer Java/Kotlin target | Java 11 |
| Resolved ISQU module set | core, training, testkit |
| Expected ISQU Gradle variant | JVM 8 |

The ArgusA repository was observed using Gradle 9.8.1. The fixture uses 9.9.1 instead of reproducing that exact wrapper because Gradle 9.8.1 is in the affected range of the recent Gradle security advisories. It retains the ArgusA Kotlin plugin version and Java 11 target. This is a compatibility smoke test, not a change to ArgusA.

The fixture verifies that all three Maven-local artifacts resolve, that Gradle selects a compatible JVM 8 variant for a Java 11 consumer, and that the consumer's Java and Kotlin source sets compile. The current ISQU modules intentionally contain no public tokenizer API yet, so this stage cannot test application imports or runtime tokenizer behavior.

## 4. Artifact coordinates

Local Maven publication uses:

| Module | Maven coordinates |
| --- | --- |
| tokenizer-core | `io.github.zegamingx:isqu-tokenizer-core:0.1.0-SNAPSHOT` |
| tokenizer-training | `io.github.zegamingx:isqu-tokenizer-training:0.1.0-SNAPSHOT` |
| tokenizer-testkit | `io.github.zegamingx:isqu-tokenizer-testkit:0.1.0-SNAPSHOT` |

The group ID is provisional pending namespace verification with the eventual publication service. No public Maven Central release is created by Stage 6.

Each module publishes:
- The Java component and Gradle Module Metadata.
- A Maven POM with Apache-2.0 license and SCM/project metadata.
- A sources JAR.
- Transitive API dependency declarations for module-to-module dependencies.

This stage validates `publishToMavenLocal`, not public namespace ownership, signatures, Javadoc publication, Maven Central staging, or release provenance.

## 5. Wrapper policy

The checked-in Gradle Wrapper is the canonical project entry point.

- Pin Gradle 9.9.1.
- Pin the distribution SHA-256 in `gradle-wrapper.properties`.
- Use binary-only (`bin`) distribution for CI and ordinary development.
- Enable distribution URL validation.
- Validate the Wrapper JAR in CI.
- Keep `gradlew`, `gradlew.bat`, `gradle-wrapper.jar`, and `gradle-wrapper.properties` in version control.
- Do not commit local Gradle caches or build outputs.

## 6. Validation workflow

The CI workflow must:

1. Start with JDK 25.
2. Generate the pinned Gradle Wrapper from the pinned Gradle 9.9.1 distribution.
3. Validate the generated Wrapper JAR.
4. Run `clean build` and all available module tests.
5. Publish all modules to Maven Local.
6. Build the separate consumer fixture with KGP 2.4.10 and Java 11 target.
7. Assert that the resolved ISQU Gradle variants advertise JVM 8.
8. Commit the generated wrapper files to `main` only after all validation steps pass.

The repository currently has no production tokenizer source and no module test cases. A successful build at this stage validates the Gradle/Kotlin configuration, dependency resolution, Maven publication structure, JVM target metadata, and consumer compilation. It does not prove tokenizer correctness or runtime behavior.

## 7. Compatibility guarantees and non-goals

Before 1.0.0, the project does not guarantee:
- Compatibility with every JDK or Android runtime.
- Compatibility with every Gradle or Kotlin plugin version.
- A stable binary API or serialization format.
- Public Maven Central coordinates.
- Runtime behavior on Android devices.

After a stable API and format release, the project should publish a maintained compatibility matrix and test representative minimum and current consumer combinations.

## References

- [Kotlin Gradle Plugin compatibility and JVM target configuration](https://kotlinlang.org/docs/gradle-configure-project.html)
- [Gradle 9.9.1 release notes and security fixes](https://docs.gradle.org/9.9.1/release-notes.html)
- [Gradle Java runtime compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)
- [JUnit 5.14.4 user guide and Java runtime requirements](https://docs.junit.org/5.14.4/user-guide/)
- [Gradle Wrapper validation](https://github.com/gradle/actions/tree/main/wrapper-validation)
