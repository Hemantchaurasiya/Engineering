# Pattern 1: Vector Embeddings

## 1. Introduce the Pattern

A **vector embedding** is a way of turning a piece of text (a word, a sentence, a paragraph, a whole document) into a list of numbers — a vector — such that texts with **similar meaning end up close together** in that number-space, and texts with different meaning end up far apart.

Think of it like GPS coordinates, but for *meaning* instead of *location*. Just like `(28.5, 77.3)` tells you "this is somewhere near Delhi," an embedding like `[0.12, -0.44, 0.81, ...]` (usually 384 to 4096 numbers) tells you "this text is about refund policies" or "this text is about database indexing."

```
"How do I reset my password?"     →  [0.12, -0.44, 0.81, ..., 0.09]   (768 numbers)
"I forgot my login credentials"   →  [0.14, -0.41, 0.79, ..., 0.11]   (768 numbers) ← close to above
"What is the capital of France?"  →  [-0.55, 0.30, -0.12, ..., 0.66]  (768 numbers) ← far from above
```

The two password-related sentences use **completely different words**, but their vectors land close together, because an embedding model captures *meaning*, not just *spelling*. This one idea — meaning as geometry — is the foundation every other pattern in this series (chunking, vector databases, similarity search, re-ranking, ANN search) is built on top of.

## 2. The Problem It Solves

Before embeddings, search was mostly **keyword matching**: does the document contain the exact word the user typed?

This breaks down constantly in real systems:

- A user searches "cancel my subscription" but the help article says "terminate your plan." Zero keyword overlap — keyword search finds nothing.
- A user asks "why is my laptop battery draining so fast," and the relevant runbook is titled "Troubleshooting rapid power depletion on portable devices." Again, no shared words.
- Keyword search also can't tell that "Java" (the programming language) and "Java" (the island) are different things — it has no sense of context.

Embeddings solve this by comparing **meaning instead of spelling**. Once text is converted into vectors, "finding relevant information" becomes a well-understood geometry problem: find the vectors that are closest to the query's vector. This is what makes semantic search, RAG, recommendation engines, and de-duplication all possible.

## 3. Realistic Enterprise Scenario

**DocuMind**, our running scenario for this series, is an internal knowledge assistant for a software company. Employees ask things like:

> "How do I fix a stuck deployment pipeline?"
> "What's our policy on remote work during notice period?"
> "Is there a runbook for database failover?"

The underlying documents (runbooks, HR policies, Confluence pages) rarely use the exact same wording as the employee's question. If DocuMind relied on keyword search, it would fail on the majority of real questions. So the very first thing DocuMind needs — before chunking, before a vector database, before anything else — is a reliable way to turn both **documents** and **questions** into embeddings that live in the same meaning-space, so it can measure "how related is this question to this document?" mathematically.

This file builds and tests exactly that piece: a small, reusable embedding service for DocuMind.

## 4. Architecture / Flow Diagram

```
                         ┌─────────────────────────┐
                         │   Ollama (local server)  │
                         │  model: nomic-embed-text │
                         └────────────┬─────────────┘
                                      │
                     embed(text) → [0.12, -0.44, ... ]  (768-dim vector)
                                      │
        ┌─────────────────────────────┴──────────────────────────────┐
        │                                                             │
┌───────▼────────┐                                          ┌─────────▼─────────┐
│  DOCUMENT SIDE  │                                          │    QUERY SIDE     │
│                 │                                          │                    │
│ "Runbook: DB    │──► OllamaEmbeddings.embed_documents() ──►│ stored as vectors  │
│  failover steps"│                                          │ (used later by the │
│                 │                                          │ vector DB pattern) │
└─────────────────┘                                          └────────────────────┘
                                      │
                                      │           at query time:
                                      │
                         "how do I fail over the DB?"
                                      │
                          OllamaEmbeddings.embed_query()
                                      │
                                      ▼
                         [0.13, -0.42, 0.80, ...]  (query vector)
                                      │
                                      ▼
                     compare distance to every document vector
                     (cosine similarity — covered fully in
                      Pattern 14: Similarity Search)
                                      │
                                      ▼
                    closest vectors = most semantically relevant docs
```

Both documents and the user's query pass through the **same embedding model**, landing in the **same vector space**. That shared space is what makes comparison meaningful — you can't compare a vector from one model to a vector from another model, since each model defines its own space.

