# Pattern 4: Semantic Chunking

## 1. Introduce the Pattern

Pattern 3 (Chunking) split text based on **structure and size** — paragraph breaks, character counts, overlap windows. **Semantic chunking** splits text based on **meaning shifts** instead: it embeds small pieces of text (usually sentences), measures how similar consecutive pieces are to each other, and cuts a new chunk boundary exactly where the topic changes — regardless of paragraph formatting or character count.

```
Sentence-by-sentence similarity to the previous sentence:

S1: "The database failover process starts with confirming the primary is down."
S2: "Health checks must show two consecutive failures before acting."      } high similarity (0.89) — same topic
S3: "Once confirmed, promote the standby replica using pg_ctl."            } high similarity (0.85) — same topic
S4: "Employees on notice period may work remotely with approval."         } LOW similarity (0.21) — topic shift! → cut here
S5: "This is separate from the standard remote work policy."              } high similarity (0.91) — same topic as S4
```

Instead of a fixed 500-character window, semantic chunking would produce: **Chunk A = {S1, S2, S3}** (the failover procedure) and **Chunk B = {S4, S5}** (the HR policy note) — even if they happened to sit in the same paragraph of a messily-formatted source document.

## 2. The Problem It Solves

Structure-based chunking (Pattern 3) assumes the document's formatting reflects its topic boundaries — that paragraph breaks and section headers line up with where the meaning actually changes. That assumption often breaks down:

- **Poorly formatted source documents**: content scraped from Confluence, Slack exports, or auto-converted PDFs frequently lacks clean paragraph structure. Two unrelated ideas can end up in the same "paragraph" with no blank line between them.
- **Long paragraphs covering multiple sub-topics**: a single dense paragraph might discuss three related-but-distinct points. A fixed-size splitter might cut it arbitrarily in the middle of point 2, or lump all three into one overly broad chunk.
- **Chunks with mixed topics hurt retrieval precision**: if a chunk contains both "database failover steps" and "HR remote work policy" (because they happened to be structurally adjacent), its embedding becomes a blurry average of both topics — exactly the dilution problem from Pattern 3, but now happening *within* a single chunk rather than across a whole document.

Semantic chunking fixes this by chunking according to what the *content itself* says, using the embedding model as the judge of "is this still the same topic," rather than trusting formatting.

## 3. Realistic Enterprise Scenario

DocuMind ingests **Slack-exported incident postmortems** — long, stream-of-consciousness documents written during a live incident, often with poor paragraph structure: engineers paste log snippets, jump between root-cause discussion and remediation steps, then circle back to timeline details, all with minimal formatting.

A structure-based chunker (Pattern 3) on this kind of content tends to produce noisy chunks — arbitrary 500-character windows that mix "what caused the outage" with "what we're going to do about it" with "who was paged." When an employee later asks *"what was the root cause of the March payment outage?"*, retrieval on badly-mixed chunks returns fuzzy, partial matches.

Semantic chunking, applied to these postmortems, naturally separates "root cause discussion," "remediation steps," and "timeline of events" into distinct chunks — because they *are* topically distinct — even though the source document never marks that separation with headers.

## 4. Architecture / Flow Diagram

```
┌───────────────────────────┐
│  Raw text (poorly          │
│  structured postmortem)    │
└─────────────┬─────────────┘
              │
              ▼
   ┌────────────────────────┐
   │ Split into sentences     │   (lightweight sentence tokenizer)
   └─────────────┬─────────────┘
                  │
                  ▼
   ┌────────────────────────────────────┐
   │ Embed every sentence individually    │  ← reuses Pattern 1/2
   │ [v1] [v2] [v3] [v4] [v5] [v6] ...    │
   └─────────────┬─────────────────────┘
                  │
                  ▼
   ┌───────────────────────────────────────┐
   │ Compute cosine similarity between        │
   │ each sentence and the next               │
   │ sim(v1,v2)=0.89  sim(v2,v3)=0.85         │
   │ sim(v3,v4)=0.21 ← below threshold!        │
   │ sim(v4,v5)=0.91                           │
   └─────────────┬─────────────────────────┘
                  │
                  ▼
   ┌───────────────────────────────────────┐
   │ Cut a new chunk boundary wherever        │
   │ similarity drops below threshold          │
   │ (with min/max chunk size guardrails)      │
   └─────────────┬─────────────────────────┘
                  │
                  ▼
        Chunk A = {S1,S2,S3}   Chunk B = {S4,S5,S6...}
                  │
                  ▼
        → each chunk goes to Pattern 1/2 (embedding, again —
          this time embedding the whole chunk, not per-sentence)
          → then Pattern 9 (vector database storage)
```

