# Pattern 06 — Context Window Management

[← Back to index](./README.md) | Previous: [Pattern 05 — Context Prioritization](./05-context-prioritization.md) | Next: Pattern 07 — Context Caching (queued)

---

## 1. Introduce the pattern

Every pattern so far (01–05) has solved a **single-request** problem: given one query and one pool of candidate context, build the best possible prompt once. **Context Window Management** zooms out to a **long-running, multi-turn session** — an investigation that spans many back-and-forth exchanges between an analyst and the copilot — and asks: *as the conversation grows turn over turn, how do I keep the active context window from growing unboundedly, while not losing the facts established earlier in the session?*

```
Turn 1 → Turn 2 → Turn 3 → ... → Turn N
   │                                │
   └── each turn adds messages ─────┘
        to a session that will eventually
        exceed any fixed context budget

[ WINDOW MANAGEMENT ] sits between every turn and the next LLM call:
  - decide what stays in the "hot" window (verbatim, recent)
  - decide what gets evicted from the hot window
  - decide what, if anything, must be preserved from evicted content
    as durable session-level facts (not summarization yet — that's Pattern 10)
```

This is the first pattern in the series that is inherently **stateful across calls**, which is why it's also the first to use **LangGraph**: the previous five patterns were pure functions over a single request; this one requires a persisted, evolving state object that survives between turns.

**Mental model:** Patterns 01–05 built the perfect single briefing document. Window Management is what a case file clerk does over the course of a months-long investigation — keeping the desk clear (only the most recent, active documents sit out), while making sure that when an older folder gets put back in storage, the one or two facts that actually matter from it get written on an index card that stays on the desk permanently.

---

## 2. The problem it solves

Multi-turn LLM sessions have a structural problem that single-request systems don't: **the conversation itself is the thing that grows**, and nothing in Patterns 01–05 addresses that, because those patterns all assume a fixed pool of *external* context sources for one request.

1. **Unbounded growth.** Every turn appends at least a user message and an assistant response. A 40-turn investigation session will, left unmanaged, eventually exceed any context window — and even before hitting a hard limit, a bloated history dilutes attention on the current turn's actual question (the same "lost in the middle" issue from Pattern 04, but now caused by conversation length rather than context-source count).
2. **Naive fixes lose critical information.** The simplest fix — "just keep the last K turns" — silently discards facts established early in the session. If an analyst confirmed in turn 3 that a beneficiary account was linked to a prior confirmed-fraud case, and that fact scrolls out of a sliding window by turn 15, the copilot will contradict or forget it, which is actively dangerous in a compliance context.
3. **Cost and latency compound over a session.** Resending the entire growing history on every turn means turn 40 costs (and takes) many times more than turn 1 — for most of a session, most of that resent history isn't relevant to the *current* question.

Window Management solves this by explicitly separating "recent, verbatim, hot" context from "older, evicted, but durably remembered" context — and making the boundary between them a designed policy rather than an accident of however many turns fit in the model's raw context length.

---

## 3. A realistic enterprise problem (Helios)

An analyst opens a running investigation thread on `CASE-88421` and works it over many turns across a session: asking about the flagged transaction, then the customer's transaction history, then a related historical case, then compliance implications, then drafting an escalation note — six or more exchanges, likely more in a real investigation that might span a working day with interruptions.

Two things must both be true simultaneously:

