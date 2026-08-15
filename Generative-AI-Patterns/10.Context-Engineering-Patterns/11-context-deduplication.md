# Pattern 11 — Context Deduplication

[← Back to index](./README.md) | Previous: [Pattern 10 — Context Summarization](./10-context-summarization.md) | Next: Pattern 12 — Contextual Retrieval (queued)

---

## 1. Introduce the pattern

**Context Deduplication** removes redundant restatements of the same information — whether they're byte-for-byte identical, worded differently but semantically the same, or partially overlapping at the sentence level — before they reach the LLM.

This is a different kind of bloat from anything the earlier patterns address. Pattern 02 (Filtering) removes content that shouldn't be there at all (noise, PII). Pattern 03 (Compression) shrinks content that *should* be there but is too verbose. **Deduplication addresses the case where multiple, independently-valid, independently-relevant pieces of context all happen to say the same thing** — because they came from different sources (a note, a summary, a case record) that each, correctly, captured the same underlying fact.

```
Multiple context items, independently relevant → [ DEDUPLICATION ] → each fact stated once
   (case metadata + analyst note + running                │
    summary all restate "$14,200, high-risk               ├── exact: byte-identical content/sentences
    jurisdiction, beneficiary added 40 min prior")         ├── near-duplicate: same meaning, different wording
                                                            └── sentence-level: partial overlap across items
```

**Mental model:** three different colleagues each independently write you a briefing note on the same case, and each one, correctly, mentions the flagged amount and the high-risk jurisdiction. You don't want to read that fact three times in three different phrasings — you want it stated once, clearly, with the rest of each note's *unique* content preserved. Deduplication is the merge step that produces that single, non-repetitive briefing.

---

## 2. The problem it solves

Redundant context shows up naturally in any system with multiple independent sources, and it causes real problems beyond simple waste:

