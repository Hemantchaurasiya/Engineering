# 4. Branching

## 4.1 What is it?

**Branching** is a **decision tree** — multiple decision points chained
together, where the outcome of one decision determines *which further
decision* gets made next. It's the natural extension of Pattern 3
(Conditional Workflow), which had exactly **one** decision point with several
possible destinations. Branching has **more than one** decision point, nested
inside each other, forming a tree instead of a single flat switch.

Think of it like a phone menu: "Press 1 for Sales, 2 for Support." If you
press 2, you get a *second* menu: "Press 1 for Billing issues, 2 for
Technical issues." Each level narrows things down further, and different
branches can have a different number of sub-levels.

## 4.2 What problem does it solve?

A single decision is often not enough to route something correctly. Real
categorization usually happens in **layers**: first a broad category, then
something more specific within that category, and the "something more
specific" question is often completely different depending on which broad
category you're in (billing questions get asked about refund amounts;
technical questions get asked about severity — those aren't the same
question).

The Branching pattern solves this by:

- Letting each branch **ask its own follow-up question**, instead of forcing
  one giant decision point to somehow account for every combination up
  front.
- Keeping each individual decision **simple and narrow** (a 2–3 way choice),
  rather than one sprawling classifier trying to output 10+ combined
  categories directly.
- Making it easy to **change one branch's sub-routing** without touching
  sibling branches at all — the technical team can change how severity is
  decided without affecting how billing is decided.
- Mixing **LLM-based decisions and plain rule-based decisions** naturally at
  different levels of the same tree, using whichever is more appropriate for
  that particular question.

## 4.3 Realistic production example: Support Ticket Intelligent Routing

A SaaS company (`GridWorks`) receives support tickets that need to reach the
right queue. A single flat classifier isn't enough here, because "what do we
ask next" is different for each category:

**Level 1 — Category** (LLM classification): `billing`, `technical`, or
`account`.

- If **`billing`** → **Level 2 — Refund amount** (plain rule, no LLM
  needed): if the disputed amount is over $500, route to a human
  **Billing Specialist**; otherwise route to the automated **Refund Bot**
  (fast, self-service resolution for small amounts).
- If **`technical`** → **Level 2 — Severity** (LLM classification): if the
  ticket describes a critical outage (`"down"`, `"can't log in"`,
  `"data loss"`), route to the **On-Call Engineer** queue immediately;
  otherwise route to the **Standard Technical Queue**.
- If **`account`** → no further branching needed; route straight to the
  **Account Team**.

Notice the tree isn't symmetric: `billing` and `technical` each split again,
`account` doesn't. That's normal and expected for Branching — different
branches can have different depths.

## 4.4 Architecture / Flow Diagram

```mermaid
flowchart TD
    Start([Ticket Received]) --> L1[Classify Category - LLM]

    L1 -->|billing| L2B{Refund amount over $500?}
    L1 -->|technical| L2T[Classify Severity - LLM]
    L1 -->|account| AccountTeam[Route: Account Team]

    L2B -->|yes| BillingSpecialist[Route: Billing Specialist]
    L2B -->|no| RefundBot[Route: Refund Bot]

    L2T -->|critical| OnCall[Route: On-Call Engineer]
    L2T -->|normal| StdQueue[Route: Standard Tech Queue]

    BillingSpecialist --> End([Ticket Routed])
    RefundBot --> End
    OnCall --> End
    StdQueue --> End
    AccountTeam --> End

    style L1 fill:#FDE9C8,stroke:#F59E0B
    style L2T fill:#FDE9C8,stroke:#F59E0B
    style L2B fill:#F3E8FF,stroke:#8B5CF6
```

Two separate decision points exist here (`L1` and, on two of the three
branches, a second-level decision) — that's the tree shape that distinguishes
Branching from Pattern 3's single flat switch.

## 4.5 Request-to-response flow, step by step

1. A client sends the raw ticket text to `run_ticket_routing()`.
2. LangGraph builds the initial `TicketState` and enters at
   `classify_category` — **Level 1** of the tree.
3. **`classify_category`** calls the local Ollama model, parsing the reply
   into exactly one of `"billing"`, `"technical"`, `"account"` (defaulting to
   `"account"` — the safest, simplest queue — if the response is unclear).
