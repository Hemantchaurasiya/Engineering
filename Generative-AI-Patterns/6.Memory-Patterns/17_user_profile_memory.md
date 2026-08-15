# Pattern 17: User Profile Memory

> Part of the **Memory Patterns** series (Section 6) — **final pattern**. Stack: `langchain-ollama` (ChatOllama + OllamaEmbeddings), `langgraph`, local models (`llama3.1`, `nomic-embed-text`).

---

## 1. Introduce the Pattern

**User Profile Memory** is the **capstone pattern** of this series: a single, coherent, production-grade system that answers "what do we know about this specific user?" by combining several patterns already built — **Long-Term Memory** (Pattern 3) for durable cross-session facts, **Entity Memory**'s (Pattern 7) discipline of structured, mergeable records, **Memory Consolidation** (Pattern 14) to keep the profile clean and non-contradictory, and **Memory Forgetting** (Pattern 16) for compliant deletion and TTL-based expiry.

Where Pattern 3 introduced the *basic idea* of a cross-session profile store, User Profile Memory is what that store needs to look like in a **real production system**: every fact tracked with **source, confidence, and recency**; **categorized** by type (so different retention/consolidation policies can apply, as Pattern 16 required); periodically **consolidated** (Pattern 14) rather than left as a raw accumulating pile; subject to **expiry policies** and **full deletability** (Pattern 16); and exposed through a single clean interface the rest of the application (chat nodes, personalization logic, analytics) can rely on.

This pattern is intentionally an **integration**, not a new mechanism — it demonstrates how the patterns in this series compose into the kind of user-profile system a real product actually ships.

---

## 2. Problem It Solves

Building Long-Term Memory (Pattern 3) in isolation, as a simple flat key-value store, works for a demo but breaks down in a real product for reasons this series has surfaced one at a time:

```
Pattern 3 alone:  simple {key: value} facts, "latest write wins" -- no
                  source/confidence tracking, no reconciliation of
                  contradictions, no expiry, no compliant deletion path.

Pattern 7 alone:  per-entity structured records -- but doesn't address
                  the SPECIFIC entity that matters most in most products:
                  the user themselves, with all their own product-specific
                  concerns (privacy, consent, expiry).

Pattern 14 alone: consolidation logic -- but generic, not wired into a
                  clear "this IS the user profile" abstraction the rest
                  of the app can just call.

Pattern 16 alone: deletion/expiry mechanisms -- but not integrated into
                  a day-to-day profile read/write API a chat node would
                  actually use on every turn.
```

A real production team needs **one clear answer** to "how do I read/write/maintain a user's profile," not four separate patterns they have to manually stitch together correctly every time a new feature touches user data. User Profile Memory solves this by defining that single, integrated interface — the same way a well-designed ORM gives you one clean API over what's actually several underlying database concerns (schema, migrations, connection pooling).

---

## 3. Realistic Production/Enterprise Scenario

**Scenario: "PersonaAI" — the unified user-profile layer behind a multi-feature personal assistant**

