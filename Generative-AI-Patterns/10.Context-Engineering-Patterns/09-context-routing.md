# Pattern 09 — Context Routing

[← Back to index](./README.md) | Previous: [Pattern 08 — Context Isolation](./08-context-isolation.md) | Next: Pattern 10 — Context Summarization (queued)

---

## 1. Introduce the pattern

**Context Routing** decides, before any context is even fetched, *which sources or subsystems a given query should be sent to at all.*

This sits **upstream** of Pattern 01 (Selection). Selection assumes the fan-out to all relevant sources has already happened and asks "of what came back, what's relevant?" Routing asks the prior question: *"should I even fan out to all six Helios sources for this query, or does this query only need one of them — or none of them, because it actually needs a completely different subsystem (like a drafting tool) rather than evidence retrieval at all?"*

```
Analyst query → [ ROUTING ] → which source(s) / subsystem should handle this?
                    │
                    ├── "why was this flagged?" → transaction history + policy + similar cases
                    ├── "what's our policy on X?" → compliance policy ONLY
                    ├── "draft an escalation email" → drafting subsystem, NOT evidence retrieval
                    └── "what's the case status?" → case metadata ONLY, fast path
```

**Mental model:** a hospital triage nurse doesn't send every patient through every department's full workup — a sprained ankle goes to radiology, not cardiology; a request for a prescription refill doesn't need a full diagnostic workup at all. Routing is that triage step: get the request to the right subsystem quickly, and don't invoke subsystems that have nothing to do with the actual question.

---

## 2. The problem it solves

Without a routing stage, systems default to one of two costly patterns:

1. **Always fan out to everything.** Every query — even "what's the current status of this case?", which only needs the case record — triggers the full six-source fan-out from Pattern 01: embedding calls against transaction history, similar-case vector search, policy document retrieval, all of it. This is wasteful (latency, cost, and — per Pattern 04 — actively risks diluting relevance for a question that had an easy, narrow answer available) and it doesn't scale as more sources get added to the system over time.
2. **Force everything through the same pipeline shape.** A request like *"draft an escalation email summarizing this case for the compliance team"* is not fundamentally an evidence-retrieval-and-QA task — it's a drafting task that needs the case summary and a specific tone/format, not a relevance-scored pile of transaction history. Running it through the exact same Selection → Filtering → Compression → Ranking pipeline built for analytical Q&A produces a worse result than routing it to a purpose-built drafting flow.

Context Routing solves both: it classifies the query's *intent* first, and only invokes the sources and subsystems that intent actually needs — cheaply and deterministically for common, clear-cut cases, falling back to an LLM classifier only when the query is genuinely ambiguous.

---

## 3. A realistic enterprise problem (Helios)

Across a single day, Helios's copilot fields wildly different kinds of requests from analysts:

- *"Why was this transaction flagged?"* — needs transaction history, similar cases, and compliance policy.
- *"What does Policy FR-14 actually require?"* — needs **only** the compliance policy source; fetching transaction history and running a similarity search over 40,000 historical cases for this question is pure waste.
- *"What's the current status of case CASE-88421?"* — needs **only** the case record; this should be the fastest, cheapest path in the whole system, and today it might be accidentally the same cost as the most complex query if nothing routes it differently.
- *"Draft an escalation note for compliance summarizing this case."* — this is a **drafting task**, not a retrieval-and-QA task; it needs a different prompt template (formal tone, specific structure) built from a case summary, not a relevance-ranked pile of raw evidence.

Context Routing is the stage that recognizes these are different jobs before any expensive work happens, and sends each one down the right path.

---

## 4. Architecture / flow diagram

