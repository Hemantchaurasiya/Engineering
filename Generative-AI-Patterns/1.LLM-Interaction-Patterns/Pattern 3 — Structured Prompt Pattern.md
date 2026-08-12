## Pattern 3 — Structured Prompt Pattern

**Depends on:** Pattern 2 (Prompt Template Pattern)

### Problem
A wall of unstructured prose in a prompt makes it hard for the model to tell *instructions* apart from *data* apart from *examples* apart from *output rules*. This ambiguity is one of the biggest sources of inconsistent output and injection vulnerability. The Structured Prompt Pattern is about **organizing a single prompt into clearly labeled sections**, so both the model and future maintainers can parse intent at a glance.

### Motivation
LLMs are trained heavily on structured formats (XML-like tags, Markdown headers, JSON). Giving the model explicit section boundaries measurably improves instruction-following compared to an equivalent prompt written as one paragraph — and it gives you, the engineer, a stable "schema" for your prompts that's easy to diff and review.

### Core Idea
Break a prompt into named blocks, each with one job:

```text
<role>            — who the model should act as
<task>            — what to do
<context>         — background/reference data (often RAG output)
<input>           — the specific untrusted user data
<constraints>      — rules, format requirements, things to avoid
<output_format>    — exact shape of the expected response
```

XML-style tags work especially well with Claude models specifically, since Claude was trained to pay close attention to XML structure — but the *concept* (label your sections) applies to any LLM.

### Architecture

```text
┌───────────────────────────────────────────┐
│               Structured Prompt              │
│  <role>...</role>                            │
│  <task>...</task>                            │
│  <context>...</context>   ← from RAG/tools    │
│  <input>...</input>       ← untrusted data     │
│  <constraints>...</constraints>               │
│  <output_format>...</output_format>           │
└───────────────────┬───────────────────────┘
                    ▼
              LLM Client (Pattern 1)
                    ▼
         Parser validates response
         matches <output_format>
```

### Internal Flow
1. Compose the prompt as discrete typed sections (in code, as a dataclass — not a free-form string).
2. Render sections into tags via the template engine from Pattern 2.
3. Send to the LLM.
4. Parse the response against the format you specified in `<output_format>` — if it doesn't match, that's a signal to retry or fall back (ties into **Validation + Retry**, a later pattern).

### Simple Implementation

```python
from dataclasses import dataclass


@dataclass
class StructuredPrompt:
    role: str
    task: str
    context: str
    user_input: str
    constraints: list[str]
    output_format: str

    def render(self) -> str:
        constraints_block = "\n".join(f"- {c}" for c in self.constraints)
        return f"""<role>{self.role}</role>

<task>{self.task}</task>

<context>
{self.context}
</context>

<input>
{self.user_input}
</input>

<constraints>
{constraints_block}
</constraints>

<output_format>
{self.output_format}
</output_format>"""


prompt = StructuredPrompt(
    role="a senior support engineer",
    task="Diagnose the likely cause of the customer's issue using the context provided.",
    context="Known issue DB-2291: connection pool exhaustion causes timeout errors under load.",
    user_input="My API calls keep timing out after 30 seconds during peak hours.",
    constraints=["Be specific, don't speculate beyond the given context", "Max 3 sentences"],
    output_format='JSON: {"diagnosis": "...", "confidence": "high|medium|low"}',
)
print(prompt.render())
```

### Production Implementation

