# ADR 0001: Vocabulary sizing strategy and evaluation protocol

- **Status:** Proposed; not accepted as a frozen architecture decision
- **Date:** 2026-11-13
- **Scope:** tokenizer-core, tokenizer-training, reference vocabulary artifacts
- **Decision owner:** ISQU Tokenizer Engine maintainers

## Context

ISQU Tokenizer Engine is a reusable library. A trained vocabulary is a separate artifact consumed by a model; it is not an intrinsic part of the library binary. A single vocabulary size cannot be assumed to fit every model, language mix, context budget, or device tier.

For a model width of 128 and FP16 embeddings, one embedding matrix requires:

`vocabulary ID space × 128 × 2 bytes`.

This excludes output-projection cost when embeddings are untied, optimizer state during training, other model parameters, runtime buffers, and allocator overhead.

| Vocabulary ID space | Embedding parameters | One FP16 matrix |
| ---: | ---: | ---: |
| 1,024 | 0.131M | 0.26 MB |
| 2,048 | 0.262M | 0.52 MB |
| 4,096 | 0.524M | 1.05 MB |
| 8,192 | 1.049M | 2.10 MB |
| 16,384 | 2.097M | 4.19 MB |
| 32,768 | 4.194M | 8.39 MB |
| 65,536 | 8.389M | 16.78 MB |
| 131,072 | 16.777M | 33.55 MB |
| 400,000 | 51.200M | 102.40 MB |

MB in this table means 1,000,000 bytes. If input embeddings and output projection are not tied, the two matrices can approximately double this particular storage cost. Actual compute cost also depends on output projection shape and decoding strategy.

The table shows why 400k must not be a default target for small local models. It does not prove that small vocabularies are universally better: tokenization quality and language coverage must be measured against a defined workload.

The current 1,024-ID ArgusA vocabulary is a useful consumer baseline, not an ISQU-wide default and not a reason to optimize the engine exclusively for ArgusA.

## Proposed direction

1. **Engine and vocabulary are separate deliverables.** The engine must not bundle a mandatory vocabulary. Reference snapshots, if published, are separate versioned artifacts. Consumers may also train their own snapshots.
2. **Byte-level BPE is the lead implementation candidate, not a frozen algorithm decision.** Its 256-byte base provides coverage for every byte sequence. For well-formed Unicode text encoded as UTF-8, this avoids an unknown token for unseen characters. It does not guarantee good compression or equal token cost across languages.
3. **Unigram is a required research comparator.** It must not be dismissed merely because it is harder to train or does not produce a nested merge list.
4. **WordPiece is a secondary compatibility comparator.** It is relevant to existing model families, but it is not the default implementation target until its training and inference contract is specified independently.
5. **No 400k reference vocabulary is planned for the initial release.** Revisit only when a defined large-model or multilingual workload demonstrates a measurable need.
6. **All vocabulary sizes are experimental configurations.** The engine accepts a requested target size subject to the selected algorithm's valid range and reserved-ID layout. The project does not promise that every requested size can be produced from every corpus.

## ID-space proposal for byte-level BPE

The current layout proposal is:

- A reserved special-token ID block `[0, S)`; `S = 32` is an unresolved proposal, not a frozen value.
- The 256 byte tokens occupy `[S, S + 256)`.
- BPE merge outputs are assigned in merge order beginning at `S + 256`.
- The output ID for merge number `i` is implied by its position in the ordered merge list.

The format must distinguish:

- `idSpaceSize`: one greater than the highest allocated ID; this is the row count a naïve embedding table must support.
- `activeTokenCount`: assigned special tokens plus the 256 byte tokens plus merge outputs.
- `reservedSpecialCapacity`: the number of special-token ID slots reserved in the layout.
- `mergeCount`: number of trained merge outputs.

Unassigned reserved special slots are not active vocabulary entries, but they still occupy positions in the ID space. Adding a special token into a reserved slot creates a new snapshot and a new runtime fingerprint. It must never silently change an existing snapshot.

The selected value of `S`, the treatment of inactive reserved slots, and the public meaning of `vocabSize` remain open. Until they are resolved, tables above describe an ID-space target under the illustrative `S = 32` layout, not a commitment to 32 active special tokens.

