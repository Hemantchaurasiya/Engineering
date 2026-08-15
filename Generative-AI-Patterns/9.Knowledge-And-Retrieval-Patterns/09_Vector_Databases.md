# Pattern 9: Vector Databases

## 1. Introduce the Pattern

A **vector database** is a database purpose-built to store embeddings (Pattern 1) alongside their source text and metadata (Pattern 7), and to answer "find the N most similar vectors to this query vector" efficiently — even across millions of entries. It replaces the brute-force, in-memory Python approach used in Patterns 1, 2, and 4 (`for chunk in chunks: compute cosine_similarity`) with a persistent, indexed, queryable store.

```
Brute-force in-memory (Patterns 1/2/4)          Vector database (this pattern)
─────────────────────────────────────          ──────────────────────────────
for chunk in all_chunks:                        collection.query(
    score = cosine_sim(query_vec, chunk.vec)        query_embeddings=[query_vec],
sort by score, take top_k                           n_results=5,
                                                     where={"team": "Platform"}
✗ Lost when the process restarts               )
✗ O(n) scan — slow past a few thousand chunks   ✓ Persisted to disk
✗ No metadata filtering built in                ✓ Indexed for fast search (Pattern 15: ANN)
                                                 ✓ Metadata filtering built in (Pattern 13)
```

This pattern is the natural convergence point of the series so far: chunks (3–5), their embeddings (1–2), and their metadata (7) all get stored together in one place, ready for real retrieval.

## 2. The Problem It Solves

The brute-force approach used earlier in this series for teaching purposes has real limitations the moment you leave a demo:

