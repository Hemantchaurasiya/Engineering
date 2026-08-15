# Pattern 15: Approximate Nearest Neighbor (ANN) Search

## 1. Introduce the Pattern

**Approximate Nearest Neighbor (ANN) search** is the indexing technique that makes vector databases (Pattern 9) fast at scale. Instead of computing an exact similarity score (Pattern 14) between a query and *every single* stored vector — an O(n) brute-force scan — ANN algorithms build a data structure at ingestion time that lets queries skip the vast majority of vectors entirely, finding results that are *very likely* the true nearest neighbors, in a small fraction of the time, at the cost of a small, tunable chance of missing the exact best match.

```
Brute-force exact search (what Patterns 1/2/4 did manually):
  query vector vs. ALL 500,000 stored vectors
  → 500,000 similarity computations, every single query
  → guaranteed exact top-K
  → too slow past roughly tens of thousands of vectors

ANN search (what Chroma does internally in Pattern 9):
  query vector vs. a small, cleverly-chosen SUBSET of vectors,
  guided by a pre-built index structure (e.g. HNSW graph)
  → maybe 500-2,000 similarity computations, not 500,000
  → NOT guaranteed exact top-K, but typically 95-99%+ recall
    in practice, tunable against speed
  → scales to millions of vectors with sub-second query latency
```

This is the pattern operating silently underneath every `collection.query()` call made throughout Patterns 9–13 — this file finally opens up what "fast vector search" actually means mechanically, and how to tune it.

## 2. The Problem It Solves

Pattern 14 established the exact math of comparing two vectors. The problem ANN search solves is what happens when you need to do that comparison **against every stored vector, for every single query, at production scale**:

- **Brute-force search is linear in corpus size.** Doubling the corpus doubles every query's latency. A corpus that grows from 10,000 to 1,000,000 chunks (a completely realistic trajectory for a growing enterprise knowledge base) makes every single search 100x slower under brute force — an unacceptable production trajectory.
- **Exact nearest-neighbor search in high dimensions is fundamentally hard to speed up without approximation.** This is a well-known result in computational geometry (sometimes called the "curse of dimensionality") — exact indexing structures that work well for 2D/3D data (like k-d trees) degrade to nearly brute-force performance once dimensionality gets into the hundreds, which is exactly where embedding vectors (384–1536+ dimensions) live.
- **Most applications don't actually need a mathematically exact top-K.** If the true 5th-best result and the true 6th-best result are 99.7% similar to each other in relevance, returning one instead of the other in position 5 is imperceptible to the end user, but computing the guaranteed-exact answer might cost 100x more compute. ANN search deliberately trades a small, controllable amount of that imperceptible precision for a large, very perceptible speed gain.

## 3. Realistic Enterprise Scenario

DocuMind's corpus has grown to several hundred thousand chunks across all ingested documents. Query latency has become noticeable — employees are waiting a couple of seconds per search, which feels sluggish for an internal tool meant to feel instant. The platform team needs to understand: what's actually happening inside Chroma's index that makes search fast, what knobs exist to trade off speed vs. accuracy, and how to verify the approximation isn't silently hurting result quality below an acceptable threshold.

