# Pattern 14: Memory Consolidation

> Part of the **Memory Patterns** series (Section 6). Stack: `langchain-ollama` (ChatOllama + OllamaEmbeddings), `langgraph`, local models (`llama3.1`, `nomic-embed-text`).

---

## 1. Introduce the Pattern

**Memory Consolidation** is the periodic process of **reconciling accumulated memory** — merging duplicates, resolving contradictions between old and new facts, and producing one clean, trustworthy record from what may have become a messy pile of raw, append-only entries over time.

This pattern directly addresses a weakness flagged repeatedly throughout this series. Pattern 3 (Long-Term Memory) noted profile facts get merged but never reconciled. Pattern 7 (Entity Memory) explicitly appends to `said: [...]` lists forever, with scalar fields simply overwritten by "latest wins" — which silently loses history and can't explain *why* something changed. Pattern 5 (Episodic Memory) and Pattern 9 (Vector-Based Memory) both flagged that stored content can accumulate stale or duplicate entries with no built-in mechanism to clean itself up.

Memory Consolidation is the answer: a **deliberate, periodic (or triggered) pass** over accumulated memory that:
- **Detects contradictions** ("role: VP Engineering" vs. a later "role: SVP Engineering" — is this an error, or a promotion?)
- **Merges duplicates** (the same fact stated slightly differently across multiple conversations)
- **Produces one clean, current record**, while preserving a trail of *what changed and when*, rather than just picking a winner and discarding the rest silently

Think of it like a database's periodic compaction, or a human assistant occasionally reviewing their notes and rewriting a clean summary instead of leaving 40 loose sticky notes, some of which now contradict each other.

---

## 2. Problem It Solves

Without consolidation, every pattern in this series that **accumulates** facts over time (Long-Term, Entity, Semantic, Episodic Memory) will eventually degrade in quality:

```
[Entity record for "Sarah", accumulated over 6 months of Entity Memory]
role: "SVP Engineering"        <- overwritten silently; WAS "VP Engineering"
said: [
  "budget is $50k",
  "budget is $75k",             <- contradicts the earlier figure!
  "actually let's revisit budget next quarter",
  "budget is $50k",              <- near-duplicate of the first entry
  ...37 more entries accumulated over 6 months...
]
```

