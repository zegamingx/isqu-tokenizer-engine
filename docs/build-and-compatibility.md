# Build and Ecosystem Compatibility

- **Status:** Modernized baseline; CI-validated
- **Last reviewed:** 2026-11-13
- **Scope:** build JDK, Kotlin compiler, production JVM artifact, test runtime, JVM consumers, Android API 33 packaging

## 1. Compatibility model

ISQU deliberately separates four version axes:

1. **Build JDK:** runs Gradle, Kotlin compilation, Java compilation, and tests.
2. **Production class-file/API target:** defines the bytecode and JDK API surface shipped in the library.
3. **Test runtime/target:** defines the JDK and dependencies used only by the project's tests.
4. **Consumer platform:** defines the JVM or Android runtime requirements of applications that consume the published artifacts.

A modern build JDK does not require production bytecode to target that same JDK. Conversely, a lower production bytecode target does not mean the project is developed with an old compiler or old language version.

## 2. Selected toolchain

| Component | Baseline | Rationale |
| --- | --- | --- |
| Build and test JDK | JDK 25 LTS | Current LTS line; deterministic CI toolchain |
| Gradle Wrapper | 9.9.1 | Current security-patched Gradle 9.9 release; checksum pinned |
| Kotlin Gradle Plugin | 2.4.21 | Latest stable Kotlin release at review time |
| Kotlin language/API | 2.4 | Explicit language and stdlib API baseline |
| Production bytecode/API | Java 11 | Matches Android API 33 core-library baseline |
| Production class-file version | 55 | Java 11 class-file format |
| Test bytecode target | Java 25 | Tests use modern JDK APIs independently of published artifacts |
| Test framework | JUnit 6.1.3 | Current GA JUnit line at review time; requires Java 17+ at runtime |
| JVM consumer fixture | Java 21 / Kotlin 2.4.21 | Validates consumption from a modern LTS JVM target |
| Android consumer fixture | AGP 9.4.0, compileSdk 37, minSdk 33 | Validates API 33 dependency resolution and D8/R8 packaging |

JDK 27 is a newer feature release, but it is not an LTS release. JDK 25 is the current LTS release and is the more appropriate baseline for reproducible long-lived CI. The next planned LTS is Java 29 in September 2027.

Kotlin 2.4.21 is the latest stable release at this review. Kotlin 2.5.0-RC is a pre-release and is intentionally not used for the production build. The Kotlin release schedule lists the 2.5.0 stable release as planned for December 2026; upgrade after GA and after its Gradle/AGP compatibility matrix is verified.

## 3. Why production remains Java 11

This baseline is a platform decision, not a preference for an old Java toolchain.

Android's current compatibility table identifies:
- Android 13 / API 33: Java 11 core libraries.
- Android 14 / API 34: Java 17 core libraries.

ArgusA's minimum Android API is 33. ISQU's production library therefore targets Java 11 bytecode and Java 11 JDK APIs so that the core library does not require Java 17 core-library APIs unavailable natively on the minimum Android release.

The production configuration must keep all of these aligned:
- Kotlin `jvmTarget = 11`.
- Kotlin `-Xjdk-release=11`.
- Java `sourceCompatibility = 11`.
- Java `targetCompatibility = 11`.
- Java `options.release = 11`.
- Gradle Module Metadata `org.gradle.jvm.version = 11`.

This does **not** mean:
- Gradle runs on JDK 11.
- Kotlin is limited to Kotlin 1.x or old language features.
- A Java 21 or Java 25 application cannot consume ISQU.
- The tokenizer must use old algorithms or old APIs.

A modern Java 21/25 JVM can run Java 11 bytecode. The trade-off is that ISQU's production source must not call JDK APIs newer than Java 11 unless a separately documented and validated platform-specific path is introduced. Do not use Java 17/21/25 JDK APIs in `tokenizer-core` merely because CI runs on JDK 25.

For Android consumers, core-library desugaring may be used only for APIs documented as supported by the selected desugared-library version. Desugaring must not be treated as a blanket implementation of every later Java API.

## 4. JUnit 6 and test isolation

JUnit 6.1.3 requires Java 17 or newer at test runtime. It can test production classes compiled for an older Java target.

ISQU therefore uses:
- JDK 25 to execute Gradle and tests.
- JVM 25 for `compileTestKotlin` and `compileTestJava`.
- JUnit Jupiter 6.1.3 and JUnit Platform Launcher 6.1.3.
- Test compile/runtime dependency attributes targeting JVM 25.

Production compile tasks remain Java 11. Test class files are not included in the production JAR or published as the main artifact.

The Kotlin test adapter retains the `kotlin-test-junit5` artifact name for JUnit Platform/Jupiter integration. Its name does not imply that the project runs JUnit 5; the resolved Jupiter and Platform dependencies are explicitly pinned to 6.1.3.

