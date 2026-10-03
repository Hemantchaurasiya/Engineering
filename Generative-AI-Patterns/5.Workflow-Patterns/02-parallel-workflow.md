# 2. Parallel Workflow

## 2.1 What is it?

A **Parallel Workflow** runs multiple independent steps **at the same time**
instead of one after another, then waits for all of them to finish before
moving on. This is often called a **fan-out / join** shape: one starting
point "fans out" into several branches that run concurrently, and a final
node "joins" their results back together.

The key difference from Sequential: the branches **don't need each other's
output** to do their work. Step B doesn't wait for Step A — they both start
from the same input and run side by side.

## 2.2 What problem does it solve?

Many real pipelines have several **independent analyses** that all need to
happen on the same input before you can produce a final result. If you run
them one after another, your total time is the *sum* of every step. If
nothing actually depends on anything else, that's wasted time — especially
when each step is a slow network or LLM call.

The Parallel Workflow pattern solves this by:

- **Cutting total latency** — total time becomes roughly the time of the
  *slowest* branch, not the sum of all branches.
- Keeping each branch **simple and single-purpose**, same as Sequential —
  parallelism doesn't have to make code more complicated.
- Making it obvious in the graph **which steps are genuinely independent**,
  which is useful documentation on its own.
- Giving you one clear place (the join node) to **combine results** and
  decide what to do if one branch fails while others succeed.

## 2.3 Realistic production example: Contract Review Assistant

A legal-tech company (`ClauseIQ`) helps in-house legal teams review incoming
vendor contracts quickly. When a contract PDF's text comes in, three
completely independent analyses need to run on it:

1. **Risk Clause Detection** — find clauses that are unusually risky
   (liability caps, auto-renewal traps, one-sided termination rights).
2. **Compliance Check** — check the contract against a standard compliance
   checklist (data protection / GDPR mentions, confidentiality terms).
3. **Financial Terms Extraction** — pull out payment amounts, currency,
   payment schedule, and penalty terms.

None of these three analyses need each other's output — they all just need
the contract text. Today, if run one after another, each LLM call might take
5–8 seconds, so reviewing one contract sequentially takes ~20 seconds. Run in
parallel, it takes about as long as the *slowest single* analysis — usually
5–8 seconds — a ~3x speedup with zero extra infrastructure.

A fourth step then **merges** the three results into one structured contract
review report for the legal team.

## 2.4 Architecture / Flow Diagram

```mermaid
flowchart TD
    Start([Contract Text Received]) --> Fork{ }
    Fork --> A[Risk Clause Detection - LLM]
    Fork --> B[Compliance Check - LLM]
    Fork --> C[Financial Terms Extraction - LLM]
    A --> Join[Merge Results]
    B --> Join
    C --> Join
    Join --> End([Consolidated Contract Review Report])

    style A fill:#FDE9C8,stroke:#F59E0B
    style B fill:#FDE9C8,stroke:#F59E0B
    style C fill:#FDE9C8,stroke:#F59E0B
    style Join fill:#DCEEFB,stroke:#3B82F6
```

All three boxes on the middle row start from the **same** node (`START`) and
all flow into the **same** join node (`merge_results`) — that fan-out /
fan-in shape is what makes this a Parallel Workflow.

## 2.5 Request-to-response flow, step by step

1. A client sends the extracted contract text to `run_contract_review()`.
2. LangGraph builds the initial `ContractState` and looks at the graph: three
   nodes (`detect_risk_clauses`, `check_compliance`,
   `extract_financial_terms`) all have an edge directly from `START`, and
   none of them depend on each other's output. LangGraph recognizes they're
   all "ready" at the same time and schedules them **in the same super-step**
   — run concurrently, not one after another.
3. Because we call the graph with `ainvoke` (the async entry point), LangGraph
   runs these three nodes as concurrent `asyncio` tasks. Each one makes its
   own independent call to the local Ollama model.
4. **Critical detail:** all three nodes write to the *same* shared state
   object at the *same* time. If two nodes tried to overwrite the same field,
   whichever finished last would silently win and you'd lose data. We avoid
   this by giving each branch **its own dedicated state field**
   (`risk_findings`, `compliance_findings`, `financial_findings`) — so there's
   no overlap, and no special merge logic is even needed for the basic
   fields.
