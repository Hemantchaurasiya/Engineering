## Pattern 10 — Output Formatting

**Depends on:** Pattern 3 (Structured Prompt Pattern)

### Problem
Downstream code, UIs, and other systems need output in a *specific, predictable shape* — a bulleted list, a markdown table, a fixed-length summary, plain text with no preamble. Left unconstrained, the model will happily add a friendly intro ("Sure, here's a summary:"), inconsistent formatting, or wrap answers in extra commentary — all of which breaks anything downstream that expects a clean, parseable shape.

### Motivation
Output Formatting is the lightweight sibling of Structured Output/JSON (Pattern 11, next) — it's for cases where you want a *specific human-readable shape*, not necessarily machine-parseable JSON. Getting this right up front avoids a surprisingly common category of bugs: brittle downstream regex/string-parsing code written to strip out a preamble the model wasn't supposed to add in the first place.

### Core Idea
Be explicit and exhaustive about the exact shape you want: format (markdown/plain/table), length constraints, what to exclude (no preamble, no "Here's..."), and provide a skeleton or example of the target shape when precision matters.

```text
"Respond with ONLY a markdown table, no other text.
Columns: Name | Category | Urgency
Do not include a header sentence or summary."
```

### Architecture

```text
┌───────────────────────────────────┐
│  <task>...</task>                     │
│  <output_format>                       │
│    - format: markdown table              │
│    - columns: [...]                     │
│    - exclusions: no preamble, no notes    │
│    - example skeleton (optional)          │
│  </output_format>                      │
└─────────────┬─────────────────┘
              ▼
        LLM Client (Pattern 1)
              ▼
   ┌─────────────────────┐
   │ Format Validator      │  ← checks shape before use
   └─────────┬───────────┘
             ▼
    Pass to downstream system
    (UI render, file write, API response)
```

### Internal Flow
1. Define the target format precisely — not just "a list" but "a markdown bulleted list, max 5 items, each under 15 words."
2. State exclusions explicitly — models default to being conversational/helpful, which often means adding intros/outros you don't want; you have to actively suppress that.
3. For tricky formats, provide a literal skeleton/example (a mini few-shot, Pattern 4) rather than describing the format only in prose.
4. Validate the actual output against the expected shape before passing it downstream — never trust format instructions blindly (same principle as Pattern 3's parse-and-retry).

### Simple Implementation

```python
def build_formatted_prompt(items: list[str]) -> str:
    items_block = "\n".join(f"- {i}" for i in items)
    return f"""<task>
Summarize the key risks below into a markdown table.
</task>

<input>
{items_block}
</input>

<output_format>
- Output ONLY a markdown table, nothing else — no intro sentence, no summary after.
- Columns exactly: | Risk | Severity (Low/Medium/High) |
- One row per risk, max 8 words per cell.
</output_format>"""

print(build_formatted_prompt([
    "Database has no automated backups",
    "Admin panel has no rate limiting",
    "Logs contain unmasked customer emails",
]))
```

### Production Implementation

```python
import logging
import re
from dataclasses import dataclass

from llm_client import LLMClient  # Pattern 1

logger = logging.getLogger("genai.output_format")


class FormatValidationError(Exception):
    pass


@dataclass
class MarkdownTableSpec:
    columns: list[str]
    max_rows: int | None = None


def validate_markdown_table(text: str, spec: MarkdownTableSpec) -> list[dict]:
    """Parses and validates a markdown table against the expected column
    spec, raising rather than silently accepting a malformed response.
    This is the same 'never trust format instructions blindly' principle
    as Pattern 3's JSON parse-and-retry, applied to a different shape."""
    lines = [l.strip() for l in text.strip().splitlines() if l.strip().startswith("|")]
    if len(lines) < 2:
        raise FormatValidationError("Expected a markdown table, got none")

    header_cells = [c.strip() for c in lines[0].strip("|").split("|")]
    if header_cells != spec.columns:
        raise FormatValidationError(f"Column mismatch: expected {spec.columns}, got {header_cells}")

    rows = []
    for line in lines[2:]:  # skip header + separator row
        cells = [c.strip() for c in line.strip("|").split("|")]
        if len(cells) != len(spec.columns):
            continue
        rows.append(dict(zip(spec.columns, cells)))

    if spec.max_rows and len(rows) > spec.max_rows:
        raise FormatValidationError(f"Too many rows: {len(rows)} > {spec.max_rows}")

    return rows


async def summarize_risks_as_table(llm: LLMClient, risks: list[str]) -> list[dict]:
    spec = MarkdownTableSpec(columns=["Risk", "Severity"], max_rows=10)
    items_block = "\n".join(f"- {r}" for r in risks)

    prompt = f"""<task>Summarize the risks below into a markdown table.</task>

<input>
{items_block}
</input>

<output_format>
Output ONLY a markdown table, nothing else. No intro, no closing remarks.
Columns exactly: | Risk | Severity |
Severity must be one of: Low, Medium, High.
</output_format>"""

    result = await llm.invoke(prompt)
    try:
        return validate_markdown_table(result["text"], spec)
    except FormatValidationError as e:
        logger.warning("output_format_invalid error=%s raw=%s", e, result["text"][:200])
        raise
```

### Real-World Use Case
A CI/CD bot posts an automated pull-request comment summarizing test failures as a compact markdown table (`| Test | File | Reason |`) directly into the PR. GitHub renders markdown tables natively, so the exact format matters — an extra sentence before or after the table, or an inconsistent column count, breaks the visual layout reviewers rely on. Explicit output-format instructions plus a validator that re-prompts on a malformed table keeps this reliable across thousands of automated runs.

### Advantages
- Removes an entire class of brittle "strip the preamble" downstream parsing code.
- Makes LLM output a reliable component in larger pipelines (UI rendering, file generation, API responses).
- Validation-before-use catches formatting drift immediately instead of surfacing as a confusing downstream bug.

### Disadvantages / Failure Modes
- Models can still occasionally add unwanted commentary despite explicit instructions — especially smaller/cheaper models; validation (not blind trust) is what actually guarantees correctness.
- Over-constraining format for content that's inherently variable-length (e.g., forcing a fixed-length summary of highly variable inputs) can produce awkward, padded, or truncated output.
- Complex nested formats (tables within tables, deeply structured markdown) are more reliably produced by real structured output/schema tools (Pattern 11) than by prose format instructions alone.

### When NOT to Use It
- The output is genuinely conversational and free-form (a chatbot reply) — imposing a rigid format fights the task.
- The format needs strict machine-parseable guarantees (types, nested structures) — use Structured Output / JSON mode or Function Calling (Pattern 11/12) instead of prose-described formatting, which is inherently softer.

### Trade-off vs Structured Output (next pattern)
| Aspect | Output Formatting (this pattern) | Structured Output / JSON (Pattern 11) |
|---|---|---|
| Target | Human-readable shapes (tables, lists, fixed prose) | Machine-parseable data (JSON, typed schemas) |
| Guarantee strength | Soft — instructions + validation | Stronger — schema-constrained generation |
| Typical consumer | UI, docs, human reviewers | Downstream code, APIs, databases |
| Failure handling | Validate + retry | Validate + retry, often with schema-level errors |

### Exercise
Extend `validate_markdown_table` to also validate that every `Severity` cell is one of `{"Low", "Medium", "High"}`, raising `FormatValidationError` with the specific bad row if not. Then wire up a retry loop (like Pattern 3's `run_structured`) that re-prompts once with an added instruction pointing out exactly what was wrong, if validation fails on the first attempt.

---
