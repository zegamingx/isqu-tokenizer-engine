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
- Validated the modern build, JUnit 6, Java 21 consumer, and Android API 33 D8/R8 packaging in GitHub Actions run #8.
- Accepted the initial byte-level BPE runtime profile, 32-slot special-token layout, lossless byte pipeline, deterministic CBOR schema, SHA-256 runtime fingerprint, and snapshot-lineage rules (ADR 0003).
- Kept the first BPE profile internal/pre-1.0; Unigram comparison, source-cleared corpus admission, and cross-runtime conformance remain open gates.
- Defined explicit bounded snapshot-loading requirements; no unbounded loader path or unmeasured mobile size preset is permitted.
- Moved tokenizer-testkit to test-only consumer scope and removed its unnecessary production dependency on tokenizer-training.
- Implemented the initial byte-level BPE runtime in `tokenizer-core`: immutable validated snapshots, the 32-slot special-token layout, a lossless byte pipeline, and ranked merging with leftmost tie-breaking.
- Added the `tokenizer-format-cbor` module with the version-1 `.isqutok` codec: RFC 8949 deterministic CBOR, SHA-256 runtime fingerprint, optional provenance, and caller-selected loading limits.
- Extended `tokenizer-testkit` with byte-level BPE vectors and a runtime-fingerprint golden vector.
- Added negative and boundary vectors for the deterministic CBOR reader and the snapshot codec, assembled with an independent test-side CBOR writer.
- Changed `TokenizerSnapshotCodec.load` to read at most `maxArtifactBytes + 1` bytes from the caller's stream instead of filling a whole 8 KiB buffer before checking the limit.
- No stable public API or published artifact is included yet; the runtime and the version-1 snapshot codec are pre-1.0, and the format is not stable/public.
