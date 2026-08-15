# Pattern 14: Similarity Search

## 1. Introduce the Pattern

Every pattern in this series so far has used "compare two vectors and get a relevance score" as a building block — but always treated it as a one-line utility call. This pattern takes a dedicated, deeper look at **similarity search itself**: the actual distance/similarity metrics (cosine similarity, dot product, Euclidean distance), what each one measures geometrically, why the choice matters, and how it must be matched to the embedding model that produced the vectors.

```
Three ways to compare vector A and vector B:

Cosine similarity:      measures the ANGLE between A and B
                         (ignores magnitude entirely)
                         range: -1 (opposite) to 1 (identical direction)

Dot product:             measures angle AND magnitude together
                         range: unbounded
                         (equivalent to cosine similarity IF vectors
                          are pre-normalized to unit length)

Euclidean distance:      measures straight-line DISTANCE between
                         the two points in vector space
                         range: 0 (identical) to unbounded
                         (smaller = more similar — inverse of the
                          other two, which are "bigger = more similar")
```

## 2. The Problem It Solves

So far, this series has used cosine similarity somewhat by default, without examining *why*, or what changes if a different metric is used. That default choice actually matters, and getting it wrong silently degrades every pattern built on top of it:

- **Using the wrong metric for a given embedding model gives systematically worse results.** Some embedding models are trained and optimized specifically for cosine similarity comparisons; others (particularly certain OpenAI and instruction-tuned models) are trained assuming dot product comparison on normalized vectors — mismatching metric to model doesn't crash anything, it just quietly ranks results worse.
- **Vector magnitude can leak unwanted signal.** Dot product (unlike cosine similarity) is sensitive to vector length, not just direction. If an embedding model happens to produce longer vectors for longer input text (a real, documented tendency in some models), using raw dot product without normalization can systematically bias results toward longer chunks, regardless of actual relevance — a subtle bug that "cosine similarity" naturally sidesteps by design (it explicitly divides out magnitude).
- **Distance metrics behave differently at the extremes**, which matters when setting similarity thresholds (e.g. "only show results above 0.7 cosine similarity") — a threshold tuned for one metric is meaningless if silently applied to another.
- **Every vector database (Pattern 9) requires you to configure which metric its index uses** — getting this wrong at collection-creation time, then discovering it later, typically means re-indexing the entire corpus.

## 3. Realistic Enterprise Scenario

DocuMind's platform team is evaluating whether to switch embedding models — from `nomic-embed-text` to a newer local model — and needs to verify that the vector database's configured similarity metric is still the right choice for the new model, rather than assuming the old configuration transfers automatically. They also want to understand a support ticket: *"search results seem to slightly favor longer documents even when a short FAQ answer is clearly more relevant — why?"* — a symptom that traces directly back to metric/normalization choices.

This file builds a small diagnostic toolkit: implement all three metrics from scratch (for genuine understanding, not just calling a library function), demonstrate the magnitude-sensitivity difference concretely, and show how to verify metric/model compatibility before committing a corpus to an index.

## 4. Architecture / Flow Diagram

```
             Vector A (query)         Vector B (candidate chunk)
                   │                          │
                   └──────────┬───────────────┘
                              ▼
              ┌─────────────────────────────────┐
              │      Choose a similarity metric     │
              └─────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌───────────────┐   ┌───────────────────┐   ┌────────────────────┐
│ Cosine similarity│   │  Dot product        │   │ Euclidean distance   │
│                   │   │                     │   │                      │
│ A·B / (‖A‖‖B‖)   │   │  A·B                │   │  ‖A - B‖              │
│                   │   │                     │   │                      │
│ magnitude-        │   │  magnitude-         │   │ magnitude-            │
│ INVARIANT          │   │  SENSITIVE           │   │ sensitive              │
│                   │   │                     │   │                      │
│ range: [-1, 1]     │   │  range: unbounded    │   │ range: [0, ∞)          │
│ bigger = closer     │   │  bigger = closer     │   │ SMALLER = closer       │
└───────────────┘   └───────────────────┘   └────────────────────┘
        │                     │                     │
        └─────────────────────┼─────────────────────┘
                              ▼
              Must match the metric the vector
              database's index was BUILT with —
              mismatched metric + index = silently
              wrong rankings, not an error
```

