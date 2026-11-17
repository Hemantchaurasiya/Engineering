# Group 10 — Virtual Threads & Structured Concurrency (Java 21+)

**Level:** Advanced | **Exercises:** 6 | **Prerequisites:** Groups 5, 6, 8

> **Version note:** Virtual threads are final in Java 21. `StructuredTaskScope` and `ScopedValue` were preview APIs in Java 21 and their APIs have evolved in later releases. Check your JDK's status and use `--enable-preview` if needed; the *concepts* below stay the same.

## What you will master
- Virtual threads vs platform threads
- `Executors.newVirtualThreadPerTaskExecutor()`
- Limiting concurrency without pool sizing (`Semaphore`)
- Pinning (`synchronized` + blocking) and diagnosing it
- `ThreadLocal` vs `ScopedValue`
- Structured concurrency (scoped lifetimes, cancel-on-failure)

---

## Exercise 10.1 — 100,000 Concurrent Downstream Calls ★★

**Scenario:** Your Exercise 1.6 meltdown, redone.

**Task**
1. Simulate 100,000 tasks, each blocking for 1 s (`Thread.sleep(1000)`), using platform threads in a fixed pool of 200, then using `newVirtualThreadPerTaskExecutor()`.
2. Compare wall time, memory, and peak platform threads (carrier threads).
3. Create threads manually with `Thread.ofVirtual().name("vt-", 0).start(...)`.
4. Explain "thread-per-request is back" in `NOTES.md`.

**Prove it:** 100k tasks finish in ≈ 1–2 s on virtual threads; show the comparison with Exercise 1.6.

---

## Exercise 10.2 — Don't Pool Virtual Threads; Limit Resources Instead ★★★

**Scenario:** A downstream DB only handles 20 concurrent queries. With virtual threads you may have 50,000 tasks.

**Task**
1. Run 50,000 virtual-thread tasks; limit DB access with `Semaphore(20)`.
2. Verify concurrent DB calls ≤ 20 and every task eventually completes.
3. Show that pooling virtual threads in a fixed pool defeats the point, and why.
4. Observe blocking-queue-like behaviour of `Semaphore` with virtual threads (parked, not blocking carriers).

**Prove it:** Max observed concurrency == 20; throughput matches theoretical (20 / latency).

---

## Exercise 10.3 — Pinning Detective ★★★★

**Scenario:** After migrating to virtual threads, throughput collapses. A `synchronized` block wraps a blocking call.

**Task**
1. Write a service with `synchronized` + `Thread.sleep()` inside, run with `-Djdk.tracePinnedThreads=full`, and read the output. (Behaviour depends on JDK version — note which you used; recent JDKs reduced `synchronized` pinning.)
2. Reproduce throughput collapse by limiting carriers (`-Djdk.virtualThreadScheduler.parallelism=2`).
3. Fix by replacing `synchronized` with `ReentrantLock`.
4. Record a JFR session and look for `jdk.VirtualThreadPinned` events.
5. Identify other pinning sources (native frames / `synchronized` in 3rd-party libraries).

**Prove it:** Before/after throughput numbers and a JFR screenshot or event list in your `NOTES.md`.

---

## Exercise 10.4 — Request Context: `ThreadLocal` vs `ScopedValue` ★★★

**Scenario:** Propagate `tenantId` and `correlationId` through a request handled by virtual threads, including child tasks.

**Task**
1. Implement with `ThreadLocal`; count memory/overhead with 100k virtual threads.
2. Re-implement with `ScopedValue.where(TENANT, id).run(...)`.
3. Show that scoped values are immutable and automatically unbound at scope exit (no cleanup bugs).
4. Show propagation into child tasks forked in a structured scope.

**Prove it:** No leakage across requests; no `remove()` needed.

---

## Exercise 10.5 — Structured Fan-Out (`StructuredTaskScope`) ★★★★

**Scenario:** Load a user dashboard: profile, orders, recommendations — all must succeed; if any fails, cancel the rest immediately.

**Task**
1. Use `StructuredTaskScope.ShutdownOnFailure`: `fork` three subtasks, `join()`, `throwIfFailed()`.
2. Use `ShutdownOnSuccess` for "query 3 replicas, take the first answer".
3. Add a deadline with `joinUntil(Instant)`.
4. Compare with the Exercise 6.2/6.4 `CompletableFuture` versions regarding readability, cancellation, and stack traces/thread dumps.
5. Demonstrate that leaving the scope guarantees no leaked subtasks.

**Prove it:** On failure of one subtask, the others observe interruption within milliseconds.

---

## Exercise 10.6 — Virtual-Thread Spring Boot Service (benchmark) ★★★

**Scenario:** Measure what virtual threads actually buy a typical Spring Boot REST service.

**Task**
1. Create an endpoint that calls a slow (300 ms) mock downstream.
2. Run with the default Tomcat pool (200 threads), then with `spring.threads.virtual.enabled=true`.
3. Load-test (k6/wrk/Gatling) at 100, 500, 2,000 concurrent users.
4. Observe CPU-bound endpoints (no benefit) vs. IO-bound (large benefit). Check DB connection pool (HikariCP) as the new bottleneck.

**Prove it:** Latency/throughput graphs for both modes and a written conclusion about where virtual threads help.

---

## Group 10 Completion Checklist
- [ ] I can explain carriers, mounting, parking, and pinning
- [ ] I limit resources with semaphores, not pool sizes
- [ ] I can diagnose pinning using JFR/flags
- [ ] I can write structured, cancel-on-failure fan-out
