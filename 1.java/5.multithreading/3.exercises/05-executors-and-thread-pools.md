# Group 5 — Executors & Thread Pools

**Level:** Intermediate | **Exercises:** 7 | **Prerequisites:** Groups 1–3

## What you will master
- `Executors` factory methods and their hidden dangers
- `ThreadPoolExecutor`: core/max size, queue types, rejection policies
- Custom `ThreadFactory`, naming, metrics
- `Future`, `invokeAll`, `invokeAny`
- `ScheduledExecutorService`
- Graceful shutdown
- Pool sizing (CPU-bound vs IO-bound, Little's Law)
- Thread-pool starvation deadlock

---

## Exercise 5.1 — Thumbnail Service (choosing a pool) ★

**Scenario:** An image-upload endpoint generates 3 thumbnails per image. Work is CPU-bound.

**Task**
1. Implement with `newFixedThreadPool(N)` where N = `availableProcessors()`.
2. Compare against `newCachedThreadPool()` and `newSingleThreadExecutor()` on 1,000 images.
3. Print thread names and observe reuse.
4. Show how `newCachedThreadPool()` can create unbounded threads and `newFixedThreadPool()` can have an unbounded queue (`LinkedBlockingQueue` with `Integer.MAX_VALUE` capacity) — explain why both are risky in production.

**Prove it:** A table of throughput, peak threads, and peak queue length for each pool type.

---

## Exercise 5.2 — Black Friday Burst (`ThreadPoolExecutor` tuning) ★★★

**Scenario:** Order-processing normally runs 50 req/s, spikes to 5,000 req/s for 10 seconds.

**Starter**
```java
ThreadPoolExecutor pool = new ThreadPoolExecutor(
    4, 16, 30, TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(100),
    new NamedThreadFactory("order-worker"),
    new ThreadPoolExecutor.CallerRunsPolicy());
```

**Task**
1. Learn the real task-submission algorithm: core threads → queue → extra threads up to max → reject. Prove it by logging pool size as you submit tasks into a slow pool.
2. Implement and compare four rejection policies: `AbortPolicy`, `CallerRunsPolicy`, `DiscardPolicy`, `DiscardOldestPolicy`, plus a custom one that writes to a dead-letter queue.
3. Explain how `CallerRunsPolicy` produces **backpressure**.
4. Try `SynchronousQueue` + large max pool and explain the difference in thread creation.

**Prove it:** For each policy report accepted / rejected / completed counts under a 5,000-task burst and justify one choice for a payment system vs. an analytics system.

---

## Exercise 5.3 — Observable Thread Pool ★★

**Scenario:** Ops asks, "Is our pool saturated?" You have no metrics.

**Task**
1. Write `NamedThreadFactory` (pool name + counter, daemon flag, `UncaughtExceptionHandler`).
2. Subclass `ThreadPoolExecutor` and override `beforeExecute`, `afterExecute`, `terminated` to record queue wait time, execution time, and failures.
3. Expose gauges: active count, pool size, queue depth, completed task count, rejected count.
4. Show that exceptions in `submit()` are captured in the `Future` and invisible in `afterExecute` unless you unwrap them, while `execute()` propagates them.

**Prove it:** Print a metrics line every second during a load test and identify saturation from the numbers alone.

---

## Exercise 5.4 — Price Comparison (`invokeAll` / `invokeAny`) ★★

**Scenario:** A travel site queries 5 airline APIs with random latency (100–2,000 ms) and occasional failures.

**Task**
1. Using `invokeAll` with a timeout, collect all results that arrived in time; cancelled tasks must be detected via `Future.isCancelled()`.
2. Using `invokeAny`, return the first successful quote (the others must be cancelled).
3. Implement "cheapest within 800 ms" combining a `CompletionService` to process results in completion order.
4. Make each task honour interruption so cancellation actually frees threads.

**Prove it:** Latency of the combined call is bounded by the timeout; no orphaned running tasks after return.

---

## Exercise 5.5 — Cache Refresher & Heartbeat (`ScheduledExecutorService`) ★★

**Scenario:** Refresh an exchange-rate cache every 5 s and send a heartbeat every 1 s.

**Task**
1. Implement both with `scheduleAtFixedRate`, then `scheduleWithFixedDelay`. Make the task occasionally take longer than the period and compare behaviour.
2. Throw an exception from a task and observe the schedule **silently stops**. Fix by wrapping the task in try/catch.
3. Add a one-shot delayed task (`schedule`) that cancels the whole thing after 30 s.
4. Add jitter to avoid all service instances refreshing at the same second.

**Prove it:** Log timestamps showing drift/overlap differences between fixed-rate and fixed-delay, and the survivability after exceptions.

---

## Exercise 5.6 — Graceful Shutdown of a Payment Worker ★★★

**Scenario:** On deploy, SIGTERM arrives while 40 payments are in flight and 500 are queued.

**Task**
1. Implement the canonical 3-phase shutdown: `shutdown()` → `awaitTermination(30s)` → `shutdownNow()` → `awaitTermination(10s)`.
2. Register in a JVM shutdown hook; handle `InterruptedException` during shutdown.
3. Define what "in-flight" payments do on forced shutdown: tasks must be idempotent or write a recovery record.
4. Capture the list returned by `shutdownNow()` and persist it to a "to-be-retried" file.

**Prove it:** Kill -TERM your process under load: no payment is lost silently (every payment is either completed, or in the retry list).

---

## Exercise 5.7 — Pool Sizing Lab (CPU vs IO; Little's Law) ★★★

**Scenario:** You must size pools for (a) PDF rendering (CPU-bound), (b) calling a 200 ms REST API (IO-bound).

**Task**
1. Benchmark both workloads with pool sizes 1, 2, N/2, N, 2N, 10N, 100N.
2. Plot throughput vs pool size; find the knee.
3. Apply formulas: CPU-bound ≈ N or N+1; IO-bound ≈ N × (1 + wait/compute). Compare with measured.
4. Apply **Little's Law** (L = λ × W) to compute the concurrency needed for 1,000 req/s at 200 ms latency.

**Prove it:** Predicted vs measured optimum and a one-page explanation.

---

## Group 5 Completion Checklist
- [ ] I never use unbounded queues/pools in production code
- [ ] I can predict what `ThreadPoolExecutor` does for the next submitted task
- [ ] I can shut a pool down gracefully
- [ ] I can size a pool with data, not guesses
