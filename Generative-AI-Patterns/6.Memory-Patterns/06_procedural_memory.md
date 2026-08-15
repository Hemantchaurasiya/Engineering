# Pattern 6: Procedural Memory

> Part of the **Memory Patterns** series (Section 6). Stack: `langchain-ollama` (ChatOllama + OllamaEmbeddings), `langgraph`, `langchain-chroma`, local models (`llama3.1`, `nomic-embed-text`).

---

## 1. Introduce the Pattern

**Procedural Memory** stores **how to do things** — verified, ordered sequences of steps/tool-calls that reliably accomplish a recurring task — so the system can *execute a known-good procedure* instead of re-planning from scratch every single time.

This completes the trio started in Patterns 4 and 5:

| | Semantic Memory | Episodic Memory | Procedural Memory |
|---|---|---|---|
| Stores | A fact | A specific past event | A reusable *skill / workflow* |
| Example | "This service uses Postgres 15" | "On June 2, a pool leak caused an outage; bumping pool size fixed it" | "To restart a stuck worker: (1) drain queue, (2) scale to 0, (3) scale to N, (4) verify health check" |
| Answers | "What do we know?" | "What happened before?" | "What's the *proven sequence of steps* to do X?" |

Procedural Memory is exactly what separates a system that "figures things out" every time from one that **gets faster and more reliable** at recurring tasks, the same way a human expert doesn't re-derive a checklist from first principles every time they perform a familiar operation — they just run the playbook.

---

## 2. Problem It Solves

Without Procedural Memory, an *agentic* system (one that plans and executes multi-step tool-using tasks) re-plans every task from zero, even ones it has already solved correctly many times:

```
[Monday]   Task: "Restart the stuck payment-worker"
Agent:     Plans: check logs -> drain queue -> scale to 0 -> scale to N -> verify health
           Executes successfully in 6 steps, 40 seconds of LLM planning overhead.

[Tuesday]  Task: "Restart the stuck payment-worker" (same task again!)
Agent:     Plans from scratch again: check logs -> drain queue -> scale to 0 -> ...
           Same 40 seconds of planning overhead, same risk of a slightly different
           (possibly worse, possibly unsafe) plan being generated this time.
```

This has three real costs in production agentic systems:
1. **Wasted latency/cost** — re-planning a well-known task via LLM calls every time is slower and more expensive than just replaying a verified sequence.
2. **Inconsistent behavior** — a stochastic planner (even at low temperature) can generate a *slightly different* plan each time, including occasionally worse or riskier orderings of steps.
3. **No compounding improvement** — the system never "gets better" at its job; every execution is independent, with no mechanism to reuse validated successes.

Procedural Memory solves this by:
- **Storing successful step sequences** as reusable "procedures," keyed/matched by task type
- **Matching new incoming tasks** to an existing procedure (via exact key or semantic similarity) before falling back to full LLM planning
- **Executing the stored procedure directly** — deterministic, fast, and previously validated
- **Falling back to LLM planning** only for genuinely novel tasks, and **saving successful new plans** as procedures for next time

---

## 3. Realistic Production/Enterprise Scenario

**Scenario: "OpsAgent" — an agentic DevOps assistant that performs operational tasks via tools**

- Engineers ask OpsAgent to do routine operational tasks: restart a stuck worker, rotate a credential, scale a deployment, clear a cache.
- Many of these tasks are **recurring** — "restart the stuck payment-worker" happens a few times a month, always solved the same correct way.
- Re-planning via LLM every time is slower, costs more inference, and introduces small variance risk into an operational action that should be **consistent and predictable**.
- The team wants OpsAgent to behave like a senior engineer who has internalized the runbooks: recognize a familiar task instantly and execute the known-good sequence, while still being able to handle a **novel** task by reasoning through it — and, critically, **save that new solution as a procedure** once it's verified to work.

This is exactly Procedural Memory's purpose: **turn validated action sequences into reusable, fast, deterministic playbooks**, with LLM planning reserved for genuinely new situations.

---

## 4. Architecture / Flow Diagram

