# Pattern 13: Retrieval Filters

## 1. Introduce the Pattern

**Retrieval filters** are hard, boolean constraints applied to a search — narrowing the candidate set to only chunks that satisfy exact conditions on metadata (Pattern 7), *before or alongside* similarity ranking. Where Pattern 12 (re-ranking) improves the *ordering* of results, retrieval filters control which results are even **eligible** to appear at all.

```
Query: "what's our remote work policy?"

Without filters:
  → similarity search across the WHOLE corpus
  → might surface a 2023 policy, a 2025 draft policy, AND the current
    2026 policy, all "similar" to the query, in no particular order
    of which one is actually correct/current/allowed for this user

With filters:
  filter = {
    "access_level": {"$in": ["all_employees"]},   ← access control
    "doc_type": "policy",                          ← scope
    "status": "published",                         ← exclude drafts
    "last_updated": {"$gte": "2025-01-01"}          ← recency
  }
  → similarity search runs ONLY over chunks matching ALL of these
  → guaranteed correct, current, permitted results
```

This pattern is where Pattern 9's `where` clause, Pattern 7's extracted metadata, and Pattern 11's graph-derived scoping all converge into a single, well-designed filtering layer — this file focuses on *designing that layer properly*, rather than the one-off filter examples scattered through earlier patterns.

## 2. The Problem It Solves

Similarity search — dense, sparse, or hybrid — answers "what's relevant in meaning," but several categories of correctness requirement have **nothing to do with meaning** and must never be left to a similarity score to enforce:

- **Access control is not a ranking preference — it's a hard boundary.** A document a user isn't authorized to see must **never** appear in results, regardless of how semantically relevant it is. If access control were implemented as "rank it lower" instead of "exclude it entirely," a sufficiently relevant unauthorized document could still surface — an unacceptable security failure.
- **Recency/versioning conflicts can't be resolved by similarity alone.** Three versions of the same policy, from three years, are all semantically near-identical — cosine similarity has no notion of "which one is current." Only an explicit `last_updated` or `status` filter can express that.
- **Explicit scoping requests need exact matching, not approximate similarity.** "Search only Platform team docs" is a literal constraint the user stated — it shouldn't be treated as "prefer Platform docs" (a soft ranking signal) when the user meant "only Platform docs" (a hard boundary).
- **Filters interact with every other pattern in this series**: they must apply correctly whether the underlying retrieval is pure vector search (Pattern 9), hybrid (Pattern 10), graph-scoped (Pattern 11), or feeding into re-ranking (Pattern 12) — a poorly designed filter layer creates inconsistent behavior across these different retrieval paths.

## 3. Realistic Enterprise Scenario

DocuMind serves employees across many teams and access levels simultaneously. A single query — *"what's the current policy on expense reimbursement?"* — must simultaneously satisfy:

1. **Access control**: never surface `managers_only` or `confidential` content to a regular employee.
2. **Currency**: prefer (or strictly require) the most recently published version when duplicates/superseded versions exist in the corpus.
3. **Draft exclusion**: never surface documents still marked `status: draft` as if they were settled policy.
4. **Optional explicit scoping**: if the user says "just check the engineering wiki," restrict to that source system entirely.

This file builds the filter construction and validation layer that makes all four of these correct and composable, reused consistently across whichever retrieval pattern (9, 10, or 11) is actually running underneath.

## 4. Architecture / Flow Diagram

```
┌─────────────────────────┐   ┌──────────────────────────┐
│  Authenticated user        │   │  User's explicit query      │
│  context (from identity     │   │  scoping request (optional, │
│  provider — NEVER from      │   │  e.g. "just Platform docs")  │
│  client-supplied input)     │   └──────────────┬──────────────┘
│  { role, access_levels,     │                  │
│    team }                   │                  │
└─────────────┬─────────────┘                  │
              │                                  │
              ▼                                  ▼
   ┌────────────────────────────────────────────────┐
   │           FilterBuilder                            │
   │                                                      │
   │  MANDATORY filters (always applied, non-negotiable): │
   │    - access_level ∈ user's allowed levels             │
   │    - status != "draft" (unless explicitly requested)   │
   │                                                      │
   │  OPTIONAL filters (only if the user/query requested):  │
   │    - team == <requested team>                         │
   │    - doc_type == <requested type>                      │
   │                                                      │
   │  RECENCY handling:                                    │
   │    - last_updated >= cutoff, OR                       │
   │    - dedupe to latest version per doc family           │
   └─────────────────────┬────────────────────────────┘
                         ▼
              validated, composed filter dict
                         ▼
      ┌──────────────┬──────────────┬──────────────┐
      ▼              ▼              ▼
 Pattern 9        Pattern 10     Pattern 11
 (vector)         (hybrid)       (graph-scoped)
      │              │              │
      └──────────────┴──────────────┘
                     ▼
         filtered, correct candidate set
                     ▼
            (optionally → Pattern 12: re-ranking)
```

