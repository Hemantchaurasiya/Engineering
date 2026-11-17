# Group 11 — Concurrency Bugs & Debugging

**Level:** Advanced | **Exercises:** 6 | **Prerequisites:** Groups 1–8

## What you will master
- Deadlock, livelock, starvation, thread-pool starvation deadlock
- Reading thread dumps (`jstack`, `jcmd Thread.print`)
- Programmatic deadlock detection (`ThreadMXBean`)
- Memory leaks from thread locals
- Reproducing "Heisenbugs" with stress harnesses
- Lock contention analysis with JFR

---

## Exercise 11.1 — Production Is Hung: Read the Thread Dump ★★★

**Scenario:** You receive a thread dump from a hung service (you will create it).

**Task**
1. Build a service with at least 3 distinct problems: a lock-ordering deadlock, a thread blocked forever on `BlockingQueue.take()`, and a thread waiting on a lock held by a thread stuck in an infinite loop.
2. Take 3 dumps 5 s apart with `jstack`; diff them to find what's not progressing.
3. Write a `DeadlockDetector` using `ThreadMXBean.findDeadlockedThreads()` that logs an alert every 10 s.
4. Document how you identify each problem in the dump (thread states, "waiting to lock", "locked", owner).

**Prove it:** A written runbook, "How to diagnose a hung Java service".

---

## Exercise 11.2 — Two Services, One Retry Storm (livelock) ★★★

**Scenario:** Two workers try to acquire both of two resources; on failure each releases and retries immediately — and they keep colliding.

**Task**
1. Implement with `tryLock` and immediate retry; show both threads remain RUNNABLE but make no progress.
2. Fix with randomized back-off (jitter).
3. Fix with ordering. Compare which is more robust.
4. Relate to retry storms in microservices: all clients retry at the same fixed interval → synchronized spikes. Simulate and fix with jitter.

**Prove it:** Progress counters flat before the fix, increasing after.

---

## Exercise 11.3 — Thread-Pool Starvation Deadlock ★★★★

**Scenario:** A task running on a single-threaded (or small fixed) executor submits a subtask to the **same** executor and waits for it.

**Task**
1. Reproduce with `newFixedThreadPool(2)` and 2 parent tasks each waiting for a child.
2. Observe that no thread is "deadlocked" per `ThreadMXBean` (no locks!) yet the system is hung.
3. Fix with (a) separate pools per layer, (b) `CompletableFuture` composition without blocking, (c) an unbounded or larger pool (and explain why that's a hack).
4. Explain how this shows up in real systems (e.g. controller → service → repository using one shared pool with `@Async`).

**Prove it:** Test with timeout that fails before the fix and passes after.

---

## Exercise 11.4 — Starvation & Priority Inversion ★★★

**Task**
1. Create a low-priority batch job that holds a lock needed by a high-priority interactive request.
2. Add medium-priority CPU-hungry threads and observe the high-priority request being delayed (conceptually priority inversion; Java thread priorities are only hints, so measure carefully).
3. Mitigate: shorter critical sections, `tryLock` with timeout, separate pools, fair locks.
4. Show a reader/writer starvation example and fix it.

**Prove it:** p99 latency of the interactive path before vs after mitigation.

---

## Exercise 11.5 — The Heisenbug Lab ★★★★

**Scenario:** "Duplicate payment, one in a million". Reproduce a rare race.

**Task**
1. Write a subtly broken idempotency check: `if (!processed.contains(id)) { processed.add(id); charge(id); }` using a `ConcurrentHashMap.newKeySet()`.
2. Build a stress harness (start-gun latch + many iterations + varied thread counts) to expose duplicates; count how many runs it takes.
3. Use [jcstress](https://github.com/openjdk/jcstress) to write a formal test for the broken and fixed versions (`Set.add` return value).
4. Explore how adding random `Thread.yield()` / short sleeps ("chaos injection") increases bug visibility.

**Prove it:** Fixed version passes 1M iterations; broken one fails in a measurable number of iterations.

---

## Exercise 11.6 — ThreadLocal Leak in a Pool ★★★

**Scenario:** A web app deployed to a servlet container leaks classloaders on redeploy because of `ThreadLocal`.

**Task**
1. Store a large object (e.g. 10 MB) in a `ThreadLocal` in a fixed pool of 8 threads; never `remove()`. Watch heap (`jcmd GC.heap_info` / VisualVM).
2. Capture a heap dump and find the retaining path through `Thread.threadLocals`.
3. Fix with `remove()` in `finally`; verify via another dump.
4. Discuss `InheritableThreadLocal` hazards and virtual-thread implications.

**Prove it:** Heap-usage graph before and after fix.

---

## Group 11 Completion Checklist
- [ ] I can find a deadlock from a thread dump in minutes
- [ ] I can distinguish deadlock, livelock, and starvation
- [ ] I can build a stress harness that reproduces a rare race
- [ ] I know the memory risks of `ThreadLocal`
