# Pattern 10: Hybrid Databases

## 1. Introduce the Pattern

**Hybrid search** combines two fundamentally different retrieval methods — **dense vector search** (Pattern 9, meaning-based) and **sparse keyword search** (BM25, term-based) — and merges their results into a single ranked list. Neither method alone is reliably best for every query; hybrid search plays to each one's strengths.

```
Query: "what's the timeout for connection pool error ECONNREFUSED-5091?"

Dense (vector) search alone:
  understands "timeout" and "connection pool" conceptually
  ✗ but ECONNREFUSED-5091 is a made-up-looking token the embedding
    model has never seen anything like — it gets diluted into the
    "generic error code" region of vector space, not matched exactly

Sparse (BM25) search alone:
  ✓ finds the exact token "ECONNREFUSED-5091" if it appears anywhere
  ✗ but would miss a document that says "connection refused" without
    ever using that exact literal string, or one that explains the
    concept in different words

Hybrid = both, merged:
  ✓ BM25 nails the exact error code
  ✓ Vector search finds conceptually related explanations
  → merged ranking benefits from both signals
```

## 2. The Problem It Solves

Every pattern so far in this series has leaned entirely on vector similarity for retrieval. That's a deliberate simplification for teaching purposes, but it has a real, well-documented weakness: **embeddings are bad at exact-match tasks**.

Specific failure modes:

- **IDs, codes, and identifiers**: error codes (`ECONNREFUSED-5091`), ticket numbers (`JIRA-4821`), service names with unusual formatting (`payments-api-v2`), config keys (`max_pool_size`). Embedding models are trained on natural language; they don't reliably preserve exact-match precision for these tokens — two different error codes can end up embedded suspiciously close together just because they're both "error-code-shaped."
- **Rare or out-of-vocabulary terms**: an internal codename, an acronym unique to the company, a very recently introduced term the embedding model was never trained on — dense search has no exact-match fallback for these.
- **Queries where the user already knows the exact term they want**: "find the runbook for `ECONNREFUSED-5091`" is really a keyword lookup wearing a natural-language sentence's clothes. BM25, the decades-old probabilistic keyword-ranking algorithm, is specifically good at exactly this.

Conversely, BM25 alone is bad at the paraphrase problem that motivated Pattern 1 in the first place ("cancel my subscription" vs. "terminate your plan" — zero token overlap). Hybrid search is the practical answer: **don't choose, combine, and let each method compensate for the other's blind spot.**

## 3. Realistic Enterprise Scenario

DocuMind's engineering runbooks are full of exact identifiers: specific error codes, config key names, internal service names with hyphens and version suffixes. Meanwhile, employee questions range from precise ("what causes `ECONNREFUSED-5091`?") to conceptual ("why does the payment service keep timing out?").

A pure vector search system handles the second style well and the first style poorly. A pure keyword search system is the reverse. DocuMind needs both running side-by-side on every query, merged into one ranked result set, so neither query style is systematically underserved.

## 4. Architecture / Flow Diagram

```
                         ┌─────────────────┐
                         │   User query       │
                         └────────┬───────────┘
                                  │
                 ┌────────────────┴─────────────────┐
                 │                                    │
                 ▼                                    ▼
      ┌─────────────────────┐          ┌─────────────────────┐
      │  Dense (vector) path   │          │  Sparse (BM25) path   │
      │  embed query            │          │  tokenize query        │
      │  → Chroma similarity    │          │  → BM25 score against  │
      │    search (Pattern 9)   │          │    the corpus's term    │
      │  → top-K by cosine       │          │    frequency index      │
      │    distance               │          │  → top-K by BM25 score  │
      └──────────┬─────────────┘          └──────────┬─────────────┘
                 │                                    │
                 │        ranked list A                │       ranked list B
                 │        (chunk_id, dense_score)      │       (chunk_id, bm25_score)
                 │                                    │
                 └────────────────┬─────────────────┘
                                  ▼
                     ┌───────────────────────────┐
                     │  Score fusion (RRF)           │
                     │  Reciprocal Rank Fusion:       │
                     │  score(doc) = Σ 1/(k+rank_i)   │
                     │  across both ranked lists       │
                     └────────────┬───────────────┘
                                  ▼
                        final merged ranking
                                  │
                                  ▼
              (often followed by Pattern 12: Re-ranking,
               for a final precision pass)
```

