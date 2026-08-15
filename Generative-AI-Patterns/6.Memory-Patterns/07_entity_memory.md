# Pattern 7: Entity Memory

> Part of the **Memory Patterns** series (Section 6). Stack: `langchain-ollama` (ChatOllama + OllamaEmbeddings), `langgraph`, local models (`llama3.1`, `nomic-embed-text`).

---

## 1. Introduce the Pattern

**Entity Memory** tracks structured facts **about specific named entities** — people, companies, services, products — mentioned during a conversation, keeping a running, per-entity record that gets updated as new facts emerge and is recalled whenever that entity is referenced again, by name or pronoun.

This is different from Pattern 3 (Long-Term Memory), which tracked a **single flat profile for one user**. Real conversations mention **many different entities**, each needing its own mini-record:

```
"I spoke with Sarah from Acme Corp. She's their VP of Engineering.
 Their budget for this deal is $50k. Separately, Acme is also
 evaluating our competitor, DataFlow Inc."
```

This one message alone introduces **three entities**: *Sarah* (role: VP Engineering, affiliated with Acme Corp), *Acme Corp* (budget: $50k, evaluating a competitor), and *DataFlow Inc* (competitor, being evaluated by Acme). Entity Memory's job is to recognize each of these, maintain a structured, evolving record per entity, and — critically — **resolve later references** ("What did Sarah say about budget?", "Is Acme still talking to that competitor?") back to the right entity's accumulated facts.

---

## 2. Problem It Solves

Without Entity Memory, a conversation involving multiple people/companies/systems quickly becomes incoherent, because the model has no structured way to track *who* has *what* attributes, especially as the same entity is referenced by different names, pronouns, or partial mentions over a long conversation:

```
Turn 5:  "Sarah mentioned their budget is $50k."
Turn 40: "What's Acme's budget again?"
Bot:     [no idea — that fact was attached to "Sarah" in raw conversation
          text 35 turns ago and was never explicitly linked to "Acme Corp"]
```

Raw Conversation Memory (Pattern 1) *contains* this information somewhere in the transcript, but relies entirely on the LLM re-reading and correctly re-associating scattered facts across a long history — increasingly unreliable as conversations grow, and impossible once older turns get trimmed (Pattern 2) or summarized (Pattern 8).

Entity Memory solves this by:
- **Extracting entities and their attributes** as they're mentioned, not leaving that work to be re-done from raw text every time
- **Maintaining one structured record per entity**, merging new facts into what's already known (not overwriting)
- **Resolving entity mentions** in the current message back to existing records (name matching, aliasing)
- **Injecting only the relevant entities' facts** into context for the current turn — not the whole entity store, and not the whole raw transcript

---

## 3. Realistic Production/Enterprise Scenario

**Scenario: "SalesCopilot" — a CRM assistant used during and between sales calls**

- A sales rep talks to an AI copilot throughout a deal's lifecycle — multiple calls, multiple threads, weeks apart — mentioning many people (buyer-side contacts) and companies (the prospect, competitors) along the way.
- Facts accumulate per entity over time: a contact's role, their stated concerns, budget figures attached to a company, which competitors a company is evaluating.
- The rep needs to ask entity-scoped questions at any point: *"What did Sarah say about timeline?"*, *"Remind me of Acme's budget,"* *"Which competitors is this account looking at?"* — and get accurate, up-to-date, per-entity answers, not a re-scan of a huge raw transcript.
- New calls introduce **new** entities (a second stakeholder, "Tom, the CFO") that need to be tracked from scratch, while **existing** entities ("Sarah," "Acme Corp") need their records *updated*, not replaced, as new details emerge.

This is exactly what Entity Memory is built for: **a live, structured, per-entity knowledge base that grows as a relationship with a company/deal grows**, distinct from a single-user profile (Pattern 3) or a pile of loose facts (Pattern 4).

---

## 4. Architecture / Flow Diagram

