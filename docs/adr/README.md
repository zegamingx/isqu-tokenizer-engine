# Architecture Decision Records

Architecture Decision Records (ADRs) capture decisions that affect ISQU Tokenizer Engine's module boundaries, runtime behavior, data formats, compatibility, and release policy.

An ADR begins as a proposal. It becomes accepted only after its assumptions, alternatives, compatibility impact, and required validation have been reviewed. A proposed ADR must not be treated as an implemented or stable contract.

## Decision log

| ADR | Topic | Status |
| --- | --- | --- |
| [0001](0001-vocabulary-sizing-and-evaluation.md) | Vocabulary sizing strategy and evaluation protocol | Proposed; not frozen |

## Status meanings

- **Proposed:** a researched direction under review; implementation must not treat it as final.
- **Accepted:** the decision is approved for implementation, subject to its stated validation gates.
- **Superseded:** a later ADR replaces it; the replacement must be linked.
- **Rejected:** evaluated and intentionally not adopted.

## ADR expectations

A decision that changes token IDs, normalization, pre-tokenization, serialization, deterministic behavior, public API, or compatibility guarantees must have an ADR or an explicit amendment to an existing ADR.

Each ADR should record context, decision, alternatives, consequences, open questions, and references. If implementation evidence contradicts a proposal, update the ADR before freezing the affected behavior.
