# Pattern 13: Planning — Python Version

Planning makes the model's strategy explicit and inspectable *before* any action happens — instead of improvising step-by-step like ReAct, or having the orchestrator immediately dispatch parallel workers, a dedicated planning call produces a full ordered plan first. That plan can be logged, validated, shown to a human for approval, or revised — all before you spend money or take any real-world action executing it.

This is a Python/FastAPI translation of the original Java/Spring AI pattern, preserving the same structure and behavior.

---

## What's happening

The planner makes one call that returns a full `Plan` — an ordered list of structured steps (Pydantic + `.parse()`, same as every structured call since Pattern 2) — before any execution begins. Each step is then executed independently (optionally via a tool-equipped call, reusing Pattern 5/11's tool loop), in order. Critically, the plan is a regular Python object you can log, validate, show to a user for approval, or reject — something pure ReAct improvisation (Pattern 11) never gives you, since ReAct only reveals its next step right as it takes it.

### The core concept — why "see the whole plan first" matters

ReAct (Pattern 11) is powerful precisely because it doesn't need to know the full sequence in advance — but that same strength is a weakness whenever the *stakes* are high. If an agent is about to run four SQL migrations against a production database, "trust it to figure out the right sequence as it goes, one step at a time" is a very different risk profile than "let a person read the full four-step plan and click approve first." Planning trades away some of ReAct's flexibility (the plan is fixed once made, aside from explicit re-planning) in exchange for a natural checkpoint where a human — or automated validation — can look at the *entire* strategy before a single real-world action happens.

**Real-world analogy:** think of the difference between a surgeon improvising during an operation versus a surgical team reviewing a full pre-op plan before making the first incision. Improvising mid-surgery based on what's found is sometimes necessary (that's ReAct) — but for anything where you *can* plan ahead, you don't want the very first time anyone sees the strategy to be while it's already being executed on a real patient. The pre-op plan gets reviewed, checked against known risks, and signed off on by the team *before* anyone picks up a scalpel. That review step is only possible because the plan exists as a whole document someone can read — not because it's being generated one improvised action at a time.

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

No new dependencies — this reuses Pattern 2's structured output, Pattern 7/9's role-client wrapper, and (optionally) Pattern 5/11's tool loop.

---

## Code

### 1. The plan's shape

```python
# models.py
from pydantic import BaseModel
from typing import List


class PlanStep(BaseModel):
    order: int
    description: str
    expected_outcome: str


class Plan(BaseModel):
    goal: str
    steps: List[PlanStep]


class StepResult(BaseModel):
    order: int
    description: str
    outcome: str
    succeeded: bool
```

### 2. Planner — produces the full plan upfront

```python
# planner.py
from openai import OpenAI
from models import Plan

PLANNER_SYSTEM_PROMPT = (
    "You are a meticulous planner. Break the goal into an "
    "ordered sequence of concrete, executable steps. Each step "
    "should be independently actionable and state what "
    "outcome it should produce. Avoid vague steps."
)


class PlannerService:
    def __init__(self, client: OpenAI, model: str = "gpt-4o-mini"):
        self.client = client
        self.model = model

    def create_plan(self, goal: str) -> Plan:
        completion = self.client.chat.completions.parse(
            model=self.model,
            messages=[
                {"role": "system", "content": PLANNER_SYSTEM_PROMPT},
                {"role": "user", "content": f"Goal: {goal}"},
            ],
            response_format=Plan,
        )
        return completion.choices[0].message.parsed
```

### 3. Executor — runs the approved plan step by step, with re-planning on failure

