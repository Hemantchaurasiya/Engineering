# Pattern 5: Episodic Memory

> Part of the **Memory Patterns** series (Section 6). Stack: `langchain-ollama` (ChatOllama + OllamaEmbeddings), `langgraph`, `langchain-chroma`, local models (`llama3.1`, `nomic-embed-text`).

---

## 1. Introduce the Pattern

**Episodic Memory** stores **specific, time-stamped past experiences ("episodes")** — not generalized facts, but records of *what actually happened*, when, in what context, and with what outcome — so the system can later ask "have we seen something like this before, and how did it turn out?"

This is the natural sibling of Pattern 4 (Semantic Memory). The distinction matters:

| | Semantic Memory | Episodic Memory |
|---|---|---|
| Stores | A generalized fact | A specific dated event/experience |
| Example | "This client's firewall blocks webhooks" | "On 2026-03-14, incident #4521: checkout API returned 500s after a bad deploy; rollback fixed it in 12 minutes" |
| Shape | Flat statement, timeless | Structured record: timestamp, situation, action taken, outcome |
| Question it answers | "What do we generally know?" | "What happened last time something like this occurred?" |

Episodic Memory is what lets a system reason **by precedent** — the same way a senior engineer says "oh, we've seen this exact symptom before, in March, and it turned out to be X" — instead of reasoning from scratch or from generic facts alone.

---

## 2. Problem It Solves

Without Episodic Memory, an AI assistant treats every situation as brand new, even if a nearly identical one was already solved:

```
[March]  Incident: checkout API 500s after deploy -> rollback fixed it. Resolved, logged in a ticket system nobody re-reads.
[August] Incident: checkout API 500s after deploy (again!)
Bot:     Let's start investigating from scratch: check logs, check CPU, check recent deploys...
```

The assistant re-derives the same diagnosis process every time, ignoring that this exact pattern (and its fix) already happened and is a matter of record. This wastes time in exactly the situations where speed matters most (live incidents), and it fails to build institutional memory — the system never gets *better* at handling recurring situations, even though the data to do so already exists.

Episodic Memory solves this by:
- **Recording structured episodes** — not raw transcripts, but *(timestamp, situation, action taken, outcome)* tuples — after something notable concludes
- **Retrieving similar past episodes** by embedding the *current* situation and searching for close matches, optionally weighted by recency
- **Surfacing precedent** ("last time this happened, X fixed it") directly in the assistant's reasoning, so it can suggest a fast path instead of starting cold

---

## 3. Realistic Production/Enterprise Scenario

**Scenario: "IncidentMemory" — an SRE/on-call copilot for a platform engineering team**

- Every resolved production incident has a recognizable shape: symptom, root cause, fix, time-to-resolve.
- Incidents recur — different services, similar failure modes (bad deploy, expired cert, downstream timeout, connection pool exhaustion).
- During a *live* incident, minutes matter. If the system can instantly recall "we saw p99 latency spikes on this service exactly like this in June, root cause was a connection pool leak, fix was bumping pool size + restart," the on-call engineer gets a massive head start instead of re-diagnosing from zero.
- Unlike Semantic Memory's timeless facts, precedent here is inherently **event-shaped and time-stamped** — engineers explicitly want to know "when did this happen before and what did we do," not just "what do we generally know about this service."

This is exactly what Episodic Memory is for: a **searchable log of past resolved situations**, retrieved by similarity to the current one, that lets the assistant reason by precedent.

---

## 4. Architecture / Flow Diagram

