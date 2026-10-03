# 7. Fan-Out / Fan-In

## 7.1 What is it?

**Fan-Out / Fan-In** sends the same request out to **multiple independent
external services** at once (fan-out), then combines whatever comes back
into **one decision** (fan-in) — using real aggregation logic (weighted
scoring, voting, reconciliation), not just formatting results side by side.
Critically, the fan-in step is built to **tolerate some branches failing or
timing out**, since real external services aren't always available.

This is a close cousin of Pattern 2 (Parallel Workflow), but with two
important differences:

- The branches here typically call **external systems** (third-party APIs,
  partner services) rather than doing different *kinds* of analysis on the
  same text.
- The fan-in step does **real combination logic** — like a weighted average
  or a majority vote — and has to **handle partial failure** gracefully,
  rather than just assembling a report assuming every branch succeeded.

## 7.2 What problem does it solve?

Many important decisions shouldn't rely on a single external data source —
that source might be wrong, biased, or simply down when you need it. Querying
several independent providers and combining their answers gives a more
reliable result. But naively requiring *all* of them to respond makes the
whole system fragile: one slow or failing provider shouldn't be able to break
everything.

The Fan-Out / Fan-In pattern solves this by:

- Getting answers from **multiple independent sources at once**, which is
  both faster (they run concurrently) and more reliable (no single point of
  failure) than querying one at a time.
- Defining **explicit combination logic** — how do you turn three different
  scores into one decision? That logic lives in one clear place.
- Making the system **degrade gracefully** — if one provider times out, the
  system can often still produce a valid result from the ones that responded,
  rather than failing the whole request.
- Keeping a **clear minimum-quorum rule** — e.g., "need at least 2 of 3
  sources" — so the system knows exactly when it has *too little* data to
  trust a result.

## 7.3 Realistic production example: Multi-Bureau Credit Verification

A lending platform (`CrediCore`) checks an applicant's credit by querying
**three independent credit bureaus** at once, since no single bureau is
always available or always accurate:

- **Bureau A** — most reliable historically, given the highest weight.
- **Bureau B** — reliable, medium weight.
- **Bureau C** — occasionally slow or unavailable, lowest weight, and in this
  example **simulated to fail** to demonstrate graceful degradation.

**Fan-out:** all three bureaus are queried concurrently.

**Fan-in:** the system combines whichever scores came back using a
**weighted average**. If **at least 2 of the 3** bureaus responded, it
produces a confident verdict. If **fewer than 2** responded, there isn't
enough data to trust a result, so it's flagged for manual review instead of
guessing.

## 7.4 Architecture / Flow Diagram

```mermaid
flowchart TD
    Start([Credit Check Requested]) --> Fork{ }
    Fork --> A[Query Bureau A]
    Fork --> B[Query Bureau B]
    Fork --> C[Query Bureau C - may time out]
    A --> Agg[Aggregate: weighted average + quorum check]
    B --> Agg
    C --> Agg
    Agg -->|quorum met| Verdict[Generate Verdict and Rationale - LLM]
    Agg -->|quorum NOT met| Manual[Flag for Manual Review]
    Verdict --> End([Result Returned])
    Manual --> End

    style A fill:#FDE9C8,stroke:#F59E0B
    style B fill:#FDE9C8,stroke:#F59E0B
    style C fill:#FEE2E2,stroke:#EF4444
    style Agg fill:#DCEEFB,stroke:#3B82F6
    style Manual fill:#FEF9C3,stroke:#EAB308
```

Notice the aggregation step here isn't just "combine everything" — it makes
its own decision (quorum met or not) about whether it even has enough to
proceed, which is what distinguishes real Fan-In logic from a simple merge.

## 7.5 Request-to-response flow, step by step

1. A client sends an applicant ID to `run_credit_check()`.
2. LangGraph builds the initial `CreditState` and fans out from `START` to
   three concurrent nodes: `query_bureau_a`, `query_bureau_b`,
   `query_bureau_c`.
