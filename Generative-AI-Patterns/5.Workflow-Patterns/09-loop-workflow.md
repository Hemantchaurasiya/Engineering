# 9. Loop Workflow

## 9.1 What is it?

A **Loop Workflow** repeats a step **until some condition becomes true** —
the general-purpose version of "keep going while there's more to do." Unlike
Pattern 8 (Iterative Workflow), there's no quality judgment involved here;
the loop simply continues because there's *more data to process*, and stops
when there genuinely isn't any more.

> **Note on naming, continued from Pattern 8:** Iterative Workflow repeats a
> **generate → critique → revise** cycle to improve *one* piece of content.
> Loop Workflow repeats a step to work through **a stream or sequence of
> external data** (like pages of an API) whose length isn't known in advance
> — closer to a `while` loop in ordinary programming than to an editing
> cycle.

## 9.2 What problem does it solve?

Some data sources don't hand you everything at once. A third-party API might
return results one "page" at a time, only telling you if there's a next page
*after* you've fetched the current one. You can't know in advance how many
pages there will be — Map-Reduce (Pattern 6) doesn't apply here because that
pattern needs the full list of items *up front* to fan out across; a Loop
Workflow instead discovers the next piece of work only as a result of doing
the current one.

The Loop Workflow pattern solves this by:

- Letting the graph **repeat a single node** for as many rounds as the data
  actually requires, instead of needing to know the count ahead of time.
- Keeping the **exit condition explicit and checked every round** — usually
  "is there a next page/cursor?" — so the loop naturally winds down instead
  of needing to be told when to stop.
- Adding a **safety cap** as a backstop in case an external system
  misbehaves (e.g., an API that never returns a null "next page" and would
  otherwise loop forever).
- Keeping the **per-round logic simple** — each pass just processes one
  page/item and decides whether to continue.

## 9.3 Realistic production example: Paginated Transaction Sync

A fintech reporting tool (`LedgerSync`) needs to pull **all** of a merchant's
transactions from a payment processor's API for the current billing period.
The API is paginated: each call returns up to 100 transactions **plus** a
`next_cursor` value — `None` once there's nothing left to fetch.

1. **Fetch Page** — call the API with the current cursor, get back a batch of
   transactions and the next cursor (or `None`).
2. **Accumulate** — add this batch to the running total collected so far.
3. **Check condition** — if `next_cursor` is not `None` **and** we're under a
   safety cap of pages, loop back to Fetch Page with the new cursor.
   Otherwise, stop looping.
4. **Summarize** — once the loop ends, produce a short summary of what was
   synced (total transaction count, total amount, page count).

## 9.4 Architecture / Flow Diagram

```mermaid
flowchart TD
    Start([Sync Requested]) --> Fetch[Fetch Page from API]
    Fetch --> Check{next_cursor exists AND under page cap?}
    Check -->|yes| Fetch
    Check -->|no| Summary[Summarize Sync Results - LLM]
    Summary --> End([Sync Complete])

    style Fetch fill:#DCEEFB,stroke:#3B82F6
    style Check fill:#F3E8FF,stroke:#8B5CF6
    style Summary fill:#FDE9C8,stroke:#F59E0B
```

The self-loop on `Fetch` is the whole pattern in one picture: the same node
runs again and again until the condition attached to it finally says "stop."

## 9.5 Request-to-response flow, step by step

1. A client sends a `merchant_id` to `run_transaction_sync()`.
2. LangGraph builds the initial `SyncState` (with `cursor = None`,
   `page_count = 0`, `all_transactions = []`) and enters at `fetch_page`.
3. **`fetch_page`** calls the (simulated) paginated API using the current
   cursor. It appends the returned transactions onto
   `all_transactions`, updates `cursor` to whatever the API returned next
   (or `None`), and increments `page_count`.
4. A **conditional edge** on `fetch_page` calls `should_continue(state)`,
   which returns `"fetch_page"` again if `cursor is not None` **and**
   `page_count < max_pages`, or `"summarize_sync"` otherwise.
5. As long as the condition says "continue," LangGraph runs `fetch_page`
   again — same node, next cursor, one more page collected. This can happen
   any number of times depending on how much data the merchant actually has.
6. Once the API reports no more pages (`cursor is None`) — or, as a safety
   backstop, the page cap is hit — the loop exits and `summarize_sync` runs
   exactly once.
