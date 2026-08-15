# Pattern 12 — Contextual Retrieval

[← Back to index](./README.md) | Previous: [Pattern 11 — Context Deduplication](./11-context-deduplication.md)

---

## 1. Introduce the pattern

**Contextual Retrieval** solves a problem that sits *before* every other pattern in this series even gets a chance to work: making sure the chunks a retrieval system pulls back in the first place carry enough context to be meaningful **on their own**, separated from the document they came from.

Every pattern so far assumed retrieval already handed back sensible, self-contained candidate items. In a real RAG-style system, that assumption often doesn't hold. Documents get split into chunks for embedding and retrieval — and a naively-chunked passage frequently loses the very context that made it meaningful:

```
Original document (e.g. a case file, a policy manual):
  "... [Section 4.2: Beneficiary Risk Scoring] ...
   The threshold is 5x the account's 90-day average. This applies only
   when combined with a beneficiary added within the preceding 24 hours. ..."

Naive chunk (retrieved in isolation):
  "The threshold is 5x the account's 90-day average. This applies only
   when combined with a beneficiary added within the preceding 24 hours."
  → Threshold for WHAT? Which policy? Retrieved alone, this chunk has
    lost its own subject.

Contextually-retrieved chunk:
  "[From Policy FR-14, Section 4.2 — Beneficiary Risk Scoring, governing
   wire transfer holds] The threshold is 5x the account's 90-day average.
   This applies only when combined with a beneficiary added within the
   preceding 24 hours."
  → Self-contained. Means the same thing whether read in the full
    document or retrieved alone.
```

**Mental model:** if you photocopy a single paragraph out of the middle of a 40-page contract and hand it to someone with no other context, they often can't tell what it's actually about — "the threshold" referring to what, exactly? Contextual Retrieval is the discipline of never handing someone that bare paragraph; instead, you staple a short note to it — "this is from the wire-transfer-hold policy, section 4.2" — so it stands on its own.

---

## 2. The problem it solves

Standard chunk-and-embed RAG pipelines have a well-known failure mode that becomes visible only when you look closely at what gets retrieved:

1. **Chunking destroys referential context.** Documents naturally use pronouns, section-relative references ("this applies," "the above threshold," "as noted earlier"), and headers that establish topic — none of which survive being sliced into a fixed-size chunk. A chunk retrieved by embedding similarity can be topically relevant while being referentially meaningless on its own.
2. **This directly undermines every pattern earlier in this series.** Pattern 01's relevance scoring, Pattern 02's filtering, Pattern 04's ranking — all of it operates on the *content* of a chunk. If the chunk's content is "The threshold is 5x the account's 90-day average" with no indication of what policy or section it's from, even perfect relevance scoring and perfect ranking can't fix the fact that the LLM downstream has no idea what "the threshold" refers to.
3. **The problem is invisible in aggregate metrics but real in individual answers.** A RAG system can have excellent retrieval recall (it found the right chunk!) and still produce a wrong or confused answer, because "found the right chunk" and "the chunk is usable in isolation" are different properties — and only the second one actually determines answer quality.

Contextual Retrieval solves this at the point where chunks are created and indexed, not after the fact: each chunk is enriched with a short, situating context — derived from its source document — before it's embedded and stored, so that whatever gets retrieved later is self-contained by construction.

---

## 3. A realistic enterprise problem (Helios)

Helios's compliance policy library is a set of long, structured PDF-derived documents — "Fraud Response Policies," running many pages, organized into numbered sections. When this library is naively chunked (fixed-size splits) and embedded for retrieval, a query like *"what triggers a mandatory hold?"* can retrieve a chunk like:

> *"The threshold is 5x the account's 90-day average. This applies only when combined with a beneficiary added within the preceding 24 hours."*