```
                     ┌───────────────────────────────────────────┐
                     │  Client: "What did Sarah say about budget?"  │
                     │  {deal_id: "deal_204"}                        │
                     └───────────────────┬───────────────────────────┘
                                          │
                                          ▼
                     ┌───────────────────────────────────────────┐
                     │              LangGraph StateGraph             │
                     │                                                │
                     │  ┌─────────────────────────┐                  │
                     │  │ resolve_entities_node        │  finds entity  │
                     │  │                              │  names          │
                     │  │                              │  mentioned in   │
                     │  │                              │  current message│
                     │  └────────────┬─────────────────┘                  │
                     │               │  matched entity names                │
                     │               ▼                                       │
                     │  ┌─────────────────────────┐                        │
                     │  │  chat_node                  │──▶ ChatOllama         │
                     │  │  (matched entities' full     │    (llama3.1)         │
                     │  │  records injected)            │                        │
                     │  └────────────┬─────────────────┘                        │
                     │               │                                           │
                     │               ▼                                           │
                     │  ┌─────────────────────────┐                          │
                     │  │ extract_and_merge_entities_  │  finds NEW/updated     │
                     │  │ node                          │  entity facts in this  │
                     │  │                              │  turn, merges into      │
                     │  │                              │  each entity's record    │
                     │  └────────────┬─────────────────┘                          │
                     └────────────────┼───────────────────────────────────────────┘
                                       ▼
                     ┌───────────────────────────────────────────┐
                     │            Entity store (persistent)          │
                     │  namespace: ("deals", "deal_204")              │
                     │                                                │
                     │  "sarah"     -> {role: "VP Engineering",       │
                     │                   company: "Acme Corp",         │
                     │                   said: ["budget is $50k"]}      │
                     │  "acme corp" -> {budget: "$50k",                │
                     │                   evaluating: ["DataFlow Inc"]}   │
                     │  "dataflow inc" -> {relation: "competitor"}       │
                     └───────────────────────────────────────────┘
```

**Key idea:** unlike Semantic Memory's undifferentiated fact soup (Pattern 4), Entity Memory keeps facts **grouped by the specific named thing they're about**, and reference resolution ("Sarah", "she", "the VP") is a first-class step, not left to chance in a long raw transcript.

---

## 5. Complete Request-to-Response Flow

For `deal_id = "deal_204"`, mid-way through a multi-call sales relationship:

1. **Rep sends** `"What did Sarah say about budget?"`
2. **`resolve_entities_node`** scans the message for known entity names/aliases already in the store for this deal (`sarah`, `acme corp`, `dataflow inc`) using normalized name matching, and finds **`sarah`** referenced.
3. Sarah's full accumulated record is pulled: `{role: "VP Engineering", company: "Acme Corp", said: ["budget is $50k"]}`.
4. **`chat_node`** calls `ChatOllama.invoke(...)` with that record injected into the system prompt. The model answers precisely: *"Sarah mentioned the budget is $50k."*
5. **`extract_and_merge_entities_node`** checks if this turn introduced any *new* entity facts — in this case, it's just a question, so nothing new is merged.
6. **Response returned.**

**A later turn that introduces a new fact:**

1. **Rep sends** `"Just talked to Tom, their CFO — he confirmed the $50k budget but said final approval needs legal sign-off."`
2. **`resolve_entities_node`** finds no existing record for `"tom"` (new entity) and confirms `"acme corp"` is already known (via "their").
3. **`chat_node`** responds normally.
4. **`extract_and_merge_entities_node`** runs an LLM extraction over this turn, producing structured entity updates:
   ```json
   {"tom": {"role": "CFO", "company": "Acme Corp", "said": ["confirmed $50k budget", "needs legal sign-off"]}}
   ```
5. This is **merged** into the entity store — a brand-new record is created for `tom`, while `sarah`'s and `acme corp`'s existing records are untouched.

---

## 6. Why This Pattern Is Appropriate

| Requirement | How Entity Memory satisfies it |
|---|---|
| Track many distinct people/companies within one relationship | Each entity gets its own structured, independently-updated record |
| Answer entity-scoped questions accurately, even much later | Facts are grouped by entity name, not buried in raw chat order |
| Handle new entities appearing over time | New names create new records automatically via extraction |
| Avoid overwriting known facts with each mention | Records are *merged*, not replaced, on every update |

**Trade-offs / when it's not enough:**
- Name resolution here uses **simple normalized matching** (and LLM-assisted extraction); it doesn't robustly solve hard coreference (e.g., "she" referring to whichever woman was mentioned three sentences ago without a name) — production systems needing that level of accuracy typically add a dedicated coreference-resolution step or lean on the LLM more heavily during extraction.
- Entity Memory tracks attributes well but doesn't inherently timestamp *when* a fact was learned the way **Episodic Memory** (Pattern 5) does — if Sarah's stated budget changes over time, this pattern as shown appends to a list (`said: [...]`) rather than tracking a clean timeline; combining Entity + Episodic patterns solves that.
- Like Pattern 3, entity records can accumulate stale or contradictory facts over a long relationship — **Memory Consolidation** (Pattern 14) is the natural next step to periodically reconcile an entity's record into a clean, de-duplicated summary.

---

