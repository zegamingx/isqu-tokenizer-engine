# ADR 0003: Initial byte-level BPE runtime profile and snapshot identity

- **Status:** Accepted for internal implementation
- **Date:** 2026-11-13
- **Scope:** initial token ID layout, byte-level BPE runtime, text pipeline, special-token behavior, snapshot schema, fingerprinting, and lineage
- **Decision owner:** ISQU Tokenizer Engine maintainers

## Context

Stages 1–7 established a standalone Kotlin/JVM library, separated the runtime from training, and documented the need for portable snapshots. The remaining P0 decisions were the reserved ID layout, the first runtime's normalization and pre-tokenization behavior, special-token safety, the exact initial snapshot schema/fingerprint, and snapshot-lineage rules.

The first implementation must be narrow enough to test thoroughly and portable enough that a future Python, C++, or Rust reader can produce identical token IDs. These decisions authorize an internal prototype; they do not declare the first algorithm universally optimal, freeze Kotlin method signatures, or make a pre-release .isqutok file a stable public format.

## Decision

### 1. Initial algorithm profile

The first runtime vertical slice uses **byte-level BPE with a ranked byte-piece vocabulary**.

- The base alphabet is every byte value from 0x00 through 0xFF.
- Every learned piece is a unique byte string containing at least two bytes.
- Learned pieces are stored in increasing rank order. Their token ID is their implicit rank-derived ID.
- The model stores the ordered learned-piece byte strings, not a separate left/right merge-pair table.
- At runtime, an adjacent pair is mergeable when the concatenation of its bytes is present in the ranked piece table. The candidate's rank is that piece's token ID.
- The runtime repeatedly selects the merge candidate with the lowest token ID (lowest rank). If the same candidate rank occurs at multiple positions, it selects the leftmost occurrence. It stops when no adjacent concatenation exists in the table.
- Caches and implementation data structures may change performance, but not the resulting ID sequence.

This is the byte-rank model used as a reference point by implementations such as tiktoken. ISQU does not adopt tiktoken's regex pre-tokenizer or special-token matching policy by implication; those are explicitly different profile decisions.

**Algorithm status:** this is the accepted initial implementation profile, not proof that BPE is the best algorithm for every language mix or model tier. Unigram remains the required research comparator before claiming a universal/default algorithm decision or a 1.0 algorithm guarantee. WordPiece remains a secondary interoperability target.

### 2. Token ID layout

The initial byte-level BPE profile fixes the following ranges:

| Range | Meaning | Allocation |
| --- | --- | --- |
| [0, 32) | Reserved special-token slots | Up to 32 model-defined special tokens |
| [32, 288) | Base byte tokens | Exactly 256 tokens; ID = 32 + unsigned byte value |
| [288, idSpaceSize) | Learned BPE pieces | IDs follow learned-piece rank order |

The rules are:

- TokenId is a non-negative signed 32-bit integer.
- idSpaceSize = 288 + mergeablePieceCount.
- idSpaceSize must not exceed Int.MAX_VALUE; therefore every allocated ID is a valid non-negative Kotlin/Java Int.
- activeTokenCount = 256 + activeSpecialTokenCount + mergeablePieceCount.
- reservedSpecialCapacity = 32.
- mergeablePieceCount is the number of unique learned byte pieces, not a number of pair occurrences.
- Unassigned special-token slots remain unavailable for ordinary byte or merge tokens. They are included in idSpaceSize but not in activeTokenCount.
- The terms vocabSize and vocabularySize must not be used without qualification. Use idSpaceSize for the embedding/output-projection row count and activeTokenCount for the number of assigned token entries.

Special-token allocation is append-only within a lineage. A new special token takes the lowest unassigned slot in [0, 32). An assigned slot is never removed, rebound, or recycled within that lineage. Existing special IDs and render bytes remain immutable. If all 32 slots are consumed, a future ID-layout version or new lineage is required; the implementation must not overflow into the byte-token range.