4. A **conditional edge** on `classify_category` reads `state.category` and
   routes to one of three next nodes: `check_refund_amount`,
   `classify_severity`, or `route_to_account_team`.
5. **If `billing`:** `check_refund_amount` runs — this is a **plain Python
   rule**, not an LLM call, because "is this number over 500" doesn't need a
   model. It sets `state.needs_specialist`. A **second conditional edge**
   then routes to `route_to_billing_specialist` or `route_to_refund_bot`
   based on that boolean.
6. **If `technical`:** `classify_severity` runs — this *is* an LLM call,
   because "does this description sound like a critical outage" is a
   judgment call suited to a model. A second conditional edge routes to
   `route_to_oncall` or `route_to_standard_queue` based on the result.
7. **If `account`:** `route_to_account_team` runs directly — no second
   decision needed for this branch.
8. Whichever leaf node ran writes `state.routing_decision` and
   `state.routing_reason`. All leaves converge to `END`, so the caller always
   gets the same result shape back.

## 4.6 Why this pattern fits this problem

- The **follow-up question genuinely differs by category** — asking a
  billing ticket about "severity" or a technical ticket about "refund
  amount" wouldn't make sense. Branching lets each path define its own
  relevant next question.
- It lets us **use the cheapest adequate tool at each decision** — a fast,
  free `if amount > 500` check for billing, and a real LLM call only where
  judgment is actually needed (severity, category). A flat single-classifier
  design would be tempted to just ask the LLM everything, which is slower and
  costs more for questions that don't need it.
- The tree structure means **adding a new sub-branch later is localized** —
  e.g., splitting `account` into `account: upgrade` vs `account: cancellation`
  only touches that one branch, not the whole graph.
- It mirrors how **humans already triage support tickets** (broad category
  first, specifics second), which makes the system's behavior easy for a
  support team to understand and trust.

## 4.7 Production-quality implementation

