# Group 12 — Concurrency Design Patterns for Backend Systems

**Level:** Advanced | **Exercises:** 8 | **Prerequisites:** Groups 1–10

> You already know Resilience4j. Here you build the primitives yourself so you understand exactly what they do under the hood.

## What you will master
- Pipelines with backpressure
- Rate limiting (token bucket, sliding window)
- Bulkhead and circuit breaker from scratch
- Request coalescing (single-flight)
- Per-key serial execution (actor / mailbox)
- TTL cache with refresh-ahead
- Delayed/priority job scheduler
- Idempotent concurrent consumers

---

## Exercise 12.1 — Log Ingestion Pipeline with Backpressure ★★★

**Scenario:** Stages: parse → enrich → batch → write to storage. Storage is slower than ingestion at peak.

**Task**
1. Connect stages with bounded `BlockingQueue`s; each stage has its own thread count.
2. Show what happens without bounds (heap grows) vs with bounds (producers block = backpressure).
3. Implement graceful shutdown using a poison pill that flows through all stages.
4. Add metrics: queue depth per stage and per-stage throughput; identify the bottleneck from the numbers.

**Prove it:** Memory stays flat under overload; no messages lost on shutdown.

---

## Exercise 12.2 — Thread-Safe Rate Limiter ★★★

**Scenario:** Limit each API key to 100 requests/second.

**Task**
1. Implement a **token bucket** (refill computed lazily from `System.nanoTime()`; state in an `AtomicReference` or guarded by a lock).
2. Implement a **sliding-window log** and **fixed window counter**; show the burst-at-boundary flaw of fixed windows.
3. Support per-key limiters in a `ConcurrentHashMap` with eviction of idle keys.
4. Test with 64 threads: allowed requests in any 1 s window never exceed the limit + burst.

**Prove it:** Statistical test over a 30 s run; compare accuracy and CPU cost of the three algorithms.

---

## Exercise 12.3 — Circuit Breaker & Bulkhead from Scratch ★★★★

**Task**
1. Build `CircuitBreaker` (CLOSED/OPEN/HALF_OPEN) with a sliding window failure-rate threshold (reuse Exercise 4.3).
2. Build `Bulkhead` using `Semaphore` (and a thread-pool bulkhead variant).
3. Compose: `retry( circuitBreaker( bulkhead( timeout( call ) ) ) )` — explain why the order matters.
4. Run chaos tests: latency spikes, error bursts, and recovery.

**Prove it:** Time-series log of state transitions matching the injected failure windows. Then compare behaviour with Resilience4j on the same scenario.

---

## Exercise 12.4 — Single-Flight Request Coalescing ★★★

**Scenario:** 1,000 simultaneous requests for the same product page trigger 1,000 identical DB queries after a cache miss.

**Task**
1. Implement `SingleFlight<K,V>`: concurrent callers for the same key share one in-flight `CompletableFuture<V>`.
2. Remove the entry on completion (success *and* failure).
3. Decide what happens when the leader is cancelled: followers must not be cancelled.
4. Add per-follower timeouts (a follower can give up without cancelling the shared work).

**Prove it:** 1,000 callers → exactly 1 loader invocation; a failed load can be retried by the next caller.

---

## Exercise 12.5 — Per-Account Ordered Execution (Actor / Serial Executor) ★★★★

**Scenario:** Events for one account must be processed strictly in order, but different accounts should run in parallel (like Kafka partitions).

**Task**
1. Implement `KeyedSerialExecutor`: a shared pool + a per-key mailbox queue; at most one task per key runs at a time.
2. Guarantee ordering per key under 16 worker threads and 1M events across 10k keys.
3. Remove idle key mailboxes safely (race between "empty" and "new message").
4. Compare with hashing keys to N single-thread executors (partitioning); discuss hot-key problems.

**Prove it:** Per-key sequence numbers always increase; no two tasks for the same key overlap (detect with an in-flight counter per key).

---

## Exercise 12.6 — TTL Cache with Refresh-Ahead ★★★★

**Scenario:** Exchange rates should never block a request when stale; refresh in the background before expiry.

**Task**
1. `Cache<K,V>` with TTL, max size (LRU or LFU), and a loader function.
2. Refresh-ahead: when an entry is within 20% of expiry, trigger **one** async refresh (single-flight) while serving the current value.
3. Serve stale on loader failure for up to a grace period ("stale-if-error").
4. Run a background eviction thread; ensure no lock is held while calling the loader.
5. Compare behaviour/performance to Caffeine on the same workload.

**Prove it:** Under constant traffic, request latency never includes a loader call after warm-up.

---

## Exercise 12.7 — Priority Job Scheduler with Retries ★★★★

**Scenario:** A job runner that supports priorities, delayed execution, retry with backoff, and dead-lettering.

**Task**
1. Use `DelayQueue` / `PriorityBlockingQueue` with a worker pool.
2. Jobs have `runAt`, `priority`, `maxAttempts`; failures re-enqueue with backoff.
3. Prevent duplicate execution of the same job id concurrently.
4. Support cancel and graceful shutdown (persist unfinished jobs).

**Prove it:** Deterministic test using a controllable `Clock`; no job lost or run twice at the same time.

---

## Exercise 12.8 — Idempotent Consumer Under Concurrent Redelivery ★★★★

**Scenario:** A message broker delivers the same message twice simultaneously to two consumer instances.

**Task**
1. Implement `IdempotentProcessor` using an atomic "claim" (`putIfAbsent` with state IN_PROGRESS/DONE).
2. Handle consumer crash mid-processing (lease timeout so another consumer can take over).
3. Make the business effect + the "done" mark atomic (outbox-style) and discuss what is not possible in-memory.
4. Simulate 10,000 messages with 30% duplicates delivered concurrently.

**Prove it:** Every message's effect is applied exactly once; crash-recovery test passes.

---

## Group 12 Completion Checklist
- [ ] I can build resilience primitives without a library
- [ ] I can guarantee per-key ordering with cross-key parallelism
- [ ] I can prevent thundering herds with single-flight
- [ ] I can design idempotent, concurrency-safe consumers
