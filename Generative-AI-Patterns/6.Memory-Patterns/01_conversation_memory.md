# Pattern 1: Conversation Memory

> Part of the **Memory Patterns** series (Section 6). Stack: `langchain-ollama` (ChatOllama + OllamaEmbeddings), `langgraph`, local models (`llama3.1`, `nomic-embed-text`).

---

## 1. Introduce the Pattern

**Conversation Memory** is the ability of an LLM-powered system to remember what was said earlier *in the same conversation* and use that context to respond to the current message.

By default, an LLM call is **stateless** — every call to `ChatOllama.invoke(...)` is a fresh request with zero knowledge of anything said before. If you don't explicitly send the previous messages back to the model, it has no idea a previous message ever existed.

Conversation Memory simply means: **store the running list of messages (human + AI) for a session, and replay that list back to the model on every new turn**, so the conversation feels continuous — like talking to a person who remembers what you just said, not a stranger who forgets you every 10 seconds.

This is the *foundation* memory pattern — almost every other pattern in this series (Short-Term, Long-Term, Summary, Entity, etc.) is really a smarter, more scalable variant of "remembering the conversation."

---

## 2. Problem It Solves

Without conversation memory:

```
User: My order number is ORD-88213, it hasn't arrived.
Bot:  I'm sorry to hear that. Could you share your order number?
User: I just gave it to you! ORD-88213.
Bot:  Could you please provide your order number?
```

The bot re-asks for information already given, because each call to the LLM starts from zero. This is unusable for any real conversational product — support bots, copilots, tutors, voice assistants — where multi-turn context is the entire point.

Conversation Memory solves this by:
- Persisting the message history per **session/thread**
- Feeding that history back into every model call
- Letting the model resolve references like "it", "that order", "the one I mentioned earlier"
- Isolating one user's conversation from another's (multi-tenant safety)

---

## 3. Realistic Production/Enterprise Scenario

**Scenario: "OrderTrack" — Customer Support Chatbot for an E-commerce Company**

- Thousands of concurrent users chat with a support bot about order status, returns, and refunds.
- Each user's conversation must be **remembered for the duration of their session** (a browser tab / chat widget instance) but must **not leak** into another user's conversation.
- Conversations can be picked back up (e.g., user refreshes the page, or comes back after 10 minutes) — so memory needs to survive beyond a single Python process's memory, i.e., it needs a **persistence layer** (not just a Python list in RAM).
- Support agents can also *view a transcript* of a resolved conversation later — so the message history must be durable and retrievable, not just "kept alive" in memory.

This is a perfect real-world driver for Conversation Memory because:
1. Multiple concurrent, isolated sessions (multi-tenant) → need a **thread/session ID**.
2. Conversations must survive process restarts → need a **persistent checkpointer**, not just an in-memory Python dict.
3. Conversations grow long → later patterns (Short-Term Memory, Summary Memory) build directly on top of this to control cost/context length. Conversation Memory is the *raw substrate* they all optimize.

---

## 4. Architecture / Flow Diagram

```
                         ┌─────────────────────────────────────────┐
                         │              Client (Chat UI)            │
                         │   sends: {thread_id, user_message}       │
                         └───────────────────┬───────────────────────┘
                                              │
                                              ▼
                         ┌─────────────────────────────────────────┐
                         │            FastAPI / App Layer            │
                         │  Resolves session -> thread_id            │
                         └───────────────────┬───────────────────────┘
                                              │
                                              ▼
                     ┌────────────────────────────────────────────────┐
                     │              LangGraph StateGraph                │
                     │                                                  │
                     │   ┌──────────────┐        ┌───────────────────┐ │
                     │   │  chat_node    │──────▶│   ChatOllama       │ │
                     │   │ (adds message,│        │  (llama3.1)        │ │
                     │   │  calls LLM)   │◀──────│                    │ │
                     │   └──────┬────────┘        └───────────────────┘ │
                     │          │                                        │
                     │          ▼                                        │
                     │   ┌──────────────────────────────┐                │
                     │   │   Checkpointer (SqliteSaver)  │                │
                     │   │  keyed by thread_id            │                │
                     │   │  stores full message history   │                │
                     │   └──────────────────────────────┘                │
                     └────────────────────────────────────────────────┘
                                              │
                                              ▼
                         ┌─────────────────────────────────────────┐
                         │        conversations.sqlite (disk)        │
                         │  thread_id="user_42" -> [HumanMsg, AIMsg, │
                         │                          HumanMsg, ...]   │
                         └─────────────────────────────────────────┘
```

