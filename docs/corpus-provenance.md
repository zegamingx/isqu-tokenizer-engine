# Corpus Provenance, Rights, and Data Governance

- **Status:** Policy proposal; dataset admission requires per-source review
- **Last reviewed:** 2026-11-13
- **Applies to:** tokenizer-training, reference vocabulary snapshots, benchmark corpora

## 1. Purpose

Tokenizer training is data processing. A dataset being downloadable, publicly visible, or tagged with an open license does not automatically establish that every text record may be used, redistributed, or included in a downstream artifact.

ISQU must preserve enough provenance to reproduce a tokenizer run and review the rights and privacy constraints of its inputs.

This document is an engineering intake policy, not legal advice. High-risk or commercially intended corpus use requires review by qualified counsel in the relevant jurisdictions.

## 2. Admission principles

A corpus or corpus component may enter a tokenizer-training run only after these questions are answered:

1. Who created or collected the text?
2. What is the original source of each record or source group?
3. What license or legal basis applies to the database/package?
4. What license or rights apply to the underlying text itself?
5. Does the permission cover the intended training use?
6. Does it permit the planned distribution of derived tokenizer snapshots, if those snapshots will be released?
7. Are attribution, share-alike, non-commercial, access-gate, privacy, or use restrictions present?
8. Can excluded or withdrawn records be traced and removed from future training runs?
9. Is the corpus free of known sensitive data, credentials, and prohibited content to the extent required by the project's safety policy?

The license of a dataset package and the rights in its individual content records must be recorded separately. A database license may cover database structure or database rights without licensing every source text.

## 3. Dataset admission statuses

| Status | Meaning | Use |
| --- | --- | --- |
| **Approved** | Source, content rights, use, provenance, privacy, and attribution have been reviewed for the exact intended use | May be used within the approved scope |
| **Conditional** | Some rights or provenance conditions are understood, but source-specific review or restrictions remain | Isolated experiment only after the listed conditions are met; not a default release corpus |
| **Not approved** | Rights, provenance, access terms, or privacy handling are not sufficiently established | Must not enter reference training runs or distributed snapshots |

A status applies to a particular dataset version and use case. It does not transfer automatically to later releases or to a different intended use.

## 4. Corpus manifest

Every training or evaluation corpus must have a versioned manifest. At minimum, record:

- Corpus ID and manifest version.
- Dataset publisher, name, version, and canonical source URL.
- Source file identifiers, snapshot/revision identifiers, and retrieval date.
- Dataset/package license and underlying content license as separate fields.
- Rights-review status, reviewer, decision date, and allowed purposes.
- Attribution and share-alike requirements.
- Source-document IDs or URLs where redistribution and privacy policy permit.
- Language/script labels and language-identification method/version.
- Exact and near-duplicate removal method/version.
- Filtering and normalization pipeline revision.
- Split assignment and source-group boundaries.
- Number of records, UTF-8 byte count, and per-language distribution.
- SHA-256 hashes for source shards and canonical processed outputs.
- Exclusion lists, takedown records, and records removed from the next run.

If a source does not provide a stable record ID, generate a project-local ID and retain the source locator and content hash where permitted.

## 5. Candidate corpus review

The following table records an initial research assessment. It does not grant permission and is not a legal opinion.