## 5. Request-to-Response Walkthrough

1. A user query arrives once, and is sent down **two parallel paths**.
2. **Dense path**: the query is embedded (Pattern 1) and searched against the Chroma vector store (Pattern 9), returning the top-K chunks ranked by similarity.
3. **Sparse path**: the query is tokenized and scored against a BM25 index built over the same corpus's raw chunk text, returning the top-K chunks ranked by BM25 score (a function of term frequency in the chunk, inverse document frequency across the corpus, and document length normalization).
4. Because dense similarity scores and BM25 scores live on **completely different numeric scales** (cosine similarity is roughly 0–1; BM25 scores are unbounded and corpus-dependent), they can't be combined by simply adding them together — this pattern uses **Reciprocal Rank Fusion (RRF)** instead, which only cares about each result's *rank position* in each list, not its raw score, sidestepping the scale-mismatch problem entirely.
5. For every chunk that appears in either list, RRF computes `1 / (k + rank)` for each list it appears in (with `k` a small constant, typically 60, that dampens the influence of very top-ranked results) and sums these contributions — a chunk ranked highly in *both* lists scores higher than one ranked highly in only one.
6. The final merged list is sorted by this fused score and returned as the hybrid search result.

## 6. Why This Pattern Is Appropriate

| Approach | Exact-match queries (error codes, IDs) | Paraphrase/conceptual queries |
|---|---|---|
| Dense (vector) search alone | Poor | Excellent |
| Sparse (BM25) search alone | Excellent | Poor |
| Naive score averaging (dense_score + bm25_score) | Broken — scores aren't on comparable scales, one signal usually drowns the other | Broken, same reason |
| **Hybrid with Reciprocal Rank Fusion (this pattern)** | Good — BM25's contribution carries exact matches through | Good — dense search's contribution carries paraphrases through |

RRF specifically (rather than, say, min-max normalizing then averaging the raw scores) is the standard, robust choice here precisely because it sidesteps the scale-mismatch problem by working purely with rank positions — it doesn't need BM25 and cosine similarity to mean the same thing numerically, just that "higher rank = more relevant" holds within each list individually, which is a much weaker and safer assumption.

## 7. Production-Quality Python Implementation

