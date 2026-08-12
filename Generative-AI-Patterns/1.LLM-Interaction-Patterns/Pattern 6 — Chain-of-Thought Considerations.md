## Pattern 6 — Chain-of-Thought Considerations

**Depends on:** Pattern 5 (Zero-Shot Prompting)

### Problem
For tasks requiring multi-step reasoning (math, multi-constraint logic, debugging, planning), models jumping straight to a final answer produce more errors than models that work through intermediate steps first. But "just tell it to think step by step" is a stale mental model on current-generation systems — modern reasoning models like Claude Sonnet 5 have **adaptive thinking built in natively**, which changes how you should actually apply this pattern.

### Motivation
Chain-of-Thought (CoT) originated as a *prompting trick*: appending "Let's think step by step" to a prompt measurably improved reasoning accuracy on older models, because it forced the model to generate intermediate tokens that functioned as scratch-space before committing to an answer. That trick still has some value on prompts sent to non-reasoning models. But for current Claude models, thinking is a **first-class capability** the model can invoke adaptively — you generally don't need to beg for reasoning with magic phrases; you need to know when to let the model use its native thinking budget versus when to force a lighter-weight explicit reasoning structure yourself.

### Core Idea
There are two distinct techniques worth telling apart:

1. **Prompted CoT** — you explicitly ask the model to "show its reasoning" or "think step by step" in the visible output, useful when you *want* the reasoning trace as part of the deliverable (e.g., showing your work in a tutoring app) or when using a model without native extended thinking.
2. **Native/extended thinking** — the model reasons internally (in a separate thinking block, not mixed into the final answer) before producing its response, giving reasoning benefits without cluttering the user-facing output. This is what you want for most production tasks where the user only needs the *answer*, not the scratch work.

### Architecture

```text
Prompted CoT (older / non-reasoning models)
┌────────────┐    ┌──────────────────────┐    ┌───────────┐
│  Prompt +   │ ─▶ │  Model generates      │ ─▶ │  Final     │
│ "think step  │    │  reasoning INLINE     │    │  answer    │
│  by step"    │    │  in the visible text   │    │ (mixed in) │
└────────────┘    └──────────────────────┘    └───────────┘

Native Extended Thinking (current Claude models)
┌────────────┐    ┌──────────────────┐    ┌────────────────┐    ┌───────────┐
│   Prompt    │ ─▶ │  thinking block    │ ─▶ │  text block      │ ─▶ │  User sees  │
│             │    │  (internal, not     │    │  (final answer,  │    │  clean       │
│             │    │  necessarily shown) │    │  reasoning done)  │    │  answer      │
└────────────┘    └──────────────────┘    └────────────────┘    └───────────┘
```

### Internal Flow (Native Thinking, current Claude API)
1. You send a normal request; for models with adaptive thinking, the model decides internally whether the task warrants deeper reasoning — you don't have to trigger it with special phrasing.
2. For tasks where you want explicit control, the API supports a `thinking` parameter (with configurable effort/budget) — check current docs before hardcoding parameters, since these controls evolve between model versions.
3. The response can include a `thinking` content block separate from the final `text` block — you decide whether to surface the thinking block to end users (useful for transparency/debugging) or discard it (cleaner UX).
4. For non-reasoning contexts or simpler models, prompted CoT ("explain your reasoning before answering") remains a valid fallback technique.

### Simple Implementation (Prompted CoT — model-agnostic, works everywhere)

```python
def build_cot_prompt(question: str) -> str:
    return f"""<task>
Solve the problem below. First reason through it step by step,
then give your final answer on the last line as "Answer: <value>".
</task>

<input>
{question}
</input>"""

print(build_cot_prompt(
    "A support team resolves 12 tickets/hour. They start with 90 open tickets "
    "and receive 8 new tickets every hour. After how many hours will they clear the backlog?"
))
```

### Production Implementation (Native Extended Thinking via the Claude API)

