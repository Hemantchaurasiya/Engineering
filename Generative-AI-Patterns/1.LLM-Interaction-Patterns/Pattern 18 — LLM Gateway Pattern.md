## Pattern 18 — LLM Gateway Pattern

**Depends on:** Pattern 14 (Model Selection), Pattern 15 (Model Routing), Pattern 16 (Fallback Models), Pattern 17 (Multi-Model Architecture)

### Problem
Patterns 14–17 are all real, necessary decisions — but if every service in your organization implements model selection, routing, and fallback logic independently, you get duplicated (and inevitably inconsistent) logic scattered across codebases, no central place to enforce rate limits or track cost, and no single point to swap providers or add a new model without touching every calling service. The LLM Gateway Pattern is the architectural answer: centralize all of it behind one internal service.

### Motivation
This is the same reasoning that led to API gateways in traditional microservice architecture — one place for cross-cutting concerns (auth, rate limiting, logging, routing) rather than reimplementing them per service. An LLM Gateway does this specifically for LLM traffic: every internal service calls *the gateway*, never a provider's API directly, and the gateway owns model selection, routing, fallback, cost tracking, and rate limiting as shared infrastructure.

### Core Idea
A single internal service sits between all your application code and every LLM provider. Callers send a provider-agnostic request (task type, prompt, constraints); the gateway resolves which actual model/provider to use (Patterns 14–15), handles failures with fallback (Pattern 16), and can mix multiple models per request (Pattern 17) — all invisibly to the caller.

### Architecture

```text
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  Service A      │  │  Service B      │  │  Service C      │
│ (support bot)    │  │ (doc summarizer) │  │ (code review)     │
└───────┬──────┘  └───────┬──────┘  └───────┬──────┘
        └─────────────────┼─────────────────┘
                          ▼
              ┌───────────────────────┐
              │      LLM Gateway           │
              │  ┌─────────────────┐  │
              │  │  Auth / Rate Limit   │  │
              │  ├─────────────────┤  │
              │  │  Model Selection      │  │  (Pattern 14)
              │  ├─────────────────┤  │
              │  │  Routing               │  │  (Pattern 15)
              │  ├─────────────────┤  │
              │  │  Fallback Chain        │  │  (Pattern 16)
              │  ├─────────────────┤  │
              │  │  Cost / Usage Tracking  │  │
              │  └─────────────────┘  │
              └──────────┬────────┘
        ┌─────────────────┼─────────────────┐
        ▼                  ▼                  ▼
┌────────────┐    ┌────────────┐    ┌────────────┐
│  Anthropic API │    │  Provider B API │    │  Local Model     │
└────────────┘    └────────────┘    └────────────┘
```

### Internal Flow
1. A calling service sends a request to the gateway with task metadata (task type, priority, max cost/latency budget) rather than a specific model name — decoupling callers from model-choice details entirely.
2. The gateway authenticates the caller and checks it against its rate limit / budget allocation.
3. The gateway applies Model Selection/Routing logic to pick a model.
4. It calls the provider, applying the Fallback Chain if the primary attempt fails.
5. It logs cost, latency, and usage centrally — one place to answer "how much are we spending on LLM calls, broken down by team/service" (Cost Tracking, later section, builds directly on this).
6. It returns a normalized response to the caller, regardless of which underlying provider/model actually served it.

### Simple Implementation

```python
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI()

class GatewayRequest(BaseModel):
    task_type: str          # e.g. "classification", "drafting", "reasoning"
    prompt: str
    caller_service: str

class GatewayResponse(BaseModel):
    text: str
    model_used: str

TASK_TO_MODEL = {
    "classification": "claude-haiku-4-5-20251001",
    "drafting": "claude-sonnet-5",
    "reasoning": "claude-opus-4-8",
}

@app.post("/v1/generate", response_model=GatewayResponse)
async def generate(req: GatewayRequest):
    model = TASK_TO_MODEL.get(req.task_type)
    if model is None:
        raise HTTPException(400, f"Unknown task_type: {req.task_type}")
    # In production: call FallbackChain.invoke(...) here, not the raw SDK directly
    text = f"[stub] response for '{req.prompt[:50]}...' using {model}"
    return GatewayResponse(text=text, model_used=model)
```

### Production Implementation

