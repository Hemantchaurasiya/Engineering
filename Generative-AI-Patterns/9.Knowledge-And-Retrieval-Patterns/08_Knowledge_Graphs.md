# Pattern 8: Knowledge Graphs

## 1. Introduce the Pattern

A **knowledge graph** represents information as a network of **entities** (nodes) connected by **relationships** (edges), instead of as free-floating text chunks. Where vector embeddings capture "these two pieces of text are semantically similar," a knowledge graph captures "this specific thing is connected to that specific thing, in this specific way."

```
Entities (nodes):           Relationships (edges):

[payments-api] ────depends_on───► [postgres-payments-db]
[payments-api] ────owned_by─────► [Platform team]
[postgres-payments-db] ──has_replica──► [postgres-payments-db-standby]
[March 2026 outage] ──caused_by──► [connection pool exhaustion]
[March 2026 outage] ──affected────► [payments-api]
[connection pool exhaustion] ──fixed_by──► [connection timeout config change]
```

Each node is a distinct, identifiable "thing" (a service, a team, an incident, a config setting). Each edge is a labeled, directed relationship between two things. This turns unstructured facts scattered across many documents into an explicit, queryable structure.

## 2. The Problem It Solves

Vector similarity search (Pattern 1) is excellent at "find text similar in meaning to this query" but structurally bad at a specific and common category of question: **multi-hop questions**, where the answer requires chaining together facts from *different* documents that aren't directly about each other.

Example: *"Which team should I contact if payments-api's database has an outage?"*

- This requires knowing: payments-api depends on `postgres-payments-db` (fact from a system architecture doc) **AND** `postgres-payments-db` is owned by the Platform team (fact from a different ownership doc).
- Neither individual document mentions both facts together. A single embedding similarity search over chunks will retrieve chunks *about* payments-api and chunks *about* the Platform team separately, but has no mechanism to **chain** "payments-api → its database → that database's owner" into one connected answer.
- As the number of hops increases (three, four services deep), pure vector retrieval degrades further — there's no chunk that happens to contain the full chain, because the full chain was never written down in one place; it only exists as a *composition* of separate facts.

Knowledge graphs solve this by making relationships **explicit and traversable** — you can walk from `payments-api` to `postgres-payments-db` to `Platform team` as a graph query, following real edges, regardless of which documents those individual facts originally came from.

## 3. Realistic Enterprise Scenario

DocuMind's engineering wiki documents dozens of internal services, their dependencies, their owning teams, and a history of incidents affecting them — but this information is scattered across separate "service catalog" pages, "team ownership" pages, and "incident postmortem" documents, written independently by different teams at different times.

An on-call engineer, mid-incident, asks: *"payments-api is down — who owns the database it depends on, and has this happened before?"*

This single question needs: a dependency lookup (payments-api → its DB), an ownership lookup (that DB → its owning team), and a historical lookup (past incidents affecting that DB) — three separate facts, from three separate document types, that need to be **connected**, not just individually retrieved. This is exactly the shape of question a knowledge graph answers well and vector search alone struggles with.

## 4. Architecture / Flow Diagram

```
┌─────────────────────────────┐
│  Parsed + chunked documents    │   (from Patterns 3-6)
│  (service catalog, ownership,  │
│   postmortems)                 │
└──────────────┬───────────────┘
               │
               ▼
   ┌────────────────────────────────────┐
   │  Entity + Relationship Extraction     │  ← LLM-based, structured output
   │  "payments-api depends on             │     (similar approach to Pattern 7,
   │   postgres-payments-db"               │     applied to relationships instead
   │   → (payments-api, depends_on,        │     of flat document metadata)
   │      postgres-payments-db)            │
   └──────────────┬───────────────────┘
                  │
                  ▼
   ┌────────────────────────────────────┐
   │        Graph construction             │
   │   nodes: services, teams, incidents,  │
   │          configs                       │
   │   edges: depends_on, owned_by,        │
   │          caused_by, fixed_by,          │
   │          has_replica, affected         │
   │   (networkx.DiGraph)                  │
   └──────────────┬───────────────────┘
                  │
                  ▼
   ┌────────────────────────────────────┐
   │  Query-time graph traversal           │
   │  start: "payments-api"                │
   │  hop 1: depends_on → postgres-payments-db │
   │  hop 2: owned_by → Platform team       │
   │  hop 2: (reverse) affected ← incidents │
   └──────────────┬───────────────────┘
                  │
                  ▼
        assembled multi-hop answer, with
        source document references per edge
                  │
                  ▼
     (often combined with vector search —
      see Pattern 11: Graph + Vector Retrieval)
```

