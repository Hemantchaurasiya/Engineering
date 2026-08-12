## Pattern 14 — Model Selection

**Depends on:** Pattern 1 (Basic LLM Invocation)

### Problem
"Which model should I call?" is a decision most tutorials skip by just hardcoding one model everywhere. In production, this is a real, recurring engineering decision with direct cost and quality consequences — using a flagship reasoning model for a trivial classification task wastes money at scale, while using the cheapest model for a task that needs deep reasoning produces worse results than the business needs.

### Motivation
Model capability, cost, and latency vary enormously across a provider's lineup, and that lineup changes over time (deprecations, new releases). A deliberate model-selection strategy — decided per *task type*, not once for the whole application — is one of the highest-leverage cost/quality levers available, and it's cheap to implement once you have the tool-use and structured-output primitives from earlier patterns in place.

### Core Idea
Match model tier to task complexity, not to habit. As of the current Claude lineup: **Haiku 4.5** for fast, cheap, high-volume simple tasks; **Sonnet 5** as the default workhorse for most agentic and general-purpose work; **Opus 4.8** when quality is the dominant concern and cost is secondary; **Fable 5** for the most demanding reasoning and long-horizon agentic work, at the top of the lineup. This mapping shifts with every model generation — the *process* of deliberately choosing is what matters, not memorizing today's specific names.

### Architecture

```text
                     ┌─────────────────────┐
                     │   Incoming Task         │
                     └──────────┬──────────┘
                                ▼
                  ┌───────────────────────┐
                  │  Task Classifier          │  ← simple/complex? latency-
                  │  (rules or a cheap model)  │     sensitive? high-stakes?
                  └──────────┬───────────┘
             ┌───────────────┼───────────────┐
             ▼                ▼                ▼
      ┌───────────┐   ┌───────────┐   ┌────────────┐
      │  Haiku 4.5   │   │  Sonnet 5   │   │  Opus 4.8 /   │
      │  (fast,       │   │  (default,   │   │  Fable 5       │
      │  cheap,        │   │  balanced)    │   │  (max quality, │
      │  high-volume)   │   │               │   │  complex tasks) │
      └───────────┘   └───────────┘   └────────────┘
```

### Internal Flow
1. Categorize the task type ahead of time (classification, extraction, drafting, complex multi-step reasoning, high-stakes decision).
2. Assign a default model tier per category — this is a config decision, made once, not re-derived per request.
3. Route each request to its assigned model (the mechanics of *how* to route are Pattern 15, next — this pattern is about the *decision criteria*, that pattern is about the *routing mechanism*).
4. Periodically re-evaluate: as models are deprecated and new ones released, your tier assignments need updating — this is not a "set once, forget forever" decision (Model Deprecations is a real operational concern, not a hypothetical).
5. Track cost and quality metrics per model tier so the choice is backed by data, not intuition (ties into Cost Tracking and Evaluation, later sections).

### Simple Implementation

```python
from enum import Enum

class TaskComplexity(str, Enum):
    SIMPLE = "simple"           # classification, short extraction, formatting
    STANDARD = "standard"        # most agentic/coding/general tasks
    COMPLEX = "complex"          # deep multi-step reasoning, high-stakes judgment

MODEL_FOR_COMPLEXITY = {
    TaskComplexity.SIMPLE: "claude-haiku-4-5-20251001",
    TaskComplexity.STANDARD: "claude-sonnet-5",
    TaskComplexity.COMPLEX: "claude-opus-4-8",
}

def select_model(complexity: TaskComplexity) -> str:
    return MODEL_FOR_COMPLEXITY[complexity]

print(select_model(TaskComplexity.SIMPLE))    # claude-haiku-4-5-20251001
print(select_model(TaskComplexity.COMPLEX))   # claude-opus-4-8
```

### Production Implementation

