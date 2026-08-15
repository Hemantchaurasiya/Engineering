# Pattern 11: Graph + Vector Retrieval

## 1. Introduce the Pattern

This pattern combines **Pattern 8 (Knowledge Graphs)** and **Pattern 9 (Vector Databases)** into a single retrieval strategy that routes each query to whichever method — or combination — fits its shape. Some questions need semantic similarity over prose ("why does X happen"); some need relationship traversal ("who owns the thing that Y depends on"); many real questions need **both**, in sequence: use the graph to find the *right entity/scope*, then use vector search to find the *best explanation* within that scope.

```
"Why did the March payments outage happen, and who should fix it?"
        │
        ├── graph-shaped part: "who should fix it"
        │     → traverse: march-2026-outage --affected--> payments-api
        │                  payments-api --owned_by--> Platform team
        │
        └── vector-shaped part: "why did it happen"
              → semantic search over postmortem prose chunks,
                scoped to documents about march-2026-outage
                (scope narrowed using the graph traversal above)

        merged answer: cites both the graph-derived ownership fact
        AND the vector-retrieved explanation text
```

## 2. The Problem It Solves

Pattern 8 already established that knowledge graphs and vector search are complementary, not competing — but stopped short of actually combining them into one system. Used in isolation, each has a hole the other fills:

- **Vector search alone** can't reliably chain facts across documents (Pattern 8's whole motivation) — it retrieves topically-relevant prose but has no notion of "the specific entity connected to this specific other entity."
- **Graph traversal alone** can find connected entities and facts, but a raw `(subject, relation, object)` triple like `(payments-api, owned_by, platform-team)` has none of the rich, nuanced explanation a human actually needs — "why" and "how" questions need the free-form prose that only chunked, embedded documents carry.
- **Using them as two entirely separate, disconnected features** (a "graph search" tab and a "text search" tab) pushes the integration work onto the user, who has to manually figure out which tool to use and stitch results together themselves — exactly the burden a good retrieval system should absorb.

This pattern's job is to make that combination automatic: detect what shape of information a query needs, run the graph and/or vector paths as appropriate, and merge them into one coherent, well-grounded answer.

## 3. Realistic Enterprise Scenario

An on-call engineer, mid-incident, asks DocuMind: *"payments-api is failing — why does this keep happening, and who owns the database it depends on?"*

