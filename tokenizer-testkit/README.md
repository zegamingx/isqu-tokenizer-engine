# tokenizer-testkit

Shared testing utilities for tokenizer implementations and consumers.

Planned responsibilities:

- Golden encoding and decoding test vectors.
- Vocabulary and snapshot compatibility fixtures.
- Determinism and round-trip contract assertions.
- Serialization and migration test helpers.
- Cross-version compatibility scenarios.

This module is intended for tests and integration verification. It is not part of the runtime dependency surface for ordinary application consumers.
