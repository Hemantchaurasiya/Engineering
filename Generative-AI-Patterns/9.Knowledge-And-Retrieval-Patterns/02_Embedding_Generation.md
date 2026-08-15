# Pattern 2: Embedding Generation

## 1. Introduce the Pattern

Pattern 1 treated the embedding model as a black box: text goes in, a vector comes out. **Embedding generation** is about what happens *around* that black box in a real system — how the model actually produces those numbers, which model to pick, how to configure it correctly, and how to generate embeddings reliably and efficiently at scale.

This pattern covers the operational and architectural decisions a production team has to make:

- How are embedding models trained, at a level that helps you pick the right one (not at research-paper depth)?
- What's the trade-off between vector **dimensionality** (384 vs 768 vs 1536+)?
- What's the difference between a **general-purpose** embedding model and an **instruction-tuned** one?
- Why do some models want you to **normalize** vectors, and why does that matter for cosine similarity?
- How do you generate embeddings for **hundreds of thousands of documents** without your pipeline falling over — retries, rate limits, parallelism, idempotency?
- Should a production system ever use **more than one embedding model**?

## 2. The Problem It Solves

Pattern 1 showed embeddings *working*. But "it works in a demo with 4 documents" is very different from "it works reliably for 200,000 documents, refreshed nightly, without duplicate work, without silently corrupting the vector space by mixing models."

Specific problems that show up the moment you leave the demo:

- **Model drift risk**: if someone swaps `nomic-embed-text` for a different model mid-project without re-embedding *everything*, similarity search silently breaks — old and new vectors are no longer comparable, but nothing throws an error. It just quietly returns wrong results.
- **Throughput**: embedding one document at a time over HTTP to a local Ollama server is slow. At scale you need batching, concurrency, and backpressure control.
- **Cost/latency trade-off**: bigger embedding models (higher dimensionality) are usually more accurate but slower and more expensive to store and search. Teams need a principled way to choose.
- **Failure handling**: Ollama (or any embedding provider) can time out, restart, or return a partial batch. A production ingestion job needs retries and idempotency so a crash halfway through doesn't leave the corpus in an inconsistent state.
- **Reproducibility**: if you re-run ingestion, do you get the exact same vectors? Do you accidentally re-embed unchanged documents and waste compute?

## 3. Realistic Enterprise Scenario

DocuMind's content team pushes updates to the internal wiki constantly — new runbooks, edited HR policies, deprecated pages removed. The platform team needs an **ingestion pipeline** that:

1. Runs nightly (or on-demand via webhook when a wiki page changes).
2. Generates embeddings only for **new or changed** content — not the whole corpus every time.
3. Tracks **which embedding model + version** produced each vector, so if the model is ever upgraded, DocuMind knows exactly which vectors are stale and need re-embedding.
4. Handles Ollama being temporarily unavailable without losing work or corrupting state.
5. Normalizes vectors consistently so downstream similarity math (Pattern 14) behaves correctly.

This file builds that ingestion pipeline as a standalone, production-quality component.

## 4. Architecture / Flow Diagram

```
                     ┌────────────────────────┐
                     │   Wiki / Source Docs     │
                     │  (new / changed pages)   │
                     └────────────┬─────────────┘
                                  │
                                  ▼
                     ┌────────────────────────┐
                     │  Change Detector         │
                     │  (hash content, compare  │
                     │   against last known     │
                     │   hash in the registry)  │
                     └────────────┬─────────────┘
                       unchanged  │  changed / new
                     ┌────────────┴─────────────┐
                     │                           │
                skip (no-op)              ┌──────▼───────┐
                                           │  Batcher      │
                                           │  (groups N    │
                                           │   texts per   │
                                           │   API call)   │
                                           └──────┬────────┘
                                                  │
                                     ┌────────────▼─────────────┐
                                     │  Ollama embed call         │
                                     │  with retry + backoff      │
                                     │  model=nomic-embed-text    │
                                     └────────────┬─────────────┘
                                                  │
                                     ┌────────────▼─────────────┐
                                     │  Normalize vector          │
                                     │  (unit length, L2 norm)    │
                                     └────────────┬─────────────┘
                                                  │
                                     ┌────────────▼─────────────┐
                                     │  Embedding Registry        │
                                     │  {doc_id, content_hash,    │
                                     │   model, model_version,    │
                                     │   vector, created_at}      │
                                     └────────────────────────────┘
```

