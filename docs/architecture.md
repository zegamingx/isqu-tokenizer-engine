# Architecture

## 1. Product boundary

ISQU Tokenizer Engine owns text tokenization and tokenizer vocabulary/model lifecycle. It does not own neural-network architecture, embeddings, optimizer state, model checkpoints, conversation training, or application UI.

The library is a standalone Kotlin/JVM project. Android applications may consume its JVM artifacts, but core modules must not import Android framework types.

The engine and trained tokenizer snapshots are separate deliverables. The engine does not require one bundled vocabulary, and a snapshot must not depend on a consuming application's package names or model architecture.

## 2. Module responsibilities

### tokenizer-core

Owns stable runtime contracts: tokenizer configuration; normalization and pre-tokenization configuration; special-token definitions; vocabulary snapshots and token identity; encoding and decoding; tokenizer fingerprinting; and versioned serialization interfaces.

Core runtime behavior should be deterministic and side-effect free wherever practical.

### tokenizer-training

Owns corpus validation and preprocessing, algorithm-specific training, incremental vocabulary evolution, candidate snapshot validation, and reproducibility metadata.

Training must produce a candidate snapshot. Promotion to an active snapshot is an explicit operation controlled by the consuming application.

### tokenizer-testkit

Owns reusable contract fixtures and test helpers. It must not become a runtime dependency of tokenizer-core.

## 3. Vocabulary identity

A vocabulary is more than a token count. It includes token strings or byte pieces, IDs, special-token assignments, merge rules or model scores, normalization settings, pre-tokenization settings, decoder behavior, and serialization metadata.

Within a vocabulary lineage:

- Existing IDs are immutable.
- New IDs are appended.
- IDs are never recycled.
- Special-token IDs are explicitly reserved and have active/inactive state.
- Snapshot identity changes whenever encoding or decoding behavior changes.
- The ID-space size is distinct from the number of active token entries.

The exact representation will be frozen in a versioned format specification before production use. The current portable-format proposal is documented in [Tokenizer Format and Persistence](tokenizer-format.md).

## 4. Model-checkpoint integration

A neural checkpoint should record the tokenizer fingerprint used during training and evaluation.

Tokenizer state and neural weights have separate lifecycles. Restoring older neural weights must not silently delete a newer tokenizer snapshot. A consuming model engine is responsible for validating and migrating embedding and output-projection dimensions when it adopts an expanded vocabulary.

Evaluation must be read-only with respect to tokenizer state.

Preserving old token IDs does not by itself guarantee model compatibility. A changed merge list or normalizer can change how input text is segmented. Consumers must check the exact tokenizer fingerprint and explicitly evaluate or migrate a changed snapshot.

## 5. Algorithm selection

No tokenizer algorithm is frozen by this scaffold. Byte-level BPE is the lead implementation candidate, while Unigram is a required comparison candidate and WordPiece is a secondary interoperability target. These are research priorities, not accepted public API commitments.

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

See the [ADR index](adr/README.md).
