# Pattern 16: Memory Forgetting

> Part of the **Memory Patterns** series (Section 6). Stack: `langchain-ollama` (ChatOllama + OllamaEmbeddings), `langgraph`, `langchain-chroma`, local models (`llama3.1`, `nomic-embed-text`).

---

## 1. Introduce the Pattern

**Memory Forgetting** is the deliberate, systematic **deletion or expiry** of stored memory — driven either by an explicit request (privacy/compliance: "delete everything you know about me") or by an automatic policy (staleness: "this fact hasn't been confirmed in 6 months and should expire").

Every pattern in this series so far has been about **retaining** information more effectively. Pattern 16 is the necessary counterweight: a production AI system that remembers things about real people has a **legal and ethical obligation** to also be able to forget them — completely, verifiably, and on request (GDPR's "right to erasure," CCPA deletion rights, and similar regulations worldwide). Separately, even without a compliance trigger, memory that's gone stale or is no longer relevant should be allowed to **decay and expire** rather than accumulate forever, degrading retrieval quality (Pattern 9/13) and violating basic data-minimization principles.

This pattern has two distinct mechanisms:
1. **Deliberate deletion ("right to be forgotten")** — a triggered, cross-cutting purge of everything associated with a specific user, across *every* memory store the system uses.
2. **Automatic expiry (TTL / relevance decay)** — a background policy that quietly removes or flags facts once they've aged past a defined relevance window, without needing an explicit request.

---

## 2. Problem It Solves

Without Memory Forgetting, a system that has built increasingly sophisticated memory throughout this series (Patterns 1-15) has a serious, unaddressed liability:

```
[User deletes their account / requests data deletion, per GDPR Article 17]

Conversation Memory (Pattern 1):  Still has 40 threads of raw chat history.
Long-Term Memory (Pattern 3):     Still has the user's profile facts.
Semantic Memory (Pattern 4):      Still has embedded facts mentioning them.
Entity Memory (Pattern 7):        Still has records referencing them by name.

Result: the company is now legally non-compliant, and the "deleted" user's
data actually still exists and can still be retrieved/surfaced by the AI.
```

This is not a hypothetical edge case — it's a **required capability** for any production AI system handling personal data in a regulated market. Separately, even for non-regulatory reasons, unmanaged accumulation causes:

1. **Compliance risk** — inability to honor deletion requests is a genuine legal exposure, not just a technical nicety.
2. **Retrieval quality decay** — stale, no-longer-true facts (Pattern 14 addressed *contradictions*; this addresses facts that are simply too *old* to trust, contradiction or not) crowd out current, relevant memory.
3. **Unbounded growth** — without any expiry mechanism, every store in this series grows forever, compounding the storage-cost problem Pattern 15 addressed from a *compression* angle; Forgetting addresses it from a *deletion* angle.

Memory Forgetting solves this with two complementary mechanisms: a **cross-store deletion sweep** triggered by explicit request, and a **policy-driven TTL expiry** that runs automatically in the background.

---

## 3. Realistic Production/Enterprise Scenario

**Scenario: "PrivacyGuard" — compliant data deletion and expiry for the MealPlanner assistant (from Pattern 3)**

- A user invokes their right to deletion ("delete my account and all my data"). The system must **verifiably purge** every trace of that user across: the conversation checkpointer (Pattern 1), the long-term profile store (Pattern 3), and any semantic/entity records that reference them (Patterns 4/7) — not just deactivate their account, but actually remove the underlying stored content.
- Separately, even for users who never request deletion, some stored facts are inherently **temporary** by nature — "prefers meals under 30 minutes today because guests are coming" is situational, not permanent, and shouldn't silently calcify into a permanent profile fact forever. The team defines a **TTL policy**: situational preferences expire after 30 days unless the user restates them; core facts (allergies, dietary restriction) never auto-expire (too safety-critical to risk losing) but do get flagged for periodic reconfirmation after a year.
- Both mechanisms need to be **auditable** — the company needs a log proving a deletion request was fully honored, and proving the expiry policy is being applied consistently.

This demonstrates Memory Forgetting's two faces clearly: a **hard, complete, triggered deletion** for compliance, and a **soft, policy-driven, automatic expiry** for ongoing data hygiene.

---

## 4. Architecture / Flow Diagram

```
   MECHANISM 1: Explicit "Right to be Forgotten" request
   ┌───────────────────────────────────────────┐
   │   forget_user(user_id="u_1")                   │
   └───────────────────┬───────────────────────────┘
                        ▼
   ┌───────────────────────────────────────────┐
   │           Cross-store deletion sweep            │
   │                                                │
   │  1. Checkpointer (Pattern 1): delete every       │
   │     thread belonging to u_1                       │
   │  2. Long-Term Store (Pattern 3): delete            │
   │     namespace ("users", "u_1")                       │
   │  3. Semantic/Vector Store (Pattern 4/9):              │
   │     delete all docs where metadata.user_id=="u_1"       │
   │  4. Entity records (Pattern 7) referencing u_1:           │
   │     delete or redact                                        │
   │                                                                │
   │  -> logs a deletion audit record (WHAT was deleted, WHEN)       │
   └───────────────────────────────────────────┘

   MECHANISM 2: Automatic TTL / relevance decay (background job)
   ┌───────────────────────────────────────────┐
   │   run_expiry_sweep()  -- e.g. nightly           │
   └───────────────────┬───────────────────────────┘
                        ▼
   ┌───────────────────────────────────────────┐
   │  For each stored fact, check its TTL policy:     │
   │   - "situational" facts: expire after 30 days      │
   │   - "core" facts (allergy, dietary): NEVER auto-      │
   │     delete, but flag "needs reconfirmation" after       │
   │     365 days                                              │
   │  -> expired facts removed; flagged facts logged             │
   │     for review, not silently dropped                          │
   └───────────────────────────────────────────┘
```

**Key idea:** deletion must be **cross-store and complete** (mechanism 1), while expiry is **policy-driven and fact-type-aware** (mechanism 2) — a blanket "delete everything after 30 days" policy would be *wrong* for safety-critical facts like allergies, which is why the policy differentiates by fact type rather than applying one rule to everything.

---

## 5. Complete Request-to-Response Flow

**Mechanism 1 — a user requests deletion:**

1. **Compliance system calls** `forget_user(user_id="u_1")`.
2. **Step 1**: query the checkpointer for every `thread_id` associated with `u_1` (via an app-level mapping, since raw checkpointer storage is keyed by thread, not user) and delete each thread's full checkpoint history.
3. **Step 2**: delete the entire `("users", "u_1")` namespace from the Long-Term Memory store — removing the profile facts built up in Pattern 3.
4. **Step 3**: query the Chroma vector store for all documents with `metadata.user_id == "u_1"` and delete them by ID — removing anything from Semantic Memory (Pattern 4) tied to this user.
5. **Step 4**: an audit record is written (to a *separate*, minimal, non-personal-data audit log — just "deletion completed for user u_1 at timestamp T, N records removed across M stores") proving compliance.
6. **Response**: deletion confirmed complete, with a count of what was removed from each store.

**Mechanism 2 — the nightly expiry job:**

1. **Scheduler fires** `run_expiry_sweep()`.
2. The job scans every stored fact's metadata for `fact_type` and `recorded_at`.
3. For `fact_type == "situational"` facts older than 30 days: **delete outright**.
4. For `fact_type == "core"` facts older than 365 days: **do not delete** — instead, set a `needs_reconfirmation` flag and log it for a human/product review process (never silently drop a safety-critical fact just because it's old).
5. **Job completes**, logging counts of expired vs. flagged facts.

---

## 6. Why This Pattern Is Appropriate

| Requirement | How Memory Forgetting satisfies it |
|---|---|
| Legal compliance (GDPR/CCPA deletion rights) | Cross-store deletion sweep ensures no store silently retains "deleted" user data |
| Data minimization / storage hygiene | TTL expiry removes situational facts that have outlived their relevance |
| Safety for critical facts | Fact-type-aware policy never silently auto-deletes safety-critical information (allergies) — only flags it for review |
| Auditability | Every deletion and expiry action is logged, proving the policy was actually applied |

**Trade-offs / when it's not enough:**
- **Cross-store deletion requires knowing every place a user's data could live** — as a system grows more memory patterns (as this series has), the deletion sweep must be kept in sync with every new store added; a store added later and forgotten in the deletion logic is a real compliance gap risk in practice.
- **Backups and logs outside the AI application's direct control** (database backups, observability logs) are a separate, often harder compliance surface — this pattern addresses the AI memory stores themselves, not the full data lifecycle of a company's infrastructure.
- **TTL policies require real product judgment** — too aggressive an expiry policy re-introduces the "forgets things a human wouldn't" problem from Pattern 2; too lax a policy fails to control growth or respect data minimization. This needs to be tuned per fact type, not applied uniformly.

---

## 7. Production-Quality Python Implementation

### Requirements

```bash
pip install -U langchain-ollama langgraph langchain-core langchain-chroma chromadb
# Ollama models used locally:
#   ollama pull llama3.1
#   ollama pull nomic-embed-text
```

### `memory_forgetting.py`

```python
"""
Pattern 16: Memory Forgetting
Two mechanisms: (1) a compliant, cross-store "right to be forgotten"
deletion sweep, and (2) an automatic, fact-type-aware TTL expiry job,
built on langchain-chroma + LangGraph's Store + langchain-ollama.

Run:
    python memory_forgetting.py
"""

from __future__ import annotations

import logging
import sqlite3
from datetime import datetime, timedelta, timezone
from typing import TypedDict

from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_ollama import OllamaEmbeddings
from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.store.memory import InMemoryStore

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("memory_forgetting")

SITUATIONAL_FACT_TTL_DAYS = 30
CORE_FACT_RECONFIRM_DAYS = 365


def build_embeddings(model: str = "nomic-embed-text") -> OllamaEmbeddings:
    return OllamaEmbeddings(model=model)


def build_vectorstore(embeddings: OllamaEmbeddings, persist_dir: str = "./forgetting_memory_db") -> Chroma:
    return Chroma(
        collection_name="user_facts",
        embedding_function=embeddings,
        persist_directory=persist_dir,
    )


# --------------------------------------------------------------------------
# 1. A minimal app-level mapping of user_id -> thread_ids, needed because
#    checkpointers (Pattern 1) are keyed by thread, not by user. Real
#    systems typically maintain this mapping in their own application DB.
# --------------------------------------------------------------------------
class ThreadRegistry:
    def __init__(self):
        self._by_user: dict[str, set[str]] = {}

    def register(self, user_id: str, thread_id: str):
        self._by_user.setdefault(user_id, set()).add(thread_id)

    def threads_for_user(self, user_id: str) -> set[str]:
        return self._by_user.get(user_id, set())

    def forget_user(self, user_id: str):
        self._by_user.pop(user_id, None)


# --------------------------------------------------------------------------
# 2. MECHANISM 1: cross-store "right to be forgotten" deletion sweep.
# --------------------------------------------------------------------------
class ForgettingService:
    def __init__(
        self,
        checkpoint_conn: sqlite3.Connection,
        long_term_store: InMemoryStore,
        vectorstore: Chroma,
        thread_registry: ThreadRegistry,
    ):
        self.checkpoint_conn = checkpoint_conn
        self.long_term_store = long_term_store
        self.vectorstore = vectorstore
        self.thread_registry = thread_registry

    def forget_user(self, user_id: str) -> dict:
        audit: dict[str, int] = {}

        # Step 1: delete every checkpointed conversation thread for this user.
        thread_ids = self.thread_registry.threads_for_user(user_id)
        for thread_id in thread_ids:
            self.checkpoint_conn.execute(
                "DELETE FROM checkpoints WHERE thread_id = ?", (thread_id,)
            )
            self.checkpoint_conn.execute(
                "DELETE FROM writes WHERE thread_id = ?", (thread_id,)
            )
        self.checkpoint_conn.commit()
        audit["conversation_threads_deleted"] = len(thread_ids)
        self.thread_registry.forget_user(user_id)

        # Step 2: delete the entire long-term profile namespace for this user.
        namespace = ("users", user_id)
        items = self.long_term_store.search(namespace)
        for item in items:
            self.long_term_store.delete(namespace, item.key)
        audit["long_term_facts_deleted"] = len(items)

        # Step 3: delete every semantic/vector doc tagged with this user_id.
        matches = self.vectorstore.get(where={"user_id": user_id})
        matched_ids = matches.get("ids", [])
        if matched_ids:
            self.vectorstore.delete(ids=matched_ids)
        audit["semantic_docs_deleted"] = len(matched_ids)

        logger.info("forget_user(%r) complete: %s", user_id, audit)
        return audit


# --------------------------------------------------------------------------
# 3. MECHANISM 2: automatic, fact-type-aware TTL expiry.
# --------------------------------------------------------------------------
class ExpiryPolicyState(TypedDict):
    expired_count: int
    flagged_count: int


def seed_facts_for_expiry_demo(store: InMemoryStore, user_id: str):
    namespace = ("users", user_id)
    now = datetime.now(timezone.utc)

    facts = {
        "situational_rush_today": {
            "fact_type": "situational",
            "value": "prefers meals under 15 minutes this week (guests visiting)",
            "recorded_at": (now - timedelta(days=45)).isoformat(),  # past 30-day TTL
        },
        "core_dietary": {
            "fact_type": "core",
            "value": "vegetarian",
            "recorded_at": (now - timedelta(days=400)).isoformat(),  # past 365-day reconfirm window
        },
        "core_allergy": {
            "fact_type": "core",
            "value": "allergic to peanuts",
            "recorded_at": (now - timedelta(days=10)).isoformat(),  # recent, not flagged
        },
    }
    for key, value in facts.items():
        store.put(namespace, key, value)
    logger.info("Seeded %d demo fact(s) with varying ages for expiry policy testing", len(facts))


def run_expiry_sweep(store: InMemoryStore, user_id: str) -> ExpiryPolicyState:
    namespace = ("users", user_id)
    now = datetime.now(timezone.utc)
    expired_count = 0
    flagged_count = 0

    for item in store.search(namespace):
        fact = item.value
        recorded_at = datetime.fromisoformat(fact["recorded_at"])
        age_days = (now - recorded_at).days

        if fact["fact_type"] == "situational" and age_days > SITUATIONAL_FACT_TTL_DAYS:
            store.delete(namespace, item.key)
            logger.info("Expired situational fact %r (age=%d days)", item.key, age_days)
            expired_count += 1

        elif fact["fact_type"] == "core" and age_days > CORE_FACT_RECONFIRM_DAYS:
            # NEVER silently delete a core/safety-relevant fact -- flag it
            # for review/reconfirmation instead.
            updated = {**fact, "needs_reconfirmation": True}
            store.put(namespace, item.key, updated)
            logger.info("Flagged core fact %r for reconfirmation (age=%d days)", item.key, age_days)
            flagged_count += 1

    return {"expired_count": expired_count, "flagged_count": flagged_count}


# --------------------------------------------------------------------------
# 4. Demo: seed data across all three stores for a user, run the
#    deletion sweep, confirm everything is gone; separately demo the
#    TTL expiry policy on a second user.
# --------------------------------------------------------------------------
if __name__ == "__main__":
    # --- Set up all three memory stores, as earlier patterns would. ---
    checkpoint_conn = sqlite3.connect(":memory:", check_same_thread=False)
    checkpointer = SqliteSaver(checkpoint_conn)
    checkpointer.setup()

    long_term_store = InMemoryStore()
    embeddings = build_embeddings()
    vectorstore = build_vectorstore(embeddings, persist_dir="./forgetting_memory_db")
    thread_registry = ThreadRegistry()

    user_id = "u_1"
    thread_registry.register(user_id, "thread_a")
    thread_registry.register(user_id, "thread_b")

    # Seed a long-term profile fact (as Pattern 3 would produce).
    long_term_store.put(("users", user_id), "profile", {"dietary": "vegetarian"})

    # Seed a semantic memory doc tagged with this user (as Pattern 4 would produce).
    vectorstore.add_documents(
        [Document(page_content="User prefers quick weeknight meals.", metadata={"user_id": user_id})]
    )

    print("--- MECHANISM 1: Right-to-be-forgotten deletion sweep ---")
    forgetting_service = ForgettingService(checkpoint_conn, long_term_store, vectorstore, thread_registry)
    audit = forgetting_service.forget_user(user_id)
    print("Deletion audit:", audit)

    remaining_facts = long_term_store.search(("users", user_id))
    remaining_docs = vectorstore.get(where={"user_id": user_id})
    print(f"Verification: {len(remaining_facts)} long-term facts remain, "
          f"{len(remaining_docs['ids'])} semantic docs remain (both should be 0).")

    print("\n--- MECHANISM 2: Automatic TTL / relevance-decay expiry ---")
    user_id_2 = "u_2"
    seed_facts_for_expiry_demo(long_term_store, user_id_2)
    result = run_expiry_sweep(long_term_store, user_id_2)
    print("Expiry sweep result:", result)

    print("\nRemaining facts for u_2 after sweep:")
    for item in long_term_store.search(("users", user_id_2)):
        print(f"  {item.key}: {item.value}")
```

### Notes on production-readiness in this code

- **Cross-store completeness is explicit and enumerated** — `forget_user` touches the checkpointer, the long-term store, and the vector store as three distinct, individually-verified steps, each returning a count for the audit record; this design makes it obvious (and testable) when a new memory store is added to the system but forgotten in the deletion sweep.
- **Fact-type-aware TTL policy** — the expiry job explicitly branches on `fact_type`, ensuring situational facts genuinely delete while core/safety-relevant facts are only ever flagged, never silently dropped, directly reflecting the safety principle established back in Pattern 3.
- **Verification step in the demo** — after calling `forget_user`, the demo explicitly re-queries both stores to confirm zero remaining records, which mirrors the kind of automated verification a real compliance test suite would run after every deletion.
- **Audit logging without storing personal data in the log itself** — `audit` records *counts* and *what was deleted*, not the actual content of what was deleted, which is the right pattern for compliance logs (proving deletion happened without creating a new copy of the deleted data).
- **`ThreadRegistry` as an explicit mapping** — since checkpointers are keyed by `thread_id` and have no built-in concept of "user," a real system needs *some* durable mapping from user to their threads to make deletion possible at all; this is called out explicitly rather than glossed over.

---

## 8. Version Pins (latest stable, compatible, at time of writing)

```
python            >= 3.11
langchain-core    >= 0.3.0
langchain-ollama  >= 0.2.0
langchain-chroma  >= 0.1.4
chromadb          >= 0.5.0
langgraph         >= 0.2.0
langgraph-checkpoint-sqlite >= 2.0.0
```

```bash
pip install -U "langchain-core>=0.3.0" "langchain-ollama>=0.2.0" "langchain-chroma>=0.1.4" "chromadb>=0.5.0" "langgraph>=0.2.0" "langgraph-checkpoint-sqlite>=2.0.0"
```

> Requires the `nomic-embed-text` Ollama model pulled locally for the vector store portion of the demo. Production systems should also confirm their chosen `BaseStore`/checkpointer backend (Postgres, Redis, etc., per Pattern 11) supports the same delete-by-namespace/delete-by-thread operations demonstrated here.

---

**Next up → Pattern 17: User Profile Memory** (the final pattern — pulling together Long-Term, Entity, Consolidation, and Forgetting into one coherent, production-grade user profile system).