```
                         ┌───────────────────────────────────────────┐
                         │   Client: "restart the stuck payment-worker" │
                         └───────────────────┬───────────────────────────┘
                                              │
                                              ▼
                         ┌───────────────────────────────────────────┐
                         │              LangGraph StateGraph             │
                         │                                                │
                         │  ┌─────────────────────┐                      │
                         │  │ match_procedure_node   │  embeds task,        │
                         │  │                        │  looks up procedure  │
                         │  │                        │  store by similarity │
                         │  └──────────┬─────────────┘                      │
                         │             │                                     │
                         │      MATCH FOUND?  ──────────────┐                │
                         │             │ yes                 │ no             │
                         │             ▼                     ▼                │
                         │  ┌─────────────────┐   ┌───────────────────────┐  │
                         │  │ execute_known_     │   │  plan_with_llm_node    │  │
                         │  │ procedure_node      │   │  (ChatOllama plans      │  │
                         │  │ (replay stored       │   │   steps for novel task) │  │
                         │  │  step sequence)       │   └──────────┬──────────────┘  │
                         │  └──────────┬─────────┘                │ execute steps      │
                         │             │                            ▼                    │
                         │             │                 ┌───────────────────────┐      │
                         │             │                 │  execute_planned_        │      │
                         │             │                 │  procedure_node           │      │
                         │             │                 └──────────┬──────────────┘      │
                         │             │                            │ if ALL steps succeed  │
                         │             │                            ▼                        │
                         │             │                 ┌───────────────────────┐          │
                         │             │                 │  save_procedure_node      │          │
                         │             │                 │  (persist as reusable      │          │
                         │             │                 │   procedure for next time)  │          │
                         │             │                 └──────────┬──────────────┘          │
                         │             └────────────────────────────┘                            │
                         └───────────────────┬───────────────────────────────────────────────────┘
                                              ▼
                         ┌───────────────────────────────────────────┐
                         │        Chroma vector store (persistent)      │
                         │  collection: "procedures"                     │
                         │  {task: "...", steps: [...], success_count}    │
                         └───────────────────────────────────────────┘
```

**Key idea:** a **match-first** decision point. Known tasks skip planning entirely and go straight to deterministic execution; only unmatched, novel tasks pay the cost of LLM planning — and even then, a successful outcome is captured as a *new* procedure so the system never pays that cost twice for the same task.

---

## 5. Complete Request-to-Response Flow

**Case A — a familiar task:**

1. **Engineer sends** `"restart the stuck payment-worker"`.
2. **`match_procedure_node`** embeds this task description and searches the `procedures` collection. It finds a stored procedure (from a prior successful run) with high similarity: `["check_logs", "drain_queue", "scale_to_zero", "scale_to_n", "verify_health"]`.
3. Because a confident match was found, the graph routes to **`execute_known_procedure_node`**, which replays those steps in order via the registered tool functions — no LLM planning call needed at all.
4. Each step's result is checked; if all succeed, the procedure's `success_count` is incremented (reinforcing confidence in it for future matching/ranking).
5. **Response returned**: "Restarted payment-worker using the known runbook (5/5 steps succeeded)."

**Case B — a novel task:**

1. **Engineer sends** `"purge the stale entries from the geo-ip cache"` — nothing similar exists yet.
2. **`match_procedure_node`** finds no sufficiently similar procedure (similarity below threshold).
3. Graph routes to **`plan_with_llm_node`**: `ChatOllama` is prompted (with the list of available tools) to produce an ordered JSON plan of steps.
4. **`execute_planned_procedure_node`** executes that plan via the same tool functions.
5. If **all** steps succeed, **`save_procedure_node`** stores this new step sequence in the vector store as a reusable procedure, keyed by an embedding of the task description.
6. **Response returned**, and the *next* time someone asks to purge the geo-ip cache, Case A applies instead.

---

## 6. Why This Pattern Is Appropriate

| Requirement | How Procedural Memory satisfies it |
|---|---|
| Fast, consistent execution of recurring tasks | Matched procedures skip LLM planning entirely — deterministic replay |
| Lower inference cost over time | Planning cost is paid once per distinct task, not once per execution |
| System improves with use | Every successfully-planned novel task becomes a reusable procedure |
| Safety/predictability for operational actions | Verified sequences are replayed exactly, not regenerated with fresh variance each time |