7. **`summarize_sync`** computes totals from the fully collected
   `all_transactions` list and asks the LLM to phrase a short, readable
   summary sentence.
8. The graph reaches `END` and returns the full result.

## 9.6 Why this pattern fits this problem

- The **total number of pages is only knowable by fetching them** — there's
  no way to "fan out" up front like Map-Reduce does, because page 2's cursor
  only exists after page 1 has actually been fetched.
- A **self-loop on one node** is the simplest possible way to express "keep
  doing this until told to stop" — no separate node is needed per page.
- The **page cap is a critical safety net**: real third-party APIs sometimes
  have bugs (an infinite pagination loop is a real, if rare, failure mode),
  and a production sync job should never be able to run forever because of
  someone else's bug.
- Accumulating into a **single running list in state** means the summarize
  step, at the end, has the complete picture without needing any extra
  merging logic — it's just one list that grew a little each round.

## 9.7 Production-quality implementation

```python
"""
Loop Workflow — Paginated Transaction Sync
Pattern: repeat one node (self-loop) until an external condition says stop,
with a safety cap as a backstop

Run:
    pip install "langgraph==1.2.10" "langchain==1.3.14" "langchain-ollama==1.1.0" "pydantic==2.9.2"
    ollama pull llama3.1:8b     # make sure Ollama is running locally
    python transaction_sync.py
"""

from __future__ import annotations

import logging
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
logger = logging.getLogger("transaction_sync")


# --------------------------------------------------------------------------
# 2. Shared state
# --------------------------------------------------------------------------
class SyncState(BaseModel):
    merchant_id: str = ""
    max_pages: int = 20  # safety cap -- never loop more than this many times

    cursor: Optional[str] = None
    page_count: int = 0
    all_transactions: list[dict] = []

    summary: Optional[str] = None


_llm = ChatOllama(model="llama3.1:8b", temperature=0)


# --------------------------------------------------------------------------
# 3. Simulated paginated API.
#    In production this would be a real HTTP call to the payment processor.
#    Returns up to 3 fake transactions per page and a next_cursor, going
#    None after page 4 to represent "no more data."
# --------------------------------------------------------------------------
def _call_paginated_api(merchant_id: str, cursor: Optional[str]) -> tuple[list[dict], Optional[str]]:
    page_number = int(cursor) if cursor else 1
    if page_number > 4:
        return [], None  # no more pages

    transactions = [
        {"id": f"{merchant_id}-txn-{page_number}-{i}", "amount": 10.0 * page_number + i}
        for i in range(3)
    ]
    next_cursor = str(page_number + 1) if page_number < 4 else None
    return transactions, next_cursor


# --------------------------------------------------------------------------
# 4. Node — Fetch Page (this is the node that loops).
#    Each call handles exactly ONE page, then updates the cursor and count
#    that the routing function will check.
# --------------------------------------------------------------------------
def fetch_page(state: SyncState) -> dict:
    logger.info("LOOP — fetching page %d (cursor=%s)", state.page_count + 1, state.cursor)

    try:
        transactions, next_cursor = _call_paginated_api(state.merchant_id, state.cursor)
    except Exception as exc:  # noqa: BLE001
        logger.error("Page fetch failed, stopping loop here: %s", exc)
        # On a real API error, stop looping rather than retrying forever;
        # a dedicated Retry Pattern (covered later) would add bounded
        # retries around just this call.
        return {"cursor": None, "page_count": state.page_count + 1}

    return {
        "all_transactions": state.all_transactions + transactions,
        "cursor": next_cursor,
        "page_count": state.page_count + 1,
    }


# --------------------------------------------------------------------------
# 5. Routing function — the loop's exit condition.
#    Continues only while there's a next cursor AND we're under the cap.
# --------------------------------------------------------------------------
def should_continue(state: SyncState) -> str:
    if state.cursor is not None and state.page_count < state.max_pages:
        return "fetch_page"
    if state.page_count >= state.max_pages and state.cursor is not None:
        logger.warning(
            "Safety cap of %d pages reached with more data remaining -- stopping early",
            state.max_pages,
        )
    return "summarize_sync"


# --------------------------------------------------------------------------
# 6. Node — Summarize (runs exactly once, after the loop ends)
# --------------------------------------------------------------------------
_SUMMARY_PROMPT = """Write one short sentence summarizing this data sync for
an operations dashboard.

Merchant: {merchant_id}
Pages fetched: {page_count}
Total transactions: {txn_count}
Total amount: ${total_amount:,.2f}
"""


def summarize_sync(state: SyncState) -> dict:
    logger.info(
        "LOOP COMPLETE — %d pages, %d transactions", state.page_count, len(state.all_transactions)
    )
    total_amount = sum(t["amount"] for t in state.all_transactions)

    try:
        response = _llm.invoke(
            _SUMMARY_PROMPT.format(
                merchant_id=state.merchant_id,
                page_count=state.page_count,
                txn_count=len(state.all_transactions),
                total_amount=total_amount,
            )
        )
        summary = response.content.strip()
    except Exception as exc:  # noqa: BLE001
        logger.error("summarize_sync LLM call failed: %s", exc)
        summary = (
            f"Synced {len(state.all_transactions)} transactions "
            f"(${total_amount:,.2f}) across {state.page_count} pages."
        )

    return {"summary": summary}


# --------------------------------------------------------------------------
# 7. Build the graph — a self-loop on fetch_page, exited by should_continue.
# --------------------------------------------------------------------------
def build_graph():
    graph = StateGraph(SyncState)

    graph.add_node("fetch_page", fetch_page)
    graph.add_node("summarize_sync", summarize_sync)

    graph.add_edge(START, "fetch_page")

    # This is the loop: fetch_page's own conditional edge can route back
    # to itself.
    graph.add_conditional_edges(
        "fetch_page",
        should_continue,
        {
            "fetch_page": "fetch_page",
            "summarize_sync": "summarize_sync",
        },
    )

    graph.add_edge("summarize_sync", END)

    return graph.compile()


# --------------------------------------------------------------------------
# 8. Public entry point
# --------------------------------------------------------------------------
def run_transaction_sync(merchant_id: str, max_pages: int = 20) -> dict:
    app = build_graph()
    initial_state = SyncState(merchant_id=merchant_id, max_pages=max_pages)
    final_state = app.invoke(initial_state)
    return final_state


# --------------------------------------------------------------------------
# 9. Demo
# --------------------------------------------------------------------------
if __name__ == "__main__":
    result = run_transaction_sync("MERCHANT-882")
    print(f"Pages fetched: {result['page_count']}")
    print(f"Transactions collected: {len(result['all_transactions'])}")
    print(f"Summary: {result['summary']}")
```

