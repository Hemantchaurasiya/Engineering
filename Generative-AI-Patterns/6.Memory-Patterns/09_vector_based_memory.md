# Pattern 9: Vector-Based Memory

> Part of the **Memory Patterns** series (Section 6). Stack: `langchain-ollama` (ChatOllama + OllamaEmbeddings), `langgraph`, `langchain-chroma`, local models (`llama3.1`, `nomic-embed-text`).

---

## 1. Introduce the Pattern

**Vector-Based Memory** is the general-purpose infrastructure pattern for storing *arbitrary* content — notes, documents, transcripts, past messages, anything text-shaped — as embeddings in a vector store, and retrieving the most relevant pieces by similarity search whenever needed.

You've already *used* this machinery three times in this series: Semantic Memory (Pattern 4, facts), Episodic Memory (Pattern 5, events), and Procedural Memory (Pattern 6, action sequences) are all **specific applications** of vector-based storage/retrieval to a particular *kind* of content. Pattern 9 zooms out to the underlying pattern itself — the decisions that determine whether *any* of those specialized patterns actually work well in production:

- **How do you chunk** long content before embedding it? (too big → poor retrieval precision; too small → lost context)
- **How do you index and filter** at scale, with metadata (source, date, owner)?
- **How do you decide what counts as "relevant enough"** to retrieve — top-k, a similarity threshold, or both?
- **How do you keep the vector store itself healthy** — persistence, re-indexing, avoiding duplicate content?

This pattern treats the vector store as a general **"memory substrate"** — a flexible, content-agnostic store-and-retrieve mechanism that other, more specialized memory patterns build on top of.

---

## 2. Problem It Solves

Without a well-designed vector-based memory layer, naive approaches break down in predictable ways:

1. **Whole-document embedding.** Embedding an entire 10-page document as one vector produces a single, blurry "average meaning" — a query about one specific paragraph on page 7 won't match well, because the embedding represents the *whole* document, diluting the specific signal.
2. **No chunk overlap.** Splitting text into disjoint chunks with no overlap can sever a sentence or idea right at a chunk boundary, so neither chunk fully captures it — and neither one is retrieved well for a query about that idea.
3. **Unbounded, unfiltered retrieval.** Returning "top 20 similar chunks" regardless of how similar they actually are floods the prompt with weak matches, reintroducing the context-bloat problem from Pattern 2.
4. **No metadata scoping.** Without filtering by source/owner/date at the vector-store level, a multi-user system risks retrieving another user's content, or answering with a document that's since been superseded.

Vector-Based Memory, done properly, solves this with **deliberate chunking, overlap, metadata tagging, and threshold-aware retrieval** — the foundational discipline that makes every content-specific memory pattern (semantic, episodic, procedural, and later, retrieval/compression patterns) actually reliable.

---

## 3. Realistic Production/Enterprise Scenario

**Scenario: "NotesMind" — a personal knowledge assistant for meeting notes and reference documents**

- Users paste in raw content over time: meeting notes, long email threads, policy documents, project write-ups — arbitrary length, arbitrary structure.
- Later, they ask natural questions: *"What did we decide about the Q3 marketing budget?"*, *"What's our policy on remote work stipends?"* — and expect the assistant to find and cite the *right specific passage*, not the whole document, and not an unrelated one.
- Content keeps growing over months; the system must scale gracefully — a linear scan over raw text would become too slow and imprecise.
- Each user's notes must remain private to them — no cross-user leakage in retrieval.

