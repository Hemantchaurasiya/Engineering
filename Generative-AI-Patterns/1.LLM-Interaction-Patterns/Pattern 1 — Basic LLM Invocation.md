## Pattern 1 — Basic LLM Invocation

### Problem
Every GenAI system starts here: given input text, get a coherent, relevant response from a language model. It looks trivial, but it's the atomic unit every other pattern (RAG, agents, workflows) is built on — worth understanding before stacking abstractions on top.

### Core Idea
You send a sequence of messages (a conversation) to the model's API. The model is **stateless** — it remembers nothing between calls. Every call must carry its full context (system instructions + history + current input). The response comes back as structured content blocks, not a raw string.

### Architecture

```text
┌────────────┐      ┌───────────────┐      ┌────────────┐
│   Client    │ ──▶  │  Messages API  │ ──▶  │   Model    │
│ (your code) │      │ (api.anthropic)│      │  Weights   │
└────────────┘      └───────────────┘      └────────────┘
       ▲                                          │
       │              response.content[]          │
       └──────────────────────────────────────────┘
```

### Internal Flow
1. Build a `messages` list: `[{"role": "user", "content": "..."}]`
2. Optionally set a `system` prompt — instructions kept separate from the conversation.
3. Call the endpoint with `model`, `max_tokens`, `messages`.
4. Anthropic tokenizes input, runs inference, returns a `Message` object.
5. Read `response.content` — a list of blocks (usually `[TextBlock(text="...")]` for plain text).
6. `response.usage` gives `input_tokens` / `output_tokens` — track this from day one for cost visibility.

### Simple Implementation

```python
import anthropic

client = anthropic.Anthropic()  # reads ANTHROPIC_API_KEY from env

response = client.messages.create(
    model="claude-sonnet-5",
    max_tokens=1000,
    messages=[
        {"role": "user", "content": "Explain what a vector database is in 2 sentences."}
    ],
)

print(response.content[0].text)
print(f"Tokens in: {response.usage.input_tokens}, out: {response.usage.output_tokens}")
```

### Production Implementation

A real system needs typed config, async execution, timeouts/retries, logging, and cost tracking — not a bare API call.

```python
import asyncio
import logging
from dataclasses import dataclass

import anthropic
from anthropic import APIStatusError, APITimeoutError

logger = logging.getLogger("genai.llm")


@dataclass
class LLMConfig:
    model: str = "claude-sonnet-5"
    max_tokens: int = 1024
    timeout: float = 30.0
    max_retries: int = 3


class LLMClient:
    """Thin, observable wrapper around the raw API call — every later
    pattern (RAG, agents, routing) builds on this."""

    def __init__(self, config: LLMConfig | None = None):
        self.config = config or LLMConfig()
        self.client = anthropic.AsyncAnthropic(
            timeout=self.config.timeout,
            max_retries=self.config.max_retries,  # SDK handles backoff for 429/5xx
        )

    async def invoke(self, prompt: str, system: str | None = None) -> dict:
        try:
            response = await self.client.messages.create(
                model=self.config.model,
                max_tokens=self.config.max_tokens,
                system=system or anthropic.NOT_GIVEN,
                messages=[{"role": "user", "content": prompt}],
            )
        except APITimeoutError:
            logger.error("LLM call timed out after %.1fs", self.config.timeout)
            raise
        except APIStatusError as e:
            logger.error("LLM call failed: status=%s body=%s", e.status_code, e.response.text)
            raise

        text = response.content[0].text
        usage = {
            "input_tokens": response.usage.input_tokens,
            "output_tokens": response.usage.output_tokens,
        }
        logger.info("llm_call model=%s in_tok=%d out_tok=%d",
                    self.config.model, usage["input_tokens"], usage["output_tokens"])
        return {"text": text, "usage": usage}


async def main():
    logging.basicConfig(level=logging.INFO)
    llm = LLMClient()
    result = await llm.invoke(
        prompt="Summarize why context windows matter for RAG systems.",
        system="You are a precise technical writer. Be concise.",
    )
    print(result["text"])
    print(result["usage"])


if __name__ == "__main__":
    asyncio.run(main())
```

> Note: the SDK's `max_retries` already applies exponential backoff for transient failures (429/5xx). We build a *custom* retry/circuit-breaker layer later, for failures the SDK can't catch — like semantic validation errors.

### Real-World Use Case
**Support-ticket triage:** every incoming ticket text runs through a single invocation with a system prompt like *"Classify this ticket as billing / technical / account, and rate urgency 1–5."* No memory, no tools, no retrieval — just one clean input → output call. This covers most "AI features" bolted onto SaaS products; teams often reach for agents when this pattern alone would do the job.

### Advantages
- Simple, cheap, fast, trivially cacheable and testable.
- Fully stateless → horizontally scalable with zero coordination.

### Disadvantages / Failure Modes
- No memory across calls — every call re-pays the full context cost.
- No access to real-time or private data (hallucination risk outside training data).
- No structure guarantee — raw text output can be inconsistent (motivates **Structured Output**, a later pattern).

### When NOT to Use It
- Needs external/current data → use **RAG**.
- Needs multi-turn state → add **Conversation Memory**.
- Output must be machine-parseable → use **Structured Output / JSON mode**.
- Needs the model to take actions → use **Tool Calling / Agents**.

### Exercise
Extend `LLMClient` to expose a `.stream()` method using `client.messages.stream(...)` that yields tokens as they arrive instead of waiting for the full response. Try it, then we'll move to **Pattern 2: Prompt Template Pattern**.

---