| Dataset | Relevant properties | Rights/provenance observation | Initial status |
| --- | --- | --- | --- |
| Wikimedia text dumps | Broad language and domain coverage; includes Indonesian Wikipedia | Wikimedia states that most original text is under GFDL and CC BY-SA 4.0, with exceptions and some content under other terms. Preserve attribution and revision/source metadata; review applicable terms and exceptions. | Conditional |
| mOSCAR / OSCAR | Large web-crawled multilingual text; many languages | OSCAR licenses its packaging under CC BY 4.0 but explicitly does not own the extracted text. Underlying pages remain subject to source rights and Common Crawl terms. | Not approved as a blanket corpus |
| mC4 / C4 | Large Common Crawl-derived corpus with multiple language subsets | The ODC-BY statement covers database rights; mC4 documentation also points to Common Crawl terms and underlying content rights. Record source-level rights separately. | Not approved as a blanket corpus |
| CulturaX | Cleaned multilingual combination of mC4 and OSCAR; includes Indonesian and many other languages | Its card says its license follows mC4 and OSCAR and warns that personal and sensitive information may remain. Cleaning and deduplication do not resolve source rights or privacy. | Not approved as a blanket corpus |
| FineWeb | Large cleaned English web corpus | Dataset card declares ODC-BY 1.0 for the database; content is derived from Common Crawl and source publishers' rights remain relevant. It is English-focused, not a multilingual baseline. | Not approved as a blanket corpus |
| Indo4B | Large Indonesian pretraining corpus with formal and colloquial sources | Public description identifies multiple source families and says Twitter content was excluded due to developer-policy restrictions. The reviewed dataset card did not establish a complete source-by-source content license. Review every source before use. | Conditional; rights audit required |
| NusaX | Parallel data for Indonesian, English, and ten Indonesian local languages | Repository declares CC-BY-SA for the dataset and Apache-2.0 for code. Useful for controlled evaluation and language coverage checks; its task-oriented size/domain is not a general pretraining corpus. | Conditional; comply with CC-BY-SA and check intended use |
| FLORES+ | High-quality multilingual translation benchmark; 230 language varieties in current card | Declared CC BY-SA 4.0 and access-gated. It is explicitly an evaluation benchmark. Keep dev/devtest out of tokenizer training to protect evaluation integrity. | Evaluation only; not training |
| Leipzig Indonesian Wikipedia corpus | Indonesian Wikipedia-derived corpus; 2021 version lists about 2.23M sentences and 35.8M tokens | It is a useful size/coverage reference but is derived from Wikipedia text. Confirm the exact corpus terms, source metadata, and Wikimedia content obligations before use. | Conditional |
| Source-cleared project or partner corpus | Can be curated with explicit documented permission and record-level provenance | Best initial path when contributors grant documented rights for tokenizer training and the intended snapshot distribution. | Preferred, after written clearance |

### 5.1 Web-crawl datasets

Common Crawl provides research access to web archives, but its terms state that crawled content can be subject to separate terms from source website owners. Common Crawl also places responsibility for evaluating content rights and risks on users.

Therefore:
- A Common Crawl-derived dataset is not automatically a corpus of openly licensed text.
- A package/database license must not be treated as an individual text license.
- Filtering, language identification, or deduplication does not create source permissions.
- The project must preserve source locators, source dates, and removal/takedown paths where available.
- Do not use a web-crawl corpus as the default reference training source until a documented rights decision covers the intended use.

### 5.2 Wikipedia-derived data

Wikimedia describes most original text as available under GFDL and CC BY-SA 4.0, while also documenting exceptions and other licenses. A Wikipedia-derived corpus should retain revision-level attribution metadata and the applicable license notices.

Do not assume that a dataset card's simplified license field resolves all record-level content or downstream distribution questions.

### 5.3 Indonesian and regional language coverage

Indo4B and NusaX are valuable research leads, but they serve different roles:

- Indo4B is a large Indonesian pretraining corpus; its component sources and rights require a source-by-source audit.
- NusaX provides curated parallel material for Indonesian and regional languages, but is small and task-oriented. It can be useful for evaluation and coverage diagnostics, not as the sole general tokenizer-training corpus.
- FLORES+ provides multilingual parallel evaluation material and must remain held out from training.
- Indonesian Wikipedia-derived corpora can provide topical breadth, subject to revision-level attribution and rights review.

## 6. Privacy and sensitive information controls

Web-derived and user-contributed text can contain personal, sensitive, confidential, or otherwise restricted information.