**Notes on production-readiness choices made above:**

- **The exit condition checks two things, in order** — the *real* signal
  (`cursor is not None`) and the *safety backstop* (`page_count < max_pages`)
  — and logs a warning specifically when the cap cuts off a sync that still
  had more data, so this doesn't fail silently in production.
- **A single node loops on itself** via `add_conditional_edges` pointing back
  to its own name — the simplest possible shape for "repeat until done,"
  with no need for a separate "loop controller" node.
- **The API call is wrapped in `try/except`**, and a failure **stops the
  loop** (sets `cursor = None`) rather than retrying indefinitely or
  crashing — with a note pointing at the dedicated Retry Pattern (coming up
  next in this series) for how to handle transient failures more
  gracefully than "give up after one failed page."
- **State accumulates the full list across every round**
  (`state.all_transactions + transactions`), so by the time `summarize_sync`
  runs, it already has the complete picture with no extra merge step needed.

---

⬅ [8. Iterative Workflow](08-iterative-workflow.md) | [Back to index](README.md) | Next: [10. Retry Pattern](10-retry-pattern.md) ➡

# Paginated Transaction Sync — Self-Loop Workflow (Java + Spring AI)

A Java port of the LangGraph self-loop workflow — one node (`fetch_page`) loops back into itself
until an external condition (no more pages) says stop, with a safety cap as a backstop against
infinite looping. Built on:

- **Java 25** (current LTS)
- **Spring Boot 4.1.0**
- **Spring AI 2.0.0** (GA) with the **Ollama** starter, mirroring `llama3.1:8b`

## Mapping the shape

Like the ad-copy generator's `generate -> critique -> revise -> critique -> ...` loop, this is a
**self-loop**: `fetch_page`'s own conditional edge can route back to itself. The exit condition
here has two independent parts worth keeping distinct, exactly as the Python version does:
"is there more data?" (`cursor != null`) and "are we still under the safety cap?"
(`pageCount < maxPages`) — and the specific behavior when the cap is hit *while data remains* is
a deliberate early-stop, logged as a warning, not an error.