```python
import logging
import time
from dataclasses import dataclass

from fastapi import FastAPI, HTTPException, Header
from pydantic import BaseModel

logger = logging.getLogger("genai.gateway")

app = FastAPI(title="LLM Gateway")


@dataclass
class RateLimitBucket:
    calls_this_minute: int = 0
    window_start: float = 0.0


class RateLimiter:
    """Per-caller rate limiting — a core gateway responsibility that has
    no natural home in individual services. Centralizing it here means
    one team's runaway service can't silently exhaust the org's shared
    provider quota."""

    def __init__(self, limit_per_minute: int = 100):
        self.limit = limit_per_minute
        self._buckets: dict[str, RateLimitBucket] = {}

    def check(self, caller: str) -> bool:
        now = time.time()
        bucket = self._buckets.setdefault(caller, RateLimitBucket(window_start=now))
        if now - bucket.window_start > 60:
            bucket.calls_this_minute = 0
            bucket.window_start = now
        bucket.calls_this_minute += 1
        return bucket.calls_this_minute <= self.limit


class UsageTracker:
    """Central place cost/usage lands — every caller's spend is visible
    from one component, instead of scattered log lines across N services."""

    def __init__(self):
        self._records: list[dict] = []

    def record(self, caller: str, model: str, input_tokens: int, output_tokens: int) -> None:
        self._records.append({
            "caller": caller, "model": model,
            "input_tokens": input_tokens, "output_tokens": output_tokens,
            "timestamp": time.time(),
        })
        logger.info("usage_recorded caller=%s model=%s in=%d out=%d",
                    caller, model, input_tokens, output_tokens)

    def total_for_caller(self, caller: str) -> dict:
        records = [r for r in self._records if r["caller"] == caller]
        return {
            "call_count": len(records),
            "total_input_tokens": sum(r["input_tokens"] for r in records),
            "total_output_tokens": sum(r["output_tokens"] for r in records),
        }


rate_limiter = RateLimiter(limit_per_minute=100)
usage_tracker = UsageTracker()

# fallback_chains: dict[str, FallbackChain] built per task type (Pattern 16),
# omitted here for brevity — this is where Patterns 14-17 plug in together.


class GatewayRequest(BaseModel):
    task_type: str
    prompt: str


class GatewayResponse(BaseModel):
    text: str
    model_used: str
    fallback_used: bool


@app.post("/v1/generate", response_model=GatewayResponse)
async def generate(req: GatewayRequest, x_caller_service: str = Header(...)):
    if not rate_limiter.check(x_caller_service):
        raise HTTPException(429, f"Rate limit exceeded for caller '{x_caller_service}'")

    # 1. Model Selection / Routing (Patterns 14-15) resolves a fallback chain
    # 2. FallbackChain.invoke(...) (Pattern 16) executes it with automatic failover
    # (wiring omitted here — see those patterns' production implementations)
    model_used = "claude-sonnet-5"
    fallback_used = False
    text = f"[response for task_type={req.task_type}]"

    usage_tracker.record(x_caller_service, model_used, input_tokens=120, output_tokens=340)

    return GatewayResponse(text=text, model_used=model_used, fallback_used=fallback_used)


@app.get("/v1/usage/{caller_service}")
async def get_usage(caller_service: str):
    return usage_tracker.total_for_caller(caller_service)
```

### Real-World Use Case
A mid-sized company with a support bot, a document-summarization feature, and an internal code-review tool — three separate teams — routes all three through one internal LLM Gateway rather than each team independently integrating with a provider's SDK. When the company later decides to add a second provider for redundancy, or needs to enforce a company-wide monthly spend cap, that's a single change in the gateway, not three separate migrations across three codebases with three different implementations of "call an LLM."

### Advantages
- Single source of truth for cost, usage, and rate limiting across the whole organization — no more "we don't actually know our total LLM spend" problems.
- Decouples calling services from provider/model specifics entirely — swapping providers or adding a new model tier is a gateway-only change.
- Centralizes security concerns (auth, PII handling, injection defenses) in one hardened component instead of trusting every team to implement them correctly and consistently.

### Disadvantages / Failure Modes
- The gateway itself becomes a single point of failure — it needs its own reliability engineering (redundancy, health checks, its own fallback story) or it becomes a bottleneck for the entire organization's LLM usage.
- Adds a network hop and operational overhead versus calling a provider directly — real cost for small organizations where this centralization isn't yet paying for itself.
- Requires genuine cross-team buy-in and governance — a gateway only works if teams actually route through it consistently, which is an organizational challenge as much as a technical one.

### When NOT to Use It
- A single team, single application, single model — there's nothing to centralize yet; a direct SDK call (Pattern 1) with the fallback/routing logic inline is simpler and entirely sufficient.
- Early-stage products still iterating quickly — the governance and stability a gateway provides matters more once you have multiple services and teams than during early single-service experimentation.

### Comparison: Direct Calls vs LLM Gateway
| Aspect | Direct SDK calls per service | LLM Gateway |
|---|---|---|
| Setup cost | None | Real — a new service to build/operate |
| Cost visibility | Scattered, per-service | Centralized, org-wide |
| Consistency of routing/fallback logic | Duplicated, drifts across services | Single implementation, enforced everywhere |
| Best for | Single-team, single-app products | Multi-team organizations with several LLM-powered products |

### Exercise
Extend the `UsageTracker` with a `check_budget(caller: str, monthly_limit_usd: float) -> bool` method that estimates spend using a rough per-model price table and blocks further calls once a caller exceeds its allocated monthly budget. This is a direct, practical preview of the **Budget Limiting** pattern in the Guardrails & Security section, and of the full **Cost Tracking** treatment in the Cost Optimization Patterns section later in the course.

---
