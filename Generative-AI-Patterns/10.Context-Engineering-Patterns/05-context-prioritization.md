# Pattern 05 — Context Prioritization

[← Back to index](./README.md) | Previous: [Pattern 04 — Context Ranking](./04-context-ranking.md) | Next: Pattern 06 — Context Window Management (queued)

---

## 1. Introduce the pattern

**Context Prioritization** is the pattern that answers a question none of the previous four ever had to fully answer: *when the context I want to send genuinely does not fit the budget, what — precisely — gets cut, and what gets kept?*

```
Ranked context items → [ PRIORITIZATION ] → budget-fitting subset, value-optimized
   (fixed order, but                            │
    total size may                              ├── tiering: some items are never droppable
    exceed budget)                               ├── value-density optimization (knapsack), not
                                                  │   just greedy truncation
                                                  └── preserves the ranking order from Pattern 04
                                                      for whatever survives
```

This is easy to confuse with Pattern 01's token-budget check, so it's worth being precise about the difference:

- **Selection's budget check (Pattern 01)** is a *coarse, early, recall-oriented* filter — it runs before compression, on original (verbose) sizes, using simple greedy truncation, mainly to avoid fetching/scoring a hopelessly oversized candidate pool.
- **Prioritization (this pattern)** is the *final, precise* gate — it runs after compression and ranking, on actual final token counts, and uses a proper **value-optimization** approach (not just "take the top N until full") because by this point every item has already survived two rounds of "is this worth including" — the remaining decisions are genuinely hard trade-offs, and greedy truncation leaves value on the table.

**Mental model:** Selection was the paralegal doing a first, generous pass on which boxes of documents to even pull from the warehouse. Prioritization is the litigator, an hour before the hearing, being told "you only get 10 minutes — pick the exhibits that maximize your case, don't just grab whatever's on top of the pile."

---

## 2. The problem it solves

Budgets are not always known in advance, and they are not always generous:

1. **Budgets can shrink dynamically.** A production system under load might need to reduce the per-request context budget to protect overall throughput/latency SLAs across many concurrent users — the budget the pipeline planned around at Selection-time may no longer hold by the time the request actually executes.
2. **Greedy truncation is provably suboptimal.** "Keep adding items by score until the budget is full" (what Pattern 01 does, deliberately, because it's cheap and running early) can strand you with a poor combination: e.g., skipping two small, high-value items because a slightly-higher-scored-but-bulky item got added first and ate the remaining budget. A real optimization — maximizing total value subject to a token constraint — is a textbook 0/1 knapsack problem, and knapsack has a well-known exact solution that outperforms greedy whenever item value-to-size ratios don't happen to align with raw scores.
3. **Not everything is equally droppable.** Some context (the case record itself; a compliance policy that directly triggers a required action) must never be silently dropped just because the optimizer found a better-scoring combination elsewhere — dropping it isn't a quality trade-off, it's a correctness or compliance failure. Prioritization needs a tiering concept that sits above pure value optimization.

Without this pattern, teams either over-provision budget "just in case" (expensive, doesn't scale) or accept whatever a simple greedy cut leaves them with (measurably leaves value on the table, and risks dropping non-negotiable items).

---

## 3. A realistic enterprise problem (Helios)

Continuing `CASE-88421`: Patterns 01–04 have produced a compressed, U-curve-ranked set of 7 context items that fits comfortably inside a 3,000-token planning budget. But suppose Helios is under a traffic spike — many analysts are running investigations simultaneously — and the platform's dynamic budget controller cuts the *actual* per-request context budget down to **90 tokens** for this call (an artificially tight number chosen here purely to force real trade-offs in the worked example below; in production this would be a more realistic reduction, e.g. from 3,000 to 1,200).

At 90 tokens, not everything fits. But two things must never be silently dropped regardless of what the optimizer decides:

- The **case record** itself (`CASE-88421-meta`) — an answer with no case record isn't a degraded answer, it's a broken one.
- **Policy FR-14** — the compliance rule that directly determines the required action for this exact pattern. Omitting it doesn't just reduce quality; it risks the copilot failing to flag a mandatory hold requirement.

Everything else — transaction history, the similar historical case, the analyst note — is genuinely valuable but *tradeable*: Prioritization's job is to pick the best possible combination of the remaining items within whatever budget is left after the two mandatory items are reserved.

---

## 4. Architecture / flow diagram

```mermaid
flowchart TD
    A[Ranked ContextItems\n(from Pattern 04), fixed order] --> B[Tier Classifier]

    B -->|score >= mandatory threshold\nOR explicitly pinned| C[MANDATORY tier\nalways included]
    B -->|score >= standard threshold| D[STANDARD tier\ncandidate for optimization]
    B -->|below standard threshold| E[OPTIONAL tier\ncandidate for optimization]

    C --> F[Reserve budget for MANDATORY items]
    F --> G{Remaining budget > 0?}
    G -- No --> H[⚠ Log critical: mandatory items\nalone exceed budget]
    G -- Yes --> I["0/1 Knapsack Optimizer\n(maximize total value,\nsubject to remaining budget)"]

    D --> I
    E --> I

    I --> J[Optimal STANDARD+OPTIONAL subset]
    C --> K[Merge: MANDATORY + optimized subset]
    J --> K
    K --> L[Re-apply Pattern 04's ranking order\nto whatever survived]
    L --> M[Final Context Block]
    M --> N[ChatOllama LLM]
    N --> O[Answer to Analyst]

    style B fill:#4A90D9,color:#fff
    style I fill:#4A90D9,color:#fff
    style H fill:#C0392B,color:#fff
```

---

## 5. The complete request-to-response flow

1. **Input**: the ranked `ContextItem` sequence from Pattern 04, plus the actual (possibly reduced) token budget for this request.
2. **Tier classification**: every item is assigned to `MANDATORY`, `STANDARD`, or `OPTIONAL` — based on a score threshold, with an explicit pin list (`{"CASE-88421-meta", "policy-fr14"}`) that always forces `MANDATORY` regardless of score, matching the "structural relevance" escape hatch established in Pattern 01.
3. **Reserve mandatory budget**: the token cost of all `MANDATORY` items is summed and subtracted from the total budget. If mandatory items alone exceed the budget, this is logged as a **critical** condition (in production: an alert, not a silent failure) — the system proceeds with mandatory items only and skips optimization entirely.
4. **Knapsack optimization on the remainder**: the `STANDARD` and `OPTIONAL` items are fed into an exact 0/1 knapsack solver (dynamic programming) that finds the subset maximizing total `importance_score` subject to the remaining token budget — this is provably at least as good as, and often strictly better than, greedy truncation.
5. **Merge and re-sort**: the mandatory items and the knapsack-selected items are combined, then put back into the exact order Pattern 04's ranking strategy produced (dropped items simply disappear from the sequence; survivors keep their relative U-curve position).
6. **Audit log**: every dropped item is logged with its score, size, and tier, giving a compliance-reviewable answer to "what did the analyst *not* see, and why."
7. **Render & call the LLM**: the final, budget-fitting context block is assembled (using the same rendering approach as Pattern 04, including the reinforced key instruction) and sent to `ChatOllama`.

---

## 6. Why this pattern is appropriate here

- **Knapsack, not greedy, because by this stage the items are genuinely comparable.** Greedy truncation (Pattern 01's approach) is the right tool early, when you're filtering a huge, noisy candidate pool and "good enough, cheap, fast" beats "optimal, slower." By Pattern 05, the candidate pool is small (single digits to low tens of items) and every item has already proven its worth — the extra cost of an exact DP solve is negligible, and the value recovered is real.
- **Tiering is a correctness mechanism, not a scoring nuance.** Mixing "must never drop this" into the same continuous score used for optimization is fragile — a slightly miscalibrated relevance score could theoretically let the optimizer trade away a compliance-critical item for two moderately-useful ones. A hard tier boundary makes that failure mode structurally impossible rather than merely unlikely.
- **It preserves everything Ranking already decided.** Prioritization doesn't re-rank survivors — it filters the ranked sequence and keeps whatever order Pattern 04 already computed. This keeps the patterns composable: change your ranking strategy without touching prioritization logic, and vice versa.
- **The critical-alert path matters as much as the happy path.** A production system that silently proceeds with an incomplete mandatory set (e.g., no room even for the case record) is far more dangerous than one that fails loudly — Prioritization is exactly the layer that should own detecting and surfacing that condition.

---

## 7. Production-quality Python implementation

```python
"""
helios_context_prioritization.py

Pattern 05 — Context Prioritization
Helios Fraud Investigation Copilot

Takes the ranked ContextItem sequence from Pattern 04 and, when the actual
token budget is tighter than what fits, applies tiered + knapsack-optimized
prioritization: MANDATORY items are always kept; STANDARD/OPTIONAL items are
selected via exact 0/1 knapsack to maximize total value under the remaining
budget; survivors keep Pattern 04's ranking order.

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

from langchain_core.prompts import ChatPromptTemplate
from langchain_ollama import ChatOllama

logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")
logger = logging.getLogger("helios.context_prioritization")


# --------------------------------------------------------------------------- #
# Domain model (same shape as Patterns 01-04, self-contained here)
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
    ContextSourceType.COMPLIANCE_POLICY: 0.85,
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
    rank_position: int = field(default=0)  # position assigned by Pattern 04's ranking

    def __post_init__(self) -> None:
        if self.token_count == 0:
            self.token_count = estimate_tokens(self.content)

    @property
    def importance_score(self) -> float:
        base = SOURCE_BASE_PRIORITY[self.source]
        return (0.6 * self.relevance_score) + (0.4 * base)


# --------------------------------------------------------------------------- #
# Tiering
# --------------------------------------------------------------------------- #

class ContextTier(str, Enum):
    MANDATORY = "mandatory"
    STANDARD = "standard"
    OPTIONAL = "optional"


MANDATORY_SCORE_THRESHOLD = 0.85
STANDARD_SCORE_THRESHOLD = 0.45


def classify_tier(item: ContextItem, pinned_mandatory_ids: set[str]) -> ContextTier:
    if item.item_id in pinned_mandatory_ids:
        return ContextTier.MANDATORY
    if item.importance_score >= MANDATORY_SCORE_THRESHOLD:
        return ContextTier.MANDATORY
    if item.importance_score >= STANDARD_SCORE_THRESHOLD:
        return ContextTier.STANDARD
    return ContextTier.OPTIONAL


# --------------------------------------------------------------------------- #
# Exact 0/1 knapsack — maximize total importance_score subject to a token budget
# --------------------------------------------------------------------------- #

def knapsack_select(items: list[ContextItem], budget_tokens: int) -> list[ContextItem]:
    """
    Standard 0/1 knapsack via dynamic programming. Value = importance_score
    (scaled to an integer for DP table indexing), weight = token_count.
    Returns the subset of `items` that maximizes total value without
    exceeding budget_tokens. Exact — strictly at least as good as greedy.
    """
    if not items or budget_tokens <= 0:
        return []

    n = len(items)
    # Scale scores to integers (x1000) for a clean DP table; values are only
    # used for comparison, so scaling doesn't affect the optimal subset.
    values = [int(round(item.importance_score * 1000)) for item in items]
    weights = [item.token_count for item in items]

    # dp[i][w] = best achievable value using first i items with capacity w
    dp = [[0] * (budget_tokens + 1) for _ in range(n + 1)]

    for i in range(1, n + 1):
        item_weight = weights[i - 1]
        item_value = values[i - 1]
        for w in range(budget_tokens + 1):
            dp[i][w] = dp[i - 1][w]  # exclude item i-1
            if item_weight <= w:
                included = dp[i - 1][w - item_weight] + item_value
                if included > dp[i][w]:
                    dp[i][w] = included

    # Reconstruct the chosen subset by walking back through the DP table.
    selected_indices: list[int] = []
    w = budget_tokens
    for i in range(n, 0, -1):
        if dp[i][w] != dp[i - 1][w]:
            selected_indices.append(i - 1)
            w -= weights[i - 1]

    selected_indices.reverse()
    return [items[i] for i in selected_indices]


def greedy_select_for_comparison(items: list[ContextItem], budget_tokens: int) -> list[ContextItem]:
    """Greedy-by-value-density baseline, included ONLY to demonstrate that
    knapsack recovers at least as much value on the same input (see the
    printed comparison in the __main__ block below)."""
    ranked = sorted(items, key=lambda i: i.importance_score / i.token_count, reverse=True)
    selected, used = [], 0
    for item in ranked:
        if used + item.token_count <= budget_tokens:
            selected.append(item)
            used += item.token_count
    return selected


# --------------------------------------------------------------------------- #
# Prioritization pipeline
# --------------------------------------------------------------------------- #

@dataclass
class PrioritizationResult:
    kept: list[ContextItem]
    dropped: list[tuple[ContextItem, ContextTier, str]]  # (item, tier, reason)
    mandatory_exceeds_budget: bool


class ContextPrioritizationPipeline:
    def __init__(self, pinned_mandatory_ids: set[str] | None = None) -> None:
        self.pinned_mandatory_ids = pinned_mandatory_ids or set()

    def prioritize(self, ranked_items: list[ContextItem], budget_tokens: int) -> PrioritizationResult:
        # Preserve Pattern 04's ranking order via an explicit position index,
        # so we can restore it after filtering regardless of tier/knapsack order.
        for idx, item in enumerate(ranked_items):
            item.rank_position = idx

        tiers = {item.item_id: classify_tier(item, self.pinned_mandatory_ids) for item in ranked_items}
        mandatory = [i for i in ranked_items if tiers[i.item_id] == ContextTier.MANDATORY]
        standard = [i for i in ranked_items if tiers[i.item_id] == ContextTier.STANDARD]
        optional = [i for i in ranked_items if tiers[i.item_id] == ContextTier.OPTIONAL]

        mandatory_tokens = sum(i.token_count for i in mandatory)
        dropped: list[tuple[ContextItem, ContextTier, str]] = []

        if mandatory_tokens > budget_tokens:
            logger.critical(
                "MANDATORY items alone (%d tokens) exceed budget (%d tokens). "
                "Proceeding with mandatory items only; escalate budget or reduce "
                "the pinned-mandatory set.",
                mandatory_tokens, budget_tokens,
            )
            for i in standard + optional:
                dropped.append((i, tiers[i.item_id], "budget exhausted by mandatory tier"))
            kept = sorted(mandatory, key=lambda i: i.rank_position)
            return PrioritizationResult(kept=kept, dropped=dropped, mandatory_exceeds_budget=True)

        remaining_budget = budget_tokens - mandatory_tokens
        optimizable = standard + optional

        optimized_subset = knapsack_select(optimizable, remaining_budget)
        optimized_ids = {i.item_id for i in optimized_subset}

        for i in optimizable:
            if i.item_id not in optimized_ids:
                dropped.append((i, tiers[i.item_id], "excluded by knapsack optimizer under remaining budget"))

        kept_unordered = mandatory + optimized_subset
        kept = sorted(kept_unordered, key=lambda i: i.rank_position)

        logger.info(
            "Prioritization complete: kept %d/%d items | mandatory=%d tokens, "
            "knapsack used %d/%d remaining tokens",
            len(kept), len(ranked_items), mandatory_tokens,
            sum(i.token_count for i in optimized_subset), remaining_budget,
        )
        return PrioritizationResult(kept=kept, dropped=dropped, mandatory_exceeds_budget=False)

    @staticmethod
    def render_context_block(kept_items: list[ContextItem]) -> str:
        lines = [f"[{i.source.value}] {i.content}" for i in kept_items]
        if len(lines) > 1:
            lines.insert(-1, "--- Reminder: cite the specific compliance policy that applies, if any. ---")
        return "\n\n".join(lines)


# --------------------------------------------------------------------------- #
# Example run — same 7-item Helios set as Pattern 04, under a tight budget
# --------------------------------------------------------------------------- #

def build_example_items() -> list[ContextItem]:
    now = datetime.utcnow()
    return [
        ContextItem(ContextSourceType.CASE_METADATA,
                    "Case CASE-88421: $14,200 wire to a new beneficiary in a high-risk jurisdiction.",
                    now - timedelta(hours=2), "CASE-88421-meta", relevance_score=0.92),
        ContextItem(ContextSourceType.COMPLIANCE_POLICY,
                    "Policy FR-14: transfers >5x 90-day average + beneficiary added <24h prior require a 48h hold.",
                    now - timedelta(days=60), "policy-fr14", relevance_score=0.88),
        ContextItem(ContextSourceType.TRANSACTION_HISTORY,
                    "18d ago | wire $9,800 | new beneficiary | high-risk jurisdiction",
                    now - timedelta(days=18), "txn-1", relevance_score=0.81),
        ContextItem(ContextSourceType.TRANSACTION_HISTORY,
                    "2d ago | wire $1,200 | existing verified beneficiary | domestic",
                    now - timedelta(days=2), "txn-2", relevance_score=0.40),
        ContextItem(ContextSourceType.TRANSACTION_HISTORY,
                    "95d ago | wire $2,000 | existing beneficiary | domestic",
                    now - timedelta(days=95), "txn-3", relevance_score=0.25),
        ContextItem(ContextSourceType.SIMILAR_CASES,
                    "CASE-71190 (5mo ago): confirmed account-takeover — new beneficiary + outsized wire, phone changed prior.",
                    now - timedelta(days=150), "similar-71190", relevance_score=0.70),
        ContextItem(ContextSourceType.ANALYST_NOTES,
                    "Customer confirmed the 18-day-ago transfer was a legitimate business payment with invoice support.",
                    now - timedelta(days=18), "note-1", relevance_score=0.55),
    ]


if __name__ == "__main__":
    items = build_example_items()
    total_tokens = sum(i.token_count for i in items)
    print(f"Total tokens across all 7 ranked items: {total_tokens}\n")

    TIGHT_BUDGET = 90  # deliberately tight, to force real trade-offs in this worked example

    pipeline = ContextPrioritizationPipeline(pinned_mandatory_ids={"CASE-88421-meta", "policy-fr14"})
    result = pipeline.prioritize(items, budget_tokens=TIGHT_BUDGET)

    print("=== KEPT (in preserved rank order) ===")
    for i in result.kept:
        print(f"  {i.item_id:<16} tier-eligible tokens={i.token_count:<4} importance={i.importance_score:.3f}")

    print("\n=== DROPPED ===")
    for item, tier, reason in result.dropped:
        print(f"  {item.item_id:<16} tier={tier.value:<10} reason={reason}")

    # Demonstrate knapsack recovering at least as much value as greedy on the
    # same STANDARD+OPTIONAL pool (the interesting comparison, not the mandatory tier)
    mandatory_ids = {"CASE-88421-meta", "policy-fr14"}
    optimizable_pool = [i for i in items if i.item_id not in mandatory_ids]
    remaining_budget = TIGHT_BUDGET - sum(i.token_count for i in items if i.item_id in mandatory_ids)

    knapsack_result = knapsack_select(optimizable_pool, remaining_budget)
    greedy_result = greedy_select_for_comparison(optimizable_pool, remaining_budget)

    print(f"\n=== Knapsack vs. Greedy on remaining budget ({remaining_budget} tokens) ===")
    print(f"  Knapsack total value: {sum(i.importance_score for i in knapsack_result):.3f} "
          f"| items: {[i.item_id for i in knapsack_result]}")
    print(f"  Greedy total value:   {sum(i.importance_score for i in greedy_result):.3f} "
          f"| items: {[i.item_id for i in greedy_result]}")

    # Final LLM call using the budget-fitting, order-preserved context
    context_block = pipeline.render_context_block(result.kept)
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", "You are the Helios Fraud Investigation Copilot. Answer using ONLY the context given. "
                       "If context was omitted due to budget limits, do not speculate about what it might contain."),
            ("human", "Analyst question: {query}\n\n--- BUDGET-FITTING CONTEXT ---\n{context}"),
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

    print("\n=== COPILOT ANSWER (grounded in prioritized, budget-fitting context) ===")
    print(response.content)
```

### Notes on running this yourself

- The `mandatory_exceeds_budget` critical path is not a decorative edge case — in production this should page someone or trigger a budget-escalation workflow, not just log and move on with a broken answer.
- The knapsack-vs-greedy comparison at the bottom of `__main__` is the empirical proof of section 6's claim: run it and check whether knapsack's total value ever exceeds greedy's on your own data — with tight enough budgets and varied enough token sizes, it reliably will.
- `rank_position` is the mechanism that keeps this pattern honestly decoupled from Pattern 04: Prioritization never re-decides order, it only decides in/out, then restores whatever order Ranking already computed.
- The DP table in `knapsack_select` is `O(n × budget_tokens)` — entirely fine at the scale of a handful to a few dozen context items and a budget in the thousands of tokens; if you were prioritizing across hundreds of items with a huge budget, you'd want a scaled/approximate knapsack (e.g. bucket token counts into coarser units) rather than the exact DP shown here.

---

**Next up:** Pattern 06 — Context Window Management, where we zoom out from a single request to a long-running, multi-turn investigation session, and manage how context accumulates, ages out, and gets re-selected turn over turn.