## 5. Request-to-Response Walkthrough

1. **Graph construction (offline, at ingestion time)**: for each parsed document, an LLM-based extractor identifies entities mentioned (services, teams, incidents, configs) and the relationships stated or implied between them, producing `(subject, relation, object)` triples — e.g. `(payments-api, depends_on, postgres-payments-db)`.
2. Each triple is added to a directed graph: subject and object become nodes (or are merged with existing nodes if they refer to the same entity — **entity resolution**, a real challenge glossed over here but flagged in production notes), and the relation becomes a labeled, directed edge, tagged with the source document it came from.
3. **Query time**: when a question implies a relationship chain (detected via keywords like "who owns," "depends on," "caused by," or via an LLM classifying the query as graph-shaped vs. plain lookup), the query is mapped to a starting entity (here, `payments-api`, found via a simple name match or fuzzy match against known node names).
4. The graph is **traversed** outward from the starting node, following relevant edge types up to a maximum hop count (to bound the search and avoid pulling in the entire graph).
5. Each traversed edge is resolved back to its source document reference, so the final answer can cite where each individual fact came from — this matters for trust, since a graph answer is now a *composition* of multiple original sources.
6. The assembled path (`payments-api --depends_on--> postgres-payments-db --owned_by--> Platform team`, plus any `affected` incident edges) is formatted into a structured answer, which can be handed directly to an LLM as grounding context for a natural-language response.

## 6. Why This Pattern Is Appropriate

| Approach | Handles multi-hop questions? | Handles "what does this text mean" questions? |
|---|---|---|
| Vector similarity search alone (Pattern 1) | Poorly — no mechanism to chain separate facts | Excellent |
| Keyword/full-text search | Poorly — same limitation, plus misses paraphrases | Poor |
| **Knowledge graph (this pattern)** | Excellent — relationships are explicit and traversable | Poor alone — a graph node/edge doesn't capture nuance, tone, or free-form explanation the way embedded prose does |
| **Knowledge graph + vector search combined** | Excellent | Excellent |

The honest takeaway: **a knowledge graph is not a replacement for vector search — it's a complement**, good at a specific class of problem (explicit relationships, multi-hop reasoning) that vector search structurally cannot do well, while being weak at exactly what vector search is strong at (fuzzy semantic matching over free-form prose). Pattern 11 (Graph + Vector Retrieval) is where these two get combined into one retrieval strategy — this pattern focuses on building the graph itself and querying it in isolation, so its distinct value is clear before combining it with anything else.

## 7. Production-Quality Python Implementation

