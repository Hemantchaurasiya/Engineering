## Pattern 4 — Few-Shot Prompting

**Depends on:** Pattern 3 (Structured Prompt Pattern)

### Problem
Telling a model *what* to do in words is often not enough to pin down *how* — tone, exact format, edge-case handling, level of detail. Instructions alone leave too much to interpretation, especially for tasks with a specific house style or a narrow output shape the model hasn't reliably seen.

### Motivation
Models are extremely good at pattern-matching from examples — often better than following abstract verbal rules. Showing 1–5 concrete input→output pairs anchors the model's behavior far more reliably than describing the desired behavior in prose. This is literally how the model was trained (next-token prediction from examples), so it's a very natural lever to pull.

### Core Idea
Add an `<examples>` section (built on the Structured Prompt pattern) containing a handful of representative `input → output` pairs *before* the real input. The model infers the pattern and applies it to the new input.

```text
Example 1: input=A → output=X
Example 2: input=B → output=Y
Example 3: input=C → output=Z
Real input: input=D → output=?   (model infers the pattern)
```

### Architecture

```text
┌─────────────────────────────────────────────┐
│              Few-Shot Prompt                    │
│  <role> / <task>  (from Pattern 3)               │
│  <examples>                                       │
│    Example 1: input → output                     │
│    Example 2: input → output                     │
│    Example 3: input → output                     │
│  </examples>                                      │
│  <input>  ← the real, new input                   │
│  <output_format>                                  │
└─────────────────────┬─────────────────────────┘
                      ▼
                LLM Client (Pattern 1)
                      ▼
        Output pattern-matches example style
```

### Internal Flow
1. Curate a small, high-quality set of example pairs — quality and diversity matter far more than quantity.
2. Format them consistently (same structure the real output should follow).
3. Insert them into a structured prompt, before the real input.
4. Call the LLM; the model's in-context learning does the rest — no weight updates, nothing persisted.
5. Track which example set/version was used for evaluation later, same as prompt templates.

### Simple Implementation

```python
EXAMPLES = [
    {"input": "The app crashed when I opened it.", "output": {"category": "technical", "urgency": 4}},
    {"input": "Can I get a refund for last month?", "output": {"category": "billing", "urgency": 2}},
    {"input": "I forgot my password.", "output": {"category": "account", "urgency": 1}},
]

def build_few_shot_prompt(examples: list[dict], new_input: str) -> str:
    example_blocks = "\n".join(
        f'Input: "{ex["input"]}"\nOutput: {ex["output"]}' for ex in examples
    )
    return f"""Classify support tickets into category (billing/technical/account) and urgency (1-5).

{example_blocks}

Input: "{new_input}"
Output:"""

print(build_few_shot_prompt(EXAMPLES, "My invoice shows the wrong amount."))
```

### Production Implementation

```python
import json
import logging
from dataclasses import dataclass, field
from typing import Any

from llm_client import LLMClient  # Pattern 1

logger = logging.getLogger("genai.few_shot")


@dataclass
class FewShotExample:
    input_text: str
    output: dict[str, Any]


@dataclass
class FewShotPrompt:
    role: str
    task: str
    output_format: str
    examples: list[FewShotExample] = field(default_factory=list)
    user_input: str = ""

    def render(self) -> str:
        parts = [f"<role>{self.role}</role>", f"<task>{self.task}</task>"]

        if self.examples:
            example_blocks = []
            for i, ex in enumerate(self.examples, start=1):
                example_blocks.append(
                    f'<example index="{i}">\n'
                    f"  <input>{ex.input_text}</input>\n"
                    f"  <output>{json.dumps(ex.output)}</output>\n"
                    f"</example>"
                )
            parts.append("<examples>\n" + "\n".join(example_blocks) + "\n</examples>")

        parts.append(f"<input>\n{self.user_input}\n</input>")
        parts.append(f"<output_format>\n{self.output_format}\n</output_format>")
        return "\n\n".join(parts)


class ExampleStore:
    """Curated, versioned example sets — kept out of application code
    so a domain expert can update examples without a deploy, and so we
    can A/B test example sets against each other (Prompt Evaluation, later)."""

    def __init__(self):
        self._sets: dict[str, list[FewShotExample]] = {}

    def register(self, name: str, examples: list[FewShotExample]) -> None:
        self._sets[name] = examples
        logger.info("example_set_registered name=%s count=%d", name, len(examples))

    def get(self, name: str) -> list[FewShotExample]:
        return self._sets[name]


async def classify_ticket(llm: LLMClient, store: ExampleStore, ticket_text: str) -> dict:
    prompt = FewShotPrompt(
        role="a support ticket classifier",
        task="Classify the ticket by category (billing/technical/account) and urgency (1-5).",
        examples=store.get("ticket_classification_v2"),
        user_input=ticket_text,
        output_format='{"category": "...", "urgency": N}',
    )
    result = await llm.invoke(prompt.render())
    return json.loads(result["text"])


store = ExampleStore()
store.register("ticket_classification_v2", [
    FewShotExample("The app crashed when I opened it.", {"category": "technical", "urgency": 4}),
    FewShotExample("Can I get a refund for last month?", {"category": "billing", "urgency": 2}),
    FewShotExample("I forgot my password.", {"category": "account", "urgency": 1}),
])
```

### Real-World Use Case
An e-commerce company generating product descriptions gives the model 4 examples of their exact brand voice (short, punchy, specific fabric/material callouts, no exclamation marks) rather than trying to describe "our brand voice" in prose. New product descriptions consistently match house style because the model is pattern-matching against real samples, not interpreting an abstract style guide.

### Advantages
- Dramatically improves output consistency for style- or format-sensitive tasks.
- No training/fine-tuning required — works instantly, per-request.
- Easy to iterate: swap in a different example set and observe the effect immediately.

### Disadvantages / Failure Modes
- Costs real tokens on every single call — examples aren't free, and this compounds at scale (motivates **Prompt Caching**, later).
- Bad or non-representative examples actively hurt output quality — the model will faithfully copy an example's mistake.
- Too many examples can crowd out the actual task context, and very long few-shot blocks risk the **lost-in-the-middle problem** (Context Engineering, later) where the model pays less attention to content buried in the middle of a long prompt.
- If examples are too similar to each other, the model may overfit to a narrow pattern and fail to generalize to genuinely different real inputs.

### When NOT to Use It
- The task is already well-specified by simple instructions (e.g., "translate to French") — zero-shot is cheaper and just as good.
- Extremely token-cost-sensitive, high-volume endpoints where every extra hundred tokens matters at scale — consider fine-tuning instead if the pattern is stable and volume is very high (see Fine-Tuning vs Prompting, later).
- The model is already highly reliable zero-shot on this task — added examples add cost with no measurable quality gain (verify with eval, don't assume).

### Trade-off vs Zero-Shot
| Aspect | Zero-Shot (next pattern) | Few-Shot |
|---|---|---|
| Token cost per call | Lowest | Higher (examples add up) |
| Output consistency | Lower, more variance | Higher, anchored to examples |
| Setup effort | None | Curate + maintain example set |
| Best for | Simple, well-understood tasks | Style-sensitive or format-strict tasks |

### Exercise
Take the `ExampleStore` above and add a method `get_best_k(name: str, k: int)` that returns only the *k* most relevant examples for a given new input (e.g., via simple keyword overlap, or — foreshadowing later patterns — embedding similarity). This "dynamic few-shot selection" idea is exactly what production RAG-driven few-shot systems do instead of using a fixed static example set for every request.

Next up: **Pattern 5 — Zero-Shot Prompting** (and a direct comparison table against what we just built).

---
