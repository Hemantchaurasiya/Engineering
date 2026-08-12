## Pattern 11 — Structured Output / JSON Output

**Depends on:** Pattern 3 (Structured Prompt), Pattern 10 (Output Formatting) — and replaces the parse-and-retry approach from Pattern 3 with a stronger guarantee

### Problem
Patterns 3 and 10 handled malformed output by *asking nicely and validating after the fact* — parse the response, retry if it's not valid JSON. That works, but it's fundamentally probabilistic: the model can still wrap output in markdown fences, add a "Here's the JSON:" preamble, or produce near-valid JSON with a trailing comma. For production systems doing real data extraction at volume, "usually works, retry when it doesn't" isn't good enough.

### Motivation
Anthropic's **Structured Outputs** feature (GA on the Claude Developer Platform as of Feb 2026, for Sonnet, Opus, and Haiku 4.5+) solves this at the *inference* level rather than the prompting level: your JSON Schema is compiled into a grammar that constrains token generation directly, so the response is guaranteed to match — not "very likely to match." This eliminates an entire category of brittle parsing and retry code.

### Core Idea
Two complementary mechanisms, usable independently or together:

1. **JSON outputs** (`output_config.format`) — constrains Claude's *text response* to match a JSON Schema you provide. Use for data extraction, structured reports, API-shaped responses.
2. **Strict tool use** (`tools[].strict: true`) — guarantees that arguments Claude passes to a *tool call* exactly match that tool's input schema. Use for reliable function calling in agentic workflows (this becomes central in Pattern 12).

Both work via constrained decoding — the schema is compiled into a grammar and applied token-by-token during generation, not validated after the fact.

### Architecture

```text
┌───────────────────┐
│   JSON Schema        │  ← you define this once (or via Pydantic)
└─────────┬───────────┘
          ▼ compiled into a grammar, cached ~24h
┌───────────────────┐      ┌───────────────────┐
│   Prompt + schema    │ ──▶ │  Constrained decode │
│   (output_config)     │     │  (token-by-token)    │
└───────────────────┘      └─────────┬───────────┘
                                     ▼
                        Guaranteed schema-valid JSON
                        (no markdown fences, no preamble,
                         no missing/extra fields)
                                     ▼
                        Direct .model_validate() —
                        no parse-and-retry loop needed
```

