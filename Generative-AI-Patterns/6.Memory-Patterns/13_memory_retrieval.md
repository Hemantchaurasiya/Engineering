# Pattern 13: Memory Retrieval

> Part of the **Memory Patterns** series (Section 6). Stack: `langchain-ollama` (ChatOllama + OllamaEmbeddings), `langgraph`, `langchain-chroma`, `rank_bm25`, local models (`llama3.1`, `nomic-embed-text`).

---

## 1. Introduce the Pattern

**Memory Retrieval** is a focused, cross-cutting pattern about **how well you pull relevant content back out** of any durable memory store — going beyond plain top-k vector similarity (used throughout Patterns 4, 5, 6, 9) to combine multiple retrieval strategies and rank the results properly.

Every store-based pattern so far has used a version of "embed the query, run `similarity_search`, take the top few." That's a fine *default*, but it has a well-known blind spot: **pure embedding similarity is bad at exact matches**. If someone searches for `"ERR-4092"` (an exact error code), a semantic embedding might rank a conceptually-similar-but-differently-worded passage *above* the document that literally contains the string `"ERR-4092"` — because embeddings capture meaning, not exact tokens.

Memory Retrieval as a pattern means combining:
- **Keyword/lexical search** (e.g., BM25) — excellent at exact term/ID/code matches
- **Vector/semantic search** (Patterns 4/9's approach) — excellent at conceptual/paraphrased matches
- **Fusion/re-ranking** — merging both result lists into one ranked list that gets the best of both

This is often called **hybrid retrieval**, and it's the production-grade answer to "why didn't it find the thing I know is in there?" — one of the most common real-world complaints about memory/RAG systems that rely on vector search alone.

---

## 2. Problem It Solves

Pure vector search, used throughout this series so far, systematically underperforms on certain query types:

```
Knowledge base contains: "Error ERR-4092 occurs when the auth token
                           cache expires mid-request."

User query: "What does error ERR-4092 mean?"

[Pure vector search]
Query embedding is close to OTHER passages that discuss "error meanings"
conceptually, but the passage containing the LITERAL STRING "ERR-4092"
might rank #4 or #5, not #1 -- because embeddings represent overall
semantic meaning, and a short alphanumeric code doesn't carry much
distinct "meaning" in embedding space compared to surrounding prose.
```

Real production knowledge bases are full of exactly this kind of content: ticket numbers, SKUs, error codes, product model numbers, exact names — content where a user's query and the right passage share an **exact token**, and pure semantic similarity can miss it or rank it too low.

The opposite failure also happens: pure **keyword** search fails on paraphrase — a user asking *"why did my payment fail"* won't keyword-match a document that only says *"transaction declined due to insufficient funds,"* even though they mean the same thing.

Memory Retrieval solves this by running **both** search strategies and **fusing** their rankings, so a query gets the benefit of exact-match precision *and* semantic recall, whichever the specific query needs.

---

## 3. Realistic Production/Enterprise Scenario

**Scenario: "KnowledgeHub" — an internal engineering knowledge base search assistant**

- Engineers search a large, growing knowledge base of postmortems, runbooks, and documentation.
- Some queries are **exact**: *"what is ERR-4092,"* *"runbook for SVC-Billing-7,"* — these need precise keyword matching to find the document containing that literal identifier.
- Other queries are **conceptual**: *"why do our payment webhooks sometimes not fire,"* — these need semantic search to find relevant content even when the exact wording differs.
- A single retrieval strategy consistently disappoints one class of query or the other. The team has had repeated complaints: *"I know we have a doc about ERR-4092, why didn't the assistant find it?"*
- This is a **retrieval-quality** problem sitting on top of already-correct storage (Pattern 9's vector store); the fix is in *how* results are retrieved and ranked, not in how they're stored.

This is exactly what Memory Retrieval as a distinct pattern addresses: **the retrieval layer deserves its own deliberate design, separate from the storage layer**, because storage-agnostic retrieval quality problems (exact-match blind spots, poor ranking) show up regardless of which specific memory pattern (semantic, episodic, vector-based) is storing the content.

---

## 4. Architecture / Flow Diagram

```
                         ┌───────────────────────────────────────────┐
                         │   Query: "what is ERR-4092?"                  │
                         └───────────────────┬───────────────────────────┘
                                              │
                    ┌─────────────────────────┴─────────────────────────┐
                    ▼                                                    ▼
        ┌─────────────────────────┐                       ┌─────────────────────────┐
        │   Keyword search (BM25)     │                       │   Vector search (Chroma)    │
        │   ranks by exact term         │                       │   ranks by embedding          │
        │   overlap/frequency            │                       │   similarity                    │
        │                                │                       │                                 │
        │   #1 "...ERR-4092 occurs...."  │                       │   #1 "auth failures generally.." │
        │   #2 "...error handling..."     │                       │   #2 "...ERR-4092 occurs..."      │
        │   #3 ...                        │                       │   #3 ...                          │
        └────────────┬─────────────┘                       └────────────┬─────────────┘
                      │                                                    │
                      └───────────────────┬────────────────────────────────┘
                                            ▼
                         ┌───────────────────────────────────────────┐
                         │       Reciprocal Rank Fusion (merge)          │
                         │   combines both ranked lists into ONE          │
                         │   ranked list, boosting docs that rank         │
                         │   well in EITHER (or both) strategies           │
                         │                                                │
                         │   Final #1: "...ERR-4092 occurs..." (ranked      │
                         │   #1 keyword AND #2 vector -> highest fused)      │
                         └───────────────────┬───────────────────────────┘
                                              ▼
                         ┌───────────────────────────────────────────┐
                         │              chat_node -> ChatOllama            │
                         │  answers grounded in the TOP fused result(s)     │
                         └───────────────────────────────────────────┘
```

**Key idea:** the two retrievers run **independently** and see different top results — the fusion step is what produces a single, better-ranked list than either strategy alone would, using **Reciprocal Rank Fusion (RRF)**, a simple, robust, no-training-required method for combining ranked lists.

---

## 5. Complete Request-to-Response Flow

For the query `"what is ERR-4092?"`:

1. **`keyword_search_node`** runs a BM25 search over the knowledge base's raw text, ranking documents by lexical term overlap. The document literally containing `"ERR-4092"` ranks **#1** here, because BM25 rewards exact term matches heavily.
2. **`vector_search_node`** runs `Chroma.similarity_search` in parallel, ranking documents by embedding similarity. The same document might rank **#2** here (a different document about general auth error handling ranks #1, since it's more "semantically prototypical" of an error-related query).
3. **`fuse_results_node`** applies **Reciprocal Rank Fusion**: for each document, it sums `1 / (k + rank)` across both lists (a standard RRF constant, e.g. `k=60`). The `ERR-4092` document, ranking #1 in keyword search and #2 in vector search, accumulates a **higher fused score** than any document that only ranked well in one list — pushing it to the **final #1 position**.
4. **`chat_node`** receives the top-fused documents and calls `ChatOllama.invoke(...)`, now correctly grounded in the `ERR-4092` passage.
5. **Response returned**: an accurate explanation of what `ERR-4092` means, pulled from the document that both search strategies agreed was relevant (even though neither strategy alone ranked it #1 confidently).

For a **different**, purely conceptual query like *"why do payment webhooks sometimes not fire"* — the keyword search might return weak/irrelevant matches (no shared exact terms), but the vector search ranks the right conceptual passage highly; RRF still surfaces it correctly because it only needs to rank well in *one* of the two lists to be pulled toward the top.

---

## 6. Why This Pattern Is Appropriate

| Requirement | How Memory Retrieval (hybrid) satisfies it |
|---|---|
| Exact-match queries (IDs, codes, names) succeed reliably | BM25/keyword search excels here, where pure vector search is weak |
| Paraphrased/conceptual queries still succeed | Vector search excels here, where pure keyword search is weak |
| No single point of retrieval failure | Fusion means a document only needs to rank well in *one* strategy to surface |
| No retraining or complex ML needed | RRF is a simple, well-established formula — no learned re-ranker required |

**Trade-offs / when it's not enough:**
- Hybrid search still doesn't understand **freshness/recency** on its own — a stale, outdated document can rank just as well as an up-to-date one; combining Memory Retrieval with recency-weighting (as touched on in Pattern 5's episodic timestamps) is a natural extension.
- For very high-precision needs, production systems sometimes add a **learned re-ranker** (a cross-encoder model that scores query-document pairs directly) as a final stage after RRF fusion — a further refinement beyond what's shown here, useful when RRF's simple rank-based fusion isn't precise enough.
- Running two retrieval strategies costs more (two searches instead of one) — for very latency-sensitive systems, this trade-off needs to be weighed against the retrieval-quality gains, though both BM25 and vector search are typically fast enough that this is rarely prohibitive.

---

## 7. Production-Quality Python Implementation

### Requirements

```bash
pip install -U langchain-ollama langgraph langchain-core langchain-chroma langchain-community rank_bm25 chromadb
# Ollama models used locally:
#   ollama pull llama3.1
#   ollama pull nomic-embed-text
```

### `memory_retrieval.py`

```python
"""
Pattern 13: Memory Retrieval
Hybrid retrieval combining keyword search (BM25) and vector similarity
search, merged via Reciprocal Rank Fusion, built on langchain-chroma +
langchain-community + langchain-ollama.

Run:
    python memory_retrieval.py
"""

from __future__ import annotations

import logging
from typing import Annotated, TypedDict

from langchain_chroma import Chroma
from langchain_community.retrievers import BM25Retriever
from langchain_core.documents import Document
from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    SystemMessage,
)
from langchain_ollama import ChatOllama, OllamaEmbeddings
from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("memory_retrieval")

RRF_K = 60          # standard Reciprocal Rank Fusion smoothing constant
TOP_N_PER_STRATEGY = 5
FINAL_TOP_N = 3


# --------------------------------------------------------------------------
# 1. Seed knowledge base content, spanning both "exact identifier" and
#    "conceptual" query types, to demonstrate hybrid retrieval's value.
# --------------------------------------------------------------------------
KB_DOCS = [
    Document(
        page_content=(
            "ERR-4092 occurs when the auth token cache expires mid-request. "
            "Fix: increase the token cache TTL or implement pre-emptive refresh."
        ),
        metadata={"doc_id": "kb-101", "title": "ERR-4092 Reference"},
    ),
    Document(
        page_content=(
            "General guidance on handling authentication failures: retry with "
            "exponential backoff, log the failure reason, and alert if the "
            "failure rate exceeds 5% over 10 minutes."
        ),
        metadata={"doc_id": "kb-102", "title": "Auth Failure Handling Guide"},
    ),
    Document(
        page_content=(
            "Payment webhooks may silently fail to fire if the receiving "
            "endpoint returns a non-2xx status without retry logic on our "
            "side. Check webhook delivery logs and enable retry-on-failure."
        ),
        metadata={"doc_id": "kb-103", "title": "Payment Webhook Troubleshooting"},
    ),
    Document(
        page_content=(
            "SVC-Billing-7 runbook: to restart, drain traffic, scale to zero, "
            "scale back to 3 replicas, verify health endpoint returns 200."
        ),
        metadata={"doc_id": "kb-104", "title": "SVC-Billing-7 Runbook"},
    ),
]


def build_llm(model: str = "llama3.1", temperature: float = 0.1) -> ChatOllama:
    return ChatOllama(model=model, temperature=temperature, keep_alive="10m")


def build_embeddings(model: str = "nomic-embed-text") -> OllamaEmbeddings:
    return OllamaEmbeddings(model=model)


def build_vectorstore(embeddings: OllamaEmbeddings, persist_dir: str = "./memory_retrieval_db") -> Chroma:
    store = Chroma(
        collection_name="knowledge_base",
        embedding_function=embeddings,
        persist_directory=persist_dir,
    )
    # Idempotent seed: only add if empty, so re-runs don't duplicate content.
    if store._collection.count() == 0:
        store.add_documents(KB_DOCS)
    return store


def build_bm25_retriever() -> BM25Retriever:
    retriever = BM25Retriever.from_documents(KB_DOCS)
    retriever.k = TOP_N_PER_STRATEGY
    return retriever


# --------------------------------------------------------------------------
# 2. Graph state
# --------------------------------------------------------------------------
class SearchState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]
    keyword_results: list[Document]
    vector_results: list[Document]
    fused_results: list[Document]


# --------------------------------------------------------------------------
# 3. Node: keyword (BM25) search.
# --------------------------------------------------------------------------
def make_keyword_search_node(bm25: BM25Retriever):
    def keyword_search_node(state: SearchState) -> SearchState:
        last_human = next(
            (m for m in reversed(state["messages"]) if isinstance(m, HumanMessage)),
            None,
        )
        if last_human is None:
            return {"keyword_results": []}

        results = bm25.invoke(last_human.content)
        logger.info("Keyword (BM25) search returned %d result(s)", len(results))
        return {"keyword_results": results}

    return keyword_search_node


# --------------------------------------------------------------------------
# 4. Node: vector (embedding) search.
# --------------------------------------------------------------------------
def make_vector_search_node(vectorstore: Chroma):
    def vector_search_node(state: SearchState) -> SearchState:
        last_human = next(
            (m for m in reversed(state["messages"]) if isinstance(m, HumanMessage)),
            None,
        )
        if last_human is None:
            return {"vector_results": []}

        results = vectorstore.similarity_search(last_human.content, k=TOP_N_PER_STRATEGY)
        logger.info("Vector search returned %d result(s)", len(results))
        return {"vector_results": results}

    return vector_search_node


# --------------------------------------------------------------------------
# 5. Node: Reciprocal Rank Fusion -- merge both ranked lists into one.
# --------------------------------------------------------------------------
def make_fuse_node():
    def fuse_results_node(state: SearchState) -> SearchState:
        scores: dict[str, float] = {}
        doc_by_id: dict[str, Document] = {}

        for rank, doc in enumerate(state["keyword_results"]):
            doc_id = doc.metadata.get("doc_id", doc.page_content[:30])
            scores[doc_id] = scores.get(doc_id, 0.0) + 1.0 / (RRF_K + rank + 1)
            doc_by_id[doc_id] = doc

        for rank, doc in enumerate(state["vector_results"]):
            doc_id = doc.metadata.get("doc_id", doc.page_content[:30])
            scores[doc_id] = scores.get(doc_id, 0.0) + 1.0 / (RRF_K + rank + 1)
            doc_by_id[doc_id] = doc

        ranked_ids = sorted(scores, key=lambda did: scores[did], reverse=True)
        fused = [doc_by_id[did] for did in ranked_ids[:FINAL_TOP_N]]

        logger.info(
            "Fused ranking (top %d): %s",
            FINAL_TOP_N, [(did, round(scores[did], 4)) for did in ranked_ids[:FINAL_TOP_N]],
        )
        return {"fused_results": fused}

    return fuse_results_node


# --------------------------------------------------------------------------
# 6. Node: answer, grounded in the fused top results.
# --------------------------------------------------------------------------
BASE_SYSTEM_PROMPT = (
    "You are KnowledgeHub, an internal engineering search assistant. Answer "
    "using ONLY the provided knowledge base excerpts. Cite the document "
    "title you used. If the excerpts don't contain the answer, say so."
)


def make_chat_node(llm: ChatOllama):
    def chat_node(state: SearchState) -> SearchState:
        if state["fused_results"]:
            context_block = "\n\n".join(
                f"[{doc.metadata.get('title', 'Untitled')}]\n{doc.page_content}"
                for doc in state["fused_results"]
            )
        else:
            context_block = "(no relevant knowledge base entries found)"

        system_prompt = SystemMessage(content=f"{BASE_SYSTEM_PROMPT}\n\nRelevant excerpts:\n{context_block}")

        try:
            response: AIMessage = llm.invoke([system_prompt, *state["messages"]])
        except Exception:
            logger.exception("LLM call failed")
            response = AIMessage(content="Sorry, I hit an error. Please try again.")

        return {"messages": [response]}

    return chat_node


# --------------------------------------------------------------------------
# 7. Assemble the graph: keyword + vector search run, then fuse, then chat.
# --------------------------------------------------------------------------
def build_graph(persist_dir: str = "./memory_retrieval_db"):
    llm = build_llm()
    embeddings = build_embeddings()
    vectorstore = build_vectorstore(embeddings, persist_dir)
    bm25 = build_bm25_retriever()

    graph_builder = StateGraph(SearchState)
    graph_builder.add_node("keyword_search_node", make_keyword_search_node(bm25))
    graph_builder.add_node("vector_search_node", make_vector_search_node(vectorstore))
    graph_builder.add_node("fuse_results_node", make_fuse_node())
    graph_builder.add_node("chat_node", make_chat_node(llm))

    # Both search nodes run off START independently (conceptually parallel);
    # both must complete before fusion, so both edge into fuse_results_node.
    graph_builder.add_edge(START, "keyword_search_node")
    graph_builder.add_edge(START, "vector_search_node")
    graph_builder.add_edge("keyword_search_node", "fuse_results_node")
    graph_builder.add_edge("vector_search_node", "fuse_results_node")
    graph_builder.add_edge("fuse_results_node", "chat_node")
    graph_builder.add_edge("chat_node", END)

    return graph_builder.compile()


# --------------------------------------------------------------------------
# 8. Service wrapper
# --------------------------------------------------------------------------
class MemoryRetrievalService:
    def __init__(self, persist_dir: str = "./memory_retrieval_db"):
        self.graph = build_graph(persist_dir)

    def ask(self, question: str) -> str:
        result = self.graph.invoke(
            {
                "messages": [HumanMessage(content=question)],
                "keyword_results": [],
                "vector_results": [],
                "fused_results": [],
            }
        )
        return result["messages"][-1].content


# --------------------------------------------------------------------------
# 9. Demo: an exact-identifier query (favors keyword search) and a
#    conceptual query (favors vector search) -- both should succeed.
# --------------------------------------------------------------------------
if __name__ == "__main__":
    service = MemoryRetrievalService()

    print("--- Exact-identifier query ---")
    print("User:", "What is ERR-4092?")
    print("Bot: ", service.ask("What is ERR-4092?"))

    print("\n--- Conceptual/paraphrased query ---")
    print("User:", "Why might payment callbacks not be firing correctly?")
    print("Bot: ", service.ask("Why might payment callbacks not be firing correctly?"))

    print("\n--- Runbook lookup by exact service name ---")
    print("User:", "How do I restart SVC-Billing-7?")
    print("Bot: ", service.ask("How do I restart SVC-Billing-7?"))
```

### Notes on production-readiness in this code

- **Two independent retrieval strategies, run for every query** — the graph doesn't try to guess in advance whether a query is "exact" or "conceptual"; it always runs both and lets fusion sort out which mattered more, which is more robust than query classification.
- **Reciprocal Rank Fusion (`1 / (k + rank)`)** requires no training, no ML model, and no tuning beyond the well-established `k=60` constant — a deliberately simple, robust choice over more complex learned re-rankers for most production use cases.
- **`doc_id`-based deduplication in fusion** — a document that ranks well in *both* lists gets its scores summed (not double-counted as two separate entries), correctly reflecting that agreement across strategies is a strong relevance signal.
- **Idempotent seeding** (`if store._collection.count() == 0`) avoids duplicate documents accumulating in the vector store across repeated demo runs — a small but real production hygiene concern for any ingestion code.
- **Grounded-answer instruction** (same discipline as Pattern 9) ensures the model only answers from the fused, retrieved excerpts, with an explicit fallback for "not found in the knowledge base."

---

## 8. Version Pins (latest stable, compatible, at time of writing)

```
python              >= 3.11
langchain-core      >= 0.3.0
langchain-ollama    >= 0.2.0
langchain-chroma    >= 0.1.4
langchain-community >= 0.3.0
rank_bm25           >= 0.2.2
chromadb            >= 0.5.0
langgraph           >= 0.2.0
```

```bash
pip install -U "langchain-core>=0.3.0" "langchain-ollama>=0.2.0" "langchain-chroma>=0.1.4" "langchain-community>=0.3.0" "rank_bm25>=0.2.2" "chromadb>=0.5.0" "langgraph>=0.2.0"
```

> `BM25Retriever` lives in `langchain_community.retrievers` and requires the `rank_bm25` package under the hood. Requires both Ollama models pulled locally: `ollama pull llama3.1` and `ollama pull nomic-embed-text`.

---

**Next up → Pattern 14: Memory Consolidation** (periodically reconciling, deduplicating, and merging accumulated memory — resolving contradictions between old and new facts across the stores built in this series).