## 7. Production-Quality Python Implementation

### Requirements

```bash
pip install -U langchain-ollama langgraph langchain-core
# Ollama model used locally:
#   ollama pull llama3.1
```

### `entity_memory.py`

```python
"""
Pattern 7: Entity Memory
Structured, per-named-entity facts that accumulate over a relationship
(deal, account, project) and are resolved/injected by name, built on
LangGraph's Store + langchain-ollama.

Run:
    python entity_memory.py
"""

from __future__ import annotations

import json
import logging
import re
from typing import Annotated, TypedDict

from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    SystemMessage,
)
from langchain_ollama import ChatOllama
from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages
from langgraph.store.memory import InMemoryStore

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("entity_memory")


# --------------------------------------------------------------------------
# 1. Graph state
# --------------------------------------------------------------------------
class SalesState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]
    deal_id: str
    matched_entities: dict  # name -> record, resolved for THIS turn


BASE_SYSTEM_PROMPT = (
    "You are SalesCopilot, a CRM assistant for a sales rep. Use known facts "
    "about specific people and companies (entities) to answer precisely. "
    "If an entity isn't known yet, say so rather than guessing."
)


def build_llm(model: str = "llama3.1", temperature: float = 0.2) -> ChatOllama:
    return ChatOllama(model=model, temperature=temperature, keep_alive="10m")


def normalize_name(name: str) -> str:
    return re.sub(r"\s+", " ", name.strip().lower())


# --------------------------------------------------------------------------
# 2. Node: resolve which known entities are referenced in the CURRENT
#    message, so we only inject relevant records (not the whole store).
# --------------------------------------------------------------------------
def make_resolve_entities_node(store: InMemoryStore):
    def resolve_entities_node(state: SalesState) -> SalesState:
        last_human = next(
            (m for m in reversed(state["messages"]) if isinstance(m, HumanMessage)),
            None,
        )
        if last_human is None:
            return {"matched_entities": {}}

        namespace = ("deals", state["deal_id"])
        all_items = store.search(namespace)  # every known entity for this deal
        message_lower = last_human.content.lower()

        matched = {
            item.key: item.value
            for item in all_items
            if item.key in message_lower  # simple, fast substring match on normalized names
        }

        logger.info("Resolved %d known entit(y/ies) in current message: %s",
                    len(matched), list(matched.keys()))
        return {"matched_entities": matched}

    return resolve_entities_node


# --------------------------------------------------------------------------
# 3. Node: chat, with resolved entity records injected.
# --------------------------------------------------------------------------
def make_chat_node(llm: ChatOllama):
    def chat_node(state: SalesState) -> SalesState:
        if state["matched_entities"]:
            entity_lines = []
            for name, record in state["matched_entities"].items():
                entity_lines.append(f"- {name.title()}: {json.dumps(record)}")
            entity_block = "\n".join(entity_lines)
        else:
            entity_block = "(no known entities referenced in this message yet)"

        system_prompt = SystemMessage(
            content=f"{BASE_SYSTEM_PROMPT}\n\nKnown entities relevant to this message:\n{entity_block}"
        )

        try:
            response: AIMessage = llm.invoke([system_prompt, *state["messages"]])
        except Exception:
            logger.exception("LLM call failed")
            response = AIMessage(content="Sorry, I hit an error. Please try again.")

        return {"messages": [response]}

    return chat_node


# --------------------------------------------------------------------------
# 4. Node: extract NEW/updated entity facts from this turn and MERGE
#    them into each entity's existing record (never blind overwrite).
# --------------------------------------------------------------------------
EXTRACTION_PROMPT = """Extract named entities (people or companies) and any \
new facts about them from this message. Respond ONLY with compact JSON \
mapping lowercase entity names to an object of attributes. Use a "said" \
list for direct statements attributed to a person. If a company is \
mentioned only as context with no new fact, omit it.

Example output:
{{"tom": {{"role": "CFO", "company": "acme corp", "said": ["confirmed $50k budget"]}}}}

If nothing new, respond with {{}}.

Message: "{message}"
JSON:"""


def make_extract_node(llm: ChatOllama, store: InMemoryStore):
    def extract_and_merge_entities_node(state: SalesState) -> SalesState:
        last_human = next(
            (m for m in reversed(state["messages"]) if isinstance(m, HumanMessage)),
            None,
        )
        if last_human is None:
            return {}

        extraction_llm = llm.bind(format="json")
        prompt = EXTRACTION_PROMPT.format(message=last_human.content)

        try:
            raw = extraction_llm.invoke(prompt).content
            new_facts = json.loads(raw) if raw.strip() else {}
        except Exception:
            logger.warning("Entity extraction failed to parse; skipping this turn.")
            new_facts = {}

        if not new_facts:
            return {}

        namespace = ("deals", state["deal_id"])
        for raw_name, attrs in new_facts.items():
            name = normalize_name(raw_name)
            existing_item = store.get(namespace, name)
            existing = existing_item.value if existing_item else {}

            merged = dict(existing)
            for key, value in attrs.items():
                if key == "said":
                    merged["said"] = existing.get("said", []) + list(value)
                else:
                    merged[key] = value  # simple fields: latest value wins

            store.put(namespace, name, merged)
            logger.info("Merged entity record for %r: %s", name, merged)

        return {}

    return extract_and_merge_entities_node


# --------------------------------------------------------------------------
# 5. Assemble the graph
# --------------------------------------------------------------------------
def build_graph():
    llm = build_llm()
    store = InMemoryStore()  # swap for a persistent, multi-instance-safe store in prod

    graph_builder = StateGraph(SalesState)
    graph_builder.add_node("resolve_entities_node", make_resolve_entities_node(store))
    graph_builder.add_node("chat_node", make_chat_node(llm))
    graph_builder.add_node("extract_and_merge_entities_node", make_extract_node(llm, store))

    graph_builder.add_edge(START, "resolve_entities_node")
    graph_builder.add_edge("resolve_entities_node", "chat_node")
    graph_builder.add_edge("chat_node", "extract_and_merge_entities_node")
    graph_builder.add_edge("extract_and_merge_entities_node", END)

    return graph_builder.compile(store=store)


# --------------------------------------------------------------------------
# 6. Service wrapper
# --------------------------------------------------------------------------
class EntityMemoryService:
    def __init__(self):
        self.graph = build_graph()

    def send(self, deal_id: str, message: str) -> str:
        result = self.graph.invoke(
            {
                "messages": [HumanMessage(content=message)],
                "deal_id": deal_id,
                "matched_entities": {},
            }
        )
        return result["messages"][-1].content


# --------------------------------------------------------------------------
# 7. Demo: introduce two entities across turns, then ask an entity-scoped
#    question that must resolve back to the right record.
# --------------------------------------------------------------------------
if __name__ == "__main__":
    service = EntityMemoryService()
    deal = "deal_204"

    print("--- Turn 1: introduce Sarah and Acme Corp ---")
    msg = "I spoke with Sarah from Acme Corp. She's their VP of Engineering, and their budget is $50k."
    print("Rep:", msg)
    print("Bot:", service.send(deal, msg))

    print("\n--- Turn 2: introduce a new entity, Tom ---")
    msg = "Just talked to Tom, their CFO -- he confirmed the $50k budget but said final approval needs legal sign-off."
    print("Rep:", msg)
    print("Bot:", service.send(deal, msg))

    print("\n--- Turn 3: entity-scoped question, weeks later, must resolve to Sarah's record ---")
    msg = "What did Sarah say about budget?"
    print("Rep:", msg)
    print("Bot:", service.send(deal, msg))
```