```python
import logging
from dataclasses import dataclass
from enum import Enum

import anthropic

logger = logging.getLogger("genai.model_selection")


class TaskComplexity(str, Enum):
    SIMPLE = "simple"
    STANDARD = "standard"
    COMPLEX = "complex"
    FRONTIER = "frontier"  # rare: only for the hardest, highest-stakes tasks


@dataclass
class ModelTierConfig:
    model: str
    max_tokens: int
    approx_cost_note: str  # human-readable reminder, not a live price feed


class ModelSelectionPolicy:
    """Centralizes model tier decisions in one reviewable place, instead
    of scattering model strings through the codebase. Updating the
    lineup (a new release, a deprecation) means editing this one class,
    not grepping the whole repo for hardcoded model names."""

    _tiers: dict[TaskComplexity, ModelTierConfig] = {
        TaskComplexity.SIMPLE: ModelTierConfig(
            model="claude-haiku-4-5-20251001", max_tokens=500,
            approx_cost_note="cheapest/fastest tier — high-volume simple tasks",
        ),
        TaskComplexity.STANDARD: ModelTierConfig(
            model="claude-sonnet-5", max_tokens=2000,
            approx_cost_note="balanced default — most agentic/general work",
        ),
        TaskComplexity.COMPLEX: ModelTierConfig(
            model="claude-opus-4-8", max_tokens=4000,
            approx_cost_note="higher cost — use when quality dominates",
        ),
        TaskComplexity.FRONTIER: ModelTierConfig(
            model="claude-fable-5", max_tokens=4000,
            approx_cost_note="top tier — reserve for the hardest, highest-stakes tasks only",
        ),
    }

    @classmethod
    def resolve(cls, complexity: TaskComplexity) -> ModelTierConfig:
        config = cls._tiers[complexity]
        logger.info("model_selected complexity=%s model=%s", complexity.value, config.model)
        return config


class ModelAwareClient:
    def __init__(self):
        self.client = anthropic.AsyncAnthropic()

    async def invoke(self, prompt: str, complexity: TaskComplexity, system: str | None = None) -> dict:
        config = ModelSelectionPolicy.resolve(complexity)
        response = await self.client.messages.create(
            model=config.model,
            max_tokens=config.max_tokens,
            system=system or anthropic.NOT_GIVEN,
            messages=[{"role": "user", "content": prompt}],
        )
        return {
            "text": response.content[0].text,
            "model_used": config.model,
            "usage": {"input": response.usage.input_tokens, "output": response.usage.output_tokens},
        }


async def classify_ticket(client: ModelAwareClient, ticket_text: str) -> dict:
    # Simple classification → cheapest capable tier
    return await client.invoke(f"Classify: {ticket_text}", TaskComplexity.SIMPLE)


async def draft_incident_postmortem(client: ModelAwareClient, incident_log: str) -> dict:
    # Multi-step reasoning over a complex log → higher tier
    return await client.invoke(f"Write a postmortem for:\n{incident_log}", TaskComplexity.COMPLEX)
```

### Real-World Use Case
A support platform runs ticket triage (category + urgency) on Haiku 4.5 across hundreds of thousands of tickets a day — that task is well within a fast model's capability and the volume makes cost the dominant concern. The same platform escalates to Sonnet 5 for actually drafting the agent's reply, and reserves Opus 4.8 for a much smaller volume of complex escalations (multi-account billing disputes, legal-adjacent complaints) where getting the answer right matters more than shaving fractions of a cent per call.

### Advantages
- Directly controls cost at scale — routing 90% of high-volume, low-complexity traffic to a cheap tier can cut spend dramatically without touching the 10% that genuinely needs a stronger model.
- Centralizing tier decisions in one policy class makes lineup changes (new releases, deprecations) a one-place edit instead of a codebase-wide hunt.
- Enables quality/cost experimentation: swap one tier's model and measure the effect via evaluation (later section) without touching call sites.

### Disadvantages / Failure Modes
- Misjudging a task's true complexity (assuming it's "simple" when it actually needs deeper reasoning) silently degrades quality — this needs to be validated with real evaluation data, not assumed once and forgotten.
- Tier assignments go stale as new models ship and old ones are deprecated — an unmaintained `ModelSelectionPolicy` is a real, easy-to-overlook source of technical debt.
- Over-engineering this for a small application with low volume and uniform task types adds complexity with no real payoff — the cost savings need to justify the added indirection.

### When NOT to Use It
- Low-volume applications where the cost difference between tiers is negligible in absolute terms — added complexity isn't justified.
- Prototypes and early-stage products — pick one solid default model (Sonnet 5) and defer tiering until you have real usage data showing where it would help.
- Tasks with genuinely uniform complexity across all requests — if every request needs the same capability level, there's no selection decision to make.

### Trade-off: Static Tiering (this pattern) vs Dynamic Routing (next pattern)
| Aspect | Static Model Selection (this pattern) | Dynamic Model Routing (Pattern 15) |
|---|---|---|
| Decision basis | Task *type*, known ahead of time | Per-request signals, evaluated at runtime |
| Complexity | Low — a config lookup | Higher — needs a classifier or heuristic per request |
| Best for | Task categories with stable, predictable complexity | Traffic where complexity varies unpredictably per request |

### Exercise
Extend `ModelSelectionPolicy` with a `resolve_with_override(complexity, force_model: str | None)` method that lets a caller explicitly override the tier's default model for a single call (useful for A/B testing a new model release against the current default before fully committing to it in the policy). Log both the assigned tier and whether an override was used, so you can measure the override's real-world performance before making it the new default.

---