**Trade-offs / when it's not enough:**
- A stored procedure can become **stale** if the underlying system changes (e.g., a new deployment tool replaces the old scaling command) — blindly replaying an outdated procedure can be *worse* than re-planning. Production systems need staleness detection/expiry, which overlaps with **Memory Forgetting** (Pattern 16).
- Matching by embedding similarity is probabilistic — a wrongly "matched" procedure for a subtly different task can cause **incorrect execution**, which is riskier than a wrong *answer* would be in a chat-only pattern. Production implementations should use a conservative similarity threshold and always support human confirmation before executing actions with real side effects.
- Procedural Memory doesn't reason about *why* a procedure works — it just replays it. If a step's precondition silently changes, the procedure has no way to notice on its own; you typically want each executed step's outcome checked (as in the implementation below) rather than blind full-sequence replay.

---

## 7. Production-Quality Python Implementation

### Requirements

```bash
pip install -U langchain-ollama langgraph langchain-core langchain-chroma chromadb
# Ollama models used locally:
#   ollama pull llama3.1
#   ollama pull nomic-embed-text
```

### `procedural_memory.py`

```python
"""
Pattern 6: Procedural Memory
Reusable, verified step-sequences ("procedures") matched by similarity
and replayed deterministically, with LLM planning as a fallback for
novel tasks, built on langchain-chroma + langchain-ollama.

Run:
    python procedural_memory.py
"""

from __future__ import annotations

import json
import logging
import uuid
from typing import Annotated, Callable, TypedDict

from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_core.messages import HumanMessage
from langchain_ollama import ChatOllama, OllamaEmbeddings
from langgraph.graph import StateGraph, START, END

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("procedural_memory")


# --------------------------------------------------------------------------
# 1. A tiny "tool registry" simulating real ops actions. In production
#    these would call real infra APIs (k8s, cloud provider SDKs, etc.).
# --------------------------------------------------------------------------
def check_logs(target: str) -> str:
    return f"Checked logs for {target}: found repeated timeout errors."


def drain_queue(target: str) -> str:
    return f"Drained pending queue for {target}."


def scale_to_zero(target: str) -> str:
    return f"Scaled {target} to 0 replicas."


def scale_to_n(target: str, n: int = 3) -> str:
    return f"Scaled {target} back to {n} replicas."


def verify_health(target: str) -> str:
    return f"Verified health checks passing for {target}."


def purge_cache(target: str) -> str:
    return f"Purged stale entries from {target}."


TOOL_REGISTRY: dict[str, Callable[..., str]] = {
    "check_logs": check_logs,
    "drain_queue": drain_queue,
    "scale_to_zero": scale_to_zero,
    "scale_to_n": scale_to_n,
    "verify_health": verify_health,
    "purge_cache": purge_cache,
}


# --------------------------------------------------------------------------
# 2. Graph state
# --------------------------------------------------------------------------
class OpsState(TypedDict):
    task: str
    target: str
    matched_procedure: list[dict] | None
    executed_steps: list[dict]
    success: bool
    final_message: str


def build_llm(model: str = "llama3.1", temperature: float = 0.1) -> ChatOllama:
    return ChatOllama(model=model, temperature=temperature, keep_alive="10m")


def build_embeddings(model: str = "nomic-embed-text") -> OllamaEmbeddings:
    return OllamaEmbeddings(model=model)


def build_vectorstore(embeddings: OllamaEmbeddings, persist_dir: str = "./procedural_memory_db") -> Chroma:
    return Chroma(
        collection_name="procedures",
        embedding_function=embeddings,
        persist_directory=persist_dir,
    )


SIMILARITY_MATCH_THRESHOLD = 0.35  # Chroma returns distance; lower = more similar


# --------------------------------------------------------------------------
# 3. Node: try to match the incoming task to a known, verified procedure.
# --------------------------------------------------------------------------
def make_match_node(vectorstore: Chroma):
    def match_procedure_node(state: OpsState) -> OpsState:
        results = vectorstore.similarity_search_with_score(state["task"], k=1)

        if results:
            doc, distance = results[0]
            logger.info("Best procedure match distance=%.3f for task=%r", distance, state["task"])
            if distance <= SIMILARITY_MATCH_THRESHOLD:
                procedure = json.loads(doc.page_content)
                logger.info("Matched known procedure: %s", procedure["steps"])
                return {"matched_procedure": procedure["steps"]}

        logger.info("No confident procedure match; will plan with LLM.")
        return {"matched_procedure": None}

    return match_procedure_node


# --------------------------------------------------------------------------
# 4. Node: execute a (matched or planned) step sequence, checking each
#    step's outcome rather than blindly trusting the whole sequence.
# --------------------------------------------------------------------------
def execute_steps(steps: list[dict], target: str) -> tuple[list[dict], bool]:
    executed = []
    all_ok = True
    for step in steps:
        tool_name = step["tool"]
        tool_fn = TOOL_REGISTRY.get(tool_name)
        if tool_fn is None:
            executed.append({"tool": tool_name, "ok": False, "result": "unknown tool"})
            all_ok = False
            continue
        try:
            result = tool_fn(target, **{k: v for k, v in step.items() if k not in ("tool",)})
            executed.append({"tool": tool_name, "ok": True, "result": result})
        except Exception as exc:
            logger.exception("Step %s failed", tool_name)
            executed.append({"tool": tool_name, "ok": False, "result": str(exc)})
            all_ok = False
    return executed, all_ok


def execute_known_procedure_node(state: OpsState) -> OpsState:
    executed, all_ok = execute_steps(state["matched_procedure"], state["target"])
    summary = "Restarted using the known runbook" if all_ok else "Known runbook had a failing step"
    return {
        "executed_steps": executed,
        "success": all_ok,
        "final_message": f"{summary} ({sum(s['ok'] for s in executed)}/{len(executed)} steps succeeded).",
    }


# --------------------------------------------------------------------------
# 5. Node: LLM plans a NEW step sequence for a novel task.
# --------------------------------------------------------------------------
PLANNING_PROMPT = """You are an ops planning assistant. Given a task and a \
list of available tools, produce an ordered JSON list of steps to accomplish \
the task. Each step must be an object: {{"tool": "<tool_name>"}}.
Only use tools from this exact list: {tool_names}

Task: "{task}"
JSON steps:"""


def make_plan_node(llm: ChatOllama):
    def plan_with_llm_node(state: OpsState) -> OpsState:
        planning_llm = llm.bind(format="json")
        prompt = PLANNING_PROMPT.format(
            tool_names=", ".join(TOOL_REGISTRY.keys()), task=state["task"]
        )
        try:
            raw = planning_llm.invoke(prompt).content
            steps = json.loads(raw)
            assert isinstance(steps, list) and all("tool" in s for s in steps)
        except Exception:
            logger.warning("LLM planning failed to produce valid steps.")
            steps = []

        logger.info("LLM-planned steps for novel task: %s", steps)
        return {"matched_procedure": steps}

    return plan_with_llm_node


def execute_planned_procedure_node(state: OpsState) -> OpsState:
    if not state["matched_procedure"]:
        return {"executed_steps": [], "success": False, "final_message": "Planning failed; no steps to execute."}
    executed, all_ok = execute_steps(state["matched_procedure"], state["target"])
    summary = "Completed via newly-planned procedure" if all_ok else "New plan had a failing step"
    return {
        "executed_steps": executed,
        "success": all_ok,
        "final_message": f"{summary} ({sum(s['ok'] for s in executed)}/{len(executed)} steps succeeded).",
    }


# --------------------------------------------------------------------------
# 6. Node: persist a successful NEW plan as a reusable procedure.
# --------------------------------------------------------------------------
def make_save_procedure_node(vectorstore: Chroma):
    def save_procedure_node(state: OpsState) -> OpsState:
        if not state["success"]:
            return {}
        procedure_record = {"task": state["task"], "steps": state["matched_procedure"], "success_count": 1}
        doc = Document(
            page_content=json.dumps(procedure_record),
            metadata={"task": state["task"]},
            id=str(uuid.uuid4()),
        )
        vectorstore.add_documents([doc])
        logger.info("Saved new procedure for future reuse: task=%r", state["task"])
        return {}

    return save_procedure_node


# --------------------------------------------------------------------------
# 7. Assemble the graph with a routing edge on "was a procedure matched?"
# --------------------------------------------------------------------------
def route_on_match(state: OpsState) -> str:
    return "execute_known_procedure_node" if state["matched_procedure"] else "plan_with_llm_node"


def build_graph(persist_dir: str = "./procedural_memory_db"):
    llm = build_llm()
    embeddings = build_embeddings()
    vectorstore = build_vectorstore(embeddings, persist_dir)

    graph_builder = StateGraph(OpsState)
    graph_builder.add_node("match_procedure_node", make_match_node(vectorstore))
    graph_builder.add_node("execute_known_procedure_node", execute_known_procedure_node)
    graph_builder.add_node("plan_with_llm_node", make_plan_node(llm))
    graph_builder.add_node("execute_planned_procedure_node", execute_planned_procedure_node)
    graph_builder.add_node("save_procedure_node", make_save_procedure_node(vectorstore))

    graph_builder.add_edge(START, "match_procedure_node")
    graph_builder.add_conditional_edges(
        "match_procedure_node",
        route_on_match,
        {
            "execute_known_procedure_node": "execute_known_procedure_node",
            "plan_with_llm_node": "plan_with_llm_node",
        },
    )
    graph_builder.add_edge("execute_known_procedure_node", END)
    graph_builder.add_edge("plan_with_llm_node", "execute_planned_procedure_node")
    graph_builder.add_edge("execute_planned_procedure_node", "save_procedure_node")
    graph_builder.add_edge("save_procedure_node", END)

    return graph_builder.compile()


# --------------------------------------------------------------------------
# 8. Service wrapper
# --------------------------------------------------------------------------
class ProceduralMemoryService:
    def __init__(self, persist_dir: str = "./procedural_memory_db"):
        self.graph = build_graph(persist_dir)

    def run_task(self, task: str, target: str) -> str:
        result = self.graph.invoke(
            {
                "task": task,
                "target": target,
                "matched_procedure": None,
                "executed_steps": [],
                "success": False,
                "final_message": "",
            }
        )
        return result["final_message"]


# --------------------------------------------------------------------------
# 9. Demo: seed a procedure by first solving a novel task, then show
#    the SAME task next time skips planning entirely.
# --------------------------------------------------------------------------
if __name__ == "__main__":
    service = ProceduralMemoryService()

    print("--- First time: novel task, requires LLM planning ---")
    msg = service.run_task("restart the stuck payment-worker", target="payment-worker")
    print("Result:", msg)

    print("\n--- Second time: same task -- should be matched and replayed instantly ---")
    msg = service.run_task("restart the stuck payment-worker", target="payment-worker")
    print("Result:", msg)

    print("\n--- A genuinely different, novel task ---")
    msg = service.run_task("purge the stale entries from the geo-ip cache", target="geo-ip-cache")
    print("Result:", msg)
```