```python
# plan_executor.py
from openai import OpenAI
from models import Plan, PlanStep, StepResult
from planner import PlannerService

EXECUTOR_SYSTEM_PROMPT = (
    "Execute exactly the step described. Use tools as needed. "
    "Report the outcome plainly."
)


class PlanExecutorService:
    def __init__(self, client: OpenAI, planner_service: PlannerService, model: str = "gpt-4o-mini"):
        self.client = client
        self.planner_service = planner_service
        self.model = model

    def _run_step(self, step: PlanStep) -> str:
        # In production, swap this for Pattern 5/11's tool-calling loop —
        # e.g. run_with_tools(client, prompt, tools=...) — so the executor
        # can actually act (query a DB, call an API), not just describe
        # what it would do. Kept as a plain call here to match the
        # doc's focus: planning structure, not tool mechanics.
        response = self.client.chat.completions.create(
            model=self.model,
            messages=[
                {"role": "system", "content": EXECUTOR_SYSTEM_PROMPT},
                {
                    "role": "user",
                    "content": (
                        f"Step {step.order}: {step.description}\n"
                        f"Expected outcome: {step.expected_outcome}"
                    ),
                },
            ],
        )
        return response.choices[0].message.content

    def _execute_steps(self, plan: Plan) -> list[StepResult]:
        results = []
        for step in plan.steps:
            outcome = self._run_step(step)
            results.append(StepResult(
                order=step.order,
                description=step.description,
                outcome=outcome,
                succeeded=True,
            ))
        return results

    def execute_goal(self, goal: str) -> list[StepResult]:

        plan = self.planner_service.create_plan(goal)
        results: list[StepResult] = []

        for step in plan.steps:
            try:
                outcome = self._run_step(step)
                results.append(StepResult(
                    order=step.order,
                    description=step.description,
                    outcome=outcome,
                    succeeded=True,
                ))

            except Exception as ex:
                results.append(StepResult(
                    order=step.order,
                    description=step.description,
                    outcome=f"Failed: {ex}",
                    succeeded=False,
                ))

                # Re-plan the remaining work given what's been done and what failed
                remaining_goal = (
                    f"Original goal: {goal}\n"
                    f"Completed so far: {results}\n"
                    f"Step that failed: {step.description} ({ex})\n"
                    f"Produce a revised plan for the remaining work."
                )

                revised_plan = self.planner_service.create_plan(remaining_goal)
                results.extend(self._execute_steps(revised_plan))
                break

        return results
```

### 4. FastAPI endpoints — exposing the plan for review before execution is the key UX win here

```python
# main.py
import os
from dotenv import load_dotenv
from fastapi import FastAPI, Query
from openai import OpenAI

from planner import PlannerService
from plan_executor import PlanExecutorService

load_dotenv()

app = FastAPI()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
planner_service = PlannerService(client)
executor_service = PlanExecutorService(client, planner_service)


# Step 1: get the plan, show it to a human, don't execute yet
@app.get("/api/plan/preview")
def preview(goal: str = Query(...)):
    return planner_service.create_plan(goal)


# Step 2: once approved, actually run it
@app.post("/api/plan/execute")
def execute(goal: str = Query(...)):
    return executor_service.execute_goal(goal)
```

Run it:

```bash
uvicorn main:app --reload --port 8080
```

---

## Try it

```bash
curl "http://localhost:8080/api/plan/preview?goal=Migrate%20our%20user%20table%20to%20add%20a%20soft-delete%20column%20safely"
```

```json
{
  "goal": "Migrate our user table to add a soft-delete column safely",
  "steps": [
    {
      "order": 1,
      "description": "Check current row count and table size",
      "expected_outcome": "Size estimate to judge migration risk"
    },
    {
      "order": 2,
      "description": "Write ALTER TABLE statement adding nullable deleted_at column",
      "expected_outcome": "Migration SQL ready"
    },
    {
      "order": 3,
      "description": "Plan backfill strategy for existing rows",
      "expected_outcome": "Backfill approach defined"
    },
    {
      "order": 4,
      "description": "Draft rollback plan",
      "expected_outcome": "Safe rollback documented"
    }
  ]
}
```

A human reviews this before anything executes — exactly the visibility ReAct doesn't give you. Notice that `/api/plan/preview` and `/api/plan/execute` are two entirely separate endpoints — the plan can be returned, displayed, edited, or rejected by a human *without* `execute_goal()` ever being called. That separation is the whole point of the pattern.