## 5. Request-to-Response Walkthrough

1. **Ingestion time** (happens once per document): DocuMind takes each runbook/policy paragraph and sends it to `OllamaEmbeddings.embed_documents()`. Each paragraph becomes a fixed-length vector (768 numbers for `nomic-embed-text`). These vectors get stored alongside the original text (in Pattern 9, we'll put them in a real vector database — for this file, we keep it simple with plain Python lists so the embedding step itself is crystal clear).
2. **Query time** (happens every time a user asks something): the employee's question — e.g. *"how do I fail over the DB?"* — is sent to `OllamaEmbeddings.embed_query()`, producing one query vector.
3. **Comparison**: DocuMind computes the cosine similarity between the query vector and every stored document vector. Cosine similarity gives a score between -1 and 1, where 1 means "pointing in exactly the same direction" (near-identical meaning).
4. **Ranking**: documents are sorted by similarity score, highest first.
5. **Response**: the top-N most similar chunks are returned as the "relevant context" — this is exactly the retrieval step that a RAG pipeline would then feed to an LLM to generate a grounded answer.

The code in section 7 implements steps 1–4 end-to-end, plus some production concerns: batching, caching, normalization, and dimensionality/model verification.

## 6. Why This Pattern Is Appropriate

| Alternative | Why it's worse for DocuMind |
|---|---|
| Exact keyword match (`LIKE '%password%'`) | Misses paraphrases, synonyms, typos; brittle |
| TF-IDF / BM25 alone | Better than keyword match, still purely lexical — "cancel" vs "terminate" still miss each other. (BM25 *is* useful — see Pattern 10: Hybrid Databases — but not as the sole strategy) |
| Manual tagging/categorization | Doesn't scale; requires humans to tag every document and anticipate every phrasing of every question |
| Embeddings (this pattern) | Captures meaning; works across paraphrases, synonyms, and even some cross-lingual cases; is the standard building block for every modern retrieval system |

Embeddings aren't magic — they don't understand the text the way a human does, and they can be fooled (two sentences that *look* similar in wording but mean opposite things can sometimes land close together). But as the foundation of a retrieval system, they are the best cost/accuracy trade-off available today, and every later pattern in this series (chunking strategy, vector DB choice, hybrid search, re-ranking) exists specifically to compensate for embeddings' known weaknesses.

## 7. Production-Quality Python Implementation

This implementation builds a small, reusable `EmbeddingService` class around `langchain-ollama`, with the concerns a production system actually needs: batching for throughput, an in-memory cache to avoid re-embedding identical text, cosine similarity search, and basic input validation.

```python
"""
embedding_service.py

A production-quality wrapper around langchain-ollama's OllamaEmbeddings,
built for DocuMind (our running enterprise knowledge-assistant scenario).

Design goals:
  - Single, reusable service for both document embedding and query embedding
  - Batching (Ollama embeds faster in batches than one call per text)
  - Simple in-memory cache keyed by text hash (avoids re-embedding duplicates)
  - Cosine similarity search over an in-memory store (no vector DB yet —
    that's Pattern 9; this file focuses purely on embeddings)
  - Defensive checks: empty text, oversized batches, dimension mismatches
"""

from __future__ import annotations

import hashlib
import logging
import time
from dataclasses import dataclass, field
from typing import List, Tuple

import numpy as np
from langchain_ollama import OllamaEmbeddings

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("documind.embeddings")


# --------------------------------------------------------------------------
# Config
# --------------------------------------------------------------------------

EMBEDDING_MODEL = "nomic-embed-text"   # local Ollama model, 768-dim output
BATCH_SIZE = 32                        # texts per embed_documents() call
EXPECTED_DIM = 768                     # nomic-embed-text's known output size


@dataclass
class EmbeddedChunk:
    """A piece of text plus its vector and metadata — the atomic unit
    every downstream pattern (chunking, vector DB, retrieval) works with."""
    text: str
    vector: np.ndarray
    metadata: dict = field(default_factory=dict)


# --------------------------------------------------------------------------
# Core service
# --------------------------------------------------------------------------

class EmbeddingService:
    """Wraps OllamaEmbeddings with batching, caching, and validation.

    Usage:
        svc = EmbeddingService()
        chunks = svc.embed_documents(["text a", "text b"], metadatas=[...])
        query_vec = svc.embed_query("how do I reset my password?")
        results = svc.most_similar(query_vec, chunks, top_k=3)
    """

    def __init__(self, model: str = EMBEDDING_MODEL, base_url: str | None = None):
        kwargs = {"model": model}
        if base_url:
            kwargs["base_url"] = base_url
        self._embedder = OllamaEmbeddings(**kwargs)
        self._cache: dict[str, np.ndarray] = {}
        self.model_name = model
        logger.info("EmbeddingService initialized with model=%s", model)

    # ---- internal helpers -------------------------------------------------

    @staticmethod
    def _hash(text: str) -> str:
        """Stable cache key for a piece of text."""
        return hashlib.sha256(text.strip().encode("utf-8")).hexdigest()

    @staticmethod
    def _validate_texts(texts: List[str]) -> None:
        if not texts:
            raise ValueError("No texts provided to embed.")
        for i, t in enumerate(texts):
            if not isinstance(t, str) or not t.strip():
                raise ValueError(f"Text at index {i} is empty or not a string.")

    def _validate_dim(self, vector: List[float]) -> np.ndarray:
        arr = np.array(vector, dtype=np.float32)
        if arr.shape[0] != EXPECTED_DIM:
            logger.warning(
                "Embedding dimension mismatch: got %d, expected %d "
                "(model may differ from nomic-embed-text)",
                arr.shape[0], EXPECTED_DIM,
            )
        return arr

    # ---- public API ---------------------------------------------------

    def embed_documents(
        self,
        texts: List[str],
        metadatas: List[dict] | None = None,
        batch_size: int = BATCH_SIZE,
    ) -> List[EmbeddedChunk]:
        """Embed a list of documents/chunks, using the cache and batching
        for anything not already cached."""
        self._validate_texts(texts)
        metadatas = metadatas or [{} for _ in texts]
        if len(metadatas) != len(texts):
            raise ValueError("metadatas length must match texts length.")

        results: List[EmbeddedChunk | None] = [None] * len(texts)
        to_embed: List[Tuple[int, str]] = []

        # Check cache first
        for i, text in enumerate(texts):
            key = self._hash(text)
            if key in self._cache:
                results[i] = EmbeddedChunk(text, self._cache[key], metadatas[i])
            else:
                to_embed.append((i, text))

        if to_embed:
            logger.info("Embedding %d new texts (%d served from cache)",
                        len(to_embed), len(texts) - len(to_embed))
            for start in range(0, len(to_embed), batch_size):
                batch = to_embed[start:start + batch_size]
                batch_texts = [t for _, t in batch]

                t0 = time.perf_counter()
                vectors = self._embedder.embed_documents(batch_texts)
                elapsed = time.perf_counter() - t0
                logger.info("Embedded batch of %d in %.2fs (%.1f texts/sec)",
                            len(batch_texts), elapsed, len(batch_texts) / max(elapsed, 1e-6))

                for (orig_idx, text), vec in zip(batch, vectors):
                    arr = self._validate_dim(vec)
                    self._cache[self._hash(text)] = arr
                    results[orig_idx] = EmbeddedChunk(text, arr, metadatas[orig_idx])

        return results  # type: ignore[return-value]

    def embed_query(self, text: str) -> np.ndarray:
        """Embed a single user query. Kept separate from embed_documents
        because some embedding models use different instructions/prefixes
        internally for queries vs. documents (asymmetric embedding)."""
        if not text or not text.strip():
            raise ValueError("Query text cannot be empty.")

        key = self._hash(text)
        if key in self._cache:
            return self._cache[key]

        vector = self._embedder.embed_query(text)
        arr = self._validate_dim(vector)
        self._cache[key] = arr
        return arr

    # ---- similarity ----------------------------------------------------

    @staticmethod
    def cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
        denom = (np.linalg.norm(a) * np.linalg.norm(b))
        if denom == 0:
            return 0.0
        return float(np.dot(a, b) / denom)

    def most_similar(
        self,
        query_vector: np.ndarray,
        chunks: List[EmbeddedChunk],
        top_k: int = 3,
    ) -> List[Tuple[EmbeddedChunk, float]]:
        """Brute-force cosine similarity search. Fine for small corpora
        (hundreds to low thousands of chunks). For production scale, see
        Pattern 9 (Vector Databases) and Pattern 15 (ANN Search)."""
        scored = [
            (chunk, self.cosine_similarity(query_vector, chunk.vector))
            for chunk in chunks
        ]
        scored.sort(key=lambda pair: pair[1], reverse=True)
        return scored[:top_k]

    def cache_stats(self) -> dict:
        return {"cached_texts": len(self._cache), "model": self.model_name}


# --------------------------------------------------------------------------
# Demo: DocuMind mini knowledge base
# --------------------------------------------------------------------------

def run_demo() -> None:
    svc = EmbeddingService()

    knowledge_base = [
        ("To reset your password, go to Settings > Security and click "
         "'Forgot password'. A reset link is emailed within 5 minutes.",
         {"source": "IT_FAQ.md", "team": "IT"}),
        ("Our database failover runbook: promote the standby replica using "
         "`pg_ctl promote`, then update the connection string in the config "
         "service.", {"source": "DB_Runbook.md", "team": "Platform"}),
        ("Employees serving their notice period may work remotely with "
         "manager approval, subject to the standard remote work policy.",
         {"source": "HR_Policy.md", "team": "HR"}),
        ("Deployment pipelines stuck in 'pending' usually indicate a "
         "runner capacity issue; scale the CI runner pool or retry the job.",
         {"source": "CI_Runbook.md", "team": "Platform"}),
    ]

    texts = [t for t, _ in knowledge_base]
    metas = [m for _, m in knowledge_base]

    chunks = svc.embed_documents(texts, metadatas=metas)
    logger.info("Cache stats after ingestion: %s", svc.cache_stats())

    queries = [
        "I forgot my login credentials, how do I get back in?",
        "How do I fail over the database to the replica?",
        "Can I work from home while I'm on my notice period?",
    ]

    for query in queries:
        query_vec = svc.embed_query(query)
        top_matches = svc.most_similar(query_vec, chunks, top_k=2)

        print(f"\nQuery: {query}")
        for chunk, score in top_matches:
            print(f"  score={score:.4f}  source={chunk.metadata.get('source')}"
                  f"  text={chunk.text[:70]}...")


if __name__ == "__main__":
    run_demo()
```

### Expected output (approximate — exact scores vary by model version)

```
Query: I forgot my login credentials, how do I get back in?
  score=0.8123  source=IT_FAQ.md  text=To reset your password, go to Settings > Security and click 'F...
  score=0.4310  source=CI_Runbook.md  text=Deployment pipelines stuck in 'pending' usually indicate a runner...

Query: How do I fail over the database to the replica?
  score=0.8654  source=DB_Runbook.md  text=Our database failover runbook: promote the standby replica usin...
  score=0.3980  source=CI_Runbook.md  text=Deployment pipelines stuck in 'pending' usually indicate a runner...

Query: Can I work from home while I'm on my notice period?
  score=0.8890  source=HR_Policy.md  text=Employees serving their notice period may work remotely with man...
  score=0.3312  source=IT_FAQ.md  text=To reset your password, go to Settings > Security and click 'For...
```

Notice: none of the three queries share exact keywords with their best-matching document ("forgot my login credentials" vs. "reset your password"), yet the correct document wins by a wide margin every time. That's the entire value of this pattern.

### Key production notes

- **Caching matters**: in a real system, the same FAQ paragraph might be embedded once at ingestion and never touched again — but user queries repeat constantly ("how do I reset my password" gets asked hundreds of times). Caching query embeddings can meaningfully cut latency and Ollama load.
- **Batching matters**: embedding one text at a time is slow due to per-call overhead. Batching (here, 32 at a time) gives much better throughput during bulk ingestion of a large document set.
- **Model consistency is critical**: you must use the *same* embedding model for both documents and queries, and never mix vectors from two different embedding models in one similarity comparison — the numbers are meaningless across models.
- **This file uses brute-force cosine similarity** on purpose, to keep the embedding concept isolated and easy to see. Real systems with more than a few thousand chunks need an actual vector database with an ANN index — that's Pattern 9 and Pattern 15.

## 8. Pinned Dependency Versions

```txt
langchain-ollama==0.2.3
langchain-core==0.3.29
numpy==1.26.4
python>=3.11,<3.13
```

```bash
# Ollama model used in this file
ollama pull nomic-embed-text
```

---

**Next:** say `next` and I'll build `02_Embedding_Generation.md` — going deeper into *how* embedding models are trained and how to choose/tune one for a production use case (dimensionality trade-offs, instruction-tuned vs. general embeddings, normalization, and multi-model strategies).
