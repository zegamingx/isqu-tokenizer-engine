# G7 — Implementation Readiness Audit

- **Status:** Audit completed; implementation readiness is conditional
- **Review date:** 2026-11-13
- **Reviewed baseline:** `main` at `9fe5f8d54cc1944d1adf7c5d37b683c2759da583`
- **Scope:** module boundaries, package layout, public API concepts, snapshot format, compatibility, test strategy, and implementation sequencing

## 1. Executive result

The repository is ready for a deliberately narrow internal prototype after the P0 design gates in this document are resolved. It is **not** ready to freeze a stable public API, freeze the `.isqutok` wire format, publish a reference vocabulary, or claim Android runtime compatibility.

| Area | Result | Reason |
| --- | --- | --- |
| Build and publication baseline | **PASS** | Stage 6 validated Maven Local publication and a separate Java 11/Kotlin consumer. |
| High-level module separation | **PASS WITH ACTION** | Core, training, and testkit responsibilities are distinct, but persistence adapter ownership is not yet represented by a module. |
| Package structure | **PROPOSED** | No production packages or public declarations exist yet. |
| Runtime API | **NOT FROZEN** | The tokenization contract is behavioral guidance; its method names are illustrative. |
| Snapshot model and ID layout | **BLOCKED FOR FORMAT FREEZE** | Reserved special-token capacity, ID-space semantics, and exact schema types remain open. |
| Serialization and fingerprint | **BLOCKED FOR FORMAT FREEZE** | CBOR is a proposal; the exact canonical schema and digest input encoding are incomplete. |
| Algorithm selection | **CONDITIONAL** | Byte-level BPE is the lead prototype candidate; Unigram remains a required comparator. |
| Corpus admission | **BLOCKED FOR REFERENCE TRAINING** | No exact source manifest has received final approval for the intended training and artifact-distribution use. |
| JVM consumer compatibility | **PENDING MODERN FIXTURE CI** | The previous Java 11 consumer passed; a Java 21/Kotlin 2.4.21 consumer is now configured but its task was not reached in the first Android-provisioning failure. |
| Android API 33 packaging | **PASS WITH LIMITATION** | Android consumer fixture resolves the Maven artifacts and assembles debug/release packages through D8/R8 with minSdk 33. |\n| Android API 33 runtime | **NOT DEVICE-VALIDATED** | No API 33 emulator or physical-device execution has been performed yet. |
| Behavioral test strategy | **DEFINED, NOT IMPLEMENTED** | Contract vectors and failure cases are listed, but no production code or tokenizer tests exist. |

The correct next step is a small, internal byte-level BPE vertical slice—not a broad public API or a complete multi-algorithm framework.

## 2. Evidence reviewed

The audit reviewed the README, changelog, architecture, tokenization contract, tokenizer format proposal, algorithm research, corpus provenance policy, ADR 0001, compatibility policy, build compatibility document, ADR index, Gradle module scripts, consumer fixture, version catalog, root build configuration, and CI workflow.

The build result is correctly described as a build/publication/metadata compatibility result. The current modules have no production tokenizer source, no stable public API, and no algorithm-level tests. The consumer fixture therefore validates dependency resolution and language compilation, not calls into a tokenizer implementation.

## 3. Recommended module and package structure

Keep the initial project small. Do not create one Gradle module per algorithm or Unicode feature before the implementation demonstrates a real isolation need.

### Initial module graph

```text
tokenizer-core
    Immutable snapshot model and runtime contracts

tokenizer-training
    Candidate snapshot training and reproducibility metadata
    depends on tokenizer-core

tokenizer-format-cbor        [add when serialization implementation begins]
    Canonical CBOR reader/writer for .isqutok
    depends on tokenizer-core

tokenizer-testkit
    Golden vectors and reusable consumer contract tests
    test-scope dependency for consumers
```

The current three modules remain a valid starting point. Add `tokenizer-format-cbor` when the actual CBOR implementation is introduced. This prevents a CBOR library, byte-stream parsing, or persistence policy from becoming an unavoidable dependency of the minimal runtime.

The core module should own the in-memory, immutable snapshot representation and runtime behavior. It should not own filesystem paths, Android storage APIs, database access, network access, or the CBOR wire parser. The format module should translate between a validated snapshot and canonical bytes.