The initial vocabulary-size sweep is defined in terms of **target ID-space size**, not the number of active token entries: 1,024; 2,048; 4,096; 8,192; 16,384; 32,768; and 65,536. Add 131,072 only when the corpus, compute budget, and target consumer justify it. A target of 1,024 therefore provides 736 learned-piece slots after the fixed 288-ID base layout. A 400k default remains rejected for the initial scope.

### 3. Normalization and pre-tokenization

The initial profile fixes:

- **Normalizer:** NONE.
- **Pre-tokenizer:** NONE (one continuous byte stream; no implicit whitespace, word, punctuation, regex, or Unicode-property splitting).

For text input, the runtime first validates that the Kotlin/JVM UTF-16 string contains only well-formed surrogate pairs, then encodes it as UTF-8. It must fail with a typed input error rather than silently replacing malformed UTF-16. The raw-byte API accepts arbitrary byte arrays without asserting that they are valid UTF-8.

This profile preserves the exact UTF-8 byte stream and avoids platform-dependent Unicode normalization or boundary tables. It intentionally permits learned pieces and token boundaries to occur across whitespace, punctuation, and UTF-8 code-point boundaries. Decoding must concatenate all token byte fragments before strict UTF-8 validation.

Training records remain hard boundaries: pair counts and merge application must never cross from one corpus record/document into another. The no-pre-tokenizer rule applies within each record and to each runtime input; it does not authorize concatenating separate corpus records without an explicit byte separator.

Any future normalizer or pre-tokenizer is a separately versioned profile. Its complete configuration, implementation/data version, and any Unicode data dependency must be included in the runtime fingerprint and pass cross-runtime conformance tests. Do not rely on whichever Unicode tables happen to ship with a JDK or Android release.

### 4. Special-token contract

A special token is an explicitly assigned ID in the reserved range. Its snapshot entry contains:

- id: the assigned integer ID.
- name: a case-sensitive ASCII identifier matching `[A-Za-z][A-Za-z0-9._-]{0,63}`, unique within the snapshot.
- renderBytes: non-empty bytes that are valid UTF-8 and are emitted when the special token is decoded in render mode.

No built-in token names or semantics are imposed by the engine. Consumers may define entries such as BOS, EOS, padding, or conversation-control tokens, but their meaning belongs to the consuming model/application.

The behavior is fixed:

- Ordinary encodeText and encodeBytes never recognize a special-token spelling. The spelling is encoded as ordinary UTF-8/raw bytes.
- Special-token insertion is a separate typed operation that accepts an assigned special-token ID/name. It must not be implemented as an implicit substring replacement.
- Decoding renders special tokens using their stored renderBytes by default. An explicit option may skip special tokens.
- An unassigned reserved ID, unknown special ID, or ID outside idSpaceSize produces a typed error.
- A special token's ID, name, and render bytes are fingerprinted. Adding a new token in a previously unassigned reserved slot creates a new snapshot fingerprint. Changing an existing entry requires a new lineage.

Byte-level BPE already represents every byte, so an unknown-token entry is not required for byte coverage.

### 5. Runtime and training determinism

For a fixed snapshot and fixed options:

- Encoding and decoding are deterministic, immutable, and thread-safe.
- The ranked piece table is the authoritative BPE merge priority.
- When two pair occurrences have the same resulting piece rank, the leftmost occurrence is merged first.
- Training starts each corpus record from its UTF-8 bytes, segments it using the current ranked vocabulary, and counts adjacent token-pair frequencies without crossing record boundaries.
- The highest-frequency pair is selected. Equal-frequency candidates are ordered by unsigned-byte lexicographic comparison of the left token bytes, then the right token bytes.
- The selected pair's concatenated bytes become the next learned piece and receive the next token ID/rank. The trainer re-segments all records against the updated ranked vocabulary before the next frequency count.
- A learned piece must be unique and must be constructible from two pieces already present at lower ranks. The trainer must fail on an inconsistent state rather than create duplicate piece bytes or nondeterministic output.
- Identical corpus manifests, preprocessing profile, trainer version, configuration, and seed must produce identical ranked pieces and runtime fingerprints wherever determinism is promised.