## Vocabulary-size sweep

The initial sweep should cover:

`1,024 / 2,048 / 4,096 / 8,192 / 16,384 / 32,768 / 65,536`

Add `131,072` only when the available corpus, compute budget, and target consumer justify it. Do not start with 400k.

For byte-level BPE with fixed base and deterministic training, one maximum-size training run can produce smaller prefix snapshots by truncating the ordered merge list. This is a useful property, not an assumption to leave untested. The implementation must prove that each prefix snapshot is identical to a direct run stopped at that merge count under the same corpus, preprocessing, trainer version, and tie-breaking rules.

Unigram vocabularies are not merge prefixes. They require separate target-size runs or a separately specified pruning path. Do not represent Unigram as if it had BPE's nested-vocabulary property.

## Evaluation protocol

### Corpus controls

- Use corpora with documented source provenance and a recorded rights decision.
- Split by source document or source group before sampling sentences.
- Perform exact and near-duplicate detection across all splits before training.
- Keep a held-out tokenizer evaluation split separate from tokenizer training.
- Record language and script proportions; do not let raw web-crawl volume silently determine the language mixture.
- Freeze the training/evaluation corpus manifests for each experiment.
- Never use FLORES+ or another public benchmark's evaluation split as tokenizer training data when it is intended to remain an evaluation set.

Initial test languages and scripts should include Indonesian, English, Arabic, Simplified Chinese, Japanese, and additional scripts selected to expose byte fragmentation (for example Thai, Tamil, and Javanese). The final mixture remains an experiment parameter, not a promise that this first list defines the product's permanent language scope.

### Intrinsic tokenizer metrics

Report each metric per language and per script where applicable:

- UTF-8 bytes per token.
- Tokens per 1,000 UTF-8 bytes.
- Word fertility only where word boundaries are consistently defined for that language.
- Grapheme-cluster fertility only where the UAX #29 version and segmentation policy are pinned.
- Share of emitted tokens that are exactly one raw byte token; do not confuse this measurement with a count of all bytes represented by the tokenizer.
- Share of token boundaries that fall on UTF-8 code-point boundaries.
- Round-trip fidelity for valid text, including emoji, combining marks, RTL text, mixed scripts, whitespace, punctuation, and embedded NUL.
- Encode/decode throughput in bytes per second and tokens per second.
- p50/p95 latency, peak memory, allocation volume, cold-load time, and snapshot size.
- Malformed-input behavior and resource-limit behavior.

Bytes-per-token is useful for comparing tokenizers on the same text within a language, but raw cross-language comparisons are affected by UTF-8 encoding width and script. Use parallel or content-controlled samples for cross-language parity analysis; report raw values and normalized comparisons rather than one misleading global score.

### Extrinsic model metric

Use bits per byte (BPB) as a tokenizer-agnostic language-model metric:

`BPB = (sum of token negative log-likelihoods in nats / ln(2)) / number of UTF-8 input bytes`.

Report the precise scoring convention, treatment of BOS/EOS and special tokens, and byte denominator. Never compare per-token perplexity across tokenizers as if the token units were identical.

Run two separate model comparisons:

1. **Fixed-backbone comparison:** keep the non-embedding architecture fixed and report the resulting total-parameter and output-projection differences. This isolates a tokenizer change more directly.
2. **Fixed-budget comparison:** keep total model parameters within a defined budget, including embeddings and an untied output projection. Adjusting the model body changes more than the tokenizer, so report this as a deployment-efficiency comparison rather than a tokenizer-only causal result.

Match corpus exposure in UTF-8 bytes for the primary comparison. Also record tokens processed, optimizer steps, wall-clock time, estimated FLOPs, and memory. Where practical, add a compute-matched secondary run. Use at least three seeds for exploratory runs and at least five for confirmatory comparisons when resources permit. Report mean, standard deviation, and the full per-seed results.

### Selection rule

Do not freeze the previously suggested "within 1% of the best BPB" rule. It is a candidate decision threshold only. Run a pilot first, estimate seed variance and practical significance, then preregister the selection threshold before the confirmatory sweep.