The training module should produce a candidate snapshot and a reproducibility report. It must not mutate an application's active tokenizer, model embeddings, output projection, or checkpoint. Corpus acquisition and application-specific storage remain outside the core runtime; the first trainer can accept a controlled corpus abstraction without prematurely creating a separate ingestion module.

The testkit is intended for test configurations, not normal application runtime. The Stage 6 consumer currently declares all three artifacts using `implementation` and checks them through `compileClasspath`. Before treating this fixture as an ecosystem contract, change the testkit dependency to `testImplementation` and verify it through the relevant test compile/runtime classpaths. Keep its transitive dependencies limited to the types its public test helpers actually expose; do not expose `tokenizer-training` transitively merely for convenience.

### Proposed package namespace

Use the provisional Maven group as the package prefix, while keeping publication namespace verification separate:

```text
io.github.zegamingx.isqu.tokenizer.core
    Public runtime and immutable snapshot contracts
io.github.zegamingx.isqu.tokenizer.core.internal
    Runtime implementation details; never public API

io.github.zegamingx.isqu.tokenizer.training
    Public training entry points and result/report types
io.github.zegamingx.isqu.tokenizer.training.internal
    Trainer orchestration and implementation details
io.github.zegamingx.isqu.tokenizer.training.bpe.internal
    Initial experimental BPE trainer implementation

io.github.zegamingx.isqu.tokenizer.format.cbor
    Public CBOR codec entry points
io.github.zegamingx.isqu.tokenizer.format.cbor.internal
    CBOR schema mapping and parser implementation

io.github.zegamingx.isqu.tokenizer.testkit
    Golden-vector models, fixture loaders, and test assertions
```

This is a package proposal, not an ABI commitment. Avoid public algorithm-specific packages until a second algorithm has a demonstrated need for a stable extension point. Kotlin `internal` is a compiler-level visibility boundary, not a substitute for module separation when a dependency must be excluded from consumers.

## 4. Public API concepts to settle

Freeze behavior and ownership before method names. The first public API should stay small and Java-consumable.

| Concept | Owner | Required contract |
| --- | --- | --- |
| Tokenizer runtime | `tokenizer-core` | Immutable runtime created from one validated snapshot; deterministic encode/decode. |
| Tokenizer snapshot | `tokenizer-core` | Immutable algorithm data, ID layout, special-token definitions, normalizer, pre-tokenizer, decoder semantics, and runtime identity. |
| Encoded sequence | `tokenizer-core` | Ordered IDs associated with the exact runtime fingerprint that produced them. |
| Encode/decode options | `tokenizer-core` | Explicit policy; no implicit control-token recognition, truncation, padding, or cleanup. |
| Typed failures | `tokenizer-core` | Stable, documented failure categories for malformed input, invalid IDs, unsupported features, corrupt data, and resource limits. |
| Trainer | `tokenizer-training` | Accepts explicit configuration and corpus input; returns a candidate snapshot and reproducibility report. |
| Snapshot reader/writer | `tokenizer-format-cbor` | Validates and serializes snapshots; no arbitrary code execution or implicit network access. |

Important API invariants:

- An encoded sequence should carry its runtime fingerprint. Decoding must reject a sequence from a different snapshot even when all integer IDs happen to be in range.
- Do not expose mutable internal `IntArray` or `ByteArray` storage. Copy at public boundaries or provide immutable value objects with explicit conversion methods.
- Keep raw-byte encoding/decoding separate from strict text encoding/decoding. Text input must reject unpaired UTF-16 surrogates rather than silently replacing them.
- Concatenate decoded byte fragments before validating UTF-8. A token fragment may end in the middle of a multi-byte UTF-8 sequence.
- Plain user text must not become a control token merely because it contains a special-token spelling. Control-token insertion must be explicit and typed.
- Snapshot training produces a candidate. Activation, checkpoint migration, embedding resizing, and rollback remain consumer-owned operations.
- Keep padding, truncation, batching, prompt roles, and model-specific post-processing outside the base tokenizer API.
- Define shared-instance thread-safety and mutation semantics. The recommended runtime model is immutable snapshots and stateless, thread-safe encoding/decoding.

Do not freeze exact Kotlin signatures until Java interoperability, array ownership, fingerprint-carrying sequences, and error semantics have been reviewed together.

## 5. P0 design gates before a format/API freeze

