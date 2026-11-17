# Java Concurrency & Multithreading Mastery
### Real-World Backend Engineering Exercises — From Basic to Advanced

A hands-on practice curriculum: **14 groups, 80+ exercises**. Every exercise is a real backend problem (payments, inventory, rate limiting, caching, fan-out APIs, job schedulers) with a scenario, tasks, a "Prove it" acceptance check, and often a stretch goal.

**Target:** Java 21+ (virtual threads are used in Group 10). Each group has its own `README.md`.

---

## Learning Path

| # | Group | Level | Exercises | Core Concepts |
|---|---|---|---|---|
| 1 | [Thread Fundamentals](01-thread-fundamentals/README.md) | Beginner | 6 | Thread lifecycle, join, daemon, interruption, ThreadLocal |
| 2 | [Synchronization & Memory Model](02-synchronization-and-memory-model/README.md) | Beginner → Intermediate | 7 | Races, `synchronized`, `volatile`, happens-before, wait/notify, deadlock |
| 3 | [Explicit Locks](03-explicit-locks/README.md) | Intermediate | 6 | `ReentrantLock`, `Condition`, `ReadWriteLock`, `StampedLock`, striping |
| 4 | [Atomics & Lock-Free](04-atomics-and-lock-free/README.md) | Intermediate → Advanced | 5 | CAS, `LongAdder`, ABA, Treiber/Michael–Scott |
| 5 | [Executors & Thread Pools](05-executors-and-thread-pools/README.md) | Intermediate | 7 | `ThreadPoolExecutor`, rejection, scheduling, shutdown, sizing |
| 6 | [CompletableFuture](06-completablefuture/README.md) | Intermediate → Advanced | 7 | Composition, fan-out, timeouts, retries, pool starvation |
| 7 | [Concurrent Collections](07-concurrent-collections/README.md) | Intermediate | 6 | CHM, COW, BlockingQueues, SkipList |
| 8 | [Synchronizers](08-synchronizers/README.md) | Intermediate | 5 | Latch, Barrier, Semaphore, Phaser, Exchanger |
| 9 | [Fork/Join & Parallel Streams](09-fork-join-and-parallel-streams/README.md) | Intermediate → Advanced | 5 | RecursiveTask, work stealing, stream pitfalls |
| 10 | [Virtual Threads & Structured Concurrency](10-virtual-threads-and-structured-concurrency/README.md) | Advanced | 6 | Virtual threads, pinning, ScopedValue, StructuredTaskScope |
| 11 | [Concurrency Bugs & Debugging](11-concurrency-bugs-and-debugging/README.md) | Advanced | 6 | Deadlock, livelock, starvation, thread dumps, leaks |
| 12 | [Concurrency Design Patterns](12-concurrency-design-patterns/README.md) | Advanced | 8 | Rate limiter, breaker, bulkhead, single-flight, actor, scheduler |
| 13 | [Testing & Observability](13-testing-and-observability/README.md) | Advanced | 5 | Stress tests, jcstress, JFR, Micrometer, OTel propagation |
| 14 | [Capstone: Payment Authorization Engine](14-capstone-payment-authorization-engine/README.md) | Expert | 1 project | Everything combined |

Difficulty markers: ★ easy · ★★ moderate · ★★★ challenging · ★★★★ advanced · ★★★★★ expert.

---

## How to Work Through Each Exercise

1. **Read the scenario** and write down what could go wrong before coding.
2. **Write the broken version first.** Reproduce the bug (lost update, hang, leak, overselling). A bug you've seen is a bug you'll recognise.
3. **Fix it**, then verify with the **Prove it** criteria.
4. **Measure** where asked (JMH or a careful warm-up harness). Never claim a speed-up without numbers.
5. **Write `NOTES.md`** per exercise: what broke, why, what fixed it, and the rule you extracted.
6. Attempt the **Stretch** goals once the core passes.

## Suggested Project Layout

```
concurrency-practice/
├── pom.xml                      # Java 21, JUnit 5, Awaitility, JMH, Micrometer
└── src/
    ├── main/java/com/practice/concurrency/
    │   ├── g01_threads/e1_1_welcome_email/...
    │   ├── g02_sync/e2_2_inventory/...
    │   └── ...
    └── test/java/...            # stress tests live next to the code
```

## Ground Rules
- Name every thread and every pool.
- Never use `Thread.sleep` to "fix" correctness; use it only to *simulate latency*.
- Every executor you create must be shut down.
- No unbounded queues or pools in "production-style" exercises.
- Always `finally` for `unlock()`, `release()`, `countDown()`, `remove()`.
- Never swallow `InterruptedException`.
- Test with more threads than cores, and run each stress test many times.

## Tooling to Install
- JDK 21+ (with `jcmd`, `jstack`, JFR)
- JMH (microbenchmarks), jcstress (memory-model tests)
- JDK Mission Control or VisualVM
- Awaitility, JUnit 5
- k6 / wrk / Gatling for load tests (Group 10, 13)

## Progress Tracker
- [ ] G1 Thread Fundamentals
- [ ] G2 Synchronization & Memory Model
- [ ] G3 Explicit Locks
- [ ] G4 Atomics & Lock-Free
- [ ] G5 Executors & Thread Pools
- [ ] G6 CompletableFuture
- [ ] G7 Concurrent Collections
- [ ] G8 Synchronizers
- [ ] G9 Fork/Join & Parallel Streams
- [ ] G10 Virtual Threads & Structured Concurrency
- [ ] G11 Concurrency Bugs & Debugging
- [ ] G12 Concurrency Design Patterns
- [ ] G13 Testing & Observability
- [ ] G14 Capstone

## Mastery Self-Test (answer without notes)
1. What are the three problems the Java Memory Model addresses (atomicity, visibility, ordering) and which primitive solves which?
2. Predict what `ThreadPoolExecutor` does with the (core+max+queue+1)th task.
3. Why can a timed-out `CompletableFuture` still be consuming a thread?
4. How does a virtual thread get pinned, and how do you find out?
5. How would you guarantee per-key ordering while processing different keys in parallel?
6. How do you reproduce a one-in-a-million race condition?
