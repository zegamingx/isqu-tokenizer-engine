# Algorithm Research and Selection

- **Status:** Initial byte-level BPE rank profile accepted for internal prototype; no universal algorithm default is frozen
- **Last reviewed:** 2026-11-13
- **Scope:** tokenizer-core and tokenizer-training

## 1. Purpose

This document records the algorithm candidates and the evidence required before selecting a default. The project must not choose an algorithm because it is popular, easy to explain, or used by a nearby model. The choice must account for multilingual coverage, segmentation behavior, deterministic snapshots, training cost, inference speed, memory, portability, and the consumer's model budget.

A subword tokenizer has at least two distinct jobs:

1. **Training:** derive a vocabulary/model from a permitted corpus and explicit configuration.
2. **Runtime:** apply the frozen model to new input and return token IDs.

The learned artifact is not just a vocabulary list. It may include ordered merge rules or scores, special-token assignments, normalization, pre-tokenization, and decoder settings.

## 2. Candidate algorithms

### 2.1 Byte-level Byte Pair Encoding (BPE)

BPE begins from a base alphabet and repeatedly merges adjacent symbol pairs according to a training objective, commonly pair frequency. In ISQU's accepted initial profile, learned byte strings are stored as a ranked piece table: the concatenation of adjacent pieces is a candidate when it exists in the table, and the lowest-rank candidate is merged first.

For byte-level BPE, the base alphabet is the 256 byte values. Text is encoded as UTF-8 bytes before merges are applied.

**Strengths**

- Every byte value is representable, including bytes unseen in the training corpus.
- A rank-ordered byte-piece table gives a compact, explicit runtime model.
- Appending learned pieces creates a sequence of vocabulary extensions.
- A deterministic training run can produce nested prefix vocabularies if preprocessing, tie-breaking, and all trainer behavior are fixed.
- The runtime can be implemented without a neural network or external service.

**Limitations**

- Coverage is not compression: poorly represented scripts can remain split into individual byte tokens.
- A frequency-greedy objective can over-allocate merges to high-resource languages.
- BPE can create low-utility intermediate pieces.
- A tokenizer change can alter segmentation even when earlier token IDs are preserved.
- Byte-level sequences can have token boundaries inside a UTF-8 code point; decoding must concatenate bytes before text validation.

**Current assessment:** accepted initial internal profile as a ranked byte-piece vocabulary. The initial profile uses no normalizer or pre-tokenizer. It is not accepted as the final default for every corpus or consumer tier until benchmarked against Unigram and multilingual-fairness alternatives.

### 2.2 Character-based BPE and BPE with byte fallback

Character-based BPE begins from observed or selected Unicode characters. It can be more compact on well-curated monolingual corpora, but unseen characters require an explicit fallback or become unknown tokens.

Byte fallback is not identical to byte-level BPE:

- **Byte-level BPE** uses the 256 byte values as the base alphabet for all input.
- **Byte fallback** generally uses a character/subword vocabulary and falls back to byte tokens for pieces not represented by the vocabulary.

Both can provide coverage, but they produce different segmentation, ID layouts, model behavior, and snapshot formats. The format must identify which behavior is being used.

**Current assessment:** retain as an experimental comparator where a curated language scope and compact alphabet may be valuable. It is not the first universal-coverage candidate.

### 2.3 Unigram language-model tokenization

Unigram begins with a large candidate set of pieces, estimates piece probabilities, and prunes pieces while optimizing a unigram language-model objective. Runtime segmentation uses a best-path procedure such as Viterbi; sampling can be offered separately for subword regularization. Its scores, pruning lineage, fallback policy, and deterministic tie-breaking require a separate snapshot schema; they are not represented by the version-1 BPE ranked-piece array.

**Strengths**

- It optimizes a probabilistic segmentation objective rather than committing only to a greedy merge history.
- It can recover linguistically meaningful subword units and morphology more naturally in some settings.
- It supports alternate segmentations and explicit sampling modes.
- It is implemented by the established SentencePiece ecosystem and by Hugging Face Tokenizers.

