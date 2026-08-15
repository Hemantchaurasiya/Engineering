# Pattern 11: Persistent Memory

> Part of the **Memory Patterns** series (Section 6). Stack: `langchain-ollama` (ChatOllama + OllamaEmbeddings), `langgraph`, local models (`llama3.1`, `nomic-embed-text`).

---

## 1. Introduce the Pattern

**Persistent Memory** is a cross-cutting concern: the guarantee that whatever memory your system holds — conversation history, long-term facts, entity records, vector embeddings — **survives process restarts, deployments, crashes, and infrastructure failures**, and can be correctly recovered afterward.

Every pattern so far has *used* persistence (SQLite checkpointers, Chroma's `persist_directory`, `InMemoryStore`) somewhat in passing. Pattern 11 makes persistence itself the subject: what does it actually take to run these systems reliably in production, where processes restart routinely (deploys, autoscaling, crashes) and data loss is unacceptable?

This pattern is about the **backend choices and operational discipline** behind the checkpointers and stores used everywhere else in this series:
- Choosing a durable backend appropriate to your deployment (SQLite for a single node, Postgres/Redis for multi-instance production)
- Understanding what's *actually* persisted vs. what silently lives only in process memory (a common, costly mistake)
- Backup and recovery — being able to restore state after a catastrophic failure
- Graceful behavior when the persistence layer itself is temporarily unavailable

---

## 2. Problem It Solves

Several of the earlier patterns' demo code used `InMemoryStore()` or a local SQLite file for simplicity — but naive choices here cause real production incidents:

```
[Naive approach: InMemoryStore() for Long-Term Memory in production]

Monday:    10,000 users' preference profiles accumulate in the running
           process's memory.
Tuesday:   A routine deploy restarts the app server (completely normal,
           happens every release).
Tuesday
(minutes
later):    ALL 10,000 users' long-term profiles are gone. Nothing was
           ever written to durable storage. This is silent, total data
           loss, and it will happen again on the NEXT deploy too.
```

This is not a hypothetical — it's the single most common mistake when moving a memory-pattern prototype into production: demo code deliberately uses in-memory stores for simplicity (as this series has, appropriately, for runnable standalone examples), and teams sometimes ship that as-is.

Persistent Memory as a *pattern* solves this by making durability an explicit, deliberate design decision at every layer:
- **Checkpointer backend** — SQLite (single-node), Postgres (multi-instance production), or Redis (low-latency, multi-instance)
- **Store backend** — same durability spectrum for cross-thread Long-Term/Entity Memory
- **Vector store persistence** — `persist_directory` for local Chroma, or a managed/hosted vector DB for multi-instance deployments
- **Backup/recovery procedures** — the ability to restore from a snapshot after catastrophic failure, tested, not assumed

---

## 3. Realistic Production/Enterprise Scenario

**Scenario: "Any of the previous 10 patterns' assistants — but now running in a real, multi-instance production deployment"**

Concretely, imagine SupportGenie (Pattern 4) or SalesCopilot (Pattern 7) deployed for real:

- The application runs as **multiple replicas** behind a load balancer (standard for any production web service, for both scale and zero-downtime deploys) — a user's request in one conversation might hit a *different* server instance than their previous request.
- Deploys happen weekly (or more often); every deploy restarts every instance's process memory.
- The company has an **RPO (Recovery Point Objective)** requirement — e.g., "we can tolerate losing at most 5 minutes of data in a disaster" — which is a concrete, auditable requirement that dictates backup frequency.
- An engineer needs to be able to say, with confidence, "if our database goes down entirely, here's exactly how we restore conversation and profile data, and here's how much (if any) we'd lose."

This scenario underlies *every other pattern in this series* the moment it's deployed for real, multi-instance, production traffic — which is precisely why Persistent Memory deserves its own dedicated treatment.

---

## 4. Architecture / Flow Diagram

```
        Multi-instance production deployment
   ┌───────────┐   ┌───────────┐   ┌───────────┐
   │ App        │   │ App        │   │ App        │      Any instance can
   │ instance A  │   │ instance B  │   │ instance C  │      serve any request --
   └─────┬─────┘   └─────┬─────┘   └─────┬─────┘      NO in-process state
         │                 │                 │              can be relied on.
         └─────────────────┼─────────────────┘
                            ▼
        ┌───────────────────────────────────────────┐
        │        Shared, durable persistence layer       │
        │                                                │
        │  ┌───────────────┐   ┌───────────────┐         │
        │  │  PostgresSaver   │   │  Postgres-backed │         │
        │  │  (checkpointer:   │   │  Store (long-term, │         │
        │  │  thread-scoped     │   │  cross-thread facts) │         │
        │  │  conversation       │   │                     │         │
        │  │  history)            │   │                     │         │
        │  └───────────────┘   └───────────────┘         │
        │                                                │
        │  ┌───────────────────────────────────┐          │
        │  │   Managed/hosted vector DB (or Chroma  │          │
        │  │   with a durable, backed-up volume)     │          │
        │  └───────────────────────────────────┘          │
        └───────────────────┬───────────────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │   Automated backup / snapshot process (e.g.    │
        │   nightly + continuous WAL archiving)           │
        │   -> restorable to a point in time                │
        └───────────────────────────────────────────┘
```

**Key idea:** persistence must be **shared across all instances**, not per-process — any instance must be able to pick up any user's conversation/state at any time. This rules out purely in-memory stores and even single-node SQLite files the moment you run more than one app instance.

---

## 5. Complete Request-to-Response Flow

**A request hitting a *different* instance than the previous turn, in a multi-instance deployment:**

1. **User's message 1** hits **App instance A**. `PostgresSaver` (the checkpointer) reads/writes conversation state for `thread_id="user_42"` from/to the shared Postgres database — not instance A's local memory.
2. **Instance A is redeployed/restarted** moments later (a routine deploy).
3. **User's message 2** happens to land on **App instance B** (load-balanced independently of instance A's lifecycle).
4. **Instance B's `PostgresSaver`** connects to the *same* shared Postgres database and correctly loads the full prior state for `thread_id="user_42"` — the conversation continues seamlessly, even though it's a completely different process than the one that handled message 1, and even though that first process no longer exists.
5. If the shared Postgres database itself needed to be restored from a backup (say, after a hardware failure), the **nightly snapshot + continuous write-ahead-log (WAL) archiving** would allow recovery to within seconds/minutes of the failure point — the RPO the business requires.

