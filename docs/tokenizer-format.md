# Tokenizer Format and Persistence

- **Status:** Draft 0.1; proposed portable representation, not a stable format
- **Applies to:** tokenizer-core, tokenizer-training, format adapters
- **Last reviewed:** 2026-11-13

## 1. Goals

A tokenizer snapshot must be a self-contained, versioned data artifact that can be interpreted independently of Kotlin, Android, a particular JDK, or a specific neural model implementation.

The format must:

- Preserve token IDs and all data required for deterministic encoding and decoding.
- Record algorithm identity and algorithm version.
- Record every normalization, pre-tokenization, special-token, and decoding setting that affects behavior.
- Support safe loading with structural and resource-bound validation.
- Support stable fingerprints for checkpoint references and snapshot identity.
- Carry provenance metadata without making training history part of the runtime's encoding semantics.
- Permit independent implementations in Kotlin/JVM, Python, C++, Rust, or other languages.

The format does not store neural weights, embeddings, optimizer state, model architecture, training examples, or application state.

## 2. Proposed container

The current proposal is a single deterministic CBOR document using the core deterministic encoding requirements from [RFC 8949](https://www.rfc-editor.org/rfc/rfc8949).

- File extension proposal: `.isqutok`.
- Top-level object: a CBOR map.
- Byte strings are used for opaque token bytes and fingerprints.
- Text strings are UTF-8.
- Maps use deterministic key ordering and deterministic encodings.
- Definite-length arrays, maps, byte strings, and text strings are required.
- Duplicate map keys, non-minimal encodings, unsupported simple values, and malformed CBOR are rejected.
- No executable payloads, scripts, dynamic class names, or arbitrary plugin references are permitted.

CBOR is proposed because it is language-independent, supports binary token data without lossy text conversions, and has a defined deterministic-encoding profile. JSON may be offered as a diagnostic/debug export, but it is not the canonical artifact representation.

Compression, transport, and package distribution are separate concerns. If compression is added later, the decompressed canonical CBOR bytes must remain identical and the compression algorithm must be declared outside the runtime semantics.

## 3. Top-level schema proposal

The logical top-level map contains:

| Field | Purpose |
| --- | --- |
| `format` | Fixed identifier: `isqu-tokenizer` |
| `formatVersion` | Integer format version, initially proposed as `1` |
| `runtime` | Complete encoding/decoding contract and algorithm data |
| `runtimeFingerprint` | SHA-256 fingerprint of the canonical format identifier, version, and runtime map |
| `provenance` | Training, corpus, and lineage metadata; not executable |
| `extensions` | Optional namespaced metadata with explicitly defined compatibility rules |

Illustrative diagnostic shape (not the literal canonical CBOR encoding):

```json
{
  "format": "isqu-tokenizer",
  "formatVersion": 1,
  "runtime": {
    "algorithm": {},
    "idLayout": {},
    "specialTokens": [],
    "normalizer": {},
    "preTokenizer": {},
    "decoder": {},
    "model": {}
  },
  "runtimeFingerprint": "sha256:<32-byte-digest>",
  "provenance": {}
}
```

The exact field numbers/types and binary encoding are still subject to schema review. The example is illustrative, not a finalized public wire contract.

## 4. Runtime section

The runtime section contains only data that changes token IDs, token interpretation, or decoded output.

It must include:

- Algorithm name and algorithm version.
- ID-layout scheme and all reserved ranges.
- Active special tokens and their IDs, byte/string spellings, types, and matching semantics.
- Base alphabet definition.
- Algorithm-specific vocabulary/model data.
- Normalizer type, version, and complete configuration.
- Pre-tokenizer type, version, Unicode data version where relevant, and complete configuration.
- Decoder mode and invalid-byte handling policy.
- Any algorithm option that changes deterministic segmentation.

A default value that affects behavior must still be represented explicitly or be normatively fixed by the format version. Loaders must not silently substitute implementation-local defaults.

## 5. Byte-level BPE model proposal

For a byte-level BPE snapshot:

- The base alphabet is exactly the 256 byte values `0x00` through `0xFF`.
- The proposed ID layout reserves `S` IDs for special tokens, places byte IDs at `S` through `S + 255`, and places merge outputs after the byte range.
- `S = 32` remains a proposal and is not frozen.
- The model stores an ordered list of merge input pairs.
- For merge index `i` beginning at zero, the output ID is implied as `S + 256 + i`.
- A merge pair contains two existing token IDs; both input IDs must be lower than the implied output ID.
- The output token byte sequence is the concatenation of the two input token byte sequences.
- The merge order is semantically significant and must not be sorted during serialization or loading.

The format must distinguish `idSpaceSize`, `activeTokenCount`, `reservedSpecialCapacity`, and `mergeCount`. Do not expose a single ambiguous `vocabSize` field until its meaning is fixed.

If an unassigned reserved special-token slot is activated later, that creates a new snapshot. An existing ID must never be silently reassigned.

A smaller BPE vocabulary may be represented by a merge-list prefix only when its byte base, special-token layout, normalizer, pre-tokenizer, and merge order are identical. Its runtime fingerprint is still different because segmentation behavior changes.

## 6. Unigram and other algorithm data

The format must not force every tokenizer algorithm into the BPE merge-list representation.

A Unigram model needs an ordered vocabulary of pieces, stable IDs, scores, base/fallback policy, normalizer, pre-tokenizer, and a defined best-path/tie-breaking contract. Scores must use a precisely specified numeric representation. If IEEE-754 values are used, NaN and infinities are rejected and tie behavior is explicit.

Future model kinds may define algorithm-specific maps. A loader that does not support the model kind/version must fail with a typed unsupported-algorithm error; it must not attempt to interpret the data as BPE.

## 7. Runtime fingerprint and artifact integrity

Two different hashes serve different purposes.

### Runtime fingerprint

The proposed runtime fingerprint input is a canonical map containing the format identifier, format version, and runtime section:

`SHA-256(deterministic-CBOR({ format, formatVersion, runtime }))`

It covers every field that can affect encoding, decoding, token IDs, special-token matching, or normalization, and domain-separates the digest by format identifier and format version. Any extension that affects runtime behavior must live inside the fingerprinted runtime section. It excludes provenance-only fields such as timestamps, corpus URLs, author metadata, and training host information.

This allows two artifacts with different provenance metadata but identical tokenizer behavior to have the same runtime fingerprint.

### Artifact digest

The complete file may have a separate SHA-256 digest distributed in a sidecar checksum or release metadata. It covers the full file, including provenance and extensions.

Neither value is a digital signature. Release signatures and build provenance are distribution/security concerns and will be specified separately.

## 8. Snapshot lineage and evolution

A snapshot should carry lineage metadata:

- Stable lineage-root fingerprint.
- Optional parent runtime fingerprint.
- Monotonic snapshot sequence within that lineage.
- Trainer and algorithm versions.
- Training configuration and corpus-manifest digest.

Lineage metadata does not override runtime compatibility. The runtime fingerprint identifies behavior; parent/root information describes how the snapshot was produced.

Within a lineage:

- Existing token IDs remain immutable.
- New IDs are appended.
- IDs are never recycled.
- A changed normalizer, pre-tokenizer, decoder, or algorithm contract produces a new runtime fingerprint and may require a new lineage.
- Vocabulary extension does not imply compatibility with an existing neural checkpoint.

A model checkpoint must store the exact runtime fingerprint it expects. A consumer must verify it before inference, evaluation, or training.

## 9. Provenance section

Provenance metadata should record, when applicable:

- Trainer name, version, source revision, and build identifier.
- Algorithm configuration and seed.
- Corpus manifest ID and SHA-256 digest.
- Source dataset names, versions, URLs, retrieval dates, and declared licenses.
- Content-rights review state and attribution obligations.
- Preprocessing pipeline revision and Unicode data version.
- Training timestamp and environment metadata.
- Parent snapshot and lineage information.

Do not embed raw private corpora, credentials, personal data, or unrestricted source documents in a tokenizer snapshot. Provenance metadata is not proof that source rights were cleared; the corpus manifest and rights review remain separate required records.

## 10. Safe loading and validation

A loader must validate before exposing a snapshot:

- Magic/format identifier and supported format version.
- Structural nesting and declared array/map lengths.
- Maximum file size, token count, token byte length, merge count, and metadata size against configurable limits.
- Duplicate IDs and duplicate special-token assignments.
- Valid reserved-ID ranges and active/inactive slot states.
- All merge references and implied output IDs.
- Model-specific invariants, such as valid scores and deterministic merge ordering.
- Normalizer/pre-tokenizer support and data-version compatibility.
- Runtime fingerprint and optional whole-artifact digest.
- UTF-8 validity for fields defined as text strings.
- No unsupported executable or extension behavior.

Unknown mandatory runtime fields must cause a clear unsupported-format/feature error. Unknown provenance metadata may be ignored only when the format version explicitly permits it.

Parsing must not allocate from untrusted lengths before validating bounds. Loading must not trigger network access or code execution.

## 11. Interoperability

Adapters may import or export known formats such as SentencePiece and Hugging Face tokenizer JSON, subject to explicit compatibility checks.

An adapter must report whether conversion is:

- **Lossless:** all runtime semantics and IDs are preserved.
- **Semantics-preserving with ID remapping:** tokenization behavior is preserved but token IDs change, and a mapping is emitted.
- **Lossy/unsupported:** some normalizer, pre-tokenizer, byte fallback, special-token, or decoder behavior cannot be represented.

An adapter must never label a conversion "compatible" solely because token strings look similar. The converted snapshot must pass shared golden vectors. SentencePiece model metadata demonstrates why trainer, normalizer, model pieces, and decoder behavior must travel together.

## 12. Versioning policy proposal

The tokenizer file-format version is independent from the library's Maven version.

- A format-major change is required when a reader cannot safely interpret the previous semantics.
- Backward-compatible optional metadata can be added only under explicit rules.
- A reader must reject unknown mandatory features.
- A migration must be explicit and produce a new fingerprint.
- Old snapshots must remain readable for the support window declared by the library release policy.

No stable format compatibility guarantee is made before the format is accepted and implemented with conformance tests.

## References

- [RFC 8949: Concise Binary Object Representation (CBOR)](https://www.rfc-editor.org/rfc/rfc8949)
- [SentencePiece model protobuf schema](https://github.com/google/sentencepiece/blob/master/src/sentencepiece_model.proto)
- [SentencePiece model metadata](https://github.com/google/sentencepiece/blob/master/doc/model_proto.md)
- [Hugging Face Tokenizers model API](https://huggingface.co/docs/tokenizers/main/en/api/models)
- [Unicode Normalization Forms, UAX #15](https://www.unicode.org/reports/tr15/)
- [Unicode Text Segmentation, UAX #29](https://www.unicode.org/reports/tr29/)
