## Pattern 16 — Fallback Models

**Depends on:** Pattern 14 (Model Selection), Pattern 15 (Model Routing)

### Problem
Model Routing (Pattern 15) picks a model based on *task difficulty*. Fallback Models solves a different problem: what happens when your chosen model is unavailable — an outage, a rate limit, an elevated error rate, a regional incident? Without a fallback strategy, your entire application goes down whenever a single provider or model endpoint has a bad moment, even though the actual task the user needs done hasn't gotten any harder.

### Motivation
LLM providers have outages, deploy incidents, and rate limits like any other external dependency — this is standard reliability-engineering territory (Circuit Breaker, Retry, and related Reliability Patterns apply directly here, covered more generally later). Fallback Models is the GenAI-specific instance of "have a backup when your primary dependency fails," and it's worth building deliberately rather than discovering the need for it during a live incident.

### Core Idea
Define an ordered list of fallback options — which can be a different model *tier* from the same provider, a different provider entirely, or (for the most critical paths) a cached/canned response — and automatically fail over to the next option when the primary fails, rather than surfacing the raw error to the user.

```text
Primary (Sonnet 5) fails
   → Fallback 1 (Opus 4.8, same provider, different model)
      → Fallback 2 (different provider entirely)
         → Fallback 3 (cached/canned safe response)
```

### Architecture

```text
┌────────────┐   ┌───────────────┐   fail   ┌───────────────┐
│   Request     │ ──▶ │  Primary Model  │ ────────▶ │  Fallback Model │
└────────────┘   │  (Sonnet 5)       │           │  (Opus 4.8 /       │
                  └───────────────┘           │   different provider) │
                          │ success            └────────┬───────┘
                          ▼                              │ still fails
                     Return response                     ▼
                                              ┌───────────────────┐
                                              │  Final fallback:     │
                                              │  cached response or    │
                                              │  graceful error message │
                                              └───────────────────┘
```