**Limitations**

- Training and candidate pruning are more complex than basic BPE.
- The vocabulary is not a simple prefix of one ranked-piece list.
- The snapshot must preserve scores, base/fallback policy, and tie-breaking behavior.
- Sampling must be explicitly separated from deterministic encoding.
- Reproducibility requires pinning random-number behavior, trainer version, and all relevant configuration.

Research comparing BPE and Unigram has found Unigram competitive or better in some settings, including morphological segmentation, but the evidence is not universal across languages, corpora, model sizes, and tasks.

**Current assessment:** mandatory comparison candidate before a universal algorithm/default claim or a 1.0 default freeze. It is not a blocker for the first internal BPE vertical slice. A trusted pinned reference implementation may be used for the first controlled comparison; a native Unigram runtime would require its own profile/ADR.

### 2.4 WordPiece

WordPiece is used in BERT-family models and resembles bottom-up subword construction, but merge selection and inference semantics differ from ordinary BPE. Implementations commonly use continuation prefixes and a greedy longest-match procedure, with an unknown-token policy.

**Strengths**

- Important compatibility target for existing model families.
- Mature reference behavior exists in Hugging Face Tokenizers.
- Its scoring objective differs from raw pair-frequency BPE.

**Limitations**

- Typical implementations depend on a pre-tokenization and word-boundary contract.
- Unknown-token behavior and maximum word length need to be specified.
- Training recipes and implementation variants are not identical across ecosystems.
- It does not provide the same universal-byte-coverage guarantee as byte-level BPE unless paired with a defined byte fallback design.

**Current assessment:** compatibility and comparison target, not the first native trainer unless a concrete consumer requires it.

### 2.5 Other families

Word-level, character-level, morphological, and byte-latent approaches may be useful in specific tasks, but are outside the initial implementation decision. They should only enter the core algorithm portfolio when they bring a measurable benefit that cannot be achieved by a well-specified subword design.

## 3. Important design distinctions

### 3.1 Vocabulary size is not a quality metric

A larger vocabulary may reduce sequence length, but increases embedding and output-projection cost. It may also allocate capacity to rare or noisy substrings. The engine must support target-size configurations; it must not treat 400k as a universal quality target.

### 3.2 Coverage is not multilingual fairness

A byte base guarantees representability, not equal compression. A low-resource or underrepresented script may still use many one-byte tokens. Evaluation must report per-language costs, byte-token share, and cross-lingual disparities.

Recent work on Parity-aware BPE proposes changing the merge objective to improve the worst-compressed language. This is a relevant experimental variant, not a feature to adopt without independent reproduction and downstream evaluation.

### 3.3 Compression is not downstream quality

Compression is useful but insufficient. Studies have reported both correlations and counterexamples between compression metrics and downstream model performance. The algorithm decision must combine tokenizer-intrinsic measures with controlled language-model experiments where compute allows.

### 3.4 BPE's nested-vocabulary property is conditional

A deterministic BPE run can produce prefix vocabularies when all target sizes share the same base, pre-tokenizer, normalizer, corpus, sampling policy, trainer implementation, and deterministic tie-breaking.

This property must be tested against direct-size runs. It must not be generalized to Unigram or to BPE implementations that change sampling or training behavior based on target size.

## 4. Existing ecosystem review

