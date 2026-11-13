# Compatibility Policy

## Current status

The project is in early development. Public APIs, tokenizer algorithms, and persisted formats are not stable. No production compatibility guarantee is made before the first stable release.

## API versioning

The project intends to follow Semantic Versioning after the 1.0.0 release:

- MAJOR: incompatible public API or behavioral changes.
- MINOR: backward-compatible functionality.
- PATCH: backward-compatible fixes.

Before 1.0.0, breaking changes may occur and will be documented in the changelog.

## Tokenizer format versioning

Persisted tokenizer data has its own format version, independent of the library version.

A library release must not silently reinterpret an unknown or unsupported format version. It should fail with a clear, typed error or use an explicitly supported migration path.

## Vocabulary compatibility

Within a vocabulary lineage, existing token IDs are immutable and must never be recycled. Any change that alters tokenization behavior must result in a new snapshot identity.

A tokenizer fingerprint must cover all data that affects encoding behavior, not just vocabulary size or token strings.

## Model integration

A model checkpoint must record the tokenizer fingerprint it expects. A consumer must verify compatibility before inference, training, or evaluation.

Changing tokenizer segmentation may invalidate direct metric comparisons. Baselines and candidates must be evaluated under compatible tokenizer contracts.

## Deprecation

Once the stable API is released, deprecated APIs should be documented and retained for a reasonable migration window before removal, except where security or data-corruption risks require urgent action.
