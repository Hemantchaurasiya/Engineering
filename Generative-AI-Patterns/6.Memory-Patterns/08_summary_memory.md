# Pattern 8: Summary Memory

> Part of the **Memory Patterns** series (Section 6). Stack: `langchain-ollama` (ChatOllama + OllamaEmbeddings), `langgraph`, local models (`llama3.1`, `nomic-embed-text`).

---

## 1. Introduce the Pattern

**Summary Memory** keeps a long conversation manageable by periodically **compressing older turns into a running natural-language summary**, instead of simply dropping them (Pattern 2's windowing) or keeping every word forever (Pattern 1's raw history).

The model always sees: **[running summary of everything older] + [the most recent messages, verbatim]**. As the conversation grows, old verbatim messages get folded into an updated summary and removed from the "recent" window — so context length stays bounded, but *unlike blunt trimming, no information is silently thrown away*. It's compressed, not deleted.

This directly addresses the weakness flagged at the end of Pattern 2: windowing drops the *oldest* messages first no matter how important they were. Summary Memory instead asks: *"before we forget these old messages, let's fold whatever mattered in them into a summary we keep forever."*

---

## 2. Problem It Solves

Pattern 2 (Short-Term Memory) solves the context-window/latency problem but at a cost — it has no concept of importance:

```
Message #3 (dropped by windowing): "By the way, the root cause turned out to
                                     be a stale DNS cache on the client side."
Message #200 (current):            "Wait, why did we conclude it was DNS again?"
Bot:                                "I don't have that in my current context."
```

A critical fact from early in the conversation is gone the moment it falls outside the window, even though it may matter for the rest of the conversation. For long-running, narratively important conversations — a multi-week project, an extended coaching relationship, a long troubleshooting session — this is a real usability problem: the assistant "forgets" things a human participant never would have.

Summary Memory solves this by:
- **Never discarding information outright** — old messages are compressed into an evolving summary before being dropped from the raw window
- **Keeping context length bounded** — the summary itself stays short (a paragraph or two) even as the underlying conversation grows into the hundreds of turns, because each re-summarization pass condenses the *previous* summary + newly-aged-out messages together, not the whole history from scratch
- **Preserving narrative coherence** — a well-written summary reads like "here's what's happened so far," which is often *more* useful to the model than a pile of raw old messages would be, not just a fallback

---

## 3. Realistic Production/Enterprise Scenario

**Scenario: "ProjectPilot" — a project management assistant used throughout a multi-month software project**

- A team keeps a single long-running thread with ProjectPilot across an entire project: initial scoping, requirement changes, decisions made, blockers resolved, scope cuts — sometimes hundreds of exchanges over months.
- Team members ask retrospective questions at any point: *"Remind me why we cut the reporting feature,"* *"What was the original launch date and why did it move?"* — questions that can reference decisions made *very* early in the project.
- The raw transcript is far too long to send to the model every turn (Pattern 2's problem), but blunt windowing (Pattern 2's fix) would silently forget the early decisions that retrospective questions specifically ask about.
- The team wants the assistant to behave like a **project historian**: always able to give a coherent account of "what happened and why," without needing to re-read the entire raw history every time.

This is precisely what Summary Memory is for: **preserve the gist of everything, keep the detail of only what's recent**, exactly how a competent human project manager keeps notes rather than trying to remember every word ever said in every meeting.

---

## 4. Architecture / Flow Diagram