This flow is invisible to the user — it's purely an infrastructure property — but getting it wrong is one of the most damaging classes of production bugs for AI memory systems: silent, cumulative data loss that often isn't even noticed until a customer complains that "the bot forgot everything."

---

## 6. Why This Pattern Is Appropriate

| Requirement | How Persistent Memory satisfies it |
|---|---|
| Survive process restarts/deploys | Shared, external durable storage (Postgres/Redis), not process memory |
| Work correctly across multiple app instances | Any instance reads/writes the same shared backend — no per-process state |
| Meet business RPO/RTO requirements | Automated, tested backup + point-in-time recovery |
| Auditability | A durable store is queryable for compliance/support long after the original conversation |

**Trade-offs / when it's not enough:**
- Durable backends add **latency and operational complexity** compared to in-memory access — a Postgres round-trip on every turn is slower than a Python dict lookup; this is an accepted cost, but it should be measured and monitored, not assumed away.
- Persistence alone doesn't address **how long** to keep data — indefinite retention of conversation history or personal facts can become a liability (storage cost, privacy/compliance exposure). That's the job of **Memory Forgetting** (Pattern 16), which should be designed *together with* your persistence strategy, not bolted on later.
- A durable backend being reachable doesn't guarantee **data correctness** — you can persist stale or wrong data just as durably as fresh, correct data. Persistence is necessary but not sufficient; it must be paired with the correctness concerns other patterns address (e.g., External Memory, Pattern 10, for avoiding staleness in the first place).

---

## 7. Production-Quality Python Implementation

### Requirements

```bash
pip install -U langchain-ollama langgraph langchain-core langgraph-checkpoint-postgres psycopg[binary,pool]
# Requires a running Postgres instance, e.g.:
#   docker run --name memory-postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:16
# Ollama model used locally:
#   ollama pull llama3.1
```

### `persistent_memory.py`