## 5. Request-to-Response Walkthrough

1. A query arrives together with an **authenticated user context** — critically, this context comes from a trusted identity/session layer, never from client-supplied request parameters, since a filter an attacker can freely set is not a security boundary at all.
2. The `FilterBuilder` starts by adding **mandatory filters** that are never skipped: the user's allowed `access_level`s, and exclusion of `status: draft` documents unless the user has explicitly opted into seeing drafts (e.g. a content reviewer role).
3. It then layers on **optional filters** derived from the query itself — if the user said "just check Platform team docs," a `team` filter is added; if not, no team constraint is applied (defaulting to open, not restrictive, for anything the user didn't explicitly ask to narrow).
4. **Recency handling** is applied last: either a hard cutoff date filter (`last_updated >= X`), or — a more sophisticated approach shown in the implementation — deduplication logic that keeps only the most recently updated chunk within each "document family" (multiple versions of the same policy sharing a `doc_family_id`), so older superseded versions are excluded even without the user specifying a date.
5. The resulting composed filter (a validated dict) is checked for consistency (e.g. no contradictory constraints) and passed uniformly to whichever retrieval pattern is actually running — Pattern 9's `similarity_search(filter=...)`, Pattern 10's dense/sparse legs, or Pattern 11's vector-scoping step.
6. Because mandatory filters are applied identically regardless of which retrieval path executes, access control and draft-exclusion are enforced consistently across the whole system, not re-implemented (and potentially forgotten) separately in each retrieval pattern.

## 6. Why This Pattern Is Appropriate

| Approach | Security guarantee | Consistency across retrieval methods |
|---|---|---|
| No filters — rely on similarity ranking alone to "usually" surface the right thing | None — a relevant-but-unauthorized document can and will surface eventually | N/A |
| Filters implemented ad hoc, separately, inside each retrieval pattern's code | Inconsistent — easy to add a new retrieval path (or a new pattern) and forget to replicate the filter logic | Poor |
| Filters built client-side and trusted as-is | Broken — a client can simply omit or falsify the access-level filter | N/A |
| **This pattern**: one shared `FilterBuilder`, driven by trusted server-side user context, mandatory filters that can't be skipped, applied uniformly across all retrieval patterns | Strong — access control is structural, not optional | Strong — one filter-construction path reused everywhere |

The core discipline here is treating **mandatory filters as code, not configuration** — they're applied unconditionally by the `FilterBuilder` itself, not passed in as an optional parameter some caller might forget to set.

## 7. Production-Quality Python Implementation

```python
"""
retrieval_filters_pipeline.py

Production-quality filter construction and application layer for
DocuMind: composes mandatory (access control, draft exclusion) and
optional (team/type scoping) filters from a trusted user context and
query intent, then applies them consistently to a Chroma-backed
vector store (reusing Pattern 9's store shape).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, datetime
from typing import Dict, List, Optional

from langchain_chroma import Chroma
from langchain_core.documents import Document


# --------------------------------------------------------------------------
# Trusted user context — in production this comes from an authenticated
# session/identity provider, NEVER from client-supplied request fields.
# --------------------------------------------------------------------------

@dataclass(frozen=True)
class UserContext:
    user_id: str
    role: str                     # e.g. "employee", "manager", "content_reviewer"
    allowed_access_levels: List[str]
    team: Optional[str] = None


ROLE_ACCESS_LEVELS = {
    "employee": ["all_employees"],
    "manager": ["all_employees", "managers_only"],
    "content_reviewer": ["all_employees", "managers_only", "team_only", "confidential"],
}


def build_user_context(user_id: str, role: str, team: Optional[str] = None) -> UserContext:
    allowed = ROLE_ACCESS_LEVELS.get(role, ROLE_ACCESS_LEVELS["employee"])
    return UserContext(user_id=user_id, role=role, allowed_access_levels=allowed, team=team)


# --------------------------------------------------------------------------
# Query-derived optional scoping (parsed from explicit user requests)
# --------------------------------------------------------------------------

@dataclass
class QueryScope:
    team: Optional[str] = None
    doc_type: Optional[str] = None
    include_drafts: bool = False
    min_last_updated: Optional[str] = None  # ISO date string


# --------------------------------------------------------------------------
# Filter builder — the single, shared path every retrieval pattern uses
# --------------------------------------------------------------------------

class FilterBuilder:
    def build(self, user: UserContext, scope: QueryScope) -> Dict:
        clauses: List[Dict] = []

        # --- MANDATORY: access control. Never optional, never skippable. ---
        clauses.append({"access_level": {"$in": user.allowed_access_levels}})

        # --- MANDATORY: exclude drafts unless explicitly requested AND
        #     the user's role is permitted to see drafts at all. ---
        if not (scope.include_drafts and user.role == "content_reviewer"):
            clauses.append({"status": {"$ne": "draft"}})

        # --- OPTIONAL: team scoping, only if explicitly requested ---
        if scope.team:
            clauses.append({"team": scope.team})

        # --- OPTIONAL: doc_type scoping ---
        if scope.doc_type:
            clauses.append({"doc_type": scope.doc_type})

        # --- OPTIONAL: recency cutoff, if the caller specified one ---
        if scope.min_last_updated:
            clauses.append({"last_updated": {"$gte": scope.min_last_updated}})

        if len(clauses) == 1:
            return clauses[0]
        return {"$and": clauses}

    @staticmethod
    def validate(filter_dict: Dict) -> List[str]:
        """Basic sanity checks — catches obviously contradictory or
        malformed filters before they're sent to the vector store."""
        warnings = []
        and_clauses = filter_dict.get("$and", [filter_dict])
        seen_keys = set()
        for clause in and_clauses:
            for key in clause:
                if key in seen_keys and key != "$and":
                    warnings.append(f"Duplicate constraint on field '{key}' — later clause may silently override.")
                seen_keys.add(key)
        return warnings


# --------------------------------------------------------------------------
# Version deduplication: keep only the latest chunk per document family
# --------------------------------------------------------------------------

def deduplicate_to_latest_version(results: List[Document]) -> List[Document]:
    """When multiple versions of the same document family are present
    in results (e.g. 'hr-remote-work-policy-v1', '...-v2', '...-v3',
    all sharing a doc_family_id), keep only the most recently updated
    one — prevents outdated policy versions from confusing the answer."""
    latest_by_family: Dict[str, Document] = {}

    for doc in results:
        family_id = doc.metadata.get("doc_family_id", doc.metadata.get("doc_id"))
        existing = latest_by_family.get(family_id)
        if existing is None:
            latest_by_family[family_id] = doc
            continue

        existing_date = existing.metadata.get("last_updated", "0000-00-00")
        candidate_date = doc.metadata.get("last_updated", "0000-00-00")
        if candidate_date > existing_date:
            latest_by_family[family_id] = doc

    return list(latest_by_family.values())


# --------------------------------------------------------------------------
# Applying filters to retrieval (reuses Pattern 9's Chroma store)
# --------------------------------------------------------------------------

class FilteredRetriever:
    def __init__(self, store: Chroma):
        self._store = store
        self._filter_builder = FilterBuilder()

    def search(
        self,
        query: str,
        user: UserContext,
        scope: QueryScope,
        top_k: int = 5,
        dedupe_versions: bool = True,
    ) -> List[Document]:
        filter_dict = self._filter_builder.build(user, scope)
        warnings = self._filter_builder.validate(filter_dict)
        for w in warnings:
            print(f"  [filter warning] {w}")

        # Retrieve a wider pool when deduplication is on, since dedup
        # can remove entries and we still want top_k after it runs.
        pool_size = top_k * 3 if dedupe_versions else top_k
        results = self._store.similarity_search(query, k=pool_size, filter=filter_dict)

        if dedupe_versions:
            results = deduplicate_to_latest_version(results)

        return results[:top_k]


# --------------------------------------------------------------------------
# Demo
# --------------------------------------------------------------------------

def build_demo_store() -> Chroma:
    from langchain_ollama import OllamaEmbeddings
    embeddings = OllamaEmbeddings(model="nomic-embed-text")
    store = Chroma(collection_name="demo_retrieval_filters", embedding_function=embeddings,
                    persist_directory="./demo_filters_store")

    docs = [
        Document(page_content="Expense reimbursement policy (2024): submit receipts within 30 days.",
                 metadata={"doc_id": "expense-policy-2024", "doc_family_id": "expense-policy",
                           "access_level": "all_employees", "status": "published",
                           "last_updated": "2024-01-10", "team": "Finance", "doc_type": "policy"}),
        Document(page_content="Expense reimbursement policy (2026, current): submit receipts within "
                               "14 days via the new self-service portal.",
                 metadata={"doc_id": "expense-policy-2026", "doc_family_id": "expense-policy",
                           "access_level": "all_employees", "status": "published",
                           "last_updated": "2026-02-01", "team": "Finance", "doc_type": "policy"}),
        Document(page_content="Draft: proposed expense policy changes for 2027, pending approval.",
                 metadata={"doc_id": "expense-policy-2027-draft", "doc_family_id": "expense-policy",
                           "access_level": "all_employees", "status": "draft",
                           "last_updated": "2026-07-01", "team": "Finance", "doc_type": "policy"}),
        Document(page_content="Executive travel expense exceptions require CFO sign-off.",
                 metadata={"doc_id": "exec-travel-policy", "doc_family_id": "exec-travel-policy",
                           "access_level": "managers_only", "status": "published",
                           "last_updated": "2025-06-01", "team": "Finance", "doc_type": "policy"}),
    ]
    store.add_documents(docs, ids=[d.metadata["doc_id"] for d in docs])
    return store


def run_demo() -> None:
    store = build_demo_store()
    retriever = FilteredRetriever(store)

    employee = build_user_context("u-101", role="employee")
    scope = QueryScope()  # no explicit scoping requested

    print("=== Regular employee query: 'what is the expense reimbursement policy?' ===")
    results = retriever.search("what is the expense reimbursement policy?", employee, scope, top_k=3)
    for doc in results:
        print(f"  [{doc.metadata['doc_id']}] status={doc.metadata['status']} "
              f"updated={doc.metadata['last_updated']}")
        print(f"    {doc.page_content}")


if __name__ == "__main__":
    run_demo()
```

### Expected output (shape)

```
=== Regular employee query: 'what is the expense reimbursement policy?' ===
  [expense-policy-2026] status=published updated=2026-02-01
    Expense reimbursement policy (2026, current): submit receipts within 14 days via the new self-service portal.
```

Only the **current, published, authorized** version survives all three layers: the 2024 version was deduplicated away in favor of the newer one in the same `doc_family_id`, the 2027 draft was excluded by the mandatory `status != draft` filter, and the executive travel policy was excluded by the mandatory access-level filter (a regular employee's context doesn't include `managers_only`) — without the query needing to mention any of those constraints explicitly.

### Key production notes

- **Mandatory filters must be structurally impossible to skip** — in this implementation, `FilterBuilder.build()` always appends the access-control and draft-exclusion clauses itself; no caller can construct a filter that omits them. Treat any code path that lets a caller opt out of mandatory filters as a security bug.
- **User context must come from a trusted source** — `UserContext` in this demo is constructed directly for illustration, but in production it must be derived from an authenticated session (e.g. a validated JWT or session lookup), never from request parameters a client could freely set.
- **Version deduplication is often more robust than a simple recency cutoff** — a hard `last_updated >= X` filter requires guessing the right cutoff date; grouping by `doc_family_id` and keeping only the latest is self-maintaining as new versions are published, with no date threshold to keep updating.
- **This filter layer is retrieval-method-agnostic by design** — the same `FilterBuilder` output feeds Pattern 9's plain vector search, Pattern 10's hybrid legs, or Pattern 11's graph-scoped vector step identically, which is exactly what prevents the "forgot to add the filter in the new code path" class of bug.
- **Filters interact with Pattern 12 (re-ranking) as well** — filtering must always happen at (or before) the initial retrieval stage, never only after re-ranking, since re-ranking can only reorder candidates that were already retrieved; an unauthorized document must never even enter the candidate pool.

## 8. Pinned Dependency Versions

```txt
langchain-chroma==0.2.0
chromadb==0.5.23
langchain-ollama==0.2.3
langchain-core==0.3.29
python>=3.11,<3.13
```

---

**Next:** say `next` and I'll build `14_Similarity_Search.md` — a dedicated, deeper look at the similarity metrics themselves (cosine, dot product, Euclidean) that every retrieval pattern so far has relied on.