The training loop emits a candidate immutable snapshot. It never activates the snapshot, changes model embeddings, or mutates an application checkpoint.

### 6. Initial snapshot schema

The initial .isqutok schema is version 1 and uses one deterministic CBOR document. Its exact top-level fields are:

| Key | CBOR type | Requirement |
| --- | --- | --- |
| format | UTF-8 text string | Required; exact value isqu-tokenizer |
| formatVersion | Unsigned integer | Required; exact value 1 |
| runtime | Map | Required; closed schema defined below |
| runtimeFingerprint | 32-byte byte string | Required; SHA-256 runtime fingerprint |
| provenance | Map | Optional; metadata only, never runtime semantics |

The version-1 runtime map has exactly these keys:

| Key | CBOR value |
| --- | --- |
| algorithm | Map: name = byte-level-bpe-rank, version = 1 |
| idLayout | Map containing the exact isqu-byte-bpe-v1 ranges and token ID type from section 2 |
| specialTokens | Array of active special-token maps, sorted by increasing id |
| normalizer | Map containing exactly type = none |
| preTokenizer | Map containing exactly type = none |
| decoder | Map containing type = byte-concatenation, textEncoding = utf-8, invalidUtf8 = error, and specialTokenPolicy = render-bytes |
| model | Map containing mergeablePieces: an array of byte strings ordered by increasing rank/implicit token ID |

Each mergeablePieces[i] has token ID 288 + i. Each item must contain at least two bytes, be unique, and not duplicate a base byte token. The piece must be constructible from two previously defined pieces. The byte-to-ID table is fixed and does not need to be duplicated in the file.

The provenance map may contain only the following version-1 fields: lineageRootFingerprint (32-byte byte string), parentRuntimeFingerprint (optional 32-byte byte string), snapshotSequence (unsigned integer), trainerName (text), trainerVersion (text), trainerRevision (optional text), trainingConfigSha256 (optional 32-byte byte string), corpusManifestSha256 (optional 32-byte byte string), seed (optional unsigned integer), and createdAt (optional RFC 3339 text). The root snapshot has sequence zero, no parent, and a lineage root equal to its runtime fingerprint. A child snapshot has the same lineage root, the immediate parent runtime fingerprint, and the parent's sequence plus one. Imported snapshots may omit provenance; their lineage must then be treated as unknown rather than inferred.

Version 1 is a closed schema: unknown top-level or runtime keys are rejected. Unknown provenance keys are also rejected; a future schema change must explicitly define new keys and compatibility behavior. CBOR tags, floating-point values, indefinite-length items, duplicate map keys, and non-minimal integer/length encodings are not used by this profile.

### 7. Deterministic CBOR and runtime fingerprint

Use RFC 8949 core deterministic encoding requirements (Section 4.2.1), not the older length-first map-key ordering. All map keys in version 1 are UTF-8 text strings; map keys are ordered by the bytewise lexicographic order of their deterministic CBOR encodings. Preferred/shortest serialization and definite lengths are required.

The runtime fingerprint input is exactly the deterministic CBOR encoding of this three-entry map:

    { "format": "isqu-tokenizer", "formatVersion": 1, "runtime": <runtime-map> }

Formally:

    runtimeFingerprintInput = deterministicCbor({format, formatVersion, runtime})
    runtimeFingerprint = SHA-256(runtimeFingerprintInput)

The stored 32-byte runtimeFingerprint field is excluded from the input. The optional provenance map is also excluded. Therefore provenance-only changes do not alter tokenizer identity, while any change to algorithm, ID layout, special tokens, normalization, pre-tokenization, decoder behavior, or model pieces does.

A separate whole-artifact SHA-256 may be distributed in a sidecar or release metadata. Neither digest is a digital signature or proof of source rights.

### 8. Snapshot lineage and model compatibility