Three concrete problems emerge:
1. **Silent contradiction** — "budget is $50k" and "budget is $75k" both exist with no indication which is current or why it changed. An LLM reading this raw list might pick either one at random, or worse, average/misinterpret them.
2. **Growing noise** — near-duplicate statements accumulate, diluting the signal and eventually pushing the raw record past a reasonable context size (reintroducing Pattern 2/8's context-bloat problem, just for structured facts instead of raw messages).
3. **Lost narrative of change** — "latest wins" overwriting (Pattern 7's simple scalar merge) loses the fact that a promotion or a budget increase *happened* — information that itself might matter ("Sarah was promoted from VP to SVP in March" is often more useful to know than just "Sarah is currently SVP").

Memory Consolidation solves this by running a **dedicated reconciliation pass** — using the LLM's reasoning ability to look at the full raw history of an entity/topic and produce a clean, current, non-contradictory record, while explicitly noting resolved changes rather than silently erasing the old value.

---

## 3. Realistic Production/Enterprise Scenario

**Scenario: continuing "SalesCopilot" (Pattern 7) — periodic account consolidation**

- Over a multi-month sales relationship, the Entity Memory store for account `deal_204` has accumulated many raw entries about `sarah` and `acme corp` — some now outdated, one pair now contradictory (`$50k` then later `$75k` after a stated budget increase).
- Left as raw accumulated entries, a rep asking *"what's Acme's current budget?"* risks getting a confused or wrong answer if the model doesn't correctly weigh recency, or the growing `said` list becomes too long to usefully fit in context.
- The business wants a **clean, current, one-paragraph account summary** available at any time — refreshed periodically (e.g., nightly, or after every N new facts) — that a rep can trust as *the* current understanding of the account, with a clear note of anything that changed and why.

This is exactly what Memory Consolidation exists for: **turning a growing pile of raw, potentially-contradictory accumulated facts into one clean, current, explainable record**, on a recurring cadence rather than leaving reconciliation to chance at query time.

---

## 4. Architecture / Flow Diagram

```
                     TRIGGER: periodic (nightly) OR after N new raw facts
                     ┌───────────────────────────────────────────┐
                     │   consolidate_entity("acme corp", deal_204)   │
                     └───────────────────┬───────────────────────────┘
                                          │
                                          ▼
                     ┌───────────────────────────────────────────┐
                     │              LangGraph StateGraph             │
                     │                                                │
                     │  ┌─────────────────────┐                      │
                     │  │  load_raw_facts_node    │  fetches ALL raw     │
                     │  │                        │  accumulated entries   │
                     │  │                        │  for this entity        │
                     │  └──────────┬─────────────┘                      │
                     │             ▼                                     │
                     │  ┌─────────────────────┐                        │
                     │  │  reconcile_node          │───▶ ChatOllama         │
                     │  │  (LLM detects              │    (llama3.1)         │
                     │  │   contradictions, merges     │    consolidates       │
                     │  │   duplicates, produces one     │                      │
                     │  │   clean record + change log)    │                      │
                     │  └──────────┬─────────────┘                        │
                     │             ▼                                       │
                     │  ┌─────────────────────┐                          │
                     │  │  write_consolidated_     │  REPLACES the raw        │
                     │  │  record_node              │  accumulated entries       │
                     │  │                          │  with ONE clean record       │
                     │  │                          │  (+ a preserved change log)   │
                     │  └──────────┬─────────────┘                          │
                     └────────────────┼───────────────────────────────────────┘
                                       ▼
                     ┌───────────────────────────────────────────┐
                     │          Entity store (from Pattern 7)         │
                     │                                                │
                     │  BEFORE: 40+ raw, possibly-contradictory         │
                     │  entries accumulated over 6 months                │
                     │                                                │
                     │  AFTER:  1 clean current record +                 │
                     │          a short "change log" of resolved         │
                     │          contradictions (budget: $50k -> $75k,     │
                     │          confirmed March 2026)                      │
                     └───────────────────────────────────────────┘
```

**Key idea:** Consolidation runs **separately from normal read/write traffic** — it's a maintenance operation, not something that happens on every chat turn — and its output **replaces** noisy raw accumulation with a clean record, while explicitly preserving *what changed*, not just the final value.

---

## 5. Complete Request-to-Response Flow

For account `deal_204`, entity `acme corp`, after 6 months of accumulated raw facts:

1. **A scheduled job (or a threshold trigger — e.g., "10+ new raw facts since last consolidation") fires** `consolidate_entity("acme corp", "deal_204")`.
2. **`load_raw_facts_node`** fetches every raw fact ever recorded for `acme corp` under this deal — including the contradictory budget entries, near-duplicates, and older attributes.
3. **`reconcile_node`** sends the full raw list to `ChatOllama` with a reconciliation prompt: *"Here are all raw facts recorded about this entity over time. Produce (a) one clean, current record, and (b) a short change log noting any contradictions you resolved and how."* The model reasons that the `$75k` figure is more recent and explicitly supersedes `$50k`, and that near-duplicate statements should collapse into one.
4. The LLM returns structured output:
   ```json
   {
     "consolidated_record": {"budget": "$75k", "evaluating": ["DataFlow Inc"], "status": "active deal"},
     "change_log": ["Budget increased from $50k to $75k (confirmed after initial quote)."]
   }
   ```
5. **`write_consolidated_record_node`** **replaces** the entity's raw, sprawling entry set in the store with this one clean record (plus the short change log kept as a compact, permanent note — not the original 40 raw entries).
6. **Next time** a rep asks *"What's Acme's budget?"*, retrieval (Pattern 7's `resolve_entities_node`) reads this clean, consolidated record directly — fast, unambiguous, and explainable, instead of forcing the chat-time LLM to re-reconcile 40 raw entries on every single query.

---

## 6. Why This Pattern Is Appropriate

| Requirement | How Memory Consolidation satisfies it |
|---|---|
| Trustworthy "current state" answers | Contradictions are resolved once, during consolidation — not re-guessed at every query |
| Bounded storage/context over long relationships | Raw entries collapse into one compact record periodically, instead of growing forever |
| Explainability of change | A change log preserves *why* something changed, not just the silently-overwritten final value |
| Query-time performance | Chat-time retrieval reads a small, clean record instead of reasoning over dozens of raw entries every turn |

**Trade-offs / when it's not enough:**
- Consolidation is itself an LLM-driven reconciliation step — it can occasionally **misjudge** which of two contradictory facts is actually current (e.g., if timestamps are ambiguous or missing), so production systems should ensure raw facts carry reliable timestamps and consider a confidence/human-review step for consolidations that touch business-critical fields.
- Running consolidation costs LLM calls, so it should be **triggered deliberately** — periodically (nightly batch) or by threshold (every N new facts) — not on every write, or it reintroduces the "unnecessary LLM calls" cost problem from earlier patterns.
- Consolidation reduces raw *volume* but is not the same as **Memory Compression** (Pattern 15), which focuses specifically on shrinking representation size (e.g., for token/storage efficiency) — the two are complementary: consolidation is about *correctness/cleanliness*, compression is about *size*.

---

## 7. Production-Quality Python Implementation

### Requirements

```bash
pip install -U langchain-ollama langgraph langchain-core
# Ollama model used locally:
#   ollama pull llama3.1
```

### `memory_consolidation.py`

```python
"""
Pattern 14: Memory Consolidation
Periodically reconciling accumulated, possibly-contradictory raw facts
about an entity into one clean, current record with a change log,
built on LangGraph's Store + langchain-ollama.

Run:
    python memory_consolidation.py
"""

from __future__ import annotations

import json
import logging
from datetime import datetime, timezone
from typing import TypedDict

from langchain_ollama import ChatOllama
from langgraph.graph import StateGraph, START, END
from langgraph.store.memory import InMemoryStore

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("memory_consolidation")


def build_llm(model: str = "llama3.1", temperature: float = 0.1) -> ChatOllama:
    return ChatOllama(model=model, temperature=temperature, keep_alive="10m")


# --------------------------------------------------------------------------
# 1. Simulate 6 months of RAW, append-only facts accumulated the way
#    Pattern 7's Entity Memory would naturally build them up -- including
#    a genuine contradiction (budget changed) and near-duplicates.
# --------------------------------------------------------------------------
def seed_raw_entity_facts(store: InMemoryStore, namespace: tuple[str, str], entity: str):
    raw_facts = [
        {"timestamp": "2026-01-10T10:00:00Z", "fact": "role: VP Engineering"},
        {"timestamp": "2026-01-10T10:00:00Z", "fact": "budget: $50k"},
        {"timestamp": "2026-02-02T09:00:00Z", "fact": "said: budget is $50k"},
        {"timestamp": "2026-03-05T14:00:00Z", "fact": "role: SVP Engineering (promoted)"},
        {"timestamp": "2026-03-20T11:00:00Z", "fact": "said: budget increased to $75k after internal review"},
        {"timestamp": "2026-04-01T08:00:00Z", "fact": "evaluating competitor: DataFlow Inc"},
        {"timestamp": "2026-04-15T16:00:00Z", "fact": "said: still evaluating DataFlow Inc, no decision yet"},
    ]
    store.put(namespace, f"{entity}_raw_facts", raw_facts)
    logger.info("Seeded %d raw fact(s) for entity=%r", len(raw_facts), entity)


# --------------------------------------------------------------------------
# 2. Graph state
# --------------------------------------------------------------------------
class ConsolidationState(TypedDict):
    deal_id: str
    entity: str
    raw_facts: list[dict]
    consolidated_record: dict
    change_log: list[str]


# --------------------------------------------------------------------------
# 3. Node: load every raw fact accumulated for this entity.
# --------------------------------------------------------------------------
def make_load_raw_facts_node(store: InMemoryStore):
    def load_raw_facts_node(state: ConsolidationState) -> ConsolidationState:
        namespace = ("deals", state["deal_id"])
        item = store.get(namespace, f"{state['entity']}_raw_facts")
        raw_facts = item.value if item else []
        logger.info("Loaded %d raw fact(s) for entity=%r", len(raw_facts), state["entity"])
        return {"raw_facts": raw_facts}

    return load_raw_facts_node


# --------------------------------------------------------------------------
# 4. Node: LLM reconciliation -- detect contradictions, merge duplicates,
#    produce one clean record PLUS an explicit change log.
# --------------------------------------------------------------------------
RECONCILE_PROMPT = """You are consolidating accumulated raw facts about an \
entity into ONE clean, current record. The facts are timestamped and \
listed oldest-first. Later facts about the SAME attribute supersede \
earlier ones (e.g. a role change, a budget update) -- treat this as a \
real-world change, not an error. Merge near-duplicate statements. Ignore \
facts that add no new information beyond what's already captured.

Respond ONLY with JSON in this exact shape:
{{
  "consolidated_record": {{"<attribute>": "<current value>", ...}},
  "change_log": ["<short note on each resolved change, if any>"]
}}

Raw facts (oldest first):
{raw_facts_text}

JSON:"""


def make_reconcile_node(llm: ChatOllama):
    def reconcile_node(state: ConsolidationState) -> ConsolidationState:
        if not state["raw_facts"]:
            return {"consolidated_record": {}, "change_log": []}

        raw_facts_text = "\n".join(f"[{f['timestamp']}] {f['fact']}" for f in state["raw_facts"])
        prompt = RECONCILE_PROMPT.format(raw_facts_text=raw_facts_text)

        reconcile_llm = llm.bind(format="json")
        try:
            raw = reconcile_llm.invoke(prompt).content
            parsed = json.loads(raw)
        except Exception:
            logger.exception("Consolidation LLM call failed to parse; aborting this run.")
            return {"consolidated_record": {}, "change_log": []}

        record = parsed.get("consolidated_record", {})
        change_log = parsed.get("change_log", [])

        logger.info("Consolidated record: %s", record)
        logger.info("Change log: %s", change_log)
        return {"consolidated_record": record, "change_log": change_log}

    return reconcile_node


# --------------------------------------------------------------------------
# 5. Node: REPLACE the raw, sprawling fact list with the clean record +
#    a compact, permanent change log. This is what keeps storage bounded.
# --------------------------------------------------------------------------
def make_write_consolidated_node(store: InMemoryStore):
    def write_consolidated_record_node(state: ConsolidationState) -> ConsolidationState:
        if not state["consolidated_record"]:
            return {}

        namespace = ("deals", state["deal_id"])
        consolidated_entry = {
            "record": state["consolidated_record"],
            "change_log": state["change_log"],
            "consolidated_at": datetime.now(timezone.utc).isoformat(),
            "source_fact_count": len(state["raw_facts"]),
        }

        # Replace the raw entries entirely -- this IS the consolidation:
        # from N raw entries down to 1 clean record.
        store.put(namespace, state["entity"], consolidated_entry)
        store.delete(namespace, f"{state['entity']}_raw_facts")

        logger.info(
            "Consolidated %d raw fact(s) into 1 clean record for entity=%r",
            state["consolidated_entry_count"] if "consolidated_entry_count" in state else len(state["raw_facts"]),
            state["entity"],
        )
        return {}

    return write_consolidated_record_node


# --------------------------------------------------------------------------
# 6. Assemble the consolidation graph -- this runs as a MAINTENANCE job,
#    separate from the normal chat graph in Pattern 7.
# --------------------------------------------------------------------------
def build_graph(store: InMemoryStore):
    llm = build_llm()

    graph_builder = StateGraph(ConsolidationState)
    graph_builder.add_node("load_raw_facts_node", make_load_raw_facts_node(store))
    graph_builder.add_node("reconcile_node", make_reconcile_node(llm))
    graph_builder.add_node("write_consolidated_record_node", make_write_consolidated_node(store))

    graph_builder.add_edge(START, "load_raw_facts_node")
    graph_builder.add_edge("load_raw_facts_node", "reconcile_node")
    graph_builder.add_edge("reconcile_node", "write_consolidated_record_node")
    graph_builder.add_edge("write_consolidated_record_node", END)

    return graph_builder.compile(store=store)


# --------------------------------------------------------------------------
# 7. Service wrapper -- exposes consolidation as a callable maintenance
#    operation (in production: triggered by a scheduler, e.g. nightly).
# --------------------------------------------------------------------------
class MemoryConsolidationService:
    def __init__(self):
        self.store = InMemoryStore()
        self.graph = build_graph(self.store)

    def consolidate_entity(self, deal_id: str, entity: str) -> dict:
        result = self.graph.invoke({"deal_id": deal_id, "entity": entity, "raw_facts": [],
                                     "consolidated_record": {}, "change_log": []})
        return result

    def get_consolidated_record(self, deal_id: str, entity: str) -> dict | None:
        item = self.store.get(("deals", deal_id), entity)
        return item.value if item else None


# --------------------------------------------------------------------------
# 8. Demo: seed 6 months of raw, contradictory facts, consolidate, and
#    show the clean record + change log that replaces them.
# --------------------------------------------------------------------------
if __name__ == "__main__":
    service = MemoryConsolidationService()
    deal_id, entity = "deal_204", "acme_corp"

    seed_raw_entity_facts(service.store, ("deals", deal_id), entity)

    print("--- Running consolidation ---")
    service.consolidate_entity(deal_id, entity)

    print("\n--- Clean, consolidated record (replaces the raw pile) ---")
    record = service.get_consolidated_record(deal_id, entity)
    print(json.dumps(record, indent=2))
```

### Notes on production-readiness in this code

- **Timestamped raw facts as input** — reconciliation critically depends on knowing *when* each fact was recorded, so the LLM can correctly infer that a later value supersedes an earlier one (a promotion, a budget increase) rather than guessing.
- **Explicit change log, not silent overwrite** — the consolidated record isn't just "the latest value wins" (Pattern 7's simpler approach); it preserves a short, permanent note of *what changed*, which is often as valuable as the current value itself for business context.
- **Raw entries are deleted after consolidation** (`store.delete(..., f"{entity}_raw_facts")`) — this is what actually bounds storage growth; without this step, you'd have both the raw pile *and* the clean record, doubling storage with no benefit.
- **Consolidation is a separate graph/maintenance operation**, not wired into the per-turn chat graph from Pattern 7 — this reflects the real deployment shape: a scheduler (cron, a queue consumer, etc.) triggers `consolidate_entity` periodically or on a threshold, independent of live chat traffic.
- **Fails safe**: if the reconciliation LLM call fails to parse, `write_consolidated_record_node` is a no-op (returns `{}` upstream) rather than replacing good raw data with an empty or malformed record — a failed consolidation attempt should never destroy the underlying raw facts.

---

## 8. Version Pins (latest stable, compatible, at time of writing)

```
python            >= 3.11
langchain-core    >= 0.3.0
langchain-ollama  >= 0.2.0
langgraph         >= 0.2.0
```

```bash
pip install -U "langchain-core>=0.3.0" "langchain-ollama>=0.2.0" "langgraph>=0.2.0"
```

> Uses `langgraph.store.memory.InMemoryStore`, the same store abstraction as Pattern 7; swap for a persistent store in production so consolidated records survive restarts.

---

**Next up → Pattern 15: Memory Compression** (shrinking the *representation size* of retained memory — distinct from consolidation's focus on correctness — for token/storage efficiency at scale).
