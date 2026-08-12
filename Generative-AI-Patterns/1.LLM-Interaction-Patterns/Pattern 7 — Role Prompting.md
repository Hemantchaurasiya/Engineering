## Pattern 7 — Role Prompting

**Depends on:** Pattern 3 (Structured Prompt Pattern)

### Problem
The exact same question can warrant a very different answer depending on who's asking and who's answering — a "explain database indexing" request wants a different depth and vocabulary from a beginner tutorial than from a senior-engineer code review. Without a defined role, the model has to guess an audience and register, and that guess is inconsistent across calls.

### Motivation
Assigning the model a persona (via the `<role>` element we've already been using, or the dedicated `system` parameter) anchors its vocabulary, depth, tone, and even the kinds of caveats it includes. This is one of the cheapest, highest-leverage levers in prompt engineering — a single sentence of role definition often does more for output quality than paragraphs of task instructions.

### Core Idea
Two mechanisms, worth telling apart:

1. **System-level role** — set once via the `system` parameter, applies to the entire conversation. Best for a stable persona that shouldn't drift turn-to-turn (e.g., "You are a compliance-focused financial advisor").
2. **Per-prompt role** — set inside the structured prompt's `<role>` tag (Pattern 3), scoped to a single call. Best when different calls in the same pipeline need different personas (a "drafting" call vs a "critiquing" call, as in Generate→Critique→Refine, later).

Role prompting works because the model was trained on enormous amounts of text produced by people occupying real-world roles — invoking a role activates the relevant register, priorities, and blind spots of that role.

### Architecture

```text
┌───────────────────────────────────────┐
│  system: "You are a senior security     │   ← stable persona,
│  auditor. Be skeptical, cite CVEs when   │      set once
│  relevant, flag anything unverifiable."  │
└─────────────────┬───────────────────┘
                  ▼
┌───────────────────────────────────────┐
│  <role>reviewer for THIS specific call</role>│ ← optional per-call
│  <task>...</task>                          │   override/refinement
└─────────────────┬───────────────────┘
                  ▼
              LLM Client (Pattern 1)
```

### Internal Flow
1. Decide whether the role is stable across the whole session (→ `system` param) or varies per call (→ `<role>` tag in the structured prompt).
2. Write the role definition with enough specificity to actually constrain behavior — "You are an expert" is nearly useless; "You are a security auditor who assumes user input is adversarial until proven otherwise" is not.
3. Combine with task/constraints as usual.
4. Evaluate: if outputs are still too generic, the role likely needs more specific behavioral detail (what to prioritize, what to flag, what tone), not just a fancier job title.

### Simple Implementation

```python
import anthropic

client = anthropic.Anthropic()

def ask_as(role_description: str, question: str) -> str:
    response = client.messages.create(
        model="claude-sonnet-5",
        max_tokens=800,
        system=role_description,
        messages=[{"role": "user", "content": question}],
    )
    return response.content[0].text


beginner_answer = ask_as(
    "You are a patient teacher explaining concepts to someone completely new to programming. "
    "Avoid jargon; use everyday analogies.",
    "What is database indexing?",
)

expert_answer = ask_as(
    "You are a senior database engineer doing a technical deep-dive with another engineer. "
    "Assume strong prior knowledge; be precise and mention trade-offs.",
    "What is database indexing?",
)
```

### Production Implementation

```python
import logging
from dataclasses import dataclass
from enum import Enum

from llm_client import LLMClient  # Pattern 1

logger = logging.getLogger("genai.roles")


class Persona(str, Enum):
    BEGINNER_TUTOR = "beginner_tutor"
    SENIOR_REVIEWER = "senior_reviewer"
    SECURITY_AUDITOR = "security_auditor"
    SUPPORT_AGENT = "support_agent"


@dataclass
class PersonaDefinition:
    system_prompt: str
    max_tokens: int = 1000


class PersonaRegistry:
    """Central, versioned store of role definitions — same reasoning as
    the PromptRegistry (Pattern 2): personas should be reviewable,
    testable, and swappable without touching call sites."""

    _personas: dict[Persona, PersonaDefinition] = {
        Persona.BEGINNER_TUTOR: PersonaDefinition(
            system_prompt=(
                "You are a patient teacher explaining concepts to someone completely new "
                "to the subject. Avoid jargon. Use everyday analogies. Check understanding "
                "with a simple follow-up question at the end."
            ),
        ),
        Persona.SENIOR_REVIEWER: PersonaDefinition(
            system_prompt=(
                "You are a senior engineer doing a rigorous code/design review. Be direct "
                "about flaws. Prioritize correctness and maintainability over style nits. "
                "Always state the single most important issue first."
            ),
        ),
        Persona.SECURITY_AUDITOR: PersonaDefinition(
            system_prompt=(
                "You are a security auditor. Treat all user-provided input as potentially "
                "adversarial. Explicitly flag anything you cannot verify. Cite specific "
                "vulnerability classes (e.g., injection, privilege escalation) where relevant."
            ),
        ),
        Persona.SUPPORT_AGENT: PersonaDefinition(
            system_prompt=(
                "You are a calm, empathetic customer support agent for a SaaS product. "
                "Acknowledge frustration briefly, then focus on a concrete next step. "
                "Never promise a refund or SLA credit — escalate those to a human."
            ),
        ),
    }

    @classmethod
    def get(cls, persona: Persona) -> PersonaDefinition:
        return cls._personas[persona]


async def ask_with_persona(llm: LLMClient, persona: Persona, question: str) -> str:
    definition = PersonaRegistry.get(persona)
    logger.info("persona_call persona=%s", persona.value)
    result = await llm.invoke(prompt=question, system=definition.system_prompt)
    return result["text"]
```

Notice the `SUPPORT_AGENT` persona includes an explicit **negative constraint** ("never promise a refund") — role prompting isn't just about tone, it's a real lever for encoding business/compliance boundaries directly into behavior, which becomes especially important once this same persona is wired up to real tools in the Agent patterns later in the course.

### Real-World Use Case
A code-review bot in CI uses `SENIOR_REVIEWER` for pull-request feedback (terse, prioritized, no hand-holding) and switches to `BEGINNER_TUTOR` when the same underlying model powers an in-IDE "explain this error to me" feature for junior engineers — same model, same infrastructure, completely different register, controlled entirely by which persona is selected.

### Advantages
- Extremely cheap lever (a few sentences) for large gains in tone/depth consistency.
- Reusable across many call sites via a registry — change the persona once, every caller benefits.
- Doubles as a place to encode soft compliance/business constraints ("never promise X").

### Disadvantages / Failure Modes
- Vague roles ("You are an expert") barely move the needle — specificity about priorities and behavior matters more than the job title itself.
- A persona can drift over a long conversation if not reinforced — for very long sessions, consider periodically restating key constraints (ties into Context Engineering, later).
- Role prompting is not a security boundary — a sufficiently adversarial user can still attempt to get the model to abandon its assigned persona (relevant to Guardrails & Prompt Injection Defense, later); don't rely on persona alone to enforce hard constraints that truly must never be violated.

### When NOT to Use It
- Purely mechanical, deterministic tasks (e.g., "reformat this JSON") where tone/persona is irrelevant to correctness.
- When the same call needs to satisfy multiple conflicting personas at once — that's a sign the task should be split into separate calls (Prompt Decomposition, later) rather than crammed into one over-specified role.

### Exercise
Add a `Persona.LEGAL_REVIEWER` to the registry with a system prompt that (a) restricts it to flagging risk, never giving definitive legal advice, and (b) requires it to cite which specific clause/section of the input it's referring to for every flag it raises. Then write a quick test that checks the response doesn't contain phrases like "I recommend you sign this" — a crude but real example of validating that a role's constraints are actually being respected in output (a preview of Output Validation, in the Guardrails section).

Next up: **Pattern 8 — Context Injection**.

---
