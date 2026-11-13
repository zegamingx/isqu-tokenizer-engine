# Tokenizer Format and Persistence

- **Status:** Version-1 design accepted for internal implementation; not a stable public file format
- **Applies to:** tokenizer-core, tokenizer-training, tokenizer-format-cbor
- **Last reviewed:** 2026-11-13
- **Normative design decision:** [ADR 0003](adr/0003-initial-runtime-profile.md)

## 1. Goals and boundary

A tokenizer snapshot is a self-contained, versioned data artifact interpretable independently of Kotlin, Android, a particular JDK, or a specific neural model implementation.

It stores tokenizer runtime state and optional provenance. It does not store neural weights, embeddings, optimizer state, model architecture, training examples, raw corpora, or application state.

The initial version-1 runtime profile is byte-level-bpe-rank. Unigram and WordPiece require separate model representations and are not silently mapped into the BPE schema.

## 2. Container and deterministic CBOR profile

The proposed file extension is .isqutok. The artifact is one CBOR data item whose top-level value is a map.

Version 1 uses RFC 8949 core deterministic encoding requirements (Section 4.2.1):

- Preferred/shortest encodings are required for integers and lengths.
- Indefinite-length arrays, maps, byte strings, and text strings are prohibited.
- Map keys are UTF-8 text strings sorted by the bytewise lexicographic order of their deterministic CBOR encodings. Do not use the older length-first ordering.
- Duplicate map keys are rejected.
- Text strings must be valid UTF-8.
- CBOR tags and floating-point values are not used in version 1.
- No scripts, executable payloads, dynamic class names, or arbitrary plugin references are permitted.

Canonical JSON is not the artifact format. JSON may be generated as a diagnostic representation only.

## 3. Exact version-1 top-level schema

The top-level map contains exactly these keys:

| Key | CBOR type | Rule |
| --- | --- | --- |
| format | Text string | Required; exact value isqu-tokenizer |
| formatVersion | Unsigned integer | Required; exact value 1 |
| runtime | Map | Required; exact closed schema in section 4 |
| runtimeFingerprint | Byte string | Required; exactly 32 bytes |
| provenance | Map | Optional; metadata only and excluded from the runtime fingerprint |

Unknown top-level keys are rejected in format version 1.

## 4. Exact runtime schema

The runtime map contains exactly these keys:

| Key | CBOR type and exact value |
| --- | --- |
| algorithm | Map with name = byte-level-bpe-rank and version = 1 |
| idLayout | Map with the exact isqu-byte-bpe-v1 ranges and token ID type from section 5 |
| specialTokens | Array of maps { id, name, renderBytes }, sorted by increasing id |
| normalizer | Map containing exactly type = none |
| preTokenizer | Map containing exactly type = none |
| decoder | Map containing type = byte-concatenation, textEncoding = utf-8, invalidUtf8 = error, specialTokenPolicy = render-bytes |
| model | Map containing mergeablePieces, an array of byte strings in increasing rank order |

Unknown runtime keys are rejected. A future normalizer, pre-tokenizer, decoder policy, algorithm family, or runtime field requires an explicitly versioned schema change.

### 4.1 Special-token entries

Each special-token entry has exactly:
- id: unsigned integer in [0, 32).
- name: case-sensitive ASCII identifier matching `[A-Za-z][A-Za-z0-9._-]{0,63}`, unique within the snapshot.
- renderBytes: non-empty byte string containing valid UTF-8.

Entries are sorted by increasing ID. New entries use the lowest unassigned reserved special-token ID. Existing entries are immutable and are never removed or rebound within a lineage.

The engine does not assign meaning to names such as BOS, EOS, pad, user, or assistant. Consumers define their meaning.

### 4.2 Ranked BPE pieces

model.mergeablePieces is an array of byte strings. For array index i, the implicit token ID/rank is 288 + i.

Each learned piece:
- contains at least two bytes;
- is unique across the base-byte alphabet and all earlier learned pieces;
- is constructible by concatenating two pieces that already existed at lower ranks;
- is stored in rank order and is never sorted lexically during serialization.

The rank table itself is the BPE merge model. A separate pair-to-output merge table is not stored. During encoding, an adjacent pair is a candidate when its concatenated bytes exist in the piece-to-ID table. The candidate with the lowest token ID is merged first; an equal-rank occurrence is resolved by choosing the leftmost position. The process repeats until no candidate remains.