5. Once **all three** branches finish, LangGraph automatically moves to the
   next super-step: the `merge_results` node, since that's the only node
   whose *every* incoming edge has now completed.
6. **`merge_results`** reads all three findings from state and assembles one
   consolidated markdown report.
7. The graph reaches `END` and returns the final state to the caller.

## 2.6 Why this pattern fits this problem

- The three analyses are **genuinely independent** — none of them reads
  another's output, so there's no reason to force them into a sequence.
- Legal review tools are often used **interactively** (a lawyer waiting on
  screen), so cutting a 20-second wait down to ~7 seconds is a real,
  user-facing improvement — not just a nice-to-have.
- Running against a **local Ollama model** means these calls don't share an
  external rate limit the way a hosted API might, and concurrent local
  requests are cheap to make — a great fit for fanning out several calls at
  once.
- Using **separate state fields per branch** avoids race conditions without
  needing custom reducer logic — the simplest possible safe way to do
  parallel writes, appropriate for this join shape.

## 2.7 Production-quality implementation

```python
"""
Parallel Workflow — Contract Review Assistant
Pattern: START fans out to 3 independent branches -> join -> END

Run:
    pip install "langgraph==1.2.10" "langchain==1.3.14" "langchain-ollama==1.1.0" "pydantic==2.9.2"
    ollama pull llama3.1:8b     # make sure Ollama is running locally
    python contract_review.py
"""

from __future__ import annotations

import asyncio
import logging
from typing import Optional

from langchain_ollama import ChatOllama
from langgraph.graph import StateGraph, START, END
from pydantic import BaseModel, Field

# --------------------------------------------------------------------------
# 1. Logging
# --------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("contract_review")


# --------------------------------------------------------------------------
# 2. Shared state
#    Each parallel branch writes to its OWN dedicated field, so there's no
#    risk of two concurrent nodes overwriting the same key.
# --------------------------------------------------------------------------
class ContractState(BaseModel):
    contract_text: str = ""

    risk_findings: Optional[str] = None
    compliance_findings: Optional[str] = None
    financial_findings: Optional[str] = None

    final_report: Optional[str] = None


# --------------------------------------------------------------------------
# 3. Shared local model client.
#    temperature=0 for consistent, repeatable extraction-style output.
# --------------------------------------------------------------------------
_llm = ChatOllama(model="llama3.1:8b", temperature=0)


async def _call_llm(system_prompt: str, contract_text: str) -> str:
    """Small shared helper so each branch node stays short and consistent."""
    messages = [
        ("system", system_prompt),
        ("human", contract_text),
    ]
    response = await _llm.ainvoke(messages)
    return response.content.strip()


# --------------------------------------------------------------------------
# 4. Branch 1 — Risk Clause Detection (independent, async)
# --------------------------------------------------------------------------
async def detect_risk_clauses(state: ContractState) -> dict:
    logger.info("BRANCH — detect_risk_clauses (started)")
    try:
        result = await _call_llm(
            system_prompt=(
                "You are a contracts lawyer. List any risky clauses in this "
                "contract (liability caps, auto-renewal, one-sided termination "
                "rights). Be concise, use bullet points. If none, say 'None found.'"
            ),
            contract_text=state.contract_text,
        )
    except Exception as exc:  # noqa: BLE001
        logger.error("detect_risk_clauses failed: %s", exc)
        result = "Risk analysis unavailable — needs manual review."
    logger.info("BRANCH — detect_risk_clauses (finished)")
    return {"risk_findings": result}


# --------------------------------------------------------------------------
# 5. Branch 2 — Compliance Check (independent, async)
# --------------------------------------------------------------------------
async def check_compliance(state: ContractState) -> dict:
    logger.info("BRANCH — check_compliance (started)")
    try:
        result = await _call_llm(
            system_prompt=(
                "You are a compliance officer. Check this contract for data "
                "protection / GDPR mentions and confidentiality terms. Be "
                "concise, use bullet points. If a required item is missing, "
                "say so explicitly."
            ),
            contract_text=state.contract_text,
        )
    except Exception as exc:  # noqa: BLE001
        logger.error("check_compliance failed: %s", exc)
        result = "Compliance analysis unavailable — needs manual review."
    logger.info("BRANCH — check_compliance (finished)")
    return {"compliance_findings": result}


# --------------------------------------------------------------------------
# 6. Branch 3 — Financial Terms Extraction (independent, async)
# --------------------------------------------------------------------------
async def extract_financial_terms(state: ContractState) -> dict:
    logger.info("BRANCH — extract_financial_terms (started)")
    try:
        result = await _call_llm(
            system_prompt=(
                "Extract all financial terms from this contract: amounts, "
                "currency, payment schedule, and penalty terms. Be concise, "
                "use bullet points."
            ),
            contract_text=state.contract_text,
        )
    except Exception as exc:  # noqa: BLE001
        logger.error("extract_financial_terms failed: %s", exc)
        result = "Financial extraction unavailable — needs manual review."
    logger.info("BRANCH — extract_financial_terms (finished)")
    return {"financial_findings": result}


# --------------------------------------------------------------------------
# 7. Join node — runs only after ALL three branches complete
# --------------------------------------------------------------------------
def merge_results(state: ContractState) -> dict:
    logger.info("JOIN — merge_results")
    report = (
        "# Contract Review Report\n\n"
        "## Risk Clauses\n"
        f"{state.risk_findings}\n\n"
        "## Compliance\n"
        f"{state.compliance_findings}\n\n"
        "## Financial Terms\n"
        f"{state.financial_findings}\n"
    )
    return {"final_report": report}


# --------------------------------------------------------------------------
# 8. Build the graph — fan-out from START, fan-in to merge_results.
#    Three edges leave START; all three branches feed into merge_results.
# --------------------------------------------------------------------------
def build_graph():
    graph = StateGraph(ContractState)

    graph.add_node("detect_risk_clauses", detect_risk_clauses)
    graph.add_node("check_compliance", check_compliance)
    graph.add_node("extract_financial_terms", extract_financial_terms)
    graph.add_node("merge_results", merge_results)

    # Fan-out: all three start directly from START
    graph.add_edge(START, "detect_risk_clauses")
    graph.add_edge(START, "check_compliance")
    graph.add_edge(START, "extract_financial_terms")

    # Fan-in: merge_results only runs once ALL three have finished
    graph.add_edge("detect_risk_clauses", "merge_results")
    graph.add_edge("check_compliance", "merge_results")
    graph.add_edge("extract_financial_terms", "merge_results")

    graph.add_edge("merge_results", END)

    return graph.compile()


# --------------------------------------------------------------------------
# 9. Public entry point — async, so the branches truly run concurrently.
# --------------------------------------------------------------------------
async def run_contract_review(contract_text: str) -> dict:
    app = build_graph()
    initial_state = ContractState(contract_text=contract_text)
    final_state = await app.ainvoke(initial_state)
    return final_state


# --------------------------------------------------------------------------
# 10. Demo
# --------------------------------------------------------------------------
if __name__ == "__main__":
    sample_contract = """
    This Master Services Agreement automatically renews annually unless
    either party gives 90 days' notice. Vendor's liability is capped at
    $500 regardless of damages. Client agrees to pay $12,000/month, due on
    the 1st, with a 5% late penalty after 10 days. Confidential information
    must be protected per applicable data protection law.
    """

    result = asyncio.run(run_contract_review(sample_contract))
    print(result["final_report"])
```

