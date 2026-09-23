# Phase 3 — Atomic Variables & Lock-Free Programming

**Project:** Concurrent Order Processing Engine
**Stack:** Java 21
**Components built this phase:** `OrderIdGenerator`, `MetricsCounter`, `TreiberStack` (lock-free buffer pool)
**Files:** `src/main/java/com/orderengine/phase3/`

---

## 1. Where this phase sits

Phase 2 went below `synchronized` to explicit locks (`ReentrantLock`,
`ReentrantReadWriteLock`, `StampedLock`) — still fundamentally
lock-based: some thread can block waiting for another. This phase goes
one level further, to **lock-free** algorithms: no thread ever blocks
waiting for another thread to do anything. Every `Atomic*` class you've
already been using since Phase 1 (`AtomicInteger`, `AtomicLong`) is built
on exactly the mechanism this phase makes explicit: **compare-and-swap
(CAS)**.

---

## 2. CAS mechanics

Compare-and-swap is a single hardware instruction (`cmpxchg` on x86,
`ldrex`/`strex` or the newer `cas` on ARM) with this contract:

> `compareAndSet(location, expectedValue, newValue)`: atomically, if
> `location` currently holds `expectedValue`, set it to `newValue` and
> return true. Otherwise change nothing and return false.

That's it — one atomic hardware operation, no OS involvement, no thread
ever blocked or parked. Every lock-free algorithm in this phase is built
from the same three-step retry shape, first made explicit in
`OrderIdGenerator.nextIdManualCas()`:

```java
while (true) {
    T current = atomicRef.get();          // 1. plain read
    T next = computeNext(current);        // 2. compute desired new value
    if (atomicRef.compareAndSet(current, next)) {
        return next;                      // 3. success — done
    }
    // 4. CAS failed: someone else changed it between steps 1 and 3.
    //    Retry with fresh state. No lock was ever held.
}
```

This is **optimistic concurrency control**: assume no interference,
attempt the change, verify atomically, and only pay a retry cost on
actual conflict — versus a lock's **pessimistic** approach of preventing
any possible interference up front by blocking everyone else out, whether
or not a conflict would actually have happened.

### Progress guarantee: what "lock-free" precisely means

"Lock-free" is a specific, formal guarantee, not just "doesn't use
`synchronized`":

- **Wait-free**: every thread completes in a bounded number of steps,
  regardless of what other threads do. The strongest guarantee, rarely
  achieved for anything nontrivial.
- **Lock-free**: *system-wide* progress is guaranteed — at every retry
  round, *at least one* thread's CAS succeeds — but any *individual*
  thread could theoretically keep losing the race under adversarial
  scheduling. This is what `OrderIdGenerator`, `TreiberStack`, and the
  JDK's `Atomic*`/`LongAdder` classes provide.
- **Obstruction-free**: a thread makes progress only if it eventually runs
  without interference for long enough — weaker than lock-free.
- **Blocking** (locks): a thread can be prevented from progressing
  indefinitely by another thread that holds the lock and never releases
  it (crashes while holding it, is preempted, etc.) — the failure mode
  Phase 2 and Phase 11 deal with directly.

The practical payoff of lock-free over blocking: no thread holding a lock
can ever suspend/crash and take every other thread down with it (a real
production failure mode — one slow or wedged thread inside a
`synchronized` block stalling every other thread waiting on it). Lock-free
code trades that risk for retry overhead under contention instead.

---

## 3. `AtomicInteger`/`AtomicLong`/`AtomicReference`

All three follow the identical CAS-retry shape internally; the JDK's
`incrementAndGet()`, `getAndAdd()`, `updateAndGet()`, etc. are all
convenience wrappers around exactly the loop you wrote by hand in
`nextIdManualCas()`. Historically implemented on top of
`sun.misc.Unsafe`; modern JDKs (9+) use the public `VarHandle` API for
the same intrinsic CAS operations, which is why these classes remain
extremely cheap — the JIT compiles them down to the same raw `cmpxchg`
instruction your hand-written loop would produce, there's no meaningful
performance reason to hand-roll it yourself outside of learning.

`AtomicReference<T>` extends the same idea to object references —
`TreiberStack` uses it to CAS the `top` pointer of a linked structure.
This is where the ABA problem becomes a real concern (§4) — primitive
`AtomicLong`/`AtomicInteger` values conceptually can't have an ABA
problem that matters in the same way (an int going 5→7→5 really is just
the value 5 again, with no notion of "the same 5 revisited" mattering to
correctness), but *references* to reused objects can.

---

## 4. The ABA problem