The **change detector** and the **registry** are the two pieces that turn "call an embedding model" into an actual production pattern — they're what make re-runs cheap and safe.

## 5. Request-to-Response Walkthrough

1. A content update triggers ingestion for a batch of document IDs (or the nightly job scans all documents).
2. For each document, the pipeline computes a **content hash** (SHA-256 of the normalized text) and looks it up in the registry.
3. If the hash matches what's stored **and** the model/version matches, the document is **skipped** — no wasted embedding calls.
4. Otherwise, the document is added to the current batch.
5. Once a batch reaches `BATCH_SIZE` (or the queue is drained), the batch is sent to Ollama's embedding endpoint via `OllamaEmbeddings.embed_documents()`.
6. If the call fails (timeout, connection error), the pipeline retries with exponential backoff up to a max attempt count, then logs and quarantines the failed batch for manual review rather than crashing the whole run.
7. Each returned vector is **L2-normalized** (scaled to unit length) so that later cosine similarity comparisons are numerically stable and, as a bonus, cosine similarity becomes equivalent to a simple dot product — cheaper to compute at scale.
8. The vector, along with the content hash, model name, and model version, is written to the registry, replacing any stale entry for that document.
9. At the end of the run, the pipeline reports how many documents were embedded, skipped, and failed.

## 6. Why This Pattern Is Appropriate

| Naive approach | Problem | This pattern's fix |
|---|---|---|
| Re-embed every document every run | Wastes compute/time linearly with corpus size | Content-hash change detection — only changed docs are re-embedded |
| One embedding call per document | Slow, high overhead | Batching |
| No retry logic | A single Ollama hiccup kills the whole nightly job | Retry with exponential backoff, quarantine instead of crash |
| No model/version tracking | Silent vector-space corruption when the model changes | Registry stores model + version per vector; mismatches are detectable |
| Raw, un-normalized vectors | Cosine similarity math is more expensive and, in some model families, less stable | L2 normalization at generation time |

This pattern is essentially "the operational discipline around Pattern 1." Skipping it doesn't mean you don't need it — it means you find out you needed it during a 2am incident when search results quietly become nonsense after a model upgrade.

## 7. Production-Quality Python Implementation

