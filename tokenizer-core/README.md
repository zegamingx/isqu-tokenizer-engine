# tokenizer-core

The tokenizer runtime module.

Planned responsibilities:

- Deterministic text-to-token encoding and token-to-text decoding.
- Immutable tokenizer and vocabulary snapshots.
- Stable special-token registry.
- Explicit normalization and pre-tokenization configuration.
- Snapshot identity and fingerprint calculation.
- Versioned serialization contracts.

This module must remain independent of Android APIs, application-specific code, network access, and neural-network implementations.

The public API and persistence format are not frozen. No production implementation is present yet.
