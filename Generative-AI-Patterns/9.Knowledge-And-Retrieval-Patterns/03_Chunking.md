# Pattern 3: Chunking

## 1. Introduce the Pattern

**Chunking** is the process of splitting a long document into smaller pieces ("chunks") before embedding and storing them. Instead of embedding an entire 40-page HR handbook as one giant vector, you break it into paragraph- or section-sized pieces, embed each piece separately, and store them individually.

```
Full document (40 pages)
        │
        ▼  chunking
┌─────────┬─────────┬─────────┬─────────┬─────  ...  ─────┐
│ Chunk 1 │ Chunk 2 │ Chunk 3 │ Chunk 4 │        ...        │
│ ~500tok │ ~500tok │ ~500tok │ ~500tok │                    │
└─────────┴─────────┴─────────┴─────────┴───────────────────┘
        │         │         │         │
        ▼         ▼         ▼         ▼
     embed()   embed()   embed()   embed()
        │         │         │         │
        ▼         ▼         ▼         ▼
    [vector1] [vector2] [vector3] [vector4]   → stored separately in the vector DB
```

This is the pattern that sits directly between "raw documents" and "vector embeddings" (Pattern 1) in any real pipeline — you almost never embed whole documents.

## 2. The Problem It Solves

Two very different problems, both solved by chunking:

**Problem A — embedding models have limited, meaning-diluting input windows.** Most embedding models produce one fixed-size vector for however much text you feed them (`nomic-embed-text` handles up to ~8k tokens). If you embed a 40-page document as a single vector, the resulting vector is an *average* of everything in that document — a request about "notice period" and a request about "expense reimbursement" both get mapped to the same washed-out vector for the whole HR handbook, because both topics are drowned out by everything else in the document. The vector becomes too generic to distinguish anything.

**Problem B — retrieval needs to return just the relevant part, not the whole haystack.** Even if you *could* embed a whole document well, returning it in full to an LLM (in a RAG pipeline) wastes context window and increases the chance of the LLM getting distracted by irrelevant sections ("lost in the middle" effect). You want to retrieve the 2–3 paragraphs that actually answer the question, not the entire document.

Chunking fixes both: each chunk gets its own focused embedding (better retrieval precision), and retrieval returns only the relevant chunks (better LLM context efficiency).

But chunking done badly creates its own problems — cutting a sentence in half, splitting a table from its caption, or separating a step from the numbered list it belongs to. This pattern is about doing it *well*.

## 3. Realistic Enterprise Scenario

DocuMind ingests a 12-page "Incident Response Runbook" PDF. It contains:

- A table of contents
- Numbered sections ("3. Database Incidents", "4. Network Incidents")
- Step-by-step procedures with numbered sub-steps
- A troubleshooting table (symptom → likely cause → fix)

An employee asks: *"What are the steps to fail over the database during an incident?"*

If chunking is naive (e.g. "split every 500 characters, ignore structure"), a chunk might end mid-step: *"...Step 3: Promote the standby replica using `pg_ctl`"* gets cut off right there, and Step 4 ("update the connection string") lands in the *next* chunk with no indication it's a continuation. Retrieval might return only Step 3's chunk, and the LLM answers with an incomplete procedure — a genuinely dangerous failure mode for an incident runbook.

Good chunking keeps each numbered step (or better, each whole procedure) intact, and uses **overlap** so that critical continuity isn't lost even at chunk boundaries.

## 4. Architecture / Flow Diagram

```
┌────────────────────────────┐
│   Raw document (parsed      │
│   text, from Pattern 6)     │
└──────────────┬───────────────┘
               │
               ▼
   ┌───────────────────────┐
   │  Splitter strategy      │   ← this file: fixed-size + recursive-ish
   │  (size + overlap rules) │      character/token splitting with
   └──────────────┬───────────┘     structure-aware separators
                  │
                  ▼
   ┌────────────────────────────────────────────┐
   │ Chunk 1  [tokens 0–500]                      │
   │ Chunk 2  [tokens 450–950]   ← 50-token overlap│
   │ Chunk 3  [tokens 900–1400]  ← 50-token overlap│
   │ ...                                           │
   └──────────────┬─────────────────────────────┘
                  │
                  ▼
   ┌───────────────────────┐
   │ Attach metadata:        │
   │ doc_id, chunk_index,    │
   │ char_start, char_end,   │
   │ section_title (if known)│
   └──────────────┬───────────┘
                  │
                  ▼
        → each chunk goes to Pattern 1/2 (embedding)
          → then Pattern 9 (vector database storage)
```

