# Pattern 10: Evaluator-Optimizer — Python Version

This is the first explicitly self-correcting pattern: one LLM call generates a solution, a second LLM call critiques it against specific criteria, and if it doesn't pass, the feedback gets fed back into another generation attempt. This loop continues until the evaluator approves or a max-iteration limit is hit — useful anywhere "good enough on the first try" isn't reliable enough (translations, code, anything with clear quality criteria).

This is a Python/FastAPI translation of the original Java/Spring AI pattern, preserving the same structure and behavior.

---

## What's happening

The generator and evaluator are two distinct role-specific calls with different jobs and different system prompts (the `RoleClient` pattern introduced in Pattern 7 and reused in Pattern 9). The evaluator returns a structured verdict (Pydantic + `.parse()`, again) — pass/fail plus specific feedback — rather than free text, so your loop logic can branch on it reliably. On failure, the original task and the evaluator's feedback get fed back into the generator for the next attempt, so each iteration genuinely improves rather than blindly retrying.

### The core concept — why a second LLM call, instead of just asking for a better answer?

You might wonder why you need *two* calls at all — why not just ask the generator to "please double-check your own work" in one call? The problem is that a model producing an answer and a model critiquing that *same* answer in the same breath tends to be overconfident about its own output — much like how it's hard for a person to proofread their own essay right after writing it, because their brain already knows what it *meant* to say and glosses over what it actually wrote. Splitting generation and evaluation into two separate calls, with the evaluator given an explicit, narrow checklist and nothing else to do, produces much more reliable critique than a single call trying to write and grade itself simultaneously.

**Real-world analogy:** think of a student submitting an essay draft to a strict teacher, getting it back covered in specific red-pen notes ("this citation is missing a source," "this paragraph doesn't support your thesis"), revising based on *exactly* those notes, and resubmitting — repeating until the teacher signs off. The teacher never writes the essay themselves; their entire job is to check it against a rubric and hand back specific, actionable feedback. The student never has to guess what's wrong; they just fix what's flagged. That's the generator/evaluator loop: one party creates, the other critiques against a fixed rubric, and the loop only stops when the critique comes back clean — or the teacher's patience (max iterations) runs out.

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

No new dependencies — this pattern is a loop built entirely from Pattern 1 (plain generation) and Pattern 2 (structured evaluation) calls.

---

## Code

Use case: generate a SQL query that must satisfy specific correctness and style requirements, looping until it passes review. Identical use case to the Java version.

### 1. The typed evaluation verdict

```python
# models.py
from pydantic import BaseModel


class EvaluationResult(BaseModel):
    passed: bool
    feedback: str  # specific, actionable critique if not passed
```

### 2. The evaluator-optimizer service

```python
# sql_evaluator_optimizer.py
from openai import OpenAI
from models import EvaluationResult

MAX_ITERATIONS = 4


class RoleClient:
    """Same wrapper from Pattern 7/9 — replaces ChatClient.Builder.clone()."""

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


class SqlGenerationFailedError(Exception):
    pass


class SqlEvaluatorOptimizerService:
    def __init__(self, client: OpenAI):
        self.raw_client = client

        self.generator = RoleClient(
            client,
            system_prompt=(
                "You write SQL queries. Output only the SQL, no explanation. "
                "If feedback from a previous attempt is provided, fix exactly "
                "what it flags."
            ),
        )

        self.evaluator_system_prompt = (
            "You are a strict SQL reviewer. Check the query against the "
            "requirements. Flag: missing requirements, SQL injection risk "
            "from string concatenation patterns, inefficient patterns "
            "(e.g. SELECT * on large tables), and incorrect joins. "
            "Be specific and actionable in feedback — name the exact issue."
        )

    def _evaluate(self, requirements: str, query: str) -> EvaluationResult:
        completion = self.raw_client.chat.completions.parse(
            model="gpt-4o-mini",
            messages=[
                {"role": "system", "content": self.evaluator_system_prompt},
                {
                    "role": "user",
                    "content": f"Requirements:\n{requirements}\n\nQuery to review:\n{query}",
                },
            ],
            response_format=EvaluationResult,
        )
        return completion.choices[0].message.parsed

    def generate_query(self, requirements: str) -> str:
        current_query = None
        feedback = None

        for attempt in range(1, MAX_ITERATIONS + 1):

            # Generate (or regenerate with feedback from the previous round)
            if feedback is None:
                generation_prompt = f"Requirements:\n{requirements}"
            else:
                generation_prompt = (
                    f"Requirements:\n{requirements}\n\n"
                    f"Previous attempt:\n{current_query}\n\n"
                    f"Feedback to fix:\n{feedback}"
                )

            current_query = self.generator.ask(generation_prompt)

            # Evaluate
            evaluation = self._evaluate(requirements, current_query)

            if evaluation.passed:
                return current_query   # success — exit the loop

            feedback = evaluation.feedback

        # Exhausted attempts — let the caller know instead of returning silently
        raise SqlGenerationFailedError(
            f"Could not produce a passing query after {MAX_ITERATIONS} "
            f"attempts. Last feedback: {feedback}"
        )
```

### 3. FastAPI endpoint