**Key idea:** LangGraph's `StateGraph` + a **checkpointer** (e.g. `SqliteSaver`) IS the production-grade way to implement conversation memory today. You don't hand-roll a Python list — the checkpointer persists the full state (message list) per `thread_id`, across restarts, automatically.

---

## 5. Complete Request-to-Response Flow

Walking through **one turn** of the OrderTrack bot for `thread_id = "user_42"`:

1. **Client sends** `{"thread_id": "user_42", "message": "My order ORD-88213 hasn't arrived."}`
2. **App layer** builds a LangGraph `config = {"configurable": {"thread_id": "user_42"}}`.
3. **Graph invocation**: `graph.invoke({"messages": [HumanMessage(...)]}, config)`
4. **LangGraph checkpointer** looks up `thread_id="user_42"` in `conversations.sqlite`. If a prior state exists, it **loads the full past message list** and merges the new `HumanMessage` onto it (via the `add_messages` reducer).
5. **`chat_node`** runs: it takes the *full* message list (past + new) and calls `ChatOllama.invoke(messages)`.
6. **llama3.1** generates a response, grounded in the entire conversation so far (so it already "knows" the order number if it was mentioned earlier).
7. **The AI response is appended** to the state's message list via the reducer.
8. **Checkpointer writes** the updated full message list back to `conversations.sqlite` under `thread_id="user_42"`.
9. **App layer returns** the AI message to the client.
10. **Next turn**: user says "Any update?" → the *same* `thread_id` is used → step 4 reloads history → the model resolves "any update" as referring to `ORD-88213` without being told again.

If the process crashes or restarts between turns, step 4 still works correctly because the history lives in SQLite, not in RAM.

---

## 6. Why This Pattern Is Appropriate

| Requirement | How Conversation Memory satisfies it |
|---|---|
| Multi-turn coherence | Full message history is replayed to the LLM every turn |
| Multi-tenant isolation | Each `thread_id` has its own independent history |
| Durability across restarts | Checkpointer persists to disk (SQLite/Postgres/Redis) |
| Auditability (support transcripts) | Full history is queryable straight from the checkpoint store |
| Simplicity | No manual summarization/pruning needed for short-lived sessions |

**When it's *not* enough (segues into later patterns):**
- Conversations that run for hours/days will eventually **exceed the model's context window** → you'll need **Short-Term Memory** (windowing) or **Summary Memory** (compression).
- Facts that should be remembered **across different sessions** (e.g., "the user's shipping address") need **Long-Term Memory** / **Entity Memory**, not just per-thread Conversation Memory.
- Conversation Memory is intentionally "dumb": it stores everything verbatim, in order. That's exactly why it's the right *first* pattern — everything else in this series is a refinement of this baseline.

---

## 7. Production-Quality Python Implementation

### Requirements

```bash
pip install -U langchain-ollama langgraph langchain-core
# Ollama models used locally:
#   ollama pull llama3.1
```

### `conversation_memory.py`

