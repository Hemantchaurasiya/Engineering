# Pattern 12: Working Memory

> Part of the **Memory Patterns** series (Section 6). Stack: `langchain-ollama` (ChatOllama + OllamaEmbeddings), `langgraph`, local models (`llama3.1`, `nomic-embed-text`).

---

## 1. Introduce the Pattern

**Working Memory** is the **transient, in-flight scratchpad** an agent uses while working through a single, multi-step task — intermediate tool results, partial calculations, draft reasoning — that exists *only* for the duration of that one task and is **deliberately never persisted**.

This is the direct opposite number to Pattern 11 (Persistent Memory). Every other pattern in this series has been about making sure information *survives* — across turns, across sessions, across restarts. Working Memory is about the opposite discipline: recognizing that **not everything needs to survive**, and that trying to persist every intermediate scratch note would be both wasteful and actively harmful (bloating storage, leaking half-formed reasoning into future context, slowing down the system).

Think of it like a human analyst's scratch paper during a complex calculation: they jot down intermediate numbers, cross things out, try an approach, abandon it, try another — and once they've written the final report, the scratch paper goes in the recycling bin. Nobody archives every napkin calculation forever; only the final, validated output matters long-term.

---

## 2. Problem It Solves

Without a clear Working Memory concept, two opposite mistakes tend to happen:

**Mistake 1 — no scratchpad at all**, forcing the LLM to hold an entire multi-step task's intermediate state inside one giant reasoning pass, with no structured place to accumulate partial results between tool calls. This leads to lost intermediate values, inconsistent partial answers, and brittle multi-step agent behavior.

**Mistake 2 — persisting everything**, treating every intermediate tool call result, every draft calculation, every abandoned reasoning branch as if it were durable Conversation/Long-Term Memory:

```
[Bad: every scratch calculation gets checkpointed as "memory"]
Task: "Compare P/E ratios of 3 companies."
-> checkpoints: "fetched AAPL price: $190.20"
-> checkpoints: "fetched AAPL earnings: $6.10... wait, that's TTM not
   forward, let me refetch"
-> checkpoints: "fetched AAPL forward earnings: $6.45"
-> checkpoints: "calculated P/E: 29.5... double-checking with a
   different formula..."
-> ...dozens more scratch entries, permanently stored, per company,
   per query, forever.
```

This pollutes durable memory with noise nobody will ever want to retrieve, inflates storage costs, and — worse — risks a *future* retrieval (Pattern 4/9-style similarity search) surfacing a stale, abandoned intermediate calculation as if it were a validated fact.

Working Memory solves this by drawing a clean line: **state that only needs to exist within one task's execution lives in ephemeral, non-checkpointed graph state; only the validated final output (and any deliberately-extracted durable facts, via the other patterns) gets persisted.**

---

## 3. Realistic Production/Enterprise Scenario

**Scenario: "FinanceAnalyst" — a multi-step financial research agent**

- A user asks a compound question: *"Compare the P/E ratios of AAPL, MSFT, and GOOGL, and tell me which looks most undervalued relative to its 5-year average."*
- Answering this requires **multiple tool calls per company** (fetch current price, fetch earnings, fetch historical average) and **intermediate calculations** (compute P/E per company, compute deviation from historical average) before a final synthesis is possible.
- All of that intermediate work — raw fetched numbers, partial ratio calculations, per-company notes — is only useful *while assembling this one answer*. Nobody will ever want to query "what was the raw earnings figure fetched at 2:14pm during this specific analysis" a week later.
- The **final answer** (and perhaps a summary of the *conclusion* reached) might be worth keeping — e.g., logged for compliance, or fed into Episodic Memory (Pattern 5) as "on this date, ran this analysis, concluded X." But the scratch work itself should not consume durable storage or ever resurface in a future unrelated query.

This cleanly demonstrates Working Memory's purpose: **a rich, structured scratchpad for a single task's execution, explicitly separate from — and never promoted into — any durable memory store.**

---

## 4. Architecture / Flow Diagram