This is exactly the passage the analyst needs — but retrieved in isolation, it doesn't say **which policy** it's from, **what it's the threshold for**, or **what happens once the threshold is met** (the actual hold requirement is stated two sentences earlier in the source document, outside this chunk's boundaries). Fed to the LLM as-is, the copilot either has to guess, hedge unhelpfully, or — worse — confidently attribute the rule to the wrong policy if a similarly-worded passage from a different section was also retrieved.

Contextual Retrieval is the pattern that ensures every chunk in Helios's policy index carries a short, document-derived situating context — "From Policy FR-14, Section 4.2, governing mandatory wire transfer holds" — baked in *before* it's ever embedded, so this failure mode can't occur regardless of how retrieval later ranks or selects it.

---

## 4. Architecture / flow diagram

```mermaid
flowchart TD
    A[Source document\n(e.g. Policy FR-14, full text)] --> B[Chunking\n(fixed-size or semantic splits)]
    B --> C[Raw chunks\n(context-poor in isolation)]

    C --> D[Contextualizer\nChatOllama: given the FULL document\n+ this chunk, write a 1-2 sentence\nsituating context]

    A -.->|full document provided\nas grounding, not just the chunk| D

    D --> E[Contextualized chunk =\nsituating context + original chunk text]
    E --> F[Embed contextualized chunk\n(nomic-embed-text)]
    F --> G[Index / Vector Store]

    H[Analyst query] --> I[Retrieve top-k\ncontextualized chunks]
    G --> I
    I --> J["Chunks are self-contained --\nfeed directly into Pattern 01\n(Selection) as ContextItems"]
    J --> K[... rest of the pipeline:\nFiltering, Compression, Ranking, ...]

    style D fill:#4A90D9,color:#fff
    style E fill:#4A90D9,color:#fff
```

---

## 5. The complete request-to-response flow

1. **Document ingestion (offline, at indexing time — not per-query)**: a source document (a compliance policy, a historical case writeup) is split into chunks using a standard chunking strategy (fixed-size, or semantic/section-aware splitting).
2. **Contextualization pass**: for each chunk, an LLM is given the chunk **plus the full source document** as grounding, and asked to produce a short (1–2 sentence) situating context — which document this is from, which section, what topic it governs — *without* restating the chunk's own content.
3. **Contextualized chunk assembly**: the situating context is prepended to the original chunk text, producing a new, self-contained unit — this is what actually gets embedded and stored, not the raw chunk.
4. **Indexing**: the contextualized chunk is embedded (`nomic-embed-text`, consistent with every other pattern in this series) and stored in the vector index, alongside metadata pointing back to the source document and section for traceability.
5. **Query time**: an analyst's query triggers retrieval as normal — but because every indexed chunk is already self-contained, whatever comes back is immediately usable without needing to chase down "which document was this from?" after the fact.
6. **Downstream integration**: retrieved contextualized chunks become `ContextItem`s exactly as in Pattern 01 — from this point on, every other pattern in the series (Filtering, Compression, Ranking, Prioritization, Deduplication) operates on them completely normally, because Contextual Retrieval's entire job was making sure that input is already trustworthy.

---

## 6. Why this pattern is appropriate here

- **It's an indexing-time investment that pays off on every future query.** The contextualization LLM call happens once per chunk, at ingestion time — not once per analyst query. This is a fundamentally different cost profile from every other pattern in this series (all of which run per-request): a modest, one-time cost per document that improves every future retrieval against it, indefinitely.
- **It fixes the problem at its source rather than compensating downstream.** You could imagine trying to work around context-poor chunks by having the LLM "figure it out" from surrounding retrieved chunks, or by asking Pattern 02's filtering to somehow detect and flag ambiguous references — but both of those are fragile compensations for a problem that's much more reliably solved by never producing an ambiguous chunk in the first place.
- **Grounding the contextualizer in the *full* document, not just the chunk, is the whole trick.** A summarizer given only the chunk has no more context than the chunk itself — it can't recover what section it's from. Giving the contextualization LLM the full source document as additional grounding (even though only the chunk plus a short situating sentence get embedded) is what makes this pattern actually work, and it's the detail most naive implementations get wrong.
- **It's the right closing pattern for this series because it's the pattern most other patterns implicitly depend on.** Selection (01) assumes candidates are meaningful. Ranking (04) assumes a chunk's content reflects its actual relevance. Deduplication (11) assumes two chunks that read similarly really do mean the same thing. All of that reasoning is only as good as the chunks it's operating on — Contextual Retrieval is what earns that assumption in the first place, which is why it belongs at the foundation, even though it's presented last in this curriculum.

