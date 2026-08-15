# Pattern 03 — Context Compression

[← Back to index](./README.md) | Previous: [Pattern 02 — Context Filtering](./02-context-filtering.md) | Next: Pattern 04 — Context Ranking (queued)

---

## 1. Introduce the pattern

**Context Compression** takes context that has already been selected (Pattern 01) and cleaned (Pattern 02) — and is still too large, or still carries more verbosity than signal — and shrinks it **without dropping the item entirely**.

This is the key distinction from the earlier patterns: Selection and Filtering decide *whole-item* in/out. Compression operates *within* a kept item, condensing it to fewer tokens while trying to preserve the facts that matter.

```
Cleaned context items → [ COMPRESSION ] → same items, fewer tokens each
                            │
                            ├── extractive: keep the most information-dense sentences as-is
                            ├── abstractive: LLM rewrites/condenses, preserving key facts
                            └── structural: turn prose into a dense key:value / bullet form
```

**Mental model:** Filtering is redlining a document (crossing out paragraphs that shouldn't be there at all). Compression is what a paralegal does to the paragraphs that *do* belong — turning three pages of a transaction log into a two-line dense summary of the pattern, without inventing anything that isn't in the source.

---

## 2. The problem it solves

Even after selection and filtering, production context is frequently more verbose than it needs to be:

1. **Verbose source formats.** Transaction logs, case notes, and policy documents are written for human readability (full sentences, repeated context, formal boilerplate phrasing) — not for token efficiency. A paragraph that conveys three facts might cost 120 tokens when those three facts could be conveyed in 25.
2. **Budget pressure from legitimately relevant items.** Sometimes everything that survives Selection + Filtering is genuinely relevant, but there's still more of it than the token budget allows — especially in long-running investigations with many prior notes and transactions. Dropping more items (going back to Selection) throws away real signal; Compression is the tool that fits more *signal* into the same space instead of fitting fewer *items*.
3. **Attention dilution.** LLMs perform worse when relevant facts are wrapped in verbose phrasing — signal-to-noise ratio matters for reasoning quality, not just for token cost. A compressed, fact-dense representation is often easier for the model to reason over correctly, not just cheaper.

Without a compression stage, teams either overspend on token budget (raising costs and latency) or under-fit by dropping items that were actually needed — Compression gives you a third option: keep the item, shrink its footprint.

---

## 3. A realistic enterprise problem (Helios)

Continuing the `CASE-88421` investigation: the customer in question has been with the bank for six years and has 40+ prior analyst notes and hundreds of transactions in the lookback window used for pattern matching. After Selection and Filtering, roughly 15 genuinely relevant items survive — but at their original verbosity, they total ~5,800 tokens, well over the 3,000-token budget used in Patterns 01–02.

Two different kinds of compression are needed here:

- **Transaction history** is naturally *structural* — a paragraph like *"On March 3rd, the customer initiated a wire transfer in the amount of $9,800 to a beneficiary that had been added to the account only eighteen days prior..."* compresses cleanly into a dense line: `Mar 3 | wire $9,800 | new beneficiary (+18d) | high-risk jurisdiction`.
- **Analyst notes**, by contrast, contain nuanced narrative reasoning that doesn't compress well structurally — these benefit from *abstractive* compression: an LLM condenses the note into 1-2 sentences that preserve the analyst's actual conclusion, not just its keywords.

Context Compression is the stage that applies the right strategy per content type, so the case fits the token budget without losing the facts a fraud determination depends on.

---

## 4. Architecture / flow diagram

```mermaid
flowchart TD
    A[Filtered ContextItems\n(from Pattern 02)] --> B{Compression Router}

    B -->|structured/tabular content\ne.g. transaction history| C[Structural Compressor\n(deterministic, template-based)]
    B -->|narrative/prose content\ne.g. analyst notes, policy text| D[Abstractive Compressor\n(ChatOllama-based, extractive-constrained)]
    B -->|already dense / short| E[Pass-through\n(no compression needed)]

    C --> F[Compressed ContextItem]
    D --> F
    E --> F

    F --> G{Fits token budget?}
    G -- No, still over --> H[Compress again at higher ratio\nor escalate to Pattern 05\n(Prioritization) to drop lowest-value items]
    G -- Yes --> I[Assembled Context Block]
    I --> J[ChatOllama LLM]
    J --> K[Answer to Analyst]

    style B fill:#4A90D9,color:#fff
    style D fill:#4A90D9,color:#fff
```

---

## 5. The complete request-to-response flow

1. **Input**: the cleaned `ContextItem`s from Pattern 02's `ContextFilterPipeline.run()`.
2. **Route by content shape**: each item's source type determines its compression strategy — transaction history and case metadata go through the deterministic **structural compressor**; analyst notes and policy text go through the **abstractive (LLM-based) compressor**; anything already under a minimum length threshold passes through unchanged.
3. **Structural compression**: regex/parsing extracts key fields (date, amount, beneficiary status, jurisdiction flag) from prose and re-renders them as a dense, consistent template string.
4. **Abstractive compression**: for narrative content, a `ChatOllama` call with a tightly constrained prompt ("condense to at most N tokens, preserve every concrete fact, do not add new claims") rewrites the content. The prompt explicitly forbids adding information not present in the source — this is a summarization task with a **no-hallucination constraint**, not creative rewriting.
5. **Token accounting**: each compressed item's new token count is measured; a compression ratio (original / compressed) is logged per item for observability.
6. **Budget re-check**: if the compressed set still exceeds budget, the pipeline can either compress harder (lower target ratio) or hand off to Pattern 05 (Prioritization) to drop the lowest-value remaining items — Compression's job is to shrink, not to decide what's expendable.
7. **Assemble & prompt**: the compressed context block replaces the verbose one in the same prompt structure used in Patterns 01–02, and `ChatOllama` generates the grounded answer.

---

## 6. Why this pattern is appropriate here

- **Compression trades a small amount of nuance for a large amount of headroom.** In a fraud investigation, the *facts* (amount, date, beneficiary status, jurisdiction) matter far more than the prose style they were originally written in — structural compression can achieve 4-6x reduction on transaction-log-style content with effectively zero information loss.
- **Different content types need different compression strategies.** Applying an LLM-abstractive summarizer to already-tabular transaction data is wasteful (slow, costly, and prone to introducing subtle rewording errors) when a deterministic template does the job better and faster. Conversely, applying regex-based extraction to nuanced analyst narrative would destroy the reasoning it contains. Routing by content shape is what makes this pattern effective rather than a blunt "summarize everything" hammer.
- **The no-hallucination constraint is non-negotiable in this domain.** A compressed note that quietly drops "customer confirmed this was NOT a legitimate transfer" would be a compliance and safety failure, not just a quality issue. This is why the abstractive compressor's prompt is deliberately restrictive and — in a real production system — would be paired with a verification pass (e.g., checking that key entities/numbers from the original appear in the compressed output).
- **It composes cleanly with the patterns before and after it.** Compression assumes its input is already relevant (Selection) and clean (Filtering) — and its output is exactly the kind of scored, sized item that Pattern 04 (Ranking) and Pattern 05 (Prioritization) need to make final budget decisions.

---

## 7. Production-quality Python implementation

```python
"""
helios_context_compression.py

Pattern 03 — Context Compression
Helios Fraud Investigation Copilot

Takes the filtered ContextItems from Pattern 02 and compresses them:
- structural/deterministic compression for tabular/transactional content
- abstractive (LLM-based, no-hallucination-constrained) compression for
  narrative content (analyst notes, policy text)

Requirements:
    pip install "langchain-core>=0.3.60" "langchain-ollama>=0.3.0"

Assumes a local Ollama server (`ollama serve`) with:
    ollama pull llama3.1
"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass, field, replace
from datetime import datetime, timedelta
from enum import Enum

from langchain_core.prompts import ChatPromptTemplate
from langchain_ollama import ChatOllama

logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")
logger = logging.getLogger("helios.context_compression")


# --------------------------------------------------------------------------- #
# Domain model (same shape as Patterns 01-02, self-contained here)
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
    original_token_count: int = field(default=0)  # preserved for compression-ratio reporting

    def __post_init__(self) -> None:
        if self.token_count == 0:
            self.token_count = estimate_tokens(self.content)
        if self.original_token_count == 0:
            self.original_token_count = self.token_count


# --------------------------------------------------------------------------- #
# Structural compressor — deterministic, template-based
# Good fit for content that is fundamentally tabular/transactional in nature.
# --------------------------------------------------------------------------- #

class StructuralCompressor:
    """
    Extracts key fields from verbose transactional prose via regex and
    re-renders them as a dense template. Zero hallucination risk because
    nothing is generated — only extracted and reformatted.
    """

    AMOUNT_RE = re.compile(r"\$([\d,]+(?:\.\d{2})?)")
    NEW_BENEFICIARY_RE = re.compile(r"new(?:ly added)? beneficiary", re.IGNORECASE)
    HIGH_RISK_RE = re.compile(r"high-risk jurisdiction", re.IGNORECASE)
    DAYS_AGO_RE = re.compile(r"(\d+)\s+days?\s+ago", re.IGNORECASE)
    ACTION_RE = re.compile(r"\b(wire transfer|atm withdrawal|deposit|payment)\b", re.IGNORECASE)

    def compress(self, item: ContextItem) -> ContextItem:
        content = item.content
        amount_match = self.AMOUNT_RE.search(content)
        action_match = self.ACTION_RE.search(content)
        age_match = self.DAYS_AGO_RE.search(content)

        parts: list[str] = []
        if age_match:
            parts.append(f"{age_match.group(1)}d ago")
        if action_match:
            parts.append(action_match.group(1).lower())
        if amount_match:
            parts.append(f"${amount_match.group(1)}")
        if self.NEW_BENEFICIARY_RE.search(content):
            parts.append("new beneficiary")
        if self.HIGH_RISK_RE.search(content):
            parts.append("high-risk jurisdiction")

        if not parts:
            # Nothing structural detected — not a good candidate for this
            # compressor, return unchanged rather than risk losing information.
            return item

        compressed_content = " | ".join(parts)
        return replace(
            item,
            content=compressed_content,
            token_count=estimate_tokens(compressed_content),
        )


# --------------------------------------------------------------------------- #
# Abstractive compressor — LLM-based, constrained against hallucination
# Good fit for narrative content where facts are entangled with reasoning.
# --------------------------------------------------------------------------- #

COMPRESSION_SYSTEM_PROMPT = """You compress fraud-investigation text for an internal analyst tool.

Rules — follow them exactly:
1. Preserve every concrete fact (amounts, dates, entities, decisions, outcomes).
2. Do NOT add any claim, inference, or detail that is not explicitly present in the source text.
3. Do NOT soften, hedge, or embellish. Output plain factual statements only.
4. Target length: at most {target_tokens} tokens (~{target_words} words).
5. Output ONLY the compressed text. No preamble, no explanation, no quotation marks."""


class AbstractiveCompressor:
    def __init__(self, llm_model: str = "llama3.1") -> None:
        self.llm = ChatOllama(model=llm_model, temperature=0.0)  # temperature 0: minimize drift/hallucination
        self.prompt = ChatPromptTemplate.from_messages(
            [
                ("system", COMPRESSION_SYSTEM_PROMPT),
                ("human", "Source text:\n{source_text}"),
            ]
        )

    def compress(self, item: ContextItem, target_ratio: float = 0.4) -> ContextItem:
        target_tokens = max(15, int(item.token_count * target_ratio))
        target_words = int(target_tokens * 0.75)  # rough tokens->words conversion for the prompt

        chain = self.prompt | self.llm
        response = chain.invoke(
            {
                "target_tokens": target_tokens,
                "target_words": target_words,
                "source_text": item.content,
            }
        )
        compressed_content = response.content.strip()

        # Safety net: if the model ignored the length constraint, fall back
        # to the original rather than silently accepting a bloated "compression."
        new_tokens = estimate_tokens(compressed_content)
        if new_tokens >= item.token_count:
            logger.warning(
                "Abstractive compression did not reduce size for %s (kept original)", item.item_id
            )
            return item

        return replace(item, content=compressed_content, token_count=new_tokens)


# --------------------------------------------------------------------------- #
# Compression router + pipeline
# --------------------------------------------------------------------------- #

STRUCTURAL_SOURCES = {ContextSourceType.TRANSACTION_HISTORY}
ABSTRACTIVE_SOURCES = {ContextSourceType.ANALYST_NOTES, ContextSourceType.COMPLIANCE_POLICY,
                        ContextSourceType.SIMILAR_CASES}
PASSTHROUGH_MIN_TOKENS = 40  # items this short aren't worth compressing


@dataclass
class CompressionReport:
    item_id: str
    strategy: str
    original_tokens: int
    compressed_tokens: int

    @property
    def ratio(self) -> float:
        return round(self.compressed_tokens / self.original_tokens, 2) if self.original_tokens else 1.0


class ContextCompressionPipeline:
    def __init__(self, llm_model: str = "llama3.1", target_ratio: float = 0.4) -> None:
        self.structural = StructuralCompressor()
        self.abstractive = AbstractiveCompressor(llm_model=llm_model)
        self.target_ratio = target_ratio
        self.reports: list[CompressionReport] = []

    def compress_item(self, item: ContextItem) -> ContextItem:
        original_tokens = item.token_count

        if item.token_count <= PASSTHROUGH_MIN_TOKENS:
            strategy = "passthrough"
            result = item
        elif item.source in STRUCTURAL_SOURCES:
            strategy = "structural"
            result = self.structural.compress(item)
        elif item.source in ABSTRACTIVE_SOURCES:
            strategy = "abstractive"
            result = self.abstractive.compress(item, target_ratio=self.target_ratio)
        else:
            strategy = "passthrough"
            result = item

        self.reports.append(
            CompressionReport(
                item_id=item.item_id,
                strategy=strategy,
                original_tokens=original_tokens,
                compressed_tokens=result.token_count,
            )
        )
        return result

    def run(self, items: list[ContextItem]) -> list[ContextItem]:
        compressed = [self.compress_item(item) for item in items]

        total_before = sum(r.original_tokens for r in self.reports)
        total_after = sum(r.compressed_tokens for r in self.reports)
        logger.info(
            "Compression complete: %d -> %d tokens (%.0f%% of original)",
            total_before, total_after, 100 * total_after / max(1, total_before),
        )
        return compressed

    def print_report(self) -> None:
        for r in self.reports:
            print(f"  [{r.strategy:<12}] {r.item_id:<20} {r.original_tokens:>4} -> {r.compressed_tokens:<4} tokens (ratio={r.ratio})")


# --------------------------------------------------------------------------- #
# Example run
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
                "On the date 18 days ago, the customer initiated a wire transfer in the "
                "amount of $9,800 to a beneficiary that had been newly added to the account. "
                "The destination was flagged as being in a high-risk jurisdiction, consistent "
                "with prior activity on this account."
            ),
            timestamp=now - timedelta(days=18),
            item_id="ACC-55210-txn-1",
            relevance_score=0.81,
        ),
        ContextItem(
            source=ContextSourceType.ANALYST_NOTES,
            content=(
                "During the review of the transaction from 18 days ago, I contacted the "
                "customer directly by phone to verify intent. The customer explained that "
                "the payment was for a legitimate international business contract with a "
                "new overseas supplier, and provided a corresponding invoice number as "
                "supporting documentation. Based on this explanation and the supporting "
                "documentation provided, I determined the transaction did not warrant "
                "further escalation at that time and closed the review as legitimate."
            ),
            timestamp=now - timedelta(days=18),
            item_id="ACC-55210-note-1",
            relevance_score=0.60,
        ),
        ContextItem(
            source=ContextSourceType.SIMILAR_CASES,
            content=(
                "In a separate case from five months prior, CASE-71190, the fraud team "
                "confirmed an account-takeover scenario. In that case, the pattern involved "
                "a new beneficiary being added to the account shortly before an unusually "
                "large wire transfer was initiated to the same high-risk jurisdiction referenced "
                "in the current case, and it was later discovered that the customer's phone "
                "number on file had also been changed just days before the fraudulent transfer "
                "occurred. Funds were ultimately recovered after the transaction was placed on "
                "a 48-hour mandatory hold pending investigation."
            ),
            timestamp=now - timedelta(days=150),
            item_id="similar-71190",
            relevance_score=0.70,
        ),
    ]


if __name__ == "__main__":
    pipeline = ContextCompressionPipeline(target_ratio=0.4)
    items = build_example_items()

    compressed_items = pipeline.run(items)

    print("=== COMPRESSION REPORT ===")
    pipeline.print_report()

    print("\n=== COMPRESSED CONTEXT ITEMS ===")
    for item in compressed_items:
        print(f"\n[{item.source.value} | {item.item_id}]")
        print(f"  {item.content}")

    # Hand the compressed items to the same ChatOllama prompt shape as Patterns 01-02
    context_block = "\n\n".join(f"[{i.source.value}] {i.content}" for i in compressed_items)
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", "You are the Helios Fraud Investigation Copilot. Answer using ONLY the context given."),
            ("human", "Analyst question: {query}\n\n--- COMPRESSED CONTEXT ---\n{context}"),
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

    print("\n=== COPILOT ANSWER (grounded in compressed context) ===")
    print(response.content)
```

### Notes on running this yourself

- The `StructuralCompressor` is intentionally conservative: if it can't confidently extract structured fields, it returns the item **unchanged** rather than guessing — a partial, wrong compression is worse than no compression.
- The `AbstractiveCompressor` runs at `temperature=0.0` specifically to minimize stochastic drift between runs — you want compression to be as close to deterministic as an LLM call can be, since it's now part of an audited pipeline.
- The safety-net check (`if new_tokens >= item.token_count: keep original`) exists because LLMs occasionally ignore length constraints in the prompt — never trust a size constraint without verifying it on the output.
- In a production Helios deployment, you'd add a **fact-verification pass**: extract key entities/numbers from the original via regex, and assert they still appear in the compressed output before accepting it — cheap insurance against silent information loss.
- `CompressionReport.ratio` per item is exactly the kind of metric you'd track over time to catch regressions (e.g., a prompt change that suddenly makes compression far less effective, or a model swap that starts silently dropping facts).

---

**Next up:** Pattern 04 — Context Ranking, where we take items that have now been selected, filtered, and compressed, and determine the *order* in which they should appear in the final prompt — because position within the context window measurably affects how much attention an LLM pays to a given fact.
