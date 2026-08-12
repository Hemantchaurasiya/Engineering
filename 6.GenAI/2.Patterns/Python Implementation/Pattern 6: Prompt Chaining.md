# Pattern 6: Prompt Chaining — Python Version

This is the first true "workflow" pattern. Instead of one mega-prompt asking the model to do everything at once, you decompose the task into a sequence of focused LLM calls, where each step's output becomes the next step's input. Each call is simpler and more reliable than one giant call trying to do it all — and you can validate or transform data between steps using regular Python code.

This is a Python/FastAPI translation of the original Java/Spring AI pattern, preserving the same structure and behavior.

---

## What's happening

Each LLM call is a plain Python function: it takes input, calls the OpenAI client, returns output. You wire them together with normal function composition — no special framework magic needed. Because each step's output type can be a regular Pydantic model (using `.parse()` from Pattern 2), you can insert validation, branching, or early-exit logic between steps using ordinary `if` statements — something a single giant prompt can't give you.

### The core concept — why decompose at all?

A single prompt asking a model to "extract key points, draft a post, and polish it" is asking one pass of the model to juggle three different *jobs* at once: research/extraction, creative drafting, and critical editing. These are different cognitive modes, and a model — like a person — does better at each one when it isn't also trying to do the other two simultaneously. Splitting the work into sequential, narrowly-scoped calls means each call only has to be good at *one* thing, and you get a checkpoint between each stage where ordinary code can inspect, validate, or reject the output before moving on.

**Real-world analogy:** think of how a magazine article actually gets made. It isn't one person doing research, writing, and copyediting all in a single sitting — it's a **researcher** who gathers key facts, a **writer** who turns those facts into a draft, and an **editor** who polishes the draft's tone and flow, each handing off their work to the next. Each person does one job well, and — critically — an editor at any stage can send the work *back* if it's not good enough ("these aren't real facts, go find better ones") instead of the whole article silently coming out wrong with no one able to tell which stage caused the problem. Prompt chaining gives your code that same handoff structure, with ordinary `if` statements playing the role of the editor who can reject a bad handoff.

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

No new dependencies beyond what Pattern 2 (structured output) already introduced — chaining is just composition of calls you already know how to make.

---

## Code

Use case: turn a rough topic into a polished blog post, via 3 sequential, focused calls — identical structure to the Java version.

### 1. The structured type for Step 1

```python
# models.py
from pydantic import BaseModel
from typing import List


class KeyPoints(BaseModel):
    points: List[str]
```

### 2. The chain service

```python
# blog_post_chain.py
from openai import OpenAI
from models import KeyPoints


class BlogPostChainService:
    def __init__(self, client: OpenAI):
        self.client = client

    def generate_blog_post(self, topic: str) -> str:

        # Step 1: extract structured key points to write about
        completion = self.client.chat.completions.parse(
            model="gpt-4o-mini",
            messages=[{
                "role": "user",
                "content": f"List 4-5 key points worth covering in a blog post about: {topic}",
            }],
            response_format=KeyPoints,
        )
        key_points = completion.choices[0].message.parsed

        # --- gate check: plain Python, no LLM call needed ---
        if len(key_points.points) < 3:
            raise ValueError(f"Not enough substance to write about: {topic}")

        # Step 2: draft the post from those points
        bullet_list = "\n- ".join(key_points.points)
        draft_response = self.client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[{
                "role": "user",
                "content": (
                    f"Write a draft blog post covering these points:\n"
                    f"- {bullet_list}\n"
                    f"Keep it informative but conversational, ~300 words."
                ),
            }],
        )
        draft = draft_response.choices[0].message.content

        # Step 3: polish tone, fix flow — operates only on step 2's output
        polish_response = self.client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[{
                "role": "user",
                "content": (
                    "Improve this draft's tone, flow, and clarity.\n"
                    "Keep the same structure and length. Return only the improved text.\n\n"
                    f"Draft:\n{draft}"
                ),
            }],
        )
        polished = polish_response.choices[0].message.content

        return polished
```

### 3. The FastAPI endpoint

```python
# main.py
import os
from dotenv import load_dotenv
from fastapi import FastAPI, Query, HTTPException
from openai import OpenAI

from blog_post_chain import BlogPostChainService

load_dotenv()

app = FastAPI()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
chain_service = BlogPostChainService(client)


@app.get("/api/chain/blog-post")
def generate(topic: str = Query(...)):
    try:
        return {"post": chain_service.generate_blog_post(topic)}
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))
```

