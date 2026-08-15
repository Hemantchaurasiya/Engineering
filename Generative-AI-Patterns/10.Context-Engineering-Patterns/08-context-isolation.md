# Pattern 08 — Context Isolation

[← Back to index](./README.md) | Previous: [Pattern 07 — Context Caching](./07-context-caching.md) | Next: Pattern 09 — Context Routing (queued)

---

## 1. Introduce the pattern

**Context Isolation** is the discipline of guaranteeing that context from one task, tenant, case, or sub-agent never leaks into another — even when they're running through the same code paths, the same caches, and the same underlying infrastructure.

Every pattern so far has assumed a single, well-defined scope: one case, one analyst, one session. Production systems rarely stay that simple — Helios serves many analysts across potentially multiple business units (retail banking fraud, commercial banking fraud) concurrently, and a sophisticated copilot may delegate sub-tasks to specialized sub-agents whose internal reasoning shouldn't automatically become part of the main conversation.

```
Analyst A, Case-88421 (Retail tenant)   Analyst B, Case-91002 (Commercial tenant)
        │                                        │
        ▼                                        ▼
  [ shared infrastructure: caches, embedding stores, sub-agent runners ]
        │                                        │
        ▼                                        ▼
   Case A's context                        Case B's context

[ ISOLATION ] is what guarantees these two paths never cross, regardless of
what shared infrastructure sits underneath them.
```

**Mental model:** a hospital's shared medical records system serves every patient through the same database, the same servers, the same application code — but a nurse pulling up Patient A's chart must never see so much as a fragment of Patient B's data, even by accident, even under load, even after a caching layer was added to make things faster. Isolation is the property that makes "shared infrastructure" safe to use for multiple independent clients — or independent sub-tasks — at all.

---

## 2. The problem it solves

Shared infrastructure is efficient (that's the whole point of Pattern 07's caching), but efficiency and isolation are in tension unless isolation is designed in deliberately:

1. **Cross-tenant data leakage.** A cache (like the one built in Pattern 07) that keys purely by content hash or query similarity has no concept of *who's asking* — if two different tenants happen to ask similarly-worded questions, a naively shared cache could serve one tenant's cached answer, built from another tenant's case data, to the wrong requester. In banking fraud operations, this isn't a quality bug, it's a data-protection incident.
2. **Sub-agent scratchpad contamination.** A sophisticated copilot that delegates work to specialized sub-agents (e.g., a transaction-pattern-analysis sub-agent, a compliance-policy-lookup sub-agent) risks letting each sub-agent's full internal reasoning — including intermediate, wrong, or overly verbose hypotheses — bleed into the parent conversation's context, polluting Ranking and Prioritization with noise the analyst never needed to see, or worse, carrying one case's sub-agent scratchpad into a different case's parent context if the runner isn't scoped correctly.
3. **No structural guarantee, only convention.** Without an explicit isolation boundary, "don't mix up case data" is just a hope enforced by careful coding at every call site — which reliably breaks down as a system grows, because it only takes one call site that forgot to pass the right case id.

Context Isolation solves this by making the isolation boundary an explicit, enforced object that every context-touching operation must go through — so a cross-tenant leak becomes a caught, loud exception rather than a silent, quiet bug.

---

## 3. A realistic enterprise problem (Helios)

Helios serves two business units through the same copilot infrastructure: **Retail Banking Fraud** and **Commercial Banking Fraud**. An analyst on the Retail team is investigating `CASE-88421`; a completely unrelated analyst on the Commercial team is investigating `CASE-91002` at the same time, on the same shared Helios instance, using the same underlying caching and embedding infrastructure from Pattern 07.

Two isolation requirements must both hold:

- **Tenant isolation**: nothing about `CASE-91002` (Commercial) may ever be visible to, cached for, or retrievable by a request scoped to `CASE-88421` (Retail), even if both cases happen to produce similar-looking cached queries.
- **Sub-agent isolation**: when the copilot delegates a sub-task — say, "analyze this customer's full transaction history for anomalies" — to a specialized sub-agent, that sub-agent's internal, multi-step reasoning (its own scratchpad of intermediate hypotheses) must not become part of the main conversation's context. Only its final, distilled finding should cross back into the parent case's context.