```
                     LIVE INCIDENT (new episode being formed)
                     ┌───────────────────────────────────────────┐
                     │   Client: "checkout API returning 500s       │
                     │   after latest deploy"                        │
                     └───────────────────┬───────────────────────────┘
                                          │
                                          ▼
                     ┌───────────────────────────────────────────┐
                     │              LangGraph StateGraph             │
                     │                                                │
                     │  ┌─────────────────────────┐                  │
                     │  │ recall_episodes_node        │  embeds current │
                     │  │                              │  situation,     │
                     │  │                              │  searches past  │
                     │  │                              │  episodes       │
                     │  └────────────┬─────────────────┘                  │
                     │               │  top-k similar past episodes         │
                     │               ▼                                       │
                     │  ┌─────────────────────────┐                        │
                     │  │  chat_node                 │──▶ ChatOllama         │
                     │  │  (precedent injected)       │    (llama3.1)         │
                     │  └────────────┬─────────────────┘                        │
                     │               │                                           │
                     │               ▼                                           │
                     │  ┌─────────────────────────┐                          │
                     │  │ log_episode_node (once      │  ONLY when incident    │
                     │  │ incident is marked resolved) │  is resolved -- writes │
                     │  │                              │  a new structured      │
                     │  │                              │  episode record        │
                     │  └────────────┬─────────────────┘                          │
                     └───────────────┼───────────────────────────────────────────┘
                                      ▼
                     ┌───────────────────────────────────────────┐
                     │        Chroma vector store (persistent)      │
                     │  collection: "incident_episodes"              │
                     │                                                │
                     │  episode: {                                    │
                     │    "timestamp": "2026-06-02T14:03:00Z",         │
                     │    "situation": "p99 latency spike, checkout",  │
                     │    "root_cause": "connection pool leak",        │
                     │    "resolution": "bumped pool size + restart",  │
                     │    "time_to_resolve_min": 18                    │
                     │  }                                             │
                     └───────────────────────────────────────────┘
```

**Key idea:** the vector store here holds **structured episode records**, not loose facts — each entry has a timestamp and an outcome, and episodes are only written when something concludes (resolved), not on every message, unlike Semantic Memory which can pick up facts mid-conversation.

---

## 5. Complete Request-to-Response Flow

**A new incident comes in for a checkout-service outage:**

1. **On-call engineer sends** `"checkout API returning 500s right after the 2pm deploy"` via the incident channel.
2. **`recall_episodes_node`** embeds this description with `OllamaEmbeddings`, then runs `Chroma.similarity_search(query, k=3)` against `incident_episodes`.
3. It finds a close match from June: *"p99 latency spike, checkout service, root cause: connection pool leak, fix: bumped pool size + restart, resolved in 18 minutes."* Even though the wording differs ("500s after deploy" vs. "latency spike"), the embeddings capture that both are checkout-service degradation incidents with a deploy/config trigger.
4. **`chat_node`** calls `ChatOllama.invoke(...)` with that precedent injected into the system prompt. The model responds: *"This resembles incident from June (connection pool leak). Suggest checking pool size/config in this deploy first before a full investigation — that resolved a similar symptom in 18 minutes previously."*
5. The engineer investigates, confirms a similar root cause, and resolves the incident.
6. **`log_episode_node`** runs once the incident is marked resolved (a distinct, explicit trigger — not every chat turn): it writes a *new* structured episode — timestamp, situation, root cause, resolution, time-to-resolve — back into the vector store.
7. The next similar incident, months later, now has **two** precedents to draw from.

---

## 6. Why This Pattern Is Appropriate

| Requirement | How Episodic Memory satisfies it |
|---|---|
| Reason by precedent, not from scratch | Similarity search surfaces the closest past *resolved* situation |
| Time-awareness | Every episode is timestamped — retrieval can weight/filter by recency |
| Institutional memory that compounds | Each resolved incident becomes future precedent automatically |
| Distinguish "what happened" from "what's generally true" | Episodes are structured events with outcomes, not standalone facts |

**Trade-offs / when it's not enough:**
- Episodic Memory only helps once episodes have actually been **logged** — an incident that was resolved but never explicitly recorded (e.g., the `log_episode_node` trigger was missed) leaves no precedent, unlike Semantic Memory which can pick up facts opportunistically mid-conversation.
- As the number of episodes grows into the thousands, older, less-relevant episodes can crowd out better matches, or become stale (a fix that was right two years ago on an old architecture may mislead today) — this is where **Memory Forgetting** (Pattern 16) and **Memory Consolidation** (Pattern 14) become necessary companions.
- Episodic Memory captures *individual* events well but doesn't automatically generalize across many episodes into a rule ("connection pool leaks happen almost every time we deploy on Fridays") — spotting that kind of pattern-across-episodes is closer to a **Memory Consolidation** or analytics task built on top of the episode log.

---

## 7. Production-Quality Python Implementation

### Requirements

```bash
pip install -U langchain-ollama langgraph langchain-core langchain-chroma chromadb
# Ollama models used locally:
#   ollama pull llama3.1
#   ollama pull nomic-embed-text
```

### `episodic_memory.py`