---

## 7. Production-quality Python implementation

```python
"""
helios_contextual_retrieval.py

Pattern 12 — Contextual Retrieval
Helios Fraud Investigation Copilot

Indexing-time pipeline: chunks a source document, then uses an LLM --
grounded in the FULL document, not just the chunk -- to prepend a short
situating context to each chunk before it's embedded and stored. Retrieved
chunks are then self-contained ContextItems, usable directly by every
earlier pattern in this series (Selection, Filtering, Ranking, etc.)
without further special handling.

Requirements:
    pip install "langchain-core>=0.3.60" "langchain-ollama>=0.3.0" "numpy>=1.26"

Assumes a local Ollama server (`ollama serve`) with:
    ollama pull llama3.1
    ollama pull nomic-embed-text
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import datetime

import numpy as np
from langchain_core.prompts import ChatPromptTemplate
from langchain_ollama import ChatOllama, OllamaEmbeddings

logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")
logger = logging.getLogger("helios.contextual_retrieval")


def estimate_tokens(text: str) -> int:
    return max(1, len(text) // 4)


# --------------------------------------------------------------------------- #
# Chunking (simple fixed-size, sentence-aware splitter -- production systems
# often use section-aware splitting for structured documents like policies)
# --------------------------------------------------------------------------- #

def chunk_document(document_text: str, chunk_size_chars: int = 400, overlap_chars: int = 50) -> list[str]:
    """A simple sliding-window chunker. Splits on sentence boundaries where
    possible to avoid cutting mid-sentence. Real Helios policy documents,
    being section-structured, would ideally use a section-aware splitter --
    this generic version is included so the pattern is easy to adapt."""
    sentences = document_text.replace("\n", " ").split(". ")
    chunks: list[str] = []
    current = ""

    for sentence in sentences:
        sentence = sentence.strip()
        if not sentence:
            continue
        candidate = f"{current} {sentence}.".strip() if current else f"{sentence}."
        if len(candidate) > chunk_size_chars and current:
            chunks.append(current.strip())
            # start next chunk with a small overlap from the end of the previous one
            overlap = current[-overlap_chars:] if len(current) > overlap_chars else current
            current = f"{overlap} {sentence}.".strip()
        else:
            current = candidate

    if current.strip():
        chunks.append(current.strip())

    return chunks


# --------------------------------------------------------------------------- #
# Contextualizer — grounded in the FULL document, not just the chunk
# --------------------------------------------------------------------------- #

CONTEXTUALIZE_SYSTEM_PROMPT = """You add short situating context to document chunks so they can be
understood correctly even when retrieved in isolation, without the rest of the document.

You will be given the FULL source document and ONE chunk from it. Write a single, short sentence
(under 25 words) that identifies: which document/policy this is, and what topic or section this
chunk covers. Do NOT restate or summarize the chunk's own content -- only provide the situating
context that isn't already obvious from the chunk alone.

Output ONLY that one sentence. No preamble, no quotation marks."""


@dataclass
class ContextualizedChunk:
    chunk_id: str
    source_document: str
    situating_context: str
    raw_chunk_text: str
    timestamp: datetime = field(default_factory=datetime.utcnow)

    @property
    def full_text(self) -> str:
        """This is what actually gets embedded and shown to the LLM downstream --
        situating context + original chunk, fused into one self-contained unit."""
        return f"[{self.situating_context}] {self.raw_chunk_text}"

    @property
    def token_count(self) -> int:
        return estimate_tokens(self.full_text)


class ChunkContextualizer:
    def __init__(self, llm_model: str = "llama3.1") -> None:
        self.llm = ChatOllama(model=llm_model, temperature=0.0)
        self.prompt = ChatPromptTemplate.from_messages(
            [
                ("system", CONTEXTUALIZE_SYSTEM_PROMPT),
                ("human", "FULL DOCUMENT:\n{full_document}\n\nCHUNK TO CONTEXTUALIZE:\n{chunk}"),
            ]
        )

    def contextualize(self, source_document_name: str, full_document_text: str, chunk_text: str, chunk_id: str) -> ContextualizedChunk:
        chain = self.prompt | self.llm
        situating_context = chain.invoke(
            {"full_document": full_document_text, "chunk": chunk_text}
        ).content.strip()

        return ContextualizedChunk(
            chunk_id=chunk_id,
            source_document=source_document_name,
            situating_context=situating_context,
            raw_chunk_text=chunk_text,
        )


# --------------------------------------------------------------------------- #
# Indexing pipeline: chunk -> contextualize -> embed -> store
# --------------------------------------------------------------------------- #

class ContextualRetrievalIndex:
    """A minimal in-memory vector index for this worked example. In production,
    swap this for pgvector, Chroma, or another real vector store -- the
    contextualization step upstream of it is unchanged either way."""

    def __init__(self, embed_model: str = "nomic-embed-text", llm_model: str = "llama3.1") -> None:
        self.embeddings = OllamaEmbeddings(model=embed_model)
        self.contextualizer = ChunkContextualizer(llm_model=llm_model)
        self._chunks: list[ContextualizedChunk] = []
        self._vectors: list[np.ndarray] = []

    def index_document(self, document_name: str, document_text: str) -> None:
        raw_chunks = chunk_document(document_text)
        logger.info("Indexing '%s': split into %d chunk(s)", document_name, len(raw_chunks))

        for i, raw_chunk in enumerate(raw_chunks):
            chunk_id = f"{document_name}-chunk-{i}"
            contextualized = self.contextualizer.contextualize(document_name, document_text, raw_chunk, chunk_id)
            vector = np.array(self.embeddings.embed_query(contextualized.full_text))

            self._chunks.append(contextualized)
            self._vectors.append(vector)

            logger.info("  [%s] situating context: %r", chunk_id, contextualized.situating_context)

    def retrieve(self, query: str, top_k: int = 3) -> list[ContextualizedChunk]:
        if not self._chunks:
            return []

        query_vec = np.array(self.embeddings.embed_query(query))
        scored = [
            (self._cosine_similarity(query_vec, vec), chunk)
            for vec, chunk in zip(self._vectors, self._chunks)
        ]
        scored.sort(key=lambda pair: pair[0], reverse=True)
        return [chunk for _, chunk in scored[:top_k]]

    @staticmethod
    def _cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
        denom = (np.linalg.norm(a) * np.linalg.norm(b)) + 1e-8
        return float(np.dot(a, b) / denom)


# --------------------------------------------------------------------------- #
# Example run — index a Helios policy document, compare naive vs. contextual retrieval
# --------------------------------------------------------------------------- #

POLICY_FR14_TEXT = """
Policy FR-14: Wire Transfer Hold Requirements

Section 4.1 — Purpose. This policy establishes mandatory hold requirements for outbound
wire transfers exhibiting elevated fraud risk indicators, in order to allow adequate time
for secondary analyst review before funds are released.

Section 4.2 — Beneficiary Risk Scoring. The threshold is 5x the account's 90-day average.
This applies only when combined with a beneficiary added within the preceding 24 hours.
When both conditions are met, the transaction must be placed on a mandatory 48-hour hold
pending secondary review by a tier-2 fraud analyst.

Section 4.3 — Exemptions. Transfers to beneficiaries verified through the enhanced KYC
process, or transfers below $1,000 regardless of beneficiary status, are exempt from this
hold requirement.

Section 5.1 — Escalation. If secondary review confirms elevated risk, the case must be
escalated to the compliance team within 4 business hours of the hold being applied.
""".strip()


if __name__ == "__main__":
    index = ContextualRetrievalIndex()
    index.index_document("Policy-FR14", POLICY_FR14_TEXT)

    query = "What triggers a mandatory hold on a wire transfer?"

    print(f"\n=== Query: {query!r} ===\n")
    results = index.retrieve(query, top_k=2)

    for chunk in results:
        print(f"--- {chunk.chunk_id} ---")
        print(f"Situating context: {chunk.situating_context}")
        print(f"Raw chunk text:     {chunk.raw_chunk_text}")
        print(f"Full text sent to LLM (situating context + chunk, fused):")
        print(f"  {chunk.full_text}\n")

    # Feed the top contextualized chunk directly to the copilot -- note it needs
    # NO extra explanation of what document/section it came from, because that's
    # already baked into full_text.
    top_chunk = results[0]
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", "You are the Helios Fraud Investigation Copilot. Answer using ONLY the context given, "
                       "and cite the policy/section it came from."),
            ("human", "Analyst question: {query}\n\n--- RETRIEVED CONTEXT ---\n{context}"),
        ]
    )
    llm = ChatOllama(model="llama3.1", temperature=0.1)
    chain = prompt | llm
    response = chain.invoke({"query": query, "context": top_chunk.full_text})

    print("=== COPILOT ANSWER (grounded in a self-contained, contextually-retrieved chunk) ===")
    print(response.content)
```