```python
"""
Pattern 11: Persistent Memory
Production-durable checkpointing with a shared Postgres backend --
correct across multiple app instances and process restarts -- with
explicit backup/recovery hooks, built on LangGraph + langchain-ollama.

Run (requires a running Postgres instance -- see setup note above):
    python persistent_memory.py
"""

from __future__ import annotations

import logging
import subprocess
from contextlib import contextmanager
from typing import Annotated, TypedDict

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage
from langchain_ollama import ChatOllama
from langgraph.checkpoint.postgres import PostgresSaver
from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("persistent_memory")

# In production this comes from a secrets manager / environment variable,
# never hardcoded. Shown explicitly here for a runnable, self-contained demo.
POSTGRES_CONN_STRING = "postgresql://postgres:postgres@localhost:5432/postgres?sslmode=disable"


# --------------------------------------------------------------------------
# 1. Graph state -- identical shape to Pattern 1. What's different here is
#    ONLY the checkpointer backend: Postgres instead of SQLite, which is
#    what makes this correct across multiple app instances.
# --------------------------------------------------------------------------
class ConversationState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]


SYSTEM_PROMPT = SystemMessage(
    content="You are a helpful assistant. Use the conversation history to stay coherent."
)


def build_llm(model: str = "llama3.1", temperature: float = 0.3) -> ChatOllama:
    return ChatOllama(model=model, temperature=temperature, keep_alive="10m")


def make_chat_node(llm: ChatOllama):
    def chat_node(state: ConversationState) -> ConversationState:
        history = state["messages"]
        model_input = [SYSTEM_PROMPT, *history] if not history or not isinstance(history[0], SystemMessage) else history

        try:
            response: AIMessage = llm.invoke(model_input)
        except Exception:
            logger.exception("LLM call failed")
            response = AIMessage(content="Sorry, I hit an error. Please try again.")

        return {"messages": [response]}

    return chat_node


# --------------------------------------------------------------------------
# 2. Build a graph backed by a SHARED, durable Postgres checkpointer.
#    Any app instance connecting to the same Postgres database sees the
#    exact same conversation state -- this is what makes it correct in a
#    multi-instance deployment, unlike a per-process SQLite file.
# --------------------------------------------------------------------------
@contextmanager
def open_durable_graph(conn_string: str = POSTGRES_CONN_STRING):
    with PostgresSaver.from_conn_string(conn_string) as checkpointer:
        # `.setup()` creates the required tables if they don't exist yet --
        # safe to call on every startup (idempotent), the way a real
        # deployment would run it as part of app initialization/migrations.
        checkpointer.setup()

        llm = build_llm()
        graph_builder = StateGraph(ConversationState)
        graph_builder.add_node("chat_node", make_chat_node(llm))
        graph_builder.add_edge(START, "chat_node")
        graph_builder.add_edge("chat_node", END)

        graph = graph_builder.compile(checkpointer=checkpointer)
        yield graph


# --------------------------------------------------------------------------
# 3. Service wrapper. Notice: each "instance" below constructs its OWN
#    fresh connection to the SAME Postgres database -- simulating two
#    different app server processes/replicas.
# --------------------------------------------------------------------------
class PersistentMemoryService:
    def __init__(self, conn_string: str = POSTGRES_CONN_STRING):
        self.conn_string = conn_string

    def send(self, thread_id: str, user_message: str) -> str:
        """Each call opens its own connection -- simulating a stateless
        app server that could be ANY instance behind a load balancer."""
        with open_durable_graph(self.conn_string) as graph:
            config = {"configurable": {"thread_id": thread_id}}
            result = graph.invoke(
                {"messages": [HumanMessage(content=user_message)]},
                config=config,
            )
            return result["messages"][-1].content


# --------------------------------------------------------------------------
# 4. Backup / recovery hooks. In production these are automated (cron,
#    managed-Postgres snapshots, WAL archiving) -- shown explicitly here
#    so the durability story is concrete and testable, not assumed.
# --------------------------------------------------------------------------
def backup_database(backup_path: str = "backup.sql", conn_string: str = POSTGRES_CONN_STRING) -> None:
    """Simple logical backup via pg_dump. Real production setups typically
    ALSO enable continuous WAL archiving for point-in-time recovery, and
    run this on a schedule that meets the business's RPO requirement."""
    try:
        subprocess.run(
            ["pg_dump", conn_string, "-f", backup_path],
            check=True,
            capture_output=True,
        )
        logger.info("Backup written to %s", backup_path)
    except FileNotFoundError:
        logger.warning("pg_dump not available in this environment; skipping backup demo.")
    except subprocess.CalledProcessError as exc:
        logger.error("Backup failed: %s", exc.stderr.decode() if exc.stderr else exc)


def restore_database(backup_path: str = "backup.sql", conn_string: str = POSTGRES_CONN_STRING) -> None:
    """Restores from a logical backup. Tested restore procedures are just
    as important as backups -- an untested backup is not a real backup."""
    try:
        subprocess.run(
            ["psql", conn_string, "-f", backup_path],
            check=True,
            capture_output=True,
        )
        logger.info("Restored from %s", backup_path)
    except FileNotFoundError:
        logger.warning("psql not available in this environment; skipping restore demo.")
    except subprocess.CalledProcessError as exc:
        logger.error("Restore failed: %s", exc.stderr.decode() if exc.stderr else exc)


# --------------------------------------------------------------------------
# 5. Demo: two "instances" (simulated by fresh connections) serving the
#    SAME thread_id, proving state is correctly shared, not per-process.
# --------------------------------------------------------------------------
if __name__ == "__main__":
    service = PersistentMemoryService()
    thread_id = "user_42"

    print("--- 'Instance A' handles turn 1 ---")
    reply = service.send(thread_id, "My name is Priya, remember that for this conversation.")
    print("Bot:", reply)

    print("\n--- ('Instance A' process conceptually restarts/redeploys here) ---")

    print("\n--- 'Instance B' (a fresh connection/process) handles turn 2 ---")
    reply = service.send(thread_id, "What's my name?")
    print("Bot:", reply)
    print("(^ correct only because state lives in shared Postgres, not in either process's memory)")

    print("\n--- Backup demo ---")
    backup_database()
```