```python
"""
embedding_generation_pipeline.py

Production-quality embedding generation pipeline for DocuMind.

Key production concerns handled:
  - Change detection via content hashing (skip unchanged docs)
  - Model/version tracking (detect stale vectors after a model upgrade)
  - Batching with retry + exponential backoff
  - L2 normalization of output vectors
  - A simple in-memory "registry" standing in for a real metadata store
    (in production this would be a Postgres table or the vector DB's
    own metadata fields — see Pattern 9)
"""

from __future__ import annotations

import hashlib
import logging
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Dict, List, Optional

import numpy as np
from langchain_ollama import OllamaEmbeddings

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("documind.embedding_generation")


# --------------------------------------------------------------------------
# Config
# --------------------------------------------------------------------------

EMBEDDING_MODEL = "nomic-embed-text"
MODEL_VERSION = "nomic-embed-text-v1.5"   # track explicitly; don't infer from model name alone
BATCH_SIZE = 32
MAX_RETRIES = 4
BASE_BACKOFF_SECONDS = 1.0


@dataclass
class SourceDocument:
    doc_id: str
    text: str
    metadata: dict = field(default_factory=dict)


@dataclass
class RegistryEntry:
    doc_id: str
    content_hash: str
    model: str
    model_version: str
    vector: np.ndarray
    created_at: str


@dataclass
class IngestionReport:
    embedded: int = 0
    skipped_unchanged: int = 0
    failed: List[str] = field(default_factory=list)


# --------------------------------------------------------------------------
# Registry (stand-in for a real metadata store / vector DB metadata)
# --------------------------------------------------------------------------

class EmbeddingRegistry:
    """Tracks which vector was generated from which content hash + model
    version, so re-runs can skip unchanged documents and model upgrades
    can be detected explicitly rather than silently."""

    def __init__(self) -> None:
        self._store: Dict[str, RegistryEntry] = {}

    def get(self, doc_id: str) -> Optional[RegistryEntry]:
        return self._store.get(doc_id)

    def is_up_to_date(self, doc_id: str, content_hash: str, model: str, model_version: str) -> bool:
        entry = self.get(doc_id)
        if entry is None:
            return False
        return (
            entry.content_hash == content_hash
            and entry.model == model
            and entry.model_version == model_version
        )

    def upsert(self, entry: RegistryEntry) -> None:
        self._store[entry.doc_id] = entry

    def stale_for_model_upgrade(self, new_model: str, new_version: str) -> List[str]:
        """Given a new target model/version, list doc_ids whose stored
        vectors were generated with a different model — these need
        re-embedding before they can be trusted in similarity search."""
        return [
            doc_id for doc_id, entry in self._store.items()
            if entry.model != new_model or entry.model_version != new_version
        ]


# --------------------------------------------------------------------------
# Pipeline
# --------------------------------------------------------------------------

class EmbeddingGenerationPipeline:
    def __init__(
        self,
        registry: EmbeddingRegistry,
        model: str = EMBEDDING_MODEL,
        model_version: str = MODEL_VERSION,
        batch_size: int = BATCH_SIZE,
    ):
        self._embedder = OllamaEmbeddings(model=model)
        self.model = model
        self.model_version = model_version
        self.batch_size = batch_size
        self.registry = registry

    @staticmethod
    def _content_hash(text: str) -> str:
        normalized = " ".join(text.strip().split())  # collapse whitespace before hashing
        return hashlib.sha256(normalized.encode("utf-8")).hexdigest()

    @staticmethod
    def _normalize(vector: List[float]) -> np.ndarray:
        arr = np.array(vector, dtype=np.float32)
        norm = np.linalg.norm(arr)
        if norm == 0:
            logger.warning("Zero-norm vector encountered; leaving unnormalized.")
            return arr
        return arr / norm

    def _embed_batch_with_retry(self, texts: List[str]) -> List[List[float]]:
        attempt = 0
        while True:
            try:
                return self._embedder.embed_documents(texts)
            except Exception as exc:  # network errors, Ollama restarts, etc.
                attempt += 1
                if attempt > MAX_RETRIES:
                    logger.error("Batch failed after %d attempts: %s", MAX_RETRIES, exc)
                    raise
                backoff = BASE_BACKOFF_SECONDS * (2 ** (attempt - 1))
                logger.warning(
                    "Embedding batch failed (attempt %d/%d): %s — retrying in %.1fs",
                    attempt, MAX_RETRIES, exc, backoff,
                )
                time.sleep(backoff)

    def run(self, documents: List[SourceDocument]) -> IngestionReport:
        report = IngestionReport()
        pending: List[SourceDocument] = []
        pending_hashes: Dict[str, str] = {}

        for doc in documents:
            content_hash = self._content_hash(doc.text)
            if self.registry.is_up_to_date(doc.doc_id, content_hash, self.model, self.model_version):
                report.skipped_unchanged += 1
                continue
            pending.append(doc)
            pending_hashes[doc.doc_id] = content_hash

        logger.info(
            "Ingestion plan: %d unchanged (skipped), %d to embed",
            report.skipped_unchanged, len(pending),
        )

        for start in range(0, len(pending), self.batch_size):
            batch = pending[start:start + self.batch_size]
            texts = [d.text for d in batch]

            try:
                vectors = self._embed_batch_with_retry(texts)
            except Exception:
                report.failed.extend(d.doc_id for d in batch)
                logger.error("Quarantining %d docs after repeated failure", len(batch))
                continue

            for doc, raw_vector in zip(batch, vectors):
                normalized = self._normalize(raw_vector)
                entry = RegistryEntry(
                    doc_id=doc.doc_id,
                    content_hash=pending_hashes[doc.doc_id],
                    model=self.model,
                    model_version=self.model_version,
                    vector=normalized,
                    created_at=datetime.now(timezone.utc).isoformat(),
                )
                self.registry.upsert(entry)
                report.embedded += 1

        return report


# --------------------------------------------------------------------------
# Demo
# --------------------------------------------------------------------------

def run_demo() -> None:
    registry = EmbeddingRegistry()
    pipeline = EmbeddingGenerationPipeline(registry)

    docs = [
        SourceDocument("hr-001", "Notice period remote work policy: employees may work "
                                  "remotely with manager approval during their notice period.",
                        {"team": "HR"}),
        SourceDocument("db-014", "Database failover runbook: promote the standby replica, "
                                  "then update the connection string in the config service.",
                        {"team": "Platform"}),
        SourceDocument("it-003", "Password reset steps: Settings > Security > Forgot password. "
                                  "Reset link arrives within 5 minutes.",
                        {"team": "IT"}),
    ]

    print("=== First run (all new) ===")
    report_1 = pipeline.run(docs)
    print(f"embedded={report_1.embedded} skipped={report_1.skipped_unchanged} failed={report_1.failed}")

    print("\n=== Second run, no changes (should skip everything) ===")
    report_2 = pipeline.run(docs)
    print(f"embedded={report_2.embedded} skipped={report_2.skipped_unchanged} failed={report_2.failed}")

    print("\n=== Third run, one doc edited (should embed only that one) ===")
    docs[1] = SourceDocument(
        "db-014",
        "Database failover runbook (v2): promote the standby replica using "
        "`pg_ctl promote`, then update the connection string, then notify #db-oncall.",
        {"team": "Platform"},
    )
    report_3 = pipeline.run(docs)
    print(f"embedded={report_3.embedded} skipped={report_3.skipped_unchanged} failed={report_3.failed}")

    print("\n=== Simulating a model upgrade check ===")
    stale = registry.stale_for_model_upgrade("nomic-embed-text", "nomic-embed-text-v2.0")
    print(f"Docs that would need re-embedding if upgrading to v2.0: {stale}")


if __name__ == "__main__":
    run_demo()
```