## 5. Request-to-Response Walkthrough

1. Two vectors need to be compared — in practice, always a query vector against a stored chunk vector, computed potentially millions of times per query across a whole corpus (which is exactly why Pattern 15's ANN indexing exists — to avoid computing this for every single stored vector).
2. **Cosine similarity** is computed as the dot product of the two vectors divided by the product of their magnitudes — this normalizes away vector length entirely, leaving only the angle between them as the signal. Two vectors pointing in exactly the same direction score 1.0 regardless of whether one is twice as long as the other.
3. **Dot product** is computed directly, with no normalization step — it's cheaper to compute (no division, no magnitude calculation needed at query time if vectors were pre-normalized during ingestion, exactly as Pattern 2's pipeline did), but is sensitive to both angle *and* magnitude.
4. **Euclidean distance** computes the straight-line distance between the two vectors treated as points in space — this is the only one of the three where a *smaller* number means *more* similar, an inversion that matters when writing threshold or sorting logic.
5. Whichever metric is chosen, it must match what the vector database's index was configured with at collection-creation time (Pattern 9) — mismatching doesn't throw an error, it just silently returns a technically-valid but meaningfully wrong ranking.
6. The choice also interacts with whether vectors were normalized during ingestion (Pattern 2 normalized to unit length by design) — once vectors are pre-normalized, dot product and cosine similarity become mathematically identical, letting a system use the cheaper dot product computation while still getting cosine similarity's magnitude-invariance benefit "for free."

## 6. Why This Pattern Is Appropriate

| Metric | Best fit | Watch out for |
|---|---|---|
| **Cosine similarity** | Most embedding models (including `nomic-embed-text`); when vector magnitude carries no meaningful signal | Slightly more compute than dot product on raw (non-normalized) vectors, due to the extra normalization step at comparison time |
| **Dot product** | Same as cosine similarity, but cheaper — *if* vectors are pre-normalized to unit length at ingestion (Pattern 2) | On non-normalized vectors, silently biases toward longer/higher-magnitude vectors — a real, hard-to-notice bug |
| **Euclidean distance** | Some specialized embedding spaces where absolute position (not just direction) carries meaning; certain clustering algorithms | Sorting logic must be inverted (smaller = better) — a common source of "search results are exactly backwards" bugs when adapted carelessly from cosine-based code |

**Recommendation for this series' stack**: `nomic-embed-text` and most modern sentence-embedding models are trained and evaluated using cosine similarity — that's the metric this series has used throughout, and it's the right default absent a specific reason to deviate. The implementation below makes the trade-offs concrete rather than asking you to take that recommendation on faith.

## 7. Production-Quality Python Implementation