This is a rank-based byte-pair merge contract. It must not be replaced by a greedy longest-match algorithm or an unordered vocabulary lookup.

## 5. Version-1 ID layout and size definitions

The idLayout map contains exactly:

| Key | Type | Value |
| --- | --- | ---: |
| scheme | Text string | isqu-byte-bpe-v1 |
| tokenIdType | Text string | non-negative-int32 |
| specialTokenBase | Unsigned integer | 0 |
| specialTokenCapacity | Unsigned integer | 32 |
| byteTokenBase | Unsigned integer | 32 |
| byteTokenCount | Unsigned integer | 256 |
| mergeTokenBase | Unsigned integer | 288 |

The base byte mapping is fixed: for an unsigned input byte value b in [0, 255], the token ID is 32 + b.

Definitions:
- mergeablePieceCount = model.mergeablePieces.length.
- idSpaceSize = 288 + mergeablePieceCount.
- activeTokenCount = 256 + specialTokens.length + mergeablePieceCount.
- reservedSpecialCapacity = 32.
- idSpaceSize - activeTokenCount is the number of still-unassigned special-token slots.
- idSpaceSize must not exceed Int.MAX_VALUE; all token IDs therefore fit a non-negative Kotlin/Java Int.

For an embedding or untied output-projection table, the number of rows must cover idSpaceSize, not activeTokenCount. This is why the public contract avoids an unqualified vocabSize field.

The vocabulary sweep's target sizes are target ID-space sizes. With the fixed 288-ID base layout, a target of 1,024 contains 736 learned pieces.

## 6. Normalization, pre-tokenization, and text/byte behavior

Version 1 fixes both the normalizer and pre-tokenizer to NONE. There are no implicit whitespace, word, punctuation, regex, grapheme, or Unicode-property boundaries.

- Text input is validated as well-formed UTF-16, then encoded as UTF-8.
- Raw-byte input accepts arbitrary byte arrays, including sequences that are not valid UTF-8.
- Learned pieces may end inside a UTF-8 code point.
- Decoding concatenates all piece bytes before strict UTF-8 validation.
- Invalid UTF-8 in a text-decoding request is a typed error; replacement characters are never inserted silently.
- Special tokens are not auto-detected in ordinary text. The caller inserts them through a typed operation.
- Decoder render mode emits a special token's renderBytes. Skipping special tokens is an explicit decode option.

A future normalizer or pre-tokenizer must be separately named/versioned, included in the runtime fingerprint, and independent of host JDK/Android Unicode defaults.

## 7. Runtime fingerprint

The runtime fingerprint is the SHA-256 digest of the deterministic CBOR encoding of exactly this three-entry map:

    { "format": "isqu-tokenizer", "formatVersion": 1, "runtime": <runtime-map> }

Formally:

    runtimeFingerprintInput = deterministicCbor({format, formatVersion, runtime})
    runtimeFingerprint = SHA-256(runtimeFingerprintInput)

The stored runtimeFingerprint field is excluded from the input. The optional provenance map is also excluded. Therefore:
- every field that affects encoding, decoding, token IDs, special-token rendering, or model behavior is fingerprinted;
- provenance-only changes do not change tokenizer identity;
- any change to algorithm, ID layout, special tokens, normalization, pre-tokenization, decoder behavior, or model pieces does.

The fingerprint is stored as a raw 32-byte CBOR byte string. It is an identity/integrity check, not a digital signature or proof of source rights.

## 8. Provenance and lineage metadata

The optional provenance map may contain only these version-1 fields:

| Key | CBOR type | Meaning |
| --- | --- | --- |
| lineageRootFingerprint | 32-byte byte string | Runtime fingerprint of the root snapshot |
| parentRuntimeFingerprint | Optional 32-byte byte string | Immediate parent snapshot |
| snapshotSequence | Unsigned integer | Zero for root; parent sequence + 1 for a child |
| trainerName | Optional text string | Trainer identifier |
| trainerVersion | Optional text string | Trainer version |
| trainerRevision | Optional text string | Trainer source revision |
| trainingConfigSha256 | Optional 32-byte byte string | Canonical training configuration digest |
| corpusManifestSha256 | Optional 32-byte byte string | Versioned source-manifest digest |
| seed | Optional unsigned integer | Training seed, where applicable |
| createdAt | Optional RFC 3339 text string | Snapshot creation timestamp |

