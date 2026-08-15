# Pattern 02 — Context Filtering

[← Back to index](./README.md) | Previous: [Pattern 01 — Context Selection](./01-context-selection.md) | Next: Pattern 03 — Context Compression (queued)

---

## 1. Introduce the pattern

**Context Filtering** is the step that takes the candidate set produced by Context Selection and removes what shouldn't reach the LLM (or the audit log) at all — regardless of how "relevant" it scored.

Selection (Pattern 01) is recall-oriented: *"cast a wide net, score by relevance."* Filtering is a **precision-and-safety** pass on top of that net: *"now strip out noise, redundant boilerplate, off-topic fragments buried inside otherwise-relevant items, and anything sensitive that must never reach the model or a log."*

```
Selected candidates → [ FILTERING ] → cleaned, safe, high-signal context
                         │
                         ├── noise / boilerplate removal
                         ├── PII / sensitive-data redaction
                         ├── hard relevance floor (defense in depth)
                         └── policy / tone filtering (e.g. subjective commentary)
```

**Mental model:** if Selection is the paralegal pulling plausibly-relevant folders from the warehouse, Filtering is the redlining pass — crossing out irrelevant paragraphs, blacking out privileged/sensitive text, and tossing the folder that turned out to be a duplicate — before the folder ever reaches the lawyer's desk.

---

## 2. The problem it solves

A high relevance score does **not** mean an item is safe or clean to hand to an LLM. In production, three distinct problems show up inside "relevant" context:

1. **Sensitive data leakage.** A transaction-history item relevant to a fraud pattern might also contain a full account number, SSN fragment, or phone number. Sending that raw to an LLM (especially one that might log prompts, or that feeds an audit trail visible to more people than should see raw PII) is a compliance violation waiting to happen.
2. **Noise inside otherwise-relevant items.** A note might be 80% boilerplate ("Case opened. Case reviewed per SOP 4.2. Case escalated.") and 20% signal. Selection can't easily discard the whole item (the signal is real and relevant) but shouldn't forward the boilerplate either — it wastes tokens and dilutes attention.
3. **Unsafe or non-compliant content passing through unchecked.** An analyst note might contain unprofessional, subjective, or speculative commentary ("customer sounded shady on the phone") that shouldn't shape a compliance-relevant answer or be echoed back in an audit-facing response.

Without an explicit filtering stage, these problems get "solved" ad hoc and inconsistently — a regex bolted onto one call site, a prompt instruction ("please don't repeat PII") that the model may or may not honor. Filtering makes this a first-class, testable, auditable pipeline stage instead.

---

## 3. A realistic enterprise problem (Helios)

Continuing the `CASE-88421` investigation from Pattern 01: the selection stage returned several transaction-history and analyst-note items. Two problems surface in the raw data:

- One transaction record includes the full destination account number and the customer's registered phone number — both must be redacted before the item reaches the LLM prompt or gets written into the audit log, per Helios's data-handling policy.
- One analyst note is mostly SOP boilerplate ("Note logged. Reviewed per SOP 4.2. Escalated to tier 2.") with a single relevant sentence buried inside, and another note contains subjective, non-evidentiary commentary ("customer seemed nervous, didn't like their tone") that should not influence a compliance-facing fraud determination.

Context Filtering is the stage responsible for catching all three before they reach `ChatOllama`.

---

## 4. Architecture / flow diagram

```mermaid
flowchart TD
    A[Selected ContextItems\n(from Pattern 01)] --> B[Filter Pipeline]

    subgraph B["Filter Pipeline (ordered, composable)"]
        F1[PII / Sensitive-Data\nRedaction Filter]
        F2[Noise / Boilerplate\nSentence Filter]
        F3[Subjective / Non-Evidentiary\nLanguage Filter]
        F4[Hard Relevance Floor\n(defense in depth)]
        F1 --> F2 --> F3 --> F4
    end

    A --> F1
    F4 --> C{Item survives\nall filters?}
    C -- No --> D[Dropped\n+ logged reason]
    C -- Yes --> E[Cleaned ContextItem]
    E --> G[Assembled Context Block]
    G --> H[ChatOllama LLM]
    H --> I[Answer to Analyst]

    style B fill:#4A90D9,color:#fff
    style D fill:#C0392B,color:#fff
```