1. **Wasted token budget on repeated information.** If the same fact appears in the case record, an analyst note, and a running summary (Pattern 10) — three restatements of "$14,200 wire, high-risk jurisdiction" — that's tokens spent three times on information that only needed to be said once, directly competing with budget that Pattern 05's prioritization could have spent on genuinely different facts.
2. **False appearance of independent corroboration.** When an LLM sees the same fact stated three times, in three different sources, there's a real risk it reads that repetition as three independent pieces of evidence confirming something, rather than recognizing it's the same fact restated — this can subtly inflate the model's apparent confidence in a conclusion that's actually resting on a single underlying data point.
3. **Near-duplicates are the hard case.** Exact duplicates are trivial to catch (they're byte-identical), but most real-world redundancy isn't exact — "the customer confirmed this was a legitimate business payment" and "customer stated the transfer was for a legitimate business purpose" say the same thing in different words. Catching this requires semantic comparison, not just string matching, and merging correctly (keeping the more detailed/informative phrasing, not an arbitrary one) matters for not losing nuance in the process.

Context Deduplication solves this with three layered techniques — exact-hash matching, embedding-based near-duplicate clustering, and cross-item sentence-level overlap removal — so that whatever level a repeat occurs at, it gets caught.

---

## 3. A realistic enterprise problem (Helios)

By the time `CASE-88421`'s context has been assembled from multiple sources — the case record, two analyst notes written independently by different reviewers, and (from Pattern 10) a running session summary — the same handful of core facts show up repeatedly:

- The case record and both analyst notes each mention the **$14,200 wire, high-risk jurisdiction** detail, in slightly different phrasing.
- Analyst note 1 says *"Customer confirmed the transfer was a legitimate business payment."* Analyst note 2, written by a different reviewer following up later, says *"The customer stated the transfer was for a legitimate business purpose."* — a near-duplicate, not an exact one.
- The running summary from Pattern 10, by design, restates several facts that are also present verbatim in the still-hot-window recent messages it was built alongside.

Without deduplication, the final context block sent to the LLM repeats the same three or four core facts across five or six differently-worded restatements — burning budget that Pattern 05 could have spent on the parts of each note and the summary that are actually unique (each analyst's distinct observations, the summary's narrative connective tissue).

---

## 4. Architecture / flow diagram

```mermaid
flowchart TD
    A[Context items from multiple sources\n(case record, notes, summary, etc.)] --> B[Layer 1:\nExact-Hash Deduplication]
    B --> C{Byte-identical content\nacross items?}
    C -- Yes --> D[Drop duplicate,\nkeep highest-priority source]
    C -- No --> E[Layer 2:\nNear-Duplicate Clustering]

    D --> E
    E --> F[Embed all items,\ncluster by cosine similarity]
    F --> G{Cluster of\nsemantically-same items?}
    G -- Yes --> H[Keep the most detailed/\nhighest-priority item per cluster,\ndrop the rest]
    G -- No --> I[Layer 3:\nCross-Item Sentence Dedup]

    H --> I
    I --> J[Split surviving items into sentences,\nprocess in importance order]
    J --> K{Sentence already\nclaimed by a\nhigher-priority item?}
    K -- Yes --> L[Remove sentence\nfrom this item]
    K -- No --> M[Keep sentence,\nmark as claimed]

    L --> N[Deduplicated Context Set]
    M --> N
    N --> O[... continues into Ranking / Prioritization ...]

    style B fill:#4A90D9,color:#fff
    style E fill:#4A90D9,color:#fff
    style I fill:#4A90D9,color:#fff
```

---

## 5. The complete request-to-response flow

1. **Input**: the assembled set of context items from multiple sources — this pattern is typically inserted after Filtering/Compression (Patterns 02–03) and before final Ranking/Prioritization (Patterns 04–05), since it operates *across* items rather than within a single one.
2. **Layer 1 — exact-hash deduplication**: every item's content is normalized (lowercased, whitespace-collapsed, punctuation-stripped) and hashed; items with identical hashes are collapsed to one, keeping whichever came from the higher source-priority (reusing the `SOURCE_BASE_PRIORITY` concept from earlier patterns).
3. **Layer 2 — near-duplicate clustering**: surviving items are embedded (`nomic-embed-text`, same as Pattern 01) and pairwise cosine similarity is computed; items above a similarity threshold are grouped into clusters via a union-find structure. Within each cluster, only the single most detailed/highest-priority item survives — the others are dropped, not silently merged, so the decision is auditable.
4. **Layer 3 — cross-item sentence-level deduplication**: the surviving items are processed in descending importance order; each item is split into sentences, and any sentence whose normalized form has already been "claimed" by a higher-priority item is removed from the current (lower-priority) item. This catches the common case where two items are mostly *different* but share one or two overlapping sentences — a full-item drop (Layer 2) would be too blunt here, since the rest of the item's content is genuinely unique and worth keeping.
5. **Output**: a deduplicated context set where every fact appears once, attributed to its highest-priority source, with each surviving item's unique content intact — ready to continue into Ranking (04) and Prioritization (05).

---

## 6. Why this pattern is appropriate here

- **Three layers because redundancy happens at three different granularities.** Exact-hash catches the cheap, common case (two sources literally copy-pasted the same boilerplate) essentially for free. Near-duplicate clustering catches whole-item paraphrases that hashing would miss. Sentence-level dedup catches the messiest, most common real-world case: items that are mostly unique but share a few overlapping sentences. Using only one layer leaves real redundancy on the table; using only the coarsest layer (near-duplicate clustering) risks dropping entire items that were actually mostly unique.
- **Dropping, not silently merging, is the safer default.** It would be tempting to have an LLM "merge" near-duplicate items into one combined statement — but that reintroduces exactly the faithfulness risk Pattern 10 had to build a whole verification layer around. Keeping the single best original item (chosen by a deterministic priority rule) rather than generating a new merged statement means deduplication never has to worry about hallucination at all.
- **Priority-ordered sentence claiming keeps the most authoritative source's phrasing.** Processing items in importance order (not document order) means that when a fact is stated in both the case record (highest priority) and a lower-priority analyst note, the case record's phrasing is what survives — which matters for consistency and for keeping the audit trail pointing at the most authoritative source of each fact.
- **It sits naturally between Compression and Ranking.** By the time items reach this pattern, they're already clean (Filtering) and appropriately sized (Compression) — deduplication's job is purely about redundancy *across* items, which is exactly the property Ranking and Prioritization need to be working with a clean, non-repetitive set before they decide order and budget.

---

## 7. Production-quality Python implementation

```python
"""
helios_context_deduplication.py

Pattern 11 — Context Deduplication
Helios Fraud Investigation Copilot

Three-layer deduplication across context items from multiple sources:
1. Exact-hash: normalize + hash, collapse byte-identical items.
2. Near-duplicate clustering: embedding similarity + union-find, keep the
   best item per cluster, drop the rest (never silently merge).
3. Cross-item sentence-level dedup: process items in priority order, remove
   sentences already claimed by a higher-priority item.

Requirements:
    pip install "langchain-core>=0.3.60" "langchain-ollama>=0.3.0" "numpy>=1.26"

Assumes a local Ollama server (`ollama serve`) with:
    ollama pull nomic-embed-text
"""

from __future__ import annotations

import hashlib
import logging
import re
from dataclasses import dataclass, field, replace
from datetime import datetime, timedelta
from enum import Enum

import numpy as np
from langchain_ollama import OllamaEmbeddings

logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")
logger = logging.getLogger("helios.context_deduplication")


# --------------------------------------------------------------------------- #
# Domain model (same shape as earlier patterns, self-contained here)
# --------------------------------------------------------------------------- #

class ContextSourceType(str, Enum):
    CASE_METADATA = "case_metadata"
    TRANSACTION_HISTORY = "transaction_history"
    CUSTOMER_PROFILE = "customer_profile"
    SIMILAR_CASES = "similar_cases"
    COMPLIANCE_POLICY = "compliance_policy"
    ANALYST_NOTES = "analyst_notes"
    SESSION_SUMMARY = "session_summary"


SOURCE_BASE_PRIORITY: dict[ContextSourceType, float] = {
    ContextSourceType.CASE_METADATA: 1.0,
    ContextSourceType.COMPLIANCE_POLICY: 0.85,
    ContextSourceType.TRANSACTION_HISTORY: 0.75,
    ContextSourceType.SIMILAR_CASES: 0.65,
    ContextSourceType.SESSION_SUMMARY: 0.60,
    ContextSourceType.CUSTOMER_PROFILE: 0.55,
    ContextSourceType.ANALYST_NOTES: 0.50,
}


def estimate_tokens(text: str) -> int:
    return max(1, len(text) // 4)


@dataclass
class ContextItem:
    source: ContextSourceType
    content: str
    timestamp: datetime
    item_id: str
    relevance_score: float = 0.0
    token_count: int = field(default=0)

    def __post_init__(self) -> None:
        if self.token_count == 0:
            self.token_count = estimate_tokens(self.content)

    @property
    def importance_score(self) -> float:
        base = SOURCE_BASE_PRIORITY[self.source]
        return (0.6 * self.relevance_score) + (0.4 * base)


# --------------------------------------------------------------------------- #
# Text normalization
# --------------------------------------------------------------------------- #

_WHITESPACE_RE = re.compile(r"\s+")
_PUNCTUATION_RE = re.compile(r"[^\w\s]")


def normalize_text(text: str) -> str:
    text = text.lower().strip()
    text = _PUNCTUATION_RE.sub("", text)
    text = _WHITESPACE_RE.sub(" ", text)
    return text


def content_hash(text: str) -> str:
    return hashlib.sha256(normalize_text(text).encode("utf-8")).hexdigest()


def split_sentences(text: str) -> list[str]:
    return [s.strip() for s in re.split(r"(?<=[.!?])\s+", text.strip()) if s.strip()]


# --------------------------------------------------------------------------- #
# Layer 1 — exact-hash deduplication
# --------------------------------------------------------------------------- #

@dataclass
class DedupReport:
    exact_duplicates_dropped: list[tuple[str, str]] = field(default_factory=list)   # (dropped_id, kept_id)
    near_duplicates_dropped: list[tuple[str, str]] = field(default_factory=list)    # (dropped_id, kept_id)
    sentences_removed: list[tuple[str, str]] = field(default_factory=list)          # (item_id, removed_sentence)


def exact_hash_dedupe(items: list[ContextItem], report: DedupReport) -> list[ContextItem]:
    seen: dict[str, ContextItem] = {}
    for item in sorted(items, key=lambda i: i.importance_score, reverse=True):
        h = content_hash(item.content)
        if h in seen:
            report.exact_duplicates_dropped.append((item.item_id, seen[h].item_id))
            continue
        seen[h] = item
    survivors_ids = {i.item_id for i in seen.values()}
    return [i for i in items if i.item_id in survivors_ids]


# --------------------------------------------------------------------------- #
# Layer 2 — near-duplicate clustering (embeddings + union-find)
# --------------------------------------------------------------------------- #

class UnionFind:
    def __init__(self, n: int) -> None:
        self.parent = list(range(n))

    def find(self, x: int) -> int:
        while self.parent[x] != x:
            self.parent[x] = self.parent[self.parent[x]]
            x = self.parent[x]
        return x

    def union(self, a: int, b: int) -> None:
        ra, rb = self.find(a), self.find(b)
        if ra != rb:
            self.parent[rb] = ra


def cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
    denom = (np.linalg.norm(a) * np.linalg.norm(b)) + 1e-8
    return float(np.dot(a, b) / denom)


def near_duplicate_dedupe(
    items: list[ContextItem], report: DedupReport,
    embeddings: OllamaEmbeddings, similarity_threshold: float = 0.90,
) -> list[ContextItem]:
    if len(items) <= 1:
        return items

    vectors = [np.array(v) for v in embeddings.embed_documents([i.content for i in items])]
    uf = UnionFind(len(items))

    for i in range(len(items)):
        for j in range(i + 1, len(items)):
            if cosine_similarity(vectors[i], vectors[j]) >= similarity_threshold:
                uf.union(i, j)

    clusters: dict[int, list[int]] = {}
    for idx in range(len(items)):
        root = uf.find(idx)
        clusters.setdefault(root, []).append(idx)

    survivors: list[ContextItem] = []
    for indices in clusters.values():
        if len(indices) == 1:
            survivors.append(items[indices[0]])
            continue

        # Keep the item with the highest importance_score; tie-break on length
        # (more detailed content), since dropping detail silently is the risk
        # we want to avoid.
        cluster_items = [items[i] for i in indices]
        best = max(cluster_items, key=lambda i: (i.importance_score, len(i.content)))
        for item in cluster_items:
            if item.item_id != best.item_id:
                report.near_duplicates_dropped.append((item.item_id, best.item_id))
        survivors.append(best)

    return survivors


# --------------------------------------------------------------------------- #
# Layer 3 — cross-item sentence-level deduplication
# --------------------------------------------------------------------------- #

def sentence_level_dedupe(items: list[ContextItem], report: DedupReport) -> list[ContextItem]:
    ordered = sorted(items, key=lambda i: i.importance_score, reverse=True)
    claimed_sentences: set[str] = set()
    result_by_id: dict[str, ContextItem] = {}

    for item in ordered:
        sentences = split_sentences(item.content)
        kept_sentences = []
        for sentence in sentences:
            key = normalize_text(sentence)
            if key in claimed_sentences:
                report.sentences_removed.append((item.item_id, sentence))
                continue
            claimed_sentences.add(key)
            kept_sentences.append(sentence)

        if not kept_sentences:
            continue  # every sentence in this item was already claimed elsewhere -- drop entirely

        new_content = " ".join(kept_sentences)
        result_by_id[item.item_id] = replace(item, content=new_content, token_count=estimate_tokens(new_content))

    # Return in original relative order among survivors, not priority order.
    return [result_by_id[i.item_id] for i in items if i.item_id in result_by_id]


# --------------------------------------------------------------------------- #
# Pipeline
# --------------------------------------------------------------------------- #

class ContextDeduplicationPipeline:
    def __init__(self, embed_model: str = "nomic-embed-text", similarity_threshold: float = 0.90) -> None:
        self.embeddings = OllamaEmbeddings(model=embed_model)
        self.similarity_threshold = similarity_threshold

    def run(self, items: list[ContextItem]) -> tuple[list[ContextItem], DedupReport]:
        report = DedupReport()

        after_exact = exact_hash_dedupe(items, report)
        logger.info("Layer 1 (exact-hash): %d -> %d items", len(items), len(after_exact))

        after_near_dup = near_duplicate_dedupe(after_exact, report, self.embeddings, self.similarity_threshold)
        logger.info("Layer 2 (near-duplicate clustering): %d -> %d items", len(after_exact), len(after_near_dup))

        after_sentence_dedup = sentence_level_dedupe(after_near_dup, report)
        logger.info("Layer 3 (sentence-level): %d -> %d items", len(after_near_dup), len(after_sentence_dedup))

        total_before = sum(i.token_count for i in items)
        total_after = sum(i.token_count for i in after_sentence_dedup)
        logger.info(
            "Deduplication complete: %d tokens -> %d tokens (%.0f%% of original)",
            total_before, total_after, 100 * total_after / max(1, total_before),
        )
        return after_sentence_dedup, report


# --------------------------------------------------------------------------- #
# Example run — deliberately redundant multi-source context set
# --------------------------------------------------------------------------- #

def build_example_items() -> list[ContextItem]:
    now = datetime.utcnow()
    return [
        ContextItem(
            ContextSourceType.CASE_METADATA,
            "Case CASE-88421: $14,200 wire transfer to a new beneficiary in a high-risk jurisdiction, flagged for review.",
            now - timedelta(hours=2), "meta", relevance_score=0.92,
        ),
        ContextItem(
            ContextSourceType.ANALYST_NOTES,
            "The $14,200 wire transfer to a new beneficiary in a high-risk jurisdiction was flagged for review. "
            "Customer confirmed the transfer was a legitimate business payment.",
            now - timedelta(days=18), "note-1", relevance_score=0.70,
        ),
        ContextItem(
            ContextSourceType.ANALYST_NOTES,
            "The customer stated the transfer was for a legitimate business purpose. "
            "Follow-up review scheduled for next quarter.",
            now - timedelta(days=10), "note-2", relevance_score=0.55,
        ),
        ContextItem(
            ContextSourceType.SESSION_SUMMARY,
            "Investigation into case CASE-88421 covered the $14,200 flagged wire transfer. "
            "Sanctions screening for the beneficiary came back clear.",
            now - timedelta(minutes=5), "summary-1", relevance_score=0.65,
        ),
    ]


if __name__ == "__main__":
    items = build_example_items()

    print("=== ORIGINAL ITEMS ===")
    for item in items:
        print(f"\n[{item.item_id} | {item.source.value} | importance={item.importance_score:.3f}]")
        print(f"  {item.content}")

    pipeline = ContextDeduplicationPipeline(similarity_threshold=0.90)
    deduped_items, report = pipeline.run(items)

    print("\n=== DEDUPLICATION REPORT ===")
    print("Exact duplicates dropped (dropped_id -> kept_id):")
    for dropped, kept in report.exact_duplicates_dropped:
        print(f"  {dropped} -> {kept}")
    print("Near-duplicates dropped (dropped_id -> kept_id):")
    for dropped, kept in report.near_duplicates_dropped:
        print(f"  {dropped} -> {kept}")
    print(f"Sentences removed as cross-item duplicates ({len(report.sentences_removed)} total):")
    for item_id, sentence in report.sentences_removed:
        print(f"  [{item_id}] removed: {sentence!r}")

    print("\n=== DEDUPLICATED ITEMS (ready for Ranking / Prioritization) ===")
    for item in deduped_items:
        print(f"\n[{item.item_id} | {item.source.value}]")
        print(f"  {item.content}")
```

### Notes on running this yourself

- Watch Layer 2 catch the `note-1` vs. `note-2` near-duplicate phrasing ("customer confirmed... legitimate business payment" vs. "customer stated... legitimate business purpose") — despite different wording, these should cluster together at a `0.90` similarity threshold; only the higher-importance one survives whole, but note that Layer 3 still needs to run afterward to catch the sentence-level overlap between `meta`, the surviving analyst note, and `summary-1`.
- Layer 3's "process in priority order, claim sentences" approach means `meta` (highest importance) keeps its full sentence about the flagged transfer, and that exact sentence — or its very close paraphrase — gets stripped from whichever lower-priority item also contained it, while each item's genuinely unique content (the follow-up review note, the sanctions screening result) survives untouched.
- `similarity_threshold=0.90` for near-duplicate clustering is intentionally higher (more conservative) than Pattern 07's `0.93` semantic-cache threshold might suggest is loose — tune this per your own data; too low risks conflating two items that are related but meaningfully different, which is a worse failure mode here than under-deduplicating.
- In production, this pipeline slots in right after Compression (Pattern 03) and before Ranking (Pattern 04) — dedup needs items already cleaned and appropriately sized, and its output should be exactly the kind of clean, non-repetitive set that Ranking and Prioritization are designed to work with.

---

**Next up:** Pattern 12 — Contextual Retrieval, the final pattern in this series, where we go back to the very first step (retrieving candidate chunks in the first place) and address a subtle problem: retrieved chunks that lose critical meaning once separated from the document they came from.
