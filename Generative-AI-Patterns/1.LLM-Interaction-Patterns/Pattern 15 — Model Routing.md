## Pattern 15 — Model Routing

**Depends on:** Pattern 14 (Model Selection)

### Problem
Pattern 14 assigns a model tier per *task type*, decided ahead of time in config. But real traffic within a single task type isn't uniform — some "simple classification" requests really are trivial, while others are genuinely ambiguous edge cases that would benefit from a stronger model. A static per-category assignment either over-spends on the easy majority or under-serves the hard minority. Model Routing solves this by making the model choice a *per-request runtime decision*, based on signals in the actual request.

### Motivation
This is the same "static config vs dynamic decision" trade-off that shows up throughout GenAI system design (compare to Prompt Routing, later). The payoff for going dynamic is real — cost savings compound at scale when you can push even 20-30% more traffic to a cheaper tier without sacrificing quality on the requests that need more — but it adds real complexity (a router than can be wrong) that isn't worth it below a certain traffic volume.

### Core Idea
Before calling a model, run a lightweight classification step — either rule-based (input length, presence of certain keywords, structured signals like account tier) or a cheap-model-based judgment call — to estimate the request's actual complexity, then route to the appropriate tier. This is **model cascading** when done as "try cheap first, escalate on low confidence," and **capability-based routing** when done as "classify complexity up front, then pick a tier directly." Both are covered below.

### Architecture

```text
                    ┌───────────────────┐
                    │   Incoming Request     │
                    └──────────┬──────────┘
                                ▼
              ┌───────────────────────────┐
              │   Routing Signal Extraction  │  ← length, keywords, account
              │   (cheap/rule-based)           │     tier, explicit user flag
              └──────────┬───────────────┘
                          ▼
              ┌───────────────────────────┐
              │   Routing Decision             │
              └──────────┬───────────────┘
        ┌─────────────────┼─────────────────┐
        ▼                  ▼                  ▼
 ┌────────────┐    ┌────────────┐    ┌─────────────┐
 │  Haiku 4.5    │    │  Sonnet 5     │    │  Opus 4.8      │
 └──────┬─────┘    └──────┬─────┘    └───────┬─────┘
        │                  │                    │
        └──────────────────┼────────────────────┘
                            ▼
              ┌───────────────────────────┐
              │  Confidence check (cascade  │  ← optional: if the cheap
              │  variant): escalate if low?   │     tier is uncertain, retry
              └───────────────────────────┘     on a stronger model
```

### Internal Flow (Capability-Based Routing)
1. Extract cheap, fast signals from the request — input length, presence of multi-part questions, domain keywords, explicit metadata (e.g., "enterprise" account tier gets priority routing).
2. Feed those signals into a routing decision — either simple rules (`if len(text) > 2000: route to Sonnet`) or a small classifier call.
3. Dispatch to the selected model tier.
4. Log the routing decision alongside the eventual output quality (via evaluation, later) so the routing rules themselves can be tuned over time — a router that's never revisited tends to drift out of alignment with real traffic patterns.

### Internal Flow (Model Cascading — try cheap, escalate on low confidence)
1. Always try the cheapest capable tier first.
2. Ask the model (or a lightweight follow-up check) to self-report confidence, or apply a validation check to its output.
3. If confidence is low or validation fails, escalate the *same* request to the next tier up and retry.
4. Repeat until either a tier succeeds with sufficient confidence or you hit the top tier.

### Simple Implementation

```python
def route_by_rules(text: str, account_tier: str) -> str:
    if account_tier == "enterprise":
        return "claude-opus-4-8"          # premium accounts get the strongest tier
    if len(text) > 1500 or "?" in text * 3:  # crude proxy for multi-part complexity
        return "claude-sonnet-5"
    return "claude-haiku-4-5-20251001"

print(route_by_rules("Reset my password please", "free"))    # Haiku
print(route_by_rules("Long, multi-part enterprise inquiry...", "enterprise"))  # Opus
```

### Production Implementation