3. Each bureau node **simulates an external API call** (`asyncio.sleep`
   standing in for network latency) and either returns a score or raises a
   simulated timeout — wrapped in `try/except` so a single failing bureau
   never crashes the graph. A failed bureau writes `None` for its score
   instead of stopping anything.
4. Once all three branches finish (successfully or not), LangGraph proceeds
   to `aggregate_scores` — the fan-in step.
5. **`aggregate_scores`** counts how many bureaus actually responded
   (non-`None`). If **fewer than 2 responded**, it sets
   `state.quorum_met = False` and stops here — there isn't enough data for a
   safe automated decision.
6. If **quorum is met**, it computes a **weighted average** using each
   bureau's reliability weight, applied only across the bureaus that
   actually responded (weights are re-normalized so partial responses don't
   unfairly skew the result).
7. A conditional edge on `aggregate_scores` routes to either
   `generate_verdict` (quorum met — makes an LLM call to phrase a short,
   plain-language rationale for the final score) or `flag_manual_review`
   (quorum not met — explains why automated scoring wasn't possible).
8. Both paths converge to `END` with a consistent result shape.

## 7.6 Why this pattern fits this problem

- **No single credit bureau should be a single point of failure** for a
  lending decision — querying three independent sources concurrently is both
  faster and safer than relying on one, or querying them one at a time.
- **Weighted combination reflects reality** — not all data sources are
  equally trustworthy; a flat average would treat a historically flaky
  bureau the same as a highly reliable one.
- **Explicit quorum logic prevents false confidence** — silently producing a
  score from just 1 out of 3 bureaus (if the other two happened to fail)
  could look identical to a confident 3-out-of-3 result unless the system
  tracks and checks this on purpose.
- **Graceful degradation matters for a live financial product** — a single
  bureau timing out (a very normal occurrence) shouldn't ever cause the whole
  credit check to fail outright.

## 7.7 Production-quality implementation

```python
"""
Fan-Out / Fan-In — Multi-Bureau Credit Verification
Pattern: query multiple independent services concurrently, combine with
weighted logic that tolerates partial failure

Run:
    pip install "langgraph==1.2.10" "langchain==1.3.14" "langchain-ollama==1.1.0" "pydantic==2.9.2"
    ollama pull llama3.1:8b     # make sure Ollama is running locally
    python credit_verification.py
"""

from __future__ import annotations

import asyncio
import logging
import random
from typing import Optional

from langchain_ollama import ChatOllama
from langgraph.graph import StateGraph, START, END
from pydantic import BaseModel

# --------------------------------------------------------------------------
# 1. Logging
# --------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("credit_verification")


# --------------------------------------------------------------------------
# 2. Shared state
# --------------------------------------------------------------------------
class CreditState(BaseModel):
    applicant_id: str = ""

    score_bureau_a: Optional[int] = None
    score_bureau_b: Optional[int] = None
    score_bureau_c: Optional[int] = None

    quorum_met: Optional[bool] = None
    combined_score: Optional[float] = None

    verdict: Optional[str] = None
    rationale: Optional[str] = None


# Reliability weights -- CrediCore's own historical accuracy data per bureau.
_BUREAU_WEIGHTS = {"a": 0.5, "b": 0.35, "c": 0.15}
_MIN_QUORUM = 2  # need at least 2 of 3 bureaus to trust an automated result

_llm = ChatOllama(model="llama3.1:8b", temperature=0.2)


# --------------------------------------------------------------------------
# 3. Fan-out — three independent, concurrent "external API calls".
#    Each is wrapped in try/except so one failing bureau can never crash
#    the graph; a failure just leaves that bureau's score as None.
# --------------------------------------------------------------------------
async def query_bureau_a(state: CreditState) -> dict:
    logger.info("FAN-OUT — querying Bureau A for %s", state.applicant_id)
    try:
        await asyncio.sleep(0.3)  # simulated network latency
        score = 680 + (hash(state.applicant_id + "a") % 100)  # deterministic demo score
        return {"score_bureau_a": score}
    except Exception as exc:  # noqa: BLE001
        logger.error("Bureau A failed: %s", exc)
        return {"score_bureau_a": None}


async def query_bureau_b(state: CreditState) -> dict:
    logger.info("FAN-OUT — querying Bureau B for %s", state.applicant_id)
    try:
        await asyncio.sleep(0.4)
        score = 660 + (hash(state.applicant_id + "b") % 120)
        return {"score_bureau_b": score}
    except Exception as exc:  # noqa: BLE001
        logger.error("Bureau B failed: %s", exc)
        return {"score_bureau_b": None}


async def query_bureau_c(state: CreditState) -> dict:
    logger.info("FAN-OUT — querying Bureau C for %s", state.applicant_id)
    try:
        await asyncio.sleep(0.5)
        # Simulated: Bureau C is the least reliable and times out here,
        # to demonstrate the fan-in step handling a missing response.
        raise TimeoutError("Bureau C did not respond in time")
    except Exception as exc:  # noqa: BLE001
        logger.warning("Bureau C unavailable, proceeding without it: %s", exc)
        return {"score_bureau_c": None}


# --------------------------------------------------------------------------
# 4. Fan-in — real aggregation logic, not just formatting.
#    Checks quorum FIRST, then computes a re-normalized weighted average
#    across only the bureaus that actually responded.
# --------------------------------------------------------------------------
def aggregate_scores(state: CreditState) -> dict:
    responses = {
        "a": state.score_bureau_a,
        "b": state.score_bureau_b,
        "c": state.score_bureau_c,
    }
    responded = {k: v for k, v in responses.items() if v is not None}
    logger.info("FAN-IN — %d/3 bureaus responded: %s", len(responded), list(responded.keys()))

    if len(responded) < _MIN_QUORUM:
        return {"quorum_met": False}

    # Re-normalize weights across only the bureaus that responded, so a
    # missing bureau doesn't silently shrink the effective score.
    total_weight = sum(_BUREAU_WEIGHTS[k] for k in responded)
    combined = sum(responded[k] * _BUREAU_WEIGHTS[k] for k in responded) / total_weight

    return {"quorum_met": True, "combined_score": round(combined, 1)}


def route_by_quorum(state: CreditState) -> str:
    return "generate_verdict" if state.quorum_met else "flag_manual_review"


# --------------------------------------------------------------------------
# 5. Path A — quorum met: generate a plain-language verdict (LLM).
# --------------------------------------------------------------------------
_VERDICT_PROMPT = """A lending applicant has a combined weighted credit score
of {score}. In one short sentence, describe what this score generally
indicates for a lending decision (e.g., strong, moderate, weak).
"""


def generate_verdict(state: CreditState) -> dict:
    logger.info("VERDICT — combined score %.1f", state.combined_score)
    try:
        response = _llm.invoke(_VERDICT_PROMPT.format(score=state.combined_score))
        rationale = response.content.strip()
    except Exception as exc:  # noqa: BLE001
        logger.error("generate_verdict LLM call failed: %s", exc)
        rationale = f"Combined score: {state.combined_score}."

    return {"verdict": "scored", "rationale": rationale}


# --------------------------------------------------------------------------
# 6. Path B — quorum not met: explain why, route to a human.
# --------------------------------------------------------------------------
def flag_manual_review(state: CreditState) -> dict:
    responded_count = sum(
        s is not None for s in (state.score_bureau_a, state.score_bureau_b, state.score_bureau_c)
    )
    logger.warning("MANUAL REVIEW — only %d/3 bureaus responded", responded_count)
    return {
        "verdict": "manual_review",
        "rationale": (
            f"Only {responded_count} of 3 credit bureaus responded "
            f"(minimum {_MIN_QUORUM} required). Insufficient data for an "
            f"automated decision — routed to manual review."
        ),
    }


# --------------------------------------------------------------------------
# 7. Build the graph — fixed fan-out to 3 external services, fan-in with
#    quorum-aware aggregation, then a conditional path based on that result.
# --------------------------------------------------------------------------
def build_graph():
    graph = StateGraph(CreditState)

    graph.add_node("query_bureau_a", query_bureau_a)
    graph.add_node("query_bureau_b", query_bureau_b)
    graph.add_node("query_bureau_c", query_bureau_c)
    graph.add_node("aggregate_scores", aggregate_scores)
    graph.add_node("generate_verdict", generate_verdict)
    graph.add_node("flag_manual_review", flag_manual_review)

    # Fan-out
    graph.add_edge(START, "query_bureau_a")
    graph.add_edge(START, "query_bureau_b")
    graph.add_edge(START, "query_bureau_c")

    # Fan-in
    graph.add_edge("query_bureau_a", "aggregate_scores")
    graph.add_edge("query_bureau_b", "aggregate_scores")
    graph.add_edge("query_bureau_c", "aggregate_scores")

    # Conditional path based on the fan-in step's own quorum decision
    graph.add_conditional_edges(
        "aggregate_scores",
        route_by_quorum,
        {
            "generate_verdict": "generate_verdict",
            "flag_manual_review": "flag_manual_review",
        },
    )

    graph.add_edge("generate_verdict", END)
    graph.add_edge("flag_manual_review", END)

    return graph.compile()


# --------------------------------------------------------------------------
# 8. Public entry point — async, so the three bureau calls run concurrently.
# --------------------------------------------------------------------------
async def run_credit_check(applicant_id: str) -> dict:
    app = build_graph()
    initial_state = CreditState(applicant_id=applicant_id)
    final_state = await app.ainvoke(initial_state)
    return final_state


# --------------------------------------------------------------------------
# 9. Demo
# --------------------------------------------------------------------------
if __name__ == "__main__":
    result = asyncio.run(run_credit_check("APPLICANT-4471"))
    print(f"Verdict: {result['verdict']}")
    print(f"Rationale: {result['rationale']}")
    if result.get("combined_score") is not None:
        print(f"Combined score: {result['combined_score']}")
```

**Notes on production-readiness choices made above:**

- **Each bureau call wraps its own `try/except`** — a timeout from Bureau C
  never propagates up and crashes the graph; it just leaves that one field
  as `None`, which the fan-in step is specifically designed to handle.
- **Quorum is checked *before* any averaging happens** — this ordering
  matters: it's the difference between "we have enough data, let's combine
  it" and "let's combine whatever we have and hope it's enough," which is a
  much weaker guarantee for a financial decision.
- **Re-normalized weights** — dividing by `total_weight` (the sum of weights
  for bureaus that actually responded) means a missing bureau doesn't quietly
  drag the combined score down just because its weight is now "missing" from
  the total; the remaining bureaus' relative importance is preserved.
- **The LLM is only used for the final human-readable rationale**, not for
  the actual scoring decision — the numeric combination is deterministic,
  auditable code, which matters a lot for a regulated financial outcome.

---

⬅ [6. Map-Reduce](06-map-reduce.md) | [Back to index](README.md) | Next: [8. Iterative Workflow](08-iterative-workflow.md) ➡

# Multi-Bureau Credit Verification — Fan-Out/Fan-In with Quorum (Java + Spring AI)

A Java port of the LangGraph workflow that queries three independent external services
concurrently, combines their results with **quorum-aware, re-normalized weighted aggregation**,
and only then makes a conditional routing decision based on whether enough of them responded.
Built on:

- **Java 25** (current LTS) — virtual threads for the concurrent bureau calls
- **Spring Boot 4.1.0**
- **Spring AI 2.0.0** (GA) with the **Ollama** starter, mirroring `llama3.1:8b`

## Mapping the shape

This combines two patterns already ported separately: fan-out/fan-in (like the contract-review
workflow) and conditional routing (like the order-fraud-check workflow) — but the fan-in step
here does real aggregation logic with partial-failure tolerance, not just formatting. That's the
part worth preserving exactly.

| LangGraph concept | Spring / Java equivalent |
|---|---|
| Three `query_bureau_*` async nodes | Three `BureauClient` implementations, called concurrently via `CompletableFuture` on virtual threads |
| A bureau raising inside `try/except`, degrading to `None` | A `BureauClient` call wrapped the same way, degrading to `null`/`Optional.empty()` |
| `aggregate_scores` (quorum check + re-normalized weighted average) | `ScoreAggregationStep` — same two-phase logic, unchanged |
| `route_by_quorum(state) -> str` | `if (quorumMet) ... else ...` in `CreditVerificationService` |
| `generate_verdict` / `flag_manual_review` | `GenerateVerdictStep` / `FlagManualReviewStep` |
| `_BUREAU_WEIGHTS`, `_MIN_QUORUM` module constants | `static final` constants on `ScoreAggregationStep` |

The routing decision here is a plain boolean (`quorumMet`), not a multi-way category the way the
order-fraud-check and ticket-routing ports were — so a simple `if/else` is the direct, honest
translation rather than reaching for a `sealed` interface where it wouldn't add anything.

---

## Project structure

```
credit-verification/
├── pom.xml
└── src/main/java/com/example/creditverification/
    ├── CreditVerificationApplication.java
    ├── model/
    │   ├── BureauScores.java
    │   └── CreditVerdict.java
    ├── pipeline/
    │   ├── BureauClient.java
    │   ├── BureauAClient.java
    │   ├── BureauBClient.java
    │   ├── BureauCClient.java
    │   ├── ScoreAggregationStep.java
    │   ├── GenerateVerdictStep.java
    │   ├── FlagManualReviewStep.java
    │   └── CreditVerificationService.java
    ├── web/
    │   └── CreditVerificationController.java
    └── CreditVerificationRunner.java   (CLI demo, mirrors the Python __main__ block)
└── src/main/resources/
    └── application.yml
```

---

## `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>credit-verification</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>25</java.version>
        <spring-ai.version>2.0.0</spring-ai.version>
    </properties>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <!-- Ollama model starter — local llama3.1:8b, same as langchain-ollama -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-ollama</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

## `src/main/resources/application.yml`

```yaml
spring:
  application:
    name: credit-verification
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: llama3.1:8b
          temperature: 0.2

logging:
  level:
    com.example.creditverification: INFO
```

---

## Domain model

### `model/BureauScores.java`

```java
package com.example.creditverification.model;

public record BureauScores(Integer bureauA, Integer bureauB, Integer bureauC) {}
```

### `model/CreditVerdict.java`

```java
package com.example.creditverification.model;

public record CreditVerdict(
        String applicantId,
        BureauScores scores,
        boolean quorumMet,
        Double combinedScore,
        String verdict,
        String rationale
) {}
```

---

## Bureau clients (fan-out)

### `pipeline/BureauClient.java`

```java
package com.example.creditverification.pipeline;

public sealed interface BureauClient
        permits BureauAClient, BureauBClient, BureauCClient {

    /** Returns the bureau's score, or {@code null} if the bureau failed/timed out. */
    Integer queryScore(String applicantId);

    String bureauKey();
}
```

### `pipeline/BureauAClient.java`

```java
package com.example.creditverification.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class BureauAClient implements BureauClient {

    private static final Logger log = LoggerFactory.getLogger(BureauAClient.class);

    @Override
    public Integer queryScore(String applicantId) {
        log.info("FAN-OUT - querying Bureau A for {}", applicantId);
        try {
            Thread.sleep(300); // simulated network latency (cheap on a virtual thread)
            return 680 + Math.floorMod((applicantId + "a").hashCode(), 100); // deterministic demo score
        } catch (Exception e) {
            log.error("Bureau A failed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String bureauKey() {
        return "a";
    }
}
```

### `pipeline/BureauBClient.java`

```java
package com.example.creditverification.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class BureauBClient implements BureauClient {

    private static final Logger log = LoggerFactory.getLogger(BureauBClient.class);

    @Override
    public Integer queryScore(String applicantId) {
        log.info("FAN-OUT - querying Bureau B for {}", applicantId);
        try {
            Thread.sleep(400);
            return 660 + Math.floorMod((applicantId + "b").hashCode(), 120);
        } catch (Exception e) {
            log.error("Bureau B failed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String bureauKey() {
        return "b";
    }
}
```

### `pipeline/BureauCClient.java`

```java
package com.example.creditverification.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class BureauCClient implements BureauClient {

    private static final Logger log = LoggerFactory.getLogger(BureauCClient.class);

    @Override
    public Integer queryScore(String applicantId) {
        log.info("FAN-OUT - querying Bureau C for {}", applicantId);
        try {
            Thread.sleep(500);
            // Simulated: Bureau C is the least reliable and times out here,
            // to demonstrate the fan-in step handling a missing response.
            throw new java.util.concurrent.TimeoutException("Bureau C did not respond in time");
        } catch (Exception e) {
            log.warn("Bureau C unavailable, proceeding without it: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String bureauKey() {
        return "c";
    }
}
```

---

## Fan-in — quorum-aware aggregation

### `pipeline/ScoreAggregationStep.java`

The real logic of the whole workflow: checks quorum first, then computes a re-normalized
weighted average across only the bureaus that actually responded — unchanged from the Python
version, since this is the one place where a subtle rewrite could silently change lending
outcomes.

```java
package com.example.creditverification.pipeline;

import com.example.creditverification.model.BureauScores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ScoreAggregationStep {

    private static final Logger log = LoggerFactory.getLogger(ScoreAggregationStep.class);

    // Reliability weights — CrediCore's own historical accuracy data per bureau.
    private static final Map<String, Double> BUREAU_WEIGHTS = Map.of("a", 0.5, "b", 0.35, "c", 0.15);
    private static final int MIN_QUORUM = 2; // need at least 2 of 3 bureaus to trust an automated result

    public record AggregationResult(boolean quorumMet, Double combinedScore) {}

    public AggregationResult aggregate(BureauScores scores) {
        Map<String, Integer> responses = new LinkedHashMap<>();
        responses.put("a", scores.bureauA());
        responses.put("b", scores.bureauB());
        responses.put("c", scores.bureauC());

        Map<String, Integer> responded = new LinkedHashMap<>();
        responses.forEach((key, value) -> {
            if (value != null) {
                responded.put(key, value);
            }
        });

        log.info("FAN-IN - {}/3 bureaus responded: {}", responded.size(), responded.keySet());

        if (responded.size() < MIN_QUORUM) {
            return new AggregationResult(false, null);
        }

        // Re-normalize weights across only the bureaus that responded, so a missing
        // bureau doesn't silently shrink the effective score.
        double totalWeight = responded.keySet().stream().mapToDouble(BUREAU_WEIGHTS::get).sum();
        double combined = responded.entrySet().stream()
                .mapToDouble(e -> e.getValue() * BUREAU_WEIGHTS.get(e.getKey()))
                .sum() / totalWeight;

        return new AggregationResult(true, Math.round(combined * 10.0) / 10.0);
    }
}
```

---

## The two conditional paths

### `pipeline/GenerateVerdictStep.java`

Path A — quorum met: generate a plain-language verdict with an LLM call.

```java
package com.example.creditverification.pipeline;

import com.example.creditverification.model.CreditVerdict;
import com.example.creditverification.model.BureauScores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class GenerateVerdictStep {

    private static final Logger log = LoggerFactory.getLogger(GenerateVerdictStep.class);

    private static final String VERDICT_PROMPT = """
            A lending applicant has a combined weighted credit score of {score}. \
            In one short sentence, describe what this score generally indicates \
            for a lending decision (e.g., strong, moderate, weak).
            """;

    private final ChatClient chatClient;

    public GenerateVerdictStep(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public CreditVerdict apply(String applicantId, BureauScores scores, double combinedScore) {
        log.info("VERDICT - combined score {}", combinedScore);
        String rationale;
        try {
            rationale = chatClient.prompt()
                    .user(u -> u.text(VERDICT_PROMPT).param("score", String.valueOf(combinedScore)))
                    .call()
                    .content()
                    .strip();
        } catch (Exception e) {
            log.error("generate_verdict LLM call failed: {}", e.getMessage());
            rationale = "Combined score: %s.".formatted(combinedScore);
        }

        return new CreditVerdict(applicantId, scores, true, combinedScore, "scored", rationale);
    }
}
```

### `pipeline/FlagManualReviewStep.java`

Path B — quorum not met: explain why, route to a human. No LLM call needed.

```java
package com.example.creditverification.pipeline;

import com.example.creditverification.model.BureauScores;
import com.example.creditverification.model.CreditVerdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FlagManualReviewStep {

    private static final Logger log = LoggerFactory.getLogger(FlagManualReviewStep.class);
    private static final int MIN_QUORUM = 2;

    public CreditVerdict apply(String applicantId, BureauScores scores) {
        long respondedCount = java.util.stream.Stream
                .of(scores.bureauA(), scores.bureauB(), scores.bureauC())
                .filter(s -> s != null)
                .count();

        log.warn("MANUAL REVIEW - only {}/3 bureaus responded", respondedCount);

        String rationale = "Only %d of 3 credit bureaus responded (minimum %d required). "
                + "Insufficient data for an automated decision - routed to manual review."
                        .formatted(respondedCount, MIN_QUORUM);

        return new CreditVerdict(applicantId, scores, false, null, "manual_review", rationale);
    }
}
```

---

## Orchestration

### `pipeline/CreditVerificationService.java`

Fan-out to the three bureau clients, fan-in via `ScoreAggregationStep`, then the conditional
routing decision — the direct analogue of the full graph.

```java
package com.example.creditverification.pipeline;

import com.example.creditverification.model.BureauScores;
import com.example.creditverification.model.CreditVerdict;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class CreditVerificationService {

    private final List<BureauClient> bureauClients;
    private final ScoreAggregationStep scoreAggregationStep;
    private final GenerateVerdictStep generateVerdictStep;
    private final FlagManualReviewStep flagManualReviewStep;
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public CreditVerificationService(List<BureauClient> bureauClients,
                                      ScoreAggregationStep scoreAggregationStep,
                                      GenerateVerdictStep generateVerdictStep,
                                      FlagManualReviewStep flagManualReviewStep) {
        this.bureauClients = bureauClients;
        this.scoreAggregationStep = scoreAggregationStep;
        this.generateVerdictStep = generateVerdictStep;
        this.flagManualReviewStep = flagManualReviewStep;
    }

    public CreditVerdict check(String applicantId) {
        // Fan-out: three independent, concurrent calls. Spring injects bureauClients in
        // whatever bean order they were declared; we key results by bureauKey() so the
        // order doesn't matter for correctness.
        var futuresByKey = bureauClients.stream()
                .collect(java.util.stream.Collectors.toMap(
                        BureauClient::bureauKey,
                        client -> CompletableFuture.supplyAsync(
                                () -> client.queryScore(applicantId), virtualThreadExecutor)));

        CompletableFuture.allOf(futuresByKey.values().toArray(CompletableFuture[]::new)).join();

        BureauScores scores = new BureauScores(
                futuresByKey.get("a").join(),
                futuresByKey.get("b").join(),
                futuresByKey.get("c").join());

        // Fan-in: real aggregation logic, quorum-checked before any score is trusted.
        var aggregation = scoreAggregationStep.aggregate(scores);

        // Conditional routing based on the fan-in step's own quorum decision.
        return aggregation.quorumMet()
                ? generateVerdictStep.apply(applicantId, scores, aggregation.combinedScore())
                : flagManualReviewStep.apply(applicantId, scores);
    }
}
```

---

## Entry points

### `web/CreditVerificationController.java`

```java
package com.example.creditverification.web;

import com.example.creditverification.model.CreditVerdict;
import com.example.creditverification.pipeline.CreditVerificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CreditVerificationController {

    private final CreditVerificationService creditVerificationService;

    public CreditVerificationController(CreditVerificationService creditVerificationService) {
        this.creditVerificationService = creditVerificationService;
    }

    @GetMapping("/api/applicants/{applicantId}/credit-check")
    public CreditVerdict check(@PathVariable String applicantId) {
        return creditVerificationService.check(applicantId);
    }
}
```

### `CreditVerificationRunner.java` (CLI demo, mirrors the Python `if __name__ == "__main__"` block)

```java
package com.example.creditverification;

import com.example.creditverification.pipeline.CreditVerificationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class CreditVerificationRunner implements CommandLineRunner {

    private final CreditVerificationService creditVerificationService;

    public CreditVerificationRunner(CreditVerificationService creditVerificationService) {
        this.creditVerificationService = creditVerificationService;
    }

    @Override
    public void run(String... args) {
        var result = creditVerificationService.check("APPLICANT-4471");
        System.out.println("Verdict: " + result.verdict());
        System.out.println("Rationale: " + result.rationale());
        if (result.combinedScore() != null) {
            System.out.println("Combined score: " + result.combinedScore());
        }
    }
}
```

### `CreditVerificationApplication.java`

```java
package com.example.creditverification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CreditVerificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(CreditVerificationApplication.class, args);
    }
}
```

---

## Running it

```bash
ollama pull llama3.1:8b
ollama serve   # if not already running

# CLI demo (prints verdict/rationale/score, like the Python script)
mvn spring-boot:run -Dspring-boot.run.profiles=demo

# Or as a service
mvn spring-boot:run
curl localhost:8080/api/applicants/APPLICANT-4471/credit-check
```

## Notes on the port

- **Aggregation logic is untouched**: the quorum check, the re-normalization across only the
  bureaus that responded, and the two constants (`MIN_QUORUM = 2`, the 0.5/0.35/0.15 weights) are
  translated as-is. This is the step where a well-intentioned "cleanup" during translation could
  quietly change which applicants get an automated verdict versus a manual review, so it's kept
  line-for-line rather than restructured.
- **Deterministic demo scores preserved**: `Math.floorMod((applicantId + "a").hashCode(), 100)`
  reproduces the same "deterministic pseudo-score from a hash" trick as Python's
  `hash(...) % 100` — useful for the demo, not meant as production bureau-integration logic
  (same caveat applies to the Python original).
- **Bureau C's simulated failure kept**: `BureauCClient` still always throws a `TimeoutException`
  internally and returns `null`, exactly reproducing the demo's point that the fan-in step must
  handle a missing response gracefully.
- **Boolean routing, not multi-way**: unlike the ticket-routing and order-fraud-check ports, the
  decision here is a plain `quorumMet` boolean, so a direct `if/else` is the honest translation —
  there's no enumerable category set that would benefit from a `sealed` interface.
- **Versions**: same Ollama-backed `spring-ai-starter-model-ollama` / `llama3.1:8b` setup as the
  other ports in this series.
