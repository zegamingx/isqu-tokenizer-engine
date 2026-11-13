# tokenizer-training

The vocabulary and tokenizer-model training module.

Planned responsibilities:

- Corpus ingestion boundaries and deterministic preprocessing.
- Training algorithms behind explicit, testable contracts.
- Incremental vocabulary evolution without reassigning existing token IDs.
- Candidate snapshot generation and validation.
- Training reports that identify the source corpus and resulting snapshot.

Tokenizer training is separate from neural-network weight training. This module must not update model embeddings, optimizer state, or neural checkpoints.

The initial algorithm portfolio is still under research. No production implementation is present yet.