| LangGraph concept | Spring / Java equivalent |
|---|---|
| `fetch_page` (the node that loops) | `TransactionPageFetchStep.fetchPage(...)`, called inside a `while` loop |
| `_call_paginated_api(merchant_id, cursor)` | `PaginatedTransactionApiClient.fetchPage(...)` |
| `should_continue(state) -> str` | The `while` loop's condition, checked in the same two-part order |
| Self-loop edge `fetch_page -> fetch_page` | The `while` loop simply iterating again |
| `summarize_sync` (runs once, after the loop) | `SyncSummaryStep.summarize(...)`, called once after the loop exits |
| `state.all_transactions + transactions` (list append per page) | `allTransactions.addAll(transactions)` on a local `List` |

---

## Project structure

```
transaction-sync/
├── pom.xml
└── src/main/java/com/example/transactionsync/
    ├── TransactionSyncApplication.java
    ├── model/
    │   ├── Transaction.java
    │   ├── TransactionPage.java
    │   └── SyncResult.java
    ├── pipeline/
    │   ├── PaginatedTransactionApiClient.java
    │   ├── TransactionPageFetchStep.java
    │   ├── SyncSummaryStep.java
    │   └── TransactionSyncService.java
    ├── web/
    │   └── TransactionSyncController.java
    └── TransactionSyncRunner.java   (CLI demo, mirrors the Python __main__ block)
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
    <artifactId>transaction-sync</artifactId>
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
    name: transaction-sync
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: llama3.1:8b
          temperature: 0.0

logging:
  level:
    com.example.transactionsync: INFO
```

---

## Domain model

### `model/Transaction.java`

```java
package com.example.transactionsync.model;

public record Transaction(String id, double amount) {}
```

### `model/TransactionPage.java`

The two-value return of `_call_paginated_api` (`transactions`, `next_cursor`) becomes one small
record rather than a Python tuple.

```java
package com.example.transactionsync.model;

import java.util.List;

public record TransactionPage(List<Transaction> transactions, String nextCursor) {}
```

### `model/SyncResult.java`

```java
package com.example.transactionsync.model;

public record SyncResult(int pageCount, int transactionCount, double totalAmount, String summary) {}
```

There's no `SyncState` record carrying `cursor`/`pageCount`/`allTransactions` through the whole
run the way the Python `SyncState` does — as with the ad-copy loop, the loop's working variables
live as locals inside `TransactionSyncService.sync`, since only that one method needs to see them
change from iteration to iteration.

---

## Simulated paginated API

### `pipeline/PaginatedTransactionApiClient.java`

In production this would be a real HTTP call to the payment processor — kept as a deterministic
simulation here, exactly matching the Python version's fake 4-page dataset.

```java
package com.example.transactionsync.pipeline;

import com.example.transactionsync.model.Transaction;
import com.example.transactionsync.model.TransactionPage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PaginatedTransactionApiClient {

    public TransactionPage fetchPage(String merchantId, String cursor) {
        int pageNumber = cursor != null ? Integer.parseInt(cursor) : 1;
        if (pageNumber > 4) {
            return new TransactionPage(List.of(), null); // no more pages
        }

        List<Transaction> transactions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            transactions.add(new Transaction(
                    "%s-txn-%d-%d".formatted(merchantId, pageNumber, i),
                    10.0 * pageNumber + i));
        }

        String nextCursor = pageNumber < 4 ? String.valueOf(pageNumber + 1) : null;
        return new TransactionPage(transactions, nextCursor);
    }
}
```

---

## The loop body

### `pipeline/TransactionPageFetchStep.java`

The node that loops — each call handles exactly one page, same as `fetch_page`.

```java
package com.example.transactionsync.pipeline;

import com.example.transactionsync.model.TransactionPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransactionPageFetchStep {

    private static final Logger log = LoggerFactory.getLogger(TransactionPageFetchStep.class);

    private final PaginatedTransactionApiClient apiClient;

    public TransactionPageFetchStep(PaginatedTransactionApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public TransactionPage fetchPage(String merchantId, String cursor, int pageCount) {
        log.info("LOOP - fetching page {} (cursor={})", pageCount + 1, cursor);
        try {
            return apiClient.fetchPage(merchantId, cursor);
        } catch (Exception e) {
            // On a real API error, stop looping rather than retrying forever; a dedicated
            // Retry Pattern would add bounded retries around just this call.
            log.error("Page fetch failed, stopping loop here: {}", e.getMessage());
            return new TransactionPage(List.of(), null);
        }
    }
}
```