```python
# main.py
import os
from dotenv import load_dotenv
from fastapi import FastAPI, Body, HTTPException
from openai import OpenAI

from sql_evaluator_optimizer import SqlEvaluatorOptimizerService, SqlGenerationFailedError

load_dotenv()

app = FastAPI()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
service = SqlEvaluatorOptimizerService(client)


@app.post("/api/eval-optimize/sql")
def generate(requirements: str = Body(..., embed=True)):
    try:
        return {"query": service.generate_query(requirements)}
    except SqlGenerationFailedError as e:
        raise HTTPException(status_code=422, detail=str(e))
```

Run it:

```bash
uvicorn main:app --reload --port 8080
```

---

## Try it

```bash
curl -X POST "http://localhost:8080/api/eval-optimize/sql" \
  -H "Content-Type: application/json" \
  -d '{"requirements": "Get all orders over $100 from the last 30 days, joined with customer name, sorted by date descending"}'
```

Round 1 might use `SELECT *` — the evaluator flags it as inefficient — round 2 generates explicit columns and passes. Trace through `generate_query`:

- **Attempt 1**: `feedback is None`, so the generator sees only the requirements. Evaluator flags `SELECT *` as inefficient — `evaluation.passed` is `False`.
- **Attempt 2**: `feedback` is now set, so the generation prompt includes the previous query *and* exactly what was flagged. The generator fixes that specific issue.
- **Attempt 2 evaluation**: passes — the loop returns `current_query` immediately, never reaching `MAX_ITERATIONS`.

---

## Mapping Java/Spring AI concepts to Python

| Spring AI / Java | Python | What it's really doing |
|---|---|---|
| `record EvaluationResult(boolean passed, String feedback)` | `class EvaluationResult(BaseModel): passed: bool; feedback: str` | The typed verdict the loop branches on |
| `.call().entity(EvaluationResult.class)` | `.parse(response_format=EvaluationResult)` → `.parsed` | The evaluator's structured critique call (Pattern 2, reused) |
| `builder.clone().defaultSystem(...)` × 2 | `RoleClient(client, system_prompt=...)` × 2 | Generator and evaluator as two independently-configured roles (Pattern 7/9's wrapper, reused) |
| `for (int attempt = 1; attempt <= MAX_ITERATIONS; attempt++)` | `for attempt in range(1, MAX_ITERATIONS + 1)` | The same bounded retry loop, identical semantics |
| `if (evaluation.passed()) return currentQuery;` | `if evaluation.passed: return current_query` | Early exit on success — the loop doesn't run needlessly once approved |
| `throw new IllegalStateException(...)` after exhausting attempts | `raise SqlGenerationFailedError(...)` after exhausting attempts | Both make failure loud and explicit rather than silently returning a possibly-broken result |
| Feedback string concatenated into the next generation prompt | Same f-string interpolation into the next generation prompt | Both feed the *specific* critique back in, not just "try again" |

**Key insight:** this pattern adds exactly one new idea beyond everything built so far — a loop with an **exit condition based on a structured verdict**, where each iteration's prompt is enriched with the previous iteration's failure. Structurally it's `while not evaluation.passed and attempts remain: regenerate with feedback`. Every individual call inside that loop is still just Pattern 1 (the generator) or Pattern 2 (the evaluator) — nothing about *how* you call the model changes; what's new is the control flow wrapped around those calls, written in completely ordinary Python (`for`, `if`, `raise`) exactly as it's ordinary Java (`for`, `if`, `throw`) in the original.

---

## When this pattern earns its cost

Anthropic's guidance on this is precise, and applies identically regardless of language: use evaluator-optimizer when you have clear evaluation criteria and when iterative refinement provides measurable value — code correctness, translation fidelity, structured document compliance. It's expensive (2× calls per iteration, up to `MAX_ITERATIONS` rounds) — don't reach for it on tasks where a single careful generation call already does well, or where "good" is too subjective for the evaluator to judge consistently.

**Real-world example of where this pattern is worth the cost vs. not:** compiling code is a great fit — "does it compile, does it pass the tests" is an unambiguous, checkable criterion, so an evaluator loop reliably converges on something actually correct. Judging "is this poem beautiful," on the other hand, has no fixed rubric — a second LLM call critiquing subjective taste isn't meaningfully more authoritative than the first call's taste, so the loop just burns tokens oscillating between two different but equally-valid opinions instead of converging on genuine improvement.

---

## Production notes

- Always cap iterations — an evaluator that's too strict (or miscalibrated) can loop forever without the limit. `MAX_ITERATIONS` as a plain module-level constant (shown above) is sufficient; no special guard mechanism needed beyond the `for` loop's own bound.
- Log every `(attempt, feedback)` pair — this is gold for debugging why the generator keeps failing, and often reveals the evaluator's criteria need tightening, not the generator. In Python, this is as simple as `logging.info(f"attempt={attempt} feedback={feedback}")` inside the loop.
- The evaluator's system prompt is the highest-leverage part of this pattern — vague criteria produce vague, unhelpful feedback that doesn't actually improve the next attempt. This is a prompt-engineering concern, not a language concern, and applies identically to `evaluator_system_prompt` in Python as it does to the Java version's evaluator system prompt.

---

## Project structure

```
pattern10-evaluator-optimizer/
├── main.py
├── sql_evaluator_optimizer.py
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
| 10 | Evaluator-Optimizer | ✅ Self-correcting generate → critique → regenerate loop (this doc) |
| 11 | Agents | Looped call/decide/act cycle with self-correction |

*(To be filled in as each pattern's source doc is provided.)*