```
                    ┌───────────────────────────────────────────┐
                    │   Client: "Remind me why we cut the           │
                    │   reporting feature?"                          │
                    └───────────────────┬───────────────────────────┘
                                          │
                                          ▼
                    ┌───────────────────────────────────────────┐
                    │              LangGraph StateGraph             │
                    │                                                │
                    │  ┌─────────────────────┐                      │
                    │  │  chat_node             │───▶ ChatOllama       │
                    │  │  input = running_summary │    (llama3.1)       │
                    │  │  + recent messages (raw)  │                      │
                    │  └──────────┬─────────────┘                      │
                    │             │                                     │
                    │             ▼                                     │
                    │  ┌─────────────────────┐                        │
                    │  │  check_length_node     │  recent messages         │
                    │  │                        │  exceed threshold?       │
                    │  └──────────┬─────────────┘                        │
                    │      too long?  ──────────────┐                     │
                    │             │ yes               │ no                  │
                    │             ▼                   ▼                     │
                    │  ┌─────────────────────┐   (do nothing, END)         │
                    │  │  summarize_node        │                             │
                    │  │  -> LLM folds oldest    │                             │
                    │  │  messages + prior        │                             │
                    │  │  summary into a NEW,     │                             │
                    │  │  updated summary          │                             │
                    │  │  -> those old messages    │                             │
                    │  │  are removed from the     │                             │
                    │  │  raw "recent" window       │                             │
                    │  └──────────┬─────────────┘                             │
                    └───────────────┼───────────────────────────────────────┘
                                      ▼
                    ┌───────────────────────────────────────────┐
                    │        conversations.sqlite (checkpointer)    │
                    │  state.running_summary = "Project started      │
                    │  with 4 features scoped. Reporting was cut       │
                    │  in week 3 due to timeline pressure after the     │
                    │  client moved launch up by a month. ..."           │
                    │  state.messages = [last ~10 raw messages only]      │
                    └───────────────────────────────────────────┘
```

**Key idea:** summarization is **incremental**, not "re-summarize everything from scratch each time" — each pass takes the *existing* summary plus the newly-aged-out raw messages and produces one updated summary, so the summarization LLM call itself stays cheap and bounded regardless of how many months the project has been running.

---

## 5. Complete Request-to-Response Flow

For a project thread that has just crossed the "recent messages" threshold (say, 16 raw messages, threshold is 12):

1. **User sends** a new message, pushing the raw window to 17 messages.
2. **`chat_node`** runs first, using `[running_summary so far] + [all 17 raw messages]` as context, and answers normally.
3. **`check_length_node`** inspects the *updated* state and sees 17 raw messages > threshold (12).
4. **`summarize_node`** fires: it takes the **oldest 8 messages** (keeping the most recent 9 untouched) plus the **existing running summary**, and calls `ChatOllama` with a summarization prompt: *"Here's what we knew so far: [old summary]. Here are new messages to fold in: [oldest 8]. Produce an updated, still-concise summary."*
5. The LLM returns a new, slightly longer (but still bounded — a paragraph or two) summary that now includes whatever mattered in those 8 messages (e.g., "reporting feature was cut in week 3 due to timeline pressure").
6. **State is updated**: `running_summary` is replaced with the new version; those 8 oldest raw messages are removed from `messages` (only the most recent 9 remain raw).
7. **Next turn**, the cycle repeats — the raw window never grows past ~20 messages before compressing again, but `running_summary` accumulates the gist of the *entire* project's history.
8. **Weeks later**, "Remind me why we cut the reporting feature?" is answered correctly because that fact now lives permanently in `running_summary`, regardless of how many raw messages have come and gone since.

---

## 6. Why This Pattern Is Appropriate

| Requirement | How Summary Memory satisfies it |
|---|---|
| Bounded context length over arbitrarily long conversations | Raw window stays small; only the summary (also bounded) grows slowly |
| No silent loss of important early information | Every aged-out message is folded into the summary before removal |
| Coherent "story so far" for retrospective questions | The summary itself reads as a narrative, not a fact dump |
| Reasonable summarization cost | Incremental folding (old summary + new messages), not full re-summarization each time |

