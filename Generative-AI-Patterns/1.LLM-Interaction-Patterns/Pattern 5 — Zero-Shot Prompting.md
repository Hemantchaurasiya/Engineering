## Pattern 5 — Zero-Shot Prompting

**Depends on:** Pattern 3 (Structured Prompt Pattern), contrasts with Pattern 4 (Few-Shot)

### Problem
Few-shot prompting (Pattern 4) works well but costs tokens and maintenance overhead for every example set. For many tasks — especially ones modern large models already handle reliably — that cost buys nothing. You need a principled way to decide when you can skip examples entirely and just *ask*.

### Motivation
Modern frontier models (Claude Sonnet 5, Opus-class, etc.) have been instruction-tuned extensively, so a large share of tasks that used to require few-shot examples now work reliably with a clear, well-structured instruction alone. Zero-shot isn't "the lazy option" — it's the *default you should start from*, only escalating to few-shot when you've actually observed zero-shot underperforming.

### Core Idea
Give the model a clear task description, sufficient context, and explicit constraints/output format — but **no example input→output pairs**. Rely entirely on the model's pretrained + instruction-tuned understanding of the task.

```text
<role> + <task> + <constraints> + <output_format>  →  model infers behavior
                    (no <examples> block)
```

### Architecture

```text
┌───────────────────────────────────────┐
│           Zero-Shot Structured Prompt     │
│  <role>...</role>                          │
│  <task>...</task>       ← must be precise   │
│  <context>...</context> ← if needed          │
│  <constraints>...</constraints>              │
│  <output_format>...</output_format>          │
│      (no <examples> block)                   │
└───────────────────┬───────────────────┘
                    ▼
              LLM Client (Pattern 1)
                    ▼
         Output relies on instruction quality,
         not pattern-matching against samples
```

### Internal Flow
1. Write the task instruction with maximum precision — this pattern shifts *all* the burden of correctness onto instruction clarity, since there are no examples to fall back on.
2. Specify constraints and output format explicitly (reuse Pattern 3's structure).
3. Call the LLM directly — no example curation step.
4. Evaluate output quality. If it's inconsistent or wrong in a specific, recurring way, that's your signal to add few-shot examples targeting exactly that failure mode — not to add examples preemptively.

### Simple Implementation

```python
def build_zero_shot_prompt(ticket_text: str) -> str:
    return f"""<role>You are a support ticket classifier.</role>

<task>
Classify the ticket into exactly one category: billing, technical, or account.
Rate urgency from 1 (low) to 5 (critical), based on business impact and customer sentiment.
</task>

<input>
{ticket_text}
</input>

<output_format>
JSON only: {{"category": "...", "urgency": N}}
</output_format>"""

print(build_zero_shot_prompt("My invoice shows the wrong amount and I need this fixed before month end."))
```

### Production Implementation

```python
import json
import logging

from llm_client import LLMClient  # Pattern 1
from structured_prompt import StructuredPrompt  # Pattern 3

logger = logging.getLogger("genai.zero_shot")


async def classify_ticket_zero_shot(llm: LLMClient, ticket_text: str) -> dict:
    prompt = StructuredPrompt(
        role="a support ticket classifier",
        task=(
            "Classify the ticket into exactly one category: billing, technical, or account. "
            "Rate urgency 1-5 based on business impact and customer sentiment."
        ),
        user_input=ticket_text,
        output_format='{"category": "...", "urgency": N}',
    )
    result = await llm.invoke(prompt.render())
    return json.loads(result["text"])


class PromptStrategySelector:
    """A small router that starts every new task zero-shot, and only
    escalates to few-shot once evaluation data shows it's needed.
    This is the practical decision process, encoded — not just a rule
    of thumb kept in someone's head."""

    def __init__(self, min_samples: int = 50, accuracy_threshold: float = 0.9):
        self.min_samples = min_samples
        self.accuracy_threshold = accuracy_threshold

    def should_use_few_shot(self, eval_accuracy: float, eval_sample_count: int) -> bool:
        if eval_sample_count < self.min_samples:
            logger.info("not enough eval data yet (%d/%d) — keep zero-shot",
                        eval_sample_count, self.min_samples)
            return False
        needs_upgrade = eval_accuracy < self.accuracy_threshold
        logger.info("zero_shot_accuracy=%.2f threshold=%.2f upgrade_to_few_shot=%s",
                    eval_accuracy, self.accuracy_threshold, needs_upgrade)
        return needs_upgrade
```

### Real-World Use Case
A general-purpose "summarize this email" feature in an email client. The task is simple, universally understood, and doesn't need a specific house style — zero-shot with a clear instruction ("Summarize in 2 sentences, preserve action items") performs just as well as a few-shot version, without paying the extra token cost across millions of emails per day.

### Advantages
- Lowest possible token cost and latency — no example overhead.
- Zero maintenance burden — no example set to curate, version, or go stale.
- Scales cleanly to novel inputs the examples might not have anticipated.

### Disadvantages / Failure Modes
- More sensitive to ambiguous task descriptions — if the instruction is vague, output variance is higher with nothing to anchor it.
- Format/style consistency is weaker than few-shot for subjective or brand-specific tasks.
- Harder to debug *why* an output is wrong, since there's no example to compare it against — you're purely relying on the model's interpretation of prose instructions.

### When NOT to Use It
- Task has a specific house style, brand voice, or narrow output convention the model hasn't reliably demonstrated zero-shot → use Few-Shot (Pattern 4).
- Task requires multi-step reasoning where showing worked examples measurably improves correctness → combine with Chain-of-Thought (next pattern) or few-shot reasoning traces.
- You've already measured zero-shot underperforming in evaluation — don't keep guessing, escalate to few-shot with targeted examples.

### Comparison: Zero-Shot vs Few-Shot

| Pattern | Problem Solved | Complexity | Cost | Latency | Consistency | Best Use Case |
|---|---|---|---|---|---|---|
| Zero-Shot | Simple, well-understood tasks | Low | Lowest | Lowest | Good, if instructions are precise | High-volume, generic tasks (summarization, simple classification) |
| Few-Shot | Style/format-sensitive or ambiguous tasks | Medium | Higher (examples add tokens) | Higher | Higher, anchored to examples | Brand voice, strict output formats, tasks with subtle edge cases |

**Practical rule of thumb:** start every new prompt zero-shot. Only add examples once evaluation data shows a specific, recurring failure mode — and target the examples at that failure mode specifically, rather than adding generic examples "just in case."

### Exercise
Take the `PromptStrategySelector` above and extend it into a real A/B harness: run the same 20 test tickets through both `classify_ticket_zero_shot` (this pattern) and `classify_ticket` (Pattern 4, few-shot), compare accuracy against a hand-labeled answer key, and print which strategy wins. This is a miniature version of the full **Prompt Evaluation** pattern we'll build properly later in the course.

Next up: **Pattern 6 — Chain-of-Thought Considerations**.

---