A BPE extension that appends new ranked pieces or a special token in an unused reserved slot may remain in the same lineage, provided every existing ID and its bytes/meaning remain unchanged. It still creates a new runtime fingerprint.

Any change to the byte-to-ID mapping, ID-layout scheme, algorithm/rank semantics, normalizer, pre-tokenizer, decoder behavior, or an existing token's bytes/name starts a new lineage. Existing IDs are never renumbered or recycled.

A ranked-piece prefix snapshot is a valid smaller snapshot only when the base layout, special-token assignments, normalizer, pre-tokenizer, decoder, training profile, and piece prefix are identical. Its fingerprint differs from the larger snapshot because its encoding behavior may differ.

Preserving IDs does not preserve segmentation. A new ranked piece can change the token sequence for previously seen text even though all old IDs remain stable. A neural checkpoint must record the exact tokenizer runtime fingerprint it expects. A consumer must reject a fingerprint mismatch by default; embedding expansion, model adaptation, and evaluation under a changed tokenizer are explicit consumer-owned migration operations.

### 9. Implementation and release boundary

ADR 0003 authorizes an internal BPE runtime prototype and a version-1 codec implementation behind pre-1.0 development APIs. It does not freeze public Kotlin method signatures or promise binary API compatibility, accept Unigram or WordPiece in the version-1 runtime, approve any general-purpose corpus, publish a reference vocabulary or Maven Central artifact, or claim Android API 33 device-runtime conformance.

Before a stable format/library release, the implementation must pass independently reviewed golden vectors, canonical CBOR byte-for-byte tests, malformed and oversized-input rejection tests, cross-runtime fingerprint tests, BPE determinism/prefix equivalence tests, and Android API 33 device-level conformance tests.

## Consequences

### Benefits

- The initial runtime and ID layout are precise enough for an internal implementation.
- Byte-level coverage does not depend on a predefined multilingual character inventory.
- The rank table is compact, language-independent, and can be represented without a separate mutable merge-pair graph.
- Plain text cannot accidentally inject control-token IDs.
- The normalizer and pre-tokenizer profile is identical on every supported platform because both are disabled in version 1.
- Snapshot identity, provenance, and artifact integrity are separate and explicit.
- Vocabulary extensions preserve existing IDs while still changing the runtime fingerprint when segmentation changes.

### Costs and risks

- A raw no-pre-tokenizer profile may produce suboptimal segmentation and is a baseline to measure, not an assumed final choice.
- Thirty-two reserved IDs reduce the number of learned pieces for a fixed ID-space target.
- Java/Kotlin callers must handle byte arrays and strict UTF-8 errors explicitly.
- A 32-slot special-token range is finite; expanding it requires a future layout or lineage change.
- Unigram needs a separate model schema and inference contract.
- Runtime rank lookup and training prefix equivalence require careful testing; the design alone does not prove implementation correctness.

## Upgrade conditions

Revisit this ADR when:
- The first BPE prototype demonstrates a correctness or performance limitation in the rank-based piece model.
- The 32 special-token slots are insufficient for a real consuming model.
- A pre-tokenizer or normalizer is justified by controlled multilingual benchmarks.
- Unigram wins the declared selection protocol for a target consumer tier.
- The format requires a new algorithm family, field, or compatibility behavior.
- Android API 33 device tests reveal a limitation in the Java 11 artifact.

## References

- [RFC 8949, Section 4.2.1: Core Deterministic Encoding Requirements](https://www.rfc-editor.org/rfc/rfc8949.html#section-4.2.1)
- [tiktoken byte-pair merge implementation](https://github.com/openai/tiktoken/blob/main/src/lib.rs)
- [SentencePiece paper](https://arxiv.org/abs/1808.06226)
- [SentencePiece model protobuf schema](https://github.com/google/sentencepiece/blob/master/src/sentencepiece_model.proto)
- [Unicode Normalization Forms, UAX #15](https://www.unicode.org/reports/tr15/)
