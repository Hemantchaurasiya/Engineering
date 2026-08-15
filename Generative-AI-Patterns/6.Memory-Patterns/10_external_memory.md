# Pattern 10: External Memory

> Part of the **Memory Patterns** series (Section 6). Stack: `langchain-ollama` (ChatOllama + OllamaEmbeddings), `langgraph`, local models (`llama3.1`, `nomic-embed-text`).

---

## 1. Introduce the Pattern

**External Memory** means treating an **existing system of record outside the LLM pipeline** — a production database, an internal API, a file system, a knowledge graph — *as the memory itself*, queried live via tool calls, rather than copying its data into a vector store or app-managed cache.

Every pattern so far (Conversation, Long-Term, Semantic, Episodic, Procedural, Entity, Vector-Based) stores memory **inside** infrastructure the AI application owns and controls (a checkpointer, a Store, a Chroma collection). External Memory is different: the "memory" already exists, is already the source of truth, and is owned by *another* system — an orders database, a CRM, an HR system. Instead of duplicating that data into the AI app, the agent **calls tools that query the external system directly**, on demand, every time it needs that information.

This is memory-via-**tool-calling**: the LLM decides *when* it needs certain information, calls a tool (e.g., `get_order_status(order_id)`), gets a live, authoritative answer back, and reasons with it — no embedding, no local copy, no staleness.

---

## 2. Problem It Solves

The other patterns in this series all involve the AI application **copying** data into its own memory infrastructure — which introduces a real production hazard: **staleness**.

```
[Vector-Based Memory approach for order data]
Monday:    Order ORD-88213 status embedded into memory: "processing"
Tuesday:   Order actually ships in the real orders database.
Wednesday: User asks "where's my order?"
Bot:       "Your order is still processing." <-- WRONG. The copy is stale;
           the real database says "shipped" but nobody re-ingested it.
```

For any data that **changes independently of the conversation** — order status, account balance, inventory levels, employee records, ticket status — copying it into an AI-managed store is actively dangerous: the copy silently drifts out of sync with reality, and there's no natural trigger to refresh it.

External Memory solves this by **never copying** that data at all. The agent queries the live system every time it's relevant, guaranteeing the answer is as fresh as the source of truth itself. The trade-off is latency/availability dependency on the external system, which is an acceptable and often *necessary* trade for correctness on this class of data.

---

## 3. Realistic Production/Enterprise Scenario

**Scenario: "AccountAssist" — a customer support assistant answering order/account questions**

- Customers ask about order status, shipping estimates, and account details — all of which live in the company's **existing orders database**, updated continuously by warehouse, shipping, and billing systems completely outside the AI application's control.
- This data changes **minute to minute**, driven by systems the AI has no visibility into (a package actually leaves the warehouse; a payment actually clears). Any local copy would be wrong within minutes.
- Compliance also matters here: for account/financial data, the company wants a **single source of truth** — the actual database — with the AI never able to "invent" or serve stale duplicated data, especially for anything that could affect a refund or billing decision.
- The team already has a well-tested, secured internal API/database layer for order data — the right engineering move is to let the AI **call into it**, not rebuild a shadow copy.

This is the textbook External Memory case: **data whose staleness would cause real business harm, already served correctly by an existing system — so the AI queries it live instead of duplicating it.**

---

## 4. Architecture / Flow Diagram

```
                    ┌───────────────────────────────────────────┐
                    │   Client: "Where's my order ORD-88213?"       │
                    └───────────────────┬───────────────────────────┘
                                          │
                                          ▼
                    ┌───────────────────────────────────────────┐
                    │              LangGraph StateGraph             │
                    │                                                │
                    │  ┌─────────────────────┐                      │
                    │  │  agent_node             │───▶ ChatOllama       │
                    │  │  (LLM with tools bound)  │    decides: "I need   │
                    │  │                          │    to call             │
                    │  │                          │    get_order_status"    │
                    │  └──────────┬─────────────┘                      │
                    │             │  tool_call requested                  │
                    │      has tool calls? ─────────────┐                 │
                    │             │ yes                  │ no               │
                    │             ▼                       ▼                 │
                    │  ┌─────────────────────┐    (return final answer)    │
                    │  │  tool_node              │                             │
                    │  │  executes                │                             │
                    │  │  get_order_status(...)     │                             │
                    │  │  -> LIVE query to the       │                             │
                    │  │     orders database          │                             │
                    │  └──────────┬─────────────┘                             │
                    │             │  tool result (fresh data)                    │
                    │             ▼                                              │
                    │      back to agent_node (loop)                              │
                    └───────────────────────────────────────────┘
                                          │
                                          ▼
                    ┌───────────────────────────────────────────┐
                    │        Orders Database (external system)      │
                    │   -- owned and updated by OTHER systems --      │
                    │   -- the AI app has NO local copy of this --     │
                    │  ORD-88213 -> {status: "shipped", eta: "2 days"}  │
                    └───────────────────────────────────────────┘
```