---

## 5. The complete request-to-response flow

1. **Input**: the scored, budget-selected `ContextItem`s from Pattern 01's `ContextSelector.select()`.
2. **PII redaction pass**: every item's content is scanned with regex-based detectors (account numbers, phone numbers, emails, SSNs) and matches are replaced with typed placeholders (e.g. `[ACCOUNT_REDACTED]`), never silently dropped — the LLM still needs to know *that* an account number was referenced.
3. **Noise removal pass**: each item's content is split into sentences; a lightweight boilerplate classifier (regex/keyword-based, fast and deterministic) strips known SOP boilerplate sentences while preserving the signal sentences around them.
4. **Subjective-language pass**: sentences matching a curated list of subjective/speculative phrasing patterns are flagged and removed — this keeps analyst notes evidentiary rather than opinion-laden, which matters for compliance defensibility.
5. **Hard relevance floor**: even after Selection's budget-aware pick, items whose relevance score falls under an absolute floor (e.g. `0.15`) are dropped outright — a defense-in-depth check in case Selection's `force_include` or blended scoring let something weak through.
6. **Every drop and every redaction is logged** with the item id and reason — this audit trail is exactly what a compliance reviewer will ask for later ("what did the model see, and what was scrubbed from it").
7. **Surviving, cleaned items** are re-assembled into the context block and handed to the same `ChatOllama` prompt shape used in Pattern 01.

---

## 6. Why this pattern is appropriate here

- **Selection and Filtering answer different questions and should stay decoupled.** Selection asks "is this plausibly relevant, and does it fit the budget?" Filtering asks "is this *safe and clean* to actually show?" Merging them into one step makes the relevance-scoring code fragile (constantly patched with one-off safety exceptions) instead of composable.
- **Redaction ≠ deletion.** A naive approach drops any item containing PII. That throws away real signal (the fact that a *new beneficiary account* was involved is highly relevant to the fraud case) along with the sensitive value. Filtering replaces the sensitive span with a typed placeholder so the semantic content survives while the raw sensitive value doesn't.
- **Determinism matters for compliance.** PII redaction and boilerplate stripping are implemented as regex/rule-based filters, not LLM calls — they need to be deterministic, fast, cheap, and independently auditable, not subject to model sampling variance. (Compare this to Pattern 10, Context Summarization, where LLM-based transformation *is* the right tool.)
- **Defense in depth.** The hard relevance floor at the end of the pipeline isn't redundant with Selection's scoring — it protects against upstream misconfiguration (e.g. an overly generous `force_include` set, or a source with a too-high `base_priority`) actually reaching the model.

---

## 7. Production-quality Python implementation

