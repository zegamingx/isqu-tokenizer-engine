# Tokenization Contract

- **Status:** Initial runtime behavior accepted for internal implementation; public API remains pre-1.0
- **Applies to:** tokenizer-core, tokenizer-testkit, format adapters
- **Last reviewed:** 2026-11-13

This document defines the behavior an ISQU tokenizer implementation is expected to preserve. It is not a public API declaration. Method names below are illustrative until the public API is reviewed and frozen.

## 1. Core responsibilities

The runtime converts input text or bytes into token IDs using one immutable tokenizer snapshot, and converts token IDs back into bytes or text.

The runtime does not:

- Train or update neural-network weights.
- Resize model embeddings or output projections.
- Own optimizer state or model checkpoints.
- Silently change an active vocabulary.
- Fetch a vocabulary or corpus from a network service.
- Apply application-specific prompt policy or conversation-role policy.
- Perform padding, truncation, batching policy, or model-specific post-processing implicitly.

Tokenizer training belongs to `tokenizer-training`. Adoption and migration of a vocabulary belongs to the consuming model system.

## 2. Input domain and byte semantics

### 2.1 Kotlin/JVM text input

Kotlin/JVM `String` values are UTF-16 sequences. The text API must validate that surrogate pairs are well-formed before UTF-8 conversion. An unpaired high or low surrogate must fail with a typed input error; silently replacing it would make round-trip behavior ambiguous. This is mandatory for the initial byte-level BPE profile.

For a valid Unicode string, the byte-level path uses its UTF-8 representation. The tokenizer must not assume that one Unicode code point, grapheme cluster, UTF-16 code unit, UTF-8 byte, and token are interchangeable units.

### 2.2 Raw byte input

A byte-level codec may expose a raw-byte API for arbitrary byte sequences. That API has byte-preserving semantics and does not assert that the bytes form valid UTF-8 text.

If arbitrary bytes are tokenized, `decodeToBytes` must reconstruct the exact byte sequence. Text decoding is a separate operation.

### 2.3 Round-trip guarantee

For a snapshot with no lossy normalizer:

- For every well-formed Unicode string `s`, `decodeText(encodeText(s), STRICT)` must equal `s`.
- For every byte array `b`, `decodeToBytes(encodeBytes(b))` must equal `b`, if the snapshot exposes the raw-byte API.
- Decoding must concatenate token byte fragments before UTF-8 validation. A token's byte sequence may not be valid UTF-8 by itself.

A decoder must not decode each token fragment independently using replacement characters; doing so can corrupt multi-byte code points split across token boundaries.

The strict text decoder must report malformed UTF-8 in generated token sequences. A replacement-mode decoder may be offered explicitly, but must never be the silent default for a lossless API.

## 3. Normalization

Normalization changes the representation of text and can change the bytes recovered by decoding. It must therefore be an explicit, fingerprinted part of a snapshot.

Accepted initial policy:

- `NONE` is the only normalizer in the initial byte-level BPE profile.
- NFC may be evaluated later as a separately versioned profile; it is not part of the initial runtime.
- NFKC is not the default; compatibility normalization can collapse distinctions that matter in some technical, mathematical, or linguistic contexts.
- The runtime must not silently switch normalization behavior when the JDK, Android release, ICU version, or host platform changes.

Any normalizer that depends on Unicode data must identify a pinned Unicode version and an implementation/data version. The implementation must pass the corresponding Unicode conformance tests. A platform-provided normalization function is acceptable only if the runtime can establish that its data version matches the snapshot contract.

When normalization is enabled, the documented round-trip result is the normalized text, not necessarily the original source string. If APIs expose token offsets, they must identify the coordinate space. The proposed canonical coordinate space is UTF-8 byte offsets in normalized text. Original-source offset mapping is a separate feature and must not be fabricated when normalization changes length or ordering.

## 4. Pre-tokenization

Pre-tokenization is an explicit, versioned component. It may split input into regions before a subword algorithm is applied, but it must preserve every input byte and every boundary needed for exact reconstruction.

Requirements:

- No whitespace or punctuation may be dropped unless an explicitly named lossy mode is selected.
- The same snapshot must produce the same boundaries on every supported runtime.
- Any Unicode property tables, regular-expression semantics, or language-specific rules that affect boundaries must be versioned and fingerprinted.
- A tokenizer must not rely on whichever Unicode tables happen to ship with the current JDK or Android release.
- The pre-tokenizer name alone is insufficient; its version and complete configuration are part of the snapshot.

The initial byte-level BPE profile uses a no-split/raw-stream input. Any later regex or Unicode-aware pre-tokenizer is a separate versioned profile and must be evaluated against this baseline. The project must measure the effect rather than assume that one convention is universal.

UAX #29 defines default grapheme, word, and sentence boundary guidance; it does not dictate one universal model-tokenizer pre-tokenization policy. Any UAX #29-based implementation must pin the applicable Unicode version and any tailoring.

## 5. Special tokens

Special-token handling must be explicit and safe for untrusted input. The initial profile reserves 32 special-token IDs in the range [0, 32); byte IDs begin at 32 and learned BPE IDs begin at 288.

- Each special token has a stable ID, exact spelling or byte representation, semantic type, and active/reserved state.
- Control tokens and user-defined literal tokens are distinct categories.
- Raw text must not be converted into control IDs merely because a substring resembles a control-token spelling.
- encodeText and encodeBytes never recognize special-token spellings. Such text is ordinary byte content.
- The initial profile has no automatic special-token matching mode.
- Special-token insertion by an application is a separate, typed operation that names an assigned special-token ID/name.
- Adding a token to a previously unassigned reserved slot creates a new snapshot and fingerprint. Existing assignments are immutable and reserved slots are never recycled within a lineage.
- Decoding renders a special token using its stored UTF-8 render bytes by default; skipping special tokens is an explicit option.
- Unknown or unassigned reserved IDs must not be treated as ordinary text tokens.

