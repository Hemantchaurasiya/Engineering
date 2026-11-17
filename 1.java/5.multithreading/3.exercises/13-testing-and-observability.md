# Group 13 — Testing, Profiling & Observability of Concurrent Code

**Level:** Advanced | **Exercises:** 5 | **Prerequisites:** Groups 5–11

## What you will master
- Deterministic and stress tests for concurrent code
- Async assertions (Awaitility)
- Formal concurrency testing (jcstress)
- JFR / thread-dump based diagnosis, contention analysis
- Executor and thread metrics (Micrometer)
- OpenTelemetry context propagation across threads

---

## Exercise 13.1 — Deterministic Concurrency Tests ★★★

**Task**
1. Build a reusable `ConcurrentTestHarness.run(int threads, int iterations, Runnable task)` using a start-gun `CountDownLatch` and a completion latch; propagate any exception from any thread to the test.
2. Test your inventory (Exercise 2.2), rate limiter (12.2), and cache (12.6) with it.
3. Avoid `Thread.sleep()` for synchronization: use latches, barriers, and `Future.get(timeout)`.
4. Add a hard test timeout (`@Timeout`) so deadlocks fail the build rather than hang it.

**Prove it:** Tests are fast (< 2 s each), fail reliably on seeded bugs, and never hang CI.

---

## Exercise 13.2 — Async Assertions with Awaitility ★★

**Task**
1. Test a `CompletableFuture` pipeline and a scheduled task using Awaitility's `await().atMost(...).untilAsserted(...)`.
2. Replace all fixed sleeps in earlier tests.
3. Test timeouts and retries with a fake `Clock` / controllable scheduler.

**Prove it:** Zero fixed sleeps in your test suite; no flaky failures over 100 repeated runs.

---

## Exercise 13.3 — jcstress: Prove the Memory Model ★★★★★

**Task**
1. Set up the jcstress Maven module.
2. Write tests for: non-volatile flag visibility, unsafe publication of a mutable object, DCL without `volatile`, `volatile` increment, and `AtomicInteger` increment.
3. Interpret the outcome tables: ACCEPTABLE / FORBIDDEN / INTERESTING.
4. Run on different hardware if possible (x86 vs ARM show different results).

**Prove it:** A written explanation of each observed outcome, mapped to happens-before rules.

---

## Exercise 13.4 — Profile Contention with JFR & Metrics ★★★

**Scenario:** A service is slow only under load.

**Task**
1. Create a service with hidden contention: one hot `synchronized` method, one undersized pool, one slow downstream.
2. Capture a JFR recording (`jcmd <pid> JFR.start`) and analyse `jdk.JavaMonitorEnter`, `jdk.ThreadPark`, and thread states in JDK Mission Control.
3. Register executor/queue metrics with Micrometer (`ExecutorServiceMetrics`) and publish gauges for active threads, queue depth, rejected tasks.
4. Build a dashboard (Prometheus + Grafana, or simply console output) and find each of the three problems from the data alone.

**Prove it:** A short report: symptom → evidence → root cause → fix for each issue.

---

## Exercise 13.5 — Trace Context Across Thread Boundaries (OpenTelemetry) ★★★★

**Scenario:** Traces break into disconnected fragments when work moves to a thread pool or a `CompletableFuture` stage.

**Task**
1. Create a span for an HTTP request; submit work to an executor; show the child work has **no** parent span.
2. Fix with `Context.current().wrap(runnable)` / `Context.taskWrapping(executor)`.
3. Do the same for `CompletableFuture` stages, scheduled tasks, and virtual threads.
4. Propagate MDC (logging context) alongside the trace context; verify trace ids appear in log lines from worker threads.

**Prove it:** One unbroken trace across 3 thread hops, with trace id present in all logs.

---

## Group 13 Completion Checklist
- [ ] My concurrent tests are deterministic or statistically rigorous, never sleep-based
- [ ] I can profile contention and thread-pool saturation
- [ ] I propagate trace/log context across every async boundary
