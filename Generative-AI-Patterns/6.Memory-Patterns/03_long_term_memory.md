# Pattern 3: Long-Term Memory

> Part of the **Memory Patterns** series (Section 6). Stack: `langchain-ollama` (ChatOllama + OllamaEmbeddings), `langgraph`, local models (`llama3.1`, `nomic-embed-text`).

---

## 1. Introduce the Pattern

**Long-Term Memory** is information that persists **across separate conversations/sessions**, tied to a *user or entity*, not to a single thread.

Patterns 1 and 2 (Conversation Memory, Short-Term Memory) are both **thread-scoped**: everything lives under a `thread_id`, and once that conversation ends, its content is only useful if you re-open that exact thread. But real users don't live in a single thread — they close the chat, come back next week in a *brand-new* conversation, and expect the assistant to still know who they are: their preferences, their past decisions, their profile.

Long-Term Memory solves this by introducing a **second storage dimension**, keyed by `user_id` (or `org_id`, `customer_id`, etc.) instead of `thread_id`. This store is:
- **Cross-session** — survives across many different threads for the same user
- **Selective** — you don't dump the whole transcript in; you extract and store specific durable facts
- **Long-lived** — retained indefinitely (subject to your data-retention policy), unlike a single conversation

In LangGraph terms: the **checkpointer** (Pattern 1) gives you *thread-scoped* memory. A separate **Store** (`BaseStore` / `InMemoryStore` / a persistent store) gives you *cross-thread, user-scoped* Long-Term Memory. Both are used together in production systems.

---

## 2. Problem It Solves

Without Long-Term Memory, every new conversation starts from zero *about the user*, even if they've talked to your system fifty times before:

```
[Monday, thread A]
User: I'm vegetarian, please don't recommend meat dishes.
Bot:  Got it, I'll keep that in mind!

[Thursday, thread B — a brand new conversation]
User: What should I cook tonight?
Bot:  How about a grilled chicken recipe?      <-- forgot everything
```

This breaks the expectation of a "personal assistant" — the whole value proposition of many AI products (shopping assistants, coding copilots that know your codebase conventions, support bots that know your account history) depends on **not** re-learning the same facts every single session.

Long-Term Memory solves this by:
- Extracting durable facts (preferences, profile details, past decisions) as they emerge in conversation
- Storing them keyed by **user**, not by thread
- Retrieving and injecting the relevant facts into the system prompt **at the start of every new conversation**, regardless of which thread it is

---

## 3. Realistic Production/Enterprise Scenario

**Scenario: "MealPlanner AI" — a cooking/grocery assistant used across many independent sessions**

- A user might open a new chat every time they want a recipe idea — sometimes days or weeks apart. Each session is a fresh `thread_id`.
- Certain facts about the user are **durable** and should apply to *every* future conversation: dietary restrictions ("vegetarian"), allergies ("allergic to peanuts" — safety-critical, must never be forgotten), and preferences ("prefers meals under 30 minutes").
- The product team explicitly wants: *"the assistant should feel like it remembers me, even in a conversation I've never had before."*
- At the same time, **not everything said should become long-term memory** — a one-off comment like "I'm in a rush today" shouldn't permanently change what the bot assumes about the user forever. This means the system needs a deliberate **extraction step**, not "just store everything."

This is the canonical Long-Term Memory scenario: a **cross-session user profile store**, populated by an explicit extraction/write step, and read back in on every new thread.

---

## 4. Architecture / Flow Diagram

```
   Thread A (Monday)                          Thread B (Thursday) -- NEW thread_id
┌────────────────────┐                    ┌────────────────────┐
│ StateGraph run       │                    │ StateGraph run       │
│                        │                    │                        │
│ 1. load_profile_node   │  reads            │ 1. load_profile_node   │  reads
│    -> Store.get(       │◀──────┐           │    -> Store.get(       │◀──────┐
│       ("users","u_1")) │       │           │       ("users","u_1")) │       │
│                        │       │           │                        │       │
│ 2. chat_node            │       │           │ 2. chat_node            │       │
│    (uses profile facts  │       │           │    (uses SAME profile   │       │
│     in system prompt)   │       │           │     facts -- persisted  │       │
│                        │       │           │     from Monday!)       │       │
│ 3. extract_memory_node  │       │           │ 3. extract_memory_node  │       │
│    -> finds new durable │       │           │    -> nothing new this  │       │
│       facts, writes to  │───────┘           │       turn               │       │
│       Store             │                    │                        │       │
└────────────────────┘                    └────────────────────┘       │
              │                                             │                    │
              ▼                                             ▼                    │
   ┌──────────────────────────────────────────────────────────┐          │
   │                      Long-Term Store (persistent)             │◀─────────┘
   │   namespace: ("users", "u_1")                                  │
   │   key: "dietary"       -> "vegetarian"                          │
   │   key: "allergy"        -> "peanuts"                             │
   │   key: "time_budget"    -> "prefers meals under 30 minutes"       │
   └──────────────────────────────────────────────────────────┘

   (Separately, each thread's own message history still lives in the
    Pattern-1-style checkpointer, keyed by thread_id, as before.)
```