This prevents ordinary user content containing strings such as role markers from being silently promoted into system/control instructions.

## 6. Encoding behavior

For a fixed snapshot and fixed options, encoding must be deterministic and side-effect free.

- Encoding does not learn new tokens.
- Runtime frequency counts do not alter token IDs or merge ranks.
- Caches may affect performance but not output.
- Parallel execution must not change token order or segmentation.
- The same input and options must produce the same ordered token IDs across all supported implementations.
- Any stochastic segmentation mode must be a separate explicit API with an explicit seed and must not be used by deterministic encode calls.

For the initial byte-level BPE profile, the runtime stores unique learned byte pieces in increasing rank order. For each adjacent pair, the concatenated bytes are looked up in the piece-to-ID table; the lowest-ID candidate is merged first, with the leftmost occurrence winning equal-rank ties. Inference never recomputes corpus frequencies. This rank-based behavior is specified in ADR 0003.

For Unigram, the snapshot carries the scores and inference contract. Deterministic best-path decoding must define stable tie-breaking. Sampling is an explicit separate mode.

## 7. Decoding behavior

- Token IDs are resolved only against the exact snapshot supplied to the decoder.
- An out-of-range ID, inactive reserved ID, or unknown special ID produces a typed error unless an explicit recovery policy is requested.
- Byte fragments are concatenated before text decoding.
- Decoder cleanup, whitespace rewriting, punctuation rewriting, and special-token removal are never implicit.
- Skipping special tokens is an explicit decode option and its behavior is tested.
- Decoding a sequence produced by another snapshot is not assumed to be valid merely because the integer IDs are in range. The consumer must check the expected fingerprint.
- The default decoder concatenates byte fragments and special-token render bytes. Text decoding validates the complete concatenated sequence as strict UTF-8.

## 8. Truncation, padding, and offsets

Truncation and padding are model-input preparation operations, not hidden behavior in the base encoder. They belong in an explicit post-processor or consuming model adapter.

If offsets are implemented:

- The canonical offset unit is UTF-8 bytes in the input stream (the initial profile has no normalizer).
- Start offsets are inclusive and end offsets exclusive.
- Every offset must refer to the exact source representation defined by the API.
- If a token spans multiple source fragments or special-token insertion, offset provenance must be represented explicitly.
- UTF-16 code-unit offsets may be exposed as an additional JVM convenience, but are not the portable reference coordinate.

The initial stable API may omit offsets until this mapping is fully specified and tested.

## 9. Errors and resource limits

Implementations must use typed failures for at least:

- Malformed UTF-16 input.
- Invalid UTF-8 during strict text decoding.
- Token ID outside the snapshot ID space.
- Inactive or reserved token ID.
- Unsupported format or algorithm version.
- Corrupt snapshot, checksum mismatch, or invalid fingerprint.
- Invalid merge graph or vocabulary references.
- Input or snapshot exceeding configured resource limits.
- Unsupported normalizer or pre-tokenizer data version.

Loaders must validate declared counts and lengths before allocating large buffers. A tokenizer file is data, not executable code. Loading a snapshot must never execute embedded scripts, plugins, or arbitrary class names.

Resource limits must be configurable and documented for Android devices with constrained memory.

## 10. Compatibility invariants

- Token IDs are immutable within a vocabulary lineage.
- New BPE merges append IDs; they do not renumber earlier IDs.
- A change to any encoding-affecting field creates a different runtime fingerprint.
- A model checkpoint records the exact tokenizer fingerprint it requires.
- A runtime must fail clearly on unsupported snapshots rather than guessing.
- Replacing a snapshot must be an explicit consumer operation.
- Vocabulary growth does not guarantee model compatibility: segmentation changes can alter the token sequence even when old IDs remain stable.

## 11. Required conformance tests

The testkit should eventually provide shared golden vectors covering:

- Empty input, ASCII, whitespace, tabs, CRLF, punctuation, and control characters.
- NFC and decomposed sequences where normalization is enabled.
- Emoji, variation selectors, zero-width joiner sequences, combining marks, and regional indicators.
- Arabic RTL text, mixed-direction text, CJK, Thai, Indic scripts, and mixed-script passages.
- Embedded NUL and arbitrary byte arrays for raw-byte APIs.
- Malformed UTF-16 and malformed UTF-8.
- Special-token-like strings in ordinary untrusted text.
- Repeated and overlapping BPE merge candidates.
- Every boundary around reserved IDs and maximum vocabulary size.
- Cross-implementation and cross-runtime fingerprint equality.
- Corrupt, truncated, oversized, and unsupported snapshot files.

## References

- [ADR 0003: Initial byte-level BPE runtime profile and snapshot identity](adr/0003-initial-runtime-profile.md)
- [tiktoken byte-pair merge implementation](https://github.com/openai/tiktoken/blob/main/src/lib.rs)
- [Unicode Normalization Forms, UAX #15](https://www.unicode.org/reports/tr15/)
- [Unicode Text Segmentation, UAX #29](https://www.unicode.org/reports/tr29/)
- [SentencePiece paper](https://arxiv.org/abs/1808.06226)
- [SentencePiece model specification](https://github.com/google/sentencepiece/blob/master/src/sentencepiece_model.proto)
- [RFC 8949: Concise Binary Object Representation (CBOR)](https://www.rfc-editor.org/rfc/rfc8949)
