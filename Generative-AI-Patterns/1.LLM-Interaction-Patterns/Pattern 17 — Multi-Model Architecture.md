## Pattern 17 — Multi-Model Architecture

**Depends on:** Pattern 14 (Model Selection), Pattern 15 (Model Routing), Pattern 16 (Fallback Models)

### Problem
Patterns 14–16 each address a single dimension in isolation: which tier to use, how to route dynamically, and what to do on failure. A real production system needs all three working together, plus the ability to use genuinely *different models for different roles within the same request* — not just different tiers of the same family. A single "which model do I call" decision point isn't enough once your system has multiple distinct jobs (drafting, critiquing, extracting, embedding) that may each be best served by a different model entirely.

### Motivation
"Multi-Model Architecture" is the pattern of deliberately using more than one model as *specialized components* in a single pipeline, rather than one model doing everything. This shows up constantly in production: a cheap model does structured extraction, a strong model does the actual reasoning/drafting, an embedding model handles retrieval, and a different provider's model might even serve as an independent "second opinion" for high-stakes validation. Treating "which model" as a single global decision misses this — the right question per component is "what's this specific job, and which model is actually best suited to it."

### Core Idea
Decompose a pipeline into components, each with an explicit model assignment based on that component's actual requirements (cost sensitivity, latency sensitivity, reasoning depth, or even needing model *diversity* — e.g., using a different provider's model to catch blind spots a single model/provider might share).

```text
Extraction (cheap, high-volume)     → Haiku 4.5
Core reasoning/drafting              → Sonnet 5
Complex judgment / high-stakes review → Opus 4.8
Embeddings for retrieval              → dedicated embedding model
Independent validation (optional)      → a different provider entirely
```

### Architecture

```text
┌────────────┐
│    Input      │
└──────┬─────┘
       ▼
┌────────────────┐
│  Extraction stage  │  ← Haiku 4.5 (cheap, fast, structured output)
└──────┬─────────┘
       ▼
┌────────────────┐
│  Reasoning/draft   │  ← Sonnet 5 (balanced default)
│  stage              │
└──────┬─────────┘
       ▼
┌────────────────┐
│  High-stakes review │  ← Opus 4.8, only if flagged as high-risk
│  (conditional)       │
└──────┬─────────┘
       ▼
┌────────────────┐
│  Final response     │
└────────────────┘
```

### Internal Flow
1. Decompose the overall task into discrete stages/components (this itself echoes Prompt Decomposition and Sequential Workflow, both covered later — Multi-Model Architecture is what happens when you additionally vary the *model* per decomposed stage, not just the prompt).
2. For each stage, evaluate its actual requirements: does it need deep reasoning, or is it mechanical? Is it high-volume (cost-sensitive) or rare (quality-sensitive)? Does correctness benefit from model diversity?
3. Assign the most appropriate model to each stage — reusing the Model Selection policy (Pattern 14) per stage rather than one policy for the whole request.
4. Wire stages together, passing outputs from one as (structured, per Pattern 11) inputs to the next.
5. Apply fallback chains (Pattern 16) per stage independently — a failure in the extraction stage shouldn't necessarily use the same fallback chain as a failure in the drafting stage.
6. Track cost, latency, and quality per *stage*, not just per request — this granularity is what makes multi-model architectures actually optimizable over time.

### Simple Implementation

```python
import anthropic

client = anthropic.Anthropic()

def extract_key_facts(document: str) -> str:
    # Cheap, mechanical extraction — Haiku is plenty capable here
    r = client.messages.create(
        model="claude-haiku-4-5-20251001", max_tokens=500,
        messages=[{"role": "user", "content": f"Extract key facts as a bullet list:\n\n{document}"}],
    )
    return r.content[0].text

def draft_summary(facts: str) -> str:
    # Actual synthesis/writing — worth the stronger, balanced default model
    r = client.messages.create(
        model="claude-sonnet-5", max_tokens=800,
        messages=[{"role": "user", "content": f"Write a coherent executive summary from these facts:\n\n{facts}"}],
    )
    return r.content[0].text

document = "..."  # some long report
facts = extract_key_facts(document)
summary = draft_summary(facts)
print(summary)
```

### Production Implementation