```python
"""
similarity_search_pipeline.py

A from-scratch, side-by-side implementation of the three core
similarity/distance metrics, plus a diagnostic toolkit for verifying
metric/model compatibility and demonstrating the magnitude-sensitivity
difference that caused the "favors longer documents" symptom described
in this pattern's scenario.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import List, Tuple

import numpy as np
from langchain_ollama import OllamaEmbeddings

EMBEDDING_MODEL = "nomic-embed-text"


# --------------------------------------------------------------------------
# The three metrics, implemented from scratch for full transparency
# --------------------------------------------------------------------------

def cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
    """Angle-only comparison. Magnitude-invariant by construction —
    this is what the division by norms accomplishes."""
    denom = np.linalg.norm(a) * np.linalg.norm(b)
    if denom == 0:
        return 0.0
    return float(np.dot(a, b) / denom)


def dot_product(a: np.ndarray, b: np.ndarray) -> float:
    """Angle AND magnitude combined. Cheap (no normalization step),
    but only equivalent to cosine similarity if a and b are already
    unit-length vectors."""
    return float(np.dot(a, b))


def euclidean_distance(a: np.ndarray, b: np.ndarray) -> float:
    """Straight-line distance. Note: SMALLER means MORE similar —
    opposite sorting direction from the other two metrics."""
    return float(np.linalg.norm(a - b))


def normalize(v: np.ndarray) -> np.ndarray:
    norm = np.linalg.norm(v)
    return v / norm if norm != 0 else v


# --------------------------------------------------------------------------
# Diagnostic: demonstrate magnitude sensitivity concretely
# --------------------------------------------------------------------------

def demonstrate_magnitude_sensitivity() -> None:
    """Constructs a controlled example: two vectors pointing in the
    EXACT SAME direction but with different magnitudes, to show
    concretely how dot product vs. cosine similarity diverge."""
    a = np.array([1.0, 2.0, 3.0])
    b_same_direction_short = a * 1.0      # identical vector
    b_same_direction_long = a * 3.0       # same direction, 3x longer

    print("Vector A:", a)
    print("Vector B (same direction, magnitude x1):", b_same_direction_short)
    print("Vector B (same direction, magnitude x3):", b_same_direction_long)
    print()

    print(f"{'Metric':<20}{'B (x1)':<15}{'B (x3)':<15}{'Changed?'}")
    cos_1 = cosine_similarity(a, b_same_direction_short)
    cos_3 = cosine_similarity(a, b_same_direction_long)
    print(f"{'Cosine similarity':<20}{cos_1:<15.4f}{cos_3:<15.4f}{'NO — angle-only' if abs(cos_1 - cos_3) < 1e-9 else 'unexpectedly changed'}")

    dot_1 = dot_product(a, b_same_direction_short)
    dot_3 = dot_product(a, b_same_direction_long)
    print(f"{'Dot product':<20}{dot_1:<15.4f}{dot_3:<15.4f}{'YES — magnitude leaks in' if dot_1 != dot_3 else 'unchanged'}")

    euc_1 = euclidean_distance(a, b_same_direction_short)
    euc_3 = euclidean_distance(a, b_same_direction_long)
    print(f"{'Euclidean distance':<20}{euc_1:<15.4f}{euc_3:<15.4f}{'YES — magnitude leaks in' if euc_1 != euc_3 else 'unchanged'}")

    print("\nThis is exactly the mechanism behind 'search favors longer "
          "documents': if an embedding model tends to produce longer-magnitude "
          "vectors for longer input text, and dot product is used on "
          "NON-normalized vectors, longer documents get an artificial similarity "
          "boost that has nothing to do with actual relevance.")


# --------------------------------------------------------------------------
# Verifying dot product == cosine similarity after normalization
# --------------------------------------------------------------------------

def demonstrate_normalization_equivalence() -> None:
    rng = np.random.default_rng(42)
    a = rng.normal(size=8)
    b = rng.normal(size=8)

    cos = cosine_similarity(a, b)
    dot_raw = dot_product(a, b)
    dot_normalized = dot_product(normalize(a), normalize(b))

    print(f"Cosine similarity (raw vectors):      {cos:.6f}")
    print(f"Dot product (raw vectors):             {dot_raw:.6f}   <- very different from cosine!")
    print(f"Dot product (pre-normalized vectors):  {dot_normalized:.6f}   <- matches cosine similarity")


# --------------------------------------------------------------------------
# Applied: comparing a query against real chunk embeddings, all 3 metrics
# --------------------------------------------------------------------------

@dataclass
class MetricComparison:
    text: str
    cosine: float
    dot: float
    euclidean: float


def compare_metrics_on_real_chunks() -> None:
    embedder = OllamaEmbeddings(model=EMBEDDING_MODEL)

    query = "how do I reset my password?"
    chunks = [
        "To reset your password, go to Settings > Security and click 'Forgot password'.",
        "Database failover runbook: promote the standby replica using pg_ctl.",
        "Password reset links expire after 5 minutes for security reasons. If yours "
        "expired, request a new one from the login page and check your spam folder "
        "if it doesn't arrive within a couple of minutes, since our mail provider "
        "occasionally routes automated emails there.",  # deliberately longer chunk
    ]

    query_vec = np.array(embedder.embed_query(query))
    chunk_vecs = [np.array(v) for v in embedder.embed_documents(chunks)]

    results = []
    for text, vec in zip(chunks, chunk_vecs):
        results.append(MetricComparison(
            text=text,
            cosine=cosine_similarity(query_vec, vec),
            dot=dot_product(query_vec, vec),
            euclidean=euclidean_distance(query_vec, vec),
        ))

    print(f"{'Rank (cosine)':<15}{'Cosine':<10}{'Dot':<12}{'Euclidean':<12}Text")
    for i, r in enumerate(sorted(results, key=lambda x: x.cosine, reverse=True), start=1):
        print(f"{i:<15}{r.cosine:<10.4f}{r.dot:<12.4f}{r.euclidean:<12.4f}{r.text[:50]}...")


# --------------------------------------------------------------------------
# Demo
# --------------------------------------------------------------------------

def run_demo() -> None:
    print("=== Part 1: Magnitude sensitivity, controlled example ===\n")
    demonstrate_magnitude_sensitivity()

    print("\n=== Part 2: Normalization makes dot product == cosine similarity ===\n")
    demonstrate_normalization_equivalence()

    print("\n=== Part 3: All three metrics applied to real embedded chunks ===\n")
    compare_metrics_on_real_chunks()


if __name__ == "__main__":
    run_demo()
```

