# Pattern 10 — Context Summarization

[← Back to index](./README.md) | Previous: [Pattern 09 — Context Routing](./09-context-routing.md) | Next: Pattern 11 — Context Deduplication (queued)

---

## 1. Introduce the pattern

**Context Summarization** condenses extended conversation history into a durable, faithful summary using an LLM — going further than Pattern 06's lightweight fact-pinning into genuine, periodic re-authoring of "everything that's happened so far" into a compact, information-preserving form.

Pattern 06 (Context Window Management) solved the *bounded hot window* problem with a deliberately cheap mechanism: a deterministic `FINDING:` tag convention that pins isolated facts without ever calling an LLM to do it. That's the right tool when what you need is "don't forget a handful of specific conclusions." It is **not** enough when a session has genuinely grown long and complex enough that the *shape of the narrative* — not just a list of isolated facts — needs to be preserved: the sequence of what was investigated, what was ruled out, what's still open.

```
Long session history → [ SUMMARIZATION ] → durable, faithful running summary
   (many turns, growing)      │                  (bounded size, updated
                               │                   incrementally each round)
                               ├── incremental: summarize(prior_summary + new_turns),
                               │   never re-summarize from scratch
                               └── faithfulness-checked: verify critical facts
                                   (amounts, case/policy IDs, dates) survived
                                   the summarization step before trusting it
```

**Mental model:** Pattern 06's fact-pinning is like sticky notes on the edge of a desk — quick, cheap, good for a handful of specific reminders. Summarization is what a case worker does at the end of each week: sit down and rewrite the case file's running narrative so it's still coherent and complete, even though the day-to-day notes that generated it have been archived.

---

## 2. The problem it solves

Fact-pinning alone breaks down as sessions grow:

1. **Narrative gets lost, even when individual facts survive.** A list of pinned facts ("beneficiary linked to CASE-71190," "customer confirmed business justification") tells you *what* was found but not the reasoning connective tissue — what was checked and ruled out, what order things happened in, what's still an open question. For a long investigation, an analyst picking the case back up needs the narrative, not just a fact list.
2. **Naively re-summarizing from scratch doesn't scale.** The obvious approach — every time you need a summary, feed the entire history to the LLM and ask for one — gets more expensive every single round, since the input keeps growing even though most of it was already captured in the previous summary.
3. **Summarization is a lossy, LLM-authored transformation, and LLMs can drop or subtly distort facts under compression, especially over many rounds.** Unlike Pattern 03's compression (a single-shot, per-item transform, one pass, corrected against the concrete unmodified original), summarization here is **recursive** — this round's summary becomes next round's input — so a single dropped fact can silently vanish for good, and a subtly distorted number can compound across multiple rounds if nothing checks for it.

Context Summarization solves this by making the update incremental (only new material and the existing summary are ever fed to the LLM, keeping cost roughly constant per round instead of growing) and by pairing every summarization pass with a **faithfulness check** — extracting the critical hard facts (dollar amounts, case/policy IDs, dates) from the input and verifying they're still present in the output before the new summary is trusted and the source material it replaces is discarded.

---

## 3. A realistic enterprise problem (Helios)

An investigation into `CASE-88421` runs long — the analyst works it over many sessions across several days, checking in periodically, asking follow-up questions, reviewing new transactions as they post. By the twentieth exchange, Pattern 06's hot window (even with fact-pinning) has evicted most of the original conversation, and the pinned facts alone read like a disconnected list rather than a coherent case narrative:

> - beneficiary linked to CASE-71190
> - customer confirmed business justification for 18-day-ago transfer
> - Policy FR-14 applies

What's missing is the connective narrative: *in what order* were these established, what was checked and ruled out along the way (e.g., "sanctions screening returned clear"), and what does the analyst still need to resolve. Context Summarization is the pattern that periodically folds the accumulating history into a running narrative summary — bounded in size, but preserving the story, not just isolated facts — with hard verification that the numbers and IDs that actually matter (the $14,200 amount, `CASE-71190`, `Policy FR-14`) never silently drop out along the way.

---

## 4. Architecture / flow diagram

