# ADR 0001: Vocabulary sizing strategy and evaluation protocol

*Status: Proposed · Date: 2026-11-13 · Applies to: tokenizer-core, tokenizer-training*

## Context

ISQU Tokenizer Engine is a library, and a vocabulary is a trained artifact ([architecture.md §3](../architecture.md#3-vocabulary-identity)). Consumer models range from roughly 1M to 1B parameters, so no single vocabulary size fits every consumer.

The embedding table costs `vocab × d_model` parameters, twice that when input and output embeddings are not tied. A larger vocabulary also makes the output projection (logits) more expensive on every generated token. One matrix at `d_model = 128` in FP16 (MB = 10^6 bytes):

| Vocabulary | Embedding parameters | Memory |
| --- | --- | --- |
| 1,024 | 0.13M | 0.26 MB |
| 4,096 | 0.52M | 1.05 MB |
| 8,192 | 1.05M | 2.10 MB |
| 32,768 | 4.19M | 8.39 MB |
| 65,536 | 8.39M | 16.78 MB |
| 131,072 | 16.78M | 33.55 MB |

At this width, an 8,192-token vocabulary alone is about 1.05M parameters, the entire budget of the smallest tier. The sweep must therefore cover small vocabularies, and the current 1,024-token vocabulary of the consumer is the baseline to beat.

## Decision

1. **Engine and vocabularies are separate deliverables.** The engine library bundles no mandatory vocabulary. Reference vocabularies are published as separate, versioned snapshots, one per consumer tier, and users may train their own.
2. **Byte-level BPE first.** The base alphabet is the 256 byte values, so there is no unknown token and every string round-trips. Unigram follows later as a comparison baseline, not as the default.
3. **Reserved ID layout.** IDs `[0, S)` form a reserved special-token block that includes unassigned slots (proposed `S = 32`, frozen in the format specification). IDs `[S, S+256)` are byte tokens, and merge tokens follow in merge-rank order. Vocabulary size is `S + 256 + merge count`, and adding a special token later never shifts a byte or merge ID.
4. **One training run, nested vocabularies.** Train once to the largest size in the sweep and derive each smaller vocabulary by truncating the ordered merge list. With deterministic tie-breaking, this equals training that size directly. Each smaller vocabulary is an ancestor of the larger ones in a single lineage, which matches [architecture.md §3](../architecture.md#3-vocabulary-identity): IDs are immutable and new IDs are appended. Each size is still its own snapshot with its own fingerprint, because adding merges changes segmentation ([compatibility-policy.md](../compatibility-policy.md)).
5. **Sweep grid.** 1,024 / 2,048 / 4,096 / 8,192 / 16,384 / 32,768 / 65,536, with 131,072 optional. A 400k vocabulary is out of scope until a large multilingual consumer needs it. The Unigram baseline is trained separately at each size because it does not nest.
6. **Selection rule.** For each consumer tier, pick the smallest vocabulary whose mean held-out bits-per-byte is within 1% (relative) of the best, subject to the RAM and snapshot-size limits of that tier. The 1% threshold is a proposed default. If differences fall within seed-to-seed variation, pick the smaller vocabulary.

## Evaluation protocol

**Corpus.** Use only corpora with documented provenance and a license that permits this use. Split by document into tokenizer-training and held-out evaluation sets, deduplicate across the split, and record corpus hashes. Initial languages: Indonesian, English, Arabic, Mandarin, Japanese. Record the mix in run metadata.

**Fixed preprocessing.** Every run uses the same normalization form, pre-tokenization and Unicode data version.

**Intrinsic metrics, per language.** Bytes per token; tokens per 1,000 characters; byte-token rate (share of emitted tokens that are raw bytes); round-trip fidelity on an edge-case suite (emoji, combining marks, right-to-left text, mixed scripts); encode and decode throughput; peak RAM; snapshot size. Measure throughput and RAM on Android-class hardware, not only on a development machine.

**Extrinsic metric: bits per byte (BPB).** `BPB = (total cross-entropy in nats ÷ ln 2) ÷ total UTF-8 bytes`. Train a fixed reference architecture from scratch for each vocabulary, with total parameters matched to the tier (embedding included, so a larger vocabulary means a smaller body), the same number of training bytes seen, the same optimizer and hyperparameters, and at least three seeds. Report the mean and standard deviation of held-out BPB, overall and per language. Per-token loss and perplexity are not comparable across vocabularies and are not used for selection. Optionally repeat with non-embedding parameters matched to separate the embedding-cost effect. Also record generation speed in bytes per second on target hardware, since fewer tokens per byte trades against costlier logits.

**Compute budget.** Run the full protocol on the smaller tiers. For tiers too large to train repeatedly from scratch, use a scaled-down proxy with the same embedding-to-body ratio and label the result as extrapolated.

**Reproducibility.** Ties between equally frequent pairs are broken by a fixed total order on byte sequences. Results must not depend on hash-map iteration order or thread scheduling. Two runs with identical corpus, configuration and trainer version must produce identical snapshot fingerprints. Each run records corpus hashes, configuration, normalization and Unicode data version, and trainer version.

**Platform Unicode data.** Behavior that affects token IDs must not rely on platform tables (`java.text.Normalizer`, `Character` properties, regex classes), because JDK and Android versions ship different Unicode data. The snapshot either embeds its own normalization and pre-tokenization tables or records a pinned Unicode version that the runtime verifies, and both are covered by the fingerprint. This belongs in [tokenizer-format.md](../tokenizer-format.md) and is recorded here because it constrains the sweep.

## Consequences

**Benefits**

- One training run yields the vocabulary for every tier, and IDs stay stable along the lineage.
- Small tiers do not pay for a large vocabulary, and large tiers are not stuck with a small one.
- A consumer can warm-start a larger model by copying existing embedding rows and appending new ones. This remains the responsibility of the consumer ([architecture.md §4](../architecture.md#4-model-checkpoint-integration)).

**Costs**

- Several reference vocabularies to version, test and publish.
- Unigram cannot be derived by truncation, so its baseline needs one training run per size.
- Every size has a distinct fingerprint, and a checkpoint is valid only with its exact snapshot (already required by [compatibility-policy.md](../compatibility-policy.md)).
- The BPB runs dominate the cost of the sweep.

## Alternatives considered

- **One large vocabulary for every tier (32k or 64k).** Rejected: the embedding table would dominate or exceed the budgets of the smallest tiers.
- **400k vocabulary now.** Rejected: 51.2M embedding parameters at `d_model = 128` (102.4 MB in FP16 per matrix) before any other layer. Revisit for a large multilingual consumer.
- **Independent training per size.** Rejected: sizes would not nest, IDs would not carry across sizes, and training cost would scale with the number of sizes.
- **Unigram as the default.** Deferred: it does not nest, and its training is more complex. Kept as a baseline.
- **Character-level base alphabet with byte fallback.** Not selected for the first implementation: at small vocabularies a multilingual character alphabet consumes the budget, while a byte base fixes coverage at 256 entries. Revisit if byte-token rates for CJK and Arabic prove too costly.

## Open questions

- Size of the reserved special-token block (`S`).
- Normalization form (NFC or NFKC) and pre-tokenization rules. These must be frozen before the sweep because they change every metric.
- Language scope per tier. Smaller tiers could cover only Indonesian and English and leave other scripts to byte tokens, at about 2 bytes per Arabic letter and 3 per CJK character.
- Whether a released vocabulary may grow when the embedding matrix of the consumer has a fixed size (reserve spare IDs or require migration).
- Hash algorithm and canonical serialization, tracked under “Open decisions” in [tokenizer-format.md](../tokenizer-format.md).