**Notes on production-readiness choices made above:**

- **Async nodes + `ainvoke`** — this is what actually makes the three LLM
  calls run concurrently rather than one after another. Using the sync
  `.invoke()` entry point here would silently make the branches run in
  sequence again, defeating the whole point of the pattern.
- **One dedicated state field per branch** (`risk_findings`,
  `compliance_findings`, `financial_findings`) — the simplest way to make
  concurrent writes safe. (When branches genuinely need to write to a
  *shared* field, like appending to one list, LangGraph state supports
  `Annotated[list, operator.add]`-style reducers — that's a detail worth
  knowing but isn't needed for this example.)
- **Each branch has its own `try/except`** — one branch failing (e.g., a
  local model timeout) doesn't crash the other two or the whole pipeline; it
  degrades to a "needs manual review" message instead.
- **Local model via Ollama** — no external API keys, no shared rate limit
  across the three concurrent calls, which is exactly the kind of situation
  where firing off several requests at once is cheap and safe to do.

---

⬅ [1. Sequential Workflow](01-sequential-workflow.md) | [Back to index](README.md) | Next: [3. Conditional Workflow](03-conditional-workflow.md) ➡

# Contract Review Assistant — Parallel Workflow (Java + Spring AI)

A Java port of the LangGraph fan-out/fan-in workflow (`START -> {3 branches} -> merge -> END`),
built on:

- **Java 25** (current LTS) — using virtual threads for true branch concurrency
- **Spring Boot 4.1.0**
- **Spring AI 2.0.0** (GA) with the **Ollama** starter, mirroring `langchain-ollama` / `llama3.1:8b`

## Mapping the shape

The Python version relies on `asyncio` + LangGraph's implicit fan-out (three edges leaving
`START`) to get concurrency, and `ainvoke` to await the join. In Java, the same shape is:

| LangGraph concept | Spring / Java equivalent |
|---|---|
| Three edges from `START` | Three `CompletableFuture`s submitted independently |
| `await app.ainvoke(state)` waiting on all branches | `CompletableFuture.allOf(...).join()` |
| Node writing to its own dedicated field | Each branch returns its own typed result — no shared mutable state, so there's nothing to race on |
| `ChatOllama(model="llama3.1:8b", temperature=0)` | Spring AI `ChatClient` over the `spring-ai-starter-model-ollama` auto-configured `OllamaChatModel` |
| Per-branch `try/except` fallback text | Per-branch `.exceptionally(...)` fallback text |
| `merge_results` join node | A `mergeResults` method run after `allOf` completes |

Each branch is genuinely independent — no shared field is written by more than one branch — so
there's no synchronization needed at all; the only coordination point is the join.

Java 25's virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) are used to run the
three blocking `ChatClient` calls concurrently without tying up platform threads — the closest
Java analogue to `asyncio.gather`-style concurrency for I/O-bound calls.

---

## Project structure

```
contract-review/
├── pom.xml
└── src/main/java/com/example/contractreview/
    ├── ContractReviewApplication.java
    ├── model/
    │   └── ContractReviewResult.java
    ├── pipeline/
    │   ├── RiskClauseBranch.java
    │   ├── ComplianceBranch.java
    │   ├── FinancialTermsBranch.java
    │   └── ContractReviewService.java
    ├── web/
    │   └── ContractReviewController.java
    └── ContractReviewRunner.java   (CLI demo, mirrors the Python __main__ block)
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
    <artifactId>contract-review</artifactId>
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
    name: contract-review
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: llama3.1:8b
          temperature: 0.0

  # Virtual threads for the request-handling side; the branches use their
  # own dedicated virtual-thread executor (see ContractReviewService).
  threads:
    virtual:
      enabled: true

logging:
  level:
    com.example.contractreview: INFO
```

---

## Domain model

### `model/ContractReviewResult.java`

```java
package com.example.contractreview.model;

public record ContractReviewResult(
        String riskFindings,
        String complianceFindings,
        String financialFindings,
        String finalReport
) {}
```

There's no `ContractState` accumulating partial writes here the way `LoanState` did in the
sequential pipeline — since every branch is independent and none of them read state written by
another, each branch simply returns its own `String`, and the join assembles them into the result
record in one step. That sidesteps any question of "is this field populated yet" that a shared
mutable state object would raise under concurrency.

---

## Branches

Each branch is a small `@Component` exposing a single method that calls the shared `ChatClient`
with its own system prompt, matching the Python `_call_llm(system_prompt, contract_text)` helper.

### `pipeline/RiskClauseBranch.java`

