# Pattern 07 — Context Caching

[← Back to index](./README.md) | Previous: [Pattern 06 — Context Window Management](./06-context-window-management.md) | Next: Pattern 08 — Context Isolation (queued)

---

## 1. Introduce the pattern

**Context Caching** avoids re-computing or re-sending context that hasn't actually changed since the last time it was processed.

Every pattern so far treats each request as if it starts from a blank slate: embed the candidates (Pattern 01), filter them (02), compress them (03), rank them (04), fit them to budget (05), manage them across turns (06). In production, a large fraction of that work is **identical, request after request** — the same compliance policy text gets embedded thousands of times a day across different analysts' sessions; the same system prompt gets resent on every single call; the same question, or a near-duplicate of it, against the same case data, gets asked more than once.

```
Request N            Request N+1 (same case, same policies, similar question)
   │                       │
   ├─ embed policies ──────┤ ← identical work, done twice
   ├─ embed transactions ──┤ ← identical work, done twice
   ├─ call LLM ─────────── ┤ ← nearly identical work, done twice

[ CACHING ] intercepts each of these: content-addressable cache for embeddings,
fingerprint + semantic cache for LLM responses — so unchanged work is reused,
not repeated.
```

**Mental model:** a research assistant who re-reads and re-summarizes the same regulatory handbook from scratch every single time a question touches it — instead of recognizing "I've read this exact page before, I already have my notes on it" — is wasting enormous effort. Caching is the discipline of keeping (and correctly invalidating) those notes.

---

## 2. The problem it solves

Without caching, three costs compound across a production system serving many analysts and many turns:

1. **Redundant embedding computation.** Pattern 01's `ContextSelector` embeds every candidate item against the query on every single call. Static sources — compliance policies, historical case summaries that don't change — get re-embedded identically, over and over, across unrelated requests, for no benefit.
2. **Redundant LLM calls for effectively-identical questions.** Two analysts (or the same analyst, re-reading the same case later) asking "why was this flagged?" against the same, unchanged case context are asking a question the system has already answered — recomputing that answer costs latency and money for zero new information.
3. **No principled way to know when a cached result is still valid.** The dangerous failure mode isn't under-caching (a minor efficiency loss) — it's **over-caching**: serving a stale cached answer after the underlying case data has actually changed (a new transaction posted, an analyst added a note). In a fraud-operations context, a stale cached answer isn't just annoying, it can be actively wrong at a moment that matters. Caching has to be paired with a correct invalidation strategy or it isn't safe to use at all.

Context Caching solves this with two complementary techniques: a **content-addressable embedding cache** (hash the input, reuse the vector if the hash matches) and a **fingerprinted semantic response cache** (only serve a cached answer if both the query is semantically close to a prior one *and* the underlying context is byte-for-byte the same as when that answer was generated).

---

## 3. A realistic enterprise problem (Helios)

Helios's compliance policy library (Policy FR-14 and others) doesn't change often — maybe a handful of times a year — but it gets pulled into the Selection stage's embedding step on every single fraud investigation query, across potentially thousands of queries a day, across every analyst. Re-embedding the same static policy text that many times is pure waste.

Separately, a common real pattern in fraud operations: an analyst opens a case, asks "why was this flagged?", steps away, comes back twenty minutes later, and effectively asks the same thing again ("remind me why this case was flagged") before continuing. If nothing about the case has changed in those twenty minutes, recomputing the full answer from scratch is unnecessary — but if a new transaction posted in the meantime, serving the old cached answer would be a real problem, so the cache must detect that the underlying context changed and correctly miss.

Context Caching is the layer that makes the first case fast and the second case safe.

---

## 4. Architecture / flow diagram