```python
"""
Pattern 1: Conversation Memory
Production-grade multi-turn chat with durable, per-thread memory,
built on LangGraph + langchain-ollama.

Run:
    python conversation_memory.py
"""

from __future__ import annotations

import logging
import sqlite3
from typing import Annotated, TypedDict

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage
from langchain_ollama import ChatOllama
from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages

# --------------------------------------------------------------------------
# Logging (production systems should never rely on print statements)
# --------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("conversation_memory")


# --------------------------------------------------------------------------
# 1. Define the graph state
# --------------------------------------------------------------------------
class ConversationState(TypedDict):
    """
    `messages` uses the `add_messages` reducer, which means LangGraph
    will APPEND new messages to the existing list rather than overwrite it.
    This is what makes memory "accumulate" across turns automatically.
    """
    messages: Annotated[list[BaseMessage], add_messages]


# --------------------------------------------------------------------------
# 2. Build the LLM
# --------------------------------------------------------------------------
def build_llm(model: str = "llama3.1", temperature: float = 0.3) -> ChatOllama:
    return ChatOllama(
        model=model,
        temperature=temperature,
        # keep_alive keeps the model warm in Ollama between requests,
        # which matters a lot for latency in a production chat service.
        keep_alive="10m",
    )


SYSTEM_PROMPT = SystemMessage(
    content=(
        "You are OrderTrack, a helpful and concise e-commerce support assistant. "
        "Use the full conversation history to avoid asking the user to repeat "
        "information they've already given you (like order numbers). "
        "If you don't know an order's real status, say you'll escalate it — "
        "never invent a status."
    )
)


# --------------------------------------------------------------------------
# 3. Define the node(s) of the graph
# --------------------------------------------------------------------------
def make_chat_node(llm: ChatOllama):
    def chat_node(state: ConversationState) -> ConversationState:
        history = state["messages"]

        # Ensure the system prompt is always present exactly once, at the front.
        if not history or not isinstance(history[0], SystemMessage):
            model_input = [SYSTEM_PROMPT, *history]
        else:
            model_input = history

        logger.info("Calling llama3.1 with %d messages of context", len(model_input))

        try:
            response: AIMessage = llm.invoke(model_input)
        except Exception:
            logger.exception("LLM call failed")
            response = AIMessage(
                content=(
                    "I'm having trouble reaching my systems right now. "
                    "Please try again in a moment."
                )
            )

        # We only need to return the NEW message(s); the `add_messages`
        # reducer takes care of appending them to the persisted state.
        return {"messages": [response]}

    return chat_node


# --------------------------------------------------------------------------
# 4. Assemble the graph with a durable checkpointer
# --------------------------------------------------------------------------
def build_graph(db_path: str = "conversations.sqlite"):
    llm = build_llm()

    graph_builder = StateGraph(ConversationState)
    graph_builder.add_node("chat_node", make_chat_node(llm))
    graph_builder.add_edge(START, "chat_node")
    graph_builder.add_edge("chat_node", END)

    # SqliteSaver persists the full message history to disk, keyed by thread_id.
    # This survives process restarts -- critical for a real chat service.
    conn = sqlite3.connect(db_path, check_same_thread=False)
    checkpointer = SqliteSaver(conn)

    graph = graph_builder.compile(checkpointer=checkpointer)
    return graph


# --------------------------------------------------------------------------
# 5. A tiny service-style wrapper (what an API layer would call)
# --------------------------------------------------------------------------
class ConversationService:
    """
    Thin wrapper simulating what a FastAPI endpoint would do:
    take (thread_id, user_message) -> return AI reply, with full
    durability and per-user isolation handled by the checkpointer.
    """

    def __init__(self, db_path: str = "conversations.sqlite"):
        self.graph = build_graph(db_path)

    def send(self, thread_id: str, user_message: str) -> str:
        config = {"configurable": {"thread_id": thread_id}}
        result = self.graph.invoke(
            {"messages": [HumanMessage(content=user_message)]},
            config=config,
        )
        ai_message = result["messages"][-1]
        return ai_message.content

    def get_transcript(self, thread_id: str) -> list[BaseMessage]:
        """For support-agent review: fetch the full durable history."""
        config = {"configurable": {"thread_id": thread_id}}
        state = self.graph.get_state(config)
        return state.values.get("messages", []) if state else []


# --------------------------------------------------------------------------
# 6. Demo: two isolated users, one of them resuming later
# --------------------------------------------------------------------------
if __name__ == "__main__":
    service = ConversationService(db_path="conversations.sqlite")

    print("\n--- User 42, turn 1 ---")
    reply = service.send("user_42", "My order ORD-88213 hasn't arrived yet.")
    print("Bot:", reply)

    print("\n--- User 99 (different thread, no context leak), turn 1 ---")
    reply = service.send("user_99", "Hi, I want to return a jacket I bought.")
    print("Bot:", reply)

    print("\n--- User 42, turn 2 (simulates coming back later; process could restart here) ---")
    reply = service.send("user_42", "Any update on it?")
    print("Bot:", reply)

    print("\n--- Support agent pulls user_42's full transcript ---")
    for msg in service.get_transcript("user_42"):
        role = msg.type
        print(f"[{role}] {msg.content}")
```

### Notes on production-readiness in this code

- **Durable checkpointer (`SqliteSaver`)** instead of an in-memory dict — survives restarts, which is a hard requirement for any real chat backend. Swap `SqliteSaver` for `PostgresSaver` / `RedisSaver` in a real multi-instance deployment (Sqlite is fine for a single-node service or local dev).
- **`thread_id` isolation** — every call is scoped by `config["configurable"]["thread_id"]`, so User 42 and User 99 never see each other's history.
- **Error handling around the LLM call** — a production support bot must never hard-crash the whole request just because Ollama hiccups.
- **Structured logging** instead of print statements, so this is observable in production.
- **System prompt injected exactly once**, not duplicated every turn (which would waste context and confuse the model over long conversations).
- **`keep_alive`** on `ChatOllama` avoids reload latency on every request — important under real traffic.

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

> Ollama models required locally: `ollama pull llama3.1` (generation). `nomic-embed-text` is not needed for this particular pattern but is pulled ahead for later patterns (Semantic/Vector-Based Memory) that reuse this same stack.

---

**Next up → Pattern 2: Short-Term Memory** (bounding context length via sliding windows / token-limited trimming, so Conversation Memory doesn't blow past the model's context window in long-running sessions).
