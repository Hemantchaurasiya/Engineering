# Pattern 12: Re-ranking

## 1. Introduce the Pattern

**Re-ranking** is a second-pass scoring step that takes the top-N candidates from an initial retrieval method (dense search, hybrid search, whatever produced them) and re-scores them with a slower but more accurate model — specifically a **cross-encoder** — before returning the final, smaller top-K to the user or the LLM.

```
Stage 1: Initial retrieval (fast, approximate)         Stage 2: Re-ranking (slow, precise)
─────────────────────────────────────────             ─────────────────────────────────
Vector/hybrid search over the WHOLE corpus              Cross-encoder scores each of the
(millions of chunks) → top 20 candidates                20 candidates AGAINST the actual
                                                          query text, pair by pair
Bi-encoder: embeds query and chunk                       Cross-encoder: feeds [query, chunk]
SEPARATELY, compares vectors                             TOGETHER into one model, letting it
→ fast (one query embedding, compare                     directly attend across both texts
  against pre-computed chunk vectors)                    → slow (must run inference once
→ approximate (never actually looks                       PER candidate pair — can't
  at query and chunk together)                             pre-compute anything)
                                                          → far more accurate at judging
                                                            true relevance
```

The two-stage pattern — cheap-and-broad, then expensive-and-narrow — is a standard shape in information retrieval generally: use a fast method to cut millions of candidates down to a few dozen, then spend more compute per-item only on that much smaller set.

## 2. The Problem It Solves

Dense vector search (Pattern 9) uses a **bi-encoder**: the query and the document are embedded *independently*, and similarity is computed afterward via cosine distance. This independence is exactly what makes vector search fast enough to run over millions of chunks (document vectors can all be pre-computed once, at ingestion time) — but it's also a real accuracy limitation.

A bi-encoder can never let the query and the document text directly interact — it can't notice, for example, that a chunk mentions a concept relevant to the query but in a dismissive or negating way ("this is NOT the cause of the outage"), because the embedding of that sentence never gets a chance to be compared *token-by-token* against the query.

Specific problems this causes:

- **"Close but wrong" results**: a chunk about "database connection timeouts in general" might embed very close to a query about "the March payments outage's specific timeout cause," without actually answering the specific question.
- **Negation and nuance get lost**: bi-encoder embeddings tend to capture topic, not fine-grained logical relationships like negation, causality direction, or conditionals — "X causes Y" and "X does not cause Y" can embed suspiciously close together, since both mention "X" and "Y" prominently.
- **Fusion-based methods (Pattern 10) still rank by independent per-method scores** — RRF combines dense and BM25 rankings, but neither underlying method ever *directly* compares query and document text together either.

Re-ranking solves this specifically by trading speed for accuracy on a small candidate set: a cross-encoder that processes `[query, chunk]` as one combined input can directly model interactions between them, catching precision errors bi-encoder-only retrieval misses — but only because, by this stage, there are just 10–30 candidates left to score, not millions.

## 3. Realistic Enterprise Scenario

DocuMind's hybrid search (Pattern 10) returns 20 candidate chunks for the query *"what caused the March payments outage?"* Among them:

- A postmortem chunk that precisely explains the root cause (connection pool exhaustion)
- A general "connection pooling best practices" glossary entry that's topically similar but doesn't answer "what caused *this* outage"
- A chunk from an unrelated February outage postmortem that also mentions "connection pool," ranking deceptively high on pure similarity because of shared vocabulary

Initial retrieval alone can't reliably distinguish these three — they're all plausible-looking matches by embedding similarity. Re-ranking, by directly comparing the query against each candidate's actual text, correctly promotes the precise March postmortem chunk to the top and demotes the vocabulary-overlap false positives.

## 4. Architecture / Flow Diagram

```
┌─────────────────────────┐
│      User query            │
└─────────────┬───────────┘
              ▼
   ┌────────────────────────────┐
   │  Stage 1: Initial retrieval    │  ← Pattern 9 (vector) or
   │  (fast, broad, bi-encoder      │     Pattern 10 (hybrid)
   │   or hybrid fusion)             │
   │  → top 20 candidates            │
   └─────────────┬───────────────┘
                 ▼
   ┌────────────────────────────────────┐
   │  Stage 2: Cross-encoder re-ranking     │
   │                                          │
   │  for each of the 20 candidates:          │
   │    score = cross_encoder([query, chunk]) │
   │                                          │
   │  cross-encoder sees query AND chunk        │
   │  TOGETHER — can model direct interactions,│
   │  negation, specificity                    │
   └─────────────┬───────────────────────┘
                 ▼
      re-sort by cross-encoder score
                 ▼
      return top 3-5 (final, precise result)
                 ▼
      → handed to an LLM as grounding context,
        or shown directly to the user
```