```python
"""
knowledge_graph_pipeline.py

Production-quality knowledge graph builder and query engine for
DocuMind, using networkx for the graph structure and ChatOllama for
LLM-based entity/relationship extraction from unstructured text.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from typing import List, Optional

import networkx as nx
from langchain_ollama import ChatOllama

CHAT_MODEL = "llama3.1"
MAX_TRAVERSAL_HOPS = 3


@dataclass
class Triple:
    subject: str
    relation: str
    obj: str
    source_doc: str


# --------------------------------------------------------------------------
# Entity + relationship extraction (LLM-based, structured output)
# --------------------------------------------------------------------------

EXTRACTION_PROMPT = """Extract relationship facts from the text below as JSON only \
— no preamble, no markdown fences.

Return a JSON array of triples, each shaped exactly like:
{{"subject": "...", "relation": "...", "object": "..."}}

Rules:
- Use short, consistent entity names (e.g. "payments-api", not "the payments API service").
- Use snake_case relation names (e.g. "depends_on", "owned_by", "caused_by", "affected", "fixed_by", "has_replica").
- Only extract relationships that are explicitly stated or clearly implied. Do not invent facts.
- Return [] if no clear relationships are present.

Text:
\"\"\"
{text}
\"\"\"

JSON array:"""


class RelationshipExtractor:
    def __init__(self, model: str = CHAT_MODEL):
        self._llm = ChatOllama(model=model, temperature=0)

    def extract(self, text: str, source_doc: str) -> List[Triple]:
        prompt = EXTRACTION_PROMPT.format(text=text[:2500])
        response = self._llm.invoke(prompt)
        raw = re.sub(r"^```json\s*|\s*```$", "", response.content.strip())

        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            return []

        triples = []
        for item in parsed:
            subj, rel, obj = item.get("subject"), item.get("relation"), item.get("object")
            if subj and rel and obj:
                triples.append(Triple(
                    subject=_normalize_entity(subj),
                    relation=rel.strip().lower().replace(" ", "_"),
                    obj=_normalize_entity(obj),
                    source_doc=source_doc,
                ))
        return triples


def _normalize_entity(name: str) -> str:
    """Basic entity normalization so 'Payments API' and 'payments-api'
    resolve to the same node. Production systems typically need a much
    more thorough entity-resolution step (fuzzy matching, aliases,
    embedding-based clustering) — this is a deliberately simple version."""
    return name.strip().lower().replace(" ", "-")


# --------------------------------------------------------------------------
# Graph construction
# --------------------------------------------------------------------------

class KnowledgeGraphBuilder:
    def __init__(self):
        self.graph = nx.MultiDiGraph()
        self._extractor = RelationshipExtractor()

    def ingest_document(self, text: str, source_doc: str) -> List[Triple]:
        triples = self._extractor.extract(text, source_doc)
        for t in triples:
            self.graph.add_node(t.subject)
            self.graph.add_node(t.obj)
            self.graph.add_edge(t.subject, t.obj, relation=t.relation, source=t.source_doc)
        return triples

    def stats(self) -> dict:
        return {"nodes": self.graph.number_of_nodes(), "edges": self.graph.number_of_edges()}


# --------------------------------------------------------------------------
# Query-time traversal
# --------------------------------------------------------------------------

@dataclass
class TraversalStep:
    subject: str
    relation: str
    obj: str
    source_doc: str


class KnowledgeGraphQueryEngine:
    def __init__(self, graph: nx.MultiDiGraph):
        self.graph = graph

    def find_entity(self, mention: str) -> Optional[str]:
        """Simple substring match against known node names — production
        systems typically use embedding similarity or a proper entity
        linker here instead of exact/substring matching."""
        mention_norm = _normalize_entity(mention)
        if mention_norm in self.graph:
            return mention_norm
        for node in self.graph.nodes:
            if mention_norm in node or node in mention_norm:
                return node
        return None

    def traverse(self, start_entity: str, max_hops: int = MAX_TRAVERSAL_HOPS,
                 relation_filter: Optional[List[str]] = None) -> List[TraversalStep]:
        resolved_start = self.find_entity(start_entity)
        if resolved_start is None:
            return []

        visited = {resolved_start}
        frontier = [resolved_start]
        steps: List[TraversalStep] = []

        for _ in range(max_hops):
            next_frontier = []
            for node in frontier:
                # Outgoing edges (node --relation--> other)
                for _, target, data in self.graph.out_edges(node, data=True):
                    if relation_filter and data["relation"] not in relation_filter:
                        continue
                    steps.append(TraversalStep(node, data["relation"], target, data["source"]))
                    if target not in visited:
                        visited.add(target)
                        next_frontier.append(target)
                # Incoming edges (other --relation--> node), useful for
                # questions like "what incidents AFFECTED this service"
                for source, _, data in self.graph.in_edges(node, data=True):
                    if relation_filter and data["relation"] not in relation_filter:
                        continue
                    steps.append(TraversalStep(source, data["relation"], node, data["source"]))
                    if source not in visited:
                        visited.add(source)
                        next_frontier.append(source)
            frontier = next_frontier
            if not frontier:
                break

        return steps

    def explain_path(self, steps: List[TraversalStep]) -> str:
        if not steps:
            return "No connected facts found in the knowledge graph."
        lines = [f"  {s.subject} --{s.relation}--> {s.obj}  (source: {s.source_doc})" for s in steps]
        return "\n".join(lines)


# --------------------------------------------------------------------------
# Demo
# --------------------------------------------------------------------------

DOCUMENTS = [
    ("payments-api depends on postgres-payments-db for all transaction "
     "storage. The service is owned and operated by the Platform team.",
     "service-catalog.md"),
    ("postgres-payments-db has a standby replica, postgres-payments-db-replica, "
     "used for failover.", "db-architecture.md"),
    ("The March 2026 payments outage affected payments-api and was caused by "
     "connection pool exhaustion in the database driver. It was fixed by a "
     "connection timeout configuration change.", "postmortem-2026-03-14.md"),
]


def run_demo() -> None:
    builder = KnowledgeGraphBuilder()
    for text, source in DOCUMENTS:
        triples = builder.ingest_document(text, source)
        print(f"Extracted {len(triples)} triples from {source}:")
        for t in triples:
            print(f"    ({t.subject}, {t.relation}, {t.obj})")
    print(f"\nGraph stats: {builder.stats()}\n")

    engine = KnowledgeGraphQueryEngine(builder.graph)

    print('Query: "payments-api is down — who owns its database, and has this happened before?"')
    steps = engine.traverse("payments-api", max_hops=3)
    print(engine.explain_path(steps))


if __name__ == "__main__":
    run_demo()
```