```mermaid
flowchart TD
    A[Analyst query] --> B{Deterministic Router\n(fast, regex/keyword rules)}

    B -->|confident match| C[Routing Decision]
    B -->|no confident match| D[LLM Classifier Fallback\n(ChatOllama, structured output)]
    D --> C

    C --> E{Intent}

    E -->|TRANSACTION_ANALYSIS| F["Full fan-out:\ncase + transactions + policy + similar cases\n(→ Pattern 01 Selection)"]
    E -->|POLICY_LOOKUP| G["Narrow fan-out:\ncompliance_policy ONLY"]
    E -->|CASE_STATUS| H["Narrow fan-out:\ncase_metadata ONLY (fast path)"]
    E -->|DRAFTING| I["Bypass evidence retrieval:\ncase summary + drafting subsystem\n(different prompt template entirely)"]

    F --> J[Downstream pipeline\n(Patterns 02-05)]
    G --> J
    H --> J
    I --> K[Drafting-specific prompt + LLM call]

    J --> L[ChatOllama\n(analytical Q&A)]
    L --> M[Answer to Analyst]
    K --> M

    style B fill:#4A90D9,color:#fff
    style D fill:#4A90D9,color:#fff
    style E fill:#4A90D9,color:#fff
```

---

## 5. The complete request-to-response flow

1. **Query arrives** with no assumptions yet about which sources it needs.
2. **Deterministic routing pass**: a set of fast, cheap keyword/regex rules checks for clear-cut intent signals ("policy", "what does .* require" → `POLICY_LOOKUP`; "status", "where are we on" → `CASE_STATUS`; "draft", "write .* email/note" → `DRAFTING`). This pass costs no LLM call and handles the large fraction of queries that are unambiguous.
3. **LLM classifier fallback**: if no deterministic rule matches with confidence, the query goes to a small, structured-output LLM call that picks the best-fitting intent from the same fixed set — this is the same "cheap deterministic first, LLM only when needed" philosophy used in Pattern 06's fact extraction and Pattern 03's compression routing.
4. **Routing decision produced**: an intent label plus the concrete set of context sources (or the drafting subsystem) that should be invoked for it.
5. **Selective fan-out**: only the sources named in the routing decision are queried — for `CASE_STATUS`, that's a single source; for `TRANSACTION_ANALYSIS`, that's the full multi-source fan-out that Pattern 01 was designed around.
6. **Divergent downstream paths**: analytical intents (`TRANSACTION_ANALYSIS`, `POLICY_LOOKUP`, `CASE_STATUS`) continue into the familiar Selection → Filtering → Compression → Ranking → Prioritization pipeline from Patterns 01–05. The `DRAFTING` intent instead goes to a distinct, purpose-built prompt template that never runs the evidence-scoring pipeline at all.
7. **Response returned** to the analyst, having only paid the cost of the subsystems the query actually needed.

---

## 6. Why this pattern is appropriate here

- **It's the cheapest optimization in the whole series, and it compounds.** Every source Routing correctly excludes is an embedding call, a DB query, or a vector search that Pattern 01 never has to perform — at Helios's scale (many analysts, many queries a day), routing narrow queries away from the full six-source fan-out is a direct, significant cost and latency win with no quality trade-off, since those sources genuinely weren't relevant.
- **Deterministic-first, LLM-fallback mirrors a pattern this series keeps returning to.** Cheap, fast, auditable rules handle the common cases (Pattern 02's boilerplate filter, Pattern 06's fact extraction); an LLM call is reserved for genuine ambiguity. This keeps median-case latency low while still handling the long tail of oddly-phrased queries correctly.
- **Drafting and analytical Q&A are genuinely different tasks and deserve different pipelines, not just different prompts bolted onto the same retrieval flow.** Forcing a drafting request through relevance-scored evidence retrieval adds cost and doesn't improve the draft — the analyst asking for an escalation email mostly needs a well-structured case summary and a correct tone, not the U-curve-ranked, knapsack-optimized evidence set built for "why was this flagged?"
- **It's the natural place to decide sub-agent dispatch too.** A routing decision that recognizes "this needs deep transaction-anomaly analysis" is exactly what would trigger Pattern 08's `SubAgentIsolationRunner` — Routing decides *that* a sub-agent is needed; Isolation governs *how* it's safely run.

