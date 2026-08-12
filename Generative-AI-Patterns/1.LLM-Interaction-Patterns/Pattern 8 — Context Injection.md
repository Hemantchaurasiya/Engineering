## Pattern 8 — Context Injection

**Depends on:** Pattern 3 (Structured Prompt Pattern)

### Problem
A model's pretrained knowledge is frozen at training time and contains nothing about your private data — your company's docs, a specific user's account state, today's date, live data. Without deliberately injecting that information into the prompt, the model either hallucinates plausible-sounding answers or honestly says it doesn't know. Context Injection is the foundational technique that makes RAG, tool results, and agent memory all actually work — it's "how outside information gets into the model's field of view," in its simplest form.

### Motivation
The model only ever "knows" what's in its context window for a given call. So any fact that must be grounded — current account balance, today's date, a specific document's contents, a previous turn's decision — has to be explicitly placed into the prompt by your application code before the call. This sounds obvious once stated, but a huge share of "the AI is hallucinating" bug reports trace back to teams assuming the model has context it was never actually given.

### Core Idea
Retrieve or compute the relevant facts *outside* the model (from a database, an API, a file, a prior turn), then inject them into a clearly delimited section of the prompt (reusing the `<context>` tag from Pattern 3) before sending the request.

```text
[External source] → [App code fetches data] → [Inject into <context>] → [LLM call]
     (DB / API /                                                          
      file / prior turns)
```

This is distinct from *retrieval* (which is about *finding* the right data — covered exhaustively in the RAG section) — Context Injection is the simpler, more general mechanic of "getting known data into the prompt," which retrieval is just one sophisticated way of feeding.

### Architecture

```text
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│   Database    │   │  External API │   │ Prior Turns    │
│  (user record) │   │  (live prices) │   │ (conversation)  │
└──────┬───────┘   └──────┬───────┘   └──────┬───────┘
       └──────────────────┼──────────────────┘
                          ▼
              ┌─────────────────────┐
              │  Context Assembler   │  ← formats + truncates + orders
              └───────────┬─────────┘
                          ▼
              <context>...</context>   ← injected into structured prompt
                          ▼
                    LLM Client (Pattern 1)
```

### Internal Flow
1. Identify what facts the model needs but doesn't inherently have (account data, current date, a document, prior conversation).
2. Fetch each piece from its source (DB query, API call, session state).
3. Format the fetched data into readable, labeled text (raw JSON dumps are usually worse than a clean summary — the model has to "parse" JSON mentally, which costs quality on complex payloads).
4. Inject into the `<context>` block, keeping it clearly separated from `<input>` (user data) and instructions.
5. Send the request — the model now reasons over facts it was never trained on.
6. Log what was injected (for debugging "why did it say that" later — this becomes essential once context sources multiply).

### Simple Implementation

```python
from datetime import date

def build_context_block(user_name: str, account_tier: str, open_tickets: int) -> str:
    return f"""Current date: {date.today().isoformat()}
User: {user_name}
Account tier: {account_tier}
Open support tickets: {open_tickets}"""

def build_prompt(context: str, question: str) -> str:
    return f"""<context>
{context}
</context>

<input>
{question}
</input>

Answer the user's question using only the information in <context>. If the
context doesn't contain what's needed, say so explicitly rather than guessing."""

ctx = build_context_block("Priya Sharma", "Enterprise", open_tickets=2)
print(build_prompt(ctx, "Do I have any open support requests right now?"))
```

### Production Implementation