## 5. Request-to-Response Walkthrough

1. Initial retrieval (Pattern 9 or Pattern 10) runs as usual and returns a **wider-than-needed** candidate set — typically 15–30 chunks, more than the final number the user actually wants, since re-ranking's whole value is picking the truly best few out of a generous pool.
2. For each candidate, the pipeline constructs a `(query, chunk_text)` pair.
3. A **cross-encoder model** (here, a `sentence-transformers` cross-encoder run locally) scores each pair — this requires one model inference call per candidate, since (unlike bi-encoder embeddings) nothing about this score can be pre-computed at ingestion time; it depends on the specific query.
4. Candidates are re-sorted by this new cross-encoder score, which is typically a much more reliable relevance signal than the original retrieval score.
5. Only the **top few** (e.g. top 3–5) re-ranked candidates are kept — this is the final, precise result set.
6. This smaller, higher-precision set is what actually gets shown to the user or passed as grounding context to an LLM — spending the re-ranking compute budget only on the shortlist, never on the full corpus.

## 6. Why This Pattern Is Appropriate

| Approach | Speed at corpus scale | Precision of final top-K |
|---|---|---|
| Bi-encoder retrieval only (Pattern 9), return top-K directly | Fast | Moderate — "close but wrong" results common |
| Hybrid fusion only (Pattern 10), return top-K directly | Fast | Better than bi-encoder alone, still no direct query-document interaction modeling |
| Cross-encoder scoring over the **entire corpus** for every query | Far too slow — one inference call per document, per query, doesn't scale past a tiny corpus | Best possible, if you could afford it |
| **This pattern**: fast retrieval for a wide candidate pool, cross-encoder re-ranking only on that pool | Fast enough — cross-encoder cost is bounded by candidate pool size (e.g. 20), not corpus size | Best practically achievable — full corpus recall from stage 1, cross-encoder precision from stage 2 |

This two-stage design is why re-ranking is considered close to a "free precision upgrade" in production RAG systems: the expensive step is bounded to a small, fixed candidate count regardless of how large the underlying corpus grows.

## 7. Production-Quality Python Implementation

```python
"""
reranking_pipeline.py

Production-quality re-ranking layer for DocuMind: takes a wide
candidate pool from initial retrieval (Pattern 9/10) and re-scores it
with a local cross-encoder (sentence-transformers), returning a
smaller, higher-precision final result set.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import List, Optional

from sentence_transformers import CrossEncoder

CROSS_ENCODER_MODEL = "cross-encoder/ms-marco-MiniLM-L-6-v2"


@dataclass
class RetrievedCandidate:
    chunk_id: str
    text: str
    metadata: dict = field(default_factory=dict)
    initial_score: Optional[float] = None
    initial_rank: Optional[int] = None


@dataclass
class RerankedResult:
    chunk_id: str
    text: str
    metadata: dict
    initial_rank: Optional[int]
    rerank_score: float
    rerank_rank: int


class Reranker:
    """Wraps a local cross-encoder model for query-document pair scoring.
    Loaded once and reused across queries — cross-encoder models are
    small enough (~80MB for MiniLM-based ones) to keep resident."""

    def __init__(self, model_name: str = CROSS_ENCODER_MODEL):
        self._model = CrossEncoder(model_name)

    def rerank(
        self,
        query: str,
        candidates: List[RetrievedCandidate],
        top_k: int = 5,
    ) -> List[RerankedResult]:
        if not candidates:
            return []

        pairs = [(query, c.text) for c in candidates]
        scores = self._model.predict(pairs)  # one inference batch, bounded by len(candidates)

        scored = list(zip(candidates, scores))
        scored.sort(key=lambda pair: pair[1], reverse=True)

        results = []
        for rank, (candidate, score) in enumerate(scored[:top_k], start=1):
            results.append(RerankedResult(
                chunk_id=candidate.chunk_id,
                text=candidate.text,
                metadata=candidate.metadata,
                initial_rank=candidate.initial_rank,
                rerank_score=float(score),
                rerank_rank=rank,
            ))
        return results

    @staticmethod
    def rank_movement_report(results: List[RerankedResult]) -> str:
        """Debugging aid: shows how much re-ranking changed the ordering
        vs. the initial retrieval rank — useful for judging whether
        re-ranking is earning its extra latency on real query traffic."""
        lines = []
        for r in results:
            if r.initial_rank is None:
                movement = "new"
            else:
                delta = r.initial_rank - r.rerank_rank
                movement = f"+{delta}" if delta > 0 else str(delta)
            lines.append(f"  rerank #{r.rerank_rank} (was #{r.initial_rank}, moved {movement}): "
                         f"{r.text[:60]}...")
        return "\n".join(lines)


# --------------------------------------------------------------------------
# Demo
# --------------------------------------------------------------------------

def build_sample_candidates() -> List[RetrievedCandidate]:
    # Simulates the output of Pattern 9/10's initial retrieval: a wider
    # pool of 5 candidates, ranked by embedding similarity alone, with
    # some "close but wrong" results mixed in among the truly relevant ones.
    return [
        RetrievedCandidate("c1",
            "Connection pooling best practices: always set a timeout to avoid "
            "holding connections open indefinitely under sustained load.",
            initial_rank=1, initial_score=0.81),
        RetrievedCandidate("c2",
            "The February 2026 checkout outage was caused by a Redis cache "
            "eviction bug unrelated to connection pooling.",
            initial_rank=2, initial_score=0.79),
        RetrievedCandidate("c3",
            "The March 2026 payments outage was caused by connection pool "
            "exhaustion after a deploy removed the connection timeout setting, "
            "leaving connections open indefinitely under load.",
            initial_rank=3, initial_score=0.77),
        RetrievedCandidate("c4",
            "Our on-call rotation schedule is published every Friday for the "
            "following week's coverage.",
            initial_rank=4, initial_score=0.55),
        RetrievedCandidate("c5",
            "Database connection pools should be sized based on expected "
            "concurrent request volume and average query duration.",
            initial_rank=5, initial_score=0.52),
    ]


def run_demo() -> None:
    query = "what caused the March 2026 payments outage?"
    candidates = build_sample_candidates()

    reranker = Reranker()
    results = reranker.rerank(query, candidates, top_k=3)

    print(f"Query: {query}\n")
    print("Re-ranked results:")
    print(reranker.rank_movement_report(results))


if __name__ == "__main__":
    run_demo()
```