### Notes on running this yourself

- Print `chunk.raw_chunk_text` alone for the top result and compare it against `chunk.full_text` — the raw chunk on its own is very likely to be missing the "which policy, which section" framing that makes it actually useful; the contextualized version fixes that without needing to change anything about retrieval itself.
- The contextualization LLM call happens **once per chunk at indexing time**, not per query — for a document library that changes rarely (as Helios's compliance policies do), this cost is trivial relative to how often the resulting chunks get retrieved and reused across every future analyst query.
- `chunk_document`'s simple sentence-aware splitter is a reasonable default; for genuinely structured documents (numbered sections, like the example policy text), a section-aware splitter that respects `Section X.Y` boundaries would produce more coherent chunks even before contextualization — contextualization compensates for imperfect chunking, but doesn't make chunking quality irrelevant.
- This pattern's output — a `ContextualizedChunk` with a `.full_text` property — is deliberately shaped so it can become a `ContextItem` (Pattern 01) with almost no adaptation: `full_text` maps directly to `content`, and `source_document`/`chunk_id` map to the kind of source/id metadata every earlier pattern in this series already expects.

---

## Series complete

This closes the 12-pattern Context Engineering series. Read together, Patterns 01–12 form a coherent pipeline:

**Contextual Retrieval (12)** ensures what gets indexed is meaningful in isolation → **Selection (01)** casts a relevance-scored net across sources → **Filtering (02)** and **Deduplication (11)** clean and de-noise → **Compression (03)** shrinks without losing facts → **Ranking (04)** and **Prioritization (05)** decide order and what survives a hard budget → **Window Management (06)**, **Caching (07)**, and **Summarization (10)** manage all of the above across a long-running session efficiently → **Isolation (08)** keeps it all safely partitioned across tenants and sub-agents → **Routing (09)** decides, up front, which of this machinery a given query even needs.

Every pattern was built against the same running example — Helios's fraud investigation copilot — deliberately, so you could see how they compose into one real system rather than staying twelve disconnected exercises.

[← Back to index](./README.md)