```python
"""
Pattern 5: Episodic Memory
Structured, time-stamped past-event records, retrieved by similarity
to reason by precedent, built on langchain-chroma + langchain-ollama.

Run:
    python episodic_memory.py
"""

from __future__ import annotations

import json
import logging
import uuid
from datetime import datetime, timezone
from typing import Annotated, Optional, TypedDict

from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    SystemMessage,
)
from langchain_ollama import ChatOllama, OllamaEmbeddings
from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("episodic_memory")


# --------------------------------------------------------------------------
# 1. Graph state
# --------------------------------------------------------------------------
class IncidentState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]
    recalled_episodes: list[dict]
    resolved: bool               # explicit trigger: only True when the
    resolution_summary: Optional[str]  # incident is actually closed out


BASE_SYSTEM_PROMPT = (
    "You are IncidentCopilot, an SRE assistant. When similar past incidents "
    "are provided, explicitly reference them and suggest checking the same "
    "root cause first before a full investigation. Be concise and actionable."
)


def build_llm(model: str = "llama3.1", temperature: float = 0.2) -> ChatOllama:
    return ChatOllama(model=model, temperature=temperature, keep_alive="10m")


def build_embeddings(model: str = "nomic-embed-text") -> OllamaEmbeddings:
    return OllamaEmbeddings(model=model)


def build_vectorstore(embeddings: OllamaEmbeddings, persist_dir: str = "./episodic_memory_db") -> Chroma:
    return Chroma(
        collection_name="incident_episodes",
        embedding_function=embeddings,
        persist_directory=persist_dir,
    )


# --------------------------------------------------------------------------
# 2. Node: recall similar past episodes for the CURRENT situation.
# --------------------------------------------------------------------------
def make_recall_node(vectorstore: Chroma, k: int = 3):
    def recall_episodes_node(state: IncidentState) -> IncidentState:
        last_human = next(
            (m for m in reversed(state["messages"]) if isinstance(m, HumanMessage)),
            None,
        )
        if last_human is None:
            return {"recalled_episodes": []}

        results = vectorstore.similarity_search(last_human.content, k=k)
        episodes = []
        for doc in results:
            try:
                episodes.append(json.loads(doc.page_content))
            except json.JSONDecodeError:
                continue

        logger.info("Recalled %d past episode(s) for current situation", len(episodes))
        return {"recalled_episodes": episodes}

    return recall_episodes_node


# --------------------------------------------------------------------------
# 3. Node: chat, with recalled episodes (precedent) injected.
# --------------------------------------------------------------------------
def make_chat_node(llm: ChatOllama):
    def chat_node(state: IncidentState) -> IncidentState:
        if state["recalled_episodes"]:
            precedent_lines = []
            for ep in state["recalled_episodes"]:
                precedent_lines.append(
                    f"- [{ep.get('timestamp', 'unknown date')}] Situation: "
                    f"{ep.get('situation')} | Root cause: {ep.get('root_cause')} | "
                    f"Resolution: {ep.get('resolution')} "
                    f"(resolved in {ep.get('time_to_resolve_min', '?')} min)"
                )
            precedent_block = "\n".join(precedent_lines)
        else:
            precedent_block = "(no similar past incidents on file)"

        system_prompt = SystemMessage(
            content=f"{BASE_SYSTEM_PROMPT}\n\nSimilar past incidents:\n{precedent_block}"
        )

        try:
            response: AIMessage = llm.invoke([system_prompt, *state["messages"]])
        except Exception:
            logger.exception("LLM call failed")
            response = AIMessage(content="Sorry, I hit an error. Please try again.")

        return {"messages": [response]}

    return chat_node


# --------------------------------------------------------------------------
# 4. Node: log a NEW episode -- only fires when the incident is explicitly
#    marked resolved (a deliberate event, not every chat turn).
# --------------------------------------------------------------------------
def make_log_episode_node(vectorstore: Chroma):
    def log_episode_node(state: IncidentState) -> IncidentState:
        if not state.get("resolved"):
            return {}

        situation = next(
            (m.content for m in state["messages"] if isinstance(m, HumanMessage)),
            "unknown situation",
        )

        episode = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "situation": situation,
            "root_cause": state.get("resolution_summary", "unspecified"),
            "resolution": state.get("resolution_summary", "unspecified"),
            "time_to_resolve_min": None,  # would be computed from real incident timers
        }

        doc = Document(
            page_content=json.dumps(episode),
            metadata={"timestamp": episode["timestamp"]},
            id=str(uuid.uuid4()),
        )
        vectorstore.add_documents([doc])
        logger.info("Logged new episode: %s", episode["situation"][:80])

        return {}

    return log_episode_node


# --------------------------------------------------------------------------
# 5. Assemble the graph
# --------------------------------------------------------------------------
def build_graph(persist_dir: str = "./episodic_memory_db"):
    llm = build_llm()
    embeddings = build_embeddings()
    vectorstore = build_vectorstore(embeddings, persist_dir)

    graph_builder = StateGraph(IncidentState)
    graph_builder.add_node("recall_episodes_node", make_recall_node(vectorstore))
    graph_builder.add_node("chat_node", make_chat_node(llm))
    graph_builder.add_node("log_episode_node", make_log_episode_node(vectorstore))

    graph_builder.add_edge(START, "recall_episodes_node")
    graph_builder.add_edge("recall_episodes_node", "chat_node")
    graph_builder.add_edge("chat_node", "log_episode_node")
    graph_builder.add_edge("log_episode_node", END)

    return graph_builder.compile()


# --------------------------------------------------------------------------
# 6. Service wrapper
# --------------------------------------------------------------------------
class EpisodicMemoryService:
    def __init__(self, persist_dir: str = "./episodic_memory_db"):
        self.graph = build_graph(persist_dir)

    def report_incident(self, description: str) -> str:
        result = self.graph.invoke(
            {
                "messages": [HumanMessage(content=description)],
                "recalled_episodes": [],
                "resolved": False,
                "resolution_summary": None,
            }
        )
        return result["messages"][-1].content

    def close_incident(self, description: str, resolution_summary: str) -> None:
        """Explicit resolution event -- this is what writes a new episode."""
        self.graph.invoke(
            {
                "messages": [HumanMessage(content=description)],
                "recalled_episodes": [],
                "resolved": True,
                "resolution_summary": resolution_summary,
            }
        )


# --------------------------------------------------------------------------
# 7. Demo: log a June incident, then show recall on a similarly-worded
#    (but not identical) August incident.
# --------------------------------------------------------------------------
if __name__ == "__main__":
    service = EpisodicMemoryService()

    print("--- June: incident occurs and gets resolved ---")
    service.close_incident(
        description="Checkout service p99 latency spike right after a deploy.",
        resolution_summary="Connection pool leak; bumped pool size and restarted service.",
    )
    print("Episode logged.")

    print("\n--- August: a NEW, differently-worded incident comes in ---")
    reply = service.report_incident(
        "checkout API returning 500s right after the 2pm deploy today"
    )
    print("Bot:", reply)
    print("(^ should reference the June connection-pool-leak precedent)")
```