```python
import logging
from dataclasses import dataclass
from typing import Callable, Awaitable

import anthropic

logger = logging.getLogger("genai.multi_model")


@dataclass
class PipelineStage:
    name: str
    model: str
    max_tokens: int
    build_prompt: Callable[[str], str]  # takes the previous stage's output, returns a prompt


class MultiModelPipeline:
    """Chains multiple stages, each with its own model assignment, cost
    profile, and prompt. Every stage's usage is tracked independently —
    this per-stage granularity is what makes it possible to answer 'where
    is our spend actually going' precisely, rather than as one lump sum
    across the whole request (Cost Tracking, Observability sections)."""

    def __init__(self, stages: list[PipelineStage]):
        self.stages = stages
        self.client = anthropic.AsyncAnthropic()

    async def run(self, initial_input: str) -> dict:
        current = initial_input
        stage_results = []

        for stage in self.stages:
            prompt = stage.build_prompt(current)
            response = await self.client.messages.create(
                model=stage.model, max_tokens=stage.max_tokens,
                messages=[{"role": "user", "content": prompt}],
            )
            current = response.content[0].text
            stage_results.append({
                "stage": stage.name, "model": stage.model,
                "input_tokens": response.usage.input_tokens,
                "output_tokens": response.usage.output_tokens,
            })
            logger.info("pipeline_stage_complete stage=%s model=%s in_tok=%d out_tok=%d",
                        stage.name, stage.model, response.usage.input_tokens, response.usage.output_tokens)

        return {"final_output": current, "stage_trace": stage_results}


document_summary_pipeline = MultiModelPipeline(stages=[
    PipelineStage(
        name="extraction", model="claude-haiku-4-5-20251001", max_tokens=500,
        build_prompt=lambda doc: f"Extract key facts as a bullet list:\n\n{doc}",
    ),
    PipelineStage(
        name="drafting", model="claude-sonnet-5", max_tokens=800,
        build_prompt=lambda facts: f"Write a coherent executive summary from these facts:\n\n{facts}",
    ),
    PipelineStage(
        name="quality_review", model="claude-opus-4-8", max_tokens=800,
        build_prompt=lambda draft: (
            f"Review this executive summary for accuracy and clarity. "
            f"If it's good, return it unchanged. If not, return an improved version:\n\n{draft}"
        ),
    ),
])
```

### Real-World Use Case
A financial report generation system: a cheap model extracts raw numbers and facts from source documents (high volume, mechanical, no need for a strong reasoning model); a mid-tier model drafts the narrative summary (needs real synthesis ability); and — only for reports above a certain dollar-value threshold — a top-tier model does a final quality/accuracy review before the report reaches a human analyst. Most reports never touch the most expensive model at all, while the highest-stakes ones get real scrutiny — the architecture matches model cost to the actual value/risk of each specific document, not a flat policy applied uniformly.

### Advantages
- Matches spend precisely to where quality actually matters, rather than either over-spending everywhere or under-serving the parts that need more capability.
- Stage-level observability makes it possible to identify exactly which part of a pipeline is expensive, slow, or low-quality — much more actionable than a single end-to-end metric.
- Using genuinely different models (or providers) for independent validation can catch errors a single model would be blind to, since different models don't necessarily share the same failure modes.

### Disadvantages / Failure Modes
- More moving parts than a single-model pipeline — more places for something to fail, and more surface area for testing/monitoring.
- Errors compound across stages: a subtly wrong extraction in stage 1 propagates into a confidently wrong summary in stage 2 — validation between stages (Pattern 11's schema guarantees help here) matters more as the pipeline grows.
- Latency is additive across sequential stages — a 3-stage pipeline is at minimum 3x the round-trip latency of a single call, unless stages can be parallelized (Parallel Workflow, later, where applicable).
- Managing multiple model integrations (especially across providers) adds real engineering overhead — normalizing request/response formats, differing rate limits, differing safety behaviors.

### When NOT to Use It
- Simple, single-purpose tasks that don't naturally decompose into distinct stages — forcing artificial stage boundaries adds latency and cost for no benefit.
- Early-stage products — start with one model and a single call; introduce staged, multi-model architecture only once you have real usage data showing where a single model is either overkill or insufficient for part of the task.
- When the added latency of sequential stages is unacceptable for the use case (e.g., a real-time autocomplete feature).

### Comparison: Single Model vs Multi-Model Architecture
| Aspect | Single Model (one call, any tier) | Multi-Model Architecture (this pattern) |
|---|---|---|
| Cost efficiency | Coarse — one tier for the whole task | Fine-grained — cost matched per stage |
| Latency | Lowest (one round trip) | Higher (sequential stage round trips) |
| Failure surface | Single point of failure | Multiple stages, each independently monitorable/fallback-able |
| Engineering complexity | Low | Higher — pipeline orchestration, per-stage validation |
| Best for | Simple, undifferentiated tasks | Complex tasks with genuinely distinct sub-jobs |

### Exercise
Add a conditional branch to `MultiModelPipeline`: after the `drafting` stage, only run the `quality_review` stage if the draft exceeds some risk threshold (e.g., mentions specific dollar amounts over $1M, or the document type is flagged as "regulatory"). This turns a fixed linear pipeline into one with real conditional routing — a direct preview of **Conditional Workflow** and **Branching**, coming up in the Workflow Patterns section.

---
