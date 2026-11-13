# Architecture

## 1. Product boundary

ISQU Tokenizer Engine owns text tokenization and tokenizer vocabulary/model lifecycle. It does not own neural-network architecture, embeddings, optimizer state, model checkpoints, conversation training, or application UI.

The library is a standalone Kotlin/JVM project. Android applications may consume its JVM artifacts, but core modules must not import Android framework types.

The engine and trained tokenizer snapshots are separate deliverables. The engine does not require one bundled vocabulary, and a snapshot must not depend on a consuming application's package names or model architecture.

## 2. Module responsibilities

### tokenizer-core

Owns the immutable snapshot/domain model, runtime configuration, special-token definitions, token identity, encoded sequences, deterministic encoding/decoding, and runtime fingerprint value. It does not parse CBOR, access files, or depend on a persistence library.

Core runtime behavior is immutable, deterministic, thread-safe, and side-effect free wherever practical.

### tokenizer-training

Owns source-manifest validation, record-preserving corpus preprocessing, algorithm-specific training, incremental vocabulary evolution, candidate snapshot validation, and reproducibility metadata.

The initial internal profile is byte-level BPE with a ranked byte-piece vocabulary. Training must produce a candidate immutable snapshot. Promotion to an active snapshot is an explicit operation controlled by the consuming application.

### tokenizer-format-cbor (add when serialization implementation begins)

Owns the versioned deterministic-CBOR reader/writer for .isqutok, bounded parsing, fingerprint verification, and format conformance. It depends on tokenizer-core; the core module must not depend on this adapter.

### tokenizer-testkit

Owns reusable contract fixtures and test helpers. It must not become a runtime dependency of tokenizer-core.

## 3. Vocabulary identity

A vocabulary is more than a token count. It includes token strings or byte pieces, IDs, special-token assignments, merge rules or model scores, normalization settings, pre-tokenization settings, decoder behavior, and serialization metadata.

Within the initial isqu-byte-bpe-v1 lineage:

- Special-token IDs occupy the reserved range [0, 32).
- Byte IDs occupy [32, 288), with ID = 32 + unsigned byte value.
- Learned byte pieces occupy [288, idSpaceSize) in increasing rank/ID order.
- Existing IDs and their byte meanings are immutable; new special tokens or learned pieces use previously unassigned/appended IDs.
- IDs are never recycled.
- idSpaceSize and activeTokenCount are distinct; reserved but unassigned special slots count toward ID space, not active entries.
- Snapshot identity changes whenever runtime encoding or decoding behavior changes.

The exact representation will be frozen in a versioned format specification before production use. The current portable-format proposal is documented in [Tokenizer Format and Persistence](tokenizer-format.md).

## 4. Model-checkpoint integration

A neural checkpoint should record the tokenizer fingerprint used during training and evaluation.

Tokenizer state and neural weights have separate lifecycles. Restoring older neural weights must not silently delete a newer tokenizer snapshot. A consuming model engine is responsible for validating and migrating embedding and output-projection dimensions when it adopts an expanded vocabulary.

Evaluation must be read-only with respect to tokenizer state.

Preserving old token IDs does not by itself guarantee model compatibility. A changed merge list or normalizer can change how input text is segmented. Consumers must check the exact tokenizer fingerprint and explicitly evaluate or migrate a changed snapshot.

## 5. Algorithm selection

The initial internal prototype profile is byte-level BPE with a ranked byte-piece vocabulary, NONE normalization, and no pre-tokenizer. This is accepted for a first vertical slice, not as a universal algorithm default. Unigram remains the required comparator before a stable default claim; WordPiece remains a secondary interoperability target. Exact selection criteria remain in Algorithm Research and Selection.

Algorithm candidates must be evaluated against:

- Multilingual coverage and per-language tokenization cost.
- Unicode, byte fallback, and decoding behavior.
- Vocabulary efficiency and embedding/output-projection cost.
- Encode/decode fidelity and deterministic behavior.
- Training reproducibility and vocabulary evolution.
- Runtime and memory cost on Android-class devices.
- Interoperability and migration complexity.

See [Algorithm Research and Selection](algorithm-research.md) and [ADR 0001](adr/0001-vocabulary-sizing-and-evaluation.md).

## 6. Dependency policy

Production modules should minimize dependencies. The runtime module must not depend on a specific neural engine, app framework, database, cloud service, or network client.

Format adapters and optional integrations should be isolated from the minimal runtime dependency surface.

## 7. Behavioral and data contracts

The runtime's input, normalization, special-token, encode/decode, error, and conformance expectations are described in [Tokenization Contract](tokenization-contract.md).

Training data must be governed by source-level provenance, rights, privacy, and split policies. Public availability or a dataset package license alone is not sufficient to approve underlying text. See [Corpus Provenance, Rights, and Data Governance](corpus-provenance.md).

## 8. Architecture decision process

Changes to token identity, serialization, normalization, pre-tokenization, deterministic behavior, or compatibility guarantees require a documented decision. Proposed decisions do not become stable contracts until reviewed and accepted.

See the [ADR index](adr/README.md), especially ADR 0003: Initial runtime profile and snapshot identity.