---

## 7. Production-quality Python implementation

```python
"""
helios_context_routing.py

Pattern 09 — Context Routing
Helios Fraud Investigation Copilot

Classifies an analyst query's intent BEFORE any context fan-out happens:
- A fast, deterministic (regex/keyword) router handles clear-cut cases with
  zero LLM calls.
- An LLM-based structured-output classifier handles genuinely ambiguous
  queries as a fallback.
- A routing table maps each intent to the concrete set of context sources
  (from Pattern 01) that should actually be queried -- or, for drafting
  intents, bypasses evidence retrieval entirely in favor of a distinct
  prompt template.

Requirements:
    pip install "langchain-core>=0.3.60" "langchain-ollama>=0.3.0" "pydantic>=2.0"

Assumes a local Ollama server (`ollama serve`) with:
    ollama pull llama3.1
"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass
from enum import Enum

from langchain_core.prompts import ChatPromptTemplate
from langchain_ollama import ChatOllama
from pydantic import BaseModel, Field

logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")
logger = logging.getLogger("helios.context_routing")


# --------------------------------------------------------------------------- #
# Intent + source vocabulary (ContextSourceType reused from Pattern 01's shape)
# --------------------------------------------------------------------------- #

class ContextSourceType(str, Enum):
    CASE_METADATA = "case_metadata"
    TRANSACTION_HISTORY = "transaction_history"
    CUSTOMER_PROFILE = "customer_profile"
    SIMILAR_CASES = "similar_cases"
    COMPLIANCE_POLICY = "compliance_policy"
    ANALYST_NOTES = "analyst_notes"


class QueryIntent(str, Enum):
    TRANSACTION_ANALYSIS = "transaction_analysis"   # "why was this flagged", pattern matching
    POLICY_LOOKUP = "policy_lookup"                 # "what does policy X require"
    CASE_STATUS = "case_status"                     # "what's the status of this case"
    DRAFTING = "drafting"                            # "draft an escalation email"
    GENERAL = "general"                              # fallback: broad fan-out, safest default


# The routing table: what each intent actually needs. This is the payoff --
# everything not listed here simply never gets fetched, embedded, or scored.
ROUTING_TABLE: dict[QueryIntent, set[ContextSourceType]] = {
    QueryIntent.TRANSACTION_ANALYSIS: {
        ContextSourceType.CASE_METADATA,
        ContextSourceType.TRANSACTION_HISTORY,
        ContextSourceType.SIMILAR_CASES,
        ContextSourceType.COMPLIANCE_POLICY,
    },
    QueryIntent.POLICY_LOOKUP: {
        ContextSourceType.COMPLIANCE_POLICY,
    },
    QueryIntent.CASE_STATUS: {
        ContextSourceType.CASE_METADATA,
    },
    QueryIntent.DRAFTING: set(),  # handled by a distinct subsystem, not source fan-out at all
    QueryIntent.GENERAL: {
        ContextSourceType.CASE_METADATA,
        ContextSourceType.TRANSACTION_HISTORY,
        ContextSourceType.CUSTOMER_PROFILE,
        ContextSourceType.SIMILAR_CASES,
        ContextSourceType.COMPLIANCE_POLICY,
        ContextSourceType.ANALYST_NOTES,
    },
}


@dataclass
class RoutingDecision:
    intent: QueryIntent
    sources_to_query: set[ContextSourceType]
    method: str  # "deterministic" | "llm_fallback"
    matched_rule: str | None = None


# --------------------------------------------------------------------------- #
# Fast path: deterministic keyword/regex router
# --------------------------------------------------------------------------- #

class DeterministicRouter:
    """Cheap, zero-LLM-call rules for clear-cut intent signals. Order
    matters: more specific patterns are checked first."""

    RULES: list[tuple[QueryIntent, re.Pattern, str]] = [
        (QueryIntent.DRAFTING,
         re.compile(r"\b(draft|write|compose)\b.*\b(email|note|memo|letter|summary for)\b", re.IGNORECASE),
         "drafting_verb_plus_document"),
        (QueryIntent.POLICY_LOOKUP,
         re.compile(r"\b(policy|regulation|require[sd]?|compliance rule)\b", re.IGNORECASE),
         "policy_keyword"),
        (QueryIntent.CASE_STATUS,
         re.compile(r"\b(status|where (are|is) we|current state)\b", re.IGNORECASE),
         "status_keyword"),
        (QueryIntent.TRANSACTION_ANALYSIS,
         re.compile(r"\b(flagged|why was|fraud pattern|anomal(y|ous)|suspicious)\b", re.IGNORECASE),
         "transaction_analysis_keyword"),
    ]

    def route(self, query: str) -> RoutingDecision | None:
        for intent, pattern, rule_name in self.RULES:
            if pattern.search(query):
                logger.info("Deterministic route matched: intent=%s rule=%s", intent.value, rule_name)
                return RoutingDecision(
                    intent=intent,
                    sources_to_query=ROUTING_TABLE[intent],
                    method="deterministic",
                    matched_rule=rule_name,
                )
        return None  # no confident match -- caller should fall back to the LLM classifier


# --------------------------------------------------------------------------- #
# Fallback path: LLM-based structured-output classifier
# --------------------------------------------------------------------------- #

class IntentClassification(BaseModel):
    intent: QueryIntent = Field(description="The single best-fitting intent category for this query.")
    reasoning: str = Field(description="One short sentence explaining the classification.")


CLASSIFIER_SYSTEM_PROMPT = """You classify fraud-investigation analyst queries into exactly one intent:

- transaction_analysis: questions about why something was flagged, fraud patterns, anomalies
- policy_lookup: questions specifically about compliance policy/regulatory requirements
- case_status: simple status/state check questions about a case
- drafting: requests to draft, write, or compose a document (email, note, memo)
- general: anything that doesn't clearly fit the above, or spans multiple categories

Choose the SINGLE best-fitting category."""


class LLMFallbackRouter:
    def __init__(self, llm_model: str = "llama3.1") -> None:
        base_llm = ChatOllama(model=llm_model, temperature=0.0)
        self.structured_llm = base_llm.with_structured_output(IntentClassification)
        self.prompt = ChatPromptTemplate.from_messages(
            [("system", CLASSIFIER_SYSTEM_PROMPT), ("human", "{query}")]
        )

    def route(self, query: str) -> RoutingDecision:
        chain = self.prompt | self.structured_llm
        try:
            result: IntentClassification = chain.invoke({"query": query})
            intent = result.intent
            logger.info("LLM fallback route: intent=%s reasoning=%r", intent.value, result.reasoning)
        except Exception as exc:  # structured output can fail if the model/tool-calling path misbehaves
            logger.warning("LLM classifier failed (%s); defaulting to GENERAL (safest, broadest fan-out).", exc)
            intent = QueryIntent.GENERAL

        return RoutingDecision(
            intent=intent,
            sources_to_query=ROUTING_TABLE[intent],
            method="llm_fallback",
        )


# --------------------------------------------------------------------------- #
# Combined router
# --------------------------------------------------------------------------- #

class ContextRouter:
    def __init__(self, llm_model: str = "llama3.1") -> None:
        self.deterministic = DeterministicRouter()
        self.llm_fallback = LLMFallbackRouter(llm_model=llm_model)

    def route(self, query: str) -> RoutingDecision:
        decision = self.deterministic.route(query)
        if decision is not None:
            return decision
        logger.info("No deterministic rule matched; falling back to LLM classifier.")
        return self.llm_fallback.route(query)


# --------------------------------------------------------------------------- #
# Drafting subsystem — deliberately NOT the analytical evidence-retrieval path
# --------------------------------------------------------------------------- #

DRAFTING_SYSTEM_PROMPT = """You draft formal internal communications for Helios fraud analysts.
Write in a professional, concise tone suitable for a compliance audience. Use the case summary
provided; do not invent facts not present in it."""


def handle_drafting_request(query: str, case_summary: str, llm_model: str = "llama3.1") -> str:
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", DRAFTING_SYSTEM_PROMPT),
            ("human", "Request: {query}\n\nCase summary:\n{case_summary}"),
        ]
    )
    llm = ChatOllama(model=llm_model, temperature=0.3)  # slightly higher temperature: drafting benefits from some fluency
    chain = prompt | llm
    response = chain.invoke({"query": query, "case_summary": case_summary})
    return response.content


# --------------------------------------------------------------------------- #
# Example run
# --------------------------------------------------------------------------- #

if __name__ == "__main__":
    router = ContextRouter()

    example_queries = [
        "Why was this $14,200 transaction flagged?",
        "What does Policy FR-14 actually require in this situation?",
        "What's the current status of case CASE-88421?",
        "Draft an escalation email to the compliance team summarizing this case.",
        "Can you help me understand this customer's overall banking relationship?",  # ambiguous -> LLM fallback
    ]

    for query in example_queries:
        print(f"\nQuery: {query!r}")
        decision = router.route(query)
        print(f"  -> intent={decision.intent.value} | method={decision.method} | "
              f"sources={sorted(s.value for s in decision.sources_to_query)}")

    print("\n" + "=" * 70)
    print("Routing a DRAFTING request all the way through to a response")
    print("=" * 70)

    drafting_query = "Draft an escalation email to the compliance team summarizing this case."
    decision = router.route(drafting_query)

    if decision.intent == QueryIntent.DRAFTING:
        case_summary = (
            "Case CASE-88421: $14,200 wire transfer flagged due to a new beneficiary added 40 minutes "
            "before transfer, to a high-risk jurisdiction. Policy FR-14 requires a 48-hour hold pending "
            "secondary review. Customer's prior similar transfer (18 days ago) was confirmed legitimate "
            "after phone verification."
        )
        draft = handle_drafting_request(drafting_query, case_summary)
        print("\nDrafted email:\n")
        print(draft)
    else:
        print(f"(Routed to {decision.intent.value} instead of drafting -- unexpected for this example query)")
```

