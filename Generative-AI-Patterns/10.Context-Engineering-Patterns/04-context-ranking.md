# Pattern 04 — Context Ranking

[← Back to index](./README.md) | Previous: [Pattern 03 — Context Compression](./03-context-compression.md) | Next: Pattern 05 — Context Prioritization (queued)

---

## 1. Introduce the pattern

**Context Ranking** decides the *order* in which an already-fixed set of context items appears in the final prompt.

This is a subtly different question from every pattern before it:

- Selection (01) asked: *which sources/items are candidates?*
- Filtering (02) asked: *what must be removed from those items?*
- Compression (03) asked: *how do I shrink the items I'm keeping?*
- **Ranking (04) asks: given the exact set of items I'm keeping, in what sequence should they appear?**

```
Compressed context items (fixed set) → [ RANKING ] → same items, deliberate order
                                           │
                                           ├── position matters: LLMs don't weigh all
                                           │   context positions equally
                                           └── goal: put decisive evidence where the
                                               model is most likely to actually use it
```

**Mental model:** you've already decided which exhibits go in front of the judge, and you've already trimmed each exhibit to its essential content. Ranking is the decision of *what order to present them in* — a skilled litigator doesn't lead with the weakest evidence and bury the smoking gun in the middle of a three-hour presentation.

---

## 2. The problem it solves

A well-documented property of LLMs is that they do not attend to all positions in a long context equally. This shows up most famously as the **"lost in the middle"** effect: models are measurably better at using information placed near the **beginning** or **end** of the context than information placed in the **middle** — even when the model's raw context window is large enough to technically hold everything.

Without a ranking stage, context items typically end up ordered by whatever is operationally convenient — the order sources happened to be fetched in, alphabetical by source name, or insertion order. This is essentially random with respect to importance, which means:

1. **The single most decisive fact might land in the worst possible position** — buried in the middle of a 15-item context block — purely by accident of fetch order.
2. **Two equally "correct" context sets can produce different answer quality** purely because of ordering, which makes systems harder to debug ("why did it work yesterday and not today, with the same underlying data?" — because Selection returned the same items in a different order).
3. **Grouping matters for coherence, not just position.** Presenting three transaction-history items scattered between two unrelated compliance-policy items forces the model to context-switch repeatedly instead of reasoning over a coherent narrative.

Context Ranking makes ordering a deliberate, tested decision instead of an accident of pipeline plumbing.

---

## 3. A realistic enterprise problem (Helios)

Continuing `CASE-88421`: after Selection, Filtering, and Compression, the pipeline has settled on a final set of 7 context items — the case record, three transaction-history entries, one analyst note, one similar historical case, and one compliance policy excerpt.

The single most decisive fact in this set is **Policy FR-14** (the rule that explicitly defines *this exact pattern* — large transfer + new beneficiary within 24 hours — as requiring a mandatory hold). If that policy excerpt happens to land 4th out of 7 items in an undifferentiated middle position, empirically the model is more likely to under-weight it relative to the case metadata (which naturally sits first) and the most recent transaction (which naturally sits last).

Context Ranking is the stage that deliberately places the highest-value evidence — the case record, the matching policy, and the most similar historical case — at the positions where the model is empirically most attentive, rather than leaving it to chance.

---

## 4. Architecture / flow diagram

```mermaid
flowchart TD
    A[Compressed ContextItems\n(from Pattern 03), fixed set] --> B[Ranking Strategy Selector]

    B -->|default| C["Primacy/Recency (U-curve) Ranking\nhighest-value items at START and END,\nweaker items in the middle"]
    B -->|narrative coherence needed| D[Source-Grouped Ranking\ngroup by source type,\norder groups by aggregate value]
    B -->|debugging / baseline| E[Naive Score-Descending Ranking\n(for comparison only)]

    C --> F[Ordered Context Sequence]
    D --> F
    E --> F

    F --> G[Render Context Block\n+ restate key instruction\nimmediately before final item]
    G --> H[ChatOllama LLM]
    H --> I[Answer to Analyst]

    style B fill:#4A90D9,color:#fff
    style C fill:#4A90D9,color:#fff
```

---

## 5. The complete request-to-response flow

