# Consumer Compatibility Fixture

This is an isolated Gradle consumer used by the repository's CI workflow.

It validates that the three ISQU modules can be published to Maven Local, resolved as external Maven dependencies, selected as JVM 8 variants by a Java 11 consumer, and used while compiling separate Java and Kotlin source sets.

The fixture currently uses:

- JDK 25 to run Gradle.
- Gradle 9.9.1, the security-patched project baseline.
- Kotlin Gradle Plugin 2.4.10, matching the Kotlin plugin version observed in ArgusA.
- Java/Kotlin target 11, matching the Java compile target observed in ArgusA.

The tokenizer modules do not expose production APIs yet. Therefore, this fixture validates dependency publication/resolution, variant metadata, and consumer compilation; it does not claim that tokenizer APIs or runtime behavior have been tested.

Run from the repository root after publishing to Maven Local:

```bash
./gradlew publishToMavenLocal
./gradlew -p compatibility/consumer clean verifyPublishedArtifacts build
```