### Notes on running this yourself

- Run the batch of example queries and check that the first four hit the **deterministic** path (no LLM call) and only the last, genuinely ambiguous one falls through to the LLM classifier — that ratio (mostly deterministic, occasionally LLM) is exactly the cost profile this pattern is designed to produce.
- `with_structured_output` relies on the underlying model's tool-calling/JSON-mode support; the `try/except` fallback to `QueryIntent.GENERAL` (the broadest, safest fan-out) is a deliberate design choice — if classification fails for any reason, fail toward *more* context rather than less, since under-fetching risks a wrong or incomplete answer while over-fetching only costs efficiency.
- Notice that `DRAFTING` maps to an **empty** `sources_to_query` set in `ROUTING_TABLE` — that's intentional signaling that this intent doesn't go through source fan-out and Pattern 01's `Selection` at all; it goes straight to `handle_drafting_request`, which is a structurally different prompt template built around a case summary rather than ranked evidence.
- In a real Helios deployment, you'd track routing-decision accuracy over time (e.g., did a `CASE_STATUS`-routed query ever need to fall back to fetching more sources mid-response because the narrow routing missed something?) — that signal is what tells you whether your deterministic rules and routing table need tightening.

---

**Next up:** Pattern 10 — Context Summarization, where we return to the long-running-session problem from Pattern 06 and go further than fact-pinning: genuinely condensing extended history into a durable, faithful summary using an LLM.
