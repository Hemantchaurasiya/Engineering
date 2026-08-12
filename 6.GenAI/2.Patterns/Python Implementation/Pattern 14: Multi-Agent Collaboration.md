# Pattern 14: Multi-Agent Collaboration — Python Version

This extends Orchestrator-Workers in one key way: instead of workers being single, stateless LLM calls, each "worker" here is a full specialist agent — with its own role, system prompt, and possibly its own tools — and a supervisor dynamically decides which specialist to invoke next based on the evolving conversation, looping until the task is genuinely done (not just dispatching once and synthesizing).

This is a Python/FastAPI translation of the original Java/Spring AI pattern, preserving the same structure and behavior.

---

## What's happening

The supervisor doesn't dispatch all workers once like Orchestrator-Workers (Pattern 9) — it makes **one routing decision at a time**, observes the result, and decides again, looping until it judges the task complete. Each specialist agent is a fully independent role-specific client (potentially with its own tools, reusing Pattern 5/11's tool loop) — the supervisor is just choosing which expert should take the next turn, based on a shared progress log everyone reads from and writes to.

### The core concept — why this is Pattern 7 (routing) turned into a loop

Recall Pattern 7's routing: classify the query once, dispatch to one specialist, done. Multi-agent collaboration takes that exact same idea — a classifier deciding "who should handle this" — and instead of running it once, runs it **repeatedly**, after every specialist's turn, feeding the growing shared history back in each time. The supervisor is a router that gets to route again and again as the situation evolves, rather than making one irreversible dispatch decision.

**Real-world analogy:** think of a magazine's managing editor overseeing a story from pitch to publication. The editor doesn't hand the story to a researcher, then a writer, then a copyeditor in one fixed sequence and call it done (that would be Pattern 6's chaining, or Pattern 9's orchestrator-workers with a fixed dispatch). Instead, the editor reads the researcher's notes, decides the writer should take a pass, reads the draft, decides it needs the fact-checker *again* because a claim looks shaky, reads the fact-check, sends it back to the writer for a revision, then finally to the copyeditor — a genuinely unpredictable back-and-forth where *any* specialist might be called on again, based on what the editor sees at each checkpoint. The editor's only real skill is deciding, moment to moment, "who does this story need right now" — exactly the supervisor's role here.

---

## Setup

### `requirements.txt`

```
fastapi==0.115.0
uvicorn==0.32.0
openai==1.54.0
pydantic==2.9.2
python-dotenv==1.0.1
```

No new dependencies — built entirely from Pattern 2 (structured routing decisions), Pattern 7/9's role-client wrapper, and an ordinary bounded loop.

---

## Code

### 1. The supervisor's decision type

```python
# models.py
from enum import Enum
from pydantic import BaseModel


class NextAgent(str, Enum):
    RESEARCHER = "RESEARCHER"
    WRITER = "WRITER"
    CRITIC = "CRITIC"
    DONE = "DONE"


class SupervisorDecision(BaseModel):
    next_agent: NextAgent
    instruction_for_agent: str
```

### 2. The agent team — supervisor plus three specialists

```python
# multi_agent_supervisor.py
from openai import OpenAI
from models import NextAgent, SupervisorDecision

MAX_TURNS = 8

SUPERVISOR_SYSTEM_PROMPT = (
    "You coordinate a team: RESEARCHER gathers facts, WRITER drafts "
    "content, CRITIC reviews quality. Given the goal and progress so "
    "far, decide which agent should act next and what exactly they "
    "should do. Choose DONE only when the final output is genuinely "
    "complete and has passed critic review."
)


class RoleClient:
    """Same wrapper from Pattern 7/9/10 — a fixed system prompt + shared client."""

    def __init__(self, client: OpenAI, system_prompt: str, model: str = "gpt-4o-mini"):
        self.client = client
        self.system_prompt = system_prompt
        self.model = model

    def ask(self, user_message: str) -> str:
        response = self.client.chat.completions.create(
            model=self.model,
            messages=[
                {"role": "system", "content": self.system_prompt},
                {"role": "user", "content": user_message},
            ],
        )
        return response.choices[0].message.content


class TaskDidNotConvergeError(Exception):
    pass


class MultiAgentSupervisorService:
    def __init__(self, client: OpenAI):
        self.raw_client = client

        self.researcher = RoleClient(
            client,
            system_prompt="You are a researcher. Gather and state relevant facts concisely.",
            # In production, swap `ask()` for a tool-calling loop (Pattern 5/11)
            # so the researcher can actually search the web or query a DB.
        )
        self.writer = RoleClient(
            client,
            system_prompt="You are a writer. Produce clear, well-structured content from the research provided.",
        )
        self.critic = RoleClient(
            client,
            system_prompt=(
                "You are a strict critic. Review the current draft and report "
                "specific issues, or explicitly state 'No issues — ready to ship' "
                "if it genuinely meets a high bar."
            ),
        )

        self.agents = {
            NextAgent.RESEARCHER: self.researcher,
            NextAgent.WRITER: self.writer,
            NextAgent.CRITIC: self.critic,
        }

    def _decide_next(self, progress_so_far: str) -> SupervisorDecision:
        completion = self.raw_client.chat.completions.parse(
            model="gpt-4o-mini",
            messages=[
                {"role": "system", "content": SUPERVISOR_SYSTEM_PROMPT},
                {"role": "user", "content": f"Progress so far:\n{progress_so_far}"},
            ],
            response_format=SupervisorDecision,
        )
        return completion.choices[0].message.parsed

    def run_task(self, goal: str) -> str:

        progress_log: list[str] = [f"GOAL: {goal}"]

        for _ in range(MAX_TURNS):

            progress_so_far = "\n\n".join(progress_log)

            decision = self._decide_next(progress_so_far)

            if decision.next_agent == NextAgent.DONE:
                return progress_log[-1]  # last meaningful output

            agent_to_call = self.agents[decision.next_agent]

            agent_output = agent_to_call.ask(
                f"Context so far:\n{progress_so_far}\n\n"
                f"Your task: {decision.instruction_for_agent}"
            )

            progress_log.append(f"[{decision.next_agent.value}]: {agent_output}")

        raise TaskDidNotConvergeError(
            f"Task did not converge within {MAX_TURNS} turns"
        )
```