### Expected output (shape)

```
=== Part 1: Magnitude sensitivity, controlled example ===

Vector A: [1. 2. 3.]
Vector B (same direction, magnitude x1): [1. 2. 3.]
Vector B (same direction, magnitude x3): [3. 6. 9.]

Metric              B (x1)         B (x3)         Changed?
Cosine similarity   1.0000         1.0000         NO — angle-only
Dot product         14.0000        42.0000        YES — magnitude leaks in
Euclidean distance  0.0000         7.4833         YES — magnitude leaks in

This is exactly the mechanism behind 'search favors longer documents': ...

=== Part 2: Normalization makes dot product == cosine similarity ===

Cosine similarity (raw vectors):      0.123456
Dot product (raw vectors):             0.987654   <- very different from cosine!
Dot product (pre-normalized vectors):  0.123456   <- matches cosine similarity

=== Part 3: All three metrics applied to real embedded chunks ===

Rank (cosine)  Cosine    Dot         Euclidean   Text
1              0.8123    12.4501     0.6123      To reset your password, go to Settings > Sec...
2              0.7654    18.2201     0.6845      Password reset links expire after 5 minutes f...
3              0.4102    9.1230      1.0854      Database failover runbook: promote the standb...
```

Part 1 is the concrete proof: identical direction, different magnitude — cosine similarity stays perfectly constant while dot product and Euclidean distance both change substantially, purely due to magnitude, with zero change in actual relevance. Part 3 (illustrative — real numbers vary by model) hints at the real-world version of that bug: the longer, more verbose password-reset chunk can score competitively or even higher on raw dot product than the shorter, more directly relevant one, purely because it's a longer chunk with (often) a longer embedding magnitude — exactly the support ticket symptom from this pattern's scenario.

### Key production notes

- **Always check what metric your embedding model was evaluated with** — this is typically documented in the model's card/README. `nomic-embed-text` and most sentence-embedding models are cosine-similarity-native; using a different metric without normalization is a subtle, hard-to-detect quality regression, not a crash.
- **Normalize vectors once, at ingestion time (Pattern 2), not per query** — this makes dot product and cosine similarity mathematically equivalent, letting a production vector database use the cheaper dot product computation internally while still getting cosine similarity's magnitude-invariance guarantee.
- **The vector database's index metric configuration is typically fixed at collection-creation time** — changing it later usually requires rebuilding the entire index from scratch (Pattern 9), so get this decision right before ingesting a large corpus, not after.
- **Euclidean distance's inverted sort order is a common source of bugs** when code is adapted from a cosine-based implementation without updating the sort direction — a search that returns the *least* relevant results first, silently, is a classic symptom of exactly this mistake.
- **This pattern is the conceptual foundation for Pattern 15 (ANN Search)** — approximate nearest neighbor algorithms are built specifically to avoid computing one of these exact metrics against every single stored vector; understanding what's being approximated is what makes Pattern 15's trade-offs make sense.

## 8. Pinned Dependency Versions

```txt
numpy==1.26.4
langchain-ollama==0.2.3
langchain-core==0.3.29
python>=3.11,<3.13
```

```bash
ollama pull nomic-embed-text
```

---

**Next:** say `next` and I'll build `15_ANN_Search.md` — the final pattern in this series: how vector databases achieve fast similarity search at scale by approximating, rather than exhaustively computing, these exact metrics.
