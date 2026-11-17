# Group 4 — Atomics & Lock-Free Programming

**Level:** Intermediate → Advanced | **Exercises:** 5 | **Prerequisites:** Groups 2–3

## What you will master
- `AtomicInteger/Long/Boolean/Reference`, CAS (compare-and-set)
- `LongAdder` / `LongAccumulator` under high contention
- CAS retry loops and `updateAndGet` / `accumulateAndGet`
- Immutable state + `AtomicReference` swap
- The ABA problem, `AtomicStampedReference`
- Lock-free data structures (Treiber stack)

---

## Exercise 4.1 — Request Counter Shootout ★

**Scenario:** An API gateway counts requests per second from 64 threads.

**Task**
1. Implement the counter with: `synchronized`, `ReentrantLock`, `AtomicLong`, `LongAdder`.
2. Benchmark with 1, 4, 16, 64 threads (JMH preferred; else warm-up + multiple runs).
3. Explain why `LongAdder` wins under contention (cells / striping) and what it costs (`sum()` is not an atomic snapshot).

**Prove it:** A table + graph of ops/sec vs threads for all four.

---

## Exercise 4.2 — Latency Tracker (CAS loops) ★★

**Scenario:** Track max, min, and total latency of calls across threads without locks.

**Starter**
```java
AtomicLong max = new AtomicLong(Long.MIN_VALUE);
void record(long latency) {
    long cur;
    do { cur = max.get(); if (latency <= cur) return; }
    while (!max.compareAndSet(cur, latency));
}
```

**Task**
1. Implement `record()` for max, min, count, and sum using CAS.
2. Re-implement max/min using `accumulateAndGet(latency, Math::max)` and `LongAccumulator`.
3. Explain what happens if the lambda passed to `updateAndGet` has side effects (it may run multiple times).
4. Add a `snapshot()` that returns a consistent (count, sum, max, min). Why is reading four atomics separately *not* consistent? Fix by packing state into an immutable record in an `AtomicReference`.

**Prove it:** Under 32 writer threads the final values equal a single-threaded reference computation.

---

## Exercise 4.3 — Lock-Free Circuit Breaker State ★★★

**Scenario:** A circuit breaker has states CLOSED / OPEN / HALF_OPEN with failure counts and an open-until timestamp. Many threads query `allowRequest()`.

**Task**
1. Model state as an immutable `record BreakerState(Status status, int failures, long openUntilNanos)`.
2. Hold it in an `AtomicReference<BreakerState>`; every transition is a `compareAndSet` loop.
3. Ensure only **one** probe request is allowed through in HALF_OPEN (CAS decides who wins).
4. Test with 100 threads hammering a failing downstream; verify the breaker opens after N failures exactly once and exactly one thread probes after the timeout.

**Prove it:** Deterministic transition log; no illegal transitions (e.g. OPEN → CLOSED without HALF_OPEN) in 1M operations.

---

## Exercise 4.4 — Object Pool & the ABA Problem ★★★★

**Scenario:** A lock-free stack is used as a free-list for reusable `ByteBuffer`s.

**Task**
1. Implement a **Treiber stack** (`push`/`pop` with CAS on `head`).
2. Construct an ABA scenario: thread 1 reads head A and gets paused; thread 2 pops A, pops B, pushes A back; thread 1's CAS succeeds incorrectly and corrupts the stack. Use a deterministic interleaving with latches (reuse of nodes is what makes it real).
3. Fix with `AtomicStampedReference` (version counter).
4. Discuss why Java's GC makes ABA rarer than in C++ but not impossible when nodes are pooled.

**Prove it:** Corruption reproduced and then prevented; stress test of 1M push/pop shows no lost or duplicated buffers.

---

## Exercise 4.5 — Michael–Scott Queue (stretch) ★★★★★

**Scenario:** Build the queue that backs `ConcurrentLinkedQueue`.

**Task**
1. Implement `offer`/`poll` with CAS on `head`/`tail` plus "helping" (advance a lagging tail).
2. Verify linearizability with a stress test: every produced item consumed exactly once, per-producer FIFO order preserved.
3. Benchmark against `ConcurrentLinkedQueue` and `LinkedBlockingQueue`.
4. (Optional) Re-implement CAS using `VarHandle` instead of `AtomicReference` and compare memory.

**Prove it:** 8P/8C, 10M items, zero loss, zero duplicates, per-producer order preserved.

---

## Group 4 Completion Checklist
- [ ] I can write a correct CAS loop from memory
- [ ] I know when `LongAdder` beats `AtomicLong` and its trade-off
- [ ] I can model multi-field state as one immutable object behind an `AtomicReference`
- [ ] I can explain and fix ABA
