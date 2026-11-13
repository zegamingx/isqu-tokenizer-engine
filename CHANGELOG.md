# Changelog

All notable changes to this project will be documented here.

The project has not published a release yet.

## Unreleased

- Created the standalone Kotlin/JVM multi-module project structure.
- Added initial architecture, persistence, and compatibility requirements.
- Defined the separation between tokenizer runtime and tokenizer training.
- Added a proposed vocabulary-sizing and evaluation ADR.
- Added draft tokenization behavior and conformance requirements.
- Added a proposed language-independent tokenizer snapshot format.
- Added algorithm comparison research for BPE, Unigram, and WordPiece.
- Added corpus provenance, rights-review, and data-governance policy.
- Added pinned Gradle Wrapper configuration, JVM 11 production targeting, and Maven-local publications.
- Modernized CI and unit testing to JDK 25 and JUnit 6.1.3; test classes target JVM 25.
- Added a Java 21 / Kotlin 2.4.21 consumer compatibility fixture.
- Added an Android API 33 consumer fixture using AGP 9.4.0 with D8/R8 debug and release packaging.
- Added separate JVM 25 test compilation/runtime for JUnit 6 and corrected consumer testkit dependency scopes.
- Corrected the Android consumer test-scope assertion to inspect the declarative testImplementation bucket without resolving AGP's self-referencing unit-test variant; kept the API 33 smoke app Java-only.
- Moved tokenizer-testkit to test-only consumer scope and removed its unnecessary production dependency on tokenizer-training.
- No production tokenizer implementation, stable public API, or accepted snapshot format is included yet.
