# Pattern 15: Memory Compression

> Part of the **Memory Patterns** series (Section 6). Stack: `langchain-ollama` (ChatOllama + OllamaEmbeddings), `langgraph`, `langchain-chroma`, local models (`llama3.1`, `nomic-embed-text`).

---

## 1. Introduce the Pattern

**Memory Compression** is the deliberate practice of **shrinking the representation size** of retained memory — fewer tokens, fewer stored bytes, fewer embedding calls — while preserving the essential meaning, specifically to control storage cost, embedding cost, and retrieval latency **at scale**.

This is easy to confuse with Pattern 14 (Memory Consolidation), so it's worth being precise about the difference:

| | Memory Consolidation | Memory Compression |
|---|---|---|
| Primary concern | **Correctness** — resolving contradictions, merging duplicates | **Size** — reducing tokens/storage/embedding cost |
| Typical trigger | Contradictory or duplicate facts detected | Content is old, low-value, or storage/cost budget exceeded |
| Output | One accurate, current record | A smaller representation of the *same* accurate content |
| Example | "$50k" and "$75k" — which is current? | A verbose 400-word incident narrative -> a terse 40-word structured summary |

You can consolidate without compressing (produce one accurate but still verbose record), and you can compress without consolidating (shrink verbose-but-already-accurate content). In practice, production systems often do both, but they solve **different problems** and deserve separate treatment — this pattern is specifically about the economics and mechanics of making memory *smaller* without losing what matters.

---

## 2. Problem It Solves