---

## The one-shot summary

### `pipeline/SyncSummaryStep.java`

Runs exactly once, after the loop ends — the direct analogue of `summarize_sync`.

```java
package com.example.transactionsync.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class SyncSummaryStep {

    private static final Logger log = LoggerFactory.getLogger(SyncSummaryStep.class);

    private static final String SUMMARY_PROMPT = """
            Write one short sentence summarizing this data sync for an operations dashboard.

            Merchant: {merchantId}
            Pages fetched: {pageCount}
            Total transactions: {txnCount}
            Total amount: ${totalAmount}
            """;

    private final ChatClient chatClient;

    public SyncSummaryStep(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String summarize(String merchantId, int pageCount, int txnCount, double totalAmount) {
        log.info("LOOP COMPLETE - {} pages, {} transactions", pageCount, txnCount);
        try {
            return chatClient.prompt()
                    .user(u -> u.text(SUMMARY_PROMPT)
                            .param("merchantId", merchantId)
                            .param("pageCount", String.valueOf(pageCount))
                            .param("txnCount", String.valueOf(txnCount))
                            .param("totalAmount", String.format(Locale.US, "%,.2f", totalAmount)))
                    .call()
                    .content()
                    .strip();
        } catch (Exception e) {
            log.error("summarize_sync LLM call failed: {}", e.getMessage());
            return "Synced %d transactions ($%,.2f) across %d pages."
                    .formatted(txnCount, totalAmount, pageCount);
        }
    }
}
```

---

## The loop itself

### `pipeline/TransactionSyncService.java`

The `while` loop is the self-loop edge; its condition preserves the Python router's two-part
check — continue only while there's a next cursor **and** we're under the safety cap — including
the specific warning log when the cap cuts off a sync that still had data remaining.

```java
package com.example.transactionsync.pipeline;

import com.example.transactionsync.model.SyncResult;
import com.example.transactionsync.model.Transaction;
import com.example.transactionsync.model.TransactionPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TransactionSyncService {

    private static final Logger log = LoggerFactory.getLogger(TransactionSyncService.class);

    private final TransactionPageFetchStep transactionPageFetchStep;
    private final SyncSummaryStep syncSummaryStep;

    public TransactionSyncService(TransactionPageFetchStep transactionPageFetchStep,
                                   SyncSummaryStep syncSummaryStep) {
        this.transactionPageFetchStep = transactionPageFetchStep;
        this.syncSummaryStep = syncSummaryStep;
    }

    public SyncResult sync(String merchantId, int maxPages) {
        List<Transaction> allTransactions = new ArrayList<>();
        String cursor = null;
        int pageCount = 0;

        // should_continue, in the same order: keep going only while there's a next
        // cursor AND we're under the safety cap.
        while (cursor != null || pageCount == 0) {
            if (pageCount > 0 && (cursor == null || pageCount >= maxPages)) {
                break;
            }

            TransactionPage page = transactionPageFetchStep.fetchPage(merchantId, cursor, pageCount);
            allTransactions.addAll(page.transactions());
            cursor = page.nextCursor();
            pageCount++;

            if (pageCount >= maxPages && cursor != null) {
                log.warn("Safety cap of {} pages reached with more data remaining - stopping early",
                        maxPages);
                cursor = null; // force loop exit, mirroring should_continue's early return
            }
        }

        double totalAmount = allTransactions.stream().mapToDouble(Transaction::amount).sum();
        String summary = syncSummaryStep.summarize(merchantId, pageCount, allTransactions.size(), totalAmount);

        return new SyncResult(pageCount, allTransactions.size(), totalAmount, summary);
    }
}
```

> The loop condition above is written to match `should_continue` exactly, including its exact
> exit point on hitting the cap. If you find the `while (cursor != null || pageCount == 0) { if
> (...) break; ... }` shape harder to read than the Python version's separate routing function,
> a `do { ... } while (cursor != null && pageCount < maxPages)` reads more naturally in Java and
> is equivalent — shown as an alternative below.

<details>
<summary>Equivalent, more idiomatic Java loop shape</summary>