### 3. FastAPI endpoint

```python
# main.py
import os
from dotenv import load_dotenv
from fastapi import FastAPI, Query, HTTPException
from openai import OpenAI

from multi_agent_supervisor import MultiAgentSupervisorService, TaskDidNotConvergeError

load_dotenv()

app = FastAPI()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
supervisor_service = MultiAgentSupervisorService(client)


@app.get("/api/multi-agent/run")
def run(goal: str = Query(...)):
    try:
        return {"result": supervisor_service.run_task(goal)}
    except TaskDidNotConvergeError as e:
        raise HTTPException(status_code=422, detail=str(e))
```

Run it:

```bash
uvicorn main:app --reload --port 8080
```

---

## Try it

```bash
curl "http://localhost:8080/api/multi-agent/run?goal=Write%20a%20short%2C%20accurate%20explainer%20on%20how%20mRNA%20vaccines%20work"
```

A plausible trace through `run_task`'s loop, watching `progress_log` grow turn by turn:

```text
progress_log[0]: "GOAL: Write a short, accurate explainer on how mRNA vaccines work"

Turn 1 — supervisor decides: RESEARCHER, "gather key facts about mRNA vaccine mechanism"
progress_log[1]: "[NextAgent.RESEARCHER]: <fact list>"

Turn 2 — supervisor decides: WRITER, "draft an explainer using this research"
progress_log[2]: "[NextAgent.WRITER]: <draft>"

Turn 3 — supervisor decides: CRITIC, "review this draft"
progress_log[3]: "[NextAgent.CRITIC]: Issue — oversimplifies the lipid nanoparticle delivery step"

Turn 4 — supervisor decides: WRITER, "revise to address the critic's feedback"
progress_log[4]: "[NextAgent.WRITER]: <revision>"

Turn 5 — supervisor decides: CRITIC, "review the revision"
progress_log[5]: "[NextAgent.CRITIC]: No issues — ready to ship"

Turn 6 — supervisor decides: DONE
-> returns progress_log[-1], the critic's approval turn... 
   (in practice you'd track "last WRITER output" separately if you want
   the polished draft itself rather than the critic's sign-off message —
   see Production notes below)
```

Note that `_decide_next()` is called **fresh, every single turn**, and it's given the *entire* `progress_so_far` string each time — the supervisor isn't following a fixed script, it's re-reading the whole situation and re-deciding who should go next, which is exactly what lets it call CRITIC → WRITER → CRITIC again in a genuine back-and-forth rather than a straight line.

---

## Mapping Java/Spring AI concepts to Python