### Internal Flow
1. Define your target shape as a JSON Schema — or, in Python, as a Pydantic model and derive the schema from it.
2. Pass it via `output_config={"format": {...}}` in the `messages.create()` call (no beta header needed now that it's GA).
3. Claude compiles the schema into a grammar and constrains generation to match it exactly.
4. Read `response.content[0].text` — it's guaranteed valid JSON matching your schema; parse it directly, no try/except needed for shape mismatches.
5. For tool-calling contexts, add `strict: true` to the tool definition instead — this constrains tool *arguments*, not the top-level text response.

### Simple Implementation

```python
import json
import anthropic

client = anthropic.Anthropic()

ticket_schema = {
    "type": "object",
    "properties": {
        "category": {"type": "string", "enum": ["billing", "technical", "account"]},
        "urgency": {"type": "integer", "minimum": 1, "maximum": 5},
    },
    "required": ["category", "urgency"],
    "additionalProperties": False,
}

response = client.messages.create(
    model="claude-sonnet-5",
    max_tokens=200,
    messages=[{"role": "user", "content": "Classify this ticket: 'My invoice shows the wrong amount.'"}],
    output_config={"format": {"type": "json_schema", "schema": ticket_schema}},
)

result = json.loads(response.content[0].text)  # guaranteed to match ticket_schema
print(result)
```

### Production Implementation (Pydantic-driven, no manual schema authoring)

```python
import logging
from typing import Literal

import anthropic
from pydantic import BaseModel

logger = logging.getLogger("genai.structured_output")


class TicketClassification(BaseModel):
    category: Literal["billing", "technical", "account"]
    urgency: int  # 1-5

    class Config:
        extra = "forbid"  # mirrors additionalProperties: False in the compiled schema


class StructuredOutputClient:
    """Wraps schema-constrained generation so callers get typed Pydantic
    objects back directly — no parse-and-retry loop, because the API
    guarantees the shape rather than merely requesting it."""

    def __init__(self, model: str = "claude-sonnet-5"):
        self.client = anthropic.AsyncAnthropic()
        self.model = model

    async def generate(self, prompt: str, schema_model: type[BaseModel], max_tokens: int = 500):
        response = await self.client.messages.create(
            model=self.model,
            max_tokens=max_tokens,
            messages=[{"role": "user", "content": prompt}],
            output_config={
                "format": {
                    "type": "json_schema",
                    "schema": schema_model.model_json_schema(),
                }
            },
        )
        raw = response.content[0].text
        logger.info("structured_output_call schema=%s in_tok=%d out_tok=%d",
                    schema_model.__name__, response.usage.input_tokens, response.usage.output_tokens)
        return schema_model.model_validate_json(raw)


async def classify_ticket(client: StructuredOutputClient, ticket_text: str) -> TicketClassification:
    prompt = f"Classify this support ticket:\n\n{ticket_text}"
    return await client.generate(prompt, TicketClassification)
```

Compare this to Pattern 3's `run_structured` function — that version needed a `max_attempts` retry loop and a `json.JSONDecodeError` catch specifically because the guarantee was soft. Here, the loop is gone entirely: the guarantee is enforced at generation time, not checked after the fact.

### Real-World Use Case
An invoice-processing pipeline extracts structured line items (`vendor`, `amount`, `due_date`, `category`) from scanned invoice text at high volume, feeding directly into an accounting system's database. Before structured outputs, this required a validate-then-retry loop that occasionally still failed after all retries, silently dropping invoices into a manual-review queue. With schema-constrained generation, malformed extractions are structurally impossible, which measurably reduced the manual-review backlog.

### Advantages
- Eliminates an entire category of parsing bugs (markdown fences, preambles, trailing commas, wrong types).
- Removes the need for retry-on-parse-failure loops in application code — simpler, more predictable pipelines.
- Pydantic integration means your schema *is* your Python type — no schema/code drift.
- Combinable with strict tool use in the same request for agentic pipelines that need both a final structured answer and reliable tool arguments along the way.

### Disadvantages / Failure Modes
- Schema compilation has complexity limits — very large or deeply nested schemas can hit compile-time limits or add latency on first use (cached ~24h afterward, so repeated calls with the same schema are cheap).
- The grammar constrains the *shape*, not the *semantic correctness* — the model can still put a plausible-but-wrong value in a correctly-shaped field (schema validity ≠ factual accuracy; you still need evaluation for correctness).
- Per Anthropic's documentation, compiled schema/grammar caches don't receive the same PHI protections as prompt/response content — sensitive values must stay in message content, never embedded in schema property names, enums, or regex patterns.
- Grammar constraints apply to Claude's direct text output, not to the contents of tool results or thinking blocks — worth knowing precisely what is and isn't covered when combining with thinking/tools.

### When NOT to Use It
- Free-form conversational responses where forcing a rigid schema would be actively counterproductive.
- Extremely simple one-off scripts where a quick prompt + manual `json.loads` in a try/except is genuinely sufficient and the reliability gain doesn't matter.
- When you need the model to reason at length before committing to an answer — remember grammar constraints apply to the final text; pair with thinking (Pattern 6) rather than trying to force reasoning to happen inside the constrained JSON itself.

### Trade-off vs Pattern 3's Parse-and-Retry
| Aspect | Parse-and-retry (Pattern 3) | Structured Outputs (this pattern) |
|---|---|---|
| Guarantee | Probabilistic — usually works | Enforced at generation time |
| Extra latency | Retry round-trips on failure | Small one-time schema compile (then cached) |
| Code complexity | Retry loop + exception handling | Direct `.model_validate_json()` |
| Model/version requirements | Works on any model | Requires structured-outputs-capable models |
| PHI handling | Normal prompt/response protections apply | Schema itself is cached outside normal protections — keep PHI out of schema definitions |

### Exercise
Take the `StructuredOutputClient` above and add a second schema, `InvoiceLineItem`, with fields `vendor: str`, `amount: float`, `due_date: str`, `category: Literal[...]`. Write a function that takes raw invoice OCR text and a list of expected vendors, and extracts a `list[InvoiceLineItem]` (hint: wrap the list in a schema like `{"items": [...]}`, since top-level JSON Schemas typically expect an object, not a bare array). This is a direct preview of the **Enterprise RAG** and **Document Intelligence** patterns later in the course.

---