### Notes on production-readiness in this code

- **Match-then-replay, plan-only-as-fallback** — the routing edge (`route_on_match`) ensures known tasks never pay the LLM planning cost twice, while unmatched tasks still get a full planning pass.
- **Per-step outcome checking**, not blind trust in a stored sequence — `execute_steps` records `ok`/`result` for every step, so a partially-failing "known" procedure is reported honestly rather than silently marked as a full success.
- **Similarity threshold (`SIMILARITY_MATCH_THRESHOLD`)** guards against false-positive matches — an operationally risky mismatch (executing the *wrong* procedure for a subtly different task) is exactly the failure mode this pattern must avoid, so the threshold is conservative on purpose.
- **Only successful new plans are saved** (`if not state["success"]: return {}` in `save_procedure_node`) — a failed novel plan is never persisted as if it were a verified procedure.
- **Constrained JSON planning output** (`llm.bind(format="json")`) with a fixed, explicit tool list keeps the LLM's plan restricted to tools that actually exist and can be executed safely.

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

> Requires both Ollama models pulled locally: `ollama pull llama3.1` (generation/planning) and `ollama pull nomic-embed-text` (embeddings for procedure matching).

---

**Next up → Pattern 7: Entity Memory** (tracking structured facts *about specific named entities* — people, accounts, services — that get updated and referenced by name across a conversation).