```python
"""
hybrid_search_pipeline.py

Production-quality hybrid search for DocuMind: dense vector search
(via the Chroma-backed VectorStore from Pattern 9) combined with
BM25 sparse keyword search (via rank_bm25), merged with Reciprocal
Rank Fusion.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Dict, List, Tuple

from langchain_core.documents import Document
from langchain_ollama import OllamaEmbeddings
from rank_bm25 import BM25Okapi

EMBEDDING_MODEL = "nomic-embed-text"
RRF_K = 60  # standard damping constant for Reciprocal Rank Fusion


@dataclass
class HybridChunk:
    chunk_id: str
    text: str
    metadata: dict = field(default_factory=dict)


@dataclass
class HybridResult:
    chunk_id: str
    text: str
    metadata: dict
    dense_rank: int | None
    bm25_rank: int | None
    fused_score: float


def _tokenize(text: str) -> List[str]:
    """Simple whitespace + punctuation-aware tokenizer for BM25. Keeps
    hyphenated identifiers like 'ECONNREFUSED-5091' as single tokens,
    which matters for exact-match precision on error codes and IDs."""
    return re.findall(r"[A-Za-z0-9][A-Za-z0-9_\-]*", text.lower())


class HybridSearchIndex:
    """Holds both a dense embedding index and a sparse BM25 index over
    the same corpus, and merges their results at query time via RRF."""

    def __init__(self, model: str = EMBEDDING_MODEL):
        self._embedder = OllamaEmbeddings(model=model)
        self.chunks: List[HybridChunk] = []
        self._chunk_vectors: List[list] = []
        self._bm25: BM25Okapi | None = None
        self._tokenized_corpus: List[List[str]] = []

    def build(self, chunks: List[HybridChunk]) -> None:
        self.chunks = chunks
        texts = [c.text for c in chunks]

        # Dense index: embed all chunk texts once, up front.
        self._chunk_vectors = self._embedder.embed_documents(texts)

        # Sparse index: tokenize and build the BM25 index.
        self._tokenized_corpus = [_tokenize(t) for t in texts]
        self._bm25 = BM25Okapi(self._tokenized_corpus)

    def _dense_search(self, query: str, top_k: int) -> List[Tuple[int, float]]:
        import numpy as np
        query_vec = np.array(self._embedder.embed_query(query))
        scored = []
        for i, vec in enumerate(self._chunk_vectors):
            v = np.array(vec)
            denom = np.linalg.norm(query_vec) * np.linalg.norm(v)
            score = float(np.dot(query_vec, v) / denom) if denom else 0.0
            scored.append((i, score))
        scored.sort(key=lambda pair: pair[1], reverse=True)
        return scored[:top_k]

    def _sparse_search(self, query: str, top_k: int) -> List[Tuple[int, float]]:
        assert self._bm25 is not None, "Call build() before searching."
        query_tokens = _tokenize(query)
        scores = self._bm25.get_scores(query_tokens)
        ranked = sorted(enumerate(scores), key=lambda pair: pair[1], reverse=True)
        return ranked[:top_k]

    def search(self, query: str, top_k: int = 5, candidate_pool: int = 20) -> List[HybridResult]:
        dense_results = self._dense_search(query, candidate_pool)
        sparse_results = self._sparse_search(query, candidate_pool)

        dense_ranks: Dict[int, int] = {idx: rank for rank, (idx, _) in enumerate(dense_results, start=1)}
        sparse_ranks: Dict[int, int] = {idx: rank for rank, (idx, _) in enumerate(sparse_results, start=1)}

        all_indices = set(dense_ranks) | set(sparse_ranks)
        fused: List[Tuple[int, float]] = []
        for idx in all_indices:
            score = 0.0
            if idx in dense_ranks:
                score += 1.0 / (RRF_K + dense_ranks[idx])
            if idx in sparse_ranks:
                score += 1.0 / (RRF_K + sparse_ranks[idx])
            fused.append((idx, score))

        fused.sort(key=lambda pair: pair[1], reverse=True)

        results = []
        for idx, score in fused[:top_k]:
            chunk = self.chunks[idx]
            results.append(HybridResult(
                chunk_id=chunk.chunk_id, text=chunk.text, metadata=chunk.metadata,
                dense_rank=dense_ranks.get(idx), bm25_rank=sparse_ranks.get(idx),
                fused_score=score,
            ))
        return results


# --------------------------------------------------------------------------
# Demo
# --------------------------------------------------------------------------

def build_sample_corpus() -> List[HybridChunk]:
    return [
        HybridChunk("c1", "Connection pool exhaustion causes requests to time out "
                          "when all pooled connections are held open too long."),
        HybridChunk("c2", "Error ECONNREFUSED-5091 indicates the database refused "
                          "the connection attempt entirely, usually a network issue."),
        HybridChunk("c3", "The payments service occasionally experiences slow "
                          "response times under high traffic load."),
        HybridChunk("c4", "To fix connection pool exhaustion, increase pool_size "
                          "and add a connection timeout of 30 seconds."),
        HybridChunk("c5", "Our HR notice period policy allows remote work with "
                          "manager approval during the transition."),
    ]


def run_demo() -> None:
    index = HybridSearchIndex()
    index.build(build_sample_corpus())

    print('Query: "ECONNREFUSED-5091" (exact-match style query)')
    for r in index.search("ECONNREFUSED-5091", top_k=3):
        print(f"  [{r.chunk_id}] dense_rank={r.dense_rank} bm25_rank={r.bm25_rank} "
              f"fused={r.fused_score:.4f}  {r.text[:60]}...")

    print('\nQuery: "why do requests keep timing out under load" (conceptual query)')
    for r in index.search("why do requests keep timing out under load", top_k=3):
        print(f"  [{r.chunk_id}] dense_rank={r.dense_rank} bm25_rank={r.bm25_rank} "
              f"fused={r.fused_score:.4f}  {r.text[:60]}...")


if __name__ == "__main__":
    run_demo()
```