`AbaProblemDemo.java` reproduces this **deterministically** (not
timing-dependent luck — it uses `CountDownLatch` to force the exact
interleaving every single run, unlike Phase 1's JIT-hoisting demo).

**The core issue:** a plain CAS only checks reference/value equality. If
a value changes from A to B and back to A before a paused thread resumes
its CAS, that thread's `compareAndSet(expected=A, ...)` succeeds — because
it genuinely *is* A again by reference — even though the world changed
completely in between. The paused thread has no way to know "this is A
again, but a different situation than the A I originally saw."

This matters concretely for **pooled/reused objects** — exactly
`TreiberStack`'s free-list use case in this engine. If node objects are
recycled rather than always freshly allocated (a very common real
optimization to reduce GC pressure under high throughput), the exact same
node object can legitimately reappear at the top of the stack after being
popped and pushed again by other threads, and a paused thread's stale CAS
can silently corrupt the structure — as the demo shows concretely: a
value pushed back by "thread 2" gets silently dropped, with **no
exception, no CAS failure, no error signal at all**. That silence is what
makes ABA bugs disproportionately dangerous compared to most concurrency
bugs — the program runs cleanly and just produces a wrong answer.

### The fix: `AtomicStampedReference` (and `AtomicMarkableReference`)

`AtomicStampedReference<T>` pairs every reference with an integer stamp
(version counter) and CASes both together: `compareAndSet(expectedRef,
newRef, expectedStamp, newStamp)`. Every mutation bumps the stamp, so
even when the *reference* legitimately returns to an earlier value, the
*stamp* has moved on — a paused thread's CAS now correctly fails on stamp
mismatch, forcing it to retry with fresh state instead of silently
succeeding on stale assumptions. `AbaProblemDemo`'s fixed scenario proves
this: the identical interleaving that silently corrupted the plain-CAS
version gets cleanly rejected by the stamped version.

(`AtomicMarkableReference` is the same idea with a single boolean "mark"
instead of an integer stamp — used when you need to tag a reference as
"logically deleted" alongside its value rather than version it.)

**Important nuance:** `TreiberStack.java` in this codebase does *not* use
`AtomicStampedReference` — it uses plain `AtomicReference`, and the class
javadoc explicitly calls out that it's vulnerable to ABA under node
reuse/pooling. This is intentional: most real Treiber stack usages
(including typical JDK-adjacent ones) accept this by *always allocating
fresh nodes* and letting the garbage collector reclaim popped ones,
rather than pooling — which sidesteps ABA entirely, at the cost of GC
pressure under very high throughput. Whether that trade-off is
acceptable is a real production decision, not something to default past
without thinking about — which is exactly why `AbaProblemDemo` exists as
a separate, standalone demonstration rather than silently baked into
`TreiberStack` — you should be able to see the exact failure mode before
deciding whether your use case can tolerate the "always allocate fresh"
mitigation or genuinely needs `AtomicStampedReference`.

---

## 5. `LongAdder` vs `AtomicLong` under contention

`MetricsCounter.java` puts both side by side for the engine's throughput
dashboard, and `CounterBenchmark.java` measures the difference directly.

**Why `AtomicLong` degrades under high contention:** every
`incrementAndGet()` is a CAS on *one shared memory location*. Modern CPUs
cache that location per-core; every successful write invalidates every
other core's cached copy (cache-line "ping-pong" across cores via the
cache-coherence protocol). Under heavy concurrent writers, most CAS
attempts fail and retry, and the retries themselves generate more
invalidation traffic — throughput can degrade well below what raw core
count would suggest.

**How `LongAdder` fixes this:** instead of one shared counter, it
maintains a base value plus an array of separate, padded `Cell` counters.
Under detected contention, different threads get striped across
different cells (via a per-thread hash that JDK internals adjust
dynamically as contention is detected), so concurrent increments mostly
land on *different* memory locations and don't invalidate each other's
caches. `sum()` walks the base plus every cell and adds them up on
demand.

**The trade-off — read this before reaching for `LongAdder` reflexively:**
`sum()` is not a cheap, instantaneous, linearizable snapshot the way
`AtomicLong.get()` is. It's computed by walking multiple cells, and if
increments are happening concurrently with the `sum()` call, there is no
single instant "the sum" was taken across all cells — the result can be
slightly stale relative to a true real-time count. For a periodically-
scraped dashboard counter, this is invisible and irrelevant. For a
counter feeding a decision that must be exactly, atomically correct at a
specific instant — Phase 2's inventory stock is the canonical example
in this codebase — `LongAdder` is the **wrong tool**, full stop, no matter
how much faster it benchmarks: you cannot let "the sum was a little
stale" oversell real inventory. This distinction — hot *approximate*
counters vs. exact *business-critical* counters — is the single most
important judgment call this phase teaches, more important than the raw
performance numbers.