This single question has both shapes at once:
- **"who owns the database it depends on"** — a pure relationship-chain question, answered precisely by graph traversal (exactly Pattern 8's demo).
- **"why does this keep happening"** — a question needing actual explanatory prose from postmortem documents, best answered by semantic search — but *scoped* specifically to incidents affecting `payments-api`, a scope that the graph traversal itself can help establish (find all `affected` edges pointing at `payments-api`, then vector-search only within those specific source documents, rather than the whole corpus).

This is the combined retrieval flow this file builds.

## 4. Architecture / Flow Diagram

```
┌─────────────────────────┐
│      User query            │
└─────────────┬───────────┘
              ▼
   ┌────────────────────────────┐
   │  Query classifier              │  ← LLM-based: does this query need
   │  (graph-shaped? vector-shaped?  │     entity/relationship info,
   │   both?)                        │     semantic explanation, or both?
   └─────────────┬───────────────┘
                 │
      ┌──────────┴───────────┐
      │                       │
      ▼                       ▼
┌──────────────────┐  ┌──────────────────────┐
│  Graph traversal    │  │  Vector search          │
│  (Pattern 8)         │  │  (Pattern 9)            │
│  → entities, facts,  │  │  → relevant prose        │
│    connected docs    │  │    chunks                │
└─────────┬──────────┘  └──────────┬───────────┘
          │                        │
          │   graph results can    │
          │   NARROW the scope     │
          │   of vector search     │
          │   (e.g. restrict to    │
          │   doc_ids the graph    │
          │   flagged as related)  │
          └───────────┬────────────┘
                      ▼
          ┌────────────────────────────┐
          │  Merged, grounded context     │
          │  { graph_facts: [...],        │
          │    text_chunks: [...] }       │
          └────────────┬───────────────┘
                       ▼
              handed to an LLM to compose
              the final natural-language answer,
              citing both fact types
```

## 5. Request-to-Response Walkthrough

1. The incoming query is classified by a small LLM prompt into one of three shapes: **graph-only** (pure relationship lookup, e.g. "who owns payments-api"), **vector-only** (pure semantic explanation, e.g. "explain how connection pooling works"), or **hybrid** (needs both, e.g. the on-call scenario above).
2. For the **graph portion**, the query engine from Pattern 8 resolves a starting entity from the query text and traverses outward, collecting relevant triples (ownership, dependency, incident-affected edges).
3. The `source_doc` fields on the traversed edges give a set of **document IDs the graph considers relevant** — e.g. every postmortem that has an `affected` edge pointing at `payments-api`.
4. For the **vector portion**, semantic search (Pattern 9) runs as usual, but when graph results produced a relevant document-ID set, the vector search's metadata filter is **narrowed to those document IDs** — turning an open-ended corpus-wide search into a precisely scoped one, without giving up the ability to find explanatory prose the graph itself doesn't contain.
5. If the query was hybrid-shaped, both result sets — graph triples and vector-retrieved chunks — are combined into one structured context object.
6. This merged context is handed to an LLM (via `ChatOllama`) with a prompt instructing it to compose a final answer that draws on both fact types, citing the graph-derived facts (ownership, dependencies) separately from the retrieved explanatory text (why/how), so the user can distinguish "hard fact from the graph" from "explanation from a document."

## 6. Why This Pattern Is Appropriate

| Strategy | "Who owns X" style questions | "Why/how does X happen" style questions | Combined questions |
|---|---|---|---|
| Vector search only | Weak | Strong | Weak — can't chain facts |
| Graph traversal only | Strong | Weak — no prose explanation | Weak — no explanatory depth |
| Both run always, unconditionally, unmerged | Wastes compute on the graph for pure prose questions and vice versa; user still has to reconcile two separate result sets | Same | Better, but no scoping benefit and doubles the UX burden |
| **This pattern**: classify query shape, run only what's needed, let graph results scope vector search, merge into one answer | Strong | Strong | Strong — and the graph-derived scope actively *improves* vector search precision rather than just running alongside it |

The key design insight distinguishing this from "just running both retrieval methods and showing two lists" is **using the graph's output to narrow the vector search's input** — the combination is genuinely more powerful than either method's isolated output, not just a concatenation of two separate answers.

## 7. Production-Quality Python Implementation

```python
"""
graph_vector_retrieval_pipeline.py

Combines the knowledge graph query engine (Pattern 8) with the Chroma
vector store (Pattern 9) into one retrieval pipeline: classifies query
shape, runs graph traversal and/or vector search, and lets graph
results narrow the vector search scope for hybrid queries.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from typing import List, Optional

import networkx as nx
from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_ollama import ChatOllama, OllamaEmbeddings

CHAT_MODEL = "llama3.1"
EMBEDDING_MODEL = "nomic-embed-text"


# --------------------------------------------------------------------------
# Query classification
# --------------------------------------------------------------------------

CLASSIFY_PROMPT = """Classify the following user question into exactly one \
category. Respond with JSON only, no preamble, no markdown fences.

Categories:
- "graph": the question asks about relationships, ownership, dependencies,
  or "who/what is connected to X" — answerable by following explicit facts.
- "vector": the question asks for an explanation, reasoning, or "why/how"
  that requires understanding prose text.
- "hybrid": the question needs both — e.g. it references a relationship
  AND asks for an explanation.

Question: "{query}"

Respond with: {{"category": "graph" | "vector" | "hybrid", "entity_hint": "the main entity name mentioned, or null"}}"""


@dataclass
class QueryClassification:
    category: str  # "graph" | "vector" | "hybrid"
    entity_hint: Optional[str]


class QueryClassifier:
    def __init__(self, model: str = CHAT_MODEL):
        self._llm = ChatOllama(model=model, temperature=0)

    def classify(self, query: str) -> QueryClassification:
        response = self._llm.invoke(CLASSIFY_PROMPT.format(query=query))
        raw = re.sub(r"^```json\s*|\s*```$", "", response.content.strip())
        try:
            parsed = json.loads(raw)
            return QueryClassification(
                category=parsed.get("category", "vector"),
                entity_hint=parsed.get("entity_hint"),
            )
        except json.JSONDecodeError:
            return QueryClassification(category="vector", entity_hint=None)


# --------------------------------------------------------------------------
# Graph side (reuses Pattern 8's traversal shape, condensed here)
# --------------------------------------------------------------------------

@dataclass
class GraphFact:
    subject: str
    relation: str
    obj: str
    source_doc: str


class GraphRetriever:
    def __init__(self, graph: nx.MultiDiGraph):
        self.graph = graph

    def _resolve_entity(self, mention: str) -> Optional[str]:
        norm = mention.strip().lower().replace(" ", "-")
        if norm in self.graph:
            return norm
        for node in self.graph.nodes:
            if norm in node or node in norm:
                return node
        return None

    def retrieve(self, entity_hint: Optional[str], max_hops: int = 2) -> List[GraphFact]:
        if not entity_hint:
            return []
        start = self._resolve_entity(entity_hint)
        if start is None:
            return []

        facts: List[GraphFact] = []
        visited = {start}
        frontier = [start]
        for _ in range(max_hops):
            next_frontier = []
            for node in frontier:
                for _, target, data in self.graph.out_edges(node, data=True):
                    facts.append(GraphFact(node, data["relation"], target, data["source"]))
                    if target not in visited:
                        visited.add(target)
                        next_frontier.append(target)
                for source, _, data in self.graph.in_edges(node, data=True):
                    facts.append(GraphFact(source, data["relation"], node, data["source"]))
                    if source not in visited:
                        visited.add(source)
                        next_frontier.append(source)
            frontier = next_frontier
        return facts


# --------------------------------------------------------------------------
# Vector side (reuses Pattern 9's Chroma store shape)
# --------------------------------------------------------------------------

class VectorRetriever:
    def __init__(self, store: Chroma):
        self._store = store

    def retrieve(self, query: str, top_k: int = 5, doc_id_scope: Optional[List[str]] = None) -> List[Document]:
        where = {"doc_id": {"$in": doc_id_scope}} if doc_id_scope else None
        return self._store.similarity_search(query, k=top_k, filter=where)


# --------------------------------------------------------------------------
# Combined pipeline
# --------------------------------------------------------------------------

@dataclass
class CombinedContext:
    graph_facts: List[GraphFact] = field(default_factory=list)
    text_chunks: List[Document] = field(default_factory=list)


class GraphVectorRetrievalPipeline:
    def __init__(self, graph: nx.MultiDiGraph, vector_store: Chroma):
        self._classifier = QueryClassifier()
        self._graph_retriever = GraphRetriever(graph)
        self._vector_retriever = VectorRetriever(vector_store)

    def retrieve(self, query: str) -> tuple[QueryClassification, CombinedContext]:
        classification = self._classifier.classify(query)
        context = CombinedContext()

        if classification.category in ("graph", "hybrid"):
            context.graph_facts = self._graph_retriever.retrieve(classification.entity_hint)

        if classification.category in ("vector", "hybrid"):
            # Narrow vector search scope using documents the graph
            # traversal flagged as relevant, when available — this is
            # the key integration point, not just running both blindly.
            doc_scope = list({f.source_doc for f in context.graph_facts}) or None
            context.text_chunks = self._vector_retriever.retrieve(query, doc_id_scope=doc_scope)

        return classification, context

    @staticmethod
    def format_context(context: CombinedContext) -> str:
        lines = []
        if context.graph_facts:
            lines.append("Known facts (from knowledge graph):")
            for f in context.graph_facts:
                lines.append(f"  - {f.subject} {f.relation.replace('_', ' ')} {f.obj} (source: {f.source_doc})")
        if context.text_chunks:
            lines.append("\nRelevant explanations (from documents):")
            for c in context.text_chunks:
                lines.append(f"  - {c.page_content[:120]}... (source: {c.metadata.get('doc_id')})")
        return "\n".join(lines) if lines else "No relevant information found."


# --------------------------------------------------------------------------
# Demo
# --------------------------------------------------------------------------

def build_demo_graph() -> nx.MultiDiGraph:
    g = nx.MultiDiGraph()
    edges = [
        ("payments-api", "depends_on", "postgres-payments-db", "service-catalog.md"),
        ("payments-api", "owned_by", "platform-team", "service-catalog.md"),
        ("march-2026-payments-outage", "affected", "payments-api", "postmortem-2026-03-14.md"),
        ("march-2026-payments-outage", "caused_by", "connection-pool-exhaustion", "postmortem-2026-03-14.md"),
    ]
    for s, r, o, src in edges:
        g.add_edge(s, o, relation=r, source=src)
    return g


def build_demo_vector_store() -> Chroma:
    embeddings = OllamaEmbeddings(model=EMBEDDING_MODEL)
    store = Chroma(collection_name="demo_graph_vector", embedding_function=embeddings,
                    persist_directory="./demo_graph_vector_store")
    docs = [
        Document(page_content="The March 2026 payments outage was caused by connection pool "
                               "exhaustion: a recent deploy removed a connection timeout setting, "
                               "so connections were held open indefinitely under load instead of "
                               "being released after 30 seconds.",
                 metadata={"doc_id": "postmortem-2026-03-14.md"}),
        Document(page_content="Connection pool exhaustion is a common failure mode where all "
                               "available database connections are checked out and none are "
                               "returned to the pool, causing new requests to queue or fail.",
                 metadata={"doc_id": "platform-glossary.md"}),
        Document(page_content="Our HR notice period policy allows remote work with manager "
                               "approval during the transition period.",
                 metadata={"doc_id": "hr-policy-004.md"}),
    ]
    store.add_documents(docs, ids=[f"chunk-{i}" for i in range(len(docs))])
    return store


def run_demo() -> None:
    graph = build_demo_graph()
    vector_store = build_demo_vector_store()
    pipeline = GraphVectorRetrievalPipeline(graph, vector_store)

    query = "payments-api is failing — why does this keep happening, and who owns the database it depends on?"
    classification, context = pipeline.retrieve(query)

    print(f"Query classified as: {classification.category} (entity_hint={classification.entity_hint})\n")
    print(pipeline.format_context(context))


if __name__ == "__main__":
    run_demo()
```

### Expected output (shape)

```
Query classified as: hybrid (entity_hint=payments-api)

Known facts (from knowledge graph):
  - payments-api depends on postgres-payments-db (source: service-catalog.md)
  - payments-api owned by platform-team (source: service-catalog.md)
  - march-2026-payments-outage affected payments-api (source: postmortem-2026-03-14.md)

Relevant explanations (from documents):
  - The March 2026 payments outage was caused by connection pool exhaustion: a recent deploy removed a connection timeout... (source: postmortem-2026-03-14.md)
```

Notice the vector search result is scoped to `postmortem-2026-03-14.md` — the document the graph traversal flagged via the `affected` edge — rather than also surfacing the generic `platform-glossary.md` entry about connection pool exhaustion in general, which would have ranked reasonably high in an unscoped search but is less specifically relevant to *this* incident.

### Key production notes

- **Classification doesn't need to be perfect to be useful** — even an imperfect graph/vector/hybrid classifier that's right most of the time meaningfully improves results over either "always run both, unscoped" or "always pick one method"; treat misclassification as a tunable error rate to monitor, not a blocking correctness requirement.
- **The scoping step is the actual value-add of this pattern** — without it, this file would just be "run Pattern 8 and Pattern 9 in the same function," which is a much weaker pattern. Explicitly using graph-derived `source_doc`s to narrow the vector `where` filter is what makes the combination more than the sum of its parts.
- **Graceful degradation matters**: if the graph traversal finds nothing (unknown entity, no matching edges), the pipeline should fall back to an unscoped vector search rather than returning empty results — the demo's `doc_scope = ... or None` line handles exactly this case.
- **Citation separation** (graph facts vs. document excerpts, shown distinctly in `format_context`) helps the end user — and the LLM composing the final answer — distinguish a hard structural fact from a document's explanatory claim, which matters for trust calibration.

## 8. Pinned Dependency Versions

```txt
networkx==3.4.2
langchain-chroma==0.2.0
chromadb==0.5.23
langchain-ollama==0.2.3
langchain-core==0.3.29
python>=3.11,<3.13
```

```bash
ollama pull llama3.1
ollama pull nomic-embed-text
```

---

**Next:** say `next` and I'll build `12_Reranking.md` — adding a precision pass on top of retrieval results using a cross-encoder re-ranker.