| Project or family | Implementation / focus | Relevance to ISQU |
| --- | --- | --- |
| SentencePiece | C++ and Python implementation; BPE and Unigram; can train from raw sentences | Reference algorithm, normalizer, model-file, and interoperability behavior |
| Hugging Face Tokenizers | Rust implementation with BPE, Unigram, WordPiece and other components | Reference for configurable models, normalization alignment, pre-tokenization, and production runtime |
| Ktoken | Kotlin Multiplatform BPE-oriented library; documented usage centers on supported model encodings and local/remote encoding data | Direct Kotlin/JVM comparison; avoid claiming ISQU is the first Kotlin tokenizer |
| KotlinNLP NeuralTokenizer | Historical Kotlin neural text tokenizer | Evidence of prior Kotlin-native NLP tokenization work; not the target LLM subword design |
| Kotori | Kotlin Japanese morphological tokenizer | Relevant for language-specific morphological analysis, not a general LLM subword engine |

The opportunity is not "a tokenizer written in Kotlin has never existed." That claim is false. ISQU must differentiate through its independently specified snapshot format, deterministic vocabulary lifecycle, training/runtime separation, cross-language compatibility, and device-conscious implementation—provided those features are implemented and measured.

## 5. Proposed implementation sequence

1. Define the behavioral contract and portable snapshot format before freezing algorithm-specific public APIs.
2. Implement a byte-level BPE reference path behind an internal algorithm contract.
3. Build a correctness corpus and deterministic merge-order tests.
4. Build a Unigram comparison path or use a version-pinned reference implementation for research.
5. Compare against WordPiece on compatibility fixtures, not as a presumed universal winner.
6. Evaluate a parity-aware BPE variant after a reproducible baseline exists.
7. Freeze the first stable algorithm only after correctness, quality, performance, and compatibility criteria are met.

The first implementation sequence is a proposal. If experiments show Unigram or another method is clearly better for the declared product workload, the architecture should adapt before 1.0.0.

## 6. Evaluation dimensions

Every candidate must be tested against the same approved corpus manifests and split policy.

| Dimension | Required evidence |
| --- | --- |
| Coverage | No unknown bytes for the byte-level path; explicit unknown/fallback rate for other paths |
| Compression | Bytes/token and tokens/1,000 bytes per language |
| Segmentation | Word/grapheme alignment where language-appropriate; UTF-8 boundary metrics |
| Fairness | Per-language tokenization cost on parallel/content-controlled samples |
| Robustness | Emoji, combining marks, RTL, mixed scripts, malformed input, whitespace |
| Determinism | Repeated training and cross-runtime golden-vector equality |
| Runtime | Throughput, p50/p95 latency, allocations, memory, cold-load time |
| Artifact | Snapshot size, load time, fingerprint verification |
| Model impact | Held-out BPB and task-level performance for controlled reference models |
| Device fit | CPU, memory, storage, and thermal behavior on representative Android hardware |

Do not use raw tokens-per-word across all scripts as the sole cross-lingual metric. Word boundaries differ by language and writing system. Report per-language metrics and use parallel content-controlled samples for parity analysis.

## 7. Research references

- [SentencePiece paper](https://arxiv.org/abs/1808.06226)
- [SentencePiece training options](https://google.github.io/sentencepiece/doc/options/)
- [SentencePiece model schema](https://github.com/google/sentencepiece/blob/master/src/sentencepiece_model.proto)
- [Hugging Face Tokenizers model API](https://huggingface.co/docs/tokenizers/main/en/api/models)
- [Hugging Face tokenizer algorithm summary](https://huggingface.co/docs/transformers/main/en/tokenizer_summary)
- [Byte Pair Encoding is Suboptimal for Language Model Pretraining](https://aclanthology.org/2020.findings-emnlp.414/)
- [Parity-Aware Byte-Pair Encoding](https://aclanthology.org/2026.acl-long.342/)
- [TokEval: A Tokenizer Evaluation Suite](https://arxiv.org/abs/2608.18062)
- [TokLens: A Multilingual Lens on Tokenizer Quality for LLMs](https://aclanthology.org/2026.acl-srw.18/)
- [Ktoken](https://github.com/aallam/ktoken)
- [KotlinNLP NeuralTokenizer](https://github.com/KotlinNLP/NeuralTokenizer)
- [Kotori](https://github.com/wanasit/kotori)