### Running the benchmark yourself

```bash
cd phase3-atomics-and-lock-free/src/main/java
javac com/orderengine/phase3/*.java -d out
java -cp out com.orderengine.phase3.Phase3ConcurrencyTest
java -cp out com.orderengine.phase3.AbaProblemDemo
java -cp out com.orderengine.phase3.CounterBenchmark
```

Run `Phase3ConcurrencyTest` first (proves `OrderIdGenerator` never
duplicates/loses an ID under concurrency, and `TreiberStack` never
duplicates/loses a pushed item under concurrent push/pop) before trusting
any speed numbers, same discipline as Phase 2. Both passed (`OK`) when
actually run. `AbaProblemDemo` also reproduced exactly as described —
the vulnerable version silently lost the re-pushed "A" node every time,
the stamped version correctly rejected the stale CAS every time
(`compareAndSet(...) succeeded = false`) — since it's a *deterministic*
demo (forced interleaving via `CountDownLatch`, not timing luck), it
reliably reproduces on any hardware, including this sandbox.

**`CounterBenchmark`'s real measured numbers, and an important caveat**
about them:

```
1 thread:   AtomicLong    40ms   LongAdder    71ms  (LongAdder 0.56x — SLOWER)
4 threads:  AtomicLong   136ms   LongAdder   267ms  (LongAdder 0.51x — SLOWER)
16 threads: AtomicLong   515ms   LongAdder   871ms  (LongAdder 0.59x — SLOWER)
32 threads: AtomicLong   982ms   LongAdder 1,797ms  (LongAdder 0.55x — SLOWER)
```

This is the *opposite* of what §5 predicts, and — exactly as Phase 2's
real benchmark numbers turned out — the explanation is the sandbox's
hardware, not an error in the reasoning: **this environment has exactly
one CPU core** (`nproc` reports 1). `LongAdder`'s entire value proposition
is relieving cross-core cache-line contention by striping increments
across separate memory locations different cores can update
independently. With only one core, there is no cross-core cache
contention to relieve in the first place — nothing is ever truly
simultaneous — so `LongAdder`'s striping machinery (hashing to a cell,
indirection through the cells array) is pure added overhead with zero
offsetting benefit, consistently *slower* than `AtomicLong` at every
thread count tested here, not just at low contention.

This doesn't invalidate §5's reasoning — it validates the mechanism
description precisely, by showing what happens when the mechanism's
precondition (genuine multi-core hardware contention) isn't met. On real
multi-core hardware, expect `AtomicLong` to degrade as thread count
climbs past core count while `LongAdder` holds up much better, exactly
as originally described — run `CounterBenchmark` yourself on real
hardware to see the crossover point directly. The broader lesson,
consistent with Phase 2's own real-numbers surprise: **every claim about
what a concurrency technique buys you under contention is implicitly a
claim about hardware with real parallelism to contend over** — always
verify that precondition before trusting a benchmark result, including
the ones in this document.

---

## 6. Common production bugs from this phase's concepts

