# Group 8 — Synchronizers (Coordination Utilities)

**Level:** Intermediate | **Exercises:** 5 | **Prerequisites:** Groups 2–5

## What you will master
- `CountDownLatch` (one-shot gate)
- `CyclicBarrier` (reusable rendezvous)
- `Semaphore` (permits, resource limiting)
- `Phaser` (dynamic multi-phase coordination)
- `Exchanger` (pairwise hand-off)

---

## Exercise 8.1 — Startup Readiness Gate (`CountDownLatch`) ★★

**Scenario:** The app must not accept traffic until DB, cache, message broker, and config service are all healthy. Each health check runs in parallel.

**Task**
1. Start 4 health checks (random 200–2,000 ms) in parallel; each calls `countDown()` in `finally`.
2. Main awaits with a 3 s timeout; if the timeout expires, print which dependencies are still down.
3. Use a second latch as a **start gun**: 100 request threads are created but wait on `startSignal.await()` and release simultaneously — useful for concurrency tests.
4. Show what happens if a task throws before `countDown()` (without `finally`).

**Prove it:** Readiness reported in ≈ max(check durations); the start-gun test produces measurably simultaneous start (log nanoTime spread).

---

## Exercise 8.2 — Batch ETL Stages (`CyclicBarrier`) ★★★

**Scenario:** 4 workers each process a slice of a file. They must all finish "extract" before anyone starts "transform", and all finish "transform" before "load".

**Task**
1. Use a `CyclicBarrier(4, barrierAction)` where the action logs a phase summary.
2. Reuse the same barrier across 3 phases.
3. Make one worker throw an exception mid-phase: observe `BrokenBarrierException` in the others. Handle it and abort the batch cleanly.
4. Add `await(timeout)` and handle `TimeoutException`.

**Prove it:** No worker enters phase N+1 before all finish phase N (assert with a phase counter); broken-barrier path aborts all workers.

---

## Exercise 8.3 — Third-Party API Rate Bulkhead (`Semaphore`) ★★★

**Scenario:** A partner API allows only 10 concurrent calls. You have 200 worker threads.

**Task**
1. Guard the call with `Semaphore(10)`; verify via a counter that concurrent in-flight calls never exceed 10.
2. Use `tryAcquire(timeout)` and fail fast with a `BulkheadFullException` instead of queuing forever.
3. Always `release()` in `finally`; demonstrate the permit leak if you don't.
4. Build a simple **connection pool** with a `Semaphore` plus a `ConcurrentLinkedQueue<Connection>`.
5. Compare fair vs non-fair semaphore under contention.

**Prove it:** `maxObservedConcurrency == 10`; zero leaked permits after 100,000 calls with random failures.

---

## Exercise 8.4 — Multi-Stage Data Migration (`Phaser`) ★★★★

**Scenario:** Migrating tenants between databases; the number of worker threads changes as tenants are added during the migration. Phases: snapshot → copy → verify → cutover.

**Task**
1. Use `Phaser` with `register()` / `arriveAndAwaitAdvance()` / `arriveAndDeregister()`.
2. Dynamically register a new worker while the migration is running.
3. Override `onAdvance` to log phase completion and terminate after cutover.
4. Compare the same flow with `CyclicBarrier` and list what you can't do with it.

**Prove it:** Correct phase ordering with a worker joining mid-way and another leaving early.

---

## Exercise 8.5 — Double-Buffered Log Writer (`Exchanger`) ★★★

**Scenario:** A producer fills a buffer with log lines while a writer thread flushes another buffer to disk; they swap when the producer's is full and the writer's is empty.

**Task**
1. Implement with `Exchanger<List<String>>`.
2. Handle the end-of-stream (a final partial buffer) and shutdown.
3. Measure throughput vs a single `BlockingQueue<String>` approach.

**Prove it:** All lines written, in order; no allocations per line beyond the two buffers.

---

## Group 8 Completion Checklist
- [ ] I can choose between latch, barrier, phaser, and semaphore
- [ ] I always release permits / count down in `finally`
- [ ] I can handle broken barriers and timeouts
