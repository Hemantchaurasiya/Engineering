# Pattern 12: Reflection — Python Version

Reflection is the model critiquing and improving its own work, in the same conversational thread — distinct from Evaluator-Optimizer's strict pass/fail loop with a dedicated, separately-prompted evaluator. Reflection works well for open-ended quality (writing, design, code style) where there's no crisp binary criterion, just "is this actually good," and the same model can usually catch its own mistakes when explicitly asked to look back at its own work.

This is a Python/FastAPI translation of the original Java/Spring AI pattern, preserving the same structure and behavior.

---

## What's happening

Unlike Evaluator-Optimizer (Pattern 10), there's no separate evaluator client with strict pass/fail output — it's the same model, same conversation thread, just prompted at each turn to look back critically at what it just wrote. The `messages` list itself carries the context: the model sees its own draft as a prior `assistant` message and is asked to find problems with it, then revise. This is cheaper to set up (no separate evaluator system prompt to design) but gives you less control than evaluator-optimizer's explicit criteria — good for creative/subjective quality, less ideal where you need deterministic gating.

### The core concept — why this works with only one "role," unlike Pattern 10

Pattern 10 needed two distinct roles (generator, evaluator) because the task had an objective, checkable criterion — the evaluator needed a fixed rubric to check against, and separating it from the generator kept that rubric from being contaminated by the generator's own assumptions. Reflection doesn't have an objective rubric to check against; "is this paragraph engaging" isn't something a stricter system prompt makes more checkable — it's inherently a judgment call. So instead of building a second role with a different system prompt, you just ask the *same* model, in the *same conversation*, to switch hats: "now look back at what you wrote and find the weak spots." The model can genuinely do this reasonably well, the same way a writer rereading their own draft the next morning (with fresh eyes) catches things they missed while writing it the first time.

**Real-world analogy:** think of a musician recording a demo, then playing it back and listening critically — "that transition is clunky, the bridge drags" — and re-recording with those specific notes in mind. It's the *same musician*, not a different producer brought in to grade the recording against a checklist. There's real value in this kind of self-review (fresh perspective after a pause, explicit permission to be self-critical), but it's also inherently softer than an outside producer's opinion — the musician might still be a little too fond of their own choices. That's exactly the tradeoff versus Evaluator-Optimizer: reflection is cheaper and works for subjective quality, but it's the same "judge" reviewing their own work, which caps how rigorous the critique can really be.

---

## Setup

### `requirements.txt`

```
fastapi==0.115.0
uvicorn==0.32.0
openai==1.54.0
python-dotenv==1.0.1
```

No new dependencies — this pattern is built entirely from Pattern 1's plain-text calls, looped over a growing `messages` list.

---

## Code

```python
# self_reflection.py
from openai import OpenAI

SYSTEM_PROMPT = "You are a skilled writer who holds high standards for your own work."

CRITIQUE_PROMPT = (
    "Critically review what you just wrote. Identify specific "
    "weaknesses: unclear sentences, weak word choices, logical "
    "gaps, or anything that doesn't serve the reader. Be honest "
    "and specific — don't just say it's fine if it isn't."
)

REVISION_PROMPT = (
    "Now rewrite your original piece, addressing the issues "
    "you just identified. Return only the revised text."
)


class SelfReflectionService:
    def __init__(self, client: OpenAI, model: str = "gpt-4o-mini"):
        self.client = client
        self.model = model

    def _call(self, conversation: list[dict]) -> str:
        response = self.client.chat.completions.create(
            model=self.model,
            messages=conversation,
        )
        return response.choices[0].message.content

    def write_with_reflection(self, writing_task: str, reflection_rounds: int = 1) -> str:

        conversation = [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": writing_task},
        ]

        # Initial draft
        current = self._call(conversation)
        conversation.append({"role": "assistant", "content": current})

        # Reflection rounds — same thread, model sees its own prior output
        for _ in range(reflection_rounds):

            # Step A: ask the model to critique its own last message
            conversation.append({"role": "user", "content": CRITIQUE_PROMPT})
            critique = self._call(conversation)
            conversation.append({"role": "assistant", "content": critique})

            # Step B: ask for a revision based on its own critique
            conversation.append({"role": "user", "content": REVISION_PROMPT})
            current = self._call(conversation)
            conversation.append({"role": "assistant", "content": current})

        return current
```

```python
# main.py
import os
from dotenv import load_dotenv
from fastapi import FastAPI, Query
from openai import OpenAI

from self_reflection import SelfReflectionService

load_dotenv()

app = FastAPI()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
reflection_service = SelfReflectionService(client)


@app.get("/api/reflect/write")
def write(task: str = Query(...), rounds: int = Query(default=1)):
    return {"result": reflection_service.write_with_reflection(task, rounds)}
```