### Expected output (shape — exact scores vary by model version)

```
Query: what caused the March 2026 payments outage?

Re-ranked results:
  rerank #1 (was #3, moved +2): The March 2026 payments outage was caused by connection pool ...
  rerank #2 (was #1, moved -1): Connection pooling best practices: always set a timeout to av...
  rerank #3 (was #2, moved -1): The February 2026 checkout outage was caused by a Redis cache ...
```

The chunk that actually answers the specific question (`c3`, initially ranked 3rd by embedding similarity alone) correctly jumps to the top after re-ranking, because the cross-encoder can directly recognize it specifically discusses "the March 2026 payments outage" — the exact entity and event in the query — rather than just sharing vocabulary about connection pooling in general.

### Key production notes

- **Re-ranking cost scales with candidate pool size, not corpus size** — this is the entire reason the two-stage design works. Keep the initial retrieval's candidate pool wide enough to likely contain the true best answer (typically 15–30) but not so wide that cross-encoder inference becomes a latency problem (each candidate costs one model forward pass).
- **Cross-encoder models are small and fast enough to run locally** — `ms-marco-MiniLM-L-6-v2` is roughly 80MB and runs efficiently on CPU for pool sizes in this range, keeping this pattern's cost fully local, consistent with this series' Ollama-first, no-cloud-API tech stack (the cross-encoder itself is a separate small local model, not routed through Ollama, since Ollama's chat/embedding APIs don't expose cross-encoder-style pair scoring).
- **The rank-movement report is a genuinely useful production debugging tool**, not just a demo nicety — tracking how often and how far re-ranking changes the top result across real query traffic tells you whether the re-ranking stage is earning its added latency, or whether initial retrieval was already good enough for your corpus.
- **Re-ranking is a precision tool, not a recall tool** — it can only re-order candidates that made it into the initial pool; if the true best answer wasn't retrieved at all in stage 1 (a recall failure), no amount of re-ranking can recover it. This is why hybrid search (Pattern 10) feeding into re-ranking is a stronger combination than either alone: hybrid search maximizes the chance the right chunk is *somewhere* in the candidate pool, and re-ranking maximizes the chance it ends up at the *top* of that pool.

## 8. Pinned Dependency Versions

```txt
sentence-transformers==3.3.1
torch==2.5.1
python>=3.11,<3.13
```

---

**Next:** say `next` and I'll build `13_Retrieval_Filters.md` — designing the metadata filter layer (access control, recency, scoping) that sits alongside similarity search and re-ranking.