### Notes on production-readiness in this code

- **Per-entity, per-deal namespacing (`("deals", deal_id)`)** keeps entity records scoped correctly — Acme Corp's contacts in one deal never bleed into another deal's entity store.
- **Merge semantics differ by field type**: list-like facts (`said`) are appended to preserve history of statements, while scalar facts (`role`, `company`) use latest-value-wins — a deliberate, explicit merge policy rather than a naive blind overwrite or blind append for everything.
- **Only relevant entities are injected per turn** (`resolve_entities_node` filters by substring match against the current message), not the entire entity store — this keeps the prompt small even as a deal accumulates dozens of tracked entities over months.
- **Constrained JSON extraction** (`llm.bind(format="json")`) keeps entity-fact extraction structured and parseable, with a fallback to skip the turn cleanly if the model's output doesn't parse.
- **`InMemoryStore()` for the demo**, swappable for a persistent, multi-instance-safe store in production (the same consideration as Pattern 3) so entity knowledge survives restarts and is shared across app replicas.

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

> Uses `langgraph.store.memory.InMemoryStore`, part of `langgraph` itself. No extra vector-store dependency is required for this pattern's name-based resolution approach, though production systems needing fuzzier entity resolution could add embedding-based matching (as in Pattern 4) on top of this.

---

**Next up → Pattern 8: Summary Memory** (compressing older parts of a long conversation into a running summary — the smarter alternative to Pattern 2's blunt windowing, so nothing important gets silently dropped).