```
                    ┌───────────────────────────────────────────┐
                    │   User: "Compare P/E ratios of AAPL, MSFT,    │
                    │   and GOOGL vs their 5-year average."          │
                    └───────────────────┬───────────────────────────┘
                                          │
                                          ▼
        ╔═════════════════ EPHEMERAL: exists only for this run ═════════════════╗
        ║                                                                        ║
        ║  ┌─────────────────────┐                                              ║
        ║  │  fetch_data_node       │  for EACH company: calls tools,             ║
        ║  │                        │  appends raw results to                     ║
        ║  │                        │  state["scratchpad"]                         ║
        ║  └──────────┬─────────────┘                                              ║
        ║             ▼                                                            ║
        ║  ┌─────────────────────┐                                              ║
        ║  │  calculate_node         │  computes P/E + deviation per company,       ║
        ║  │                        │  appends working calculations to              ║
        ║  │                        │  state["scratchpad"] (NOT durable memory)      ║
        ║  └──────────┬─────────────┘                                              ║
        ║             ▼                                                            ║
        ║  ┌─────────────────────┐                                              ║
        ║  │  synthesize_node        │  LLM reads the FULL scratchpad, produces      ║
        ║  │                        │  ONE final, validated answer                   ║
        ║  └──────────┬─────────────┘                                              ║
        ╚═════════════┼══════════════════════════════════════════════════════════╝
                       │  only the FINAL ANSWER crosses this line
                       ▼
        ┌───────────────────────────────────────────┐
        │   (Optional) durable log: final answer only,   │
        │   e.g. via Episodic Memory (Pattern 5) --        │
        │   the scratchpad itself is discarded entirely     │
        │   the moment this graph run completes               │
        └───────────────────────────────────────────┘
```

**Key idea:** the double-line box marks the ephemeral boundary. `scratchpad` lives in the graph's runtime state for this single `invoke()` call and is **never passed to a checkpointer** — the graph in this pattern is deliberately compiled **without** persistence, because persisting Working Memory would defeat its purpose.

---

## 5. Complete Request-to-Response Flow

1. **User asks** the P/E comparison question.
2. **`fetch_data_node`** runs, once per company (AAPL, MSFT, GOOGL): it calls tool functions to get current price, current earnings, and 5-year average P/E, and **appends each raw result as a scratchpad entry** — e.g., `"AAPL: price=$190.20, earnings=$6.45, 5yr_avg_pe=27.8"`. These entries live only in `state["scratchpad"]` for this run.
3. **`calculate_node`** reads the raw scratchpad entries, computes each company's current P/E and its deviation from the 5-year average, and **appends these working calculations** to the same scratchpad — e.g., `"AAPL: current P/E=29.5, vs 5yr avg 27.8 -> +6.1% above average (less undervalued)"`.
4. **`synthesize_node`** is the only node that calls the LLM for a natural-language answer — it receives the **entire scratchpad** (all raw fetches + all calculations, for all three companies) as context, and produces one clear, final comparison: *"GOOGL looks most undervalued, trading 8% below its 5-year average P/E, while AAPL and MSFT are both trading above their averages."*
5. **The graph run ends.** Because this graph was compiled **without a checkpointer**, `state["scratchpad"]` — all those raw fetches and intermediate calculations — is **not written anywhere durable**. It existed only in memory during this one execution and is now gone.
6. **Only the final answer** is returned to the user (and, optionally, a calling system could choose to log *that final answer* into Episodic Memory — a deliberate, separate decision, not an automatic side effect of Working Memory).

---

## 6. Why This Pattern Is Appropriate

| Requirement | How Working Memory satisfies it |
|---|---|
| Support complex multi-step reasoning within one task | A structured scratchpad lets the agent accumulate intermediate results reliably across steps |
| Avoid polluting durable memory with scratch noise | The scratchpad is never checkpointed — it's gone once the task completes |
| Keep durable retrieval (Patterns 4/9) clean | No abandoned/intermediate calculations can ever surface in a future similarity search |
| Lower storage costs at scale | Only final, validated outputs are ever written to durable storage |

**Trade-offs / when it's not enough:**
- If a multi-step task is **interrupted** (process crash mid-execution), an ephemeral, non-checkpointed scratchpad is lost entirely — for long-running, expensive multi-step agent tasks where resuming from partway through matters, you may want *some* intermediate checkpointing (a hybrid: checkpoint the scratchpad itself, but still never promote it into long-term/semantic memory once the task concludes).
- Deciding **what, if anything, graduates from working memory into durable memory** is a deliberate design choice, not automatic — this pattern deliberately does *not* auto-promote scratchpad content; pairing it with Patterns 3/5/7/8 for the specific facts/episodes worth keeping is a separate, explicit step.
- Debugging a Working Memory-based agent's behavior after the fact is harder precisely because the scratchpad is gone — production systems often add **short-lived, non-durable structured logging** (e.g., to an observability system with its own retention policy, distinct from the AI application's memory stores) purely for debugging, without treating those logs as "memory" the agent can retrieve from.