### Expected output (shape — exact triples depend on the LLM's phrasing)

```
Extracted 2 triples from service-catalog.md:
    (payments-api, depends_on, postgres-payments-db)
    (payments-api, owned_by, platform-team)
Extracted 1 triples from db-architecture.md:
    (postgres-payments-db, has_replica, postgres-payments-db-replica)
Extracted 2 triples from postmortem-2026-03-14.md:
    (march-2026-payments-outage, affected, payments-api)
    (march-2026-payments-outage, caused_by, connection-pool-exhaustion)

Graph stats: {'nodes': 7, 'edges': 5}

Query: "payments-api is down — who owns its database, and has this happened before?"
  payments-api --depends_on--> postgres-payments-db  (source: service-catalog.md)
  payments-api --owned_by--> platform-team  (source: service-catalog.md)
  march-2026-payments-outage --affected--> payments-api  (source: postmortem-2026-03-14.md)
  postgres-payments-db --has_replica--> postgres-payments-db-replica  (source: db-architecture.md)
```

Note the traversal surfaces the ownership fact, the dependency fact, the replica fact, *and* the historical incident fact — four facts from three separate source documents, connected through the shared entity `payments-api`/`postgres-payments-db`, none of which any single document stated together.

### Key production notes

- **Entity resolution is the hardest real part of this pattern** — the demo's `_normalize_entity` is deliberately simplistic (lowercase + hyphenate). Real systems need to handle "payments-api," "Payments API," "the payment service," and "payments-svc" all resolving to the *same* node, typically via a combination of alias dictionaries, fuzzy string matching, and embedding-similarity clustering of candidate entity names.
- **LLM-extracted triples should be treated as untrusted until validated** — the prompt explicitly instructs "do not invent facts," but LLM extraction can still hallucinate relationships, especially on ambiguous text. Production systems often run a confidence check or a second-pass verification before trusting a triple enough to surface it as fact.
- **Bound traversal hops explicitly** — an ungapped graph walk can explode combinatorially on a densely connected graph; `MAX_TRAVERSAL_HOPS` exists specifically to keep query latency predictable.
- **`MultiDiGraph` (not `DiGraph`) matters here** — two entities can legitimately be connected by more than one relationship type simultaneously (e.g. a service could both `depends_on` and be `co_located_with` another service); a simple `DiGraph` would silently overwrite one edge with another.
- **At real production scale** (hundreds of thousands of entities), `networkx` — an in-memory, single-process graph library — stops being sufficient, and teams typically move to a dedicated graph database like Neo4j, which supports persistent storage, indexed traversal, and the Cypher query language. The concepts here (triples, traversal, hop-bounding) transfer directly; only the storage/query engine changes.

## 8. Pinned Dependency Versions

```txt
networkx==3.4.2
langchain-ollama==0.2.3
langchain-core==0.3.29
python>=3.11,<3.13
```

```bash
ollama pull llama3.1
```

---

**Next:** say `next` and I'll build `09_Vector_Databases.md` — moving from the brute-force, in-memory similarity search used so far to a real, production-grade vector database (Chroma).