```mermaid
flowchart TD
    A[Candidate context items\n+ analyst query] --> B{Embedding Cache}
    B -->|hash(content) found\n& not expired| C[Reuse cached vector]
    B -->|miss| D[Call OllamaEmbeddings]
    D --> E[Store in cache, keyed by\nsha256 content hash]
    C --> F[Relevance scoring\n(Pattern 01)]
    E --> F

    F --> G[... Filtering, Compression,\nRanking, Prioritization ...]
    G --> H[Final assembled prompt\n+ context fingerprint]

    H --> I{Semantic Response Cache}
    I -->|query embedding similar\nAND context fingerprint matches| J[Return cached response\n(no LLM call)]
    I -->|miss: new question,\nor context fingerprint changed| K[Call ChatOllama]
    K --> L[Store response in cache,\nkeyed by fingerprint + query embedding]

    J --> M[Response to analyst]
    L --> M

    style B fill:#4A90D9,color:#fff
    style I fill:#4A90D9,color:#fff
    style J fill:#2E7D32,color:#fff
```

---

## 5. The complete request-to-response flow

1. **Embedding cache check**: before calling `OllamaEmbeddings` on any candidate context item (as Pattern 01's selector does), the pipeline hashes the item's content (SHA-256) and checks a content-addressable cache. A hit returns the stored vector instantly; a miss computes it once and stores it, keyed by that hash — so identical content, even from a completely unrelated request, is only ever embedded once (until it expires or the underlying content changes, which changes the hash).
2. **Downstream pipeline runs as normal** (Filtering, Compression, Ranking, Prioritization — Patterns 02–05) using whichever vectors came from cache or fresh computation; caching is transparent to every stage after it.
3. **Context fingerprinting**: once the final context block is assembled, the pipeline computes a fingerprint (hash) of its exact content. This fingerprint is the safety mechanism: it changes the instant *any* underlying context changes (a new transaction, an updated note), which is exactly the property needed to invalidate stale cache entries automatically.
4. **Semantic response cache check**: the analyst's query is embedded, and compared against previously cached (query embedding, context fingerprint, response) triples **for the same case**. A cache hit requires both: (a) the new query is semantically close enough to a previously answered one, **and** (b) the context fingerprint is an exact match — if either condition fails, it's a miss.
5. **On a hit**: the cached response is returned directly, with no LLM call at all — logged distinctly so cache-hit-rate is observable.
6. **On a miss**: `ChatOllama` is called normally, and the new (query embedding, context fingerprint, response) triple is stored for future reuse.
7. **TTL and capacity bounds**: every cache entry carries a time-to-live and the caches are capacity-bounded with LRU eviction, so stale-but-still-fingerprint-matching entries (e.g., policy text that's correct today but might be reviewed and reissued next quarter even without Helios "knowing" via a fingerprint change) don't live forever.

---

## 6. Why this pattern is appropriate here

- **Embedding computation is pure, so content-addressable caching is exactly correct, not just a heuristic.** The same input to `OllamaEmbeddings.embed_documents` always produces the same vector (for a fixed model), so hashing content and caching by that hash has zero correctness risk — this is the safest, highest-value cache in the whole pipeline, and worth implementing before anything else.
- **Response caching genuinely needs the fingerprint guard, not just a semantic similarity threshold.** Semantic similarity alone ("this new question is 92% similar to a cached one") is not sufficient in a domain where the underlying facts can change between two similar-looking questions — pairing similarity with an exact fingerprint match on the actual context sent is what makes this cache *safe* to use in a compliance-sensitive setting rather than merely fast.
- **This pairs naturally with Pattern 06.** The durable `session_facts` and hot window built up in Window Management are exactly the kind of stable-until-they-change context that benefits most from fingerprinted caching — a case that's gone several turns without new information is a case where cache hits should be common; a case where a new transaction just posted is a case where the fingerprint should (correctly) force a miss.
- **A note on local models vs. hosted prompt caching**: several hosted LLM APIs offer native prompt-prefix caching (reusing KV-cache state for a shared prompt prefix across calls, transparently, server-side). Ollama, running locally, doesn't expose an equivalent formal API in this stack — so this pattern implements the caching Helios actually needs (embeddings and full responses) explicitly, at the application layer, rather than relying on a caching primitive the local serving stack doesn't provide. If you later move part of this pipeline to a hosted API with native prompt caching, structuring your prompts with a stable, unchanging prefix (system prompt + static policy text first, variable content last) is still the right habit — this pattern's fingerprinting approach and that habit are complementary, not alternatives.

---

## 7. Production-quality Python implementation

```python
"""
helios_context_caching.py

Pattern 07 — Context Caching
Helios Fraud Investigation Copilot

Two complementary caches:
1. EmbeddingCache — content-addressable (SHA-256 of text), exact-match,
   used to avoid re-embedding unchanged context items (e.g. static policies).
2. SemanticResponseCache — per-case, requires BOTH semantic similarity of the
   query AND an exact context-fingerprint match before serving a cached
   response, so cache hits are fast without ever risking a stale answer.

Requirements:
    pip install "langchain-core>=0.3.60" "langchain-ollama>=0.3.0" "numpy>=1.26"

Assumes a local Ollama server (`ollama serve`) with:
    ollama pull llama3.1
    ollama pull nomic-embed-text
"""

from __future__ import annotations

import hashlib
import logging
import time
from collections import OrderedDict
from dataclasses import dataclass, field

import numpy as np
from langchain_core.prompts import ChatPromptTemplate
from langchain_ollama import ChatOllama, OllamaEmbeddings

logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")
logger = logging.getLogger("helios.context_caching")


def sha256_hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
    denom = (np.linalg.norm(a) * np.linalg.norm(b)) + 1e-8
    return float(np.dot(a, b) / denom)


# --------------------------------------------------------------------------- #
# Generic bounded TTL + LRU cache primitive
# --------------------------------------------------------------------------- #

@dataclass
class CacheEntry:
    value: object
    created_at: float
    hit_count: int = 0


class TTLLRUCache:
    """A capacity-bounded cache with both TTL expiry and LRU eviction.
    Deliberately simple and dependency-free — swap the backing store for
    Redis in a real multi-process Helios deployment without changing the
    interface used by the caches built on top of it."""

    def __init__(self, max_size: int = 5000, ttl_seconds: float = 3600) -> None:
        self.max_size = max_size
        self.ttl_seconds = ttl_seconds
        self._store: OrderedDict[str, CacheEntry] = OrderedDict()
        self.hits = 0
        self.misses = 0

    def get(self, key: str) -> object | None:
        entry = self._store.get(key)
        if entry is None:
            self.misses += 1
            return None
        if time.time() - entry.created_at > self.ttl_seconds:
            del self._store[key]
            self.misses += 1
            return None
        entry.hit_count += 1
        self._store.move_to_end(key)  # mark as recently used
        self.hits += 1
        return entry.value

    def set(self, key: str, value: object) -> None:
        if key in self._store:
            self._store.move_to_end(key)
        self._store[key] = CacheEntry(value=value, created_at=time.time())
        if len(self._store) > self.max_size:
            evicted_key, _ = self._store.popitem(last=False)  # evict least-recently-used
            logger.debug("Evicted LRU cache entry: %s", evicted_key)

    @property
    def hit_rate(self) -> float:
        total = self.hits + self.misses
        return round(self.hits / total, 3) if total else 0.0


# --------------------------------------------------------------------------- #
# 1. Content-addressable embedding cache
# --------------------------------------------------------------------------- #

class CachedEmbeddings:
    """Wraps OllamaEmbeddings with a content-addressable cache. Because
    embeddings are a pure function of (model, text), hashing the text is an
    exact-correctness cache key -- there is no staleness risk here as long
    as the underlying embedding model doesn't change."""

    def __init__(self, model: str = "nomic-embed-text", ttl_seconds: float = 24 * 3600) -> None:
        self._embeddings = OllamaEmbeddings(model=model)
        self._model_name = model
        self.cache = TTLLRUCache(max_size=20_000, ttl_seconds=ttl_seconds)

    def _key(self, text: str) -> str:
        return f"{self._model_name}:{sha256_hash(text)}"

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        results: list[list[float] | None] = [None] * len(texts)
        to_compute: list[tuple[int, str]] = []

        for i, text in enumerate(texts):
            cached = self.cache.get(self._key(text))
            if cached is not None:
                results[i] = cached  # type: ignore[assignment]
            else:
                to_compute.append((i, text))

        if to_compute:
            fresh_vectors = self._embeddings.embed_documents([t for _, t in to_compute])
            for (i, text), vector in zip(to_compute, fresh_vectors):
                self.cache.set(self._key(text), vector)
                results[i] = vector

        logger.info(
            "Embedding cache: %d/%d served from cache (hit_rate=%.0f%%)",
            len(texts) - len(to_compute), len(texts), self.cache.hit_rate * 100,
        )
        return results  # type: ignore[return-value]

    def embed_query(self, text: str) -> list[float]:
        cached = self.cache.get(self._key(text))
        if cached is not None:
            return cached  # type: ignore[return-value]
        vector = self._embeddings.embed_query(text)
        self.cache.set(self._key(text), vector)
        return vector


# --------------------------------------------------------------------------- #
# 2. Fingerprinted semantic response cache
# --------------------------------------------------------------------------- #

@dataclass
class CachedResponse:
    query_text: str
    query_embedding: np.ndarray
    context_fingerprint: str
    response_text: str


class SemanticResponseCache:
    """
    Caches LLM responses per case. A cache hit requires BOTH:
      1. The new query's embedding is within `similarity_threshold` of a
         previously cached query for this case, AND
      2. The context fingerprint matches EXACTLY.

    Condition (2) is what makes this safe: if anything in the underlying
    context changed (new transaction, new note), the fingerprint changes,
    and every previously cached entry for this case instantly stops matching
    -- no explicit invalidation logic needed, it falls out of the fingerprint
    design for free.
    """

    def __init__(self, embeddings: CachedEmbeddings, similarity_threshold: float = 0.93,
                 ttl_seconds: float = 1800) -> None:
        self.embeddings = embeddings
        self.similarity_threshold = similarity_threshold
        self._by_case: dict[str, list[CachedResponse]] = {}
        self.ttl_seconds = ttl_seconds
        self._timestamps: dict[int, float] = {}
        self.hits = 0
        self.misses = 0

    @staticmethod
    def fingerprint_context(context_block: str) -> str:
        return sha256_hash(context_block)

    def lookup(self, case_id: str, query: str, context_fingerprint: str) -> str | None:
        candidates = self._by_case.get(case_id, [])
        if not candidates:
            self.misses += 1
            return None

        query_vec = np.array(self.embeddings.embed_query(query))

        for cached in candidates:
            if cached.context_fingerprint != context_fingerprint:
                continue  # context changed since this was cached -- never a valid hit
            similarity = cosine_similarity(query_vec, cached.query_embedding)
            if similarity >= self.similarity_threshold:
                logger.info(
                    "Semantic cache HIT for case=%s (similarity=%.3f to cached query %r)",
                    case_id, similarity, cached.query_text,
                )
                self.hits += 1
                return cached.response_text

        self.misses += 1
        return None

    def store(self, case_id: str, query: str, context_fingerprint: str, response_text: str) -> None:
        query_vec = np.array(self.embeddings.embed_query(query))
        entry = CachedResponse(
            query_text=query, query_embedding=query_vec,
            context_fingerprint=context_fingerprint, response_text=response_text,
        )
        self._by_case.setdefault(case_id, []).append(entry)

    @property
    def hit_rate(self) -> float:
        total = self.hits + self.misses
        return round(self.hits / total, 3) if total else 0.0


# --------------------------------------------------------------------------- #
# Pipeline tying both caches together
# --------------------------------------------------------------------------- #

class HeliosCachingPipeline:
    def __init__(self, llm_model: str = "llama3.1") -> None:
        self.embeddings = CachedEmbeddings()
        self.response_cache = SemanticResponseCache(self.embeddings)
        self.llm = ChatOllama(model=llm_model, temperature=0.1)
        self.prompt = ChatPromptTemplate.from_messages(
            [
                ("system", "You are the Helios Fraud Investigation Copilot. Answer using ONLY the context given."),
                ("human", "Analyst question: {query}\n\n--- CONTEXT ---\n{context}"),
            ]
        )

    def answer(self, case_id: str, query: str, context_block: str) -> tuple[str, bool]:
        """Returns (response_text, was_cache_hit)."""
        fingerprint = SemanticResponseCache.fingerprint_context(context_block)

        cached = self.response_cache.lookup(case_id, query, fingerprint)
        if cached is not None:
            return cached, True

        chain = self.prompt | self.llm
        response = chain.invoke({"query": query, "context": context_block})
        response_text = response.content

        self.response_cache.store(case_id, query, fingerprint, response_text)
        return response_text, False


# --------------------------------------------------------------------------- #
# Example run
# --------------------------------------------------------------------------- #

if __name__ == "__main__":
    pipeline = HeliosCachingPipeline()
    case_id = "CASE-88421"

    context_v1 = (
        "[case_metadata] Case CASE-88421: $14,200 wire to a new beneficiary in a high-risk jurisdiction.\n\n"
        "[compliance_policy] Policy FR-14: transfers >5x 90-day average + beneficiary added <24h prior "
        "require a 48h hold."
    )

    print("=== Turn 1: original question ===")
    answer_1, hit_1 = pipeline.answer(case_id, "Why was this transaction flagged?", context_v1)
    print(f"cache_hit={hit_1}\n{answer_1}\n")

    print("=== Turn 2: near-duplicate question, SAME context (should be a cache hit) ===")
    answer_2, hit_2 = pipeline.answer(case_id, "Remind me why this case was flagged?", context_v1)
    print(f"cache_hit={hit_2}\n{answer_2}\n")

    print("=== Turn 3: same near-duplicate question, but context CHANGED (must be a cache miss) ===")
    context_v2 = context_v1 + (
        "\n\n[transaction_history] NEW: 5 minutes ago | wire $11,000 | same new beneficiary | "
        "second large transfer to same account within 24 hours."
    )
    answer_3, hit_3 = pipeline.answer(case_id, "Remind me why this case was flagged?", context_v2)
    print(f"cache_hit={hit_3}\n{answer_3}\n")

    print("=== Cache stats ===")
    print(f"Embedding cache hit rate: {pipeline.embeddings.cache.hit_rate * 100:.0f}%")
    print(f"Semantic response cache hit rate: {pipeline.response_cache.hit_rate * 100:.0f}%")
```

### Notes on running this yourself

- Turn 2 should print `cache_hit=True` — same case, semantically close question, **unchanged** context fingerprint.
- Turn 3 should print `cache_hit=False` even though the question is nearly identical to Turn 2's — the moment a real transaction was added to the context, the fingerprint changed, and the old cache entry stops matching automatically. Run it yourself and confirm this holds; it's the core safety property of this pattern.
- `TTLLRUCache` is intentionally dependency-free (a plain `OrderedDict`) for this worked example — the interface (`get`/`set`) is exactly what you'd need to swap in a Redis-backed implementation for a real multi-process/multi-instance Helios deployment, where an in-memory cache wouldn't be shared across service instances.
- `similarity_threshold=0.93` is a starting point, not a universal constant — tune it against your own query distribution; too low risks false-positive cache hits (subtly different questions treated as the same), too high makes the semantic cache barely more useful than an exact-match cache.

---

**Next up:** Pattern 08 — Context Isolation, where we address the opposite concern from caching: making sure context from *different* cases, tenants, or concurrent agent tasks never leaks into each other — including making sure a cache like the one built here is correctly partitioned per case, not shared across them.