```python
import logging
from dataclasses import dataclass

import anthropic

logger = logging.getLogger("genai.reasoning")


@dataclass
class ReasoningResult:
    answer: str
    thinking: str | None  # None if thinking wasn't requested or wasn't returned
    input_tokens: int
    output_tokens: int


class ReasoningClient:
    """Wraps the Messages API to cleanly separate the model's internal
    reasoning from its final answer — critical for both UX (don't dump
    scratch-work on users by default) and cost tracking (thinking tokens
    are billed and should be logged separately from answer tokens)."""

    def __init__(self, model: str = "claude-sonnet-5"):
        self.client = anthropic.AsyncAnthropic()
        self.model = model

    async def solve(self, prompt: str, expose_thinking: bool = False) -> ReasoningResult:
        response = await self.client.messages.create(
            model=self.model,
            max_tokens=2000,
            messages=[{"role": "user", "content": prompt}],
            # Extended thinking is configured via the `thinking` param on
            # supported models — check the current API docs for the exact
            # shape (budget/effort controls change between model versions).
        )

        thinking_text = None
        answer_text = ""
        for block in response.content:
            if block.type == "thinking":
                thinking_text = block.thinking
            elif block.type == "text":
                answer_text += block.text

        logger.info(
            "reasoning_call model=%s had_thinking=%s in_tok=%d out_tok=%d",
            self.model, thinking_text is not None,
            response.usage.input_tokens, response.usage.output_tokens,
        )

        return ReasoningResult(
            answer=answer_text.strip(),
            thinking=thinking_text if expose_thinking else None,
            input_tokens=response.usage.input_tokens,
            output_tokens=response.usage.output_tokens,
        )
```

### Real-World Use Case
A financial-analysis assistant that answers "should we approve this loan application given these 6 risk factors?" benefits enormously from reasoning through each factor before concluding — a direct zero-shot answer without reasoning is measurably less reliable on this kind of multi-constraint judgment call. The reasoning trace itself can also be logged (not necessarily shown to the end customer) so a human underwriter can audit *why* the model reached its conclusion — an important trait for regulated domains.

### Advantages
- Meaningfully improves accuracy on multi-step reasoning, math, and multi-constraint decisions.
- With native thinking, you get reasoning benefits without polluting the user-facing answer.
- The reasoning trace, when logged, becomes a valuable debugging and audit artifact.

### Disadvantages / Failure Modes
- Reasoning tokens cost money and add latency — using it on trivial tasks (simple classification) is pure waste.
- A plausible-looking reasoning trace doesn't guarantee a correct answer — the model can reason confidently to a wrong conclusion (don't treat the presence of reasoning as proof of correctness).
- Prompted CoT (the "think step by step" trick) can be actively counterproductive on tasks that are already simple for the model — it can introduce unnecessary hedging or overthinking.
- Exposing raw thinking traces to end users can leak more than intended (draft reasoning, uncertainty, discarded approaches) — decide deliberately whether thinking is user-facing or internal-only.

### When NOT to Use It
- Simple classification, extraction, or lookup tasks with no real reasoning chain — it adds cost/latency for no accuracy gain.
- Latency-critical paths (e.g., real-time autocomplete) where the extra reasoning time isn't acceptable.
- When you've evaluated and confirmed the task doesn't actually benefit — don't apply CoT reflexively to every prompt.

### Comparison: Prompted CoT vs Native Extended Thinking

| Aspect | Prompted CoT | Native Extended Thinking |
|---|---|---|
| How it's invoked | Explicit phrasing in the prompt | Model-native capability / API parameter |
| Reasoning visibility | Always inline with the answer | Separate block — you choose to expose or hide |
| Model support | Works on any model | Requires a reasoning-capable model |
| Output cleanliness | Reasoning mixed into user-facing text | Clean final answer, reasoning optional |
| Best for | Non-reasoning models, or when reasoning trace IS the deliverable | Most production reasoning tasks on current models |

### Exercise
Extend `ReasoningClient.solve` with a `min_confidence_check` step: after getting the answer, make a *second*, cheap LLM call asking the model to rate its own confidence (1–5) in the answer it just gave, given the same question and answer. Log cases where confidence is low — this is a lightweight precursor to the **Self-Consistency** and **Generate → Critique → Refine** patterns coming up later in Prompt Engineering.

Next up: **Pattern 7 — Role Prompting**.

---