```python
"""
Branching — Support Ticket Intelligent Routing
Pattern: nested decision points forming a tree (not just one flat switch)

Run:
    pip install "langgraph==1.2.10" "langchain==1.3.14" "langchain-ollama==1.1.0" "pydantic==2.9.2"
    ollama pull llama3.1:8b     # make sure Ollama is running locally
    python ticket_routing.py
"""

from __future__ import annotations

import logging
import re
from typing import Literal, Optional

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
logger = logging.getLogger("ticket_routing")


# --------------------------------------------------------------------------
# 2. Shared state
# --------------------------------------------------------------------------
class TicketState(BaseModel):
    ticket_text: str = ""
    disputed_amount: float = 0.0  # only relevant for billing tickets

    category: Optional[Literal["billing", "technical", "account"]] = None
    needs_specialist: Optional[bool] = None  # billing branch, level 2
    severity: Optional[Literal["critical", "normal"]] = None  # technical branch, level 2

    routing_decision: Optional[str] = None
    routing_reason: Optional[str] = None


# --------------------------------------------------------------------------
# 3. Local model client
# --------------------------------------------------------------------------
_llm = ChatOllama(model="llama3.1:8b", temperature=0)


# --------------------------------------------------------------------------
# 4. Level 1 — Classify Category (LLM)
# --------------------------------------------------------------------------
_CATEGORY_PROMPT = """Classify this support ticket into exactly one category:
billing, technical, or account. Respond with ONLY that one word.

Ticket: {ticket_text}
"""


def classify_category(state: TicketState) -> dict:
    logger.info("LEVEL 1 — classify_category")
    try:
        response = _llm.invoke(_CATEGORY_PROMPT.format(ticket_text=state.ticket_text))
        word = response.content.strip().lower()
        if "billing" in word:
            category = "billing"
        elif "technical" in word:
            category = "technical"
        else:
            category = "account"
        return {"category": category}
    except Exception as exc:  # noqa: BLE001
        logger.error("classify_category failed, defaulting to account: %s", exc)
        return {"category": "account"}


def route_by_category(state: TicketState) -> str:
    return {
        "billing": "check_refund_amount",
        "technical": "classify_severity",
        "account": "route_to_account_team",
    }[state.category]


# --------------------------------------------------------------------------
# 5a. Billing branch, Level 2 — plain rule, no LLM needed
# --------------------------------------------------------------------------
def check_refund_amount(state: TicketState) -> dict:
    logger.info("LEVEL 2 (billing) — check_refund_amount: $%.2f", state.disputed_amount)
    return {"needs_specialist": state.disputed_amount > 500}


def route_by_refund_amount(state: TicketState) -> str:
    return "route_to_billing_specialist" if state.needs_specialist else "route_to_refund_bot"


def route_to_billing_specialist(state: TicketState) -> dict:
    return {
        "routing_decision": "Billing Specialist",
        "routing_reason": f"Disputed amount ${state.disputed_amount:,.2f} exceeds $500 threshold.",
    }


def route_to_refund_bot(state: TicketState) -> dict:
    return {
        "routing_decision": "Refund Bot (automated)",
        "routing_reason": f"Disputed amount ${state.disputed_amount:,.2f} is within self-service limit.",
    }


# --------------------------------------------------------------------------
# 5b. Technical branch, Level 2 — LLM judgment call
# --------------------------------------------------------------------------
_SEVERITY_PROMPT = """Does this technical support ticket describe a CRITICAL
outage (site down, can't log in at all, data loss)? Respond with ONLY one
word: critical or normal.

Ticket: {ticket_text}
"""


def classify_severity(state: TicketState) -> dict:
    logger.info("LEVEL 2 (technical) — classify_severity")
    try:
        response = _llm.invoke(_SEVERITY_PROMPT.format(ticket_text=state.ticket_text))
        word = response.content.strip().lower()
        severity = "critical" if "critical" in word else "normal"
        return {"severity": severity}
    except Exception as exc:  # noqa: BLE001
        logger.error("classify_severity failed, defaulting to critical (safer): %s", exc)
        # When unsure, treat as critical -> gets a human's eyes sooner rather than later.
        return {"severity": "critical"}


def route_by_severity(state: TicketState) -> str:
    return "route_to_oncall" if state.severity == "critical" else "route_to_standard_queue"


def route_to_oncall(state: TicketState) -> dict:
    return {
        "routing_decision": "On-Call Engineer",
        "routing_reason": "Ticket describes a critical outage.",
    }


def route_to_standard_queue(state: TicketState) -> dict:
    return {
        "routing_decision": "Standard Technical Queue",
        "routing_reason": "Ticket is technical but not a critical outage.",
    }


# --------------------------------------------------------------------------
# 5c. Account branch — no Level 2, routes directly
# --------------------------------------------------------------------------
def route_to_account_team(state: TicketState) -> dict:
    return {
        "routing_decision": "Account Team",
        "routing_reason": "Ticket classified as an account-related request.",
    }


# --------------------------------------------------------------------------
# 6. Build the graph — a tree of conditional edges, not just one.
# --------------------------------------------------------------------------
def build_graph():
    graph = StateGraph(TicketState)

    graph.add_node("classify_category", classify_category)
    graph.add_node("check_refund_amount", check_refund_amount)
    graph.add_node("classify_severity", classify_severity)
    graph.add_node("route_to_billing_specialist", route_to_billing_specialist)
    graph.add_node("route_to_refund_bot", route_to_refund_bot)
    graph.add_node("route_to_oncall", route_to_oncall)
    graph.add_node("route_to_standard_queue", route_to_standard_queue)
    graph.add_node("route_to_account_team", route_to_account_team)

    graph.add_edge(START, "classify_category")

    # Level 1 decision: 3-way split
    graph.add_conditional_edges(
        "classify_category",
        route_by_category,
        {
            "check_refund_amount": "check_refund_amount",
            "classify_severity": "classify_severity",
            "route_to_account_team": "route_to_account_team",
        },
    )

    # Level 2 decision on the billing branch
    graph.add_conditional_edges(
        "check_refund_amount",
        route_by_refund_amount,
        {
            "route_to_billing_specialist": "route_to_billing_specialist",
            "route_to_refund_bot": "route_to_refund_bot",
        },
    )

    # Level 2 decision on the technical branch
    graph.add_conditional_edges(
        "classify_severity",
        route_by_severity,
        {
            "route_to_oncall": "route_to_oncall",
            "route_to_standard_queue": "route_to_standard_queue",
        },
    )

    # All leaf nodes converge to END
    for leaf in (
        "route_to_billing_specialist",
        "route_to_refund_bot",
        "route_to_oncall",
        "route_to_standard_queue",
        "route_to_account_team",
    ):
        graph.add_edge(leaf, END)

    return graph.compile()


# --------------------------------------------------------------------------
# 7. Public entry point
# --------------------------------------------------------------------------
def run_ticket_routing(ticket_text: str, disputed_amount: float = 0.0) -> dict:
    app = build_graph()
    initial_state = TicketState(ticket_text=ticket_text, disputed_amount=disputed_amount)
    final_state = app.invoke(initial_state)
    return final_state


# --------------------------------------------------------------------------
# 8. Demo
# --------------------------------------------------------------------------
if __name__ == "__main__":
    examples = [
        ("I was charged twice for my subscription, please refund $45.", 45.0, {}),
        ("I need a $1,200 refund, this charge is completely wrong.", 1200.0, {}),
        ("The entire site is down and I can't log in at all!", 0.0, {}),
        ("The export button is a bit slow sometimes, minor annoyance.", 0.0, {}),
        ("I want to upgrade my plan to the enterprise tier.", 0.0, {}),
    ]

    for ticket_text, amount, _ in examples:
        result = run_ticket_routing(ticket_text, amount)
        print(f"-> {result['routing_decision']}: {result['routing_reason']}")
```