**Key idea:** two independent stores, two independent keys — `thread_id` for the checkpointer (conversation memory) and `user_id` for the Store (long-term memory). A given user's long-term facts are read at the *start* of every thread and written to whenever new durable facts are detected.

---

## 5. Complete Request-to-Response Flow

**Thread B, Thursday — a brand-new conversation for `user_id="u_1"` (who talked to the bot on Monday in a different thread):**

1. **Client sends** `{"thread_id": "thread_B_20240815", "user_id": "u_1", "message": "What should I cook tonight?"}`
2. **`load_profile_node`** runs first: calls `store.get(("users", "u_1"), "profile")`. This finds the facts written on Monday (`vegetarian`, `allergic to peanuts`, `prefers <30 min meals`) even though this is a totally different `thread_id`.
3. Those facts are formatted into a **profile block** and injected into the system prompt for this call only (they are *not* re-saved to the thread's own checkpointed history as raw messages — they're injected fresh, every time, from the Store).
4. **`chat_node`** calls `ChatOllama.invoke(...)` with the system prompt (now containing the user's known profile) + this thread's own short message history. The model suggests a vegetarian, quick, peanut-free recipe **without being told any of that again**.
5. **`extract_memory_node`** runs after the reply: it asks the LLM (or applies rules) to check if this turn introduced any *new* durable fact worth remembering (e.g., if the user had just said "actually I'm cooking for 4 people now," that might get stored as a new fact: `household_size=4`).
6. Any new facts are written via `store.put(("users", "u_1"), "profile", updated_facts)`.
7. **Response returned** to the client, and the long-term store is now even more complete for the *next* future thread.

Meanwhile, this thread's own raw messages are still separately persisted by the Pattern-1 checkpointer, keyed by `thread_id="thread_B_20240815"` — Long-Term Memory doesn't replace that, it complements it.

---

## 6. Why This Pattern Is Appropriate

| Requirement | How Long-Term Memory satisfies it |
|---|---|
| "Remembers me" across brand-new conversations | Facts are keyed by `user_id`, independent of any single `thread_id` |
| Safety-critical facts never dropped by trimming | Stored durably and re-injected fully every turn — not subject to Short-Term Memory's windowing |
| Avoids re-litigating the same facts every session | Facts are read once at session start and folded into the system prompt |
| Avoids storing noise as permanent fact | An explicit extraction step decides what's "durable" vs. one-off conversational color |

**Trade-offs / when it's not enough:**
- Long-Term Memory as shown here stores **flat key-value facts** (a simple profile). If you need to store many small, semantically searchable snippets ("things the user has ever said about their preferences," at scale) rather than a curated profile, that's **Semantic Memory** / **Vector-Based Memory** (Patterns 4 & 9) — they use embeddings + similarity search instead of exact keys.
- Deciding *what* counts as a durable fact worth extracting is itself a hard problem — later patterns (**Memory Consolidation**, Pattern 14, and **Memory Forgetting**, Pattern 16) address how to merge, deduplicate, and expire long-term facts responsibly over time.
- This pattern assumes a single flat profile per user; multi-fact histories with time-based relevance ("what happened last Tuesday") are the domain of **Episodic Memory** (Pattern 5).

---

## 7. Production-Quality Python Implementation

### Requirements

```bash
pip install -U langchain-ollama langgraph langchain-core
# Ollama model used locally:
#   ollama pull llama3.1
```

### `long_term_memory.py`

```python
"""
Pattern 3: Long-Term Memory
Cross-session, user-scoped facts stored independently of any single
conversation thread, built on LangGraph's Store + langchain-ollama.

Run:
    python long_term_memory.py
"""

from __future__ import annotations

import json
import logging
import sqlite3
from typing import Annotated, TypedDict

from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    SystemMessage,
)
from langchain_ollama import ChatOllama
from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages
from langgraph.store.memory import InMemoryStore

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("long_term_memory")


# --------------------------------------------------------------------------
# 1. Graph state. Note `user_id` is now part of state -- it's how we know
#    which Long-Term Memory namespace to read/write, separate from
#    `thread_id`, which the checkpointer uses for the message history.
# --------------------------------------------------------------------------
class ConversationState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]
    user_id: str
    profile_summary: str  # facts loaded from long-term store, for this turn


BASE_SYSTEM_PROMPT = (
    "You are MealPlanner, a friendly cooking assistant. "
    "Give a specific, concrete suggestion, not generic advice."
)


def build_llm(model: str = "llama3.1", temperature: float = 0.4) -> ChatOllama:
    return ChatOllama(model=model, temperature=temperature, keep_alive="10m")


# --------------------------------------------------------------------------
# 2. Node: load the user's long-term profile at the START of the turn,
#    regardless of which thread this is.
# --------------------------------------------------------------------------
def make_load_profile_node(store: InMemoryStore):
    def load_profile_node(state: ConversationState) -> ConversationState:
        namespace = ("users", state["user_id"])
        item = store.get(namespace, "profile")
        facts = item.value if item else {}

        if facts:
            summary = "; ".join(f"{k}: {v}" for k, v in facts.items())
            logger.info("Loaded long-term profile for %s: %s", state["user_id"], summary)
        else:
            summary = "No known long-term facts about this user yet."
            logger.info("No existing long-term profile for %s", state["user_id"])

        return {"profile_summary": summary}

    return load_profile_node


# --------------------------------------------------------------------------
# 3. Node: chat, using this thread's messages PLUS the injected profile.
# --------------------------------------------------------------------------
def make_chat_node(llm: ChatOllama):
    def chat_node(state: ConversationState) -> ConversationState:
        system_prompt = SystemMessage(
            content=(
                f"{BASE_SYSTEM_PROMPT}\n\n"
                f"Known long-term facts about this user:\n{state['profile_summary']}"
            )
        )
        model_input = [system_prompt, *state["messages"]]

        try:
            response: AIMessage = llm.invoke(model_input)
        except Exception:
            logger.exception("LLM call failed")
            response = AIMessage(content="Sorry, I hit an error. Please try again.")

        return {"messages": [response]}

    return chat_node


# --------------------------------------------------------------------------
# 4. Node: extract any NEW durable facts from this turn and persist them
#    to the long-term Store. Kept intentionally conservative/simple here;
#    in production this extraction call would use structured output
#    (e.g. a JSON schema) rather than free text.
# --------------------------------------------------------------------------
EXTRACTION_PROMPT = """You extract durable user facts (dietary restrictions, \
allergies, standing preferences) from a single chat message. \
Only extract facts that should apply to ALL future conversations \
(e.g. "I'm vegetarian", "I'm allergic to peanuts", "I prefer meals under 30 \
minutes"). Do NOT extract one-off situational statements (e.g. "I'm in a \
rush today", "I have guests over tonight").

Respond ONLY with compact JSON mapping short fact keys to values, e.g.:
{{"dietary": "vegetarian", "allergy": "peanuts"}}
If there is nothing durable to extract, respond with {{}}.

User message: "{message}"
JSON:"""


def make_extract_memory_node(llm: ChatOllama, store: InMemoryStore):
    def extract_memory_node(state: ConversationState) -> ConversationState:
        last_human = next(
            (m for m in reversed(state["messages"]) if isinstance(m, HumanMessage)),
            None,
        )
        if last_human is None:
            return {}

        extraction_llm = llm.bind(format="json")  # ask Ollama for JSON output
        prompt = EXTRACTION_PROMPT.format(message=last_human.content)

        try:
            raw = extraction_llm.invoke(prompt).content
            new_facts = json.loads(raw) if raw.strip() else {}
        except Exception:
            logger.warning("Fact extraction failed to parse; skipping this turn.")
            new_facts = {}

        if not new_facts:
            return {}

        namespace = ("users", state["user_id"])
        existing = store.get(namespace, "profile")
        merged = {**(existing.value if existing else {}), **new_facts}

        store.put(namespace, "profile", merged)
        logger.info("Updated long-term profile for %s: %s", state["user_id"], merged)

        return {}

    return extract_memory_node


# --------------------------------------------------------------------------
# 5. Assemble the graph: two persistence mechanisms working together --
#    checkpointer (thread-scoped) + store (user-scoped, cross-thread).
# --------------------------------------------------------------------------
def build_graph(checkpoint_db: str = "conversations.sqlite"):
    llm = build_llm()
    store = InMemoryStore()  # swap for a persistent store (e.g. Postgres-backed) in prod

    graph_builder = StateGraph(ConversationState)
    graph_builder.add_node("load_profile_node", make_load_profile_node(store))
    graph_builder.add_node("chat_node", make_chat_node(llm))
    graph_builder.add_node("extract_memory_node", make_extract_memory_node(llm, store))

    graph_builder.add_edge(START, "load_profile_node")
    graph_builder.add_edge("load_profile_node", "chat_node")
    graph_builder.add_edge("chat_node", "extract_memory_node")
    graph_builder.add_edge("extract_memory_node", END)

    conn = sqlite3.connect(checkpoint_db, check_same_thread=False)
    checkpointer = SqliteSaver(conn)

    # `store=` is what makes cross-thread long-term memory available to
    # every node via LangGraph's runtime, in addition to the per-thread
    # `checkpointer=`.
    return graph_builder.compile(checkpointer=checkpointer, store=store)


# --------------------------------------------------------------------------
# 6. Service wrapper
# --------------------------------------------------------------------------
class LongTermMemoryService:
    def __init__(self, checkpoint_db: str = "conversations.sqlite"):
        self.graph = build_graph(checkpoint_db)

    def send(self, thread_id: str, user_id: str, user_message: str) -> str:
        config = {"configurable": {"thread_id": thread_id}}
        result = self.graph.invoke(
            {
                "messages": [HumanMessage(content=user_message)],
                "user_id": user_id,
                "profile_summary": "",
            },
            config=config,
        )
        return result["messages"][-1].content


# --------------------------------------------------------------------------
# 7. Demo: two DIFFERENT threads for the SAME user, days apart, showing
#    facts learned in thread A are available in thread B.
# --------------------------------------------------------------------------
if __name__ == "__main__":
    service = LongTermMemoryService()

    print("--- Monday: thread_A, user u_1 ---")
    print("User:", "I'm vegetarian, please don't recommend meat dishes.")
    print("Bot: ", service.send("thread_A_monday", "u_1", "I'm vegetarian, please don't recommend meat dishes."))

    print("\n--- Monday: thread_A, user u_1 (continued) ---")
    print("User:", "Also I'm allergic to peanuts.")
    print("Bot: ", service.send("thread_A_monday", "u_1", "Also I'm allergic to peanuts."))

    print("\n--- Thursday: BRAND NEW thread_B, same user u_1 ---")
    print("User:", "What should I cook tonight?")
    print("Bot: ", service.send("thread_B_thursday", "u_1", "What should I cook tonight?"))
    print("(^ note: no meat/peanuts, without being reminded -- pulled from long-term profile)")
```

### Notes on production-readiness in this code

- **Two independent persistence layers, on purpose.** `checkpointer=` (thread-scoped, from Pattern 1) and `store=` (user-scoped, cross-thread) are compiled into the *same* graph but serve different lifetimes. This mirrors how real systems separate "this conversation's transcript" from "what we know about this user forever."
- **Explicit extraction, not blind storage.** `extract_memory_node` uses a constrained JSON-only prompt to decide what's durable — this avoids silently promoting one-off comments ("I'm in a rush today") into permanent facts, which would degrade personalization quality over time.
- **Safety-relevant facts (allergies) are always fully re-injected**, not subject to Short-Term Memory's windowing — critical, because an allergy fact getting silently trimmed out of context in Pattern 2's style would be a genuine safety bug in a food app.
- **Swappable store.** `InMemoryStore()` is used here for a runnable demo; in production this is swapped for a persistent, multi-instance-safe store (e.g., a Postgres- or Redis-backed `BaseStore` implementation) so facts survive process restarts and are visible across all app server replicas.
- **Merge, don't overwrite**, when writing new facts (`{**existing, **new_facts}`) — so a new session's extraction doesn't wipe out facts learned in a previous one.

---

## 8. Version Pins (latest stable, compatible, at time of writing)

```
python            >= 3.11
langchain-core    >= 0.3.0
langchain-ollama  >= 0.2.0
langgraph         >= 0.2.0
langgraph-checkpoint-sqlite >= 2.0.0
```

```bash
pip install -U "langchain-core>=0.3.0" "langchain-ollama>=0.2.0" "langgraph>=0.2.0" "langgraph-checkpoint-sqlite>=2.0.0"
```

> `langgraph.store.memory.InMemoryStore` ships as part of `langgraph` itself — no extra package needed for the demo. For production, look at persistent `BaseStore` implementations (e.g. Postgres-backed) so long-term facts survive restarts and are shared across multiple app instances.

---

**Next up → Pattern 4: Semantic Memory** (storing and retrieving *general knowledge/facts* by meaning via embeddings — e.g., "what does this user usually order" retrieved by similarity rather than exact key lookup).