---

## 7. Production-Quality Python Implementation

### Requirements

```bash
pip install -U langchain-ollama langgraph langchain-core
# Ollama model used locally:
#   ollama pull llama3.1
```

### `working_memory.py`

```python
"""
Pattern 12: Working Memory
A transient, in-flight scratchpad for multi-step reasoning within a
SINGLE task, deliberately never checkpointed/persisted, built on
LangGraph + langchain-ollama.

Run:
    python working_memory.py
"""

from __future__ import annotations

import logging
from typing import TypedDict

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_ollama import ChatOllama
from langgraph.graph import StateGraph, START, END

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("working_memory")


# --------------------------------------------------------------------------
# 1. Simulated tools for fetching financial data. In production these
#    would call a real market-data API.
# --------------------------------------------------------------------------
_MOCK_MARKET_DATA = {
    "AAPL": {"price": 190.20, "earnings_per_share": 6.45, "five_year_avg_pe": 27.8},
    "MSFT": {"price": 415.10, "earnings_per_share": 11.65, "five_year_avg_pe": 32.4},
    "GOOGL": {"price": 178.30, "earnings_per_share": 7.90, "five_year_avg_pe": 24.0},
}


def fetch_market_data(ticker: str) -> dict:
    logger.info("Tool call: fetching market data for %s", ticker)
    return _MOCK_MARKET_DATA[ticker]


# --------------------------------------------------------------------------
# 2. Graph state. NOTE: `scratchpad` is the Working Memory field -- it is
#    a normal piece of state for the duration of one graph run, but this
#    graph is compiled WITHOUT a checkpointer, so nothing in this state
#    (including the scratchpad) is ever written to durable storage.
# --------------------------------------------------------------------------
class AnalysisState(TypedDict):
    tickers: list[str]
    scratchpad: list[str]   # ephemeral working notes -- never persisted
    final_answer: str


def build_llm(model: str = "llama3.1", temperature: float = 0.2) -> ChatOllama:
    return ChatOllama(model=model, temperature=temperature, keep_alive="10m")


# --------------------------------------------------------------------------
# 3. Node: fetch raw data for each ticker, recording each result as a
#    scratchpad entry.
# --------------------------------------------------------------------------
def fetch_data_node(state: AnalysisState) -> AnalysisState:
    notes = []
    for ticker in state["tickers"]:
        data = fetch_market_data(ticker)
        note = (
            f"{ticker}: price=${data['price']:.2f}, "
            f"EPS=${data['earnings_per_share']:.2f}, "
            f"5yr_avg_PE={data['five_year_avg_pe']:.1f}"
        )
        notes.append(note)
        logger.info("Working memory += %r", note)

    return {"scratchpad": state.get("scratchpad", []) + notes}


# --------------------------------------------------------------------------
# 4. Node: compute intermediate values, appending MORE scratch notes on
#    top of the raw fetches -- still entirely within working memory.
# --------------------------------------------------------------------------
def calculate_node(state: AnalysisState) -> AnalysisState:
    notes = []
    for ticker in state["tickers"]:
        data = _MOCK_MARKET_DATA[ticker]  # in a real system, re-derive from scratchpad/tool cache
        current_pe = data["price"] / data["earnings_per_share"]
        avg_pe = data["five_year_avg_pe"]
        deviation_pct = ((current_pe - avg_pe) / avg_pe) * 100

        direction = "above" if deviation_pct > 0 else "below"
        note = (
            f"{ticker}: current P/E={current_pe:.1f}, 5yr avg={avg_pe:.1f} "
            f"-> {abs(deviation_pct):.1f}% {direction} average"
        )
        notes.append(note)
        logger.info("Working memory += %r", note)

    return {"scratchpad": state["scratchpad"] + notes}


# --------------------------------------------------------------------------
# 5. Node: the ONLY node that produces user-facing output. It reads the
#    full scratchpad but nothing from it survives past this function
#    call except whatever ends up in `final_answer`.
# --------------------------------------------------------------------------
SYNTHESIS_PROMPT = (
    "You are FinanceAnalyst. Given the following working notes from a "
    "multi-step P/E ratio analysis, write a clear, concise comparison "
    "identifying which company looks most undervalued relative to its "
    "5-year average P/E, and briefly explain why."
)


def make_synthesize_node(llm: ChatOllama):
    def synthesize_node(state: AnalysisState) -> AnalysisState:
        scratchpad_text = "\n".join(state["scratchpad"])
        messages = [
            SystemMessage(content=SYNTHESIS_PROMPT),
            HumanMessage(content=f"Working notes:\n{scratchpad_text}"),
        ]

        try:
            response = llm.invoke(messages)
            answer = response.content
        except Exception:
            logger.exception("LLM call failed")
            answer = "Sorry, I hit an error synthesizing the analysis."

        return {"final_answer": answer}

    return synthesize_node


# --------------------------------------------------------------------------
# 6. Assemble the graph -- deliberately WITHOUT a checkpointer. This is
#    the key design decision that makes `scratchpad` genuine Working
#    Memory rather than accidentally-durable Conversation Memory.
# --------------------------------------------------------------------------
def build_graph():
    llm = build_llm()

    graph_builder = StateGraph(AnalysisState)
    graph_builder.add_node("fetch_data_node", fetch_data_node)
    graph_builder.add_node("calculate_node", calculate_node)
    graph_builder.add_node("synthesize_node", make_synthesize_node(llm))

    graph_builder.add_edge(START, "fetch_data_node")
    graph_builder.add_edge("fetch_data_node", "calculate_node")
    graph_builder.add_edge("calculate_node", "synthesize_node")
    graph_builder.add_edge("synthesize_node", END)

    # NOTE: no `checkpointer=` argument here, intentionally.
    return graph_builder.compile()


# --------------------------------------------------------------------------
# 7. Service wrapper
# --------------------------------------------------------------------------
class WorkingMemoryService:
    def __init__(self):
        self.graph = build_graph()

    def analyze(self, tickers: list[str]) -> dict:
        """Returns both the final answer AND the (soon-to-be-discarded)
        scratchpad, purely so the demo can show what Working Memory
        looked like during execution -- a real caller would typically
        only use `final_answer`."""
        result = self.graph.invoke({"tickers": tickers, "scratchpad": [], "final_answer": ""})
        return result


# --------------------------------------------------------------------------
# 8. Demo: run one multi-step analysis, showing the scratchpad build up
#    and then confirming nothing from it persists to a second, unrelated
#    invocation.
# --------------------------------------------------------------------------
if __name__ == "__main__":
    service = WorkingMemoryService()

    print("--- Running multi-step P/E analysis ---")
    result = service.analyze(["AAPL", "MSFT", "GOOGL"])

    print("\nWorking memory used during this run (would normally be discarded, shown here for illustration):")
    for note in result["scratchpad"]:
        print(" -", note)

    print("\nFinal answer (the only thing that would normally leave this function):")
    print(result["final_answer"])

    print("\n--- A second, unrelated invocation ---")
    result2 = service.analyze(["AAPL"])
    print(f"New run's scratchpad starts fresh, length={len(result2['scratchpad'])} "
          f"(previous run's notes were never carried over or persisted anywhere).")
```