### P0-1 — Token ID layout

Resolve the meaning of every ID range before writing a stable serializer:

- Whether reserved special-token capacity is fixed, configurable, or absent in the first format version.
- Whether the illustrative `S = 32` is actually needed and what happens to unassigned slots.
- Exact definitions for `idSpaceSize`, `activeTokenCount`, `reservedSpecialCapacity`, and the public meaning (if any) of `vocabSize`.
- The numeric width and maximum supported token ID. The Kotlin/JVM public API, CBOR representation, and embedding-row count must agree.
- Whether assigning a new special token into a reserved slot is permitted only in a new snapshot and how that affects lineage.

A model embedding table must be sized from the highest usable ID plus one, not from active token count when the ID space contains holes.

### P0-2 — Normalization and pre-tokenization

Keep `NONE` as the lossless baseline. If NFC or another normalizer is later supported, its Unicode data and implementation version must be pinned in the snapshot. Do not delegate behavior to whatever Unicode data happens to ship with the host JDK or Android version.

The first BPE experiment must compare a raw/no-split byte stream with any proposed pre-tokenizer. Do not select a regex by convention alone. Its exact pattern, engine semantics, Unicode property tables, and version must be specified if adopted.

### P0-3 — Special-token semantics

Keep literal text and control tokens separate. Define the typed insertion/matching path, collision behavior, case sensitivity, and treatment of inactive reserved IDs. Do not enable automatic control-token recognition in the default text path.

### P0-4 — Snapshot schema and fingerprint

Before format version 1 is accepted, publish an exact field/type table, not only a diagnostic JSON example. Specify:

- CBOR map key types and deterministic ordering requirements.
- Required, optional, and unknown-field behavior for each schema version.
- Exact numeric ranges and string encodings.
- The complete runtime-fingerprint input bytes and domain-separation rule.
- Whether the fingerprint is represented in the file as a 32-byte CBOR byte string and as what form in public APIs.
- The exclusion of provenance-only fields from the runtime fingerprint.
- The separate whole-artifact digest location. A digest of the complete file must not recursively be embedded in the same bytes it hashes.
- Resource limits checked before allocation.

The runtime fingerprint is an identity/integrity check for tokenizer semantics, not a digital signature or proof of source rights.

### P0-5 — Lineage and compatibility semantics

Preserving old IDs does not preserve old segmentation. A BPE merge extension can change the token sequence for existing text while keeping prior IDs stable. Document this distinction explicitly in APIs and migration guidance.

Define when a change is a same-lineage extension and when it starts a new lineage. Changes to normalization, pre-tokenization, byte mapping, decoder behavior, or algorithm semantics should not be treated as ordinary vocabulary growth.

### P0-6 — Initial training data

No candidate corpus is automatically cleared by the current dataset review table. Before creating a distributable reference vocabulary, create a versioned manifest with source-level rights decisions, attribution obligations, privacy review, duplicate policy, language/script mix, split assignments, and hashes.

A small source-cleared pilot is preferable to a large web crawl with unresolved underlying text rights. Synthetic or explicitly licensed fixtures can support correctness tests before the full reference corpus is available.

## 6. Android and ecosystem compatibility gap

Stage 6 proves the current JVM artifact metadata and compilation path for a Java 11/Kotlin consumer. It does not prove Android runtime compatibility.

Before advertising Android support, add a minimal Android consumer fixture that:

- Uses the agreed minimum Android API level and a maintained Android Gradle Plugin version.
- Resolves the published Maven-local artifacts (or project dependencies in a dedicated fixture).
- Runs D8/R8 as appropriate and catches unsupported Java API usage.
- Exercises UTF-8, byte-array, and snapshot-loading code on the minimum supported API.
- Verifies no Android framework dependency leaked into `tokenizer-core`.
- Documents any desugaring requirement and tests release/minified packaging where relevant.

Keep the production core free of Android framework types. Do not infer Android compatibility solely from Java 11 bytecode or from a desktop JVM consumer. The minimum Android API level is explicitly set to 33 by the consuming Argus application.

## 7. Required test strategy

Implement tests in layers. A successful Gradle build is not a substitute for these behavioral gates.

### Core contract tests