---

## Mapping Java/Spring AI concepts to Python

| Spring AI / Java | Python | What it's really doing |
|---|---|---|
| `record Plan(String goal, List<PlanStep> steps)` | `class Plan(BaseModel): goal: str; steps: List[PlanStep]` | The full upfront strategy as a typed, inspectable object |
| `record StepResult(...)` | `class StepResult(BaseModel): ...` | The typed outcome of one executed (or failed) step |
| `.call().entity(Plan.class)` | `.parse(response_format=Plan)` → `.parsed` | The planner's structured planning call (Pattern 2, reused) |
| `builder.clone().defaultSystem(...)` for planner & executor | Separate system-prompt constants (`PLANNER_SYSTEM_PROMPT`, `EXECUTOR_SYSTEM_PROMPT`) passed into each service | Both keep planner and executor as distinctly-instructed roles, same idea as Pattern 7/9's `RoleClient` |
| `for (Plan.PlanStep step : plan.steps())` with `try`/`catch` | `for step in plan.steps:` with `try`/`except` | Step-by-step execution with failure handling identical in shape |
| `plannerService.createPlan(remainingGoal)` on failure | `self.planner_service.create_plan(remaining_goal)` on failure | Re-planning: a second full planner call given what succeeded and what failed |
| `@GetMapping("/preview")` / `@PostMapping("/execute")` | `@app.get("/api/plan/preview")` / `@app.post("/api/plan/execute")` | Two distinct endpoints — one to *see* the plan, one to *run* it — the human checkpoint lives in the gap between them |

**Key insight:** planning is Pattern 2 (structured output) used to make an entire *strategy* — not just one field — inspectable as data, combined with an ordinary `for` loop executing that data one step at a time. The re-planning branch is really just "call `create_plan()` again, with more context" — no new mechanism, just the same planner function invoked a second time mid-execution. The pattern's real value isn't in any of the individual pieces (you've built all of them already) — it's in the *architectural decision* to separate "decide the whole strategy" from "carry it out," which is what creates the approval checkpoint ReAct structurally can't offer.

---

## Planning vs. ReAct vs. Orchestrator-Workers

|  | ReAct (11) | Planning (13) | Orchestrator-Workers (9) |
|---|---|---|---|
| **When is the plan visible** | Never upfront — revealed one action at a time | Fully upfront, before execution | Upfront, but immediately dispatched |
| **Human review possible** | Hard — would need to interrupt mid-loop | Easy — natural checkpoint (`/preview` vs `/execute`) | Hard — workers fire immediately |
| **Adapts mid-execution** | Yes, every step | Only via explicit re-planning | No, unless you add it |
| **Best for** | Exploratory tasks, unclear info needs | High-stakes or auditable multi-step work | Decomposable tasks safe to fully parallelize |

---

## Production notes

- The plan-preview endpoint is exactly where you'd hook in a Human-in-the-Loop approval gate for risky operations — `/api/plan/preview` already returns a plain JSON `Plan` object your frontend can render as a checklist with an "Approve & Run" button that then calls `/api/plan/execute`.
- Re-planning is expensive — it's a full extra planner call (`self.planner_service.create_plan(remaining_goal)`) — so only trigger it on genuine step failures, not minor warnings. The `try`/`except` boundary above is deliberately narrow: it only re-plans on an actual exception, not on every step.
- Validate the plan structurally before executing (non-empty steps, sane ordering) — a malformed plan from the LLM should fail fast rather than executing garbage steps. In Python, add this as a `Field` constraint directly on `Plan.steps` (e.g. `Field(min_length=1)`) or as an explicit check right after `create_plan()` returns, before the `for` loop begins.

---

## Project structure

```
pattern13-planning/
├── main.py
├── planner.py
├── plan_executor.py
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
| 13 | Planning | ✅ Full upfront strategy, inspectable before execution (this doc) |

*(To be filled in as each pattern's source doc is provided.)*