**Notes on production-readiness choices made above:**

- **Two separate `add_conditional_edges` calls, not one** — this is what
  actually creates the tree shape. Each decision point is its own small,
  independently testable routing function (`route_by_category`,
  `route_by_refund_amount`, `route_by_severity`).
- **Mixing an LLM decision with a plain rule** in the same tree
  (`check_refund_amount` is pure Python, `classify_severity` calls the
  model) — a good branching design uses a model only where judgment is
  actually required, and cheap deterministic code everywhere else.
- **Different, sensible default directions on failure** — category defaults
  to the least disruptive option (`account`); severity defaults to the
  *safer* option (`critical`, so a possibly-serious issue doesn't
  accidentally sit in a slower queue). The right default depends on which
  mistake is cheaper for that specific branch.
- **Every leaf node returns the same two fields**
  (`routing_decision`, `routing_reason`) — even though the tree has different
  depths on different branches, the caller always gets a consistent, simple
  result shape back.

---

⬅ [3. Conditional Workflow](03-conditional-workflow.md) | [Back to index](README.md) | Next: [5. Routing](05-routing.md) ➡

# Support Ticket Intelligent Routing — Branching Workflow (Java + Spring AI)

A Java port of the LangGraph nested-branching workflow — a *tree* of conditional edges rather
than one flat switch (`classify_category` splits 3 ways, and two of those three branches split
again at level 2). Built on:

- **Java 25** (current LTS) — nested `sealed` interfaces + exhaustive `switch` for the decision tree
- **Spring Boot 4.1.0**
- **Spring AI 2.0.0** (GA) with the **Ollama** starter, mirroring `llama3.1:8b`

## Mapping the shape

The Python version has *three* separate `add_conditional_edges` calls, each with its own routing
function and dict — one for the top-level category split, and one more for each of the two
branches (billing, technical) that need a second decision. Java expresses this the same way it
expressed the single-level conditional workflow, just nested: each level is its own small
`sealed` hierarchy with its own exhaustive `switch`.

| LangGraph concept | Spring / Java equivalent |
|---|---|
| `route_by_category` (level 1, 3-way) | `switch (category)` in `TicketRoutingService.route(...)` |
| `route_by_refund_amount` (level 2, billing) | `switch` inside `BillingBranch.route(...)` |
| `route_by_severity` (level 2, technical) | `switch` inside `TechnicalBranch.route(...)` |
| `route_to_account_team` (no level 2) | `AccountBranch.route(...)` returns directly, no nested switch |
| All 5 leaf nodes → `END` | All leaf methods return the same `TicketState` shape |

Each level of the tree gets its own enum + exhaustive `switch`, so — as with the flat conditional
workflow — the compiler rejects an unhandled category or severity value rather than that only
surfacing when the graph is built or run.

---

## Project structure