**Trade-offs / when it's not enough:**
- Summarization is **lossy by nature** — a good summary preserves the *gist*, not every verbatim detail (an exact dollar figure or exact quote from message #4 might not survive many rounds of re-summarization faithfully). For facts that must be preserved with 100% fidelity indefinitely, pair Summary Memory with **Entity Memory** (Pattern 7) or **Long-Term Memory** (Pattern 3) for the specific durable facts, and let Summary Memory handle the narrative connective tissue.
- The summarization step itself costs an LLM call — for very high-throughput systems, this needs to be batched/async so it doesn't add latency to the user-facing turn (the implementation below runs it inline for simplicity, but production systems often run it as a background step after responding).
- Repeated re-summarization can introduce **drift** — each pass is an LLM's paraphrase of a paraphrase, and details can subtly shift over many iterations. **Memory Consolidation** (Pattern 14) generalizes this concern with more deliberate reconciliation strategies.

---

## 7. Production-Quality Python Implementation

### Requirements

```bash
pip install -U langchain-ollama langgraph langchain-core
# Ollama model used locally:
#   ollama pull llama3.1
```

### `summary_memory.py`

```python
"""
Pattern 8: Summary Memory
Bounded context length via incremental summarization of aged-out
messages, preserving the gist of a long-running conversation, built
on LangGraph + langchain-ollama.

Run:
    python summary_memory.py
"""

from __future__ import annotations

import logging
import sqlite3
from typing import Annotated, TypedDict

from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    RemoveMessage,
    SystemMessage,
)
from langchain_ollama import ChatOllama
from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import REMOVE_ALL_MESSAGES, add_messages

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("summary_memory")

RECENT_MESSAGE_THRESHOLD = 12   # trigger summarization above this many raw messages
KEEP_RECENT_COUNT = 6           # how many of the newest messages stay raw after folding


# --------------------------------------------------------------------------
# 1. Graph state: raw recent messages + a running natural-language summary
#    of everything older, kept separately.
# --------------------------------------------------------------------------
class ProjectState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]
    running_summary: str


BASE_SYSTEM_PROMPT = "You are ProjectPilot, a project management assistant."


def build_llm(model: str = "llama3.1", temperature: float = 0.2) -> ChatOllama:
    return ChatOllama(model=model, temperature=temperature, keep_alive="10m")


# --------------------------------------------------------------------------
# 2. Node: chat, using [summary] + [raw recent messages] as context.
# --------------------------------------------------------------------------
def make_chat_node(llm: ChatOllama):
    def chat_node(state: ProjectState) -> ProjectState:
        if state.get("running_summary"):
            system_prompt = SystemMessage(
                content=(
                    f"{BASE_SYSTEM_PROMPT}\n\n"
                    f"Summary of the project so far (older history, already "
                    f"condensed):\n{state['running_summary']}"
                )
            )
        else:
            system_prompt = SystemMessage(content=BASE_SYSTEM_PROMPT)

        model_input = [system_prompt, *state["messages"]]

        try:
            response: AIMessage = llm.invoke(model_input)
        except Exception:
            logger.exception("LLM call failed")
            response = AIMessage(content="Sorry, I hit an error. Please try again.")

        return {"messages": [response]}

    return chat_node


# --------------------------------------------------------------------------
# 3. Node: fold the oldest raw messages into an updated running summary,
#    then remove those messages from the raw window.
# --------------------------------------------------------------------------
SUMMARIZE_PROMPT = """You maintain a running summary of a project management \
conversation. Update the summary below to incorporate the new messages, \
preserving important decisions, dates, numbers, and reasons -- be concise \
but do not drop concrete facts (e.g. feature names, dates, dollar amounts).

Existing summary:
{existing_summary}

New messages to fold in:
{new_messages}

Updated summary (concise, a few sentences to a short paragraph):"""


def make_summarize_node(llm: ChatOllama):
    def summarize_node(state: ProjectState) -> ProjectState:
        messages = state["messages"]
        if len(messages) <= RECENT_MESSAGE_THRESHOLD:
            return {}

        num_to_fold = len(messages) - KEEP_RECENT_COUNT
        to_fold = messages[:num_to_fold]
        remaining = messages[num_to_fold:]

        new_messages_text = "\n".join(
            f"{m.type}: {m.content}" for m in to_fold if hasattr(m, "content")
        )
        prompt = SUMMARIZE_PROMPT.format(
            existing_summary=state.get("running_summary") or "(none yet)",
            new_messages=new_messages_text,
        )

        try:
            updated_summary = llm.invoke(prompt).content
        except Exception:
            logger.exception("Summarization LLM call failed; keeping old summary and raw window as-is.")
            return {}

        logger.info(
            "Folded %d old message(s) into summary; %d message(s) remain raw.",
            len(to_fold), len(remaining),
        )

        # Remove the folded-in messages from the checkpointed raw window.
        # `add_messages` understands RemoveMessage to delete by id.
        removals = [RemoveMessage(id=m.id) for m in to_fold]

        return {
            "running_summary": updated_summary,
            "messages": removals,
        }

    return summarize_node


# --------------------------------------------------------------------------
# 4. Assemble the graph
# --------------------------------------------------------------------------
def build_graph(db_path: str = "conversations.sqlite"):
    llm = build_llm()

    graph_builder = StateGraph(ProjectState)
    graph_builder.add_node("chat_node", make_chat_node(llm))
    graph_builder.add_node("summarize_node", make_summarize_node(llm))

    graph_builder.add_edge(START, "chat_node")
    graph_builder.add_edge("chat_node", "summarize_node")
    graph_builder.add_edge("summarize_node", END)

    conn = sqlite3.connect(db_path, check_same_thread=False)
    checkpointer = SqliteSaver(conn)

    return graph_builder.compile(checkpointer=checkpointer)


# --------------------------------------------------------------------------
# 5. Service wrapper
# --------------------------------------------------------------------------
class SummaryMemoryService:
    def __init__(self, db_path: str = "conversations.sqlite"):
        self.graph = build_graph(db_path)

    def send(self, thread_id: str, user_message: str) -> str:
        config = {"configurable": {"thread_id": thread_id}}
        result = self.graph.invoke(
            {"messages": [HumanMessage(content=user_message)]},
            config=config,
        )
        return result["messages"][-1].content

    def get_summary(self, thread_id: str) -> str:
        config = {"configurable": {"thread_id": thread_id}}
        state = self.graph.get_state(config)
        return state.values.get("running_summary", "") if state else ""

    def raw_message_count(self, thread_id: str) -> int:
        config = {"configurable": {"thread_id": thread_id}}
        state = self.graph.get_state(config)
        return len(state.values.get("messages", [])) if state else 0


# --------------------------------------------------------------------------
# 6. Demo: simulate a long project thread, show summarization kick in,
#    and confirm an early fact is still answerable much later.
# --------------------------------------------------------------------------
if __name__ == "__main__":
    service = SummaryMemoryService()
    thread_id = "project_atlas"

    scripted_turns = [
        "We're kicking off Project Atlas. Scope: auth, billing, reporting, and notifications.",
        "Client wants launch by end of Q3 originally.",
        "Week 2: billing integration is more complex than expected, may slip.",
        "Week 3: client moved the launch date UP by a month due to a trade show.",
        "Given the tighter timeline, we're cutting the reporting feature from v1.",
        "Notifications feature is on track, no changes there.",
        "Week 5: auth module passed security review.",
        "Week 6: billing integration finally stable after the rework.",
        "Week 7: QA found two critical bugs in notifications, fixing now.",
        "Week 8: both bugs fixed, notifications back on track.",
        "Week 9: doing a full regression pass before launch.",
        "Week 10: launch is tomorrow, final go/no-go meeting is at 9am.",
        "Launch went out successfully this morning!",
    ]

    for turn in scripted_turns:
        service.send(thread_id, turn)

    print(f"Raw messages currently kept: {service.raw_message_count(thread_id)}")
    print(f"\nRunning summary:\n{service.get_summary(thread_id)}")

    print("\n--- Much later: a retrospective question about an EARLY decision ---")
    reply = service.send(thread_id, "Remind me why we cut the reporting feature?")
    print("Bot:", reply)
```

### Notes on production-readiness in this code

- **`RemoveMessage` + the `add_messages` reducer** is the correct, checkpointer-aware way to delete specific messages from persisted state in LangGraph — this actually removes them from what's stored, not just what's sent to the model (a meaningful difference from Pattern 2's Short-Term Memory, which never deletes from storage).
- **Incremental summarization** — each summarization call only processes the old summary + the newly-aged-out messages, not the entire history from scratch, keeping the summarization LLM call's cost roughly constant regardless of total conversation length.
- **`KEEP_RECENT_COUNT` buffer** ensures a healthy amount of raw, verbatim recent context always remains available for immediate follow-ups, while `RECENT_MESSAGE_THRESHOLD` controls how often the (moderately expensive) summarization pass runs.
- **Graceful degradation on summarization failure** — if the summarization LLM call errors, the node returns `{}` (no state change) rather than corrupting the running summary or losing messages; the raw window will simply attempt to summarize again next turn.
- **Concrete-fact preservation instruction** in `SUMMARIZE_PROMPT` explicitly asks the model not to drop dates/numbers/names during compression — a deliberate mitigation for the "summarization is lossy" trade-off discussed above.

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

> `RemoveMessage` and the `add_messages` reducer's delete-by-id support live in `langchain_core.messages` / `langgraph.graph.message` and require no extra dependency beyond what Pattern 1 already installs.

---

**Next up → Pattern 9: Vector-Based Memory** (a deeper look at the general-purpose vector-store retrieval infrastructure underlying Patterns 4, 5, and 6 — indexing strategies, chunking for memory, and retrieval tuning).