```python
import json
import logging
from dataclasses import dataclass, field

from llm_client import LLMClient  # Pattern 1

logger = logging.getLogger("genai.structured_prompt")


class OutputParseError(Exception):
    pass


@dataclass
class StructuredPrompt:
    role: str
    task: str
    output_format: str
    context: str = ""
    user_input: str = ""
    constraints: list[str] = field(default_factory=list)

    def render(self) -> str:
        parts = [f"<role>{self.role}</role>", f"<task>{self.task}</task>"]
        if self.context:
            parts.append(f"<context>\n{self.context}\n</context>")
        if self.user_input:
            # Untrusted data gets its own clearly delimited tag — never
            # merged into <task> or <constraints>. This is a light-weight
            # prompt-injection mitigation (full defenses in Guardrails).
            parts.append(f"<input>\n{self.user_input}\n</input>")
        if self.constraints:
            constraints_block = "\n".join(f"- {c}" for c in self.constraints)
            parts.append(f"<constraints>\n{constraints_block}\n</constraints>")
        parts.append(f"<output_format>\n{self.output_format}\n</output_format>")
        return "\n\n".join(parts)


async def run_structured(llm: LLMClient, prompt: StructuredPrompt, max_attempts: int = 2) -> dict:
    """Render, call the LLM, and validate the response actually matches
    the requested JSON shape — retrying once on a parse failure."""
    rendered = prompt.render()

    for attempt in range(1, max_attempts + 1):
        result = await llm.invoke(rendered)
        raw_text = result["text"].strip()
        try:
            # Strip accidental markdown code fences before parsing
            if raw_text.startswith("```"):
                raw_text = raw_text.strip("`").removeprefix("json").strip()
            return json.loads(raw_text)
        except json.JSONDecodeError:
            logger.warning("structured_output_parse_failed attempt=%d raw=%s", attempt, raw_text[:200])
            if attempt == max_attempts:
                raise OutputParseError(f"Model did not return valid JSON after {max_attempts} attempts")

    raise OutputParseError("unreachable")


async def diagnose_ticket(llm: LLMClient, issue_context: str, customer_message: str) -> dict:
    prompt = StructuredPrompt(
        role="a senior support engineer",
        task="Diagnose the likely cause of the customer's issue using only the given context.",
        context=issue_context,
        user_input=customer_message,
        constraints=["Do not speculate beyond the given context", "Max 3 sentences in the diagnosis field"],
        output_format='{"diagnosis": "string", "confidence": "high|medium|low"}',
    )
    return await run_structured(llm, prompt)
```

Note this is still *text-based* JSON parsing with a retry loop — a real safety net, but a blunt one. Pattern 11 (Structured Output / JSON Output) and Pattern 12 (Function Calling) replace this with schema-constrained generation so malformed output becomes far rarer by construction, not just caught after the fact.

### Real-World Use Case
A legal-document review tool sends each clause through a structured prompt: `<role>` = contract analyst, `<context>` = the firm's risk playbook, `<input>` = the clause text, `<output_format>` = a fixed JSON schema with `risk_level`, `explanation`, `suggested_redline`. Because every section is labeled, the same prompt skeleton is reused across dozens of clause types just by swapping `<context>` and a couple of constraints — and downstream code can reliably parse `risk_level` to route high-risk clauses to a human reviewer.

### Advantages
- Reduces ambiguity between instructions, data, and examples → more consistent output.
- Clear separation of the untrusted `<input>` block is a real (if partial) injection mitigation.
- Sections map cleanly to code (dataclass fields), making prompts reviewable in PRs like any other code.

### Disadvantages / Failure Modes
- More verbose than a plain prompt — costs a few extra tokens per call.
- Structure alone doesn't guarantee compliance — models can still ignore `<constraints>` or `<output_format>`, especially smaller/cheaper models. Always validate, never just trust the tag.
- Over-tagging (20+ nested sections) adds noise without benefit — use as many sections as the task genuinely has distinct roles, no more.

### When NOT to Use It
- Very short, simple prompts (a one-line classification task) — the overhead isn't worth it.
- When you're about to use native function calling / schema-constrained generation anyway — that provides stronger format guarantees than tag-based structuring, making manual `<output_format>` less critical (though `<role>`/`<context>`/`<input>` sectioning still helps).

### Trade-off vs Pattern 2
| Aspect | Prompt Template (Pattern 2) | Structured Prompt (Pattern 3) |
|---|---|---|
| Focus | Variable injection into a fixed skeleton | Organizing the skeleton itself into labeled roles |
| Combines with | Can render structured prompts too | Usually built via a template underneath |
| Injection defense | Delimiters, optional | Explicit `<input>` isolation, stronger by default |
| Output consistency | No inherent effect | Improves it, but doesn't guarantee it |

### Exercise
Take the `diagnose_ticket` function above and add a `<few_shot_examples>` section containing two example diagnoses (input → correct JSON output). Re-run it mentally (or for real, if you have an API key) and think about whether the extra tokens are worth the consistency gain — we'll formalize this exact question in **Pattern 4 — Few-Shot Prompting**, next.

---