Every pattern in this series that stores content verbatim or near-verbatim (Episodic Memory's narrative episodes, Vector-Based Memory's document chunks, Summary Memory's running summary) eventually runs into a **scale** problem that has nothing to do with correctness:

```
[IncidentMemory, Pattern 5, after 3 years of operation]
- 4,200 stored incident episodes
- Average episode: ~350 tokens of narrative detail
- Total: ~1.47 million tokens embedded and stored
- Embedding cost: paid once per token, for every episode, forever
- Vector index size: grows linearly, slowing similarity search
- Most episodes older than 6 months are RARELY retrieved (recent,
  operationally-relevant incidents dominate real queries), yet they
  still cost full storage/embedding/retrieval overhead
```

Three concrete costs compound over time:
1. **Storage and embedding cost** scale linearly (or worse) with raw content volume — a growing liability with no natural ceiling.
2. **Retrieval latency and precision degrade** as the index grows — more candidate vectors to search, and more low-value content diluting result quality (echoing Pattern 9's "signal dilution" concern, but from a *volume* angle rather than a *chunking* angle).
3. **Most of that verbose detail is rarely needed** — once an incident is old, what typically matters for future precedent-matching (Pattern 5's use case) is the *gist* (situation, root cause, fix), not every verbose sentence of the original narrative.

Memory Compression solves this by **aggressively shrinking older/lower-value content into compact representations** — while keeping recent or high-value content at full fidelity — trading a small amount of detail loss for a large reduction in ongoing storage, embedding, and retrieval cost.

---

## 3. Realistic Production/Enterprise Scenario

**Scenario: continuing "IncidentMemory" (Pattern 5) — archival compression at scale**

- After 3 years, IncidentMemory holds thousands of episodes. Storage and embedding costs have become a real line item, and vector search latency has crept up.
- Analysis shows: incidents from the **last 90 days** are queried frequently (active precedent-matching for live issues); incidents **older than 90 days** are queried rarely, and when they are, users typically want the gist ("have we seen this failure mode before, and roughly how was it fixed"), not the full verbose narrative.
- The team wants a **tiered retention policy**: keep recent episodes at full narrative detail, but **compress** episodes older than 90 days into compact structured summaries — cutting their token footprint dramatically — without deleting the underlying precedent value entirely.
- This must run as a **background archival job**, not something that affects live incident response.

This is a clean demonstration of Memory Compression as an operational cost/performance discipline, distinct from Pattern 14's correctness concern: the compressed episodes aren't wrong or contradictory — they're just made smaller.

---

## 4. Architecture / Flow Diagram

```
                     TRIGGER: scheduled archival job (e.g. nightly)
                     ┌───────────────────────────────────────────┐
                     │   compress_old_episodes(older_than_days=90)   │
                     └───────────────────┬───────────────────────────┘
                                          │
                                          ▼
                     ┌───────────────────────────────────────────┐
                     │              LangGraph StateGraph             │
                     │                                                │
                     │  ┌─────────────────────┐                      │
                     │  │  find_candidates_node   │  queries episodes    │
                     │  │                        │  older than the       │
                     │  │                        │  threshold, not yet    │
                     │  │                        │  compressed              │
                     │  └──────────┬─────────────┘                      │
                     │             ▼                                     │
                     │  ┌─────────────────────┐                        │
                     │  │  compress_node           │───▶ ChatOllama         │
                     │  │  (aggressively shrinks      │    (llama3.1)         │
                     │  │   narrative to compact        │    extreme             │
                     │  │   structured gist)              │    summarization        │
                     │  └──────────┬─────────────┘                        │
                     │             ▼                                       │
                     │  ┌─────────────────────┐                          │
                     │  │  replace_in_store_node   │  re-embeds the SMALL      │
                     │  │                          │  compressed version,       │
                     │  │                          │  replaces the verbose        │
                     │  │                          │  original in the index         │
                     │  └──────────┬─────────────┘                          │
                     └────────────────┼───────────────────────────────────────┘
                                       ▼
                     ┌───────────────────────────────────────────┐
                     │        Chroma vector store (persistent)      │
                     │                                                │
                     │  BEFORE: "On June 2nd at 14:03 UTC, the on-call │
                     │  engineer noticed elevated p99 latency on the   │
                     │  checkout service shortly after a routine        │
                     │  deploy. Initial investigation focused on..."     │
                     │  (~350 tokens)                                     │
                     │                                                    │
                     │  AFTER:  "2026-06-02 | checkout p99 latency        │
                     │  spike post-deploy | cause: connection pool leak    │
                     │  | fix: bumped pool size + restart | 18min"           │
                     │  (~25 tokens)                                          │
                     └───────────────────────────────────────────┘
```

**Key idea:** compression **re-embeds and replaces** — the compact version becomes the new stored representation (with its own new, smaller embedding), reducing both storage bytes *and* the cost of any future embedding operations on that content.

---

## 5. Complete Request-to-Response Flow

1. **A nightly archival job fires** `compress_old_episodes(older_than_days=90)`.
2. **`find_candidates_node`** queries the episode store's metadata for entries where `timestamp` is older than 90 days and a `compressed` flag isn't already set — say, 340 episodes qualify tonight.
3. **`compress_node`** processes each candidate: it sends the full verbose episode narrative to `ChatOllama` with an aggressive compression prompt: *"Reduce this to the minimum tokens needed to preserve searchable precedent value: date, situation, root cause, fix, and outcome only. No prose, no filler."* The model returns a terse, structured one-liner — often a 10-15x token reduction.
4. **`replace_in_store_node`** re-embeds the compact version (a cheap operation given its small size) and **replaces** the original verbose document in the Chroma collection — same `doc_id`, new compact `page_content`, updated metadata (`compressed=True, original_token_count=350, compressed_token_count=25`).
5. **Job completes**, logging aggregate savings (e.g., "compressed 340 episodes, reduced stored tokens from ~119,000 to ~8,500 — a 93% reduction").
6. **Live precedent search (Pattern 5's `recall_episodes_node`) continues to work unchanged** — it still finds and returns these episodes by similarity, just now retrieving the compact form instead of the verbose original. A rep asking about a pattern from 8 months ago still gets useful precedent ("connection pool leak, fixed by bumping pool size"), just without the full narrative color.

---

## 6. Why This Pattern Is Appropriate

| Requirement | How Memory Compression satisfies it |
|---|---|
| Control long-run storage/embedding cost at scale | Older/low-value content is shrunk dramatically, cutting stored tokens and future embedding costs |
| Keep vector index lean and fast | Smaller, more numerous-but-compact entries reduce per-search overhead compared to bloated verbose entries |
| Preserve precedent/searchable value | Compression targets *structure* (date, cause, fix) — the parts actually useful for future retrieval — not random truncation |
| Doesn't disrupt live/recent-data quality | Tiered policy (recent = full detail, old = compressed) protects the data that's actually queried often |

**Trade-offs / when it's not enough:**
- Compression is **lossy by design** — nuance, exact wording, and minor details from the original are gone after compression; this is an explicit, deliberate trade of detail for size, appropriate for aged, rarely-accessed content but *not* for anything that might need full-fidelity legal/compliance review later (keep an untouched archive/cold-storage copy elsewhere if that's a requirement, separate from the compressed hot-retrieval index).
- Deciding **what's safe to compress** (age, access frequency, business criticality) is itself a policy decision that needs real usage data, not just a fixed day-count — a naive fixed threshold risks compressing something that turns out to matter later.
- Compression and Consolidation (Pattern 14) address different problems but often want to run **together** on the same aging content — a mature system typically consolidates first (fix correctness) then compresses (shrink size) for content leaving the "hot" tier.

---

## 7. Production-Quality Python Implementation

### Requirements

```bash
pip install -U langchain-ollama langgraph langchain-core langchain-chroma chromadb tiktoken
# Ollama models used locally:
#   ollama pull llama3.1
#   ollama pull nomic-embed-text
```

### `memory_compression.py`

```python
"""
Pattern 15: Memory Compression
Aggressively shrinking older, low-access episodic content into compact
structured summaries to reduce storage/embedding cost at scale, built
on langchain-chroma + langchain-ollama.

Run:
    python memory_compression.py
"""

from __future__ import annotations

import logging
import uuid
from datetime import datetime, timedelta, timezone
from typing import TypedDict

from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_ollama import ChatOllama, OllamaEmbeddings
from langgraph.graph import StateGraph, START, END

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("memory_compression")

COMPRESS_OLDER_THAN_DAYS = 90


def build_llm(model: str = "llama3.1", temperature: float = 0.1) -> ChatOllama:
    return ChatOllama(model=model, temperature=temperature, keep_alive="10m")


def build_embeddings(model: str = "nomic-embed-text") -> OllamaEmbeddings:
    return OllamaEmbeddings(model=model)


def build_vectorstore(embeddings: OllamaEmbeddings, persist_dir: str = "./compression_memory_db") -> Chroma:
    return Chroma(
        collection_name="incident_episodes",
        embedding_function=embeddings,
        persist_directory=persist_dir,
    )


def approx_token_count(text: str) -> int:
    """Cheap approximation (~4 chars/token) to avoid a hard tiktoken
    dependency in this example; swap for a real tokenizer count in
    production if precise cost accounting matters."""
    return max(1, len(text) // 4)


# --------------------------------------------------------------------------
# 1. Seed some old, verbose episodes (simulating Pattern 5's IncidentMemory
#    after months of accumulation) so compression has real candidates.
# --------------------------------------------------------------------------
def seed_old_verbose_episodes(vectorstore: Chroma):
    old_date = (datetime.now(timezone.utc) - timedelta(days=200)).isoformat()
    episodes = [
        (
            "On June 2nd at 14:03 UTC, the on-call engineer noticed elevated "
            "p99 latency on the checkout service shortly after a routine "
            "deploy went out. Initial investigation focused on the database "
            "layer, but query times looked normal. After about 12 minutes, "
            "the team noticed connection pool exhaustion metrics climbing "
            "steadily. Root cause was identified as a connection pool leak "
            "introduced by the new retry logic in the deploy, which failed "
            "to release connections on timeout. The fix was to bump the "
            "connection pool size as an immediate mitigation and restart "
            "the affected service instances, which resolved the issue "
            "within 18 minutes of detection. A follow-up ticket was filed "
            "to properly fix the retry logic's connection handling."
        ),
        (
            "A customer-reported incident came in around 9:40 AM regarding "
            "failed webhook deliveries to their integration endpoint. "
            "Engineering traced this to an upstream certificate rotation "
            "that had not been properly propagated to the webhook delivery "
            "service's trust store, causing TLS handshake failures. Once "
            "identified, the fix involved manually refreshing the trust "
            "store and redeploying the webhook delivery service, which "
            "took roughly 25 minutes end to end. No data was lost since "
            "failed webhook deliveries were automatically queued for retry."
        ),
    ]

    for i, text in enumerate(episodes):
        doc = Document(
            page_content=text,
            metadata={
                "timestamp": old_date,
                "compressed": False,
                "original_token_count": approx_token_count(text),
            },
            id=f"legacy-episode-{i}",
        )
        vectorstore.add_documents([doc])
    logger.info("Seeded %d old, verbose, uncompressed episode(s)", len(episodes))


# --------------------------------------------------------------------------
# 2. Graph state
# --------------------------------------------------------------------------
class CompressionJobState(TypedDict):
    candidate_ids: list[str]
    total_original_tokens: int
    total_compressed_tokens: int


# --------------------------------------------------------------------------
# 3. Node: find episodes eligible for compression (old enough, not
#    already compressed).
# --------------------------------------------------------------------------
def make_find_candidates_node(vectorstore: Chroma):
    def find_candidates_node(state: CompressionJobState) -> CompressionJobState:
        cutoff = datetime.now(timezone.utc) - timedelta(days=COMPRESS_OLDER_THAN_DAYS)
        all_docs = vectorstore.get(include=["metadatas"])

        candidate_ids = []
        for doc_id, metadata in zip(all_docs["ids"], all_docs["metadatas"]):
            if metadata.get("compressed"):
                continue
            ts = metadata.get("timestamp")
            if ts and datetime.fromisoformat(ts.replace("Z", "+00:00")) < cutoff:
                candidate_ids.append(doc_id)

        logger.info("Found %d episode(s) eligible for compression", len(candidate_ids))
        return {"candidate_ids": candidate_ids}

    return find_candidates_node


# --------------------------------------------------------------------------
# 4. Node: aggressively compress each candidate's content, then re-embed
#    and REPLACE it in the store under the same doc_id.
# --------------------------------------------------------------------------
COMPRESSION_PROMPT = """Compress this incident narrative to the ABSOLUTE \
MINIMUM tokens needed to preserve future searchable precedent value. \
Output a single terse line in this exact format, nothing else:
<date> | <one-line situation> | cause: <root cause> | fix: <fix> | <time to resolve if known>

Narrative:
{narrative}

Compressed line:"""


def make_compress_node(llm: ChatOllama, vectorstore: Chroma):
    def compress_node(state: CompressionJobState) -> CompressionJobState:
        total_original = 0
        total_compressed = 0

        for doc_id in state["candidate_ids"]:
            existing = vectorstore.get(ids=[doc_id], include=["documents", "metadatas"])
            original_text = existing["documents"][0]
            metadata = existing["metadatas"][0]

            prompt = COMPRESSION_PROMPT.format(narrative=original_text)
            try:
                compressed_text = llm.invoke(prompt).content.strip()
            except Exception:
                logger.exception("Compression LLM call failed for doc_id=%s; skipping.", doc_id)
                continue

            original_tokens = approx_token_count(original_text)
            compressed_tokens = approx_token_count(compressed_text)
            total_original += original_tokens
            total_compressed += compressed_tokens

            # Replace: delete the old (verbose) entry, add the new (compact) one
            # under the SAME id, with updated metadata marking it compressed.
            vectorstore.delete(ids=[doc_id])
            new_metadata = {
                **metadata,
                "compressed": True,
                "original_token_count": original_tokens,
                "compressed_token_count": compressed_tokens,
            }
            vectorstore.add_documents(
                [Document(page_content=compressed_text, metadata=new_metadata, id=doc_id)]
            )

            logger.info(
                "Compressed doc_id=%s: %d -> %d tokens (%.0f%% reduction)",
                doc_id, original_tokens, compressed_tokens,
                100 * (1 - compressed_tokens / max(original_tokens, 1)),
            )

        return {
            "total_original_tokens": total_original,
            "total_compressed_tokens": total_compressed,
        }

    return compress_node


# --------------------------------------------------------------------------
# 5. Assemble the compression job graph -- a background/scheduled job,
#    separate from live incident chat traffic.
# --------------------------------------------------------------------------
def build_graph(persist_dir: str = "./compression_memory_db"):
    llm = build_llm()
    embeddings = build_embeddings()
    vectorstore = build_vectorstore(embeddings, persist_dir)

    graph_builder = StateGraph(CompressionJobState)
    graph_builder.add_node("find_candidates_node", make_find_candidates_node(vectorstore))
    graph_builder.add_node("compress_node", make_compress_node(llm, vectorstore))

    graph_builder.add_edge(START, "find_candidates_node")
    graph_builder.add_edge("find_candidates_node", "compress_node")
    graph_builder.add_edge("compress_node", END)

    return graph_builder.compile(), vectorstore


# --------------------------------------------------------------------------
# 6. Service wrapper
# --------------------------------------------------------------------------
class MemoryCompressionService:
    def __init__(self, persist_dir: str = "./compression_memory_db"):
        self.graph, self.vectorstore = build_graph(persist_dir)

    def run_compression_job(self) -> dict:
        result = self.graph.invoke(
            {"candidate_ids": [], "total_original_tokens": 0, "total_compressed_tokens": 0}
        )
        return result


# --------------------------------------------------------------------------
# 7. Demo: seed old verbose episodes, run the compression job, show the
#    aggregate token savings.
# --------------------------------------------------------------------------
if __name__ == "__main__":
    service = MemoryCompressionService()
    seed_old_verbose_episodes(service.vectorstore)

    print("--- Running nightly compression job ---")
    result = service.run_compression_job()

    original = result["total_original_tokens"]
    compressed = result["total_compressed_tokens"]
    if original:
        reduction_pct = 100 * (1 - compressed / original)
        print(f"\nCompressed {len(result.get('candidate_ids', []))} episode(s):")
        print(f"  Original tokens:   ~{original}")
        print(f"  Compressed tokens: ~{compressed}")
        print(f"  Reduction:         ~{reduction_pct:.0f}%")

    print("\n--- A compressed episode, still fully searchable for precedent ---")
    results = service.vectorstore.similarity_search("checkout latency after deploy", k=1)
    if results:
        print(results[0].page_content)
```

### Notes on production-readiness in this code

- **Replace-in-place under the same `doc_id`** — compression deletes and re-adds under the identical ID, so any external references to that episode (e.g., a link from a postmortem ticket) remain valid even after compression.
- **Metadata tracks the compression event itself** (`compressed`, `original_token_count`, `compressed_token_count`) — this makes the job idempotent (won't re-compress already-compressed entries) and gives you a built-in audit trail of how much has been saved.
- **Aggregate savings reporting** — logging/returning total original vs. compressed token counts is what makes this pattern's value measurable and justifiable as an ongoing operational cost-control practice, not just a one-off cleanup.
- **Tiered age threshold (`COMPRESS_OLDER_THAN_DAYS`)** keeps recent, frequently-queried content untouched — protecting retrieval quality where it matters most, while only paying the compression-fidelity trade-off on content that's already aged out of active use.
- **Graceful per-document failure handling** — if compression fails for one candidate, the loop logs and continues rather than aborting the whole nightly job over a single bad entry.

---

## 8. Version Pins (latest stable, compatible, at time of writing)

```
python            >= 3.11
langchain-core    >= 0.3.0
langchain-ollama  >= 0.2.0
langchain-chroma  >= 0.1.4
chromadb          >= 0.5.0
langgraph         >= 0.2.0
```

```bash
pip install -U "langchain-core>=0.3.0" "langchain-ollama>=0.2.0" "langchain-chroma>=0.1.4" "chromadb>=0.5.0" "langgraph>=0.2.0"
```

> `approx_token_count` uses a simple character-based heuristic to avoid a hard `tiktoken` dependency; swap in a real tokenizer (or Ollama's own token counting) for precise cost accounting in production.

---

**Next up → Pattern 16: Memory Forgetting** (deliberately, safely deleting/expiring memory — for privacy compliance, staleness, and relevance decay — completing the lifecycle every pattern in this series has been building toward).