```python
"""
helios_context_filtering.py

Pattern 02 — Context Filtering
Helios Fraud Investigation Copilot

Takes the selected ContextItems from Pattern 01 and runs them through an
ordered, composable filter pipeline: PII redaction, boilerplate/noise
removal, subjective-language removal, and a hard relevance floor.

Requirements:
    pip install "langchain-core>=0.3.60" "langchain-ollama>=0.3.0" "numpy>=1.26"

Assumes a local Ollama server (`ollama serve`) with:
    ollama pull llama3.1
    ollama pull nomic-embed-text
"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass, field, replace
from datetime import datetime, timedelta
from enum import Enum
from typing import Protocol

from langchain_core.prompts import ChatPromptTemplate
from langchain_ollama import ChatOllama, OllamaEmbeddings
import numpy as np

logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")
logger = logging.getLogger("helios.context_filtering")


# --------------------------------------------------------------------------- #
# Domain model (re-declared here so this file is self-contained;
# identical shape to Pattern 01's ContextItem)
# --------------------------------------------------------------------------- #

class ContextSourceType(str, Enum):
    CASE_METADATA = "case_metadata"
    TRANSACTION_HISTORY = "transaction_history"
    CUSTOMER_PROFILE = "customer_profile"
    SIMILAR_CASES = "similar_cases"
    COMPLIANCE_POLICY = "compliance_policy"
    ANALYST_NOTES = "analyst_notes"


def estimate_tokens(text: str) -> int:
    return max(1, len(text) // 4)


@dataclass
class ContextItem:
    source: ContextSourceType
    content: str
    timestamp: datetime
    item_id: str
    relevance_score: float = 0.0
    token_count: int = field(default=0)

    def __post_init__(self) -> None:
        if self.token_count == 0:
            self.token_count = estimate_tokens(self.content)


# --------------------------------------------------------------------------- #
# Filter protocol + result tracking
# --------------------------------------------------------------------------- #

class ContextFilter(Protocol):
    """Every filter either transforms an item's content (redaction/trimming)
    or decides to drop it entirely. Returning None means 'drop this item.'"""

    name: str

    def apply(self, item: ContextItem) -> ContextItem | None: ...


@dataclass
class FilterAuditEntry:
    item_id: str
    filter_name: str
    action: str  # "redacted" | "trimmed" | "dropped"
    detail: str


# --------------------------------------------------------------------------- #
# Filter 1 — PII / sensitive-data redaction (deterministic, regex-based)
# --------------------------------------------------------------------------- #

class PIIRedactionFilter:
    name = "pii_redaction"

    # Ordered (label, pattern) — order matters so more specific patterns
    # (e.g. account numbers) are checked before looser ones (e.g. generic digit runs).
    PATTERNS: list[tuple[str, re.Pattern]] = [
        ("EMAIL", re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+")),
        ("PHONE", re.compile(r"\b(?:\+?1[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}\b")),
        ("SSN", re.compile(r"\b\d{3}-\d{2}-\d{4}\b")),
        ("ACCOUNT_NUMBER", re.compile(r"\bACC-\d{4,}\b")),
        ("CARD_NUMBER", re.compile(r"\b(?:\d[ -]*?){13,19}\b")),
    ]

    def __init__(self, audit_log: list[FilterAuditEntry]) -> None:
        self.audit_log = audit_log

    def apply(self, item: ContextItem) -> ContextItem | None:
        content = item.content
        redacted_labels: list[str] = []

        for label, pattern in self.PATTERNS:
            def _redact(match: re.Match, label=label) -> str:
                redacted_labels.append(label)
                return f"[{label}_REDACTED]"

            content = pattern.sub(_redact, content)

        if redacted_labels:
            self.audit_log.append(
                FilterAuditEntry(
                    item_id=item.item_id,
                    filter_name=self.name,
                    action="redacted",
                    detail=f"redacted: {', '.join(sorted(set(redacted_labels)))}",
                )
            )
            return replace(item, content=content, token_count=estimate_tokens(content))

        return item


# --------------------------------------------------------------------------- #
# Filter 2 — Boilerplate / noise sentence removal (deterministic)
# --------------------------------------------------------------------------- #

class BoilerplateFilter:
    name = "boilerplate_removal"

    BOILERPLATE_PATTERNS: list[re.Pattern] = [
        re.compile(r"^\s*case (opened|logged|closed)\b.*$", re.IGNORECASE),
        re.compile(r"^\s*(note logged|reviewed per sop[\w .]*|escalated to tier \d)\.?\s*$", re.IGNORECASE),
        re.compile(r"^\s*no further action (required|taken)\.?\s*$", re.IGNORECASE),
    ]

    def __init__(self, audit_log: list[FilterAuditEntry]) -> None:
        self.audit_log = audit_log

    def apply(self, item: ContextItem) -> ContextItem | None:
        sentences = re.split(r"(?<=[.!?])\s+", item.content.strip())
        kept, dropped = [], []

        for sentence in sentences:
            if any(p.match(sentence.strip()) for p in self.BOILERPLATE_PATTERNS):
                dropped.append(sentence)
            else:
                kept.append(sentence)

        if not kept:
            self.audit_log.append(
                FilterAuditEntry(item.item_id, self.name, "dropped", "entire item was boilerplate")
            )
            return None

        if dropped:
            self.audit_log.append(
                FilterAuditEntry(
                    item.item_id, self.name, "trimmed",
                    f"removed {len(dropped)} boilerplate sentence(s)",
                )
            )

        new_content = " ".join(kept)
        return replace(item, content=new_content, token_count=estimate_tokens(new_content))


# --------------------------------------------------------------------------- #
# Filter 3 — Subjective / non-evidentiary language removal (deterministic)
# --------------------------------------------------------------------------- #

class SubjectiveLanguageFilter:
    name = "subjective_language_removal"

    SUBJECTIVE_MARKERS = [
        r"\bseemed\b", r"\bsounded\b", r"\bfelt (like|shady|off)\b",
        r"\bi (think|believe|suspect|feel)\b", r"\bdidn'?t like (his|her|their) tone\b",
        r"\bhonestly\b", r"\bkind of (sketchy|suspicious)\b",
    ]

    def __init__(self, audit_log: list[FilterAuditEntry]) -> None:
        self.audit_log = audit_log
        self._pattern = re.compile("|".join(self.SUBJECTIVE_MARKERS), re.IGNORECASE)

    def apply(self, item: ContextItem) -> ContextItem | None:
        sentences = re.split(r"(?<=[.!?])\s+", item.content.strip())
        kept = [s for s in sentences if not self._pattern.search(s)]
        removed_count = len(sentences) - len(kept)

        if removed_count == 0:
            return item

        if not kept:
            self.audit_log.append(
                FilterAuditEntry(item.item_id, self.name, "dropped", "entire item was subjective commentary")
            )
            return None

        self.audit_log.append(
            FilterAuditEntry(
                item.item_id, self.name, "trimmed",
                f"removed {removed_count} subjective/non-evidentiary sentence(s)",
            )
        )
        new_content = " ".join(kept)
        return replace(item, content=new_content, token_count=estimate_tokens(new_content))


# --------------------------------------------------------------------------- #
# Filter 4 — Hard relevance floor (defense in depth against upstream misconfig)
# --------------------------------------------------------------------------- #

class RelevanceFloorFilter:
    name = "relevance_floor"

    def __init__(self, audit_log: list[FilterAuditEntry], floor: float = 0.15) -> None:
        self.audit_log = audit_log
        self.floor = floor

    def apply(self, item: ContextItem) -> ContextItem | None:
        if item.relevance_score < self.floor:
            self.audit_log.append(
                FilterAuditEntry(
                    item.item_id, self.name, "dropped",
                    f"relevance_score={item.relevance_score:.3f} below floor={self.floor}",
                )
            )
            return None
        return item


# --------------------------------------------------------------------------- #
# Filter pipeline
# --------------------------------------------------------------------------- #

class ContextFilterPipeline:
    """Runs an ordered list of filters over each ContextItem. An item that is
    dropped by any filter is excluded from the final output; every action
    (redact / trim / drop) is captured in a single audit log for compliance review."""

    def __init__(self, relevance_floor: float = 0.15) -> None:
        self.audit_log: list[FilterAuditEntry] = []
        self.filters: list[ContextFilter] = [
            PIIRedactionFilter(self.audit_log),
            BoilerplateFilter(self.audit_log),
            SubjectiveLanguageFilter(self.audit_log),
            RelevanceFloorFilter(self.audit_log, floor=relevance_floor),
        ]

    def run(self, items: list[ContextItem]) -> list[ContextItem]:
        survivors: list[ContextItem] = []

        for item in items:
            current: ContextItem | None = item
            for f in self.filters:
                if current is None:
                    break
                current = f.apply(current)
            if current is not None:
                survivors.append(current)

        logger.info(
            "Filtering complete: %d/%d items survived | %d audit events",
            len(survivors), len(items), len(self.audit_log),
        )
        return survivors

    def print_audit_log(self) -> None:
        for entry in self.audit_log:
            print(f"  [{entry.filter_name:<26}] {entry.item_id:<20} {entry.action:<9} — {entry.detail}")


# --------------------------------------------------------------------------- #
# Example run — reusing Pattern 01's shape, with deliberately messy input
# --------------------------------------------------------------------------- #

def build_example_items() -> list[ContextItem]:
    now = datetime.utcnow()
    return [
        ContextItem(
            source=ContextSourceType.CASE_METADATA,
            content=(
                "Case CASE-88421: Wire transfer of $14,200 from customer ACC-55210 to a "
                "new beneficiary in a high-risk jurisdiction, flagged for review."
            ),
            timestamp=now - timedelta(hours=2),
            item_id="CASE-88421-meta",
            relevance_score=0.92,
        ),
        ContextItem(
            source=ContextSourceType.TRANSACTION_HISTORY,
            content=(
                "Customer ACC-55210 wire transfer $9,800 to new beneficiary. "
                "Contact on file: jane.doe@example.com, phone 415-555-0199. "
                "Destination account ACC-99231."
            ),
            timestamp=now - timedelta(days=18),
            item_id="ACC-55210-txn-1",
            relevance_score=0.81,
        ),
        ContextItem(
            source=ContextSourceType.ANALYST_NOTES,
            content=(
                "Note logged. Reviewed per SOP 4.2. Escalated to tier 2. "
                "Customer confirmed the transfer was for a legitimate business payment. "
                "No further action required."
            ),
            timestamp=now - timedelta(days=18),
            item_id="ACC-55210-note-1",
            relevance_score=0.55,
        ),
        ContextItem(
            source=ContextSourceType.ANALYST_NOTES,
            content=(
                "Honestly the customer sounded shady on the phone, I think he was lying "
                "about the payment reason, felt off to me."
            ),
            timestamp=now - timedelta(days=5),
            item_id="ACC-55210-note-2",
            relevance_score=0.40,
        ),
        ContextItem(
            source=ContextSourceType.COMPLIANCE_POLICY,
            content="Policy HR-02: Employee expense reimbursements over $500 require director approval.",
            timestamp=now - timedelta(days=200),
            item_id="policy-hr02",
            relevance_score=0.08,  # will be dropped by the relevance floor
        ),
    ]


if __name__ == "__main__":
    pipeline = ContextFilterPipeline(relevance_floor=0.15)
    raw_items = build_example_items()

    cleaned_items = pipeline.run(raw_items)

    print("=== FILTER AUDIT LOG ===")
    pipeline.print_audit_log()

    print("\n=== CLEANED CONTEXT ITEMS (safe to send to the LLM) ===")
    for item in cleaned_items:
        print(f"\n[{item.source.value} | {item.item_id} | score={item.relevance_score:.2f}]")
        print(f"  {item.content}")

    # Hand the cleaned items to the same ChatOllama prompt shape as Pattern 01
    context_block = "\n\n".join(f"[{i.source.value}] {i.content}" for i in cleaned_items)
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", "You are the Helios Fraud Investigation Copilot. Answer using ONLY the context given."),
            ("human", "Analyst question: {query}\n\n--- CLEANED CONTEXT ---\n{context}"),
        ]
    )
    llm = ChatOllama(model="llama3.1", temperature=0.1)
    chain = prompt | llm
    response = chain.invoke(
        {
            "query": "Why was this transaction flagged, and does this customer's recent behavior match a known fraud pattern?",
            "context": context_block,
        }
    )

    print("\n=== COPILOT ANSWER (grounded in filtered context) ===")
    print(response.content)
```

### Notes on running this yourself

- The `PIIRedactionFilter` intentionally **replaces**, never blindly deletes — `[ACCOUNT_NUMBER_REDACTED]` preserves the fact that an account number was referenced, which the LLM (and the fraud pattern-matching it's doing) still needs.
- `BoilerplateFilter` and `SubjectiveLanguageFilter` operate at **sentence granularity**, not item granularity — this is the key difference from Selection, which only ever includes/excludes whole items.
- All four filters are deterministic and regex/rule-based on purpose (see section 6) — if you need *semantic* noise detection (e.g., "this paragraph is off-topic but doesn't match any keyword pattern"), that becomes an LLM-assisted classifier filter, which is a reasonable extension but should be treated as a distinct, more expensive filter stage — not the default.
- `pipeline.audit_log` is exactly what you'd persist alongside the case record for compliance/audit purposes — "here is every redaction and drop that happened before the model saw this."

---

**Next up:** Pattern 03 — Context Compression, where we take the filtered, clean set from this pattern and shrink it further — condensing verbose-but-relevant content instead of dropping items outright — when even the cleaned set doesn't fit the budget.
