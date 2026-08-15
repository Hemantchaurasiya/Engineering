# Pattern 2: Short-Term Memory

> Part of the **Memory Patterns** series (Section 6). Stack: `langchain-ollama` (ChatOllama + OllamaEmbeddings), `langgraph`, local models (`llama3.1`, `nomic-embed-text`).

---

## 1. Introduce the Pattern

**Short-Term Memory** is a *bounded* window of the most recent conversation turns that gets sent to the LLM on every call — instead of the entire, ever-growing history.

Pattern 1 (Conversation Memory) stores and replays the **full** message history forever. That's correct for durability, but it has a hidden problem: the amount of text sent to the model grows without limit as a conversation goes on. Short-Term Memory fixes this by drawing a line: **keep everything in durable storage, but only feed the model the last N messages (or last K tokens) of "working context."**

Think of it like human short-term memory: you can hold roughly the last few things said in a conversation clearly in your head, even if you "logged" the whole meeting in your notes. The notes (full history) still exist — you just don't re-read all of them before every sentence you speak.

---

## 2. Problem It Solves

If you feed the *entire* conversation history into the model on every turn (as raw Conversation Memory does), three production problems appear as the conversation grows:

1. **Context window overflow.** Every model has a hard token limit (e.g., 128K for `llama3.1`). A long-running session — a multi-hour debugging chat, a long customer support thread — will eventually exceed it, and the request will simply fail.
2. **Runaway latency and cost.** Even well before the hard limit, prompt-processing time grows roughly linearly (or worse) with context length. A 50,000-token prompt is dramatically slower to process than a 2,000-token one — unacceptable for a chat UI where users expect sub-second-to-a-few-seconds responses.
3. **Signal dilution ("lost in the middle").** LLMs are empirically worse at using information buried in the middle of a very long prompt. Feeding 300 old, irrelevant turns can actually make the model's answer to the *current* question worse, not better.

Short-Term Memory solves all three by capping what's sent to the model, while a separate durable store (Pattern 1's checkpointer) keeps the complete, un-truncated record for audit/analytics/later retrieval.

---

## 3. Realistic Production/Enterprise Scenario

**Scenario: "DevCopilot" — an internal engineering chat assistant used during long, multi-hour debugging sessions**

- Engineers keep a single chat thread open for an entire incident or debugging session — sometimes 3-4 hours, hundreds of turns, including pasted stack traces and logs.
- The full transcript **must** be kept (compliance + post-incident review), so nothing can ever be silently deleted from storage.
- But `llama3.1`'s context window, while large, is not infinite, and prompt-processing latency must stay low so the tool feels responsive during a live incident — engineers won't tolerate a 20-second wait because the prompt now contains 4 hours of chat.
- The assistant mainly needs the **recent** exchanges to stay useful ("what's the last error we saw?", "did that last fix work?") — it rarely needs message #3 from three hours ago verbatim.

This is exactly the tension Short-Term Memory resolves: **durable full history + a small, fast, recent-context window actually sent to the LLM.**

---

## 4. Architecture / Flow Diagram

```
                    ┌───────────────────────────────────────────┐
                    │                Client (Chat UI)              │
                    │   sends: {thread_id, user_message}           │
                    └───────────────────┬───────────────────────────┘
                                         │
                                         ▼
                    ┌───────────────────────────────────────────┐
                    │              LangGraph StateGraph             │
                    │                                                │
                    │  ┌───────────────┐                             │
                    │  │  ingest_node   │  appends new HumanMessage   │
                    │  │                │  to FULL state history      │
                    │  └───────┬───────┘                             │
                    │          │                                      │
                    │          ▼                                      │
                    │  ┌───────────────────┐   trims to last N msgs   │
                    │  │  window_node        │   or last K tokens      │
                    │  │ (Short-Term Memory)  │──────────────┐         │
                    │  └───────────────────┘               │         │
                    │                                        ▼         │
                    │                              ┌────────────────┐  │
                    │                              │   ChatOllama    │  │
                    │                              │   (llama3.1)    │  │
                    │                              └────────┬───────┘  │
                    │                                        │          │
                    │                                        ▼          │
                    │                          AI reply appended to FULL│
                    │                          state history (not just  │
                    │                          the trimmed window)      │
                    └───────────────────┬───────────────────────────────┘
                                         │
                                         ▼
                    ┌───────────────────────────────────────────┐
                    │        conversations.sqlite (disk)            │
                    │  FULL, untruncated history — every turn ever   │
                    │  said, for thread_id="incident_501"            │
                    └───────────────────────────────────────────┘
```

**Key idea:** the checkpointer (from Pattern 1) still stores the **complete** history. Short-Term Memory only changes *what subset of that history is actually sent to the model* on a given call — via a trimming step that runs right before the LLM node.

---

## 5. Complete Request-to-Response Flow

For `thread_id = "incident_501"`, deep into a 3-hour debugging session (say, 220 stored messages):