This scenario is the cleanest possible demonstration of Vector-Based Memory as **general infrastructure**: unlike Patterns 4-6, the *content itself* here has no predetermined shape (it's not "facts" or "episodes" or "procedures" specifically) — it's just "whatever the user pasted in," which is exactly the case general-purpose vector memory is built for.

---

## 4. Architecture / Flow Diagram

```
     INGESTION PATH                                RETRIEVAL PATH
┌─────────────────────────┐              ┌─────────────────────────────┐
│  User pastes long note     │              │  User asks: "What did we       │
│  or document                │              │  decide about the Q3 budget?"  │
└───────────┬─────────────┘              └───────────┬─────────────────────┘
            │                                          │
            ▼                                          ▼
┌─────────────────────────┐              ┌─────────────────────────────┐
│  chunk_node                 │              │  retrieve_node                  │
│  RecursiveCharacterText-    │              │  embed query -> similarity      │
│  Splitter                   │              │  search with score threshold    │
│  chunk_size=800,             │              │  + metadata filter               │
│  overlap=120                  │              │  (user_id=..., optionally         │
└───────────┬─────────────┘              │   source/date filters)             │
            │                                          └───────────┬─────────────────┘
            ▼                                                       │  relevant chunks
┌─────────────────────────┐                                       ▼
│  embed + store_node          │                          ┌─────────────────────────┐
│  OllamaEmbeddings            │                          │  chat_node                  │
│  (nomic-embed-text)           │                          │  ChatOllama answers using    │
│  metadata: {user_id, source,   │                          │  ONLY retrieved chunks,       │
│  chunk_index, ingested_at}      │                          │  cites source if known         │
└───────────┬─────────────┘                          └─────────────────────────┘
            ▼
┌─────────────────────────────────────────────────────────┐
│                Chroma vector store (persistent)               │
│  collection: "notes"                                           │
│  many small chunks per document, each independently searchable   │
└─────────────────────────────────────────────────────────┘
```

**Key idea:** ingestion and retrieval are **two independent paths sharing one store**. The quality of the whole system hinges on chunking decisions made at ingestion time and threshold/filter decisions made at retrieval time — this pattern is fundamentally about getting *both halves* right, not just "call a vector store."

---

## 5. Complete Request-to-Response Flow

**Ingestion (happens once, when content is added):**

1. **User pastes** a long meeting-notes document.
2. **`chunk_node`** splits it using `RecursiveCharacterTextSplitter(chunk_size=800, chunk_overlap=120)` — chunks respect paragraph/sentence boundaries where possible, and overlapping windows ensure an idea spanning a chunk boundary is still fully captured in at least one chunk.
3. **`embed_and_store_node`** embeds each chunk via `OllamaEmbeddings(model="nomic-embed-text")` and stores it in Chroma with metadata: `{user_id, source="Q3 planning meeting", chunk_index, ingested_at}`.

**Retrieval (happens on every question):**

1. **User asks** `"What did we decide about the Q3 marketing budget?"`
2. **`retrieve_node`** embeds the question and runs `similarity_search_with_score(query, k=5, filter={"user_id": "u_1"})`, then **discards any result below a similarity threshold** — so a weak, barely-related match doesn't get included just to fill out `k=5`.
3. Suppose 2 chunks pass the threshold — both from the "Q3 planning meeting" note, one mentioning the budget figure, one mentioning who approved it.
4. **`chat_node`** calls `ChatOllama.invoke(...)` with those 2 chunks (and their source metadata) injected into the prompt, instructed to answer *only* from the provided chunks and to say so if the answer isn't in them.
5. **Response returned**, grounded specifically in the relevant passage — not the whole document, not an unrelated note.

---

## 6. Why This Pattern Is Appropriate

| Requirement | How Vector-Based Memory satisfies it |
|---|---|
| Precise retrieval from long, unstructured content | Chunking + embeddings let similarity search target specific passages, not whole documents |
| Scales to large, growing content volumes | Vector indexes stay fast and relevant even as stored content grows into the thousands of chunks |
| Avoids weak/irrelevant context in the prompt | Similarity-score thresholding filters out poor matches, not just capping at `k` |
| Multi-tenant safety | Metadata filtering scopes retrieval to the right user/source |

**Trade-offs / when it's not enough:**
- Vector-Based Memory is **content-agnostic infrastructure** — it doesn't know or care whether a chunk is a fact, an event, or a procedure. That's exactly why Patterns 4-6 layer *structure* (JSON schemas, extraction steps, merge policies) on top of this same underlying mechanism for their specific needs. If your content genuinely doesn't need that structure (e.g., freeform notes), this pattern alone is often sufficient.
- Pure similarity search can miss exact keyword matches (e.g., an exact product SKU or error code) that a keyword/BM25 search would catch instantly — production systems often combine vector search with keyword search ("hybrid retrieval"), a technique explored further in **Memory Retrieval** (Pattern 13).
- This pattern doesn't address *when* to re-chunk/re-embed content that's edited after ingestion, or how to avoid duplicate/near-duplicate chunks accumulating over time — those concerns are picked up by **Memory Consolidation** (Pattern 14) and **Memory Compression** (Pattern 15).

---

## 7. Production-Quality Python Implementation

### Requirements

```bash
pip install -U langchain-ollama langgraph langchain-core langchain-chroma langchain-text-splitters chromadb
# Ollama models used locally:
#   ollama pull llama3.1
#   ollama pull nomic-embed-text
```

### `vector_based_memory.py`

```python
"""
Pattern 9: Vector-Based Memory
General-purpose chunk-embed-store-retrieve infrastructure for arbitrary
text content, built on langchain-chroma + langchain-ollama.

Run:
    python vector_based_memory.py
"""

from __future__ import annotations

import logging
import uuid
from datetime import datetime, timezone
from typing import Annotated, TypedDict

from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    SystemMessage,
)
from langchain_ollama import ChatOllama, OllamaEmbeddings
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("vector_based_memory")

CHUNK_SIZE = 800
CHUNK_OVERLAP = 120
RETRIEVE_K = 5
SIMILARITY_DISTANCE_THRESHOLD = 0.6  # Chroma: lower distance = more similar; tune per embedding model


def build_llm(model: str = "llama3.1", temperature: float = 0.1) -> ChatOllama:
    return ChatOllama(model=model, temperature=temperature, keep_alive="10m")


def build_embeddings(model: str = "nomic-embed-text") -> OllamaEmbeddings:
    return OllamaEmbeddings(model=model)


def build_vectorstore(embeddings: OllamaEmbeddings, persist_dir: str = "./vector_memory_db") -> Chroma:
    return Chroma(
        collection_name="notes",
        embedding_function=embeddings,
        persist_directory=persist_dir,
    )


# --------------------------------------------------------------------------
# 1. Ingestion path: chunk + embed + store arbitrary content.
#    This is a standalone function, not a graph node -- ingestion happens
#    on its own schedule (whenever a user adds content), separate from
#    the per-question retrieval graph below.
# --------------------------------------------------------------------------
def ingest_content(vectorstore: Chroma, user_id: str, source: str, text: str) -> int:
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=CHUNK_SIZE,
        chunk_overlap=CHUNK_OVERLAP,
        separators=["\n\n", "\n", ". ", " ", ""],  # prefer breaking at paragraph/sentence boundaries
    )
    chunks = splitter.split_text(text)

    docs = [
        Document(
            page_content=chunk,
            metadata={
                "user_id": user_id,
                "source": source,
                "chunk_index": i,
                "ingested_at": datetime.now(timezone.utc).isoformat(),
            },
            id=str(uuid.uuid4()),
        )
        for i, chunk in enumerate(chunks)
    ]
    vectorstore.add_documents(docs)
    logger.info("Ingested %d chunk(s) from source=%r for user=%r", len(docs), source, user_id)
    return len(docs)


# --------------------------------------------------------------------------
# 2. Graph state for the retrieval/answer path.
# --------------------------------------------------------------------------
class NotesQAState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]
    user_id: str
    retrieved_chunks: list[dict]  # [{content, source}]


BASE_SYSTEM_PROMPT = (
    "You are NotesMind, a personal knowledge assistant. Answer ONLY using "
    "the provided note excerpts below. If the answer isn't in them, say you "
    "don't have that information in the user's notes -- never guess. Cite "
    "the source of each fact you use."
)


# --------------------------------------------------------------------------
# 3. Node: retrieve relevant chunks, applying BOTH a top-k cap AND a
#    similarity-score threshold -- weak matches are dropped, not padded in.
# --------------------------------------------------------------------------
def make_retrieve_node(vectorstore: Chroma):
    def retrieve_node(state: NotesQAState) -> NotesQAState:
        last_human = next(
            (m for m in reversed(state["messages"]) if isinstance(m, HumanMessage)),
            None,
        )
        if last_human is None:
            return {"retrieved_chunks": []}

        results = vectorstore.similarity_search_with_score(
            last_human.content,
            k=RETRIEVE_K,
            filter={"user_id": state["user_id"]},
        )

        relevant = [
            {"content": doc.page_content, "source": doc.metadata.get("source", "unknown")}
            for doc, distance in results
            if distance <= SIMILARITY_DISTANCE_THRESHOLD
        ]

        logger.info(
            "Retrieved %d/%d chunk(s) above similarity threshold for user=%r",
            len(relevant), len(results), state["user_id"],
        )
        return {"retrieved_chunks": relevant}

    return retrieve_node


# --------------------------------------------------------------------------
# 4. Node: answer grounded strictly in the retrieved chunks.
# --------------------------------------------------------------------------
def make_chat_node(llm: ChatOllama):
    def chat_node(state: NotesQAState) -> NotesQAState:
        if state["retrieved_chunks"]:
            context_block = "\n\n".join(
                f"[Source: {c['source']}]\n{c['content']}" for c in state["retrieved_chunks"]
            )
        else:
            context_block = "(no sufficiently relevant notes found)"

        system_prompt = SystemMessage(content=f"{BASE_SYSTEM_PROMPT}\n\nRelevant note excerpts:\n{context_block}")

        try:
            response: AIMessage = llm.invoke([system_prompt, *state["messages"]])
        except Exception:
            logger.exception("LLM call failed")
            response = AIMessage(content="Sorry, I hit an error. Please try again.")

        return {"messages": [response]}

    return chat_node


# --------------------------------------------------------------------------
# 5. Assemble the retrieval/answer graph.
# --------------------------------------------------------------------------
def build_graph(persist_dir: str = "./vector_memory_db"):
    llm = build_llm()
    embeddings = build_embeddings()
    vectorstore = build_vectorstore(embeddings, persist_dir)

    graph_builder = StateGraph(NotesQAState)
    graph_builder.add_node("retrieve_node", make_retrieve_node(vectorstore))
    graph_builder.add_node("chat_node", make_chat_node(llm))
    graph_builder.add_edge(START, "retrieve_node")
    graph_builder.add_edge("retrieve_node", "chat_node")
    graph_builder.add_edge("chat_node", END)

    return graph_builder.compile(), vectorstore


# --------------------------------------------------------------------------
# 6. Service wrapper: exposes both ingestion and Q&A.
# --------------------------------------------------------------------------
class VectorBasedMemoryService:
    def __init__(self, persist_dir: str = "./vector_memory_db"):
        self.graph, self.vectorstore = build_graph(persist_dir)

    def add_note(self, user_id: str, source: str, text: str) -> int:
        return ingest_content(self.vectorstore, user_id, source, text)

    def ask(self, user_id: str, question: str) -> str:
        result = self.graph.invoke(
            {
                "messages": [HumanMessage(content=question)],
                "user_id": user_id,
                "retrieved_chunks": [],
            }
        )
        return result["messages"][-1].content


# --------------------------------------------------------------------------
# 7. Demo: ingest a meeting note, ask a targeted question that should
#    retrieve the right passage, and confirm out-of-scope questions
#    are honestly declined.
# --------------------------------------------------------------------------
if __name__ == "__main__":
    service = VectorBasedMemoryService()
    user = "u_1"

    meeting_note = """
Q3 Planning Meeting - March 14

Attendees: Priya, Marcus, Dana

We reviewed the Q3 roadmap. Marketing requested a budget increase for
the Q3 campaign. After discussion, we approved a Q3 marketing budget of
$120,000, up from $90,000 in Q2, primarily to fund the trade show
presence in September. Dana will own the trade show logistics.

Engineering update: the new onboarding flow is on track for a July 15
release. No blockers reported.

Action items:
- Dana: finalize trade show vendor contracts by April 1
- Marcus: send updated budget breakdown to finance by March 20
""".strip()

    service.add_note(user, source="Q3 Planning Meeting (March 14)", text=meeting_note)

    print("--- Targeted question, should retrieve the budget passage ---")
    print(service.ask(user, "What did we decide about the Q3 marketing budget?"))

    print("\n--- Out-of-scope question, should be honestly declined ---")
    print(service.ask(user, "What's our company's parental leave policy?"))
```

### Notes on production-readiness in this code

- **`RecursiveCharacterTextSplitter` with overlap** — `chunk_overlap=120` ensures an idea spanning a chunk boundary (e.g., "we approved a Q3 marketing budget of $120,000" straddling two chunks) still appears fully within at least one chunk, improving retrieval precision.
- **Separate ingestion and retrieval paths** — `ingest_content` is a plain function called whenever new content arrives (not part of the per-question graph), reflecting that ingestion and querying happen on entirely different, independent schedules in production.
- **Similarity threshold, not just top-k** (`SIMILARITY_DISTANCE_THRESHOLD`) — prevents weak, barely-relevant chunks from being force-included just because `k=5` was requested; the out-of-scope demo question above should retrieve nothing useful and the model should say so, rather than hallucinating from unrelated notes.
- **Rich metadata on every chunk** (`user_id`, `source`, `chunk_index`, `ingested_at`) supports both correctness (multi-tenant filtering) and future capabilities (e.g., "show me notes from before April," recency-aware retrieval — a hook toward Pattern 13).
- **Grounded-answer instruction with explicit "say you don't know"** in the system prompt is essential for any retrieval-based memory system — it's the difference between a trustworthy assistant and one that quietly fabricates when retrieval comes up empty.

---

## 8. Version Pins (latest stable, compatible, at time of writing)

```
python                    >= 3.11
langchain-core            >= 0.3.0
langchain-ollama          >= 0.2.0
langchain-chroma          >= 0.1.4
langchain-text-splitters  >= 0.3.0
chromadb                  >= 0.5.0
langgraph                 >= 0.2.0
```

```bash
pip install -U "langchain-core>=0.3.0" "langchain-ollama>=0.2.0" "langchain-chroma>=0.1.4" "langchain-text-splitters>=0.3.0" "chromadb>=0.5.0" "langgraph>=0.2.0"
```

> Requires both Ollama models pulled locally: `ollama pull llama3.1` (generation) and `ollama pull nomic-embed-text` (embeddings). `SIMILARITY_DISTANCE_THRESHOLD` should be re-tuned if you swap embedding models, since raw distance scales differ between embedding spaces.

---

**Next up → Pattern 10: External Memory** (offloading memory to systems *outside* the LLM/vector-store pipeline entirely — databases, APIs, file systems, knowledge graphs — treated as a queryable memory source).