```
ticket-routing/
├── pom.xml
└── src/main/java/com/example/ticketrouting/
    ├── TicketRoutingApplication.java
    ├── model/
    │   ├── Category.java
    │   ├── Severity.java
    │   ├── Ticket.java
    │   ├── TicketState.java
    │   └── RoutingResult.java
    ├── pipeline/
    │   ├── CategoryClassificationStep.java
    │   ├── BillingBranch.java
    │   ├── TechnicalBranch.java
    │   ├── AccountBranch.java
    │   └── TicketRoutingService.java
    ├── web/
    │   └── TicketRoutingController.java
    └── TicketRoutingRunner.java   (CLI demo, mirrors the Python __main__ block)
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
    <artifactId>ticket-routing</artifactId>
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
    name: ticket-routing
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: llama3.1:8b
          temperature: 0.0

logging:
  level:
    com.example.ticketrouting: INFO
```

---

## Domain model

### `model/Category.java`

```java
package com.example.ticketrouting.model;

public enum Category {
    BILLING,
    TECHNICAL,
    ACCOUNT
}
```

### `model/Severity.java`

```java
package com.example.ticketrouting.model;

public enum Severity {
    CRITICAL,
    NORMAL
}
```

### `model/Ticket.java`

```java
package com.example.ticketrouting.model;

public record Ticket(String ticketText, double disputedAmount) {

    public Ticket(String ticketText) {
        this(ticketText, 0.0);
    }
}
```

### `model/RoutingResult.java`

```java
package com.example.ticketrouting.model;

public record RoutingResult(String routingDecision, String routingReason) {}
```

### `model/TicketState.java`

```java
package com.example.ticketrouting.model;

public record TicketState(
        Ticket ticket,
        Category category,
        Boolean needsSpecialist,   // billing branch, level 2
        Severity severity,         // technical branch, level 2
        RoutingResult routingResult
) {

    public static TicketState initial(Ticket ticket) {
        return new TicketState(ticket, null, null, null, null);
    }

    public TicketState withCategory(Category category) {
        return new TicketState(ticket, category, needsSpecialist, severity, routingResult);
    }

    public TicketState withNeedsSpecialist(boolean needsSpecialist) {
        return new TicketState(ticket, category, needsSpecialist, severity, routingResult);
    }

    public TicketState withSeverity(Severity severity) {
        return new TicketState(ticket, category, needsSpecialist, severity, routingResult);
    }

    public TicketState withRoutingResult(String decision, String reason) {
        return new TicketState(ticket, category, needsSpecialist, severity,
                new RoutingResult(decision, reason));
    }
}
```

---

## Level 1 — category classification

### `pipeline/CategoryClassificationStep.java`

```java
package com.example.ticketrouting.pipeline;

import com.example.ticketrouting.model.Category;
import com.example.ticketrouting.model.TicketState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class CategoryClassificationStep {

    private static final Logger log = LoggerFactory.getLogger(CategoryClassificationStep.class);

    private static final String CATEGORY_PROMPT = """
            Classify this support ticket into exactly one category:
            billing, technical, or account. Respond with ONLY that one word.

            Ticket: {ticketText}
            """;

    private final ChatClient chatClient;

    public CategoryClassificationStep(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public TicketState apply(TicketState state) {
        log.info("LEVEL 1 - classify_category");
        try {
            String word = chatClient.prompt()
                    .user(u -> u.text(CATEGORY_PROMPT).param("ticketText", state.ticket().ticketText()))
                    .call()
                    .content()
                    .strip()
                    .toLowerCase(Locale.ROOT);

            Category category;
            if (word.contains("billing")) {
                category = Category.BILLING;
            } else if (word.contains("technical")) {
                category = Category.TECHNICAL;
            } else {
                category = Category.ACCOUNT;
            }
            return state.withCategory(category);
        } catch (Exception e) {
            log.error("classify_category failed, defaulting to account: {}", e.getMessage());
            return state.withCategory(Category.ACCOUNT);
        }
    }
}
```

---

## Level 2 branches

### `pipeline/BillingBranch.java`

The billing branch's own level-2 decision (`check_refund_amount` -> `route_by_refund_amount`) —
a plain rule, no LLM call, same as the Python version.

