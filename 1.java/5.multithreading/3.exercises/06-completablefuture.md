# Group 6 — CompletableFuture & Asynchronous Pipelines

**Level:** Intermediate → Advanced | **Exercises:** 7 | **Prerequisites:** Group 5

> You already know the basic `supplyAsync` / `thenCombine` / `thenAccept` / `exceptionally` flow. These exercises go further: composition, fan-out/fan-in, timeouts, retries, and avoiding the classic traps.

## What you will master
- `thenApply` vs `thenCompose` (map vs flatMap)
- `thenCombine`, `allOf`, `anyOf`
- `exceptionally`, `handle`, `whenComplete`
- `orTimeout`, `completeOnTimeout`
- Async variants and which executor runs your callback
- Retry with backoff, cancellation limits
- Partial-result aggregation

---

## Exercise 6.1 — Checkout Chain (`thenCompose`) ★★

**Scenario:** Checkout: validate cart → reserve inventory → charge payment → create shipment. Each step is an async call returning `CompletableFuture<T>` and depends on the previous result.

**Task**
1. Implement each step as a method returning `CompletableFuture`.
2. Chain with `thenCompose`. Then (deliberately) use `thenApply` and observe the `CompletableFuture<CompletableFuture<T>>` problem.
3. If payment fails, you must release the inventory (compensation). Implement using `handle` / `exceptionallyCompose`.
4. Log the thread name at each step and explain why it changes (or not).

**Prove it:** A failure at each of the 4 steps produces the correct compensation and a single final failure result.

---

## Exercise 6.2 — Product Page Aggregator (fan-out / fan-in) ★★★

**Scenario:** A product page needs pricing, inventory, reviews, recommendations, and seller info — 5 independent services with 50–800 ms latency.

**Starter**
```java
var price   = CompletableFuture.supplyAsync(() -> pricing.get(id), ioPool);
var stock   = CompletableFuture.supplyAsync(() -> inventory.get(id), ioPool);
var reviews = CompletableFuture.supplyAsync(() -> reviews.get(id), ioPool);
// ... then join them into ProductPage
```

**Task**
1. Run sequentially, then in parallel; show total latency ≈ max(latencies), not sum.
2. Join with `allOf(...)`, then extract results with `join()` safely (all are complete by then).
3. Build a typed `ProductPage` from the results without the unchecked casts of `allOf` (hint: `thenCombine` chains, or a small `Futures.allOf(List<CF<T>>)` helper returning `CF<List<T>>`).
4. Make `recommendations` and `reviews` optional: failure → empty section, but `pricing` failure → fail the whole page.

**Prove it:** Page latency ≈ slowest *required* call; optional failures are tolerated.

---

## Exercise 6.3 — Resilient Error Handling ★★

**Scenario:** Recommendations service is flaky. Return cached recommendations when it fails.

**Task**
1. Compare behaviour: `exceptionally` (recover), `handle` (result or error), `whenComplete` (observe only, doesn't recover).
2. Inspect how exceptions are wrapped: `CompletionException` vs `ExecutionException` vs original. Write `unwrap(Throwable)`.
3. Show what happens to downstream stages when an upstream stage fails and no handler is attached (the "silent failure" when you never call `join`/`get`).
4. Add structured logging of failures with the correlation id (use Exercise 1.5's context wrapper).

**Prove it:** Unit tests with injected failures show the exact exception type seen at each stage.

---

## Exercise 6.4 — 300 ms SLA Budget (timeouts) ★★★

**Scenario:** Your endpoint must respond in ≤ 300 ms. Downstream calls may take up to 2 s.

**Task**
1. Use `orTimeout(250, MILLISECONDS)` on the whole aggregation and `completeOnTimeout(fallback, 150, MILLISECONDS)` for optional parts.
2. Demonstrate that the underlying blocking call is **not** cancelled when the future times out (`cancel(true)` does not interrupt `supplyAsync` work). Prove it with a counter of "still running" tasks.
3. Make cancellation effective: hold the `Future` returned by `executor.submit()` and cancel it on timeout, bridging it to a `CompletableFuture`.
4. Implement a `Deadline` object that propagates the remaining budget to each downstream call.

**Prove it:** p99 latency stays under 300 ms with injected 2 s stalls, and no zombie tasks remain.

---

## Exercise 6.5 — Retry with Exponential Backoff & Jitter ★★★

**Scenario:** A payment-gateway call fails transiently (HTTP 503). Retry up to 4 times with exponential backoff + jitter.

**Task**
1. Implement `Retry.async(Supplier<CF<T>>, RetryPolicy, ScheduledExecutorService)` that never blocks a thread while waiting (use `schedule`, not `sleep`).
2. Retry only on retryable exceptions (predicate).
3. Respect an overall deadline.
4. Ensure the operation is idempotent: pass an idempotency key to the payment call.

**Prove it:** A test double fails twice, then succeeds: observe delay ≈ 100 ms, 200 ms (± jitter), and a total of 3 attempts. Thread count stays constant during waits.

---

## Exercise 6.6 — Common-Pool Starvation (executor choice) ★★★

**Scenario:** A team used `supplyAsync` with no executor and calls blocking HTTP clients. Under load the entire app slows, including unrelated parallel streams.

**Task**
1. Show `ForkJoinPool.commonPool()` parallelism = `cores - 1`. Fire blocking tasks and observe starvation of other code using the common pool.
2. Fix by providing dedicated bounded executors per downstream (bulkhead by pool).
3. Explore `thenApply` vs `thenApplyAsync` vs `thenApplyAsync(fn, executor)`: print which thread runs the callback in each case, including when the future is already complete.
4. Write guidelines for your team: when is it OK to use the common pool?

**Prove it:** Latency of an unrelated task stays flat after you isolate pools.

---

## Exercise 6.7 — Search Aggregator with Partial Results ★★★★

**Scenario:** Hotel search queries 6 providers; return whatever has arrived within 1 s, ranked, and mark which providers timed out.

**Task**
1. Fire all calls; each result is `Result<Hotels>` (success/timeout/error) — never throw.
2. After 1 s, gather all completed results; incomplete ones are marked "timed out".
3. Stream results to the client progressively (callback for each provider as it arrives, using `thenAccept`).
4. Add `anyOf`-style "first N successful" short-circuit (return as soon as 3 providers respond).

**Prove it:** Output includes per-provider status and total latency ≤ 1 s + ε.

---

## Group 6 Completion Checklist
- [ ] I can explain `thenApply` vs `thenCompose` with a diagram
- [ ] I know timeouts don't cancel work and how to compensate
- [ ] I never block inside a common-pool callback
- [ ] I can write async retry/backoff without sleeping threads