Run it:

```bash
uvicorn main:app --reload --port 8080
```

---

## Try it

```bash
curl "http://localhost:8080/api/chain/blog-post?topic=why%20vector%20databases%20matter"
```

Trace through `generate_blog_post`:

- **Step 1** calls the model once, gets back a typed `KeyPoints` object (Pattern 2's structured output) — no free text to parse.
- **Gate check** runs in plain Python — if the model returned too few points, the function raises immediately, **before spending tokens on drafting a post about a topic with no substance.**
- **Step 2** calls the model again, this time with a plain-text prompt built from Step 1's structured points — its only job is drafting.
- **Step 3** calls the model a third time, its only job is polishing Step 2's draft — it never sees the original topic or the key points, only the draft, keeping its scope narrow.

---

## Mapping Java/Spring AI concepts to Python

| Spring AI / Java | Python | What it's really doing |
|---|---|---|
| `record KeyPoints(List<String> points)` | `class KeyPoints(BaseModel): points: List[str]` | The typed shape passed between Step 1 and the gate check |
| `.call().entity(KeyPoints.class)` | `.parse(response_format=KeyPoints)` → `.choices[0].message.parsed` | Step 1's structured extraction (this is Pattern 2, reused) |
| `if (keyPoints.points().size() < 3) throw ...` | `if len(key_points.points) < 3: raise ValueError(...)` | The gate check — ordinary code, no LLM call, sitting *between* two LLM calls |
| `.call().content()` (Steps 2 & 3) | `.create(...)` → `.choices[0].message.content` | Plain-text generation calls (this is Pattern 1, reused) |
| Method composition: `generateBlogPost()` calling `chatClient` three times | Function composition: `generate_blog_post()` calling `self.client` three times | Both wire the chain together with ordinary method calls — no special "chain" API needed in either language |
| `@Service` + constructor-injected `ChatClient.Builder` | `class BlogPostChainService.__init__(self, client)` | Both wrap the chain's steps as a reusable, testable unit rather than inline route-handler logic |

**Key insight:** there is no "chain" object, decorator, or framework primitive in either version — a chain is just **calling Pattern 1 and Pattern 2 multiple times in a row, in the same function, passing each result into the next.** The entire pattern is a demonstration that once you have reliable single calls (Pattern 1) and reliable typed calls (Pattern 2), "workflow" is just regular programming — `if` statements, variables, and function calls — applied around them.

---

## Why chain instead of one big prompt?

| One mega-prompt | Chained calls |
|-----------------|---------------|
| Model juggles extraction + drafting + polishing all at once | Each call has a single, narrow job — higher accuracy per step |
| No way to validate mid-process | Gate checks between steps with regular Python (`if`/`raise`) |
| Hard to debug which part went wrong | Each step's output is independently inspectable/loggable — print or log `key_points`, `draft`, and `polished` separately |
| Can't swap models per step | Step 1 could use a cheap/fast model (e.g. `gpt-4o-mini`), Step 3 a stronger one (e.g. `gpt-4o`) — just change the `model=` string per call |

---

## When to use this pattern

Anthropic's own guidance applies directly here regardless of language: use prompt chaining when a task can be cleanly decomposed into fixed subtasks — the latency/cost tradeoff (multiple LLM calls instead of one) is worth it specifically because each step becomes more accurate and verifiable. Don't chain when a single well-crafted prompt reliably does the job — added complexity, latency, and API cost (three calls instead of one) isn't free in either ecosystem.

**Real-world example:** a support-ticket triage system that (1) extracts the customer's core complaint as structured data, (2) drafts a response, (3) checks the draft against company policy before sending — each stage can independently fail loudly (missing complaint data, a draft that contradicts policy) instead of silently producing a bad reply that nobody can trace back to which stage went wrong. Compare that to asking one prompt to "read this ticket, understand the complaint, write a policy-compliant response" — when it goes wrong, you have no visibility into *which* part of that compound instruction the model failed to satisfy.

---

## Project structure

```
pattern6-prompt-chaining/
├── main.py
├── blog_post_chain.py
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
| 6 | Prompt Chaining | ✅ Sequential, focused calls with gate checks in between (this doc) |
| 7 | Agents | Looped call/decide/act cycle with self-correction |

*(To be filled in as each pattern's source doc is provided.)*