```mermaid
flowchart TD
    A[Turn N: analyst message + response] --> B{Unsummarized history\nover trigger threshold?}
    B -- No --> C[No-op: continue with\ncurrent running_summary]
    B -- Yes --> D[Incremental Summarizer\nChatOllama: summarize prior_summary\n+ NEW turns only]

    D --> E[Candidate new summary]
    E --> F[Faithfulness Checker]

    F --> G{All critical facts\n(amounts, case/policy IDs, dates)\nstill present?}
    G -- Yes --> H[Accept new summary;\ndiscard folded-in raw turns,\nkeep last few turns verbatim]
    G -- No --> I[Retry ONCE with explicit\n'you missed X, Y' reminder]
    I --> J{Retry passes\nfaithfulness check?}
    J -- Yes --> H
    J -- No --> K["⚠ Fall back: KEEP raw turns\n(do not discard on unverified summary)"]

    H --> L[Updated session state:\nrunning_summary + recent verbatim turns]
    K --> L
    C --> L
    L --> M[Next turn uses running_summary\n+ recent turns as context]

    style D fill:#4A90D9,color:#fff
    style F fill:#4A90D9,color:#fff
    style K fill:#C0392B,color:#fff
```

---

## 5. The complete request-to-response flow

1. **Turn completes as normal** (per Pattern 06's flow) — the analyst's message and the copilot's response are appended to the session's message history.
2. **Trigger check**: after the turn, the pipeline checks whether the amount of *unsummarized* history (messages accumulated since the last summarization pass) exceeds a threshold — by message count or token count.
3. **If not triggered**: nothing else happens this turn; the existing `running_summary` (if any) carries forward unchanged.
4. **If triggered — incremental summarization**: the LLM is given **only** the prior `running_summary` plus the new turns since the last summary (never the full original history) and asked to produce an updated summary — this is what keeps the cost of each summarization pass roughly constant, regardless of how many rounds the session has been through.
5. **Faithfulness check**: before the new summary is trusted, the pipeline extracts critical hard facts from the input (the prior summary plus the new turns) via regex — dollar amounts, case IDs (`CASE-\d+`), policy IDs (`Policy [A-Z]+-\d+`), and explicit dates/day-counts — and checks each one still appears somewhere in the candidate new summary.
6. **On success**: the new summary is accepted; the raw turns that were just folded into it are discarded from the hot window (a handful of the most recent turns are always kept verbatim regardless, matching Pattern 06's approach).
7. **On failure**: the pipeline retries once with an explicit prompt naming exactly which facts were missing. If the retry still fails, the system **fails safe**: it keeps the raw, unsummarized turns rather than discarding them on an unverified summary — a slightly larger context window is a much smaller problem than silently losing a fact from a fraud investigation.
8. **Next turn** proceeds using the (possibly just-updated) `running_summary` plus whatever recent turns remain verbatim, exactly as Pattern 06's window management already does.

---

## 6. Why this pattern is appropriate here

- **Incremental summarization is the only version of this pattern that actually scales.** Re-summarizing the full history from scratch every round means cost grows with total session length; summarizing only "prior summary + what's new" keeps each round's cost bounded by how much happened *since the last summary*, not by how long the investigation has been running overall.
- **The faithfulness check is not optional polish — it's what makes discarding the source material safe.** Compression (Pattern 03) could get away with a lighter safety net (a single-shot, size-only check) because a failed compression just means an item didn't shrink; failing a recursive summarization means data could be gone for the rest of the session, permanently, with no way to re-derive it. The extract-and-verify approach here is deliberately stricter than Pattern 03's.
- **Fail-safe-by-keeping-raw-data is the correct default in this domain.** A summarization system that discards source material on a failed verification is optimizing for the wrong thing — a bigger context window is a cost problem; a silently dropped fact in an active fraud investigation is a correctness and compliance problem. The retry-then-fall-back-to-raw design encodes that priority explicitly rather than leaving it to chance.
- **It composes directly with Pattern 06, not around it.** This pattern doesn't replace Window Management's hot-window/eviction mechanics — it adds a second, less frequent, LLM-authored layer on top of it. Cheap fact-pinning still runs every turn (Pattern 06); genuine narrative summarization runs only when the accumulated unsummarized history actually crosses a meaningful threshold.

---

## 7. Production-quality Python implementation

```python
"""
helios_context_summarization.py

Pattern 10 — Context Summarization
Helios Fraud Investigation Copilot

Extends Pattern 06's session management with genuine, incremental,
LLM-authored summarization: periodically folds accumulated turns into a
running narrative summary, verified against critical extracted facts
(amounts, case/policy IDs, dates) before the raw turns it replaces are
discarded. Falls back to keeping raw turns if faithfulness can't be verified.

Requirements:
    pip install "langchain-core>=0.3.60" "langchain-ollama>=0.3.0" "langgraph>=0.2.60"

Assumes a local Ollama server (`ollama serve`) with:
    ollama pull llama3.1
"""

from __future__ import annotations

import logging
import re
from typing import TypedDict

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage
from langchain_core.prompts import ChatPromptTemplate
from langchain_ollama import ChatOllama
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph

logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")
logger = logging.getLogger("helios.context_summarization")


# --------------------------------------------------------------------------- #
# Config
# --------------------------------------------------------------------------- #

KEEP_VERBATIM_LAST_N = 4       # always keep this many most-recent messages raw
SUMMARIZE_TRIGGER_EXCESS = 4   # summarize once unsummarized messages exceed this many, beyond the kept tail


def estimate_tokens(text: str) -> int:
    return max(1, len(text) // 4)


# --------------------------------------------------------------------------- #
# Critical-fact extraction for faithfulness verification
# --------------------------------------------------------------------------- #

AMOUNT_RE = re.compile(r"\$[\d,]+(?:\.\d{2})?")
CASE_ID_RE = re.compile(r"\bCASE-\d+\b")
POLICY_ID_RE = re.compile(r"\bPolicy [A-Z]+-\d+\b", re.IGNORECASE)
DAY_REFERENCE_RE = re.compile(r"\b\d+\s*days?\s*ago\b", re.IGNORECASE)


def extract_critical_facts(text: str) -> set[str]:
    """Extracts hard, checkable facts (amounts, case IDs, policy IDs, day
    references) that must survive summarization. Deliberately narrow --
    this is NOT trying to catch every nuance, only the facts where silently
    dropping them would be a real problem (numbers, identifiers)."""
    facts: set[str] = set()
    facts.update(AMOUNT_RE.findall(text))
    facts.update(m.upper() for m in CASE_ID_RE.findall(text))
    facts.update(m.upper() for m in POLICY_ID_RE.findall(text))
    facts.update(m.lower() for m in DAY_REFERENCE_RE.findall(text))
    return facts


def missing_facts(source_text: str, summary_text: str) -> set[str]:
    source_facts = extract_critical_facts(source_text)
    summary_facts_blob = summary_text.upper()
    missing = set()
    for fact in source_facts:
        # Normalize for a simple substring check (case-insensitive).
        if fact.upper() not in summary_facts_blob:
            missing.add(fact)
    return missing


# --------------------------------------------------------------------------- #
# Incremental summarizer
# --------------------------------------------------------------------------- #

SUMMARIZE_SYSTEM_PROMPT = """You maintain a running investigation summary for a fraud case.

You will be given the EXISTING summary (may be empty, if this is the first pass) and NEW
conversation turns that happened since that summary was last updated.

Produce an UPDATED summary that:
1. Preserves every critical fact from both the existing summary and the new turns:
   dollar amounts, case IDs, policy IDs, dates/day-references, and key decisions/conclusions.
2. Reads as a coherent narrative (what was investigated, in what order, what was concluded)
   -- not just a bullet list of disconnected facts.
3. Is concise: eliminate conversational filler, but NEVER at the cost of a concrete fact.
4. Does not add any fact, number, or conclusion that isn't present in the source material.

Output ONLY the updated summary text. No preamble, no meta-commentary."""

RETRY_REMINDER_TEMPLATE = """Your previous summary was missing these critical facts that MUST
be included: {missing_facts}. Produce the updated summary again, making sure every one of
these appears somewhere in it."""


class IncrementalSummarizer:
    def __init__(self, llm_model: str = "llama3.1") -> None:
        self.llm = ChatOllama(model=llm_model, temperature=0.0)
        self.prompt = ChatPromptTemplate.from_messages(
            [
                ("system", SUMMARIZE_SYSTEM_PROMPT),
                ("human", "EXISTING SUMMARY:\n{existing_summary}\n\nNEW TURNS:\n{new_turns}"),
            ]
        )
        self.retry_prompt = ChatPromptTemplate.from_messages(
            [
                ("system", SUMMARIZE_SYSTEM_PROMPT),
                ("human", "EXISTING SUMMARY:\n{existing_summary}\n\nNEW TURNS:\n{new_turns}"),
                ("human", RETRY_REMINDER_TEMPLATE),
            ]
        )

    @staticmethod
    def _format_turns(messages: list[BaseMessage]) -> str:
        lines = []
        for m in messages:
            role = "Analyst" if isinstance(m, HumanMessage) else "Copilot"
            lines.append(f"{role}: {m.content}")
        return "\n".join(lines)

    def summarize(self, existing_summary: str, new_messages: list[BaseMessage]) -> tuple[str, bool]:
        """Returns (new_summary, faithfulness_verified). If verification
        fails even after one retry, faithfulness_verified is False and the
        caller must NOT discard the raw messages."""
        new_turns_text = self._format_turns(new_messages)
        source_text = f"{existing_summary}\n{new_turns_text}"

        chain = self.prompt | self.llm
        candidate = chain.invoke(
            {"existing_summary": existing_summary or "(none yet)", "new_turns": new_turns_text}
        ).content

        missing = missing_facts(source_text, candidate)
        if not missing:
            logger.info("Summarization passed faithfulness check on first attempt.")
            return candidate, True

        logger.warning("Faithfulness check failed, missing facts: %s. Retrying once.", missing)
        retry_chain = self.retry_prompt | self.llm
        retried = retry_chain.invoke(
            {
                "existing_summary": existing_summary or "(none yet)",
                "new_turns": new_turns_text,
                "missing_facts": ", ".join(sorted(missing)),
            }
        ).content

        still_missing = missing_facts(source_text, retried)
        if not still_missing:
            logger.info("Summarization passed faithfulness check on retry.")
            return retried, True

        logger.error(
            "Summarization FAILED faithfulness check after retry. Missing: %s. "
            "Falling back to keeping raw messages (fail-safe).", still_missing,
        )
        return existing_summary, False


# --------------------------------------------------------------------------- #
# Graph state + nodes
# --------------------------------------------------------------------------- #

class InvestigationState(TypedDict):
    messages: list[BaseMessage]     # plain list -- last-value-wins, we manage appends explicitly
    running_summary: str
    case_id: str


SYSTEM_PROMPT_TEMPLATE = """You are the Helios Fraud Investigation Copilot for case {case_id}.

Running investigation summary so far:
{running_summary}

Continue the investigation using this summary plus the recent messages below."""


def generate_response_node(state: InvestigationState) -> dict:
    llm = ChatOllama(model="llama3.1", temperature=0.2)
    system_message = SystemMessage(
        content=SYSTEM_PROMPT_TEMPLATE.format(
            case_id=state["case_id"], running_summary=state["running_summary"] or "(none yet)"
        )
    )
    response = llm.invoke([system_message] + state["messages"])
    return {"messages": state["messages"] + [response]}


def maybe_summarize_node(state: InvestigationState) -> dict:
    messages = state["messages"]
    unsummarized_count = len(messages)  # everything in `messages` is, by construction, unsummarized

    if unsummarized_count <= KEEP_VERBATIM_LAST_N + SUMMARIZE_TRIGGER_EXCESS:
        return {}  # no-op: below trigger threshold

    to_fold_in = messages[: -KEEP_VERBATIM_LAST_N]
    keep_verbatim = messages[-KEEP_VERBATIM_LAST_N:]

    summarizer = IncrementalSummarizer()
    new_summary, verified = summarizer.summarize(state["running_summary"], to_fold_in)

    if verified:
        logger.info(
            "Summarization accepted: folded %d message(s) into running_summary, kept %d verbatim.",
            len(to_fold_in), len(keep_verbatim),
        )
        return {"messages": keep_verbatim, "running_summary": new_summary}

    logger.warning("Summarization rejected by faithfulness check; keeping all messages verbatim this round.")
    return {}  # fail-safe: change nothing, try again next turn once more context has accumulated


# --------------------------------------------------------------------------- #
# Graph assembly
# --------------------------------------------------------------------------- #

def build_investigation_graph():
    graph = StateGraph(InvestigationState)
    graph.add_node("generate_response", generate_response_node)
    graph.add_node("maybe_summarize", maybe_summarize_node)
    graph.add_edge(START, "generate_response")
    graph.add_edge("generate_response", "maybe_summarize")
    graph.add_edge("maybe_summarize", END)

    checkpointer = MemorySaver()
    return graph.compile(checkpointer=checkpointer)


def run_turn(app, case_id: str, user_text: str) -> str:
    config = {"configurable": {"thread_id": case_id}}
    existing_state = app.get_state(config)

    if existing_state.values:
        messages = list(existing_state.values["messages"])
        running_summary = existing_state.values.get("running_summary", "")
    else:
        messages, running_summary = [], ""

    messages = messages + [HumanMessage(content=user_text)]

    result = app.invoke(
        {"messages": messages, "running_summary": running_summary, "case_id": case_id},
        config=config,
    )
    return result["messages"][-1].content


# --------------------------------------------------------------------------- #
# Example run — a long-ish investigation session that triggers summarization
# --------------------------------------------------------------------------- #

if __name__ == "__main__":
    app = build_investigation_graph()
    case_id = "CASE-88421"

    turns = [
        "A $14,200 wire transfer on this account was flagged. Can you summarize why?",
        "Has this customer had any similar transfers before?",
        "The beneficiary account is linked to CASE-71190 from 5 months ago -- please note that.",
        "What did the customer say when we called them about the transfer from 18 days ago?",
        "Does Policy FR-14 apply here given everything so far?",
        "Sanctions screening came back clear for the beneficiary, for the record.",
        "Given everything, what's your recommendation for next steps?",
    ]

    for turn_num, user_text in enumerate(turns, start=1):
        print(f"\n{'=' * 70}\nTURN {turn_num}: {user_text}\n{'=' * 70}")
        answer = run_turn(app, case_id, user_text)
        print(f"COPILOT: {answer}")

        state = app.get_state({"configurable": {"thread_id": case_id}})
        print(f"\n[hot window size: {len(state.values['messages'])} messages | "
              f"running_summary length: {len(state.values['running_summary'])} chars]")

    final_state = app.get_state({"configurable": {"thread_id": case_id}})
    print(f"\n{'=' * 70}\nFINAL RUNNING SUMMARY\n{'=' * 70}")
    print(final_state.values["running_summary"] or "(summarization was never triggered in this run)")
```

### Notes on running this yourself

- With `KEEP_VERBATIM_LAST_N = 4` and `SUMMARIZE_TRIGGER_EXCESS = 4` (deliberately small for this demo), summarization should trigger somewhere around turn 4–5 in the example session — watch the log output for `"Summarization accepted"` and note how `running_summary` grows while the hot window's message count drops back down.
- The faithfulness check is intentionally narrow (dollar amounts, case IDs, policy IDs, day-references) rather than trying to verify *every* nuance of meaning was preserved — that's a deliberate trade-off: a lightweight, fast, regex-based check on the facts most likely to matter for a compliance-relevant record, not a full semantic-equivalence verifier (which would itself require another LLM call, with its own faithfulness questions).
- Try lowering `SUMMARIZE_TRIGGER_EXCESS` to force a mid-conversation summarization, then manually inspect whether `CASE-71190`, `Policy FR-14`, and `$14,200` survived into `running_summary` — this is the concrete, checkable guarantee this pattern is built around.
- The fail-safe path (`return {}` when the retry still fails) is worth deliberately triggering in testing — e.g. by mocking the summarizer to always drop a fact — to confirm the system genuinely keeps raw messages rather than ever silently accepting an unfaithful summary.

---

**Next up:** Pattern 11 — Context Deduplication, where we address a different kind of bloat: not "history is too long" but "the same fact, or near-duplicate content, is present multiple times across different sources," wasting tokens and risking conflicting-looking restatements of the same thing.