1. **Input**: the fixed, compressed set of `ContextItem`s from Pattern 03 — ranking never adds or removes items, only reorders them.
2. **Compute an importance score per item**: reuses relevance + source priority (same blend introduced in Pattern 01), giving each item a single ordering key.
3. **Choose a ranking strategy**:
   - **U-curve (primacy/recency)** — the default for most queries: sort by importance, then interleave so the highest-scored items alternate between the front and back of the sequence, leaving lower-scored items in the middle.
   - **Source-grouped** — used when narrative coherence matters more than raw position (e.g., a request that explicitly asks to "walk through the transaction timeline" benefits from transactions staying contiguous and chronologically ordered, even if that means a mid-importance item sits in the middle of its group).
   - **Naive score-descending** — kept only as a baseline for A/B comparison and debugging; not recommended as a production default, included here specifically so you can see *why* it underperforms the U-curve on the same data.
4. **Reorder**: items are rearranged into the final sequence per the chosen strategy — content, scores and token counts are untouched.
5. **Render the context block**: items are serialized in their new order; for extra insurance, the key instruction ("cite the specific policy that applies, if any") is restated right before the last context item, exploiting the same recency effect for the *instruction* that ranking exploits for the *evidence*.
6. **LLM call**: `ChatOllama` receives the reordered, instruction-reinforced prompt and generates the grounded answer.

---

## 6. Why this pattern is appropriate here

- **Ranking is cheap insurance against a documented, measurable failure mode.** Reordering a fixed set of already-relevant, already-clean, already-compressed items costs essentially nothing (no extra tokens, no extra LLM calls in the default U-curve strategy) but directly targets a known weakness in how LLMs consume long contexts.
- **It's orthogonal to Selection/Filtering/Compression, and that's the point.** You could build a "good enough" system without a distinct ranking stage by getting lucky with fetch order — but as the number of context sources grows (Helios has six and counting), relying on luck stops being viable. Separating ranking out means you can change ordering strategy without touching selection logic at all.
- **Different tasks want different strategies, which argues for keeping this configurable rather than hardcoded.** A "what happened, in order?" question benefits from chronological/source-grouped ordering; a "why was this flagged?" question benefits from U-curve ordering that surfaces the single most decisive fact at the edges. Baking one fixed order into the prompt-assembly code forecloses that flexibility.
- **The naive baseline is included deliberately.** In production, you should be able to demonstrate (via eval, not just intuition) that your chosen ranking strategy actually outperforms naive ordering on your own tasks — this pattern file gives you the harness to make that comparison rather than asking you to take "lost in the middle" on faith.

---

## 7. Production-quality Python implementation