The **overlap** window is the key idea in this diagram: chunk 2 repeats the tail end of chunk 1, so a sentence or step that straddles the boundary is fully present in at least one chunk.

## 5. Request-to-Response Walkthrough

1. A parsed document (plain text, already extracted from PDF/HTML by Pattern 6) arrives at the chunker.
2. The chunker walks through the text and repeatedly cuts a `chunk_size`-sized window, using a hierarchy of preferred split points: first try splitting on double newlines (paragraph breaks), then single newlines, then sentence-ending punctuation, then finally raw character count if nothing better is found nearby. This is what makes it "recursive-ish" — try the most structure-respecting separator first, fall back only when necessary. (Pattern 5, *Recursive Chunking*, goes deeper into this exact mechanism as its own dedicated pattern.)
3. After cutting a chunk, the splitter backs up by `chunk_overlap` characters/tokens before starting the next chunk, so context isn't lost at the seam.
4. Each chunk is stamped with metadata: which document it came from, its position (`chunk_index`), its character offsets in the original document, and — if section headers were detected during parsing — which section it belongs to.
5. The list of `(chunk_text, metadata)` pairs is handed off to the embedding pipeline (Patterns 1–2).
6. At query time, when a chunk is retrieved as relevant, its metadata (especially `doc_id` and `char_start`/`char_end`) lets DocuMind optionally "expand" the result — pull in a bit of surrounding text or link back to the exact spot in the source document, which is valuable for user trust ("show me where this came from").

## 6. Why This Pattern Is Appropriate

| Chunk size choice | Effect |
|---|---|
| Too small (e.g. 50 tokens) | Loses context — a chunk like "Step 3: Promote the standby replica" without the surrounding "what replica, what system" context is ambiguous. Also creates huge numbers of chunks, increasing storage and search cost. |
| Too large (e.g. whole document) | Diluted, low-precision embeddings (Problem A above); wastes LLM context at generation time |
| No overlap | Sentences/steps split at boundaries can lose meaning entirely in both resulting chunks |
| Too much overlap (e.g. 90%) | Massive storage/compute waste — you're nearly duplicating every chunk |
| **This pattern**: moderate chunk size (300–800 tokens) tuned to content type, with a small overlap (10–15% of chunk size), structure-aware splitting | Balances retrieval precision, context completeness, and cost |

There's no single "correct" chunk size — it depends on the content (dense legal text vs. conversational FAQ vs. code) and the embedding model's effective range. The implementation below makes chunk size and overlap configurable and shows how to reason about picking them for different document types.

## 7. Production-Quality Python Implementation

