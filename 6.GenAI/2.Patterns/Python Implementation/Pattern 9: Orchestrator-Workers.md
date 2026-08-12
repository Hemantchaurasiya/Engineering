# Pattern 9: Orchestrator-Workers — Python Version

This looks like sectioning (Pattern 8), but with one critical difference: in sectioning, you hardcode the subtasks in your source code. Here, an orchestrator LLM call decides the subtasks *at runtime*, based on the specific input — because for many tasks, you can't know the right breakdown in advance. The orchestrator plans, dispatches worker calls (often in parallel), then a synthesizer combines their outputs into the final result.

This is a Python/FastAPI translation of the original Java/Spring AI pattern, preserving the same structure and behavior.

---

## What's happening

The orchestrator makes one LLM call that returns a structured plan (a list of subtasks, typed via Pydantic + `.parse()` from Pattern 2) — the number and nature of subtasks isn't fixed in your code, the model decides based on the specific input. You then dispatch a worker call per planned subtask (in parallel, using the `ThreadPoolExecutor` approach from Pattern 8), and a final synthesis call combines all worker outputs into a coherent result.

### The core concept — why the orchestrator can't be hardcoded

Pattern 8's sectioning worked because you, the developer, already knew the fixed dimensions to check — grammar, clarity, tone, facts — regardless of what document came in. That works because *the shape of the work doesn't change based on the input.* Report writing is different: a report on "the rise of edge computing" naturally breaks into history/drivers/use-cases/challenges, while a report on "how coffee is processed" naturally breaks into harvesting/processing/roasting/brewing — two completely different section lists, and neither one is knowable by looking at your source code in advance. You'd have to write an `if/else` for every possible topic, which is exactly the kind of endless-special-casing problem an LLM is good at replacing.

**Real-world analogy:** think of the difference between a factory assembly line and a newspaper's managing editor. An assembly line runs the exact same fixed steps on every unit — that's sectioning, Pattern 8. A managing editor, handed a breaking story, doesn't run a fixed checklist; they look at *this specific story* and decide on the spot: "we need someone on the political angle, someone on the economic impact, someone interviewing eyewitnesses, and a photographer" — a completely different assignment sheet than yesterday's story got. The editor is the orchestrator: their whole job is deciding *what the right breakdown is* for this particular case, then handing out assignments (worker calls) to specialists, and finally stitching everyone's copy together into one coherent front page (the synthesizer).

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

No new dependencies beyond Pattern 2 (structured output) and Pattern 8 (thread pool concurrency) — this pattern is built entirely by combining those two.

---

## Code

Use case: write a comprehensive report on any topic — the orchestrator decides what sections are needed, since that varies wildly by topic. Identical use case to the Java version.

### 1. The typed plan and worker output shapes

```python
# models.py
from pydantic import BaseModel
from typing import List


class Subtask(BaseModel):
    section: str
    instruction: str


class SubtaskPlan(BaseModel):
    subtasks: List[Subtask]


class WorkerOutput(BaseModel):
    section: str
    content: str
```

### 2. The orchestrator service

```python
# report_orchestrator.py
from concurrent.futures import ThreadPoolExecutor
from openai import OpenAI

from models import SubtaskPlan, WorkerOutput


class RoleClient:
    """
    Same small wrapper introduced in Pattern 7 to replace ChatClient.Builder.clone():
    a fixed system prompt paired with the shared OpenAI client.
    """

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


class ReportOrchestratorService:
    def __init__(self, client: OpenAI, max_workers: int = 8):
        self.raw_client = client
        self.executor = ThreadPoolExecutor(max_workers=max_workers)

        self.orchestrator_system_prompt = (
            "You are a report planner. Break the requested report topic "
            "into 3-6 distinct sections that together cover it well. "
            "Each section needs a clear, specific writing instruction."
        )

        self.worker_client = RoleClient(
            client,
            system_prompt="You are a subject-matter writer. Write only the section requested, no preamble.",
        )

        self.synthesizer_client = RoleClient(
            client,
            system_prompt="You are an editor. Combine these sections into one cohesive report with smooth transitions.",
        )

    def _plan(self, topic: str) -> SubtaskPlan:
        completion = self.raw_client.chat.completions.parse(
            model="gpt-4o-mini",
            messages=[
                {"role": "system", "content": self.orchestrator_system_prompt},
                {"role": "user", "content": f"Plan a report on: {topic}"},
            ],
            response_format=SubtaskPlan,
        )
        return completion.choices[0].message.parsed

    def _run_worker(self, subtask) -> WorkerOutput:
        content = self.worker_client.ask(subtask.instruction)
        return WorkerOutput(section=subtask.section, content=content)

    def generate_report(self, topic: str) -> str:

        # 1. Orchestrator plans the subtasks dynamically
        plan = self._plan(topic)

        # 2. Dispatch workers in parallel — one per planned subtask
        futures = [
            self.executor.submit(self._run_worker, subtask)
            for subtask in plan.subtasks
        ]
        sections = [future.result() for future in futures]

        # 3. Synthesize all worker outputs into one cohesive report
        combined_draft = "\n\n".join(
            f"## {s.section}\n{s.content}" for s in sections
        )

        return self.synthesizer_client.ask(
            f"Combine and polish these sections into a cohesive report:\n\n{combined_draft}"
        )
```