- Empty input, ASCII, whitespace, tabs, CRLF, punctuation, control characters, and embedded NUL.
- UTF-16 surrogate validation and malformed UTF-8 strict decoding.
- Emoji, combining marks, variation selectors, zero-width joiners, regional indicators, RTL, CJK, Thai, Indic, and mixed-script input.
- Byte-preserving round trips for arbitrary byte arrays.
- Special-token-like literal text versus explicit typed special-token insertion.
- Invalid, inactive, reserved, and out-of-range IDs.
- No implicit truncation, padding, normalization, cleanup, or control-token promotion.
- Determinism across repeated calls and shared concurrent use.

### BPE algorithm tests

- Pair ranking and deterministic tie-breaking using the unsigned-byte lexicographic rule.
- Repeated, overlapping, and equal-frequency merge candidates.
- Exact merge output IDs and reconstruction of byte sequences.
- No invalid merge references, cycles, or output IDs outside the declared ID space.
- Prefix-snapshot equivalence against direct runs at the same merge count, under exactly equal corpus and trainer configuration.
- Separate tests for raw/no-split and selected pre-tokenization modes.

### Format and fingerprint tests

- Canonical CBOR byte-for-byte golden files.
- Same runtime data gives the same fingerprint across JVM versions and an independent implementation.
- Provenance-only changes do not change the runtime fingerprint.
- Every runtime-affecting field change does change the fingerprint.
- Duplicate keys, malformed CBOR, non-minimal forms, unknown mandatory features, corrupt digests, and truncated files are rejected.
- Oversized maps, arrays, token payloads, and merge lists are rejected before large allocations.
- Round-trip serialization preserves IDs and exact runtime semantics.

### Training, corpus, and performance tests

- Repeated training with identical manifests/configuration yields identical output where determinism is promised.
- Corpus splitting and deduplication happen before sampling and do not leak held-out sources.
- Per-language bytes/token, tokens per 1,000 bytes, byte-token share, and UTF-8 boundary metrics are reported.
- Throughput, p50/p95 latency, peak memory, allocations, cold load, and snapshot size are measured on representative desktop and Android hardware.
- Model-level BPB is a later controlled experiment; it is not required to prove the initial tokenizer runtime correct.

Golden vectors should be versioned as test data and reused by JVM and future non-JVM implementations. Do not rely only on implementation-generated expected outputs; at least one independently reviewed fixture set is required.

## 8. Recommended implementation sequence

1. **Decision bundle:** resolve ID layout, initial special-token policy, `NONE` normalization baseline, pre-tokenizer experiment scope, byte/text error behavior, and the exact snapshot runtime model.
2. **Core domain model:** implement immutable snapshot and fingerprint value types, typed failures, byte/text boundaries, and an in-memory runtime contract. Keep experimental classes internal.
3. **BPE runtime:** implement deterministic encoding/decoding against hand-authored tiny snapshots and independently checked golden vectors.
4. **CBOR adapter:** add `tokenizer-format-cbor`, specify the exact schema and canonical digest input, then implement safe bounded parsing and writing.
5. **Testkit:** publish reusable golden vectors and consumer contract tests; use test-scope dependency declarations.
6. **BPE trainer:** implement the selected training data structure only after memory/streaming requirements are supported by a representative pilot corpus and benchmark.
7. **Android validation:** add the Android consumer fixture and device tests before claiming Android support.
8. **Algorithm comparison:** compare against a pinned Unigram reference implementation under the same permitted corpus and evaluation split. Add native Unigram only if evidence justifies the maintenance cost.
9. **Release readiness:** complete format migration tests, compatibility matrix, API documentation, namespace verification, publication metadata/signing, and corpus-rights checks before a 1.0.0 or reference-vocabulary release.

Do not block correctness implementation on expensive model-level BPB experiments. Do block a stable algorithm/default claim on the evidence required by the selection protocol.

## 9. Final decision

**G7 audit: COMPLETE. Implementation readiness: CONDITIONAL.**

The project can proceed with an internal, non-stable BPE runtime prototype after P0-1 through P0-5 are recorded as explicit decisions. A distributable reference vocabulary additionally requires P0-6. Android dependency and D8/R8 packaging validation now pass in CI; on-device API 33 runtime support remains unclaimed until emulator or physical-device conformance tests pass.

No production implementation, stable public API, accepted format version, approved general-purpose corpus, Maven Central release, or Android compatibility claim is created by this audit.