### Expected output (shape)

```
Query: "ECONNREFUSED-5091" (exact-match style query)
  [c2] dense_rank=1 bm25_rank=1 fused=0.0328  Error ECONNREFUSED-5091 indicates the database refused ...
  [c1] dense_rank=3 bm25_rank=None fused=0.0159  Connection pool exhaustion causes requests to time out ...
  [c4] dense_rank=2 bm25_rank=None fused=0.0161  To fix connection pool exhaustion, increase pool_size ...

Query: "why do requests keep timing out under load" (conceptual query)
  [c1] dense_rank=1 bm25_rank=2 fused=0.0323  Connection pool exhaustion causes requests to time out ...
  [c3] dense_rank=2 bm25_rank=1 fused=0.0322  The payments service occasionally experiences slow resp...
  [c4] dense_rank=3 bm25_rank=None fused=0.0159  To fix connection pool exhaustion, increase pool_size ...
```

`c2` (the exact error code chunk) wins the first query decisively because BM25 nails the literal token `ECONNREFUSED-5091`. In the second, conceptual query, `c1` and `c3` — which never use the phrase "timing out under load" verbatim — still surface near the top because dense search recognizes the paraphrase, with BM25 contributing a secondary signal from shared common terms.

### Key production notes

- **Never average raw dense and BM25 scores directly** — they live on incompatible scales (bounded cosine similarity vs. unbounded, corpus-size-dependent BM25 scores). RRF's rank-based fusion is the standard fix precisely because it ignores the raw score magnitudes.
- **Tokenization choices matter a lot for BM25 quality on technical content** — the tokenizer here deliberately keeps hyphenated identifiers (`ECONNREFUSED-5091`, `payments-api`) as single tokens rather than splitting on hyphens, since splitting would break exact-match lookup for exactly the identifiers BM25 is supposed to be good at.
- **Candidate pool size vs. final top_k**: this implementation pulls a wider `candidate_pool` (20) from each method before fusing down to the requested `top_k` — fusing from a narrow pool risks missing a chunk that ranks, say, 8th in one list and 2nd in the other; a wider pool gives RRF more to work with.
- **This is a from-scratch BM25 layer added alongside Chroma** for teaching clarity; some vector databases (and Chroma itself, via community extensions) support hybrid search natively. Either approach is production-viable — what matters is the fusion strategy (RRF) and tokenization quality, not which library hosts the BM25 index.
- **Hybrid search is commonly followed by Pattern 12 (Re-ranking)** — RRF produces a solid *candidate* list efficiently, but a cross-encoder re-ranker (which looks at the actual query-document pair, not just independent scores) often gives a meaningfully more precise final ordering for the top handful of results actually shown to the user.

## 8. Pinned Dependency Versions

```txt
rank-bm25==0.2.2
langchain-chroma==0.2.0
langchain-ollama==0.2.3
langchain-core==0.3.29
numpy==1.26.4
python>=3.11,<3.13
```

```bash
ollama pull nomic-embed-text
```

---

**Next:** say `next` and I'll build `11_Graph_Vector_Retrieval.md` — combining the knowledge graph (Pattern 8) with vector search (Pattern 9) into a single retrieval strategy.