```python
import logging
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Callable, Awaitable

from llm_client import LLMClient  # Pattern 1

logger = logging.getLogger("genai.context_injection")


@dataclass
class ContextSource:
    name: str
    fetch: Callable[[], Awaitable[str]]   # returns pre-formatted text
    required: bool = True                  # fail the whole call if this source errors?


@dataclass
class AssembledContext:
    sections: dict[str, str] = field(default_factory=dict)

    def render(self) -> str:
        blocks = [f"### {name}\n{text}" for name, text in self.sections.items()]
        return "\n\n".join(blocks)


class ContextAssembler:
    """Pulls context from multiple live sources, tolerates optional-source
    failures, and produces one clean, labeled block for the prompt. This is
    the piece that later grows into a full RAG retrieval pipeline — for now
    it's deliberately simple: fetch, format, inject."""

    def __init__(self, sources: list[ContextSource]):
        self.sources = sources

    async def assemble(self) -> AssembledContext:
        result = AssembledContext()
        for source in self.sources:
            try:
                text = await source.fetch()
                result.sections[source.name] = text
            except Exception as e:
                logger.warning("context_source_failed name=%s error=%s", source.name, e)
                if source.required:
                    raise
                # optional source: skip it, degrade gracefully
        return result


async def fetch_account_info(user_id: str) -> str:
    # In production: real DB/service call
    return "Account tier: Enterprise\nOpen support tickets: 2\nRenewal date: 2026-11-01"


async def fetch_current_datetime() -> str:
    return datetime.now(timezone.utc).strftime("Current UTC date/time: %Y-%m-%d %H:%M")


async def answer_account_question(llm: LLMClient, user_id: str, question: str) -> str:
    assembler = ContextAssembler(sources=[
        ContextSource(name="account_info", fetch=lambda: fetch_account_info(user_id)),
        ContextSource(name="datetime", fetch=fetch_current_datetime, required=False),
    ])
    context = await assembler.assemble()

    prompt = f"""<context>
{context.render()}
</context>

<input>
{question}
</input>

Answer using only the information in <context>. If it's not there, say you don't have that information."""

    result = await llm.invoke(prompt)
    logger.info("context_injected sources=%s", list(context.sections.keys()))
    return result["text"]
```

### Real-World Use Case
A banking chatbot answering "what's my current balance?" doesn't rely on the model "knowing" anything — it fetches the real balance from the account service, injects it into `<context>`, and the model's only job is to phrase a natural-language answer grounded in that fetched number. If the balance-fetch fails, the assembler (marked `required=True` for that source) fails the whole call rather than letting the model guess a balance — a critical safety property for financial data.

### Advantages
- Directly solves the "model doesn't know my private/live data" problem without any fine-tuning.
- Keeps facts auditable — you can log exactly what was injected and trace any wrong answer back to its source data.
- Composable: multiple independent sources (DB, API, prior turns) can all feed the same context block.

### Disadvantages / Failure Modes
- Naive injection of large raw payloads (e.g., an entire JSON API response) wastes tokens and can bury the relevant fact in noise — format and trim before injecting, don't dump raw data.
- If a required source silently fails and there's no proper error handling, the model may answer from its (uninformed) prior knowledge instead of failing loudly — always distinguish "no data available" from "data unavailable due to error."
- Context grows unbounded as you add more sources — this is exactly the problem **Context Window Management** and **Context Compression** (Context Engineering section, later) exist to solve.
- Injecting stale cached data as if it were live is a subtle correctness bug — be deliberate about cache TTLs on anything time-sensitive.

### When NOT to Use It
- The fact is genuinely static and well-represented in the model's training data (e.g., general world knowledge) — no need to fetch and inject what the model already reliably knows.
- The "context" would be the entire contents of a large document corpus — that's a retrieval problem, not a direct-injection problem; use RAG instead (RAG Patterns section).

### Trade-off vs Retrieval (RAG)
| Aspect | Context Injection (this pattern) | RAG (later section) |
|---|---|---|
| Data source | Known, specific facts (account, date, API result) | Large, unstructured corpora — retrieval finds *which* chunks matter |
| Selection logic | Deterministic fetch (you know exactly what to pull) | Similarity search / ranking (you don't know exactly what's relevant in advance) |
| Complexity | Low | Higher (chunking, embeddings, indexing, re-ranking) |
| When to use | Small number of known, structured facts | Large, unstructured, or growing knowledge base |

### Exercise
Add a third `ContextSource` to the example above, `fetch_recent_orders(user_id)`, that returns the user's last 3 orders formatted as a short list. Then write a test that mocks `fetch_account_info` to raise an exception and confirms `ContextAssembler.assemble()` correctly re-raises (since it's `required=True`) instead of silently producing an incomplete context block — this distinction between graceful degradation and hard failure is exactly the judgment call you'll need throughout the Reliability Patterns section later.

Next up: **Pattern 9 — Instruction Hierarchy**.

---