- PersonaAI powers several product surfaces (a meal-planning feature like Pattern 3's MealPlanner, a scheduling feature, a shopping feature) that **all** need to read and write facts about the same user.
- Facts come from many sources with varying trustworthiness: something the user explicitly stated ("I'm vegetarian") vs. something inferred from behavior ("frequently orders quick meals, likely time-constrained on weeknights") — these need different **confidence levels** and should be treated differently when they conflict.
- The product needs the profile to stay **clean over time** (Consolidation), **respect legal deletion rights** (Forgetting), and **let situational facts expire** while never silently dropping safety-critical ones (also Forgetting) — and it needs all of this behind **one API** every feature team can use correctly without re-deriving these concerns themselves.

This is the natural, final scenario for the series: a **single, well-designed User Profile Memory service** that any feature in a real product can build on, embodying the lessons from Patterns 3, 7, 14, and 16 in one place.

---

## 4. Architecture / Flow Diagram

```
                         Any feature (MealPlanner, Scheduler, Shopping, ...)
                         ┌───────────────────────────────────────────┐
                         │   profile.record_fact(user_id, fact, ...)     │
                         │   profile.get_profile(user_id)                 │
                         │   profile.consolidate(user_id)   [scheduled]     │
                         │   profile.expire(user_id)         [scheduled]     │
                         │   profile.forget(user_id)         [on request]     │
                         └───────────────────┬───────────────────────────┘
                                              │
                                              ▼
                         ┌───────────────────────────────────────────┐
                         │             UserProfileMemory (this pattern)    │
                         │                                                │
                         │  record_fact():                                │
                         │    - tags fact with {source, confidence,        │
                         │      fact_type, recorded_at}   (Pattern 3+7)      │
                         │    - merges into raw fact log per attribute        │
                         │                                                     │
                         │  get_profile():                                     │
                         │    - reads current consolidated view                 │
                         │    - falls back to raw facts if not yet                │
                         │      consolidated                                        │
                         │                                                            │
                         │  consolidate():        (Pattern 14)                         │
                         │    - LLM reconciles raw facts -> ONE clean,                   │
                         │      current profile + change log                              │
                         │                                                                   │
                         │  expire():              (Pattern 16)                              │
                         │    - situational facts: TTL delete                                  │
                         │    - core facts: flag for reconfirmation, never                       │
                         │      silently deleted                                                   │
                         │                                                                            │
                         │  forget():              (Pattern 16)                                        │
                         │    - full deletion of everything for this user_id                             │
                         └───────────────────┬───────────────────────────────────────────────────────┘
                                              ▼
                         ┌───────────────────────────────────────────┐
                         │      LangGraph Store (persistent, per-user)   │
                         │  namespace: ("users", user_id)                 │
                         │    "raw_facts"       -> [ {attr, value, source,│
                         │                             confidence, type,   │
                         │                             recorded_at}, ... ]  │
                         │    "consolidated"     -> {clean current record}   │
                         └───────────────────────────────────────────┘
```

**Key idea:** this is the same underlying Store abstraction from Pattern 3/7, but wrapped in **one disciplined service class** that enforces the metadata (source, confidence, fact_type, recorded_at) every fact needs to support consolidation and expiry correctly — instead of leaving each calling feature to remember to do this consistently on its own.

---

## 5. Complete Request-to-Response Flow

**Day 1 — a feature records a fact:**

1. The MealPlanner feature calls `profile.record_fact(user_id="u_1", attribute="dietary", value="vegetarian", source="user_stated", confidence="high", fact_type="core")`.
2. `UserProfileMemory` appends this to the raw fact log for `u_1`, tagged with today's timestamp.

**Day 1, later — a chat node needs the profile:**

3. A chat node calls `profile.get_profile("u_1")`. No consolidation has run yet, so it falls back to summarizing the raw facts directly — returns `{"dietary": "vegetarian"}`.

**Day 45 — a situational fact is recorded, then ages out:**

4. The Shopping feature records `attribute="meal_time_budget", value="under 15 min (guests visiting)", fact_type="situational"`.
5. 45 days later, a scheduled `profile.expire("u_1")` call finds this situational fact past its 30-day TTL and deletes it — it was never meant to be permanent.

**Day 90 — periodic consolidation:**

6. A scheduled `profile.consolidate("u_1")` call runs (as in Pattern 14): it reads all raw facts accumulated so far, resolves any contradictions (e.g., if a later fact updated `dietary` to `"vegan"`), and writes one clean `consolidated` record + change log.
7. From this point on, `profile.get_profile("u_1")` returns the fast, clean, consolidated record directly instead of re-deriving it from raw facts every time.

**Any time — a deletion request:**

8. `profile.forget("u_1")` is called (per Pattern 16): both the raw fact log and the consolidated record are deleted entirely from the store, and an audit record is logged.

---

## 6. Why This Pattern Is Appropriate

| Requirement | How User Profile Memory satisfies it |
|---|---|
| One consistent API across many features | `record_fact` / `get_profile` centralizes what would otherwise be duplicated, inconsistent logic per feature |
| Facts trustworthy and explainable | Every fact carries `source` and `confidence`, enabling smarter consolidation decisions |
| Profile stays clean over time | Built-in `consolidate()` (Pattern 14's logic) prevents raw-fact sprawl and contradictions |
| Compliant and safe over the long run | Built-in `expire()` and `forget()` (Pattern 16's logic) are first-class, not bolted on later |

**Trade-offs / when it's not enough:**
- This pattern is deliberately an **integration/composition** of Patterns 3, 7, 14, and 16 — it doesn't introduce fundamentally new mechanics, so if your product only needs a very simple profile, adopting the full service may be more machinery than necessary; smaller products can start with plain Pattern 3 and grow into this as complexity demands.
- A single unified profile store across multiple product features (as in the PersonaAI scenario) requires **careful attribute namespacing** so that, e.g., MealPlanner's `"budget"` (a grocery dollar amount) doesn't collide with a different feature's unrelated `"budget"` attribute — real systems typically prefix attributes by domain (`meal_planner.budget`).
- As with every pattern in this series that uses an LLM for extraction/consolidation, the **quality of the profile is only as good as the extraction and reconciliation prompts** — this pattern inherits that dependency from Patterns 3 and 14, and production systems should monitor and evaluate profile quality over time, not assume it's correct by construction.

---

## 7. Production-Quality Python Implementation

### Requirements

```bash
pip install -U langchain-ollama langgraph langchain-core
# Ollama model used locally:
#   ollama pull llama3.1
```

### `user_profile_memory.py`

```python
"""
Pattern 17 (final): User Profile Memory
A unified, production-grade user profile service integrating Long-Term
Memory (Pattern 3), Entity Memory's structured-record discipline
(Pattern 7), Memory Consolidation (Pattern 14), and Memory Forgetting
(Pattern 16) behind one clean API, built on LangGraph's Store +
langchain-ollama.

Run:
    python user_profile_memory.py
"""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Literal

from langchain_ollama import ChatOllama
from langgraph.store.memory import InMemoryStore

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("user_profile_memory")

FactType = Literal["core", "situational"]
Confidence = Literal["low", "medium", "high"]

SITUATIONAL_TTL_DAYS = 30
CORE_RECONFIRM_DAYS = 365


def build_llm(model: str = "llama3.1", temperature: float = 0.1) -> ChatOllama:
    return ChatOllama(model=model, temperature=temperature, keep_alive="10m")


@dataclass
class RawFact:
    attribute: str
    value: str
    source: str          # e.g. "user_stated", "inferred_from_behavior"
    confidence: Confidence
    fact_type: FactType
    recorded_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    needs_reconfirmation: bool = False


# --------------------------------------------------------------------------
# The unified User Profile Memory service.
# --------------------------------------------------------------------------
class UserProfileMemory:
    def __init__(self, store: InMemoryStore | None = None, llm: ChatOllama | None = None):
        self.store = store or InMemoryStore()
        self.llm = llm or build_llm()

    # ---- WRITE PATH (Pattern 3 + 7 discipline: structured, tagged facts) ----
    def record_fact(
        self,
        user_id: str,
        attribute: str,
        value: str,
        source: str,
        confidence: Confidence = "medium",
        fact_type: FactType = "core",
    ) -> None:
        namespace = ("users", user_id)
        existing_item = self.store.get(namespace, "raw_facts")
        raw_facts = existing_item.value if existing_item else []

        new_fact = RawFact(
            attribute=attribute, value=value, source=source,
            confidence=confidence, fact_type=fact_type,
        )
        raw_facts.append(new_fact.__dict__)
        self.store.put(namespace, "raw_facts", raw_facts)
        logger.info("Recorded fact for user=%r: %s=%r (source=%s, type=%s)",
                    user_id, attribute, value, source, fact_type)

    # ---- READ PATH: prefer the clean consolidated view; fall back to raw ----
    def get_profile(self, user_id: str) -> dict:
        namespace = ("users", user_id)
        consolidated_item = self.store.get(namespace, "consolidated")
        if consolidated_item:
            return consolidated_item.value["record"]

        # No consolidation has run yet -- derive a simple latest-wins view
        # from raw facts as a fallback (same simplicity as Pattern 3 alone).
        raw_item = self.store.get(namespace, "raw_facts")
        raw_facts = raw_item.value if raw_item else []
        fallback = {}
        for fact in raw_facts:
            fallback[fact["attribute"]] = fact["value"]
        return fallback

    # ---- CONSOLIDATION (Pattern 14): periodic reconciliation ----
    CONSOLIDATE_PROMPT = """Consolidate these raw, timestamped facts about a \
user into ONE clean, current profile record. Later facts about the same \
attribute supersede earlier ones. Merge near-duplicates. Note any real \
changes (not errors) in a short change log.

Respond ONLY with JSON:
{{"consolidated_record": {{"<attribute>": "<value>", ...}}, "change_log": ["..."]}}

Raw facts (oldest first):
{raw_facts_text}

JSON:"""

    def consolidate(self, user_id: str) -> dict:
        namespace = ("users", user_id)
        raw_item = self.store.get(namespace, "raw_facts")
        raw_facts = raw_item.value if raw_item else []
        if not raw_facts:
            return {}

        raw_facts_text = "\n".join(
            f"[{f['recorded_at']}] {f['attribute']}: {f['value']} "
            f"(source={f['source']}, confidence={f['confidence']})"
            for f in raw_facts
        )
        prompt = self.CONSOLIDATE_PROMPT.format(raw_facts_text=raw_facts_text)

        consolidate_llm = self.llm.bind(format="json")
        try:
            raw = consolidate_llm.invoke(prompt).content
            parsed = json.loads(raw)
        except Exception:
            logger.exception("Consolidation failed for user=%r; profile unchanged.", user_id)
            return {}

        entry = {
            "record": parsed.get("consolidated_record", {}),
            "change_log": parsed.get("change_log", []),
            "consolidated_at": datetime.now(timezone.utc).isoformat(),
        }
        self.store.put(namespace, "consolidated", entry)
        logger.info("Consolidated profile for user=%r: %s", user_id, entry["record"])
        return entry

    # ---- EXPIRY (Pattern 16): TTL for situational, reconfirm-flag for core ----
    def expire(self, user_id: str) -> dict:
        namespace = ("users", user_id)
        raw_item = self.store.get(namespace, "raw_facts")
        raw_facts = raw_item.value if raw_item else []
        if not raw_facts:
            return {"expired": 0, "flagged": 0}

        now = datetime.now(timezone.utc)
        kept, expired_count, flagged_count = [], 0, 0

        for fact in raw_facts:
            recorded_at = datetime.fromisoformat(fact["recorded_at"])
            age_days = (now - recorded_at).days

            if fact["fact_type"] == "situational" and age_days > SITUATIONAL_TTL_DAYS:
                expired_count += 1
                continue  # dropped -- situational facts are meant to lapse

            if fact["fact_type"] == "core" and age_days > CORE_RECONFIRM_DAYS:
                fact = {**fact, "needs_reconfirmation": True}
                flagged_count += 1

            kept.append(fact)

        self.store.put(namespace, "raw_facts", kept)
        logger.info("Expiry sweep for user=%r: expired=%d, flagged=%d",
                    user_id, expired_count, flagged_count)
        return {"expired": expired_count, "flagged": flagged_count}

    # ---- FORGETTING (Pattern 16): full, compliant deletion ----
    def forget(self, user_id: str) -> dict:
        namespace = ("users", user_id)
        items = self.store.search(namespace)
        for item in items:
            self.store.delete(namespace, item.key)
        logger.info("Forgot all profile data for user=%r (%d record(s) deleted)", user_id, len(items))
        return {"records_deleted": len(items)}


# --------------------------------------------------------------------------
# Demo: simulate a realistic profile lifecycle -- record facts from
# different sources over time, expire a situational one, consolidate a
# contradiction, then demonstrate full deletion.
# --------------------------------------------------------------------------
if __name__ == "__main__":
    profile = UserProfileMemory()
    user_id = "u_1"

    print("--- Day 1: MealPlanner records a user-stated core fact ---")
    profile.record_fact(user_id, "dietary", "vegetarian", source="user_stated",
                         confidence="high", fact_type="core")
    print("Current profile (pre-consolidation, raw fallback):", profile.get_profile(user_id))

    print("\n--- Day 1: Shopping feature records a situational fact ---")
    profile.record_fact(user_id, "meal_time_budget", "under 15 min (guests visiting)",
                         source="user_stated", confidence="high", fact_type="situational")

    print("\n--- Day 20: dietary preference updates (contradiction to resolve later) ---")
    profile.record_fact(user_id, "dietary", "vegan", source="user_stated",
                         confidence="high", fact_type="core")

    print("\n--- Day 45: expiry sweep runs -- situational fact should lapse ---")
    # Manually age the situational fact for this demo by editing its timestamp.
    namespace = ("users", user_id)
    raw = profile.store.get(namespace, "raw_facts").value
    for fact in raw:
        if fact["attribute"] == "meal_time_budget":
            fact["recorded_at"] = (datetime.now(timezone.utc) - timedelta(days=45)).isoformat()
    profile.store.put(namespace, "raw_facts", raw)

    expiry_result = profile.expire(user_id)
    print("Expiry result:", expiry_result)

    print("\n--- Day 46: consolidation resolves the dietary contradiction ---")
    consolidation_result = profile.consolidate(user_id)
    print("Consolidated record:", consolidation_result.get("record"))
    print("Change log:", consolidation_result.get("change_log"))

    print("\n--- Day 46: get_profile() now returns the fast, clean, consolidated view ---")
    print(profile.get_profile(user_id))

    print("\n--- Later: user requests full account deletion ---")
    forget_result = profile.forget(user_id)
    print("Forget result:", forget_result)
    print("Profile after deletion:", profile.get_profile(user_id))
```

### Notes on production-readiness in this code

- **One class, four responsibilities, cleanly separated methods** — `record_fact`, `get_profile`, `consolidate`, `expire`, `forget` map directly onto Patterns 3/7, 14, and 16 respectively, so anyone who has read this series can immediately see which underlying pattern each method embodies.
- **Every fact is tagged with `source` and `confidence`** at write time — this is what makes intelligent consolidation possible later (a `high`-confidence, `user_stated` fact should generally outrank a `low`-confidence, `inferred_from_behavior` one when they conflict, information the consolidation prompt can be extended to use).
- **`get_profile()` degrades gracefully** — it returns a useful (if simpler) answer even before the first consolidation has ever run, rather than requiring consolidation as a hard prerequisite for the profile to be usable at all.
- **`expire()` and `forget()` are both first-class, tested code paths**, not afterthoughts bolted on — reflecting the lesson from Pattern 16 that these need to be as reliable and well-integrated as the read/write paths themselves.
- **Fully separable across product features via `attribute` naming** — this implementation stores flat `attribute: value` pairs; a real multi-feature deployment (as described in the scenario) should prefix attributes by domain (e.g., `"meal_planner.dietary"`) to avoid cross-feature collisions, as noted in the trade-offs above.

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

> Uses `langgraph.store.memory.InMemoryStore` for the demo; swap for a persistent, multi-instance-safe `BaseStore` implementation (per Pattern 11) in production so profiles survive restarts and are consistent across app replicas.

---

## Series Complete

This is the **final pattern (17/17)** in the Memory Patterns series. Together, Patterns 1-17 form a complete toolkit:

- **Patterns 1-2**: the foundation — raw conversation history and how to bound it
- **Patterns 3-7**: what to remember and how to structure it — long-term facts, semantic knowledge, episodes, procedures, entities
- **Pattern 8**: compressing conversation itself without losing meaning
- **Patterns 9-10**: the infrastructure underneath — vector stores and live external systems
- **Pattern 11**: making all of it durable
- **Pattern 12**: knowing what should *not* be durable
- **Pattern 13**: retrieving well from what you've stored
- **Patterns 14-16**: keeping stored memory correct, small, and compliant over time
- **Pattern 17**: pulling it all together into one production-grade system

Thanks for building through this series — see `README.md` for the full index and every pattern file for its complete, standalone implementation.