### Notes on production-readiness in this code

- **Explicit resolution trigger (`resolved=True`)** — episodes are only written when an incident is deliberately closed out, not on every chat message. This mirrors real incident-management workflows (PagerDuty/Jira-style "resolve" actions) and avoids polluting episodic memory with half-formed, in-progress situations.
- **Structured JSON episodes**, not raw prose — every episode has consistent fields (`timestamp`, `situation`, `root_cause`, `resolution`, `time_to_resolve_min`), so downstream code (dashboards, consolidation jobs) can process them reliably, while the embedding is still computed over the natural-language `situation`/`root_cause` text for good similarity matching.
- **Timestamped by design** — every episode records `datetime.now(timezone.utc)`, enabling future recency-weighting or expiry logic (a hook for Pattern 16, Memory Forgetting).
- **Separate vector collection (`incident_episodes`)** from Pattern 4's `account_facts` — episodic and semantic memory are conceptually distinct and should not be mixed in the same index, since their retrieval semantics (event precedent vs. general fact) differ.
- **Graceful degradation**: if no similar episodes exist yet (cold start), the assistant explicitly says so rather than fabricating false precedent.

---

## 8. Version Pins (latest stable, compatible, at time of writing)

```
python            >= 3.11
langchain-core    >= 0.3.0
langchain-ollama  >= 0.2.0
langchain-chroma  >= 0.1.4
chromadb          >= 0.5.0
langgraph         >= 0.2.0
```

```bash
pip install -U "langchain-core>=0.3.0" "langchain-ollama>=0.2.0" "langchain-chroma>=0.1.4" "chromadb>=0.5.0" "langgraph>=0.2.0"
```

> Requires both Ollama models pulled locally: `ollama pull llama3.1` (generation) and `ollama pull nomic-embed-text` (embeddings).

---

**Next up → Pattern 6: Procedural Memory** (remembering *how to do things* — learned skills, workflows, or successful action sequences — as opposed to facts or events).