1. **Forgetting the retry loop and only trying CAS once.** `if
   (ref.compareAndSet(old, new)) { ... }` with no surrounding loop
   silently does nothing on the (routine, expected) case where another
   thread won the race first. CAS failing is a normal, expected outcome
   under contention, not an error — every correct usage needs a retry
   loop (or explicit "give up after N attempts" logic for cases where
   infinite retry isn't acceptable).

2. **ABA corruption from pooled/reused references**, as demonstrated
   above — specifically dangerous in free-lists, object pools, and any
   lock-free structure built on `AtomicReference` where node reuse is an
   optimization someone adds later without realizing the original
   algorithm assumed fresh allocation.

3. **Reaching for `LongAdder` for a value that needs to be read
   atomically/consistently**, not just incremented fast — e.g. someone
   "optimizing" `InventoryLedger`'s stock counter (Phase 2) by swapping
   it to `LongAdder` because it benchmarks faster, without recognizing
   that `sum()`'s non-atomic-across-cells nature can now let a
   `reserve()` call race against a concurrent update and observe stale
   stock, undermining the exact correctness Phase 2 built.

4. **CAS loops with expensive `computeNext()` logic under high
   contention** — if computing the new value from the old one is costly
   (not the case for a simple increment, but common in richer lock-free
   structures), high contention means that work gets redone repeatedly
   on every failed retry, sometimes making the lock-free version slower
   in practice than a short critical section under a lock would have
   been. Lock-free is not unconditionally faster — it's a genuine
   trade-off that depends on contention level and per-attempt cost.

5. **Assuming `AtomicReference<SomeMutableObject>` makes the *object*
   thread-safe.** It only makes the *reference* (which object the
   variable currently points to) atomically swappable — it says nothing
   about safe concurrent mutation of the object's own fields once you
   have a reference to it. This is why `TreiberStack`'s `Node.value` is
   effectively treated as immutable once constructed — mutating node
   contents after publication would reopen exactly the Phase 1 visibility
   problem the atomic reference itself doesn't protect against.

---

## 7. Interview Q&A (junior → staff)

**Q (junior): What does compare-and-swap actually do, in one sentence?**
A: Atomically checks whether a memory location still holds an expected
value, and if so, swaps in a new value — succeeding or failing as a
single indivisible hardware operation, with no thread ever blocked
waiting for another.

**Q (junior/mid): Why does every CAS-based update need a retry loop?**
A: A failed CAS means another thread changed the value first — that's an
expected, routine outcome under contention, not an error condition. The
loop re-reads the current value and retries the whole compute-and-swap
step against fresh state until it succeeds.

**Q (mid): What's the difference between "lock-free" and "wait-free"?**
A: Lock-free guarantees system-wide progress — at least one thread
succeeds on every contended retry round — but an individual thread could
theoretically keep losing races indefinitely under adversarial
scheduling. Wait-free guarantees every individual thread completes in a
bounded number of steps regardless of other threads' behavior — a
strictly stronger and much harder to achieve property.

**Q (mid): Describe the ABA problem in your own words, and why it's
specifically dangerous for pooled/reused objects.**
A: A CAS only compares whether a reference/value currently matches what
was expected — it can't distinguish "this is the same value because
nothing changed" from "this is the same value again because it changed
away and coincidentally changed back." With object pooling, the exact
same object identity can legitimately reappear after being freed and
reallocated, so a thread paused mid-CAS can have its stale compare
succeed against a completely different real-world state, silently
corrupting a structure with no exception or failure signal at all.

**Q (mid/senior): When would you choose `LongAdder` over `AtomicLong`,
and when would that choice be a mistake?**
A: Choose `LongAdder` for high-write, rarely-read hot counters — metrics,
request counts, throughput dashboards — where losing a precise,
instantaneous, linearizable read in exchange for much higher write
throughput under contention is an acceptable trade. It's a mistake for
any counter that feeds a correctness-critical decision requiring an exact
value at a specific instant — e.g. inventory stock gating whether a sale
can proceed — because `sum()` is not atomic across its internal cells and
can return a slightly stale value under concurrent writes.

**Q (senior/staff): You're asked to review a PR that replaces a
`synchronized`-guarded free-list with a lock-free `AtomicReference`-based
stack for a hot object pool, citing a throughput benchmark. What do you
check before approving?**
A: Points worth hitting: (1) whether pooled nodes are reused (same object
identity recycled) — if so, this is exactly the ABA-vulnerable shape from
this phase, and the PR needs either `AtomicStampedReference`/
`AtomicMarkableReference`, or a switch to always-fresh allocation, not a
plain `AtomicReference`; (2) whether the benchmark measured realistic
contention levels and thread counts matching production traffic, not just
a low-contention microbenchmark where the lock-free version wins for
reasons that won't hold under real load; (3) whether the "faster"
measurement is even the right axis — a lock-free structure trades
predictable-but-blocking behavior for retry-storm risk under pathological
contention, which can matter more than average-case throughput for tail
latency; (4) whether correctness tests equivalent to
`Phase3ConcurrencyTest` (concurrent push/pop with uniqueness/no-loss
assertions) exist and pass, not just a throughput number — a faster,
subtly-corrupting free list is a worse production outcome than the slower
correct one, exactly as with Phase 2's locking trade-offs.

---

## 8. What's next

**Phase 4 — Thread Pools & Executors.** Everything so far has run raw
`Thread` objects directly. Real systems don't do that — they submit work
to managed pools. We cover `ThreadPoolExecutor` internals (core size, max
size, work queue, rejection policies — and what actually happens, in
order, when a task is submitted), custom `ThreadFactory`s, the pool-
sizing math for CPU-bound vs I/O-bound work, and `ScheduledExecutorService`.
Component built: tuned, purpose-specific worker pools for each pipeline
stage of the engine — this is where `OrderIdGenerator`, `MetricsCounter`,
and `TreiberStack` all get put to real use inside actual worker threads
instead of raw demo `Thread`s.

Say **"next"** when ready.
