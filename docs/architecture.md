# Architecture

## 1. Product boundary

ISQU Tokenizer Engine owns text tokenization and tokenizer vocabulary/model lifecycle. It does not own neural-network architecture, embeddings, optimizer state, model checkpoints, conversation training, or application UI.

The library is a standalone Kotlin/JVM project. Android applications may consume its JVM artifacts, but core modules must not import Android framework types.

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

A vocabulary is more than a token count. It includes token strings, IDs, special-token assignments, merge or model data, normalization settings, pre-tokenization settings, and serialization metadata.

Within a vocabulary lineage:

- Existing IDs are immutable.
- New IDs are appended.
- IDs are never recycled.
- Special-token IDs are explicitly reserved.
- Snapshot identity changes whenever encoding behavior changes.

The exact representation will be frozen in a versioned format specification before production use.

## 4. Model-checkpoint integration

A neural checkpoint should record the tokenizer fingerprint used during training and evaluation.

Tokenizer state and neural weights have separate lifecycles. Restoring older neural weights must not silently delete a newer tokenizer snapshot. A consuming model engine is responsible for validating and migrating embedding and output-projection dimensions when it adopts an expanded vocabulary.

Evaluation must be read-only with respect to tokenizer state.

## 5. Algorithm selection

No tokenizer algorithm is frozen by this scaffold. BPE, unigram, byte-level approaches, byte fallback, normalization, and pre-tokenization must be evaluated against:

- Multilingual coverage.
- Unicode and emoji behavior.
- Vocabulary efficiency.
- Encode/decode fidelity.
- Deterministic incremental training.
- Runtime and memory cost on Android-class devices.
- Compatibility and migration complexity.

Algorithm decisions require documented evidence and compatibility tests.

## 6. Dependency policy

Production modules should minimize dependencies. The runtime module must not depend on a specific neural engine, app framework, database, cloud service, or network client.