```java
package com.example.ticketrouting.pipeline;

import com.example.ticketrouting.model.TicketState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class BillingBranch {

    private static final Logger log = LoggerFactory.getLogger(BillingBranch.class);
    private static final double SPECIALIST_THRESHOLD = 500.0;

    public TicketState route(TicketState state) {
        double disputedAmount = state.ticket().disputedAmount();
        log.info("LEVEL 2 (billing) - check_refund_amount: ${}", String.format(Locale.US, "%.2f", disputedAmount));

        boolean needsSpecialist = disputedAmount > SPECIALIST_THRESHOLD;
        TicketState withFlag = state.withNeedsSpecialist(needsSpecialist);

        return needsSpecialist
                ? routeToBillingSpecialist(withFlag, disputedAmount)
                : routeToRefundBot(withFlag, disputedAmount);
    }

    private TicketState routeToBillingSpecialist(TicketState state, double disputedAmount) {
        String reason = "Disputed amount $%,.2f exceeds $500 threshold.".formatted(disputedAmount);
        return state.withRoutingResult("Billing Specialist", reason);
    }

    private TicketState routeToRefundBot(TicketState state, double disputedAmount) {
        String reason = "Disputed amount $%,.2f is within self-service limit.".formatted(disputedAmount);
        return state.withRoutingResult("Refund Bot (automated)", reason);
    }
}
```

### `pipeline/TechnicalBranch.java`

The technical branch's own level-2 decision (`classify_severity` -> `route_by_severity`) — this
one *does* need an LLM judgment call.

```java
package com.example.ticketrouting.pipeline;

import com.example.ticketrouting.model.Severity;
import com.example.ticketrouting.model.TicketState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class TechnicalBranch {

    private static final Logger log = LoggerFactory.getLogger(TechnicalBranch.class);

    private static final String SEVERITY_PROMPT = """
            Does this technical support ticket describe a CRITICAL outage (site down,
            can't log in at all, data loss)? Respond with ONLY one word: critical or normal.

            Ticket: {ticketText}
            """;

    private final ChatClient chatClient;

    public TechnicalBranch(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public TicketState route(TicketState state) {
        log.info("LEVEL 2 (technical) - classify_severity");
        Severity severity = classifySeverity(state);
        TicketState withSeverity = state.withSeverity(severity);

        return switch (severity) {
            case CRITICAL -> withSeverity.withRoutingResult(
                    "On-Call Engineer", "Ticket describes a critical outage.");
            case NORMAL -> withSeverity.withRoutingResult(
                    "Standard Technical Queue", "Ticket is technical but not a critical outage.");
        };
    }

    private Severity classifySeverity(TicketState state) {
        try {
            String word = chatClient.prompt()
                    .user(u -> u.text(SEVERITY_PROMPT).param("ticketText", state.ticket().ticketText()))
                    .call()
                    .content()
                    .strip()
                    .toLowerCase(Locale.ROOT);
            return word.contains("critical") ? Severity.CRITICAL : Severity.NORMAL;
        } catch (Exception e) {
            // When unsure, treat as CRITICAL -> gets a human's eyes sooner rather than later.
            log.error("classify_severity failed, defaulting to critical (safer): {}", e.getMessage());
            return Severity.CRITICAL;
        }
    }
}
```

### `pipeline/AccountBranch.java`

The account branch has no level 2 — it routes directly, same as the Python version.

```java
package com.example.ticketrouting.pipeline;

import com.example.ticketrouting.model.TicketState;
import org.springframework.stereotype.Component;

@Component
public class AccountBranch {

    public TicketState route(TicketState state) {
        return state.withRoutingResult(
                "Account Team", "Ticket classified as an account-related request.");
    }
}
```

---

## The tree router

### `pipeline/TicketRoutingService.java`

The top-level `switch (category)` is the level-1 conditional edge; each branch then makes its own
(possibly nested) decision internally.

```java
package com.example.ticketrouting.pipeline;

import com.example.ticketrouting.model.Ticket;
import com.example.ticketrouting.model.TicketState;
import org.springframework.stereotype.Service;

@Service
public class TicketRoutingService {

    private final CategoryClassificationStep categoryClassificationStep;
    private final BillingBranch billingBranch;
    private final TechnicalBranch technicalBranch;
    private final AccountBranch accountBranch;

    public TicketRoutingService(CategoryClassificationStep categoryClassificationStep,
                                 BillingBranch billingBranch,
                                 TechnicalBranch technicalBranch,
                                 AccountBranch accountBranch) {
        this.categoryClassificationStep = categoryClassificationStep;
        this.billingBranch = billingBranch;
        this.technicalBranch = technicalBranch;
        this.accountBranch = accountBranch;
    }

    public TicketState route(Ticket ticket) {
        TicketState afterClassification = categoryClassificationStep.apply(TicketState.initial(ticket));

        // Level 1 conditional edge: 3-way split by category.
        // Two of the three branches make their own level-2 decision internally
        // (BillingBranch and TechnicalBranch each contain their own exhaustive switch),
        // mirroring the two nested add_conditional_edges calls in the Python graph.
        return switch (afterClassification.category()) {
            case BILLING -> billingBranch.route(afterClassification);
            case TECHNICAL -> technicalBranch.route(afterClassification);
            case ACCOUNT -> accountBranch.route(afterClassification);
        };
    }
}
```