| Spring AI / Java | Python | What it's really doing |
|---|---|---|
| `enum NextAgent { RESEARCHER, WRITER, CRITIC, DONE }` | `class NextAgent(str, Enum): ...` | The fixed set of routing outcomes the supervisor can choose (Pattern 7's `QueryCategory`, extended with a `DONE` terminal state) |
| `record SupervisorDecision(NextAgent nextAgent, String instructionForAgent)` | `class SupervisorDecision(BaseModel): next_agent: NextAgent; instruction_for_agent: str` | The supervisor's structured decision each turn |
| `.call().entity(SupervisorDecision.class)` | `.parse(response_format=SupervisorDecision)` → `.parsed` | The supervisor's per-turn structured routing call (Pattern 2, reused) |
| Four `builder.clone().defaultSystem(...)` clients | Four `RoleClient` instances (`researcher`, `writer`, `critic`, plus the raw supervisor client) | Each specialist is an independently-instructed role (Pattern 7/9/10/12's wrapper, reused) |
| `switch (decision.nextAgent()) { case RESEARCHER -> researcherClient; ... }` | `self.agents[decision.next_agent]` dict lookup | Dispatch to the chosen specialist based on the supervisor's decision |
| `List<String> progressLog` + `String.join("\n\n", progressLog)` | `list[str] progress_log` + `"\n\n".join(progress_log)` | The shared history every agent (including the supervisor) reads from and writes to |
| `for (int turn = 0; turn < MAX_TURNS; turn++)` | `for _ in range(MAX_TURNS)` | The bounded outer loop capping how many supervisor decisions can happen |
| `throw new IllegalStateException(...)` on non-convergence | `raise TaskDidNotConvergeError(...)` on non-convergence | Failing loudly instead of silently returning an incomplete result |

**Key insight:** multi-agent collaboration adds exactly one new idea on top of everything built so far — **the router from Pattern 7 gets called again, inside a loop, instead of once.** Every specialist is still just a `RoleClient.ask()` call (Pattern 1 with a system prompt); the supervisor's decision is still just a structured-output call (Pattern 2); the loop shape is the bounded `for` loop from Pattern 10's evaluator-optimizer. Nothing mechanically new is introduced — what's new is that the *routing decision itself* becomes part of the loop's body, re-evaluated fresh every iteration against the growing shared history, rather than being made once up front.

---

## Multi-Agent vs. Orchestrator-Workers — the real distinction

|  | Orchestrator-Workers (9) | Multi-Agent (14) |
|---|---|---|
| **Planning** | One upfront plan, dispatched at once | One decision at a time, re-evaluated each turn |
| **Workers** | Stateless, single-purpose calls | Full agents — can have their own tools, persona, multi-turn behavior |
| **Loop** | Fan-out → synthesize (one pass) | Genuine loop until convergence |
| **Best for** | Decomposable tasks where subtask list is fairly clear once planned | Tasks needing iterative cross-checking between specialized perspectives |

---

## Production notes

- This is the most expensive and slowest pattern so far — potentially many sequential LLM calls (supervisor + specialist, repeated). Reserve it for tasks where quality genuinely benefits from specialist cross-review; don't reach for it as a default. In Python this cost is visible directly as one `_decide_next()` call plus one `agent_to_call.ask()` call *per turn*, up to `MAX_TURNS` times.
- Convergence isn't guaranteed — always cap `MAX_TURNS`; a critic and writer can in principle loop indefinitely if the critic's bar is miscalibrated. Consider tracking the *last WRITER output* separately (e.g. `last_draft = agent_output if decision.next_agent == NextAgent.WRITER else last_draft`) rather than relying on `progress_log[-1]` when the supervisor says `DONE` — as shown in the trace above, the very last log entry might be the critic's approval message rather than the actual deliverable.
- In production multi-agent systems, specialists are often deployed as separate services communicating over a message bus (e.g. via HTTP calls between microservices, or a task queue) rather than in-process function calls — useful when specialists need independent scaling, different infra, or are owned by different teams. The pattern's logic is identical; only `RoleClient.ask()`'s implementation would change from a direct OpenAI call to an HTTP call against another service — the loop, the supervisor's decision-making, and the shared progress log stay exactly the same.

---

## Project structure

```
pattern14-multi-agent/
├── main.py
├── multi_agent_supervisor.py
├── models.py
├── requirements.txt
├── .env
└── README.md
```

---

## Pattern series progress

| # | Pattern | Adds |
|---|---|---|
| 1 | Direct Prompting | ✅ Single stateless call |
| 2 | Structured Output | ✅ Typed, validated responses via Pydantic |
| 3 | Prompt Templates & Few-Shot | ✅ Reusable templates + example-driven prompting |
| 4 | RAG | ✅ Retrieval-grounded answers from private data |
| 5 | Tool Calling | ✅ Model-initiated function execution via an explicit loop |
| 6 | Prompt Chaining | ✅ Sequential, focused calls with gate checks in between |
| 7 | Routing | ✅ Classify-then-dispatch to specialized handlers |
| 8 | Parallelization | ✅ Concurrent sectioning and voting via thread pools |
| 9 | Orchestrator-Workers | ✅ LLM-planned subtasks dispatched and synthesized dynamically |
| 10 | Evaluator-Optimizer | ✅ Self-correcting generate → critique → regenerate loop |
| 11 | ReAct Agent | ✅ Multi-hop reason → act → observe loop with model-driven branching |
| 12 | Reflection | ✅ Same-thread self-critique and revision |
| 13 | Planning | ✅ Full upfront strategy, inspectable before execution |
| 14 | Multi-Agent Collaboration | ✅ Routing decision looped across specialist agents until convergence (this doc) |

*(To be filled in as each pattern's source doc is provided.)*