Run it:

```bash
uvicorn main:app --reload --port 8080
```

---

## Try it

```bash
curl "http://localhost:8080/api/reflect/write?task=Write%20an%20opening%20paragraph%20for%20a%20blog%20post%20about%20remote%20work%20burnout&rounds=2"
```

Round-by-round, you'd typically see: draft 1 has generic openers ("In today's fast-paced world...") → self-critique flags the cliché → revision 1 opens with something more specific → second critique flags pacing → revision 2 tightens it further.

Trace through `write_with_reflection`'s `conversation` list as it grows:

```text
[0] system:    "You are a skilled writer..."
[1] user:      "Write an opening paragraph..."
[2] assistant: <draft 1 — the generic "fast-paced world" opener>
[3] user:      "Critically review what you just wrote..."
[4] assistant: <critique flagging the cliché>
[5] user:      "Now rewrite your original piece..."
[6] assistant: <revision 1 — more specific opener>
[7] user:      "Critically review what you just wrote..."          (round 2 begins)
[8] assistant: <critique flagging pacing>
[9] user:      "Now rewrite your original piece..."
[10] assistant: <revision 2 — tightened> <- returned as `current`
```

Every single one of those 5 calls (`self._call(conversation)`) is sent with the *entire* growing list — the model has to reread its own draft and its own critique each time, which is exactly what lets it "remember" what it was asked to fix.

---

## Mapping Java/Spring AI concepts to Python

| Spring AI / Java | Python | What it's really doing |
|---|---|---|
| `List<Message> conversation` with `SystemMessage`/`UserMessage`/`AssistantMessage` | `list[dict]` with `{"role": "system"/"user"/"assistant", ...}` | The same growing conversation thread — a plain list, appended to at every step |
| `conversation.add(new UserMessage(...))` | `conversation.append({"role": "user", "content": ...})` | Adds the next instruction (critique prompt, then revision prompt) to the thread |
| `chatClient.prompt(new Prompt(conversation)).call().content()` | `self._call(conversation)` → `.choices[0].message.content` | Sends the *entire* current thread and gets the next turn back (this is just Pattern 1, called repeatedly on a growing list) |
| `for (int round = 1; round <= reflectionRounds; round++)` | `for _ in range(reflection_rounds)` | The same fixed-count reflection loop |
| Two-step inner loop: critique message, then revision message | Same two-step inner loop: `CRITIQUE_PROMPT`, then `REVISION_PROMPT` | Both explicitly separate "look back and find flaws" from "now fix them," rather than asking for both at once |

**Key insight:** reflection isn't a new *mechanism* at all — it's Pattern 1's single call (`messages` → `.create()`) called repeatedly against a **conversation that never resets**, where each new call is really just "the same conversation with two more turns appended." Compare this to Pattern 10, where the evaluator was a *separate*, freshly-started conversation with its own system prompt every time — reflection deliberately keeps everything in one unbroken thread, because the whole point is that the model needs to see its own prior reasoning to critique it, not just the current draft in isolation.

---

## Reflection vs. Evaluator-Optimizer — side by side

|  | Evaluator-Optimizer (Pattern 10) | Reflection (Pattern 12) |
|---|---|---|
| **Roles** | Separate generator + evaluator clients, distinct system prompts (`RoleClient` × 2) | Same client, same growing `messages` list |
| **Stop condition** | Explicit `EvaluationResult.passed` from structured evaluator output | Fixed round count, or model self-reports "no more issues" |
| **Best for** | Tasks with checkable correctness (code, SQL, structured compliance) | Subjective quality (prose, tone, creative work) |
| **Control** | Tight — you can gate on specific criteria via a Pydantic model | Looser — depends on the model's own judgment of quality |

---

## Production notes

- Reflection rounds have diminishing returns fast — 1-2 rounds typically capture most of the gain; beyond that you're often just paying for cosmetic rewording. `reflection_rounds` defaults to `1` above for exactly this reason.
- Because everything lives in one growing `messages` list, token cost compounds — each round resends the entire history (system prompt + every draft + every critique so far). For long documents, consider trimming early critique turns (e.g. `conversation.pop(3)` after they've served their purpose) once a later revision has already addressed them, rather than letting the list grow unbounded.
- For tasks where you genuinely need a checkable pass/fail gate, prefer Evaluator-Optimizer (Pattern 10) — reflection's self-grading can be overly generous ("looks good to me") since the model is reviewing its own work without an independent, differently-instructed perspective. This is true in Python exactly as in Java, since the underlying limitation is about the *model*, not the framework.

---

## Project structure

```
pattern12-reflection/
├── main.py
├── self_reflection.py
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
| 12 | Reflection | ✅ Same-thread self-critique and revision (this doc) |

*(To be filled in as each pattern's source doc is provided.)*