This file builds a **from-scratch, simplified HNSW-style ANN index** — not for production use (a real deployment should use Chroma's built-in indexing, exactly as Pattern 9 did), but to make the mechanism fully transparent, plus a recall-evaluation harness to measure approximation quality against brute-force ground truth.

## 4. Architecture / Flow Diagram

```
                    INGESTION TIME (build the index once)

┌───────────────────────────────────────────────────────────────┐
│  All corpus vectors                                               │
│  [v1] [v2] [v3] [v4] [v5] [v6] [v7] [v8] ... [v500000]            │
└───────────────────────────┬───────────────────────────────────┘
                            ▼
          ┌────────────────────────────────────┐
          │  Build a navigable graph structure     │
          │  (HNSW: Hierarchical Navigable          │
          │   Small World graph)                    │
          │                                          │
          │  - each vector becomes a graph node       │
          │  - nodes are connected to their nearby     │
          │    neighbors (not all other nodes)          │
          │  - multiple LAYERS: sparse "highway"        │
          │    connections at the top layer for          │
          │    fast long-distance jumps, denser          │
          │    connections at the bottom layer for        │
          │    fine-grained local search                  │
          └────────────────────────────────────┘

                    QUERY TIME (traverse the graph, not scan everything)

┌───────────────────────────┐
│      Query vector            │
└─────────────┬─────────────┘
              ▼
   ┌────────────────────────────────────┐
   │  Enter at the TOP (sparse) layer       │
   │  → greedily hop toward the closest        │
   │    neighbor at this layer, repeat          │
   │    until no closer neighbor exists           │
   └─────────────┬───────────────────────┘
                 ▼
   ┌────────────────────────────────────┐
   │  Drop down to the NEXT layer            │
   │  starting from where the previous layer   │
   │  search ended → repeat the greedy hop       │
   └─────────────┬───────────────────────┘
                 ▼
              ... repeat down through all layers ...
                 ▼
   ┌────────────────────────────────────┐
   │  BOTTOM (dense) layer: do a wider,        │
   │  more thorough local search around          │
   │  where we ended up, collect the top-K         │
   └─────────────┬───────────────────────┘
                 ▼
        approximate top-K results
        (visited maybe 0.5% of all vectors,
         not 100% of them)
```

## 5. Request-to-Response Walkthrough

1. **Index construction (once, at ingestion time, incrementally as new chunks arrive)**: each new vector is inserted into a multi-layer graph structure. It's randomly assigned a "top layer" (higher layers are exponentially less populated — most vectors only exist in the bottom layer, a few exist in several layers, forming the "express lanes" for fast traversal), then connected via edges to a small number of its nearest-already-inserted neighbors at each of its layers.
2. **Query time**: the search starts at a designated entry point in the very top (sparsest) layer.
3. At each layer, the algorithm does a **greedy walk**: from the current position, check its graph neighbors, move to whichever neighbor is closer to the query vector than the current position, and repeat until no neighbor is closer (a local minimum has been reached at this layer).
4. The algorithm then **drops down one layer**, continuing the greedy walk from that same position in the denser, more fine-grained layer below — top layers get you to the right *neighborhood* quickly, bottom layers refine the *exact* answer within that neighborhood.
5. At the bottom (densest) layer, a slightly wider local search collects the actual top-K candidates to return, rather than stopping at just the single nearest point.
6. Because at every step the algorithm only examines a small number of graph neighbors (not the whole corpus), total query cost scales roughly **logarithmically** with corpus size rather than linearly — this is the entire mechanism behind ANN search's speed advantage, and why it holds up even as the corpus grows into the millions.
7. **Recall is "approximate"** because the greedy walk can, in principle, get stuck in a local minimum that isn't the true global nearest neighbor — a genuinely rare but real occurrence, tunable via index construction parameters (`ef_construction`, controlling how thoroughly the graph is built) and query-time parameters (`ef_search`, controlling how wide the search is at query time) — higher values of both cost more compute but reduce this rare miss rate further.

## 6. Why This Pattern Is Appropriate

| Approach | Query latency at 500K vectors | Guaranteed exact results? | Scales past millions of vectors? |
|---|---|---|---|
| Brute-force scan (Patterns 1/2/4's teaching approach) | Seconds, growing linearly with corpus size | Yes | No — becomes impractically slow |
| Exact tree-based indexing (e.g. k-d trees) | Degrades toward brute-force performance in high dimensions (curse of dimensionality) | Yes | Poorly — high-dimensional embeddings defeat these structures' assumptions |
| **ANN search (this pattern, e.g. HNSW)** | Milliseconds, growing roughly logarithmically | No — but typically 95-99%+ recall in practice, tunable | Yes — this is specifically what ANN indexing is built for |

The trade-off ANN search makes — a small, controllable, usually-imperceptible chance of a slightly-suboptimal result, in exchange for orders-of-magnitude faster queries at scale — is the right call for essentially every production retrieval system, which is exactly why every mainstream vector database (Chroma included, used throughout Pattern 9 onward) implements some form of ANN indexing (commonly HNSW) as its default rather than brute-force search.

## 7. Production-Quality Python Implementation

```python
"""
ann_search_pipeline.py

A simplified, from-scratch HNSW-style ANN index, built to make the
mechanism (multi-layer graph, greedy traversal, approximate recall)
fully transparent for study purposes. Also includes a recall
evaluation harness comparing this ANN index against brute-force
ground truth — the kind of check a production team should run before
trusting an ANN configuration's speed/accuracy trade-off.

NOTE: for actual production use, rely on Chroma's built-in HNSW
implementation (used throughout Pattern 9-13), which is a mature,
heavily optimized C++ implementation. This file exists purely to make
the underlying mechanism concrete, not to replace it.
"""

from __future__ import annotations

import random
import time
from dataclasses import dataclass, field
from typing import Dict, List, Set, Tuple

import numpy as np


def cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
    denom = np.linalg.norm(a) * np.linalg.norm(b)
    return float(np.dot(a, b) / denom) if denom else 0.0


# --------------------------------------------------------------------------
# Simplified HNSW-style index
# --------------------------------------------------------------------------

@dataclass
class HNSWConfig:
    m: int = 8                  # max neighbors per node, per layer
    ef_construction: int = 40   # candidate list size while BUILDING the index
    ef_search: int = 20         # candidate list size while QUERYING the index
    level_multiplier: float = 1 / np.log(2)  # controls layer-count distribution


class SimplifiedHNSW:
    """A simplified, single-process, in-memory HNSW-style index.
    Real implementations (e.g. inside Chroma) add substantial
    additional engineering (disk persistence, concurrent updates,
    heuristic neighbor selection) omitted here for clarity."""

    def __init__(self, config: HNSWConfig = HNSWConfig()):
        self.config = config
        self.vectors: Dict[int, np.ndarray] = {}
        self.layers: Dict[int, Dict[int, Set[int]]] = {}  # layer -> node_id -> neighbor node_ids
        self.entry_point: int | None = None
        self.max_layer: int = -1
        self._rng = random.Random(42)

    def _random_layer(self) -> int:
        return int(-np.log(self._rng.random()) * self.config.level_multiplier)

    def _search_layer(self, query: np.ndarray, entry: int, layer: int, ef: int) -> List[Tuple[int, float]]:
        """Greedy graph traversal within one layer, returning up to `ef`
        closest candidates found."""
        visited = {entry}
        candidates = [(cosine_similarity(query, self.vectors[entry]), entry)]
        best = list(candidates)

        while candidates:
            candidates.sort(key=lambda x: x[0], reverse=True)
            current_score, current = candidates.pop(0)

            worst_best_score = min(best, key=lambda x: x[0])[0] if len(best) >= ef else -1.0
            if current_score < worst_best_score:
                break  # no closer neighbor can improve the result set further

            for neighbor in self.layers.get(layer, {}).get(current, set()):
                if neighbor in visited:
                    continue
                visited.add(neighbor)
                score = cosine_similarity(query, self.vectors[neighbor])
                candidates.append((score, neighbor))
                best.append((score, neighbor))

            best.sort(key=lambda x: x[0], reverse=True)
            best = best[:ef]

        return best

    def insert(self, node_id: int, vector: np.ndarray) -> None:
        self.vectors[node_id] = vector
        node_layer = self._random_layer()

        if self.entry_point is None:
            self.entry_point = node_id
            self.max_layer = node_layer
            for layer in range(node_layer + 1):
                self.layers.setdefault(layer, {})[node_id] = set()
            return

        entry = self.entry_point
        # Descend from the top layer down to node_layer+1 with a simple
        # greedy walk (ef=1), just to find a good entry point per layer.
        for layer in range(self.max_layer, node_layer, -1):
            if layer not in self.layers:
                continue
            nearest = self._search_layer(vector, entry, layer, ef=1)
            if nearest:
                entry = nearest[0][1]

        # For layers at and below node_layer, do a proper ef_construction
        # search and connect to the best candidates found.
        for layer in range(min(node_layer, self.max_layer), -1, -1):
            if layer not in self.layers:
                self.layers[layer] = {}
            candidates = self._search_layer(vector, entry, layer, ef=self.config.ef_construction)
            neighbors = [node for _, node in candidates[:self.config.m]]

            self.layers[layer].setdefault(node_id, set())
            for neighbor in neighbors:
                self.layers[layer][node_id].add(neighbor)
                self.layers[layer].setdefault(neighbor, set()).add(node_id)
            if candidates:
                entry = candidates[0][1]

        for layer in range(node_layer + 1):
            self.layers.setdefault(layer, {}).setdefault(node_id, set())

        if node_layer > self.max_layer:
            self.max_layer = node_layer
            self.entry_point = node_id

    def search(self, query: np.ndarray, top_k: int = 5) -> List[Tuple[int, float]]:
        if self.entry_point is None:
            return []
        entry = self.entry_point
        for layer in range(self.max_layer, 0, -1):
            nearest = self._search_layer(query, entry, layer, ef=1)
            if nearest:
                entry = nearest[0][1]
        results = self._search_layer(query, entry, 0, ef=max(self.config.ef_search, top_k))
        results.sort(key=lambda x: x[0], reverse=True)
        return [(node_id, score) for score, node_id in results[:top_k]]


# --------------------------------------------------------------------------
# Brute-force ground truth + recall evaluation
# --------------------------------------------------------------------------

def brute_force_search(query: np.ndarray, vectors: Dict[int, np.ndarray], top_k: int) -> List[int]:
    scored = [(node_id, cosine_similarity(query, vec)) for node_id, vec in vectors.items()]
    scored.sort(key=lambda x: x[1], reverse=True)
    return [node_id for node_id, _ in scored[:top_k]]


def evaluate_recall(index: SimplifiedHNSW, vectors: Dict[int, np.ndarray],
                     queries: List[np.ndarray], top_k: int = 10) -> float:
    """Recall@K: of the true top-K nearest neighbors (brute-force ground
    truth), what fraction did the ANN index actually return? This is
    exactly the kind of check a production team should run before
    trusting an ef_search/ef_construction configuration at scale."""
    total_recall = 0.0
    for query in queries:
        true_top_k = set(brute_force_search(query, vectors, top_k))
        ann_top_k = set(node_id for node_id, _ in index.search(query, top_k))
        total_recall += len(true_top_k & ann_top_k) / top_k
    return total_recall / len(queries)


# --------------------------------------------------------------------------
# Demo: build a moderately sized index, compare speed and recall
# --------------------------------------------------------------------------

def run_demo() -> None:
    rng = np.random.default_rng(7)
    n_vectors = 3000
    dim = 64

    print(f"Generating {n_vectors} random {dim}-dim vectors "
          f"(standing in for real embeddings, for a fast, self-contained demo)...\n")
    vectors = {i: rng.normal(size=dim) for i in range(n_vectors)}

    index = SimplifiedHNSW(HNSWConfig(m=8, ef_construction=40, ef_search=20))
    build_start = time.perf_counter()
    for node_id, vec in vectors.items():
        index.insert(node_id, vec)
    build_time = time.perf_counter() - build_start
    print(f"Built ANN index over {n_vectors} vectors in {build_time:.2f}s\n")

    queries = [rng.normal(size=dim) for _ in range(20)]

    brute_start = time.perf_counter()
    for q in queries:
        brute_force_search(q, vectors, top_k=10)
    brute_time = time.perf_counter() - brute_start

    ann_start = time.perf_counter()
    for q in queries:
        index.search(q, top_k=10)
    ann_time = time.perf_counter() - ann_start

    recall = evaluate_recall(index, vectors, queries, top_k=10)

    print(f"Brute-force search: {brute_time:.4f}s total for {len(queries)} queries "
          f"({brute_time / len(queries) * 1000:.2f}ms/query)")
    print(f"ANN (HNSW-style) search: {ann_time:.4f}s total for {len(queries)} queries "
          f"({ann_time / len(queries) * 1000:.2f}ms/query)")
    print(f"Speedup: {brute_time / ann_time:.1f}x")
    print(f"Recall@10 (ANN vs. brute-force ground truth): {recall * 100:.1f}%")


if __name__ == "__main__":
    run_demo()
```

### Expected output (shape — exact numbers vary by run and hardware)

```
Generating 3000 random 64-dim vectors (standing in for real embeddings, for a fast, self-contained demo)...

Built ANN index over 3000 vectors in 1.84s

Brute-force search: 0.1210s total for 20 queries (6.05ms/query)
ANN (HNSW-style) search: 0.0186s total for 20 queries (0.93ms/query)
Speedup: 6.5x
Recall@10 (ANN vs. brute-force ground truth): 94.5%
```

Even at this deliberately small demo scale (3,000 vectors, chosen so the teaching implementation runs quickly), the speedup is already clearly visible, and recall stays high — trading roughly 5% of theoretical exactness for several times the speed. At real production scale (hundreds of thousands to millions of vectors), this gap widens dramatically: brute-force search time keeps growing linearly while ANN search time grows only logarithmically, and a well-tuned mature implementation (like Chroma's) achieves considerably higher recall than this simplified teaching version at comparable speed.

### Key production notes

- **Use a real, mature ANN implementation in production** — Chroma's built-in HNSW indexing (used throughout Pattern 9 onward), or dedicated libraries like `hnswlib` or `faiss`, are vastly more optimized (proper heuristic neighbor selection, disk persistence, concurrent-safe updates) than this teaching implementation. This file exists to make the *mechanism* transparent, not to be deployed.
- **`ef_search` is the main production-tunable knob** — increasing it improves recall at the cost of query latency, decreasing it does the reverse, and unlike `ef_construction` (a one-time index-build cost), it can typically be adjusted per-query without rebuilding anything, making it a natural lever for a "fast mode" vs. "thorough mode" toggle in a real system.
- **Always measure recall against brute-force ground truth before trusting a configuration at scale** — the `evaluate_recall` function here is exactly the kind of validation harness a production team should run periodically (e.g. after a corpus grows significantly, or after tuning index parameters) rather than assuming default settings remain appropriate forever.
- **ANN's approximation is a feature, not a bug, for this use case** — Pattern 12 (Re-ranking) already establishes that initial retrieval doesn't need to be perfectly precise, since re-ranking cleans up the top candidates afterward; ANN search's small recall trade-off is very rarely the bottleneck on actual answer quality in a well-designed pipeline that includes re-ranking downstream.
- **This closes the loop on the whole series**: Pattern 1 introduced embeddings as points in meaning-space; Pattern 14 explained how to measure distance between them precisely; this pattern explains how to find the nearest ones *fast* without checking every single point — the three foundational ideas underneath every retrieval pattern built on top of them in this series.

## 8. Pinned Dependency Versions

```txt
numpy==1.26.4
python>=3.11,<3.13
# Production alternative to this teaching implementation:
hnswlib==0.8.0
# (Chroma, used throughout Patterns 9-13, already includes its own
#  production-grade HNSW implementation internally — no separate
#  install needed if using langchain-chroma as this series does.)
```

---

## Series complete

That's all 15 patterns in the Knowledge & Retrieval Patterns series. Quick recap of how they connect, end to end:

- **1-2 (Vector Embeddings, Embedding Generation)**: turning text into meaningful, well-managed vectors.
- **3-5 (Chunking, Semantic Chunking, Recursive Chunking)**: splitting documents into retrieval-sized, coherent pieces.
- **6-7 (Document Parsing, Metadata Extraction)**: getting clean text and structured facts out of real-world source files.
- **8 (Knowledge Graphs)**: representing explicit relationships for multi-hop questions.
- **9-10 (Vector Databases, Hybrid Databases)**: persistent, filterable, dense+sparse storage and search.
- **11 (Graph + Vector Retrieval)**: combining graph traversal with scoped vector search.
- **12 (Re-ranking)**: a precision pass with a cross-encoder.
- **13 (Retrieval Filters)**: hard constraints — access control, recency, scoping.
- **14-15 (Similarity Search, ANN Search)**: the exact math underneath every comparison, and how it's made fast at scale.

Together, these form the full retrieval stack behind DocuMind — and behind essentially any production-grade RAG or knowledge-assistant system.