```java
public SyncResult sync(String merchantId, int maxPages) {
    List<Transaction> allTransactions = new ArrayList<>();
    String cursor = null;
    int pageCount = 0;
    boolean cappedWithDataRemaining = false;

    do {
        TransactionPage page = transactionPageFetchStep.fetchPage(merchantId, cursor, pageCount);
        allTransactions.addAll(page.transactions());
        cursor = page.nextCursor();
        pageCount++;

        if (pageCount >= maxPages && cursor != null) {
            cappedWithDataRemaining = true;
            break;
        }
    } while (cursor != null);

    if (cappedWithDataRemaining) {
        log.warn("Safety cap of {} pages reached with more data remaining - stopping early", maxPages);
    }

    double totalAmount = allTransactions.stream().mapToDouble(Transaction::amount).sum();
    String summary = syncSummaryStep.summarize(merchantId, pageCount, allTransactions.size(), totalAmount);
    return new SyncResult(pageCount, allTransactions.size(), totalAmount, summary);
}
```

</details>

---

## Entry points

### `web/TransactionSyncController.java`

```java
package com.example.transactionsync.web;

import com.example.transactionsync.model.SyncResult;
import com.example.transactionsync.pipeline.TransactionSyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransactionSyncController {

    private final TransactionSyncService transactionSyncService;

    public TransactionSyncController(TransactionSyncService transactionSyncService) {
        this.transactionSyncService = transactionSyncService;
    }

    @GetMapping("/api/merchants/{merchantId}/sync")
    public SyncResult sync(@PathVariable String merchantId,
                            @RequestParam(defaultValue = "20") int maxPages) {
        return transactionSyncService.sync(merchantId, maxPages);
    }
}
```

### `TransactionSyncRunner.java` (CLI demo, mirrors the Python `if __name__ == "__main__"` block)

```java
package com.example.transactionsync;

import com.example.transactionsync.pipeline.TransactionSyncService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class TransactionSyncRunner implements CommandLineRunner {

    private final TransactionSyncService transactionSyncService;

    public TransactionSyncRunner(TransactionSyncService transactionSyncService) {
        this.transactionSyncService = transactionSyncService;
    }

    @Override
    public void run(String... args) {
        var result = transactionSyncService.sync("MERCHANT-882", 20);
        System.out.println("Pages fetched: " + result.pageCount());
        System.out.println("Transactions collected: " + result.transactionCount());
        System.out.println("Summary: " + result.summary());
    }
}
```

### `TransactionSyncApplication.java`

```java
package com.example.transactionsync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TransactionSyncApplication {
    public static void main(String[] args) {
        SpringApplication.run(TransactionSyncApplication.class, args);
    }
}
```

---

## Running it

```bash
ollama pull llama3.1:8b
ollama serve   # if not already running

# CLI demo (prints pages/transactions/summary, like the Python script)
mvn spring-boot:run -Dspring-boot.run.profiles=demo

# Or as a service
mvn spring-boot:run
curl "localhost:8080/api/merchants/MERCHANT-882/sync?maxPages=20"

# Try a low cap to see the early-stop warning path (the demo data has 4 pages)
curl "localhost:8080/api/merchants/MERCHANT-882/sync?maxPages=2"
```

## Notes on the port

- **Self-loop → `while`/`do-while` loop**: as with the ad-copy generator's loop, `fetch_page`
  routing back to itself needs no graph machinery in Java — the "idiomatic" variant above using
  `do { ... } while (cursor != null && pageCount < maxPages)` is arguably a cleaner read than the
  literal translation, and both are included so you can pick based on how closely you want the
  code to mirror `should_continue`'s exact control flow versus how naturally it reads as Java.
- **Two-part exit condition kept distinct**: "is there more data" and "are we under the cap" stay
  as two separate checks rather than being collapsed into one, matching the Python version's
  `should_continue`, which also needs to distinguish *why* it's stopping in order to log the
  cap-hit warning correctly.
- **Cap-hit warning preserved**: hitting `maxPages` while `cursor` is still non-null logs the
  exact same warning as `should_continue`'s second branch — this is a case Python's own comment
  flags as worth surfacing to an operator, since data was left unsynced.
- **Per-page failure handling unchanged**: `TransactionPageFetchStep.fetchPage` stops the loop on
  a failed page fetch (returning an empty page with no next cursor) rather than retrying forever,
  exactly matching the Python version's explicit non-retry choice and its note that bounded
  retries would be a separate, dedicated pattern.
- **Versions**: same Ollama-backed `spring-ai-starter-model-ollama` / `llama3.1:8b` setup as the
  other ports in this series.