## 5. Consumer compatibility fixtures

### 5.1 Modern Kotlin/JVM consumer

The fixture at `compatibility/consumer` uses:
- JDK 25 to run Gradle.
- Kotlin Gradle Plugin 2.4.21.
- Java/Kotlin target 21.
- ISQU production artifacts at Java 11.

It verifies:
- Core and training artifacts resolve on the production compile classpath.
- Testkit resolves only on the test compile classpath.
- All artifacts advertise `org.gradle.jvm.version = 11`.
- JAR, sources JAR, POM, and Gradle Module Metadata exist.
- Apache-2.0 and SCM metadata are present.
- Training exposes core as an API dependency.
- Testkit exposes core but does not expose training transitively.
- Java and Kotlin consumer sources compile.

### 5.2 Android 13 / API 33 consumer

The fixture at `compatibility/android-consumer` uses:
- Android Gradle Plugin 9.4.0.
- Gradle Wrapper 9.9.1.
- JDK 25 to run Gradle.
- compileSdk 37.
- minSdk 33 and targetSdk 37.
- Java source/target 11.
- The published Maven-local core and training artifacts.
- Testkit in the Android unit-test dependency scope only.

CI resolves the ISQU artifacts, checks the Android compile/test dependency scopes and Java 11 variant metadata, then builds both debug and minified release APKs. This executes the D8 and R8 packaging paths.

This is an Android packaging and dependency-compatibility check. It does **not** yet claim that the tokenizer has been executed on an Android 13 emulator or physical device. A device-level API 33 test remains a release-readiness gate once production tokenizer code exists.

The fixture deliberately uses a Java Android consumer and does not apply the Kotlin Android Gradle Plugin. That avoids claiming an unverified KGP 2.4.21 + AGP 9.4.0 pairing. The standalone Kotlin/JVM consumer separately verifies the current Kotlin plugin.

## 6. Known Gradle/Kotlin compatibility boundary

The Kotlin compatibility table lists KGP 2.4.20–2.4.21 as fully tested through Gradle 9.7.0 and AGP 9.3.1. Gradle 9.9.1 is newer than that fully-tested Gradle range.

Gradle 9.9.1 is retained because its release addresses three security vulnerabilities and Gradle recommends upgrading to it. The exact KGP 2.4.21 + Gradle 9.9.1 pairing is tested by this repository's CI, but it must be described as **project-tested**, not vendor-fully-tested.

Do not downgrade to a security-affected Gradle release merely to fit the old KGP compatibility ceiling. Re-evaluate this boundary when Kotlin 2.5.0 reaches stable release. Do not move the production build to Kotlin 2.5.0-RC.

## 7. Wrapper, publication, and CI policy

- Gradle Wrapper 9.9.1 is the canonical build entry point.
- The binary distribution SHA-256 is pinned.
- Wrapper URL validation is enabled.
- The Wrapper JAR is validated in CI.
- All modules publish to Maven Local during CI.
- A separate JVM consumer and Android API 33 consumer resolve those artifacts.
- CI permissions remain read-only except for the narrowly scoped wrapper-commit job.
- External GitHub Actions are pinned to immutable commit SHAs.

This workflow validates local publication and consumption. It does not publish to Maven Central, verify ownership of the provisional Maven namespace, or create a signed public release.

## 8. Upgrade policy

Review the baseline at least when:
- A new stable Kotlin release becomes available.
- A new JDK LTS becomes available.
- Gradle or AGP ships a security patch or a supported compatibility range changes.
- JUnit ships a stable maintenance or major release.
- ArgusA raises its minimum Android API level.
- ISQU begins using Java APIs newer than Java 11.

A version upgrade is accepted only after the JVM consumer, Android D8/R8 fixture, module tests, and publication metadata checks pass. A release candidate may be tested in a separate experiment, but it must not replace the stable production toolchain by default.

## References

- [Oracle Java SE Support Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)
- [Kotlin release process and stable versions](https://kotlinlang.org/docs/releases.html)
- [Kotlin Gradle Plugin compatibility table](https://kotlinlang.org/docs/gradle-configure-project.html)
- [Kotlin JVM compiler options](https://kotlinlang.org/docs/compiler-reference.html)
- [Gradle 9.9.1 release notes and security fixes](https://docs.gradle.org/9.9.1/release-notes.html)
- [Android Java versions and API availability](https://developer.android.com/build/jdks)
- [Android Gradle Plugin 9.4.0 release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
- [Java 11+ API desugaring](https://developer.android.com/studio/write/java8-support)
- [JUnit 6.1.3 user guide and runtime requirements](https://docs.junit.org/6.1.3/user-guide/)