Select per consumer tier, not globally. Prefer the smallest ID space that meets the tier's declared quality, latency, memory, and language-coverage requirements. If measured differences are within the experiment's uncertainty and practical tolerance, prefer the smaller snapshot.

### Reproducibility

A run manifest must record corpus hashes, source manifest version, preprocessing and Unicode data version, algorithm and trainer version, all hyperparameters, deterministic tie-breaking rules, seed where applicable, runtime environment, and output fingerprint.

For BPE, equal-frequency pairs must be ordered by a fixed comparison of the unsigned byte sequence of the left token and then the unsigned byte sequence of the right token. Comparing only the concatenated pair bytes is insufficient because distinct pairs can produce the same concatenated byte sequence.

Identical inputs and a fixed trainer version must produce identical output fingerprints. Concurrency, hash-map iteration order, filesystem order, and thread scheduling must not influence merge selection or snapshot serialization.

## Consequences

### Benefits

- Consumers can choose a vocabulary appropriate to model capacity and device constraints.
- The engine distribution remains small and independent of large corpus artifacts.
- BPE prefix snapshots can support controlled tier experiments if the equivalence tests pass.
- A fixed protocol makes performance, multilingual quality, and vocabulary-size claims reproducible.

### Costs and risks

- Reference vocabularies require independent versioning, licensing review, and compatibility tests.
- A BPE merge list and a Unigram vocabulary have different lineage/evolution semantics.
- Increasing vocabulary size changes segmentation and model output dimensionality even when old token IDs remain stable.
- Embedding-row migration alone does not guarantee behavioral compatibility: a new merge can change how the same text is segmented.
- The BPB experiments require model training and dominate compute cost.
- Large web corpora carry source-rights, privacy, duplication, and quality risks that are not solved by the tokenizer algorithm.

## Alternatives considered

- **One 32k or 64k vocabulary for every consumer:** rejected as a universal policy because it can spend too much of a small model's parameter and memory budget.
- **400k as the default:** rejected for the initial scope; the embedding cost is too large for many small models and no target workload has justified it.
- **Independent BPE training at every size:** not selected as the primary sweep method if prefix equivalence can be demonstrated; retain direct runs as a verification control.
- **Unigram as the only implementation:** not selected yet; its language-model objective is valuable, but the training and snapshot-evolution model differs materially from BPE.
- **Character-only base alphabet:** not selected for the first byte-coverage candidate because a multilingual character inventory can be large and unseen characters need a defined fallback.
- **A universal compression-only objective:** rejected because compression alone misses cross-lingual cost, structural alignment, model quality, and device performance.

## Open questions

- Final special-token reserved capacity and active-slot semantics.
- Whether the primary runtime supports only byte-level BPE in the first stable release or includes Unigram before 1.0.0.
- Normalization default and supported Unicode versioning policy.
- Whether the first reference model prioritizes general multilingual coverage or a declared device/language tier.
- Exact pilot design, compute budget, seed count, and selection margin.
- Whether the first BPE training algorithm is fully streaming, external-memory, or memory-bounded in-memory.
- When an updated vocabulary is a compatible extension versus a distinct tokenizer lineage.

## References

- [SentencePiece: A simple and language independent subword tokenizer and detokenizer](https://arxiv.org/abs/1808.06226)
- [SentencePiece training options](https://google.github.io/sentencepiece/doc/options/)
- [Byte Pair Encoding is Suboptimal for Language Model Pretraining](https://aclanthology.org/2020.findings-emnlp.414/)
- [Parity-Aware Byte-Pair Encoding](https://aclanthology.org/2026.acl-long.342/)
- [TokEval: A Tokenizer Evaluation Suite](https://arxiv.org/abs/2608.18062)
- [TokLens: A Multilingual Lens on Tokenizer Quality for LLMs](https://aclanthology.org/2026.acl-srw.18/)
- [Unicode Normalization Forms, UAX #15](https://www.unicode.org/reports/tr15/)
- [Unicode Text Segmentation, UAX #29](https://www.unicode.org/reports/tr29/)