1. **Client sends** the 221st user message: `"Did restarting the pod fix it?"`
2. **`ingest_node`** appends this new `HumanMessage` to the *full* state (now 221 messages), which the checkpointer will eventually persist in full.
3. **`window_node`** runs `trim_messages(...)` against those 221 messages, configured with e.g. `max_tokens=4000, strategy="last"`. This walks backward from the newest message, keeping whole messages until the token budget is hit — producing, say, the last 14 messages (~4,000 tokens), always keeping the system prompt.
4. **Only those ~14 messages** are passed to `ChatOllama.invoke(...)` — not all 221.
5. **llama3.1** generates a reply grounded in recent context (it can see the last error, the last fix attempted, and the question) in a fraction of the time a 221-message prompt would take.
6. **The AI reply is appended to the *full* state** (now 222 messages) and the checkpointer persists all 222 messages to disk — nothing is lost from the durable record, even though the model only "saw" the last 14.
7. **Next turn**: the same trimming happens again, now over 222 messages, again producing a small recent window.

If someone opens the incident's full transcript later (for a postmortem), they see all 222 messages — Short-Term Memory only affected what the *model* saw per-call, never what was *stored*.

---

## 6. Why This Pattern Is Appropriate

| Requirement | How Short-Term Memory satisfies it |
|---|---|
| Stay under the model's context window | Token-budgeted trimming guarantees an upper bound on prompt size |
| Keep latency predictable as sessions grow | Prompt size stays roughly constant regardless of conversation length |
| Preserve full audit trail | Trimming happens only on the *outbound* path to the LLM; storage is untouched |
| Keep the model focused on what's relevant *right now* | Recent-window trimming avoids "lost in the middle" dilution from stale turns |

**Trade-offs / when it's not enough:**
- Short-Term Memory has **no concept of importance** — it drops the *oldest* messages first, even if message #12 contained a critical fact ("the root cause is a memory leak in service X") that's still relevant at message #200. That's exactly the gap **Summary Memory** (Pattern 8) and **Memory Consolidation** (Pattern 14) fill — they compress old context into a persistent summary instead of just dropping it.
- If specific facts must always be recoverable regardless of recency (e.g., "the user's account ID"), that's a job for **Entity Memory** (Pattern 7) or **Long-Term Memory** (Pattern 3), not Short-Term Memory.
- Short-Term Memory is best understood as the **cheapest, simplest fix** for context-window/latency problems — a sensible default, but not a substitute for the smarter compression patterns later in this series.

---

## 7. Production-Quality Python Implementation

### Requirements

```bash
pip install -U langchain-ollama langgraph langchain-core
# Ollama model used locally:
#   ollama pull llama3.1
```

### `short_term_memory.py`

