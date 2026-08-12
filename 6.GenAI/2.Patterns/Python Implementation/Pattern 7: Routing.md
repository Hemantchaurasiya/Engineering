# Pattern 7: Routing — Python Version

Routing solves a different problem than chaining: instead of a fixed sequence of steps, you have different *kinds* of requests that each deserve specialized handling. A router call classifies the incoming request first, then dispatches it to whichever specialized prompt, model, or tool-set fits best — rather than forcing one generic prompt to handle every case mediocrely.

This is a Python/FastAPI translation of the original Java/Spring AI pattern, preserving the same structure and behavior.

---

## What's happening

The router call is a cheap, fast classification step — a structured-output call (Pattern 2) returning a simple enum. Based on that classification, you dispatch to a separate, pre-configured "client" (different system prompt, different tools, sometimes even a different underlying model — e.g. a fast/cheap model for simple queries, a stronger model for complex ones). This is more reliable than one generic prompt trying to be good at everything simultaneously.

### The core concept — why routing beats one generic prompt

A single system prompt trying to cover "billing questions, technical troubleshooting, and general chit-chat" has to hedge its instructions to avoid contradicting itself across cases — "be precise about numbers, but also feel free to troubleshoot, but also be friendly and casual" is a muddled set of instructions compared to three separate, sharply-focused ones. Routing lets each branch's system prompt be written as if it's the *only* thing the assistant ever does, which produces much more confident, on-target behavior.

**Real-world analogy:** think of calling a company's main support phone line. The very first thing that happens isn't a generalist trying to solve your problem — it's an automated menu (or a receptionist) asking one quick question: "Is this about billing, a technical issue, or something else?" That triage step is fast and cheap compared to the actual specialist call that follows. Once routed, you're connected to someone whose entire job is billing, or entirely technical support — not a generalist trying to be adequate at everything. The router's only job is figuring out *who* should handle you; it never tries to solve your problem itself.

### The core concept — why "clone the builder" matters in Spring, and what replaces it in Python

In Spring AI, `ChatClient.Builder.clone()` lets each branch fork from a shared base configuration (model, default advisors) while overriding just the system prompt — so you don't re-declare shared setup four times. Python doesn't have a builder-clone pattern in the OpenAI SDK, but the underlying need is identical: **each branch needs its own system prompt without duplicating shared config.** The idiomatic Python fix is a small `SupportClient` wrapper class that holds a system prompt + shared client reference — you instantiate one per branch, and the "shared configuration" (the underlying `OpenAI` client, the model name) lives in one place and gets passed in, not copy-pasted.

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

---

## Code

### 1. Define the routing categories

Python's `enum.Enum` is the direct equivalent of a Java `enum` — and Pydantic understands `Enum` members natively as a structured-output field type, so no extra glue code is needed to use it with `.parse()`.

```python
# models.py
from enum import Enum
from pydantic import BaseModel


class QueryCategory(str, Enum):
    BILLING = "BILLING"
    TECHNICAL = "TECHNICAL"
    GENERAL = "GENERAL"


class RoutingDecision(BaseModel):
    category: QueryCategory
```

> **Why wrap the enum in `RoutingDecision`?** Same reason Pattern 2 needed a `RecipeList` wrapper for `List[Recipe]` — OpenAI's structured-output mode requires `response_format` to be an object schema, not a bare scalar/enum. Wrapping a single enum field in a small model is the idiomatic fix.

### 2. The router service — a small wrapper class replacing `.clone()`

```python
# support_router.py
from openai import OpenAI
from models import QueryCategory, RoutingDecision


class SupportClient:
    """
    Equivalent to one 'forked' ChatClient in the Java version: a fixed
    system prompt paired with the shared OpenAI client and model. Each
    branch gets one of these instead of re-declaring shared config.
    """

    def __init__(self, client: OpenAI, system_prompt: str, model: str = "gpt-4o-mini"):
        self.client = client
        self.system_prompt = system_prompt
        self.model = model

    def ask(self, user_query: str) -> str:
        response = self.client.chat.completions.create(
            model=self.model,
            messages=[
                {"role": "system", "content": self.system_prompt},
                {"role": "user", "content": user_query},
            ],
        )
        return response.choices[0].message.content


class SupportRouterService:
    def __init__(self, client: OpenAI):
        # Router: minimal system prompt, only job is classification.
        # Uses a cheap/fast model — classification is simpler than the
        # final answer, so it doesn't need the strongest model available.
        self.router_client = client

        # Each branch gets its own specialized system prompt, sharing the
        # same underlying OpenAI client — the Python equivalent of forking
        # ChatClient.Builder via .clone() while keeping common config centralized.
        self.billing_client = SupportClient(
            client,
            system_prompt=(
                "You are a billing support specialist. Be precise about "
                "charges, refunds, and subscription terms. Never guess "
                "at account-specific numbers — ask for an account ID if needed."
            ),
        )
        self.technical_client = SupportClient(
            client,
            system_prompt=(
                "You are a technical support engineer. Give step-by-step "
                "troubleshooting instructions. Ask for error messages or "
                "logs if the issue description is vague."
            ),
        )
        self.general_client = SupportClient(
            client,
            system_prompt="You are a friendly general support assistant.",
        )

    def _route(self, user_query: str) -> QueryCategory:
        completion = self.router_client.chat.completions.parse(
            model="gpt-4o-mini",
            messages=[
                {"role": "system", "content": "Classify the support query into exactly one category."},
                {"role": "user", "content": user_query},
            ],
            response_format=RoutingDecision,
        )
        return completion.choices[0].message.parsed.category

    def handle(self, user_query: str) -> str:
        # Step 1: route
        category = self._route(user_query)

        # Step 2: dispatch to the specialized handler
        target_client = {
            QueryCategory.BILLING: self.billing_client,
            QueryCategory.TECHNICAL: self.technical_client,
            QueryCategory.GENERAL: self.general_client,
        }[category]

        return target_client.ask(user_query)
```

