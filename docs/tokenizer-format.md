# Tokenizer Format and Persistence

## Status

This document defines requirements, not a finalized binary or text serialization format. The exact schema must be selected through an architecture decision record and compatibility tests before the first production release.

## Required properties

A persisted tokenizer snapshot must carry:

- A format identifier and format version.
- Tokenizer algorithm and algorithm version.
- Vocabulary entries and stable token IDs.
- Special-token definitions and IDs.
- Normalization and pre-tokenization configuration.
- Algorithm-specific data such as merge ranks or unigram scores.
- Metadata needed to reproduce encoding behavior.
- A cryptographic integrity checksum or fingerprint.

## Determinism

The same snapshot and input must produce the same token IDs across supported runtimes. Serialization must be canonical so equivalent snapshots do not produce ambiguous fingerprints.

## Safe loading

A loader must validate structural bounds, duplicate IDs, duplicate token definitions where prohibited, special-token collisions, malformed Unicode data, and checksum integrity before exposing a snapshot to callers.

Unknown format versions must not be loaded optimistically.

## Snapshot evolution

New vocabulary entries must append IDs without changing previously assigned IDs. A new snapshot must be independently identifiable and preserve its lineage relationship to its parent snapshot.

## Atomicity and rollback

Writing a snapshot must not leave a partially visible active state. Candidate creation, validation, promotion, and rollback are separate operations.

Restoring a model checkpoint must not implicitly restore or erase tokenizer data. The consuming model engine must handle embedding compatibility separately.

## Open decisions

- Canonical serialization representation.
- Hash algorithm and canonicalization rules.
- Compression policy.
- Incremental snapshot representation versus full snapshots.
- Migration guarantees between format versions.