```python
"""
Pattern 2: Short-Term Memory
Bounded, token-budgeted "working context" on top of a fully durable
conversation history, built on LangGraph + langchain-ollama.

Run:
    python short_term_memory.py
"""

from __future__ import annotations

import logging
import sqlite3
from typing import Annotated, TypedDict

from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    SystemMessage,
    trim_messages,
)
from langchain_ollama import ChatOllama
from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("short_term_memory")


# --------------------------------------------------------------------------
# 1. Graph state -- identical shape to Pattern 1. The FULL history always
#    lives here and is always what gets persisted by the checkpointer.
# --------------------------------------------------------------------------
class ConversationState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]


SYSTEM_PROMPT = SystemMessage(
    content=(
        "You are DevCopilot, an engineering assistant helping during a live "
        "debugging/incident session. Be concise and technical. Use recent "
        "conversation context to track what has already been tried."
    )
)


def build_llm(model: str = "llama3.1", temperature: float = 0.2) -> ChatOllama:
    return ChatOllama(model=model, temperature=temperature, keep_alive="10m")


# --------------------------------------------------------------------------
# 2. The Short-Term Memory step: trim the full history down to a token
#    budget BEFORE it ever reaches the LLM. This never mutates state --
#    it only shapes what gets sent to `llm.invoke(...)`.
# --------------------------------------------------------------------------
def build_trimmer(llm: ChatOllama, max_tokens: int = 4000):
    """
    Returns a callable: full_history -> trimmed_history

    strategy="last": keep the most recent messages first, drop oldest.
    Always keeps the system message (start_on/include_system semantics
    below), and never splits a single message in half.
    """

    def trim(history: list[BaseMessage]) -> list[BaseMessage]:
        # Separate system prompt so it's never at risk of being trimmed away.
        non_system = [m for m in history if not isinstance(m, SystemMessage)]

        trimmed = trim_messages(
            non_system,
            token_counter=llm,          # uses the model's own tokenizer/estimator
            max_tokens=max_tokens,
            strategy="last",            # keep newest, drop oldest
            start_on="human",           # avoid starting the window mid-AI-turn
            include_system=False,       # we add our own system message below
            allow_partial=False,        # never truncate a message mid-way
        )

        dropped = len(non_system) - len(trimmed)
        if dropped > 0:
            logger.info(
                "Short-term window: dropped %d older message(s), kept %d "
                "recent message(s) (~%d token budget)",
                dropped, len(trimmed), max_tokens,
            )

        return [SYSTEM_PROMPT, *trimmed]

    return trim


# --------------------------------------------------------------------------
# 3. Nodes
# --------------------------------------------------------------------------
def make_chat_node(llm: ChatOllama, trimmer):
    def chat_node(state: ConversationState) -> ConversationState:
        full_history = state["messages"]          # everything ever said
        working_context = trimmer(full_history)     # short-term window only

        logger.info(
            "Full stored history: %d messages | Sent to LLM: %d messages",
            len(full_history), len(working_context),
        )

        try:
            response: AIMessage = llm.invoke(working_context)
        except Exception:
            logger.exception("LLM call failed")
            response = AIMessage(
                content="I hit an error reaching the model. Please retry."
            )

        # We still append the reply to the FULL state -- durability is
        # unaffected by short-term trimming.
        return {"messages": [response]}

    return chat_node


# --------------------------------------------------------------------------
# 4. Assemble the graph
# --------------------------------------------------------------------------
def build_graph(db_path: str = "conversations.sqlite", max_tokens: int = 4000):
    llm = build_llm()
    trimmer = build_trimmer(llm, max_tokens=max_tokens)

    graph_builder = StateGraph(ConversationState)
    graph_builder.add_node("chat_node", make_chat_node(llm, trimmer))
    graph_builder.add_edge(START, "chat_node")
    graph_builder.add_edge("chat_node", END)

    conn = sqlite3.connect(db_path, check_same_thread=False)
    checkpointer = SqliteSaver(conn)

    return graph_builder.compile(checkpointer=checkpointer)


# --------------------------------------------------------------------------
# 5. Service wrapper
# --------------------------------------------------------------------------
class ShortTermMemoryService:
    def __init__(self, db_path: str = "conversations.sqlite", window_tokens: int = 4000):
        self.graph = build_graph(db_path, max_tokens=window_tokens)

    def send(self, thread_id: str, user_message: str) -> str:
        config = {"configurable": {"thread_id": thread_id}}
        result = self.graph.invoke(
            {"messages": [HumanMessage(content=user_message)]},
            config=config,
        )
        return result["messages"][-1].content

    def full_history_length(self, thread_id: str) -> int:
        """Proves the durable record keeps growing even though the model
        only ever sees a bounded recent window."""
        config = {"configurable": {"thread_id": thread_id}}
        state = self.graph.get_state(config)
        return len(state.values.get("messages", [])) if state else 0


# --------------------------------------------------------------------------
# 6. Demo: simulate a long incident session to show windowing kick in
# --------------------------------------------------------------------------
if __name__ == "__main__":
    # Small token budget on purpose, so the demo triggers trimming quickly.
    service = ShortTermMemoryService(db_path="conversations.sqlite", window_tokens=300)
    thread_id = "incident_501"

    scripted_turns = [
        "The checkout service is returning 500s since 2pm.",
        "Logs show a NullPointerException in PaymentValidator.",
        "We rolled back the last deploy, still failing.",
        "CPU and memory both look normal on the pods.",
        "Restarted the pods, error rate dropped but didn't go to zero.",
        "Now seeing intermittent timeouts calling the fraud-check service.",
        "Fraud-check service dashboard shows elevated p99 latency.",
        "Did restarting the pod fix it?",
    ]

    for turn in scripted_turns:
        reply = service.send(thread_id, turn)
        print(f"\nUser: {turn}\nBot:  {reply}")

    print(
        f"\n--- Full durable history for '{thread_id}': "
        f"{service.full_history_length(thread_id)} messages stored "
        f"(model only ever saw a small recent window per call) ---"
    )
```

### Notes on production-readiness in this code

- **Durability is never compromised.** Trimming happens only in `build_trimmer`, right before the `llm.invoke(...)` call — the checkpointer keeps appending to (and persisting) the *full* state every turn, exactly as in Pattern 1.
- **Token-based, not just count-based, trimming.** `trim_messages(..., token_counter=llm)` budgets by actual tokens (using the model's tokenizer/estimator), which is far more reliable than a fixed "last 10 messages" rule — a support paste of a huge stack trace can blow a fixed-count window instantly, but a token budget handles it correctly.
- **`start_on="human"` / `allow_partial=False`** avoid two common bugs: starting the window on a dangling AI message with no matching human turn, and truncating a message in the middle (which can corrupt structured content like tool calls).
- **System prompt is re-added after trimming**, not trimmed away — the assistant's core instructions must never silently disappear as the conversation grows.
- **Observability**: logs both the full stored length and the trimmed length sent to the model every turn, so you can monitor window behavior in production (e.g., alert if trimming almost never triggers — a sign your budget may be too generous for your traffic/cost targets).

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

> `trim_messages` lives in `langchain_core.messages` and works with any chat model that exposes a token counter (`ChatOllama` does). No extra dependency needed beyond what Pattern 1 already installs.

---

**Next up → Pattern 3: Long-Term Memory** (persisting facts *across* sessions/threads — not just within one — e.g., remembering a user's preferences the next time they start a brand-new conversation days later).