```java
package com.example.contractreview.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class RiskClauseBranch {

    private static final Logger log = LoggerFactory.getLogger(RiskClauseBranch.class);

    private static final String SYSTEM_PROMPT = """
            You are a contracts lawyer. List any risky clauses in this contract \
            (liability caps, auto-renewal, one-sided termination rights). Be concise, \
            use bullet points. If none, say 'None found.'
            """;

    private final ChatClient chatClient;

    public RiskClauseBranch(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String detectRiskClauses(String contractText) {
        log.info("BRANCH - detect_risk_clauses (started)");
        try {
            String result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(contractText)
                    .call()
                    .content()
                    .strip();
            log.info("BRANCH - detect_risk_clauses (finished)");
            return result;
        } catch (Exception e) {
            log.error("detect_risk_clauses failed: {}", e.getMessage());
            return "Risk analysis unavailable - needs manual review.";
        }
    }
}
```

### `pipeline/ComplianceBranch.java`

```java
package com.example.contractreview.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class ComplianceBranch {

    private static final Logger log = LoggerFactory.getLogger(ComplianceBranch.class);

    private static final String SYSTEM_PROMPT = """
            You are a compliance officer. Check this contract for data protection / GDPR \
            mentions and confidentiality terms. Be concise, use bullet points. If a \
            required item is missing, say so explicitly.
            """;

    private final ChatClient chatClient;

    public ComplianceBranch(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String checkCompliance(String contractText) {
        log.info("BRANCH - check_compliance (started)");
        try {
            String result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(contractText)
                    .call()
                    .content()
                    .strip();
            log.info("BRANCH - check_compliance (finished)");
            return result;
        } catch (Exception e) {
            log.error("check_compliance failed: {}", e.getMessage());
            return "Compliance analysis unavailable - needs manual review.";
        }
    }
}
```

### `pipeline/FinancialTermsBranch.java`

```java
package com.example.contractreview.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class FinancialTermsBranch {

    private static final Logger log = LoggerFactory.getLogger(FinancialTermsBranch.class);

    private static final String SYSTEM_PROMPT = """
            Extract all financial terms from this contract: amounts, currency, payment \
            schedule, and penalty terms. Be concise, use bullet points.
            """;

    private final ChatClient chatClient;

    public FinancialTermsBranch(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String extractFinancialTerms(String contractText) {
        log.info("BRANCH - extract_financial_terms (started)");
        try {
            String result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(contractText)
                    .call()
                    .content()
                    .strip();
            log.info("BRANCH - extract_financial_terms (finished)");
            return result;
        } catch (Exception e) {
            log.error("extract_financial_terms failed: {}", e.getMessage());
            return "Financial extraction unavailable - needs manual review.";
        }
    }
}
```

### `pipeline/ContractReviewService.java`

The fan-out / fan-in orchestration — the direct analogue of the three `add_edge(START, ...)`
calls plus the three `add_edge(..., "merge_results")` calls.

```java
package com.example.contractreview.pipeline;

import com.example.contractreview.model.ContractReviewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ContractReviewService {

    private static final Logger log = LoggerFactory.getLogger(ContractReviewService.class);

    private final RiskClauseBranch riskClauseBranch;
    private final ComplianceBranch complianceBranch;
    private final FinancialTermsBranch financialTermsBranch;
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ContractReviewService(RiskClauseBranch riskClauseBranch,
                                  ComplianceBranch complianceBranch,
                                  FinancialTermsBranch financialTermsBranch) {
        this.riskClauseBranch = riskClauseBranch;
        this.complianceBranch = complianceBranch;
        this.financialTermsBranch = financialTermsBranch;
    }

    public ContractReviewResult review(String contractText) {
        // Fan-out: all three branches are submitted independently and run concurrently
        // on virtual threads, matching the Python version's asyncio concurrency.
        CompletableFuture<String> riskFuture =
                CompletableFuture.supplyAsync(() -> riskClauseBranch.detectRiskClauses(contractText),
                        virtualThreadExecutor);
        CompletableFuture<String> complianceFuture =
                CompletableFuture.supplyAsync(() -> complianceBranch.checkCompliance(contractText),
                        virtualThreadExecutor);
        CompletableFuture<String> financialFuture =
                CompletableFuture.supplyAsync(() -> financialTermsBranch.extractFinancialTerms(contractText),
                        virtualThreadExecutor);

        // Fan-in: block only until ALL three have completed, same as the join node
        // that LangGraph runs once every incoming edge has fired.
        CompletableFuture.allOf(riskFuture, complianceFuture, financialFuture).join();

        return mergeResults(riskFuture.join(), complianceFuture.join(), financialFuture.join());
    }

    private ContractReviewResult mergeResults(String riskFindings, String complianceFindings,
                                               String financialFindings) {
        log.info("JOIN - merge_results");
        String report = """
                # Contract Review Report

                ## Risk Clauses
                %s

                ## Compliance
                %s

                ## Financial Terms
                %s
                """.formatted(riskFindings, complianceFindings, financialFindings);

        return new ContractReviewResult(riskFindings, complianceFindings, financialFindings, report);
    }
}
```