### Notes on production-readiness in this code

- **No checkpointer, by design** — `build_graph()` deliberately omits `checkpointer=`, which is the concrete mechanism that keeps `scratchpad` genuinely ephemeral. This is the single most important line (or rather, the absence of one) in this pattern's implementation.
- **Clear separation of "working" vs. "final"** — `scratchpad` (many entries, intermediate, disposable) vs. `final_answer` (one value, the only thing meant to leave this graph run) makes the ephemeral/durable boundary explicit in the state schema itself, not just a convention.
- **Each node appends, never mutates history** — `calculate_node` builds on `fetch_data_node`'s notes rather than overwriting them, mirroring how a human analyst's scratch pad accumulates across steps of one calculation.
- **The demo intentionally exposes the scratchpad** (`return result` includes it) purely for pedagogical visibility into what Working Memory looked like — a real production `analyze()` method would typically return only `final_answer`, reinforcing that the scratchpad's contents are not meant to leave the task boundary.
- **If resumability is needed** for long/expensive multi-step tasks, the correct extension is to checkpoint the *scratchpad* for crash-recovery purposes while *still* never promoting it into Semantic/Episodic/Entity memory once the task concludes — those are two independent decisions.

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

> No checkpointer package is required for this pattern's core implementation, since persistence is deliberately excluded — that omission is the point.

---

**Next up → Pattern 13: Memory Retrieval** (a focused, cross-cutting look at *how* to retrieve well from any of the durable stores in this series — hybrid search, re-ranking, and relevance tuning beyond plain top-k similarity).