---

## 4. Architecture / flow diagram

```mermaid
flowchart TD
    A[Incoming request:\nanalyst_id, tenant_id, case_id] --> B[IsolationContext\n(the enforced boundary object)]

    B --> C{Access Control Check\ndoes tenant_id match\ncase_id's registered tenant?}
    C -- No --> D[🚫 AccessControlError\nlogged as security event]
    C -- Yes --> E[Tenant-Scoped Cache\n(Pattern 07's caches, key-prefixed\nby tenant_id:case_id)]

    E --> F[Main copilot reasoning]
    F --> G{Needs a sub-agent?\ne.g. transaction anomaly analysis}

    G -- No --> H[Direct response]
    G -- Yes --> I["Sub-Agent Isolation Runner\n(own isolated message state,\nscoped to the SAME IsolationContext)"]
    I --> J[Sub-agent's full scratchpad\n(internal reasoning, hypotheses)]
    J --> K[Distill to a structured,\nbounded finding ONLY]
    K --> L[Merge distilled finding\ninto parent context]
    L --> H

    style B fill:#4A90D9,color:#fff
    style D fill:#C0392B,color:#fff
    style I fill:#4A90D9,color:#fff
    style K fill:#4A90D9,color:#fff
```

---

## 5. The complete request-to-response flow

