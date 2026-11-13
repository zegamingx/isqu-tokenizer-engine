# ADR 0002: Modern build toolchain and Android API 33 compatibility baseline

- **Status:** Proposed; awaiting CI validation
- **Date:** 2026-11-13
- **Scope:** build toolchain, production JVM target, testing runtime, JVM/Android consumer compatibility
- **Decision owner:** ISQU Tokenizer Engine maintainers

## Context

The previous Stage 6 baseline targeted Java 8 and used JUnit 5. That baseline was selected primarily for broad historical JVM compatibility. It did not adequately account for the project's intended Android consumer floor (Android 13/API 33), nor did it keep the testing framework aligned with the current JUnit generation.

The project must use current stable development tools while choosing a production artifact floor from actual consumer requirements. The JDK used for development, production class-file/API target, test runtime, and Android runtime are distinct compatibility dimensions.

## Decision

### Build and test toolchain

- Use JDK 25 LTS for Gradle, compilation toolchains, CI, and tests.
- Keep Gradle Wrapper 9.9.1 because the release addresses current security vulnerabilities.
- Use Kotlin Gradle Plugin 2.4.21, the current stable release at this review.
- Keep Kotlin language/API version 2.4.
- Use JUnit Jupiter 6.1.3 and JUnit Platform 6.1.3.
- Compile test sources to JVM 25 and run them on JDK 25.
- Do not use Kotlin 2.5.0-RC in the production build; wait for stable GA and compatibility validation.

### Production artifact target

- Publish production Kotlin/Java classes at Java 11 bytecode and Java 11 JDK API level.
- Keep Kotlin `jvmTarget`, Kotlin `-Xjdk-release`, Java source/target compatibility, Java `options.release`, and Gradle JVM variant metadata aligned at 11.
- Do not call Java 17+ JDK APIs from platform-independent production modules without an explicit compatibility strategy.

This target is selected because Android API 33 provides Java 11 core libraries, while Java 17 core libraries are listed at API 34. ArgusA's intended minimum Android API is 33. Java 11 class files remain consumable by newer JVMs; this is a deployment floor, not a development-toolchain ceiling.

### Consumer fixtures

- Modern JVM consumer: JDK 25 build runtime, Kotlin Gradle Plugin 2.4.21, Java/Kotlin target 21.
- Android consumer: Android Gradle Plugin 9.4.0, compileSdk 36, minSdk 33, targetSdk 36, Java 11, Maven-local ISQU artifacts.
- Testkit is a test-scope dependency only. It must not be placed on production compile/runtime classpaths or expose tokenizer-training transitively.
- Android CI builds debug and minified release packages to exercise D8 and R8.

The Android fixture does not apply the Kotlin Android Gradle Plugin. It validates Java-based Android consumption of the Kotlin/JVM artifacts and avoids claiming an unverified KGP 2.4.21 + AGP 9.4.0 combination.

## Compatibility caveats

Kotlin documents KGP 2.4.20–2.4.21 as fully tested through Gradle 9.7.0 and AGP 9.3.1. Gradle 9.9.1 is outside that fully-tested Gradle range. This project validates the exact KGP/Gradle pair in CI, but project testing is not vendor certification.

JUnit 6 requires Java 17 or newer at runtime. Test compilation and runtime are isolated from the Java 11 production variant; test dependencies must never become published production dependencies.

The first CI attempt passed the production build, JUnit 6 tests, and Maven-local publication. Android SDK provisioning failed because `platforms;android-37` was not present in the runner's SDK package catalog; the fixture has been changed to the available Android 16/API 36 compile platform while retaining minSdk 33. The Android D8/R8 packaging fixture is not an emulator/device execution test. Actual tokenizer runtime support on Android API 33 remains gated on device-level conformance testing once production tokenizer code exists.

## Consequences

### Benefits

- Current stable Kotlin and JUnit toolchains are used instead of retaining the old JUnit 5 line solely for Java 8.
- The production artifact matches the selected Android API 33 Java core-library floor.
- Modern JVM consumers are tested independently at Java 21.
- Android dependencies are checked through D8/R8 packaging.
- Test support dependencies are correctly isolated from production consumers.

### Costs and risks

- Java 11 production APIs constrain the portable core from using later JDK library APIs.
- The exact Kotlin 2.4.21 + Gradle 9.9.1 pairing remains outside the KGP fully-tested Gradle range until a later Kotlin release expands support.
- Android API 33 runtime execution is not yet proven; the CI fixture covers packaging only.
- JDK 25 must be available in developer and CI environments.

## Alternatives considered

- **Java 8 production target:** rejected because it preserves an unnecessary baseline below the selected Android API 33 platform floor.
- **Java 17 production target:** deferred because Android API 33 lists Java 11 core libraries; Java 17 core libraries are listed at API 34.
- **Java 21 or 25 production target:** rejected for the shared artifact while API 33 remains the minimum consumer target; later JDK APIs are not universally available in Android API 33.
- **JUnit 5 maintenance line:** rejected for new tests because JUnit 6 is the current GA generation and can test Java 11 production bytecode.
- **Kotlin 2.5.0-RC:** rejected for production because it is a pre-release; use after stable GA.
- **Gradle 9.7.0 to fit KGP's documented range:** rejected because Gradle 9.9.1 is the security-patched release and is verified in project CI.

## Upgrade conditions

Revisit this ADR when:
- ArgusA raises its minimum Android API to 34 or higher.
- Kotlin 2.5.0 stable is released and its compatibility table is published.
- Gradle or JDK security/support status changes.
- Android API 33 runtime tests reveal a limitation in the Java 11 artifact.
- A separately versioned modern-JVM artifact becomes justified by real consumer requirements.

## References

- [Oracle Java SE Support Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)
- [Kotlin release process](https://kotlinlang.org/docs/releases.html)
- [Kotlin Gradle Plugin compatibility](https://kotlinlang.org/docs/gradle-configure-project.html)
- [Gradle 9.9.1 security release notes](https://docs.gradle.org/9.9.1/release-notes.html)
- [Android Java versions by API level](https://developer.android.com/build/jdks)
- [Android Gradle Plugin 9.4.0](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
- [JUnit 6.1.3 supported Java versions](https://docs.junit.org/6.1.3/user-guide/)