---

## Entry points

### `web/ContractReviewController.java`

```java
package com.example.contractreview.web;

import com.example.contractreview.model.ContractReviewResult;
import com.example.contractreview.pipeline.ContractReviewService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContractReviewController {

    private final ContractReviewService contractReviewService;

    public ContractReviewController(ContractReviewService contractReviewService) {
        this.contractReviewService = contractReviewService;
    }

    @PostMapping("/api/contract-reviews")
    public ContractReviewResult review(@RequestBody String contractText) {
        return contractReviewService.review(contractText);
    }
}
```

### `ContractReviewRunner.java` (CLI demo, mirrors the Python `if __name__ == "__main__"` block)

```java
package com.example.contractreview;

import com.example.contractreview.pipeline.ContractReviewService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class ContractReviewRunner implements CommandLineRunner {

    private final ContractReviewService contractReviewService;

    public ContractReviewRunner(ContractReviewService contractReviewService) {
        this.contractReviewService = contractReviewService;
    }

    @Override
    public void run(String... args) {
        String sampleContract = """
                This Master Services Agreement automatically renews annually unless
                either party gives 90 days' notice. Vendor's liability is capped at
                $500 regardless of damages. Client agrees to pay $12,000/month, due on
                the 1st, with a 5%% late penalty after 10 days. Confidential information
                must be protected per applicable data protection law.
                """;

        var result = contractReviewService.review(sampleContract);
        System.out.println(result.finalReport());
    }
}
```

### `ContractReviewApplication.java`

```java
package com.example.contractreview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ContractReviewApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContractReviewApplication.class, args);
    }
}
```

---

## Running it

```bash
ollama pull llama3.1:8b
ollama serve   # if not already running

# CLI demo (prints the report, like the Python script)
mvn spring-boot:run -Dspring-boot.run.profiles=demo

# Or as a service
mvn spring-boot:run
curl -X POST localhost:8080/api/contract-reviews \
  -H "Content-Type: text/plain" \
  --data-binary @sample-contract.txt
```

## Notes on the port

- **Concurrency model**: `asyncio.gather`-style concurrency becomes three `CompletableFuture`s on
  virtual threads plus `CompletableFuture.allOf(...).join()`. Since the branches are I/O-bound
  (waiting on the Ollama HTTP call) rather than CPU-bound, virtual threads give the same "many
  cheap concurrent waiters" benefit `asyncio` gives Python, without needing an async `ChatClient`
  API or a reactive stack.
- **No shared mutable state**: `ContractState` in Python has three `Optional` fields that each
  branch fills in independently — safe there because Python's node functions each return a small
  dict merged into state by LangGraph, one key at a time. In Java, skipping the shared record
  entirely (each branch returns its own `String`, merged only at the join) removes any need to
  reason about partial/concurrent writes to one object.
- **Per-branch resilience**: each branch's `try/catch` mirrors the Python `try/except` exactly —
  a failed branch degrades to a "needs manual review" placeholder instead of failing the whole
  request.
- **Versions**: Spring AI's `spring-ai-starter-model-ollama` auto-configures an `OllamaChatModel`
  from `spring.ai.ollama.*` properties, the same role `ChatOllama(model=..., temperature=...)`
  plays in the Python version — point `base-url` at your local Ollama instance.