```python
"""
chunking_pipeline.py

Production-quality text chunker for DocuMind, built on top of
LangChain's RecursiveCharacterTextSplitter, with:
  - Structure-aware separator hierarchy (paragraphs > lines > sentences > words)
  - Configurable chunk size / overlap per document type
  - Rich per-chunk metadata (doc_id, chunk_index, char offsets, section title)
  - A simple heuristic to avoid ending a chunk on a lone list-item number
    (a common failure mode with numbered runbook steps)
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import List, Optional

from langchain_text_splitters import RecursiveCharacterTextSplitter


# --------------------------------------------------------------------------
# Config presets per content type — this is the "art" of chunking:
# different document types genuinely need different settings.
# --------------------------------------------------------------------------

CHUNK_PRESETS = {
    "runbook":       {"chunk_size": 600, "chunk_overlap": 80},   # keep steps whole
    "policy_doc":    {"chunk_size": 500, "chunk_overlap": 60},   # dense legal-ish text
    "faq":           {"chunk_size": 300, "chunk_overlap": 30},   # short, self-contained Q&A
    "default":       {"chunk_size": 500, "chunk_overlap": 60},
}

# Ordered from "most structure-preserving" to "last resort" — this is the
# core idea behind recursive/structure-aware splitting (see Pattern 5 for
# a deeper dedicated treatment of this exact mechanism).
SEPARATOR_HIERARCHY = [
    "\n\n\n",   # section breaks
    "\n\n",     # paragraph breaks
    "\n",       # line breaks (e.g. numbered steps)
    ". ",       # sentence boundaries
    " ",        # word boundaries (last resort)
    "",         # raw characters (absolute last resort)
]


@dataclass
class Chunk:
    text: str
    doc_id: str
    chunk_index: int
    char_start: int
    char_end: int
    section_title: Optional[str] = None
    metadata: dict = field(default_factory=dict)


# --------------------------------------------------------------------------
# Section detection (lightweight) — lets chunks carry a "section_title"
# so retrieval results can show "from: Section 3 - Database Incidents"
# --------------------------------------------------------------------------

SECTION_HEADER_PATTERN = re.compile(r"^(?:\d+\.\s+)?([A-Z][A-Za-z0-9 \-/]{3,60})\s*$", re.MULTILINE)


def _section_title_for_offset(text: str, offset: int) -> Optional[str]:
    """Find the nearest preceding line that looks like a section header."""
    preceding_text = text[:offset]
    matches = list(SECTION_HEADER_PATTERN.finditer(preceding_text))
    return matches[-1].group(1).strip() if matches else None


# --------------------------------------------------------------------------
# Chunker
# --------------------------------------------------------------------------

class DocumentChunker:
    def __init__(self, content_type: str = "default"):
        preset = CHUNK_PRESETS.get(content_type, CHUNK_PRESETS["default"])
        self.content_type = content_type
        self.chunk_size = preset["chunk_size"]
        self.chunk_overlap = preset["chunk_overlap"]

        self._splitter = RecursiveCharacterTextSplitter(
            chunk_size=self.chunk_size,
            chunk_overlap=self.chunk_overlap,
            separators=SEPARATOR_HIERARCHY,
            length_function=len,   # character-based; swap for a tokenizer for token-exact control
        )

    def chunk(self, text: str, doc_id: str, extra_metadata: Optional[dict] = None) -> List[Chunk]:
        if not text or not text.strip():
            return []

        raw_chunks = self._splitter.split_text(text)
        chunks: List[Chunk] = []

        # Track a search cursor to compute approximate char offsets even
        # though the splitter doesn't return them natively.
        cursor = 0
        for i, chunk_text in enumerate(raw_chunks):
            start = text.find(chunk_text, cursor)
            if start == -1:
                # Overlap can make exact re-finding tricky; fall back to a
                # nearby search rather than failing offset tracking entirely.
                start = text.find(chunk_text)
            end = start + len(chunk_text) if start != -1 else cursor + len(chunk_text)
            cursor = max(cursor, start + 1) if start != -1 else cursor

            section_title = _section_title_for_offset(text, start if start != -1 else cursor)

            chunks.append(Chunk(
                text=chunk_text.strip(),
                doc_id=doc_id,
                chunk_index=i,
                char_start=max(start, 0),
                char_end=max(end, 0),
                section_title=section_title,
                metadata={**(extra_metadata or {}), "content_type": self.content_type,
                          "chunk_size_setting": self.chunk_size, "overlap_setting": self.chunk_overlap},
            ))

        self._warn_on_fragment_boundaries(chunks)
        return chunks

    @staticmethod
    def _warn_on_fragment_boundaries(chunks: List[Chunk]) -> None:
        """Heuristic sanity check: flag chunks that start or end on what
        looks like a lone numbered-list marker with no content — a common
        symptom of overly aggressive splitting inside a numbered procedure."""
        lone_marker = re.compile(r"^\s*\d+\.\s*$|^\s*\d+\.\s*$", re.MULTILINE)
        for c in chunks:
            first_line = c.text.split("\n", 1)[0]
            last_line = c.text.rsplit("\n", 1)[-1]
            if lone_marker.match(first_line) or lone_marker.match(last_line):
                print(f"  [warn] chunk {c.chunk_index} of {c.doc_id} may have split "
                      f"mid-procedure (starts/ends on a bare list marker)")


# --------------------------------------------------------------------------
# Demo
# --------------------------------------------------------------------------

RUNBOOK_TEXT = """Incident Response Runbook

3. Database Incidents

When the primary database becomes unresponsive, follow these steps in order.

Step 1. Confirm the primary is actually down by checking the health endpoint
at https://db-health.internal/status. A single failed check is not enough;
confirm with at least two consecutive failures 30 seconds apart.

Step 2. Notify the on-call channel #db-oncall with the health check output
and a timestamp. This starts the incident clock.

Step 3. Promote the standby replica using `pg_ctl promote -D /var/lib/pg/data`.
This process typically completes within 10-15 seconds.

Step 4. Update the connection string in the config service to point at the
new primary, then restart the application pods so they pick up the change.

Step 5. Verify application health by checking error rates in the dashboard.
Error rates should return to baseline within 2 minutes of the connection
string update.

4. Network Incidents

When network partitions are suspected, follow a different procedure...
"""


def run_demo() -> None:
    chunker = DocumentChunker(content_type="runbook")
    chunks = chunker.chunk(RUNBOOK_TEXT, doc_id="incident-runbook-v3",
                            extra_metadata={"source": "Incident_Response_Runbook.pdf"})

    print(f"Produced {len(chunks)} chunks (chunk_size={chunker.chunk_size}, "
          f"overlap={chunker.chunk_overlap})\n")

    for c in chunks:
        preview = c.text.replace("\n", " ")[:90]
        print(f"[{c.chunk_index}] section='{c.section_title}' "
              f"chars=[{c.char_start}:{c.char_end}]")
        print(f"     {preview}...\n")


if __name__ == "__main__":
    run_demo()
```