### 3. FastAPI endpoint

```python
# main.py
import os
from dotenv import load_dotenv
from fastapi import FastAPI, Query
from openai import OpenAI

from report_orchestrator import ReportOrchestratorService

load_dotenv()

app = FastAPI()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
orchestrator_service = ReportOrchestratorService(client)


@app.get("/api/orchestrator/report")
def generate(topic: str = Query(...)):
    return {"report": orchestrator_service.generate_report(topic)}
```

Run it:

```bash
uvicorn main:app --reload --port 8080
```

---

## Try it

```bash
curl "http://localhost:8080/api/orchestrator/report?topic=the%20rise%20of%20edge%20computing"
```

```text
Orchestrator might plan:
- History
- Key drivers
- Use cases
- Challenges
- Future outlook
```

```bash
curl "http://localhost:8080/api/orchestrator/report?topic=how%20coffee%20is%20processed"
```

```text
Orchestrator plans entirely different sections:
- Harvesting
- Processing methods
- Roasting
- Brewing
```

The same `generate_report()` code handles both — nothing in the Python source changes between these two calls. The `SubtaskPlan` returned by `_plan()` is different each time, and everything downstream (worker dispatch count, section headers, synthesis) adapts automatically because it's driven entirely by that plan's contents, not by anything hardcoded.

---

## Mapping Java/Spring AI concepts to Python

| Spring AI / Java | Python | What it's really doing |
|---|---|---|
| `record SubtaskPlan(List<Subtask> subtasks)` | `class SubtaskPlan(BaseModel): subtasks: List[Subtask]` | The typed shape of the orchestrator's dynamic plan |
| `.call().entity(SubtaskPlan.class)` | `.parse(response_format=SubtaskPlan)` → `.parsed` | The orchestrator's structured planning call (Pattern 2, reused) |
| `builder.clone().defaultSystem(...)` per role | `RoleClient(client, system_prompt=...)` per role | Both fork shared config into a role-specific client (same idea introduced in Pattern 7's routing) |
| `Executors.newVirtualThreadPerTaskExecutor()` + `CompletableFuture.supplyAsync` | `ThreadPoolExecutor` + `executor.submit(...)` | Fan out worker calls concurrently (Pattern 8, reused) |
| `futures.stream().map(CompletableFuture::join).toList()` | `[future.result() for future in futures]` | Fan in: wait for every worker to finish before synthesizing |
| `plan.subtasks().stream().map(subtask -> ...)` | `[self._run_worker(subtask) for subtask in plan.subtasks]` (dispatched via `submit`) | Iterates the *dynamically-sized* list of subtasks — could be 3, could be 6, code doesn't care |
| Final `synthesizerClient.prompt().user(...)` call | `self.synthesizer_client.ask(...)` | A plain Pattern 1 call combining all worker outputs into the final answer |

**Key insight:** this pattern is literally **Pattern 2 (structured output) deciding the shape of Pattern 8 (parallelization) at runtime, followed by one more Pattern 1 call to combine everything.** Nothing new is introduced mechanically — the only new idea, compared to everything built in Patterns 1-8, is that the *list you fan out over* is itself the output of an LLM call instead of a list you wrote by hand. Once you see that, "orchestrator-workers" stops looking like a distinct new mechanism and starts looking like "sectioning, except the sections are decided by an LLM call instead of a Python list literal."

---

## Orchestrator-Workers vs. Parallelization (Pattern 8) — the key distinction

|  | Parallelization (sectioning) | Orchestrator-Workers |
|---|---|---|
| **Subtasks defined** | Hardcoded by you, upfront (a Python list literal) | Decided by the LLM, at runtime (`plan.subtasks` from a `.parse()` call) |
| **Subtask count** | Fixed | Variable, input-dependent |
| **Use when** | You know the breakdown in advance (e.g. always check grammar + tone + facts) | The right breakdown genuinely varies per input (e.g. report sections differ wildly by topic) |

---

## Production notes

- Cap the orchestrator's subtask count (e.g. via the system prompt: "3-6 sections") — an unconstrained planner can spiral into excessive worker calls and runaway cost. In Python you can additionally enforce this defensively in code: `if not (3 <= len(plan.subtasks) <= 6): raise ValueError(...)` after `_plan()` returns, since Pydantic validates *shape* but not domain-specific bounds like list length unless you add a `Field(min_length=3, max_length=6)` constraint directly on `SubtaskPlan.subtasks`.
- This pattern is more expensive and slower than chaining or routing (orchestrator call + N worker calls + synthesizer call), so reserve it for genuinely open-ended tasks where the structure can't be predicted — true in both ecosystems, since the cost is driven by call count, not language.
- Consider validating the orchestrator's plan before dispatching workers (e.g. reject empty/duplicate sections) — garbage plans produce garbage worker calls. A quick Python check like `len(set(s.section for s in plan.subtasks)) != len(plan.subtasks)` catches duplicate section names before burning worker calls on them.

---

## Project structure

```
pattern9-orchestrator-workers/
├── main.py
├── report_orchestrator.py
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
| 9 | Orchestrator-Workers | ✅ LLM-planned subtasks dispatched and synthesized dynamically (this doc) |
| 10 | Agents | Looped call/decide/act cycle with self-correction |

*(To be filled in as each pattern's source doc is provided.)*