1. **Request arrives** carrying `analyst_id`, `tenant_id`, and `case_id` — these three together form the `IsolationContext` that every subsequent operation must be scoped to.
2. **Access control check**: before touching any case data, the pipeline verifies that `tenant_id` actually matches the tenant registered for `case_id` in a case registry. A mismatch — whether from a bug, a misconfigured client, or an actual attempted cross-tenant access — raises immediately and is logged as a security event, not silently ignored or "helpfully" corrected.
3. **Tenant-scoped cache access**: every cache lookup and write (reusing Pattern 07's `TTLLRUCache` machinery) is wrapped so its keys are always prefixed by `tenant_id:case_id`, making cross-tenant or cross-case collisions structurally impossible rather than merely unlikely — two different cases can ask byte-for-byte the same question and never share a cache entry.
4. **Main reasoning proceeds** within this scoped context as normal.
5. **Sub-agent delegation, when needed**: if the copilot needs a specialized sub-task done (e.g., deep transaction-pattern analysis), it's dispatched to a `SubAgentIsolationRunner` that executes with its **own, separate message state** — the sub-agent reasons over as many intermediate steps as it needs, but that reasoning lives entirely inside its own isolated state.
6. **Distillation, not merging**: when the sub-agent finishes, only a small, structured result (a finding, a confidence level, supporting evidence references) crosses back into the parent's context — the sub-agent's raw scratchpad is discarded by design, not filtered after the fact.
7. **Response assembly** proceeds using the parent context plus the distilled sub-agent finding, exactly as if it had been a normal context item all along.

---

## 6. Why this pattern is appropriate here

- **Structural enforcement beats convention.** The `IsolationContext` + tenant-scoped cache approach means a cross-tenant leak requires an actual bug in the isolation layer itself (a small, heavily-tested piece of code) rather than a mistake at any of the many call sites that touch context throughout Patterns 01–07. This is the same principle as Pattern 05's hard tiering boundary: some properties are too important to leave to a continuous score or a "remember to check this" convention.
- **Sub-agent distillation is a deliberate information bottleneck, not a limitation.** It would be technically easier to just merge a sub-agent's full message history into the parent's context — but that reintroduces exactly the noise-dilution problem Pattern 02 (Filtering) and Pattern 03 (Compression) exist to prevent, and does so specifically with content the analyst never asked to see. Forcing sub-agents to return a small, structured finding is what keeps a multi-agent Helios copilot's context budget from exploding as more sub-agents get added.
- **It composes with, rather than duplicates, Pattern 07's caching.** Context Isolation doesn't reinvent caching — it wraps the exact same cache primitives from Pattern 07 with a scoping layer. This is a good example of patterns in this series building on each other rather than each reimplementing infrastructure from scratch.
- **Fail loud, not quiet.** An access-control violation in a fraud-operations system is a security-relevant event, not a normal control-flow branch — raising a distinct, logged exception (rather than, say, silently returning an empty result) is what makes this kind of bug detectable in testing and auditable in production.

---

## 7. Production-quality Python implementation

```python
"""
helios_context_isolation.py

Pattern 08 — Context Isolation
Helios Fraud Investigation Copilot

Two isolation mechanisms:
1. IsolationContext + TenantScopedCache -- structurally prevents cross-tenant/
   cross-case cache collisions by prefixing every cache key, and enforces an
   access-control check against a case registry before any access.
2. SubAgentIsolationRunner -- runs a sub-agent in its own isolated message
   state and returns ONLY a distilled, structured finding to the parent
   context, discarding the sub-agent's raw scratchpad by design.

Requirements:
    pip install "langchain-core>=0.3.60" "langchain-ollama>=0.3.0"

Assumes a local Ollama server (`ollama serve`) with:
    ollama pull llama3.1
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Any

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_core.prompts import ChatPromptTemplate
from langchain_ollama import ChatOllama

logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")
logger = logging.getLogger("helios.context_isolation")
security_logger = logging.getLogger("helios.security_events")


# --------------------------------------------------------------------------- #
# Isolation boundary
# --------------------------------------------------------------------------- #

class AccessControlError(Exception):
    """Raised when a request attempts to access context outside its
    authorized tenant/case scope. Always a security-relevant event."""


@dataclass(frozen=True)
class IsolationContext:
    """Every context-touching operation in this pattern must be scoped to
    one of these. Immutable by design -- a context can't be silently
    widened partway through handling a request."""
    tenant_id: str
    case_id: str
    analyst_id: str

    @property
    def scope_key(self) -> str:
        return f"{self.tenant_id}:{self.case_id}"


# A stand-in for Helios's real case registry (would be a DB lookup in production)
CASE_TENANT_REGISTRY: dict[str, str] = {
    "CASE-88421": "retail-banking",
    "CASE-91002": "commercial-banking",
}


def enforce_access_control(ctx: IsolationContext) -> None:
    """Verifies the requesting tenant actually owns this case. Raises loudly
    on any mismatch -- never silently narrows or 'corrects' the request."""
    registered_tenant = CASE_TENANT_REGISTRY.get(ctx.case_id)

    if registered_tenant is None:
        security_logger.warning(
            "Access attempt for unknown case_id=%s by tenant=%s analyst=%s",
            ctx.case_id, ctx.tenant_id, ctx.analyst_id,
        )
        raise AccessControlError(f"Unknown case_id: {ctx.case_id}")

    if registered_tenant != ctx.tenant_id:
        security_logger.error(
            "CROSS-TENANT ACCESS ATTEMPT: tenant=%s analyst=%s tried to access "
            "case_id=%s which belongs to tenant=%s",
            ctx.tenant_id, ctx.analyst_id, ctx.case_id, registered_tenant,
        )
        raise AccessControlError(
            f"Tenant '{ctx.tenant_id}' is not authorized for case '{ctx.case_id}' "
            f"(registered tenant: '{registered_tenant}')"
        )

    logger.info("Access control OK: tenant=%s analyst=%s case=%s", ctx.tenant_id, ctx.analyst_id, ctx.case_id)


# --------------------------------------------------------------------------- #
# Tenant-scoped cache (wraps a plain dict here for a self-contained example;
# in production this wraps Pattern 07's TTLLRUCache / a Redis client)
# --------------------------------------------------------------------------- #

class TenantScopedCache:
    """Every key passed in is prefixed with the IsolationContext's scope_key
    before touching the backing store, so two different tenants/cases can
    never collide on a cache key even if they compute an identical raw key."""

    def __init__(self) -> None:
        self._store: dict[str, Any] = {}

    def get(self, ctx: IsolationContext, key: str) -> Any | None:
        enforce_access_control(ctx)
        return self._store.get(f"{ctx.scope_key}:{key}")

    def set(self, ctx: IsolationContext, key: str, value: Any) -> None:
        enforce_access_control(ctx)
        self._store[f"{ctx.scope_key}:{key}"] = value

    def dump_keys(self) -> list[str]:
        """For demonstration/audit purposes only -- shows the actual
        namespaced keys, proving cross-case entries never share a key."""
        return list(self._store.keys())


# --------------------------------------------------------------------------- #
# Sub-agent isolation: own message state in, distilled finding out
# --------------------------------------------------------------------------- #

@dataclass
class SubAgentFinding:
    """The ONLY thing that crosses back into the parent context. The
    sub-agent's full internal reasoning never leaves the runner."""
    summary: str
    confidence: str  # "low" | "medium" | "high"
    supporting_evidence: list[str] = field(default_factory=list)


SUB_AGENT_SYSTEM_PROMPT = """You are a specialized transaction-anomaly-analysis sub-agent.
You may reason step by step internally, but your FINAL line of output must be exactly one
line in this format (no other text after it):
FINDING | confidence=<low|medium|high> | summary=<one sentence> | evidence=<comma-separated short refs>"""


class SubAgentIsolationRunner:
    """Runs a sub-agent with its own isolated message list -- deliberately
    NOT sharing or appending to the parent conversation's message state.
    Only `run()`'s return value (a SubAgentFinding) is meant to cross the
    isolation boundary back to the caller."""

    def __init__(self, llm_model: str = "llama3.1") -> None:
        self.llm = ChatOllama(model=llm_model, temperature=0.1)

    def run(self, ctx: IsolationContext, task_description: str, raw_data: str) -> SubAgentFinding:
        # This sub-agent's own message list -- isolated, scoped only to this
        # single call, discarded once we've extracted the distilled finding.
        sub_agent_messages = [
            SystemMessage(content=SUB_AGENT_SYSTEM_PROMPT),
            HumanMessage(content=f"Task: {task_description}\n\nData:\n{raw_data}"),
        ]

        logger.info(
            "Sub-agent run started (isolated) | tenant=%s case=%s analyst=%s",
            ctx.tenant_id, ctx.case_id, ctx.analyst_id,
        )
        response = self.llm.invoke(sub_agent_messages)
        raw_output = response.content

        return self._distill(raw_output)

    @staticmethod
    def _distill(raw_output: str) -> SubAgentFinding:
        """Parses the sub-agent's constrained FINDING line. Everything else
        the sub-agent said internally is intentionally dropped here -- this
        is the isolation boundary between sub-agent scratchpad and parent context."""
        for line in raw_output.splitlines():
            if line.strip().upper().startswith("FINDING"):
                parts = {}
                for segment in line.split("|")[1:]:
                    if "=" in segment:
                        k, v = segment.split("=", 1)
                        parts[k.strip()] = v.strip()
                return SubAgentFinding(
                    summary=parts.get("summary", "(no summary provided)"),
                    confidence=parts.get("confidence", "low"),
                    supporting_evidence=[e.strip() for e in parts.get("evidence", "").split(",") if e.strip()],
                )

        # Defensive fallback if the sub-agent didn't follow the format --
        # never propagate raw, unstructured scratchpad content to the parent.
        logger.warning("Sub-agent did not return a parseable FINDING line; returning low-confidence fallback.")
        return SubAgentFinding(summary="Sub-agent analysis inconclusive.", confidence="low")


# --------------------------------------------------------------------------- #
# Example run
# --------------------------------------------------------------------------- #

if __name__ == "__main__":
    cache = TenantScopedCache()
    sub_agent_runner = SubAgentIsolationRunner()

    retail_ctx = IsolationContext(tenant_id="retail-banking", case_id="CASE-88421", analyst_id="analyst-A")
    commercial_ctx = IsolationContext(tenant_id="commercial-banking", case_id="CASE-91002", analyst_id="analyst-B")

    print("=== Normal, correctly-scoped cache writes ===")
    cache.set(retail_ctx, "context_block", "Retail case CASE-88421 context data...")
    cache.set(commercial_ctx, "context_block", "Commercial case CASE-91002 context data...")
    print("Namespaced keys in the backing store:", cache.dump_keys())

    print("\n=== Correctly-scoped read (should succeed) ===")
    print(cache.get(retail_ctx, "context_block"))

    print("\n=== Attempted CROSS-TENANT access (must raise AccessControlError) ===")
    forged_ctx = IsolationContext(tenant_id="retail-banking", case_id="CASE-91002", analyst_id="analyst-A")
    try:
        cache.get(forged_ctx, "context_block")
        print("!! THIS SHOULD NOT PRINT -- isolation was violated !!")
    except AccessControlError as e:
        print(f"Correctly blocked: {e}")

    print("\n=== Sub-agent isolation: delegate transaction-anomaly analysis ===")
    transaction_data = (
        "18d ago: wire $9,800 to new beneficiary, high-risk jurisdiction. "
        "2d ago: wire $1,200 to existing verified beneficiary, domestic. "
        "95d ago: wire $2,000 to existing beneficiary, domestic."
    )
    finding = sub_agent_runner.run(
        retail_ctx,
        task_description="Identify any anomalous transaction patterns for this customer.",
        raw_data=transaction_data,
    )

    print(f"Distilled finding returned to parent context:")
    print(f"  summary:    {finding.summary}")
    print(f"  confidence: {finding.confidence}")
    print(f"  evidence:   {finding.supporting_evidence}")
    print("\n(Note: the sub-agent's own internal reasoning steps, if any, never")
    print(" left the SubAgentIsolationRunner -- only this structured finding did.)")

    # The distilled finding now becomes a normal context item for the main
    # copilot response, exactly like any other Pattern-01-style ContextItem.
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", "You are the Helios Fraud Investigation Copilot. Answer using ONLY the context given."),
            ("human", "Analyst question: {query}\n\n--- CONTEXT (including sub-agent finding) ---\n{context}"),
        ]
    )
    context_block = (
        f"[case_metadata] Case CASE-88421: $14,200 wire to a new beneficiary in a high-risk jurisdiction.\n\n"
        f"[sub_agent_finding | confidence={finding.confidence}] {finding.summary} "
        f"(evidence: {', '.join(finding.supporting_evidence) or 'none listed'})"
    )
    llm = ChatOllama(model="llama3.1", temperature=0.1)
    chain = prompt | llm
    response = chain.invoke(
        {"query": "Does this customer's transaction pattern look anomalous?", "context": context_block}
    )

    print("\n=== COPILOT ANSWER (grounded in isolated, distilled sub-agent output) ===")
    print(response.content)
```

### Notes on running this yourself

- The cross-tenant access attempt in the example (`forged_ctx`) is deliberately constructed to show the failure mode this pattern exists to prevent — confirm for yourself that it raises `AccessControlError` and never reaches the cache's backing store.
- `cache.dump_keys()` is included purely for demonstration: in production you would never expose raw cache keys across tenants, but seeing the namespaced keys (`retail-banking:CASE-88421:context_block` vs. `commercial-banking:CASE-91002:context_block`) makes the isolation mechanism concrete rather than abstract.
- `SubAgentIsolationRunner._distill` is the actual isolation boundary for sub-agents — note that it discards the raw LLM output entirely except for the one structured `FINDING` line. If a sub-agent's prompt doesn't reliably produce that format, the defensive fallback (`confidence="low"`) is what keeps a parsing failure from ever accidentally leaking raw scratchpad text into the parent context.
- In a real multi-instance Helios deployment, `TenantScopedCache` would wrap a shared backing store (Redis, etc.) rather than a local dict — the isolation logic (prefixing, access control) stays identical regardless of what's underneath it, which is exactly the point of building it as a wrapper around Pattern 07's cache interface rather than as a one-off.

---

**Next up:** Pattern 09 — Context Routing, where we address a related but distinct question: not "how do I keep contexts separate" but "given a query, which context source or sub-system should even handle it in the first place."