### Expected output (shape, not exact numbers)

```
=== First run (all new) ===
embedded=3 skipped=0 failed=[]

=== Second run, no changes (should skip everything) ===
embedded=0 skipped=3 failed=[]

=== Third run, one doc edited (should embed only that one) ===
embedded=1 skipped=2 failed=[]

=== Simulating a model upgrade check ===
Docs that would need re-embedding if upgrading to v2.0: ['hr-001', 'db-014', 'it-003']
```

The second run proves change detection is working (0 embedding calls made). The third run proves it's granular — editing one document doesn't force a full re-embed of the corpus.

### Key production notes

- **Content hashing must normalize whitespace/formatting first** — otherwise a document that's semantically identical but re-saved with different line endings would be treated as "changed" and wastefully re-embedded.
- **Track model *and* version, not just model name** — providers frequently update a model's weights under the same name (e.g. a "latest" tag). Pin and record an explicit version string.
- **Normalize vectors once, at generation time**, not on every query — this pushes the cost to ingestion (which happens rarely) instead of query time (which happens constantly).
- **Quarantine, don't crash** — a batch failure after retries shouldn't take down the whole nightly job; log it, keep going, and surface it in the report for follow-up.
- **In production**, `EmbeddingRegistry` would be a real table (e.g. Postgres) or the metadata layer of the vector database itself (Pattern 9 shows this with Chroma).

## 8. Pinned Dependency Versions

```txt
langchain-ollama==0.2.3
langchain-core==0.3.29
numpy==1.26.4
python>=3.11,<3.13
```

```bash
ollama pull nomic-embed-text
```

---

**Next:** say `next` and I'll build `03_Chunking.md` — how to split long documents into retrieval-friendly pieces, and why chunk size/overlap choices make or break retrieval quality.