---

## Entry points

### `web/TicketRoutingController.java`

```java
package com.example.ticketrouting.web;

import com.example.ticketrouting.model.Ticket;
import com.example.ticketrouting.model.TicketState;
import com.example.ticketrouting.pipeline.TicketRoutingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TicketRoutingController {

    private final TicketRoutingService ticketRoutingService;

    public TicketRoutingController(TicketRoutingService ticketRoutingService) {
        this.ticketRoutingService = ticketRoutingService;
    }

    @PostMapping("/api/tickets/route")
    public TicketState route(@RequestBody Ticket ticket) {
        return ticketRoutingService.route(ticket);
    }
}
```

### `TicketRoutingRunner.java` (CLI demo, mirrors the Python `if __name__ == "__main__"` block)

```java
package com.example.ticketrouting;

import com.example.ticketrouting.model.Ticket;
import com.example.ticketrouting.pipeline.TicketRoutingService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("demo")
public class TicketRoutingRunner implements CommandLineRunner {

    private final TicketRoutingService ticketRoutingService;

    public TicketRoutingRunner(TicketRoutingService ticketRoutingService) {
        this.ticketRoutingService = ticketRoutingService;
    }

    @Override
    public void run(String... args) {
        List<Ticket> examples = List.of(
                new Ticket("I was charged twice for my subscription, please refund $45.", 45.0),
                new Ticket("I need a $1,200 refund, this charge is completely wrong.", 1200.0),
                new Ticket("The entire site is down and I can't log in at all!", 0.0),
                new Ticket("The export button is a bit slow sometimes, minor annoyance.", 0.0),
                new Ticket("I want to upgrade my plan to the enterprise tier.", 0.0)
        );

        for (Ticket ticket : examples) {
            var result = ticketRoutingService.route(ticket);
            var routing = result.routingResult();
            System.out.printf("-> %s: %s%n", routing.routingDecision(), routing.routingReason());
        }
    }
}
```

### `TicketRoutingApplication.java`

```java
package com.example.ticketrouting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TicketRoutingApplication {
    public static void main(String[] args) {
        SpringApplication.run(TicketRoutingApplication.class, args);
    }
}
```

---

## Running it

```bash
ollama pull llama3.1:8b
ollama serve   # if not already running

# CLI demo (prints all 5 example routings, like the Python script)
mvn spring-boot:run -Dspring-boot.run.profiles=demo

# Or as a service
mvn spring-boot:run
curl -X POST localhost:8080/api/tickets/route \
  -H "Content-Type: application/json" \
  -d '{"ticketText":"The entire site is down and I cannot log in at all!","disputedAmount":0}'
```

## Notes on the port

- **Tree of switches, not one switch**: the structural point of this workflow — nested decision
  points, not just one flat routing table — comes through as one `switch` per level, each living
  in the component responsible for that level (`TicketRoutingService` for level 1,
  `BillingBranch`/`TechnicalBranch` for level 2). This keeps each branch's internal decision
  private to that branch, the same encapsulation the Python version gets from splitting
  `route_by_refund_amount` and `route_by_severity` into their own functions.
- **Both safety defaults preserved exactly**: an unclear category defaults to `ACCOUNT` (the
  least action-triggering outcome), and an unclear severity defaults to `CRITICAL` (the
  fail-safe-toward-a-human outcome) — same asymmetry as the Python version, kept intentionally
  rather than "cleaned up" to be symmetric.
- **Billing branch stays LLM-free**: `BillingBranch.route` is a plain threshold check with no
  `ChatClient` call, matching the Python `check_refund_amount`'s comment that it needs no LLM.
- **Versions**: same Ollama-backed `spring-ai-starter-model-ollama` / `llama3.1:8b` setup as the
  other two branching/parallel ports in this series.