```python
import logging
from dataclasses import dataclass

import anthropic

logger = logging.getLogger("genai.model_routing")


@dataclass
class RoutingSignals:
    text_length: int
    account_tier: str
    is_multi_part: bool


class RuleBasedRouter:
    """Fast, deterministic, cheap — no extra model call needed to route.
    Good default starting point; escalate to a classifier-based router
    only once you have evidence rules aren't precise enough."""

    def route(self, signals: RoutingSignals) -> str:
        if signals.account_tier == "enterprise":
            return "claude-opus-4-8"
        if signals.text_length > 1500 or signals.is_multi_part:
            return "claude-sonnet-5"
        return "claude-haiku-4-5-20251001"


class CascadingRouter:
    """Always tries the cheapest tier first, escalating only on low
    confidence — spends the extra cost of a stronger model only on the
    subset of requests that actually need it, discovered at runtime
    rather than predicted in advance."""

    TIER_ORDER = ["claude-haiku-4-5-20251001", "claude-sonnet-5", "claude-opus-4-8"]

    def __init__(self, confidence_threshold: float = 0.7):
        self.client = anthropic.AsyncAnthropic()
        self.confidence_threshold = confidence_threshold

    async def _try_tier(self, model: str, prompt: str) -> tuple[str, float]:
        response = await self.client.messages.create(
            model=model,
            max_tokens=800,
            messages=[{"role": "user", "content":
                f"{prompt}\n\nAfter your answer, on a new line write "
                f"'CONFIDENCE: <0.0-1.0>' rating how confident you are."}],
        )
        text = response.content[0].text
        answer, _, confidence_line = text.rpartition("CONFIDENCE:")
        try:
            confidence = float(confidence_line.strip())
        except ValueError:
            confidence = 0.5  # couldn't parse — treat as uncertain, may trigger escalation
        return answer.strip(), confidence

    async def run(self, prompt: str) -> dict:
        for tier_index, model in enumerate(self.TIER_ORDER):
            answer, confidence = await self._try_tier(model, prompt)
            logger.info("cascade_attempt model=%s confidence=%.2f", model, confidence)

            if confidence >= self.confidence_threshold or tier_index == len(self.TIER_ORDER) - 1:
                return {"answer": answer, "model_used": model, "confidence": confidence,
                         "escalations": tier_index}

        raise RuntimeError("unreachable")  # loop always returns by the last tier
```

### Real-World Use Case
A content-moderation pipeline routes the vast majority of posts (clearly benign or clearly violating) through Haiku 4.5 for a fast, cheap first pass. Posts where that pass reports low confidence — genuinely ambiguous edge cases near a policy boundary — automatically escalate to Sonnet 5 for a more careful second opinion, and the rare remaining ambiguous cases escalate further to human review. This keeps the overwhelming majority of moderation cheap and fast while still giving hard cases the scrutiny they need.

### Advantages
- Captures cost savings that static per-category tiering (Pattern 14) leaves on the table, by adapting to actual per-request difficulty rather than an assumed category average.
- Cascading in particular provides a graceful, self-correcting escalation path rather than a single fixed guess.
- Routing decisions and their outcomes are a rich, minable dataset for continuously improving both the router and the underlying task performance.

### Disadvantages / Failure Modes
- A poorly calibrated router (rules that don't actually predict difficulty, or a model that's overconfident) can misroute — silently degrading quality on genuinely hard requests routed to a weak tier.
- Cascading adds latency for the subset of requests that end up escalating multiple tiers — worth measuring the real-world escalation rate, since a router that escalates 80% of the time isn't actually saving much over just using the strong tier directly.
- Self-reported confidence from a model is not a rigorously calibrated probability — treat it as a useful heuristic signal, not ground truth (a known weak point; a proper eval-based confidence signal is more reliable if the stakes justify the extra engineering).
- Added system complexity (a router that can itself fail or misbehave) is a new component to monitor, test, and debug.

### When NOT to Use It
- Low or moderate traffic volume where the static, per-category tiering from Pattern 14 already captures most of the achievable savings — the added complexity of dynamic routing isn't justified.
- Tasks where getting it wrong even occasionally is unacceptable (e.g., certain compliance or safety-critical checks) — route those deterministically to your strongest tier rather than trusting a router's judgment call.
- Early-stage products still validating product-market fit — instrument and measure with a single default model first; build a router once you have real traffic data showing where it would help.

### Comparison: Static Tiering vs Rule-Based Routing vs Cascading
| Pattern | Decision Basis | Complexity | Cost Efficiency | Latency Risk | Best Use Case |
|---|---|---|---|---|---|
| Static Model Selection (Pattern 14) | Task type, decided ahead of time | Low | Moderate | None (single call) | Stable, predictable task categories |
| Rule-Based Routing | Per-request heuristics (length, metadata) | Medium | Higher | None (single call) | High volume, decent heuristics available |
| Cascading | Runtime confidence, tier-by-tier | High | Highest (for skewed-easy traffic) | Higher (multi-attempt on hard cases) | Traffic that's mostly easy with a genuine hard tail |

### Exercise
Extend `CascadingRouter` to track and log the overall escalation rate across a batch of test requests (what fraction of requests needed tier 2+, what fraction needed tier 3). Then think through: if the escalation rate to the top tier turns out to be 60%, what does that tell you about whether cascading is actually saving money versus just routing everything to Sonnet 5 directly and skipping the cascade? This exact cost/complexity trade-off analysis is what you'll be asked to reason through explicitly in the Cost Optimization Patterns section later.

---