```python
"""
helios_context_ranking.py

Pattern 04 — Context Ranking
Helios Fraud Investigation Copilot

Takes the fixed, compressed set of ContextItems from Pattern 03 and reorders
them using one of several ranking strategies (U-curve primacy/recency,
source-grouped, or naive score-descending baseline), then renders the final
prompt with the key instruction reinforced near the end of the context block.

Requirements:
    pip install "langchain-core>=0.3.60" "langchain-ollama>=0.3.0"

Assumes a local Ollama server (`ollama serve`) with:
    ollama pull llama3.1
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from enum import Enum
from typing import Protocol

from langchain_core.prompts import ChatPromptTemplate
from langchain_ollama import ChatOllama

logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")
logger = logging.getLogger("helios.context_ranking")


# --------------------------------------------------------------------------- #
# Domain model (same shape as Patterns 01-03, self-contained here)
# --------------------------------------------------------------------------- #

class ContextSourceType(str, Enum):
    CASE_METADATA = "case_metadata"
    TRANSACTION_HISTORY = "transaction_history"
    CUSTOMER_PROFILE = "customer_profile"
    SIMILAR_CASES = "similar_cases"
    COMPLIANCE_POLICY = "compliance_policy"
    ANALYST_NOTES = "analyst_notes"


SOURCE_BASE_PRIORITY: dict[ContextSourceType, float] = {
    ContextSourceType.CASE_METADATA: 1.0,
    ContextSourceType.COMPLIANCE_POLICY: 0.85,  # high here: a matching policy is often decisive
    ContextSourceType.TRANSACTION_HISTORY: 0.75,
    ContextSourceType.SIMILAR_CASES: 0.65,
    ContextSourceType.CUSTOMER_PROFILE: 0.55,
    ContextSourceType.ANALYST_NOTES: 0.50,
}


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

    @property
    def importance_score(self) -> float:
        """Blended score used purely for ORDERING, not for in/out decisions
        (those already happened in Patterns 01-02)."""
        base = SOURCE_BASE_PRIORITY[self.source]
        return (0.6 * self.relevance_score) + (0.4 * base)


# --------------------------------------------------------------------------- #
# Ranking strategies
# --------------------------------------------------------------------------- #

class RankingStrategy(Protocol):
    name: str

    def rank(self, items: list[ContextItem]) -> list[ContextItem]: ...


class NaiveScoreDescendingRanking:
    """Baseline only: sort purely by importance, highest first. Included so
    you can A/B this against the U-curve strategy — NOT recommended as a
    production default, since it buries the *lowest*-value items last, but
    also front-loads too much decisive evidence away from the recency
    position, which empirically underperforms U-curve on long contexts."""

    name = "naive_score_descending"

    def rank(self, items: list[ContextItem]) -> list[ContextItem]:
        return sorted(items, key=lambda i: i.importance_score, reverse=True)


class PrimacyRecencyRanking:
    """
    The production default. Sorts items by importance, then distributes them
    into a 'U-curve': the single most important item goes first, the second
    most important goes last, the third goes second, the fourth goes
    second-to-last, and so on — spiraling inward. This deliberately keeps
    the weakest items in the middle of the sequence, which is exactly where
    LLMs are documented to pay the least attention ("lost in the middle").
    """

    name = "primacy_recency_u_curve"

    def rank(self, items: list[ContextItem]) -> list[ContextItem]:
        ranked_desc = sorted(items, key=lambda i: i.importance_score, reverse=True)
        n = len(ranked_desc)
        result: list[ContextItem | None] = [None] * n

        front, back = 0, n - 1
        for i, item in enumerate(ranked_desc):
            if i % 2 == 0:
                result[front] = item
                front += 1
            else:
                result[back] = item
                back -= 1

        assert all(x is not None for x in result)
        return result  # type: ignore[return-value]


class SourceGroupedRanking:
    """
    Groups items by source type (preserving narrative coherence — e.g. all
    transaction-history entries stay contiguous and chronologically ordered
    within their group) and orders the groups themselves by the group's peak
    importance score. Best for queries that ask for a coherent walkthrough
    (timelines, narratives) rather than a single decisive-fact lookup.
    """

    name = "source_grouped"

    def rank(self, items: list[ContextItem]) -> list[ContextItem]:
        groups: dict[ContextSourceType, list[ContextItem]] = {}
        for item in items:
            groups.setdefault(item.source, []).append(item)

        # Order within each group: transaction history chronologically (oldest first,
        # to read as a timeline); everything else by importance descending.
        for source, group_items in groups.items():
            if source == ContextSourceType.TRANSACTION_HISTORY:
                group_items.sort(key=lambda i: i.timestamp)
            else:
                group_items.sort(key=lambda i: i.importance_score, reverse=True)

        # Order the groups themselves by their peak importance score, descending.
        ordered_sources = sorted(
            groups.keys(),
            key=lambda s: max(i.importance_score for i in groups[s]),
            reverse=True,
        )

        result: list[ContextItem] = []
        for source in ordered_sources:
            result.extend(groups[source])
        return result


# --------------------------------------------------------------------------- #
# Ranking pipeline + prompt rendering
# --------------------------------------------------------------------------- #

KEY_INSTRUCTION = (
    "Reminder: cite the specific compliance policy that applies, if any, and state "
    "explicitly whether this case matches a known fraud pattern."
)


class ContextRankingPipeline:
    def __init__(self, strategy: RankingStrategy | None = None) -> None:
        self.strategy = strategy or PrimacyRecencyRanking()

    def rank(self, items: list[ContextItem]) -> list[ContextItem]:
        ordered = self.strategy.rank(items)
        logger.info(
            "Ranked %d items using strategy=%s | order: %s",
            len(ordered), self.strategy.name, [i.item_id for i in ordered],
        )
        return ordered

    @staticmethod
    def render_context_block(ordered_items: list[ContextItem]) -> str:
        """Renders the ordered items, reinforcing the key instruction right
        before the final item — exploiting the same recency effect for the
        instruction that ranking already exploits for the evidence."""
        lines = [f"[{i.source.value}] {i.content}" for i in ordered_items]
        if len(lines) > 1:
            lines.insert(-1, f"--- {KEY_INSTRUCTION} ---")
        return "\n\n".join(lines)


# --------------------------------------------------------------------------- #
# Example run — compare naive vs U-curve ordering on the same fixed item set
# --------------------------------------------------------------------------- #

def build_example_items() -> list[ContextItem]:
    now = datetime.utcnow()
    return [
        ContextItem(
            source=ContextSourceType.CASE_METADATA,
            content="Case CASE-88421: $14,200 wire to a new beneficiary in a high-risk jurisdiction.",
            timestamp=now - timedelta(hours=2),
            item_id="meta",
            relevance_score=0.92,
        ),
        ContextItem(
            source=ContextSourceType.COMPLIANCE_POLICY,
            content=(
                "Policy FR-14: transfers >5x the 90-day average combined with a beneficiary "
                "added within 24 hours prior require a mandatory 48-hour hold and secondary review."
            ),
            timestamp=now - timedelta(days=60),
            item_id="policy-fr14",
            relevance_score=0.88,
        ),
        ContextItem(
            source=ContextSourceType.TRANSACTION_HISTORY,
            content="18d ago | wire $9,800 | new beneficiary | high-risk jurisdiction",
            timestamp=now - timedelta(days=18),
            item_id="txn-1",
            relevance_score=0.81,
        ),
        ContextItem(
            source=ContextSourceType.TRANSACTION_HISTORY,
            content="2d ago | wire $1,200 | existing verified beneficiary | domestic",
            timestamp=now - timedelta(days=2),
            item_id="txn-2",
            relevance_score=0.40,
        ),
        ContextItem(
            source=ContextSourceType.TRANSACTION_HISTORY,
            content="95d ago | wire $2,000 | existing beneficiary | domestic",
            timestamp=now - timedelta(days=95),
            item_id="txn-3",
            relevance_score=0.25,
        ),
        ContextItem(
            source=ContextSourceType.SIMILAR_CASES,
            content=(
                "CASE-71190 (5mo ago): confirmed account-takeover — new beneficiary + outsized "
                "wire to same high-risk jurisdiction, phone number changed days prior."
            ),
            timestamp=now - timedelta(days=150),
            item_id="similar-71190",
            relevance_score=0.70,
        ),
        ContextItem(
            source=ContextSourceType.ANALYST_NOTES,
            content="Customer confirmed the 18-day-ago transfer was a legitimate business payment with invoice support.",
            timestamp=now - timedelta(days=18),
            item_id="note-1",
            relevance_score=0.55,
        ),
    ]


if __name__ == "__main__":
    items = build_example_items()

    print("=== IMPORTANCE SCORES (unordered) ===")
    for i in sorted(items, key=lambda x: x.importance_score, reverse=True):
        print(f"  {i.item_id:<16} importance={i.importance_score:.3f}")

    strategies: list[RankingStrategy] = [
        NaiveScoreDescendingRanking(),
        SourceGroupedRanking(),
        PrimacyRecencyRanking(),
    ]

    rendered_blocks: dict[str, str] = {}
    for strategy in strategies:
        pipeline = ContextRankingPipeline(strategy=strategy)
        ordered = pipeline.rank(items)
        block = pipeline.render_context_block(ordered)
        rendered_blocks[strategy.name] = block
        print(f"\n=== ORDER under strategy '{strategy.name}' ===")
        print("  " + " -> ".join(i.item_id for i in ordered))

    # Use the production-default U-curve ordering for the actual LLM call
    final_block = rendered_blocks["primacy_recency_u_curve"]

    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", "You are the Helios Fraud Investigation Copilot. Answer using ONLY the context given."),
            ("human", "Analyst question: {query}\n\n--- RANKED CONTEXT ---\n{context}"),
        ]
    )
    llm = ChatOllama(model="llama3.1", temperature=0.1)
    chain = prompt | llm
    response = chain.invoke(
        {
            "query": "Why was this transaction flagged, and does this customer's recent behavior match a known fraud pattern?",
            "context": final_block,
        }
    )

    print("\n=== COPILOT ANSWER (grounded in U-curve-ranked context) ===")
    print(response.content)
```

### Notes on running this yourself

- Run the printed order comparisons for all three strategies on the example data — note how `primacy_recency_u_curve` places `meta` (highest importance) first and `policy-fr14` (second-highest) *last*, while `txn-3` (lowest importance) lands in the dead-center middle position. That's the U-curve working as designed.
- `SourceGroupedRanking` keeps the three transaction-history items contiguous and chronological — useful if you extend this example with a query like *"walk me through this customer's transaction timeline."*
- The `KEY_INSTRUCTION` reinforcement trick (repeating the critical instruction just before the last context item) is a cheap, well-supported technique independent of which ranking strategy you use — it costs a handful of tokens and directly exploits the same recency effect that motivates the U-curve itself.
- In a real Helios deployment, you'd want an **eval harness** that runs the same query against the naive and U-curve orderings across many cases and measures answer accuracy/citation-correctness — this file gives you the ordering logic; building that harness is the natural extension.

---

**Next up:** Pattern 05 — Context Prioritization, where — unlike Ranking, which never drops anything — we handle the case where even the compressed, well-ordered set genuinely doesn't fit the budget, and something has to go.