When provenance is present, lineageRootFingerprint and snapshotSequence are required. A root uses sequence zero, has no parent, and stores its own runtime fingerprint as the lineage root. A child records the immediate parent fingerprint and increments the sequence by one. If provenance is absent, the snapshot remains usable but its lineage is unknown; a reader must not infer it.

Unknown keys in provenance are rejected in version 1. New metadata requires a defined schema update.

## 9. Lineage and compatibility rules

Within a lineage:
- Existing token IDs and byte meanings are immutable.
- New special tokens may be appended into unused reserved slots.
- New ranked pieces are appended; old piece IDs and ranks are not changed.
- IDs are never recycled.
- A special token's existing name/render bytes may not be edited in place.

Appending a BPE piece or a new special token changes the runtime fingerprint even when the lineage remains the same.

The following changes start a new lineage: ID-layout scheme or byte mapping changes, algorithm/rank semantics changes, normalizer or pre-tokenizer changes, decoder behavior changes, or mutation of any existing token's bytes/meaning.

A smaller BPE snapshot may be represented by a ranked-piece prefix only when its base layout, special-token assignments, normalizer, pre-tokenizer, decoder, and piece prefix are identical. Its runtime fingerprint still differs from the larger snapshot.

Preserving IDs does not preserve segmentation. Model checkpoints must store the exact tokenizer fingerprint they expect. Consumers reject fingerprint mismatch by default; embedding resizing, model adaptation, and evaluation against a changed snapshot are explicit consumer-owned migration steps.

## 10. Safe loading and validation

A loader validates before exposing the snapshot:

- Format identifier and supported format version.
- Closed-schema top-level/runtime/provenance keys.
- CBOR structure, duplicate keys, deterministic encoding, UTF-8 text validity, and definite lengths.
- Maximum file size, nesting depth, map/array lengths, metadata size, mergeable-piece count, and piece byte length against configured resource limits before allocation.
- Correct ID ranges, unique special IDs/names, valid render bytes, and assigned-slot ordering.
- Unique learned pieces, valid piece lengths, rank order, and at least one valid lower-rank split for every learned piece.
- Exact normalizer/pre-tokenizer/decoder profile support.
- Runtime fingerprint equality.
- Unsupported algorithms, fields, IDs, or resource sizes produce typed errors.

Parsing never performs network access or executes code. Raw source corpora and credentials are never embedded. The pre-1.0 loader requires an explicit resource-limit object and rejects input before allocation when any configured bound is exceeded.

## 11. Interoperability

Adapters may import or export known formats such as SentencePiece model files and Hugging Face tokenizer JSON, but must explicitly classify conversion:

- **Lossless:** runtime behavior and token IDs are preserved.
- **Semantics-preserving with ID remapping:** tokenization behavior is preserved but IDs change, and a mapping is emitted.
- **Lossy/unsupported:** some normalizer, pre-tokenizer, byte fallback, special-token, or decoder behavior cannot be represented.

Conversion must pass shared golden vectors. Similar token strings alone do not establish compatibility. Unigram and WordPiece cannot be represented as the version-1 ranked BPE model.

## 12. Versioning and implementation boundary

The file-format version is independent of the Maven library version. Version 1 is the accepted design for the internal initial runtime profile, but it is not a stable public compatibility promise until the codec and its conformance suite are implemented and released.

Unknown mandatory features are rejected. Format migrations are explicit and produce a new fingerprint. The engine's Apache-2.0 software license does not grant rights in corpus text or derived artifacts.

## References

- [RFC 8949, Section 4.2.1: Core Deterministic Encoding Requirements](https://www.rfc-editor.org/rfc/rfc8949.html#section-4.2.1)
- [tiktoken byte-pair merge implementation](https://github.com/openai/tiktoken/blob/main/src/lib.rs)
- [SentencePiece: a simple language-independent tokenizer](https://arxiv.org/abs/1808.06226)
- [SentencePiece model schema](https://github.com/google/sentencepiece/blob/master/src/sentencepiece_model.proto)
- [Unicode Normalization Forms, UAX #15](https://www.unicode.org/reports/tr15/)
- [Unicode Text Segmentation, UAX #29](https://www.unicode.org/reports/tr29/)