- The **hot window** (what's sent verbatim to the LLM on each turn) must stay small enough to keep latency and cost reasonable and keep the model's attention on the *current* question.
- **Durable facts** established early in the session — "the beneficiary account was linked to CASE-71190," "the customer confirmed a business justification for the 18-day-ago transfer" — must survive being evicted from the hot window, because a fraud determination made in turn 20 that contradicts a fact confirmed in turn 3 is a real compliance problem, not just an inconvenience.

Context Window Management is the pattern that makes both of these true at once, turn after turn, for the life of the investigation session.

---

## 4. Architecture / flow diagram

```mermaid
flowchart TD
    A[Analyst sends message\nTurn N] --> B[LangGraph StateGraph\n(persisted via checkpointer, keyed by case_id)]

    subgraph B["Per-turn graph execution"]
        C[Load session state:\nmessages, session_facts, case_id]
        D{Hot window\nover token budget?}
        E["Extract durable facts from\nmessages about to be evicted\n(deterministic FINDING: tags)"]
        F[Trim hot window to budget\n(keep most recent messages)]
        G[Assemble prompt:\nsystem + session_facts + hot window + new message]
        H[ChatOllama generates response]
        I[Append response to messages,\npersist updated state via checkpointer]

        C --> D
        D -- Yes --> E --> F --> G
        D -- No --> G
        G --> H --> I
    end

    I --> J[Response to analyst]
    J -.->|next turn, same case_id| A

    style B fill:#4A90D9,color:#fff
    style E fill:#4A90D9,color:#fff
```

---

## 5. The complete request-to-response flow

1. **Turn arrives**: the analyst sends a new message tied to a `case_id`, which doubles as the LangGraph **thread id** — this is how state is looked up and persisted across turns.
2. **State load**: LangGraph's checkpointer (here, an in-memory saver — swap for a persistent backend like Postgres in production) restores the session's prior `messages`, `session_facts`, and metadata.
3. **Budget check**: the pipeline estimates the token size of the current hot window. If it's within budget, no eviction is needed this turn.
4. **Fact extraction before eviction**: if the window is over budget, messages that are about to be trimmed are scanned for durable facts — in this implementation, a deterministic convention (`FINDING: ...` prefix, or a set pattern) rather than an extra LLM call, keeping this pattern cheap and predictable. (An LLM-based extraction is a reasonable upgrade, but it starts to overlap with Pattern 10's job — Window Management's extraction step is intentionally lightweight.)
5. **Trim**: the hot window is trimmed to the most recent messages that fit the budget, using LangChain's built-in `trim_messages` utility with a custom token counter.
6. **Assemble the prompt**: system prompt + the accumulated `session_facts` (which never get evicted, by design) + the trimmed hot window + the new message.
7. **LLM call**: `ChatOllama` generates a response grounded in both the recent conversation and the durable facts from earlier in the session.
8. **Persist**: the new message pair (human + AI) and any newly extracted facts are written back into the graph's state via the checkpointer, ready for the next turn.

---

## 6. Why this pattern is appropriate here

- **It's the natural point to introduce statefulness.** Patterns 01–05 are legitimately stateless, single-request transforms — forcing session state into them would have made those patterns harder to understand in isolation. Window Management is where a session-spanning state machine actually becomes necessary, which is exactly why LangGraph enters the curriculum here rather than earlier.
- **Separating "hot window" from "durable facts" avoids the two failure modes at once.** A pure sliding window is cheap but forgets things. A "never evict anything" policy remembers everything but doesn't scale. Splitting the concern gives you both: bounded cost per turn, and a growing-but-small set of durable facts that scales with *information*, not with *turn count*.
- **Deterministic fact extraction is a deliberate scope boundary.** It would be tempting to reach for an LLM summarization call every time the window needs trimming — but that's Pattern 10's job, and conflating the two makes this pattern's cost and behavior much harder to reason about. Window Management's extraction step stays cheap and predictable; Pattern 10 will show the richer, LLM-based alternative when genuine summarization (not just fact-pinning) is the right tool.
- **`case_id` as the thread key mirrors Helios's real domain model.** Fraud investigations are naturally long-running, resumable units of work — using the case id as the session identity means the window-management state model maps directly onto how Helios already organizes work, rather than inventing a parallel "conversation id" concept.

---

## 7. Production-quality Python implementation

```python
"""
helios_context_window_management.py

Pattern 06 — Context Window Management
Helios Fraud Investigation Copilot

A LangGraph-based, stateful, multi-turn session manager. Keeps a bounded
"hot window" of recent messages per case, evicting older messages once the
window exceeds a token budget -- but first extracting any durable facts
(FINDING: ... tagged content) from messages about to be evicted, so they
persist for the life of the investigation even after the raw messages
that established them are gone.

Requirements:
    pip install "langchain-core>=0.3.60" "langchain-ollama>=0.3.0" "langgraph>=0.2.60"

Assumes a local Ollama server (`ollama serve`) with:
    ollama pull llama3.1
"""

from __future__ import annotations

import logging
import re
from typing import Annotated, TypedDict

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage, trim_messages
from langchain_ollama import ChatOllama
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages

logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")
logger = logging.getLogger("helios.context_window_management")


# --------------------------------------------------------------------------- #
# Config
# --------------------------------------------------------------------------- #

HOT_WINDOW_TOKEN_BUDGET = 400  # deliberately small, to force eviction within a short demo session
FINDING_PATTERN = re.compile(r"FINDING:\s*(.+)", re.IGNORECASE)


def estimate_tokens(text: str) -> int:
    return max(1, len(text) // 4)


def message_token_counter(messages: list[BaseMessage]) -> int:
    """Custom token counter for trim_messages — a real deployment would swap
    this for the tokenizer matching the deployed model."""
    return sum(estimate_tokens(str(m.content)) for m in messages)


# --------------------------------------------------------------------------- #
# Graph state
# --------------------------------------------------------------------------- #

class InvestigationState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]
    session_facts: list[str]  # durable facts, never evicted once extracted
    case_id: str


# --------------------------------------------------------------------------- #
# Fact extraction (deterministic, cheap — NOT an LLM summarization call;
# that richer capability is Pattern 10, Context Summarization)
# --------------------------------------------------------------------------- #

def extract_durable_facts(messages: list[BaseMessage]) -> list[str]:
    """Scans messages for the FINDING: convention and pulls out durable facts.
    In this demo, the copilot itself is prompted to tag genuinely durable
    conclusions with FINDING: — a simple, auditable, zero-extra-cost convention."""
    facts = []
    for msg in messages:
        for match in FINDING_PATTERN.finditer(str(msg.content)):
            facts.append(match.group(1).strip())
    return facts


# --------------------------------------------------------------------------- #
# Graph nodes
# --------------------------------------------------------------------------- #

SYSTEM_PROMPT_TEMPLATE = """You are the Helios Fraud Investigation Copilot, assisting an analyst
across a multi-turn investigation of case {case_id}.

Durable facts established earlier in this investigation (treat these as settled,
do not contradict them):
{session_facts}

When you reach a genuinely durable conclusion worth preserving for the rest of the
investigation (e.g. a confirmed link to another case, a confirmed customer explanation),
tag it on its own line as: FINDING: <the fact, stated plainly and durably>
Only use FINDING: for conclusions that should survive the rest of this conversation."""


def manage_window_node(state: InvestigationState) -> dict:
    """Runs BEFORE the LLM call each turn: checks the hot window's size, and
    if it's over budget, extracts durable facts from the messages about to be
    evicted, then trims the window down."""
    messages = state["messages"]
    current_tokens = message_token_counter(messages)

    if current_tokens <= HOT_WINDOW_TOKEN_BUDGET:
        logger.info("Hot window at %d/%d tokens — no eviction needed this turn.",
                     current_tokens, HOT_WINDOW_TOKEN_BUDGET)
        return {}

    trimmed = trim_messages(
        messages,
        max_tokens=HOT_WINDOW_TOKEN_BUDGET,
        token_counter=message_token_counter,
        strategy="last",       # keep the most recent messages
        include_system=False,  # system prompt is rebuilt fresh each turn, not part of the window
        allow_partial=False,
    )

    evicted = [m for m in messages if m not in trimmed]
    new_facts = extract_durable_facts(evicted)

    if new_facts:
        logger.info("Evicting %d message(s); extracted %d durable fact(s) before eviction.",
                     len(evicted), len(new_facts))
    else:
        logger.info("Evicting %d message(s); no durable facts found in evicted content.", len(evicted))

    # Returning {"messages": trimmed} with the add_messages reducer REPLACES
    # the tracked message list here because we pass full replacement semantics
    # via a fresh list rather than new messages to append — see the note in
    # the __main__ walkthrough for how this interacts with add_messages.
    return {
        "messages_override": trimmed,  # handled explicitly in run_turn(), see below
        "session_facts": state["session_facts"] + new_facts,
    }


def generate_response_node(state: InvestigationState) -> dict:
    """Assembles the system prompt (with durable facts baked in) plus the
    (already-trimmed) hot window, and calls the LLM."""
    llm = ChatOllama(model="llama3.1", temperature=0.2)

    facts_block = (
        "\n".join(f"- {f}" for f in state["session_facts"]) if state["session_facts"] else "(none yet)"
    )
    system_message = SystemMessage(
        content=SYSTEM_PROMPT_TEMPLATE.format(case_id=state["case_id"], session_facts=facts_block)
    )

    full_prompt = [system_message] + state["messages"]
    response = llm.invoke(full_prompt)

    return {"messages": [response]}


# --------------------------------------------------------------------------- #
# Graph assembly
# --------------------------------------------------------------------------- #

def build_investigation_graph():
    graph = StateGraph(InvestigationState)
    graph.add_node("manage_window", manage_window_node)
    graph.add_node("generate_response", generate_response_node)
    graph.add_edge(START, "manage_window")
    graph.add_edge("manage_window", "generate_response")
    graph.add_edge("generate_response", END)

    checkpointer = MemorySaver()  # swap for a persistent checkpointer (e.g. Postgres) in production
    return graph.compile(checkpointer=checkpointer)


# --------------------------------------------------------------------------- #
# Turn-driving helper
#
# manage_window_node can't cleanly express "replace the message list" through
# the add_messages reducer (which appends/merges by message id), so we handle
# window trimming explicitly here at the orchestration layer: read the graph
# state, trim it ourselves if needed, and write the trimmed list back via the
# checkpointer before invoking the graph for this turn's generation step.
# --------------------------------------------------------------------------- #

def run_turn(app, case_id: str, user_text: str) -> str:
    config = {"configurable": {"thread_id": case_id}}
    existing_state = app.get_state(config)

    if existing_state.values:
        messages = list(existing_state.values["messages"])
        session_facts = list(existing_state.values.get("session_facts", []))
    else:
        messages, session_facts = [], []

    current_tokens = message_token_counter(messages)
    if current_tokens > HOT_WINDOW_TOKEN_BUDGET:
        trimmed = trim_messages(
            messages, max_tokens=HOT_WINDOW_TOKEN_BUDGET,
            token_counter=message_token_counter, strategy="last",
            include_system=False, allow_partial=False,
        )
        evicted = [m for m in messages if m not in trimmed]
        new_facts = extract_durable_facts(evicted)
        messages = trimmed
        session_facts = session_facts + new_facts
        logger.info("[%s] Pre-turn eviction: %d message(s) evicted, %d new fact(s) extracted.",
                     case_id, len(evicted), len(new_facts))

    messages.append(HumanMessage(content=user_text))

    result = app.invoke(
        {"messages": messages, "session_facts": session_facts, "case_id": case_id},
        config=config,
    )
    return result["messages"][-1].content


# --------------------------------------------------------------------------- #
# Example run — a 6-turn investigation session on CASE-88421
# --------------------------------------------------------------------------- #

if __name__ == "__main__":
    app = build_investigation_graph()
    case_id = "CASE-88421"

    turns = [
        "Why was this $14,200 wire transfer flagged?",
        "Has this customer had any similar transfers before? Please tag any durable conclusion with FINDING:.",
        "Is the beneficiary account linked to any other case in our system?",
        "What did the customer say when contacted about the 18-day-ago transfer?",
        "Given everything so far, does Policy FR-14 apply here?",
        "Summarize the case status for my escalation note to the compliance team.",
    ]

    for turn_num, user_text in enumerate(turns, start=1):
        print(f"\n{'=' * 70}\nTURN {turn_num}: {user_text}\n{'=' * 70}")
        answer = run_turn(app, case_id, user_text)
        print(f"COPILOT: {answer}")

    final_state = app.get_state({"configurable": {"thread_id": case_id}})
    print(f"\n{'=' * 70}\nFINAL SESSION STATE")
    print(f"Hot window message count: {len(final_state.values['messages'])}")
    print("Durable session facts preserved across evictions:")
    for fact in final_state.values.get("session_facts", []):
        print(f"  - {fact}")
```

### Notes on running this yourself

- The `FINDING:` convention is deliberately simple and auditable — every durable fact in `session_facts` traces back to an explicit, greppable tag the model produced, rather than an opaque extraction step. You can inspect exactly why a fact was preserved.
- `MemorySaver` is process-local and will not survive a restart — it's the right choice for this worked example, but a real Helios deployment would use a persistent checkpointer (LangGraph supports Postgres-backed checkpointing) so investigation sessions survive service restarts.
- `run_turn`'s explicit pre-invoke trimming (rather than doing it purely inside `manage_window_node`) exists because LangGraph's `add_messages` reducer is append/merge-oriented by message id, not a wholesale replace — for a genuine eviction (removing old messages, not adding new ones), doing the trim at the orchestration layer before the graph call is the cleaner, more explicit approach. This is a good example of a case where fighting a framework's default reducer semantics is a sign to step outside them rather than around them.
- With `HOT_WINDOW_TOKEN_BUDGET = 400` (deliberately tight for this demo), you should see eviction and fact-extraction logging kick in by turn 3 or 4 — try raising the budget to see the same session run without any eviction at all, and compare the final answer quality and the size of `session_facts` in both cases.

---

**Next up:** Pattern 07 — Context Caching, where we stop re-computing and re-sending context (like the fixed system prompt and durable session facts built up in this pattern) that hasn't changed since the last call.