### Notes on production-readiness in this code

- **`PostgresSaver` instead of `SqliteSaver`** — this is the concrete change that makes the system correct under multiple app instances; SQLite files are local to a single machine/process and are the wrong choice the moment you scale beyond one node.
- **Fresh connection per call in the demo** (`open_durable_graph` used as a context manager inside `send`) deliberately simulates "any instance could handle any request" — proving state correctness doesn't depend on reusing the same in-process object. Real production code would typically hold a connection pool rather than reconnect every call, but the *correctness* property demonstrated is the same.
- **`checkpointer.setup()` is idempotent** and safe to call on every app startup — this is how real deployments handle schema migration for the checkpoint tables without manual intervention.
- **Explicit backup/restore functions**, not just a comment saying "back this up somehow" — `pg_dump`/`psql` here are the simplest correct baseline; production systems typically add continuous WAL archiving for tighter RPOs and use managed-database snapshot features where available.
- **Secrets handling note**: the connection string is a placeholder constant here for a runnable demo; production code must load it from a secrets manager or environment variable, never commit it to source control.

---

## 8. Version Pins (latest stable, compatible, at time of writing)

```
python                        >= 3.11
langchain-core                >= 0.3.0
langchain-ollama              >= 0.2.0
langgraph                     >= 0.2.0
langgraph-checkpoint-postgres >= 2.0.0
psycopg[binary,pool]          >= 3.2.0
```

```bash
pip install -U "langchain-core>=0.3.0" "langchain-ollama>=0.2.0" "langgraph>=0.2.0" "langgraph-checkpoint-postgres>=2.0.0" "psycopg[binary,pool]>=3.2.0"
```

> Requires a reachable Postgres instance (local Docker container is fine for development; a managed Postgres service — with automated backups enabled — is the production equivalent). `pg_dump`/`psql` must be on `PATH` for the backup/restore demo functions to run.

---

**Next up → Pattern 12: Working Memory** (the transient, in-flight scratchpad a single agent step reasons with — distinct from anything persisted — e.g., intermediate reasoning state during one multi-tool task).