- **No persistence**: everything lived in a Python list in memory. Restart the process, lose the entire corpus. A production system needs embeddings to survive restarts, deployments, and crashes.
- **Doesn't scale**: brute-force cosine similarity is O(n) — compare the query against *every single* stored vector. Fine for 4 documents, painfully slow for 500,000 chunks. (Pattern 15 covers *how* vector databases solve this internally with ANN indexes; this pattern is about using one.)
- **No built-in metadata filtering**: Pattern 7 produced rich metadata (`team`, `access_level`, `last_updated`), but the brute-force `most_similar()` from Pattern 1 has no mechanism to say "only consider chunks where `team == 'Platform'`" — you'd have to bolt that on manually, inefficiently, by filtering the whole list before scoring.
- **No update/delete story**: when a document changes (Pattern 2's change-detection scenario), you need to be able to update or remove its old vectors, not just append new ones — otherwise stale content stays searchable forever.
- **Concurrency**: a real system has many chunks being written (ingestion) while queries are being read (user questions) simultaneously — an in-memory Python list isn't built for that.

A vector database handles all of this: persistence, efficient similarity search (via ANN indexing), metadata filtering, and update/delete, as one integrated system.

## 3. Realistic Enterprise Scenario

DocuMind's corpus has grown past the "a few example documents" stage from earlier patterns — thousands of runbooks, wiki pages, and policies, ingested nightly, queried constantly by employees during business hours. The system needs to:

1. **Persist** all chunk embeddings + metadata across deployments (the ingestion pipeline from Pattern 2 shouldn't need to re-embed everything every time the service restarts).
2. **Query fast**, filtered by metadata — e.g. "find chunks similar to this query, but only ones with `access_level` the current user is allowed to see" (tying directly into Pattern 7's extracted metadata and Pattern 13's filters).
3. **Update in place** when a document changes, without leaving orphaned stale vectors behind.
4. Run **locally**, without requiring a separate hosted infrastructure team — a reasonable fit for DocuMind's scale is an embedded, file-backed vector database rather than a large distributed cluster.

This file builds that storage layer using **Chroma**, the vector database in this series' fixed tech stack.

## 4. Architecture / Flow Diagram

```
┌────────────────────────┐
│  Chunks + metadata        │  (from Patterns 3-7)
│  {text, doc_id, team,     │
│   access_level, ...}      │
└──────────────┬───────────┘
               │
               ▼
   ┌────────────────────────────────┐
   │  OllamaEmbeddings                 │  ← Pattern 1/2
   │  (embed each chunk's text)        │
   └──────────────┬───────────────┘
                  │
                  ▼
   ┌────────────────────────────────┐
   │        Chroma Collection           │
   │  ┌───────────────────────────┐  │
   │  │ id: chunk-hr-001-0          │  │
   │  │ embedding: [0.12,-0.44,..]  │  │
   │  │ document: "Notice period.." │  │
   │  │ metadata: {team:"HR",       │  │
   │  │  access_level:"all_employees│  │
   │  │  doc_id:"hr-001", ...}      │  │
   │  └───────────────────────────┘  │
   │              (persisted to        │
   │               local disk)         │
   └──────────────┬───────────────┘
                  │
        ┌─────────┴──────────┐
        │                    │
   query time             update/delete time
        │                    │
        ▼                    ▼
┌───────────────────┐  ┌──────────────────────┐
│ collection.query()  │  │ collection.update()    │
│  embed query,        │  │  (doc content changed)  │
│  similarity search,  │  │ collection.delete()     │
│  + metadata filter   │  │  (doc removed/deprecated)│
└───────────────────┘  └──────────────────────┘
```

## 5. Request-to-Response Walkthrough

1. **Ingestion**: for a batch of chunks (each with text + metadata from Patterns 3–7), the pipeline calls `collection.add()`, passing the chunk text (Chroma can embed internally via a configured embedding function, or accept pre-computed vectors — this implementation uses `OllamaEmbeddings` explicitly so behavior is consistent with the rest of the series), a unique ID per chunk, and the metadata dictionary.
2. Chroma **persists** these to its local storage (a `PersistentClient` backed by files on disk), so the corpus survives process restarts.
3. **Query time**: a user's question is embedded (Pattern 1), then passed to `collection.query()` along with an optional `where` clause built from the user's allowed `access_level`s and any explicit scope they requested (e.g. "only Platform team docs").
4. Chroma returns the top-N most similar chunks **that also satisfy the metadata filter**, each with its text, metadata, and distance score.
5. **Update flow**: when Pattern 2's change-detection determines a document changed, the pipeline calls `collection.update()` (or delete + re-add) using the same chunk IDs, so old vectors don't linger stale in the corpus.
6. **Delete flow**: when a document is removed or deprecated, its chunk IDs are deleted from the collection entirely — important for compliance (e.g. removing a policy document should also remove its searchable trace).

## 6. Why This Pattern Is Appropriate

| Storage choice | Fit for DocuMind |
|---|---|
| Brute-force in-memory Python list (Patterns 1/2/4) | Great for learning/small demos; wrong for a real corpus — no persistence, no filtering, O(n) search |
| Raw SQL database with a vector column, no ANN index | Persistence ✓, but similarity search either falls back to a full table scan (slow) or needs a vector extension (e.g. `pgvector`) to get real ANN performance |
| A large distributed vector DB (e.g. a managed cloud vector service) | Overkill for DocuMind's internal-tool scale; adds hosted infrastructure dependency this series' local-first tech stack deliberately avoids |
| **Chroma (this pattern)**: embedded, file-backed, has metadata filtering and ANN indexing built in | Right-sized for DocuMind: persistent, fast, runs locally with `langchain-chroma`, integrates cleanly with the metadata from Pattern 7 |

Chroma is a good default for small-to-medium production workloads (up to roughly low millions of vectors on a single node) precisely because it bundles persistence, ANN search, and metadata filtering into one embedded library with no separate server to operate. Pattern 10 (Hybrid Databases) and Pattern 15 (ANN Search) build directly on the same Chroma collection introduced here.

## 7. Production-Quality Python Implementation

```python
"""
vector_database_pipeline.py

Production-quality vector database layer for DocuMind, using Chroma
(via langchain-chroma) as the persistent, indexed store for chunk
embeddings + metadata, replacing the brute-force in-memory approach
used in earlier patterns.
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Optional

from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_ollama import OllamaEmbeddings

EMBEDDING_MODEL = "nomic-embed-text"
PERSIST_DIR = "./documind_chroma_store"
COLLECTION_NAME = "documind_knowledge_base"


@dataclass
class ChunkRecord:
    text: str
    doc_id: str
    chunk_index: int
    metadata: dict = field(default_factory=dict)

    @property
    def chunk_id(self) -> str:
        return f"{self.doc_id}::chunk-{self.chunk_index}"


class VectorStore:
    """Wraps a Chroma collection with DocuMind-specific ingestion,
    update, delete, and access-aware query operations."""

    def __init__(self, persist_dir: str = PERSIST_DIR, collection_name: str = COLLECTION_NAME):
        Path(persist_dir).mkdir(parents=True, exist_ok=True)
        self._embeddings = OllamaEmbeddings(model=EMBEDDING_MODEL)
        self._store = Chroma(
            collection_name=collection_name,
            embedding_function=self._embeddings,
            persist_directory=persist_dir,
        )

    # ---- ingestion --------------------------------------------------

    def add_chunks(self, chunks: List[ChunkRecord]) -> None:
        if not chunks:
            return
        documents = [
            Document(page_content=c.text, metadata={**c.metadata, "doc_id": c.doc_id,
                                                      "chunk_index": c.chunk_index})
            for c in chunks
        ]
        ids = [c.chunk_id for c in chunks]
        self._store.add_documents(documents=documents, ids=ids)

    # ---- update / delete (ties into Pattern 2's change detection) ----

    def replace_document_chunks(self, doc_id: str, new_chunks: List[ChunkRecord]) -> None:
        """When a document changes, remove all its old chunks and add
        the freshly re-chunked/re-embedded ones — avoids orphaned stale
        vectors if the new chunk count differs from the old one."""
        self.delete_document(doc_id)
        self.add_chunks(new_chunks)

    def delete_document(self, doc_id: str) -> None:
        existing = self._store.get(where={"doc_id": doc_id})
        ids_to_delete = existing.get("ids", [])
        if ids_to_delete:
            self._store.delete(ids=ids_to_delete)

    # ---- query --------------------------------------------------------

    def search(
        self,
        query: str,
        top_k: int = 5,
        access_levels: Optional[List[str]] = None,
        team_filter: Optional[str] = None,
    ) -> List[Document]:
        """Similarity search with an optional metadata filter. Chroma's
        `where` clause uses MongoDB-style operators; this builds one
        from DocuMind's access-control and scoping needs. (Pattern 13
        goes much deeper into filter design generally — this is a
        first, direct application of it against the store built here.)"""
        where_clauses = []
        if access_levels:
            where_clauses.append({"access_level": {"$in": access_levels}})
        if team_filter:
            where_clauses.append({"team": team_filter})

        where = None
        if len(where_clauses) == 1:
            where = where_clauses[0]
        elif len(where_clauses) > 1:
            where = {"$and": where_clauses}

        return self._store.similarity_search(query, k=top_k, filter=where)

    def search_with_scores(self, query: str, top_k: int = 5, **kwargs) -> List[tuple]:
        return self._store.similarity_search_with_relevance_scores(query, k=top_k)

    def count(self) -> int:
        return self._store._collection.count()


# --------------------------------------------------------------------------
# Demo
# --------------------------------------------------------------------------

def build_sample_chunks() -> List[ChunkRecord]:
    return [
        ChunkRecord(
            text="To reset your password, go to Settings > Security and click "
                 "'Forgot password'. A reset link is emailed within 5 minutes.",
            doc_id="it-faq-001", chunk_index=0,
            metadata={"team": "IT", "access_level": "all_employees", "doc_type": "faq"},
        ),
        ChunkRecord(
            text="Database failover runbook: promote the standby replica using "
                 "`pg_ctl promote`, then update the connection string.",
            doc_id="db-runbook-014", chunk_index=0,
            metadata={"team": "Platform", "access_level": "team_only", "doc_type": "runbook"},
        ),
        ChunkRecord(
            text="Executive compensation bands are reviewed annually by the "
                 "board compensation committee.",
            doc_id="hr-exec-comp-002", chunk_index=0,
            metadata={"team": "HR", "access_level": "managers_only", "doc_type": "policy"},
        ),
    ]


def run_demo() -> None:
    store = VectorStore(persist_dir="./demo_chroma_store")

    chunks = build_sample_chunks()
    store.add_chunks(chunks)
    print(f"Stored {store.count()} chunks in the persistent Chroma collection.\n")

    print("=== Query as a regular employee (no managers_only access) ===")
    results = store.search(
        "how do I recover my account access?",
        top_k=3,
        access_levels=["all_employees", "team_only"],
    )
    for doc in results:
        print(f"  [{doc.metadata['team']}] {doc.page_content[:70]}...")

    print("\n=== Query scoped to Platform team only ===")
    results = store.search(
        "how do I fail over the database?",
        top_k=3,
        team_filter="Platform",
    )
    for doc in results:
        print(f"  [{doc.metadata['team']}] {doc.page_content[:70]}...")

    print("\n=== Simulating a document update (IT FAQ content changed) ===")
    updated_chunk = ChunkRecord(
        text="To reset your password, visit the self-service portal at "
             "portal.internal/reset and follow the prompts.",
        doc_id="it-faq-001", chunk_index=0,
        metadata={"team": "IT", "access_level": "all_employees", "doc_type": "faq"},
    )
    store.replace_document_chunks("it-faq-001", [updated_chunk])
    print(f"Chunk count after update (should be unchanged, 3): {store.count()}")


if __name__ == "__main__":
    run_demo()
```

### Expected output (shape)

```
Stored 3 chunks in the persistent Chroma collection.

=== Query as a regular employee (no managers_only access) ===
  [IT] To reset your password, go to Settings > Security and click 'Forgot...
  [Platform] Database failover runbook: promote the standby replica using `pg_c...

=== Query scoped to Platform team only ===
  [Platform] Database failover runbook: promote the standby replica using `pg_c...

=== Simulating a document update (IT FAQ content changed) ===
Chunk count after update (should be unchanged, 3): 3
```

Note the executive compensation chunk (`managers_only`) never appears in the first query's results, even though it might be topically adjacent to "account access" in some abstract sense — the metadata filter enforces the access boundary at the database layer, before similarity ranking even matters.

### Key production notes

- **Chunk IDs must be deterministic and stable** (`doc_id::chunk-N` here) — this is what makes `replace_document_chunks` safe: deleting by `doc_id` metadata and re-adding won't leave orphaned entries, and re-running ingestion idempotently updates rather than duplicates.
- **Metadata filtering happens *before* similarity ranking**, not after — this is both a correctness requirement (access control must be a hard boundary, not a "usually filtered" best-effort) and a performance one (filtering first means the ANN index only needs to rank within the allowed subset).
- **`PersistentClient`-backed Chroma writes to local disk** — back up that directory like you would any other production database; there's no separate managed backup story unless you build one.
- **This file uses Chroma directly** for the storage/query mechanics; Pattern 10 (Hybrid Databases) will add a BM25 keyword layer alongside it, and Pattern 15 (ANN Search) explains what's actually happening inside Chroma's index to make `similarity_search` fast at scale rather than a full O(n) scan.
- **Access-control filtering here is illustrative, not a complete auth system** — a real production system would derive `access_levels` from an authenticated user's actual role/permissions via an identity provider, never trust a client-supplied filter value directly.

## 8. Pinned Dependency Versions

```txt
langchain-chroma==0.2.0
chromadb==0.5.23
langchain-ollama==0.2.3
langchain-core==0.3.29
python>=3.11,<3.13
```

```bash
ollama pull nomic-embed-text
```

---

**Next:** say `next` and I'll build `10_Hybrid_Databases.md` — combining this vector search with BM25 keyword search for the cases where semantic similarity alone misses exact terms (error codes, service names, IDs).
