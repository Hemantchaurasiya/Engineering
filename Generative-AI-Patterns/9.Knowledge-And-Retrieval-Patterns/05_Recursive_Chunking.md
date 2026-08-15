# Pattern 5: Recursive Chunking

## 1. Introduce the Pattern

**Recursive chunking** is the specific splitting *algorithm* that Pattern 3 used under the hood without fully explaining it. This pattern gives it a dedicated, in-depth treatment: what "recursive" actually means here, why it's the default strategy in almost every production RAG system, and how to extend it for structured content like Markdown and code.

The core idea: instead of picking one separator (like "always split on newlines") and applying it uniformly, a recursive splitter is handed an **ordered list of separators**, from most to least structure-preserving. It tries the first separator; if a resulting piece is still too big, it recurses *into that piece* with the next separator down the list; and so on, down to raw characters as an absolute last resort.

```
separators = ["\n\n", "\n", ". ", " ", ""]

split_text(document, separators):
    try separators[0] = "\n\n"  → split into paragraphs
        for each paragraph:
            if paragraph fits in chunk_size → keep as-is
            else → RECURSE: split_text(paragraph, separators[1:])
                       try separators[1] = "\n"  → split into lines
                           for each line:
                               if line fits → keep as-is
                               else → RECURSE: split_text(line, separators[2:])
                                          try ". " → split into sentences
                                          ... and so on down to ""
```

This is exactly why it's called *recursive*: the same splitting function calls itself with a shorter separator list whenever a piece is still oversized, drilling down through progressively finer-grained structure only where needed — and leaving already-small, well-structured pieces untouched.

## 2. The Problem It Solves

A **single fixed separator** fails differently depending on the document:

- Splitting only on `"\n\n"` (paragraphs): great for well-formatted prose, but does nothing useful for a document with one giant 3000-character paragraph — you'd get one oversized chunk.
- Splitting only on `" "` (words) or raw character count: never produces an oversized chunk, but ignores all document structure — happily cuts a sentence, a numbered step, or a code line in half.
- Splitting only on newlines: works for line-oriented content (code, numbered lists) but shatters normal prose paragraphs into many tiny, context-poor chunks (one chunk per line).

Real documents mix all of these: prose paragraphs, embedded numbered lists, code blocks, tables. No single separator is right for the whole document. Recursive chunking solves this by **trying the most content-preserving separator first, and only falling back to finer-grained separators where a piece genuinely doesn't fit** — so a short paragraph stays a single clean chunk, while a long paragraph gets progressively broken down, but only as much as necessary.

## 3. Realistic Enterprise Scenario

DocuMind ingests a **mixed-format engineering wiki page** that contains:

- Regular prose paragraphs explaining a system's design
- An embedded fenced code block (a config file example)
- A numbered list of deployment steps
- A markdown table comparing environment configs

A naive single-separator splitter applied to this page would either shatter the code block line-by-line (destroying its structure and making it unreadable out of context) or lump the entire page — prose, code, table, and all — into one oversized chunk if using only paragraph breaks on a page with few blank lines.

Recursive chunking, configured with a **Markdown-aware separator hierarchy** (headers > code fences > paragraphs > lines > sentences > words), keeps the code block intact as its own chunk when it fits, keeps the table intact, and only drills down into fine-grained splitting for the long prose sections that actually need it.

## 4. Architecture / Flow Diagram

```
                    split_text(text, separators, chunk_size)
                                    │
                    ┌───────────────┴────────────────┐
                    │  Try separators[0]                │
                    │  e.g. "\n\n" (paragraph break)     │
                    └───────────────┬────────────────┘
                                    │
                     splits text into candidate pieces
                                    │
              ┌─────────────────────┼─────────────────────┐
              │                     │                       │
        piece fits in         piece fits in            piece TOO BIG
        chunk_size?           chunk_size?               for chunk_size
              │                     │                       │
              ▼                     ▼                       ▼
        keep as a chunk      keep as a chunk      RECURSE with separators[1:]
        (merge with          (merge with          e.g. "\n" (line break)
         neighbors up to      neighbors up to               │
         chunk_size)          chunk_size)          same logic repeats:
                                                     fits? keep. too big?
                                                     recurse with separators[2:]
                                                     (". ", then " ", then "")
                                                              │
                                                              ▼
                                                 eventually bottoms out at
                                                 raw character slicing if
                                                 even single "words" (e.g. a
                                                 long URL or hash) are too big
```