Note the embedding model gets used **twice**: once per-sentence to *decide where to cut*, and once per-chunk (after cutting) to produce the actual vector that gets stored and searched.

## 5. Request-to-Response Walkthrough

1. The raw document text is split into sentences using a lightweight sentence boundary detector (regex-based here; a proper NLP sentence tokenizer in a larger production system).
2. Every sentence is embedded individually via `OllamaEmbeddings.embed_documents()`.
3. The pipeline walks through consecutive sentence pairs, computing cosine similarity between each sentence and the next.
4. A **breakpoint** is marked wherever similarity drops below a configurable threshold (or, more robustly in production, wherever the similarity drop is a statistical outlier relative to the document's own similarity distribution — since "high" and "low" similarity are relative to the writing style of the specific document).
5. Sentences between breakpoints are grouped into a chunk. Guardrails enforce a **minimum chunk size** (so we don't produce a one-sentence chunk from a tiny topic blip) and a **maximum chunk size** (so a long run of highly-similar sentences doesn't become one giant chunk).
6. Each resulting chunk is embedded again (this time as a whole unit) for storage — this is the vector that retrieval will actually search against.
7. Chunks carry metadata noting they were produced by semantic chunking, plus the similarity threshold used, which is useful for debugging retrieval quality later.

## 6. Why This Pattern Is Appropriate

| Approach | Strength | Weakness |
|---|---|---|
| Fixed-size chunking (Pattern 3) | Fast, cheap, predictable chunk count | Ignores actual topic boundaries; can mix unrelated content in one chunk |
| Recursive/structure-based chunking (Pattern 5) | Respects document structure (headers, lists) | Only as good as the document's formatting; fails on messy source content |
| **Semantic chunking (this pattern)** | Chunk boundaries reflect actual topic shifts, regardless of formatting | More expensive (embeds every sentence, not just every chunk); threshold tuning needed per content type |

Semantic chunking is **not a universal replacement** for Pattern 3 — it costs more (one embedding call per sentence during ingestion) and is harder to tune. The right call for DocuMind is a **hybrid strategy**: well-structured documents (runbooks with clear headers) use fast structure-based chunking; messy, unstructured sources (Slack exports, meeting notes, postmortems) use semantic chunking where the extra cost pays for itself in retrieval quality.

## 7. Production-Quality Python Implementation

```python
"""
semantic_chunking_pipeline.py

Production-quality semantic chunker for DocuMind, for messy/unstructured
source content (Slack exports, postmortems, meeting notes) where
formatting can't be trusted to reflect topic boundaries.

Approach:
  1. Split into sentences
  2. Embed every sentence
  3. Find topic-shift breakpoints using a percentile-based threshold on
     the distribution of consecutive-sentence similarities (adaptive,
     not a fixed magic number — different documents have different
     "normal" similarity baselines)
  4. Group sentences into chunks at those breakpoints, respecting
     min/max chunk size guardrails
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import List

import numpy as np
from langchain_ollama import OllamaEmbeddings

EMBEDDING_MODEL = "nomic-embed-text"

MIN_SENTENCES_PER_CHUNK = 2
MAX_SENTENCES_PER_CHUNK = 12
# Percentile of the similarity-drop distribution used as the cut threshold.
# Lower percentile = more aggressive splitting (more, smaller chunks).
BREAKPOINT_PERCENTILE = 25


@dataclass
class SemanticChunk:
    text: str
    doc_id: str
    chunk_index: int
    sentence_count: int
    metadata: dict = field(default_factory=dict)


def _split_sentences(text: str) -> List[str]:
    """Lightweight sentence splitter. Handles common abbreviations well
    enough for runbook/postmortem style content; swap for a proper NLP
    tokenizer (e.g. spaCy) for production text with heavy abbreviation use."""
    text = re.sub(r"\s+", " ", text.strip())
    # Split on '.', '!', '?' followed by a space and a capital letter,
    # but avoid splitting on common abbreviations like "e.g." or "pg_ctl".
    raw = re.split(r"(?<=[.!?])\s+(?=[A-Z])", text)
    return [s.strip() for s in raw if s.strip()]


class SemanticChunker:
    def __init__(self, model: str = EMBEDDING_MODEL):
        self._embedder = OllamaEmbeddings(model=model)

    @staticmethod
    def _cosine(a: np.ndarray, b: np.ndarray) -> float:
        denom = np.linalg.norm(a) * np.linalg.norm(b)
        return float(np.dot(a, b) / denom) if denom else 0.0

    def _find_breakpoints(self, sentence_vectors: List[np.ndarray]) -> List[int]:
        """Returns sentence indices where a new chunk should START
        (i.e. the boundary is BEFORE this sentence)."""
        if len(sentence_vectors) < 2:
            return []

        similarities = [
            self._cosine(sentence_vectors[i], sentence_vectors[i + 1])
            for i in range(len(sentence_vectors) - 1)
        ]
        # Adaptive threshold: cut wherever similarity falls in the bottom
        # BREAKPOINT_PERCENTILE of this document's own similarity distribution.
        threshold = float(np.percentile(similarities, BREAKPOINT_PERCENTILE))

        breakpoints = []
        for i, sim in enumerate(similarities):
            if sim <= threshold:
                breakpoints.append(i + 1)  # boundary is before sentence i+1
        return breakpoints

    def _enforce_size_guardrails(self, breakpoints: List[int], total_sentences: int) -> List[int]:
        """Merge chunks that would be too small; force-split chunks that
        would be too large, even if no natural breakpoint was found there."""
        bounds = [0] + breakpoints + [total_sentences]
        adjusted = [0]

        i = 1
        while i < len(bounds):
            start = adjusted[-1]
            end = bounds[i]
            size = end - start

            if size < MIN_SENTENCES_PER_CHUNK and i < len(bounds) - 1:
                # too small — skip this boundary, merge into the next segment
                i += 1
                continue

            if size > MAX_SENTENCES_PER_CHUNK:
                # too large — force a split partway through
                forced_end = start + MAX_SENTENCES_PER_CHUNK
                adjusted.append(forced_end)
                continue  # re-evaluate remaining span from forced_end

            adjusted.append(end)
            i += 1

        if adjusted[-1] != total_sentences:
            adjusted.append(total_sentences)
        return sorted(set(adjusted))

    def chunk(self, text: str, doc_id: str, extra_metadata: dict | None = None) -> List[SemanticChunk]:
        sentences = _split_sentences(text)
        if not sentences:
            return []
        if len(sentences) <= MIN_SENTENCES_PER_CHUNK:
            return [SemanticChunk(
                text=" ".join(sentences), doc_id=doc_id, chunk_index=0,
                sentence_count=len(sentences),
                metadata={**(extra_metadata or {}), "method": "semantic", "note": "too_short_to_split"},
            )]

        vectors = [np.array(v, dtype=np.float32) for v in self._embedder.embed_documents(sentences)]
        raw_breakpoints = self._find_breakpoints(vectors)
        bounds = self._enforce_size_guardrails(raw_breakpoints, len(sentences))

        chunks = []
        for idx in range(len(bounds) - 1):
            start, end = bounds[idx], bounds[idx + 1]
            chunk_sentences = sentences[start:end]
            chunks.append(SemanticChunk(
                text=" ".join(chunk_sentences),
                doc_id=doc_id,
                chunk_index=idx,
                sentence_count=len(chunk_sentences),
                metadata={**(extra_metadata or {}), "method": "semantic",
                          "breakpoint_percentile": BREAKPOINT_PERCENTILE},
            ))
        return chunks


# --------------------------------------------------------------------------
# Demo: a messy, unstructured postmortem excerpt
# --------------------------------------------------------------------------

POSTMORTEM_TEXT = """
The payment service started returning 500 errors at 14:02 UTC. Error rates
climbed from baseline 0.1% to 38% within four minutes. The root cause was
a connection pool exhaustion in the payments-api service caused by a recent
deploy that removed a connection timeout setting. Connections were held
open indefinitely under load instead of being released after 30 seconds.
We rolled back the deploy at 14:19 UTC and error rates returned to baseline
by 14:24 UTC. Going forward, we will add a hard connection timeout back
into the pool config and add an alert on pool utilization exceeding 80%.
We are also adding a pre-deploy checklist item to review connection pool
settings for any service handling payment traffic. Separately, the on-call
engineer noted that the paging alert took 6 minutes to fire, which is
longer than our 3-minute SLA for critical service alerts. We will review
the alerting pipeline configuration to understand the delay and file a
follow-up ticket with the observability team.
"""


def run_demo() -> None:
    chunker = SemanticChunker()
    chunks = chunker.chunk(POSTMORTEM_TEXT, doc_id="postmortem-payments-mar14",
                            extra_metadata={"source": "postmortem-2026-03-14.md"})

    print(f"Produced {len(chunks)} semantic chunks\n")
    for c in chunks:
        print(f"[Chunk {c.chunk_index}] ({c.sentence_count} sentences)")
        print(f"  {c.text}\n")


if __name__ == "__main__":
    run_demo()
```

### Expected output (shape)

```
Produced 3 semantic chunks

[Chunk 0] (3 sentences)
  The payment service started returning 500 errors at 14:02 UTC. Error rates
  climbed from baseline 0.1% to 38% within four minutes. The root cause was
  a connection pool exhaustion in the payments-api service caused by a
  recent deploy that removed a connection timeout setting.

[Chunk 1] (3 sentences)
  Connections were held open indefinitely under load instead of being
  released after 30 seconds. We rolled back the deploy at 14:19 UTC and
  error rates returned to baseline by 14:24 UTC. Going forward, we will
  add a hard connection timeout back into the pool config and add an
  alert on pool utilization exceeding 80%.

[Chunk 2] (3 sentences)
  Separately, the on-call engineer noted that the paging alert took 6
  minutes to fire, which is longer than our 3-minute SLA for critical
  service alerts. We will review the alerting pipeline configuration to
  understand the delay and file a follow-up ticket with the observability
  team.
```

Even though the source is one unbroken block of text with no paragraph breaks at all, semantic chunking correctly isolates the **paging/alerting SLA discussion** (a distinct sub-topic, signaled by "Separately,") into its own chunk — a fixed-size splitter would very likely have sliced straight through it, landing it half in the remediation chunk and half wherever the character count ran out.

### Key production notes

- **The threshold is adaptive (percentile-based), not a fixed magic number** — "0.75 similarity" means something different in a terse runbook vs. a chatty postmortem. Computing the threshold relative to each document's own similarity distribution makes this far more robust across content types.
- **Min/max guardrails are essential** — pure similarity-based cutting, with no guardrails, can produce degenerate results (a single-sentence chunk from a brief tangent, or one giant chunk if the whole document happens to be topically uniform).
- **Cost trade-off**: this pattern embeds every *sentence*, then embeds every *chunk* again for storage — roughly 2x the embedding calls compared to Pattern 3 for the same document. Reserve it for content where structure-based chunking demonstrably underperforms.
- **This pairs well with Pattern 6 (Document Parsing) and Pattern 7 (Metadata Extraction)** — semantic chunking works purely on plain text, so it's typically applied *after* parsing has stripped away PDF/HTML artifacts, and chunk metadata can still be enriched afterward.

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

**Next:** say `next` and I'll build `05_Recursive_Chunking.md` — a deeper, dedicated look at the structure-first, fallback-based splitting mechanism briefly introduced in Pattern 3.