### Expected output (shape)

```
Produced 3 chunks (chunk_size=600, overlap=80)

[0] section='Database Incidents' chars=[0:601]
     Incident Response Runbook  3. Database Incidents  When the primary database becomes...

[1] section='Database Incidents' chars=[521:1123]
     completes within 10-15 seconds.  Step 4. Update the connection string in the config...

[2] section='Database Incidents' chars=[1043:1245]
     baseline within 2 minutes of the connection string update.  4. Network Incidents...
```

Notice chunk 1 starts partway *inside* Step 3's explanation (thanks to the 80-character overlap), so Step 3 and Step 4 both appear in full somewhere, even though the raw 600-character window would have otherwise split them awkwardly.

### Key production notes

- **Chunk size is content-dependent, not universal** — that's why this implementation uses presets per content type (`runbook` vs `policy_doc` vs `faq`) instead of one global constant. Tune presets by actually testing retrieval quality on real queries, not by guessing.
- **Character-based length here is an approximation** — many production systems chunk by *token count* (matching the embedding model's tokenizer) rather than character count, since embedding model limits are token-based. Swap `length_function=len` for a real tokenizer's `len(encode(text))` when precision matters.
- **Section-title tracking** is a small addition with a big payoff: it lets retrieval results show *where* an answer came from ("Section 3: Database Incidents"), which builds user trust and helps them verify the answer against the source.
- **The fragment-boundary warning** is a cheap sanity check that catches an entire class of chunking bugs (splitting mid-procedure) before they reach production — treat it as a linting step in your ingestion CI, not just a runtime print.
- Chunking interacts directly with Pattern 4 (Semantic Chunking) and Pattern 5 (Recursive Chunking) — this file is the general-purpose foundation; those two patterns specialize it further.

## 8. Pinned Dependency Versions

```txt
langchain-text-splitters==0.3.5
langchain-core==0.3.29
python>=3.11,<3.13
```

---

**Next:** say `next` and I'll build `04_Semantic_Chunking.md` — splitting based on meaning shifts detected via embeddings, instead of fixed size.