The ingestion pipeline must:

- Reject credentials, secrets, access tokens, private keys, and obvious authentication material.
- Detect and remove personal or sensitive information according to a documented policy and applicable law.
- Exclude content whose source terms prohibit the intended processing.
- Avoid retaining raw personal data beyond the justified processing purpose.
- Keep user-submitted personal text out of training by default.
- Require explicit, informed opt-in before personal user content can be included in a training corpus.
- Maintain a removal/takedown workflow and rebuild future snapshots when required.
- Record the filters and versions applied, without retaining unnecessary sensitive source text in logs.

Automated PII filters are imperfect. They are one control, not proof that a corpus is free of personal data.

## 7. Splitting, deduplication, and leakage prevention

- Split by document, source, or source group before any sentence-level shuffling.
- Never randomly split individual sentences from the same document into training and held-out sets.
- Deduplicate exact records using a canonical-content hash.
- Apply near-duplicate detection across all splits; record thresholds and algorithm versions.
- Keep test benchmarks and their public answers out of tokenizer training when they are reserved for evaluation.
- Preserve separate manifests for tokenizer training, intrinsic evaluation, and downstream model evaluation.
- Record overlap checks against public evaluation data when feasible.

The tokenizer's held-out evaluation set must be selected before the vocabulary sweep and remain fixed across candidate algorithms.

## 8. Recommended first corpus strategy

Do not begin with the largest public web corpus.

1. Build a small, source-cleared multilingual pilot corpus with explicit permissions and traceable provenance.
2. Ensure it includes Indonesian and English plus selected Arabic, Chinese, Japanese, and script-stress test material.
3. Use FLORES+ only as held-out parallel evaluation data, subject to its access and license conditions.
4. Use NusaX as an additional Indonesian/regional-language diagnostic only after its CC-BY-SA obligations are understood.
5. Treat Indo4B, CulturaX, mC4, FineWeb, and OSCAR as candidates requiring further rights review, not pre-approved sources.
6. Do not bundle raw corpora in the engine or ordinary Maven artifacts.

A reference vocabulary snapshot must have its own provenance and rights review. The project's Apache-2.0 software license must not be presented as a license for training data or as a blanket statement about the legal status of a trained vocabulary.

## 9. Release gate for a reference vocabulary

A reference vocabulary may be published only when:

- Its exact source manifest is available.
- Every source is approved for the intended training and distribution use.
- Required attributions and notices are documented.
- Privacy and sensitive-data review is complete.
- The trainer version and configuration are recorded.
- The vocabulary snapshot is reproducible or any nondeterministic step is fully recorded and justified.
- The snapshot passes format, fingerprint, deterministic encoding, and multilingual conformance tests.
- The release notes identify the languages, limitations, and known coverage gaps.

## References

- [Common Crawl Terms of Use](https://commoncrawl.org/terms-of-use)
- [OSCAR / mOSCAR licensing](https://oscar-project.github.io/documentation/versions/mOSCAR/)
- [CulturaX dataset card](https://huggingface.co/datasets/uonlp/CulturaX)
- [mC4 dataset license file](https://github.com/allenai/mmc4/blob/main/DATA_LICENSE.md)
- [FineWeb dataset card](https://huggingface.co/datasets/HuggingFaceFW/fineweb)
- [Wikimedia dump licensing information](https://dumps.wikimedia.org/legal.html)
- [NusaX dataset repository](https://github.com/IndoNLP/nusax)
- [IndoNLU / Indo4B repository](https://github.com/IndoNLP/indonlu)
- [FLORES+ dataset card](https://huggingface.co/datasets/openlanguagedata/flores_plus)
- [Leipzig Indonesian Wikipedia corpus](https://corpora.uni-leipzig.de/en?corpusId=ind_wikipedia_2021)
- [Open Data Commons Attribution License 1.0](https://opendatacommons.org/licenses/by/1-0/)