### 3. FastAPI endpoint

```python
# main.py
import os
from dotenv import load_dotenv
from fastapi import FastAPI, Query
from openai import OpenAI

from support_router import SupportRouterService

load_dotenv()

app = FastAPI()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
router_service = SupportRouterService(client)


@app.get("/api/route/support")
def support(query: str = Query(...)):
    return {"answer": router_service.handle(query)}
```

Run it:

```bash
uvicorn main:app --reload --port 8080
```

---

## Try it

```bash
curl "http://localhost:8080/api/route/support?query=I%20was%20charged%20twice%20this%20month"
```

```text
→ routed to billing_client
```

```bash
curl "http://localhost:8080/api/route/support?query=My%20app%20crashes%20on%20startup"
```

```text
→ routed to technical_client
```

---

## Mapping Java/Spring AI concepts to Python

| Spring AI / Java | Python | What it's really doing |
|---|---|---|
| `enum QueryCategory { BILLING, TECHNICAL, GENERAL }` | `class QueryCategory(str, Enum): ...` | Defines the fixed set of routing outcomes |
| `.call().entity(QueryCategory.class)` | `.parse(response_format=RoutingDecision)` → `.parsed.category` | The router's structured classification call (this is Pattern 2, reused) |
| `builder.clone().defaultSystem("...").build()` per branch | `SupportClient(client, system_prompt="...")` instantiated per branch | Both fork a shared base config into a branch-specific one without re-declaring shared setup |
| `switch (category) { case BILLING -> billingClient; ... }` | `{QueryCategory.BILLING: self.billing_client, ...}[category]` | Both dispatch on the classification result to select the right handler |
| Four separate `ChatClient` fields on the service | Four `SupportClient` instances (`router_client`, `billing_client`, ...) held on `SupportRouterService` | Both keep the routing table and its branches together in one cohesive service object |

**Key insight:** routing is just **chaining (Pattern 6) with a branch instead of a fixed sequence.** Step 1 is still a structured-output call (Pattern 2); Step 2 is still a plain-text call (Pattern 1) — the only new idea is that *which* Step 2 configuration runs is chosen by Step 1's result via ordinary dictionary/switch dispatch, rather than always being the same call. There's no special "router" primitive in either language; it's a classification call plus an `if`/`switch`/dict-lookup, exactly like the gate check in Pattern 6 was just an `if` statement.

---

## Production notes

- The router call itself can use a cheaper/faster model than the specialized handlers — classification is a simpler task than generating the final answer, so you don't need your most expensive model for it. In the Python version, this just means passing a different `model=` string to the router's `.parse()` call versus each `SupportClient`'s `model` attribute.
- For low-stakes routing, you can skip an LLM call entirely and use rule-based routing (regex, keyword matching) when categories are simple and well-defined — reserve LLM-based routing for genuinely ambiguous classification. In Python this is just a plain function returning a `QueryCategory` without ever touching the OpenAI client.
- Always include a fallback branch (`GENERAL` above) — the classifier will occasionally be wrong, or the query won't cleanly fit any bucket. Because the dispatch above is a plain dict lookup, add a `.get(category, self.general_client)` instead of a bare `[category]` if you want a defensive fallback even when the classifier returns something unexpected.

---

## Project structure

```
pattern7-routing/
├── main.py
├── support_router.py
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
| 7 | Routing | ✅ Classify-then-dispatch to specialized handlers (this doc) |
| 8 | Agents | Looped call/decide/act cycle with self-correction |

*(To be filled in as each pattern's source doc is provided.)*