**Key idea:** there is **no vector store, no checkpointer of order data, no embedding step** for the memory itself in this pattern — the "memory" is the external database, queried fresh via a tool call every single time. (A checkpointer may still be used for the *conversation* history, per Pattern 1 — that's orthogonal to this pattern.)

---

## 5. Complete Request-to-Response Flow

1. **Client sends** `"Where's my order ORD-88213?"`
2. **`agent_node`** calls `ChatOllama` with tools bound (`get_order_status`, `get_shipping_eta`, `get_account_balance`). The model recognizes it needs order data it doesn't have and **requests a tool call**: `get_order_status(order_id="ORD-88213")`.
3. **Routing logic** detects the response contains tool calls and routes to **`tool_node`**.
4. **`tool_node`** executes `get_order_status("ORD-88213")` — this function makes a **live query** against the orders database (in this implementation, a SQLite table standing in for a real production database/API), returning the *current* status: `{"status": "shipped", "carrier": "FedEx", "eta": "2 days"}`.
5. This tool result is appended to the message list as a `ToolMessage`, and the graph loops **back to `agent_node`**.
6. **`agent_node`** now has the live data and calls `ChatOllama` again — this time it has everything it needs and produces a final natural-language answer: *"Your order ORD-88213 has shipped via FedEx and is expected in 2 days."*
7. **No tool calls this time** → routing sends the graph to `END`, and the answer is returned.

If the customer asks again five minutes later, after the warehouse updates the real database to "delivered," step 4 will return the **new** live status automatically — there's no stale copy anywhere to cause a wrong answer.

---

## 6. Why This Pattern Is Appropriate

| Requirement | How External Memory satisfies it |
|---|---|
| Always-current answers for fast-changing data | Every query hits the live system of record, never a copy |
| Single source of truth / compliance | No duplicate, potentially-diverging data store to reconcile or audit |
| Leverages existing, trusted infrastructure | Reuses the company's already-secured database/API instead of rebuilding it |
| Avoids the "staleness" failure mode entirely | There is nothing to go stale — nothing is cached |

**Trade-offs / when it's not enough:**
- **Latency and availability coupling** — every relevant question now depends on the external system being up and reasonably fast; if the orders database is slow or down, the assistant can't answer, whereas a cached/vector copy (with its staleness risk) would still respond. Production systems need timeouts, retries, and graceful degradation messaging for this dependency.
- **Not suitable for data that benefits from semantic search** — External Memory is best for data reachable via exact lookups (an order ID, an account ID); it's a poor fit for "find the note that talks about X" style queries, which is exactly what Vector-Based Memory (Pattern 9) is for. Most production systems use **both**: External Memory for live transactional data, Vector-Based/Semantic Memory for unstructured knowledge.
- **Tool-call reliability matters** — the LLM must reliably decide *when* to call a tool vs. answer from its own reasoning; poorly designed tool descriptions can cause the model to skip a needed lookup (and guess) or call tools unnecessarily. This is a genuine engineering surface that needs testing, unlike a simple always-inject-context pattern.

---

## 7. Production-Quality Python Implementation

### Requirements

```bash
pip install -U langchain-ollama langgraph langchain-core
# Ollama model used locally (must support tool/function calling):
#   ollama pull llama3.1
```

### `external_memory.py`

```python
"""
Pattern 10: External Memory
Treating a live external system of record (here, a SQLite-backed orders
database standing in for a real production DB/API) as memory, queried
on demand via tool calls rather than copied locally, built on
LangGraph + langchain-ollama's tool-calling support.

Run:
    python external_memory.py
"""

from __future__ import annotations

import logging
import sqlite3
from typing import Annotated, TypedDict

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, ToolMessage
from langchain_core.tools import tool
from langchain_ollama import ChatOllama
from langgraph.graph import StateGraph, START, END
from langgraph.graph.message import add_messages
from langgraph.prebuilt import ToolNode

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("external_memory")


# --------------------------------------------------------------------------
# 1. Simulated EXTERNAL system of record. In production this would be a
#    real orders database / internal REST API owned by another team --
#    the AI application does NOT own or copy this data.
# --------------------------------------------------------------------------
def _seed_external_orders_db() -> sqlite3.Connection:
    conn = sqlite3.connect(":memory:", check_same_thread=False)
    conn.execute(
        """CREATE TABLE orders (
            order_id TEXT PRIMARY KEY,
            status TEXT,
            carrier TEXT,
            eta_days INTEGER,
            account_id TEXT
        )"""
    )
    conn.execute(
        "INSERT INTO orders VALUES (?, ?, ?, ?, ?)",
        ("ORD-88213", "shipped", "FedEx", 2, "acct_42"),
    )
    conn.execute(
        "INSERT INTO orders VALUES (?, ?, ?, ?, ?)",
        ("ORD-91004", "processing", None, None, "acct_42"),
    )
    conn.commit()
    return conn


EXTERNAL_DB = _seed_external_orders_db()


def _simulate_status_update():
    """Simulates the warehouse system updating order status independently
    of the AI app -- proving External Memory always reflects live state."""
    EXTERNAL_DB.execute("UPDATE orders SET status = 'delivered' WHERE order_id = 'ORD-88213'")
    EXTERNAL_DB.commit()
    logger.info("[external system] ORD-88213 status updated to 'delivered' by the warehouse system")


# --------------------------------------------------------------------------
# 2. Tools: these are the ONLY way the agent can access order data.
#    Each call hits the live external system fresh -- no local caching.
# --------------------------------------------------------------------------
@tool
def get_order_status(order_id: str) -> str:
    """Look up the CURRENT, live status of an order by its order ID.
    Always returns the latest data from the orders system of record."""
    row = EXTERNAL_DB.execute(
        "SELECT status, carrier, eta_days FROM orders WHERE order_id = ?", (order_id,)
    ).fetchone()
    if row is None:
        return f"No order found with ID {order_id}."
    status, carrier, eta_days = row
    logger.info("Live query -> order_id=%s status=%s", order_id, status)
    if status == "shipped":
        return f"Order {order_id} status: shipped via {carrier}, ETA {eta_days} day(s)."
    if status == "delivered":
        return f"Order {order_id} status: delivered."
    return f"Order {order_id} status: {status}."


@tool
def list_account_orders(account_id: str) -> str:
    """List all order IDs and current statuses for a given account ID,
    queried live from the orders system of record."""
    rows = EXTERNAL_DB.execute(
        "SELECT order_id, status FROM orders WHERE account_id = ?", (account_id,)
    ).fetchall()
    if not rows:
        return f"No orders found for account {account_id}."
    logger.info("Live query -> account_id=%s returned %d order(s)", account_id, len(rows))
    return "; ".join(f"{oid}: {status}" for oid, status in rows)


TOOLS = [get_order_status, list_account_orders]


# --------------------------------------------------------------------------
# 3. Graph state
# --------------------------------------------------------------------------
class SupportState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]


SYSTEM_PROMPT = (
    "You are AccountAssist, a customer support assistant. You have NO "
    "built-in knowledge of any order or account status -- you MUST use the "
    "provided tools to look up live data before answering any question "
    "about an order or account. Never guess or make up a status."
)


def build_llm(model: str = "llama3.1", temperature: float = 0.0) -> ChatOllama:
    return ChatOllama(model=model, temperature=temperature, keep_alive="10m")


# --------------------------------------------------------------------------
# 4. Node: the agent, with tools bound, deciding whether it needs to
#    query external memory before it can answer.
# --------------------------------------------------------------------------
def make_agent_node(llm_with_tools):
    from langchain_core.messages import SystemMessage

    def agent_node(state: SupportState) -> SupportState:
        messages = state["messages"]
        if not messages or not isinstance(messages[0], SystemMessage):
            messages = [SystemMessage(content=SYSTEM_PROMPT), *messages]

        try:
            response: AIMessage = llm_with_tools.invoke(messages)
        except Exception:
            logger.exception("LLM call failed")
            response = AIMessage(content="Sorry, I hit an error reaching my systems. Please try again.")

        return {"messages": [response]}

    return agent_node


def route_on_tool_calls(state: SupportState) -> str:
    last_message = state["messages"][-1]
    if isinstance(last_message, AIMessage) and last_message.tool_calls:
        return "tool_node"
    return END


# --------------------------------------------------------------------------
# 5. Assemble the graph: agent <-> tool_node loop, ending once the agent
#    responds with no further tool calls needed.
# --------------------------------------------------------------------------
def build_graph():
    llm = build_llm()
    llm_with_tools = llm.bind_tools(TOOLS)

    graph_builder = StateGraph(SupportState)
    graph_builder.add_node("agent_node", make_agent_node(llm_with_tools))
    graph_builder.add_node("tool_node", ToolNode(TOOLS))

    graph_builder.add_edge(START, "agent_node")
    graph_builder.add_conditional_edges(
        "agent_node", route_on_tool_calls, {"tool_node": "tool_node", END: END}
    )
    graph_builder.add_edge("tool_node", "agent_node")  # loop back after a tool result

    return graph_builder.compile()


# --------------------------------------------------------------------------
# 6. Service wrapper
# --------------------------------------------------------------------------
class ExternalMemoryService:
    def __init__(self):
        self.graph = build_graph()

    def ask(self, question: str) -> str:
        result = self.graph.invoke({"messages": [HumanMessage(content=question)]})
        return result["messages"][-1].content


# --------------------------------------------------------------------------
# 7. Demo: ask about an order, simulate the EXTERNAL system changing its
#    status independently, then ask again and confirm the answer is fresh.
# --------------------------------------------------------------------------
if __name__ == "__main__":
    service = ExternalMemoryService()

    print("--- First question ---")
    print("User:", "Where's my order ORD-88213?")
    print("Bot: ", service.ask("Where's my order ORD-88213?"))

    print("\n--- Warehouse system updates the order status (outside the AI app entirely) ---")
    _simulate_status_update()

    print("\n--- Same question, asked again ---")
    print("User:", "Where's my order ORD-88213?")
    print("Bot: ", service.ask("Where's my order ORD-88213?"))
    print("(^ must reflect the NEW live status -- there is no local copy to go stale)")

    print("\n--- A different lookup, by account ---")
    print("User:", "What orders do I have on account acct_42?")
    print("Bot: ", service.ask("What orders do I have on account acct_42?"))
```

### Notes on production-readiness in this code

- **No local copy of order data anywhere** — every answer about an order is produced via a fresh tool call against `EXTERNAL_DB`; the demo explicitly proves this by mutating the "external" data mid-run and showing the next answer reflects it immediately.
- **`llm.bind_tools(TOOLS)` + `ToolNode`** is the standard, current LangGraph/langchain-ollama pattern for tool-calling agents — the LLM decides when a lookup is needed, rather than the application always injecting data preemptively (contrast with Patterns 3/4/7, which proactively inject stored memory every turn).
- **Explicit "never guess" system instruction** is critical for this pattern — an LLM that answers order-status questions from "plausible-sounding" reasoning instead of calling the tool would silently reintroduce the exact staleness/fabrication risk this pattern exists to eliminate.
- **Agent-tool loop** (`agent_node -> tool_node -> agent_node -> ... -> END`) supports multi-step lookups (e.g., first listing account orders, then checking status on each) without hardcoding a fixed call sequence.
- **Real systems would add**: request timeouts around the external call, retries with backoff, and a clear fallback message ("I'm unable to check live order status right now") if the external system is unavailable — a dependency this pattern deliberately accepts in exchange for correctness.

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

> Requires an Ollama model with tool/function-calling support — `llama3.1` supports this. `ToolNode` and `bind_tools` are core `langgraph` / `langchain-core` features; no extra vector-store dependency is needed since this pattern deliberately avoids embeddings.

---

**Next up → Pattern 11: Persistent Memory** (a focused, cross-cutting look at *durability guarantees* — checkpointer/store backends, backup, and recovery — that every stateful pattern in this series ultimately depends on).