### Internal Flow
1. Define the fallback chain ahead of time — not improvised during an incident.
2. Attempt the primary model with a bounded timeout (don't let a hanging primary call block the fallback indefinitely — Timeout, Reliability Patterns).
3. On failure (timeout, rate limit, 5xx error), log the failure with enough detail to diagnose later, and immediately attempt the next model in the chain.
4. If every model in the chain fails, fall back to a safe, honest degraded response (a cached answer, or a clear "we're experiencing issues, please try again" message) — never let the user see a raw stack trace or silently return nothing.
5. Track fallback activation rate as a first-class metric — frequent fallback activation is itself a signal something's wrong with your primary path, worth alerting on (Observability, later).

### Simple Implementation

```python
import anthropic

client = anthropic.Anthropic()

FALLBACK_CHAIN = ["claude-sonnet-5", "claude-opus-4-8"]

def invoke_with_fallback(prompt: str) -> str:
    last_error = None
    for model in FALLBACK_CHAIN:
        try:
            response = client.messages.create(
                model=model, max_tokens=800,
                messages=[{"role": "user", "content": prompt}],
            )
            return response.content[0].text
        except anthropic.APIStatusError as e:
            last_error = e
            print(f"Model {model} failed ({e.status_code}), trying next...")
            continue
    raise RuntimeError(f"All models in fallback chain failed. Last error: {last_error}")
```

### Production Implementation

```python
import asyncio
import logging
import time
from dataclasses import dataclass

import anthropic
from anthropic import APIStatusError, APITimeoutError

logger = logging.getLogger("genai.fallback")


@dataclass
class FallbackAttempt:
    model: str
    succeeded: bool
    latency_ms: float
    error: str | None = None


class AllModelsFailedError(Exception):
    def __init__(self, attempts: list[FallbackAttempt]):
        self.attempts = attempts
        super().__init__(f"All {len(attempts)} models in the fallback chain failed")


class FallbackChain:
    """Ordered chain of models to try in sequence. Every attempt is
    recorded — this trace is what lets you distinguish 'the primary is
    having a bad day' (a metric worth alerting on) from 'this one
    request happened to fail' (routine and expected at scale)."""

    def __init__(self, models: list[str], per_call_timeout: float = 15.0):
        self.models = models
        self.per_call_timeout = per_call_timeout
        self.client = anthropic.AsyncAnthropic(timeout=per_call_timeout)

    async def invoke(self, prompt: str, max_tokens: int = 800) -> tuple[str, list[FallbackAttempt]]:
        attempts: list[FallbackAttempt] = []

        for model in self.models:
            start = time.monotonic()
            try:
                response = await self.client.messages.create(
                    model=model, max_tokens=max_tokens,
                    messages=[{"role": "user", "content": prompt}],
                )
                latency_ms = (time.monotonic() - start) * 1000
                attempts.append(FallbackAttempt(model=model, succeeded=True, latency_ms=latency_ms))
                logger.info("fallback_chain_success model=%s latency_ms=%.0f attempt_index=%d",
                            model, latency_ms, len(attempts) - 1)
                return response.content[0].text, attempts

            except (APIStatusError, APITimeoutError, asyncio.TimeoutError) as e:
                latency_ms = (time.monotonic() - start) * 1000
                attempts.append(FallbackAttempt(model=model, succeeded=False,
                                                 latency_ms=latency_ms, error=str(e)))
                logger.warning("fallback_chain_attempt_failed model=%s error=%s", model, e)
                continue  # try next model in the chain

        raise AllModelsFailedError(attempts)


async def get_response_with_graceful_degradation(chain: FallbackChain, prompt: str) -> str:
    try:
        text, attempts = await chain.invoke(prompt)
        if len(attempts) > 1:
            logger.warning("fallback_activated attempts=%d", len(attempts))  # alertable signal
        return text
    except AllModelsFailedError as e:
        logger.error("all_models_failed attempts=%s", [(a.model, a.error) for a in e.attempts])
        # Never surface a raw error to the end user — degrade gracefully.
        return ("We're having trouble generating a response right now. "
                "Please try again in a moment.")
```

### Real-World Use Case
A production chat assistant configures Sonnet 5 as primary and Opus 4.8 as fallback (same provider, different model — cheap insurance against a single-model-specific issue), and — for the most business-critical flows only, like an outage-sensitive checkout assistant — adds a second provider's model as a final fallback before degrading to a static "please try again" message. During a real incident where one model experienced elevated error rates, this chain kept the assistant functioning (with a brief latency bump from the extra attempt) instead of going fully down, while the fallback-activation metric alerted the on-call engineer to the underlying issue in real time.

### Advantages
- Converts a hard outage into a graceful, mostly-invisible degradation for end users.
- The attempt trace doubles as an early-warning signal for provider-side incidents — often faster than waiting for a status-page update.
- Composable with Model Routing (Pattern 15) — you can have a fallback chain *per* tier, not just one global chain.

### Disadvantages / Failure Modes
- Each fallback attempt adds latency — a chain of 3 sequential attempts, worst case, is 3x the timeout before the user gets any response (or the degraded message); tune `per_call_timeout` carefully.
- A fallback model may have subtly different behavior/quality than the primary — silently degrading answer quality during a fallback event is a real risk if the fallback model wasn't validated for this exact task ahead of time (same evaluation rigor as the primary applies here, not an afterthought).
- Retrying on every error type indiscriminately can worsen an outage (retry storms hitting an already-struggling provider) — distinguish retryable errors (5xx, timeout, rate limit) from non-retryable ones (a genuinely malformed request will fail identically on every model in the chain, so don't retry those).
- Different providers can have materially different safety behaviors, formatting conventions, or tool-calling schemas — a true cross-provider fallback needs an abstraction layer normalizing these differences, which is nontrivial (this is exactly what the LLM Gateway pattern, covered later in Production Architecture, is built to solve properly).

### When NOT to Use It
- Prototypes and early development — added complexity with no real uptime requirement yet.
- Tasks where a stale cached response or a clear error message is genuinely a better user experience than an answer from an unvalidated fallback model — sometimes "fail clearly" beats "degrade silently," especially for high-stakes decisions.
- Non-retryable failures (bad request, invalid schema) — a fallback chain doesn't help here; fix the request instead of cycling through models that will all reject it identically.

### Exercise
Extend `FallbackChain` to distinguish retryable from non-retryable errors: add a check that a `400 Bad Request` (malformed input) short-circuits the chain immediately with a clear error, rather than wastefully attempting every model in sequence for a request that will fail identically on all of them. This exact retryable-vs-not judgment call is formalized properly in the **Retry** and **Circuit Breaker** patterns, coming up in the Reliability Patterns section.

---