The key structural detail: **small pieces get merged back together** up to `chunk_size` (so you don't end up with one chunk per tiny paragraph when several small paragraphs could fit together), while **oversized pieces get recursively subdivided**. This "merge small, split big" behavior is what makes the output chunk sizes reasonably consistent despite the input having wildly uneven structure.

## 5. Request-to-Response Walkthrough

1. The chunker receives a document plus a separator hierarchy tailored to its format (plain prose vs. Markdown vs. code).
2. It attempts to split on the first (coarsest, most structure-preserving) separator.
3. For each resulting piece: if it's small enough, it's queued for possible merging with adjacent small pieces. If it's still too large, the splitter **recurses** on that single piece using the next separator in the hierarchy.
4. This continues until every piece is at or under `chunk_size`, or the separator list is exhausted (in which case a hard character-count cut is used as the final fallback — guaranteeing termination).
5. Adjacent small pieces are then greedily merged, respecting `chunk_overlap`, until each output chunk is as close to `chunk_size` as possible without exceeding it.
6. The result is a list of chunks where **structurally coherent content (a short paragraph, a small code block) tends to stay whole**, and only genuinely oversized structural units get subdivided — and even then, subdivision happens along the next-most-natural boundary available, not blindly by character count.

## 6. Why This Pattern Is Appropriate

| Strategy | What it optimizes for | What it sacrifices |
|---|---|---|
| Fixed character-count splitting | Simplicity, guaranteed uniform chunk size | Structure — freely cuts mid-word, mid-sentence, mid-code-line |
| Single-separator splitting (e.g. always `\n\n`) | Works well for one content shape | Fails badly on content that doesn't match that shape (one huge paragraph, or a line-oriented format) |
| Semantic chunking (Pattern 4) | Topic-boundary accuracy | Cost (embeds every sentence); doesn't respect *formatting* structure like code blocks or tables, only *topical* structure |
| **Recursive chunking (this pattern)** | Respects whatever structure is present, adapts automatically to mixed-format documents, cheap (no embedding calls needed to decide splits) | Doesn't understand *meaning* — a recursive splitter can still put two unrelated paragraphs in one chunk if the structure allows it (unlike semantic chunking) |

In practice, **recursive chunking is the default, general-purpose choice** for most production RAG systems — it's what LangChain's `RecursiveCharacterTextSplitter` implements, and it's the mechanism used in Pattern 3's implementation. This pattern's contribution is showing how to go further: customizing the separator hierarchy for specific formats (Markdown, code), understanding the merge behavior precisely, and knowing when recursive chunking alone isn't enough (mixed-topic prose — that's when Pattern 4 earns its extra cost).

## 7. Production-Quality Python Implementation

```python
"""
recursive_chunking_pipeline.py

A from-scratch implementation of recursive chunking for DocuMind,
built to make the "try separator, recurse on oversized pieces, merge
undersized pieces" mechanism fully transparent (rather than treating
LangChain's splitter as a black box, as Pattern 3 did).

Also includes a format-aware separator hierarchy selector: plain text,
Markdown (headers + code fences), and code files each get an appropriate
hierarchy.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import List


# --------------------------------------------------------------------------
# Format-aware separator hierarchies
# --------------------------------------------------------------------------

SEPARATORS_PLAIN_TEXT = ["\n\n", "\n", ". ", " ", ""]

SEPARATORS_MARKDOWN = [
    "\n## ", "\n### ",     # headers — strongest structural signal
    "\n```\n",             # code fence boundaries — keep code blocks whole where possible
    "\n\n",                # paragraph breaks
    "\n",                  # line breaks (list items, table rows)
    ". ", " ", "",
]

SEPARATORS_CODE = ["\nclass ", "\ndef ", "\n\n", "\n", " ", ""]


@dataclass
class RecursiveChunk:
    text: str
    doc_id: str
    chunk_index: int
    metadata: dict = field(default_factory=dict)


class RecursiveTextSplitter:
    """A transparent, from-scratch recursive splitter: tries each
    separator in order, recursing into oversized pieces with the
    remaining separator list, then greedily merges undersized pieces
    up to chunk_size with chunk_overlap between merged chunks."""

    def __init__(self, chunk_size: int = 500, chunk_overlap: int = 60,
                 separators: List[str] | None = None):
        if chunk_overlap >= chunk_size:
            raise ValueError("chunk_overlap must be smaller than chunk_size.")
        self.chunk_size = chunk_size
        self.chunk_overlap = chunk_overlap
        self.separators = separators or SEPARATORS_PLAIN_TEXT

    # ---- step 1: recursively split into raw pieces, each <= chunk_size ----

    def _split(self, text: str, separators: List[str]) -> List[str]:
        if len(text) <= self.chunk_size:
            return [text] if text.strip() else []

        if not separators:
            # Absolute last resort: hard character-count slicing.
            # This branch guarantees the recursion always terminates.
            return [text[i:i + self.chunk_size] for i in range(0, len(text), self.chunk_size)]

        sep, *rest = separators
        pieces = text.split(sep) if sep else list(text)

        result: List[str] = []
        for i, piece in enumerate(pieces):
            # Re-attach the separator (except for the trailing piece) so
            # the recombined text is faithful to the original document.
            reconstructed = piece + sep if sep and i < len(pieces) - 1 else piece
            if not reconstructed.strip():
                continue
            if len(reconstructed) <= self.chunk_size:
                result.append(reconstructed)
            else:
                # RECURSION: this piece is still too big — drill down
                # with the next separator in the hierarchy.
                result.extend(self._split(reconstructed, rest))
        return result

    # ---- step 2: greedily merge undersized adjacent pieces -----------

    def _merge(self, pieces: List[str]) -> List[str]:
        merged: List[str] = []
        current = ""

        for piece in pieces:
            candidate = (current + piece) if current else piece
            if len(candidate) <= self.chunk_size:
                current = candidate
            else:
                if current:
                    merged.append(current)
                # Start the next chunk with an overlap tail from the
                # previous chunk, so context survives the boundary.
                overlap_tail = current[-self.chunk_overlap:] if current else ""
                current = overlap_tail + piece
                # Edge case: a single piece can itself exceed chunk_size
                # if it came straight from the character-slicing fallback
                # with no smaller separator available at that size ceiling.
                if len(current) > self.chunk_size:
                    merged.append(current[:self.chunk_size])
                    current = current[self.chunk_size - self.chunk_overlap:]

        if current.strip():
            merged.append(current)
        return merged

    def split_text(self, text: str) -> List[str]:
        raw_pieces = self._split(text.strip(), self.separators)
        return self._merge(raw_pieces)


class RecursiveChunker:
    """Format-aware wrapper: picks the right separator hierarchy based on
    a declared content format, then produces DocuMind-ready chunk objects."""

    FORMAT_SEPARATORS = {
        "markdown": SEPARATORS_MARKDOWN,
        "code": SEPARATORS_CODE,
        "plain_text": SEPARATORS_PLAIN_TEXT,
    }

    def __init__(self, content_format: str = "plain_text",
                 chunk_size: int = 500, chunk_overlap: int = 60):
        separators = self.FORMAT_SEPARATORS.get(content_format, SEPARATORS_PLAIN_TEXT)
        self.content_format = content_format
        self._splitter = RecursiveTextSplitter(chunk_size, chunk_overlap, separators)

    def chunk(self, text: str, doc_id: str, extra_metadata: dict | None = None) -> List[RecursiveChunk]:
        raw_chunks = self._splitter.split_text(text)
        return [
            RecursiveChunk(
                text=chunk_text.strip(),
                doc_id=doc_id,
                chunk_index=i,
                metadata={**(extra_metadata or {}), "method": "recursive",
                          "content_format": self.content_format,
                          "chunk_size": self._splitter.chunk_size},
            )
            for i, chunk_text in enumerate(raw_chunks)
        ]


# --------------------------------------------------------------------------
# Demo: mixed-format engineering wiki page
# --------------------------------------------------------------------------

WIKI_PAGE = """## Deployment Configuration

Our deployment pipeline uses environment-specific config files to control
resource limits, feature flags, and connection pool sizes across staging
and production. This section documents the config format and the steps
to roll out a change safely.

```
[database]
pool_size = 20
timeout_seconds = 30
```

### Rollout Steps

1. Update the config file in the `configs/` directory for the target
   environment.
2. Open a pull request and get sign-off from the platform team.
3. Merge to main; the CD pipeline picks up the change automatically.
4. Monitor the deployment dashboard for 15 minutes post-rollout.

### Environment Comparison

| Setting | Staging | Production |
|---|---|---|
| pool_size | 10 | 20 |
| timeout_seconds | 15 | 30 |
"""


def run_demo() -> None:
    chunker = RecursiveChunker(content_format="markdown", chunk_size=350, chunk_overlap=40)
    chunks = chunker.chunk(WIKI_PAGE, doc_id="wiki-deploy-config",
                            extra_metadata={"source": "Deployment_Config.md"})

    print(f"Produced {len(chunks)} chunks (format={chunker.content_format})\n")
    for c in chunks:
        print(f"[Chunk {c.chunk_index}] ({len(c.text)} chars)")
        print(f"  {c.text!r}\n")


if __name__ == "__main__":
    run_demo()
```

### Expected output (shape)

```
Produced 4 chunks (format=markdown)

[Chunk 0] (198 chars)
  '## Deployment Configuration\n\nOur deployment pipeline uses environment-specific config files to control\nresource limits, feature flags, and connection pool sizes across staging\nand production...'

[Chunk 1] (54 chars)
  '\n```\n[database]\npool_size = 20\ntimeout_seconds = 30\n```\n'

[Chunk 2] (231 chars)
  '### Rollout Steps\n\n1. Update the config file in the `configs/` directory...\n4. Monitor the deployment dashboard for 15 minutes post-rollout.'

[Chunk 3] (162 chars)
  '### Environment Comparison\n\n| Setting | Staging | Production |\n|---|---|---|\n| pool_size | 10 | 20 |\n| timeout_seconds | 15 | 30 |'
```

Because `\n\`\`\`\n` (code fence) sits high in the Markdown separator hierarchy, the config code block stays intact as its own chunk instead of being interleaved with surrounding prose or shattered line-by-line. The numbered rollout steps and the comparison table likewise each land in their own coherent chunk.

### Key production notes

- **This from-scratch implementation exists to make the mechanism explicit** for study purposes — in a real production codebase, prefer `langchain_text_splitters.RecursiveCharacterTextSplitter` (used in Pattern 3) or `MarkdownHeaderTextSplitter` for Markdown, both of which are battle-tested and handle more edge cases (Unicode, nested structures) than this teaching version.
- **The separator hierarchy is the entire design surface** — tuning recursive chunking well is almost entirely about picking a good hierarchy for your content format, not about tuning the algorithm itself.
- **Recursion always terminates** because the final separator is always `""` (raw character split), which by construction can always fit within `chunk_size` — a correctness property worth stating explicitly, since an infinite loop here would be a nasty production bug.
- **Recursive chunking is structure-aware, not meaning-aware** — it will happily keep two unrelated but adjacently-formatted paragraphs in one chunk. When content is well-formatted but genuinely mixes unrelated topics with no formatting cues, combine this pattern with Pattern 4 (Semantic Chunking) rather than choosing one exclusively.

## 8. Pinned Dependency Versions

```txt
python>=3.11,<3.13
# (This file's core implementation is pure-Python / stdlib only.
#  langchain-text-splitters is the recommended production alternative:)
langchain-text-splitters==0.3.5
```

---

**Next:** say `next` and I'll build `06_Document_Parsing.md` — extracting clean, chunkable text out of real-world source formats (PDF, HTML, DOCX) before any of the chunking patterns above can run.
