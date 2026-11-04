# Phase 2 — Synchronization & Locks

**Project:** Concurrent Order Processing Engine
**Stack:** Java 21
**Component built this phase:** `InventoryLedger` (inventory-service stock tracking), three implementations
**Files:** `src/main/java/com/orderengine/phase2/`

---

## 1. Where this phase sits

Phase 1 established that shared mutable state needs a happens-before edge
between writer and reader, or you get visibility bugs; and that compound
operations need atomicity, or you get lost updates. `synchronized` gives
you both, in one tool, and it's usually your first correct answer.

This phase is about what happens *after* correctness is established: you
profile the running system and find that a correct, `synchronized`-based
component is a throughput bottleneck — specifically because it serializes
read-only operations against each other for no reason. `InventoryLedger`
is built three times to make that concrete: same behavior, same
correctness guarantees, three different concurrency strategies, with
measured throughput differences between them.

---

## 2. How `synchronized` actually works under the hood

Every Java object has an object header containing a **mark word** — a
machine-word-sized field that, among other things, encodes the object's
current lock state. The JVM escalates through lock states as contention
increases, because acquiring an uncontended lock should be nearly free,
and the JVM goes to real lengths to make that true:

1. **No lock / unlocked** — mark word just holds the object's normal
   hash-code/GC-age bits.

2. **Lightweight (thin) locking** — when a thread enters a `synchronized`
   block on an object with no contention, the JVM doesn't call into the
   OS at all. It CAS-writes a pointer into the mark word pointing at a
   lock record on the entering thread's own stack. This is extremely
   cheap — a single CAS, no syscall, no OS-level blocking involved. This
   is the path taken by the vast majority of `synchronized` blocks in
   real programs, because most locks are held briefly and rarely
   contended at the exact instant of acquisition.

3. **Heavyweight (inflated) locking** — when a second thread tries to
   enter the same monitor while it's held (or after enough spinning
   fails), the lock **inflates** to a real OS-level monitor. The waiting
   thread is parked (transitions to `BLOCKED`, visible in thread dumps)
   and woken by the OS scheduler when the lock is released. This is
   dramatically more expensive than the CAS path — a syscall, a context
   switch, and OS-level queue bookkeeping.

   *Note on biased locking:* older JVM material (and older interview
   prep) will mention a fourth state, **biased locking** — optimizing for
   the case where the *same* thread re-acquires a lock repeatedly with no
   other thread ever contending it, by skipping the CAS entirely after
   the first acquisition. This was removed in JDK 15 (JEP 374) because
   its complexity cost stopped being worth its benefit on modern
   hardware, where the CAS in lightweight locking got cheap enough that
   biasing barely helped. On Java 21 (this project's baseline) you'll
   never see it — if an interviewer asks about it, it's worth knowing it
   existed and why it's gone, not assuming it's still active.

Once inflated, a monitor stays heavyweight even after contention
subsides — it does not automatically deflate back to lightweight during
normal operation (deflation happens only during certain JVM safepoint
operations, notably some GC cycles). This is *why* a single hot,
contended lock can leave a lasting throughput scar on a service even
after the contention spike passes, until the next relevant safepoint.

**Key implication for this phase:** `InventoryLedgerSynchronized` isn't
slow because `synchronized` is inherently slow — an uncontended
`synchronized` block is nearly free. It's slow under this workload
specifically *because* the read-heavy traffic shape guarantees real,
sustained contention, which forces the monitor to inflate and every
reader to pay the heavyweight/OS-blocking cost, even though none of them
are mutating anything and none of them logically need to wait on each
other at all.

---

## 3. `ReentrantLock` vs `synchronized`

`ReentrantLock` (in `java.util.concurrent.locks`) gives you the same
mutual exclusion and happens-before guarantees as `synchronized`, as an
explicit object instead of a language keyword, which buys you:

- **`tryLock()`** (with or without a timeout) — attempt to acquire
  without blocking forever; essential for avoiding deadlock (Phase 11)
  and for implementing timeouts on critical sections.
- **Interruptible acquisition** (`lockInterruptibly()`) — a thread
  blocked waiting for the lock can be interrupted out of the wait,
  which plain `synchronized` cannot do.
- **Fairness policy** — `new ReentrantLock(true)` for FIFO acquisition
  order (see §5).
- **Multiple `Condition`s per lock** — `synchronized`'s `wait/notify`
  gives you exactly one implicit condition per monitor; `ReentrantLock`
  lets you create several independent `Condition`s (e.g., "not full" and
  "not empty" on the same lock for a bounded buffer), which is directly
  useful in Phase 6's producer-consumer work.

The cost: you must manually `unlock()` in a `finally` block — `synchronized`
guarantees release even on exception or unexpected return, `ReentrantLock`
does not, and a missed `unlock()` is a very real production bug (deadlocks
every subsequent acquirer forever). Every `ReentrantLock` acquisition in
this codebase follows the mandatory pattern:

```java
lock.lock();
try {
    // critical section
} finally {
    lock.unlock();
}
```

`InventoryLedgerReadWriteLock` and `InventoryLedgerStamped` both use lock
types built on the same explicit-lock family (`ReentrantReadWriteLock`,
`StampedLock`) and follow this same acquire/try/finally-release discipline
throughout.

---

## 4. `ReentrantReadWriteLock` — splitting the lock by intent

`InventoryLedgerReadWriteLock` fixes the throughput problem directly:
`checkAvailability()` takes the **read lock**, `reserve()`/`release()`
take the **write lock**. Any number of readers can hold the read lock
concurrently — they only block against an active or waiting writer, not
against each other. Correctness is unaffected: acquiring/releasing either
lock still gives you the same happens-before guarantee a monitor
lock/unlock gives (JLS 17.4.5) — you're not trading correctness for
speed here, only removing unnecessary serialization between readers.

Two things worth internalizing, both covered in the code comments and
worth re-reading there:

- **Fairness is a real trade-off, not a default to copy blindly.** Fair
  mode (`true`) gives FIFO ordering and prevents writer starvation, at a
  throughput cost (no barging). Non-fair mode (`false`, the default) is
  faster in the common case but can starve writers indefinitely under
  sustained read pressure, because a new reader can always barge ahead of
  a waiting writer. `InventoryLedgerReadWriteLock` uses fair mode
  deliberately, because a starved `reserve()` call is a real checkout
  failing under load, which is worse than a modest throughput hit.
- **Downgrading is supported, upgrading is not.** Write→read (holding the
  write lock, then also acquiring the read lock before releasing the
  write lock) is a supported, useful pattern. Read→write is not
  supported and will deadlock a thread against itself — you must fully
  release the read lock before attempting to acquire the write lock.

---

## 5. `StampedLock` — optimistic reads

`InventoryLedgerStamped` goes further. Even a "shared" read lock still
has real cost at scale: every reader acquiring/releasing
`ReentrantReadWriteLock`'s read lock does an atomic update to a shared
reader count, and that shared mutable counter becomes a cache-line
contention point across cores as concurrent readers increase — cores
fighting over which one gets to own that cache line for its atomic
update. `StampedLock`'s optimistic mode sidesteps this: `tryOptimisticRead()`
takes no lock at all — just reads a stamp (essentially a sequence
number). The reader reads the data, then calls `validate(stamp)` to check
whether a write happened during the read. Only on validation failure does
it fall back to a real read lock.

This removes shared mutable lock state from the hot path in the common
case (no concurrent writer), which is exactly the traffic shape
`InventoryLedger` sees. The trade-offs:

- **Not reentrant.** Unlike `synchronized` and `ReentrantLock`, a thread
  that already holds `StampedLock`'s write lock and calls `writeLock()`
  again deadlocks against itself. This is the most common production
  StampedLock bug — usually via an innocent-looking recursive helper
  method or an overridden method calling back into a locked method.
- **No automatic condition support** the way `ReentrantLock` has
  `Condition`s — `StampedLock` isn't a drop-in replacement everywhere
  `ReentrantLock` is used, it's specifically strong for read-mostly data
  structures like this one.
- **Correctness discipline is stricter.** You must never act on data read
  during the optimistic window (branch on it, throw based on it, hand it
  to another thread) without validating first — `checkAvailability()`
  here only ever returns the value after either validation succeeds or a
  full read-lock fallback completed, never before.

---

## 6. Benchmark: what to expect and why

`LedgerBenchmark` runs the same 95%-read/5%-write workload against all
three implementations for a fixed duration, across a thread count set
deliberately higher than typical core counts, and reports operations
completed. (A JDK became available partway through this project — see
the real, measured numbers below — but the run instructions are the same
either way.)

```bash
cd phase2-synchronization-and-locks/src/main/java
javac com/orderengine/phase2/*.java -d out
java -cp out com.orderengine.phase2.LedgerCorrectnessTest
java -cp out com.orderengine.phase2.LedgerBenchmark
```

Run `LedgerCorrectnessTest` first — it verifies all three implementations
are equally correct (net-zero reserve/release cycles must return stock to
its exact starting value) before you trust any speed comparison between
them. A faster-but-wrong implementation is not a win.

What you should expect from `LedgerBenchmark`, and why, based on the
mechanics above:

- **`synchronized`** should have the lowest throughput once thread count
  exceeds core count meaningfully — every operation, including reads,
  competes for one exclusive monitor, and at 32 threads on a typical
  developer machine that monitor is under sustained contention (inflated
  to heavyweight, per §2).
- **`ReentrantReadWriteLock`** should meaningfully outperform
  `synchronized` given the 95% read ratio — readers stop blocking each
  other — but still pays real per-acquisition cost (the shared reader
  count from §5) that grows with reader concurrency.
- **`StampedLock`** should be the fastest of the three on this workload,
  since the dominant operation (reads) mostly avoids taking any lock at
  all.

If your results don't match this ordering, that's a legitimate and
useful outcome to investigate rather than dismiss — actual numbers depend
on core count, JIT warm-up (the benchmark doesn't separate a warm-up
phase from the measured phase, which is a real limitation — see §8 below
and revisit this properly with JMH once we cover proper microbenchmarking
methodology), and OS scheduler behavior. The point of running it yourself
is to see real contention effects on real hardware, not to match a number
printed in a doc.

**Real numbers, actually measured** (this sandbox now has a working JDK,
so unlike earlier phases these are real, not hypothetical):

```
Workload: 32 threads, 2000ms each, 95% reads / 5% writes, 50 products

synchronized (coarse monitor)      21,206,469 ops  (10,603,234 ops/sec)
ReentrantReadWriteLock (fair)         752,058 ops     (376,029 ops/sec)
StampedLock (optimistic read)      26,908,962 ops  (13,454,481 ops/sec)
```

`LedgerCorrectnessTest` passed for all three (`OK`) before this benchmark
ran, so the numbers are trustworthy — but they contradict the ordering
predicted above for the RWLock case, and the reason is worth understanding
rather than glossing over: **this sandbox has exactly one CPU core.**
`nproc` reports 1. On a single core, only one thread ever executes at any
instant, period — so there is no possible benefit from letting multiple
readers proceed "concurrently," because nothing is ever actually
concurrent at the hardware level. `ReentrantReadWriteLock`'s more complex
acquire/release bookkeeping (tracking a shared reader count, fairness
queue ordering) becomes pure overhead with zero offsetting benefit,
which is exactly why it measured roughly 28x *slower* than plain
`synchronized` here — a result that would be actively misleading if
presented without this explanation, and a genuinely important lesson in
its own right: **every claim in this phase about "reduced contention"
assumes the hardware has real parallelism to reduce contention over.**
On real multi-core hardware (which almost all production servers are),
expect something much closer to the originally-predicted ordering —
`StampedLock` fastest, `ReentrantReadWriteLock` meaningfully ahead of
`synchronized`, both benefiting from readers genuinely running
simultaneously on separate cores. Run `LedgerBenchmark` yourself on
real multi-core hardware to see this — the gap between "how many cores
you have" and "what a locking strategy can win you" is itself one of the
most important intuitions to build from this phase.

---

## 7. Common production bugs from this phase's concepts

1. **Forgetting `finally` on explicit locks.** Any exception between
   `lock.lock()` and `lock.unlock()` without a `finally` leaves the lock
   held forever — every other implementation in this file follows the
   lock/try/finally-unlock pattern for exactly this reason. This class of
   bug is why `synchronized` remains the right default whenever you don't
   specifically need what an explicit lock offers.

2. **Read→write upgrade attempts on `ReentrantReadWriteLock`.** Acquiring
   the write lock while already holding the read lock, from the same
   thread, deadlocks that thread against itself (the write lock can't be
   granted while any read lock — including your own — is outstanding, and
   you're not going to release your own read lock while blocked waiting
   for the write lock). Release the read lock fully first.

3. **Non-reentrant `StampedLock` recursion.** A method holding the write
   lock that calls another method which also tries to acquire the write
   lock deadlocks that thread against itself — `StampedLock` has no
   memory of "I already hold this." Easy to introduce accidentally via
   an innocuous-looking private helper method added later by someone who
   doesn't realize it's called from inside a locked block.

4. **Trusting optimistic-read data without validating.** Using the value
   read during `tryOptimisticRead()`'s window directly — e.g., logging it,
   returning it from a public method, or branching on it — without first
   calling `validate()` reintroduces exactly the visibility bug class from
   Phase 1, defeating the entire point of `StampedLock`.

5. **Non-fair `ReentrantReadWriteLock` writer starvation under sustained
   read load** — a genuine, observed-in-production failure mode, not a
   theoretical one: a write-heavy path (checkout, cancellation) can stall
   indefinitely behind continuous read traffic (browsing) if fairness
   wasn't deliberately chosen. This is precisely why
   `InventoryLedgerReadWriteLock` uses `new ReentrantReadWriteLock(true)`.

---

## 8. What the benchmark does *not* prove (methodology honesty)

Worth stating directly, since production engineering judgment includes
knowing the limits of your own measurements: `LedgerBenchmark` is a
hand-rolled wall-clock benchmark, not a proper microbenchmark. It doesn't
account for JIT warm-up/de-optimization noise, doesn't run multiple
measured iterations with statistics, and doesn't control for dead-code
elimination risk (the JIT could theoretically prove some of this loop's
work has no externally observable effect and skip it — unlikely given the
mutation happening, but a real risk class in naive benchmarks generally).
For code you'd actually ship a performance claim about, you'd use **JMH**
(Java Microbenchmark Harness), which exists specifically to handle warm-up
iterations, prevent dead-code elimination via blackholes, and produce
statistically defensible throughput numbers. We're using a simple
hand-rolled harness here because the goal this phase is *seeing directional
contention effects with your own eyes on your own hardware*, not producing
a number you'd put in a design doc — that distinction matters and is worth
being explicit about rather than presenting a toy benchmark as
production-grade evidence.

---

## 9. Interview Q&A (junior → staff)

**Q (junior): What's the difference between `synchronized` and
`ReentrantLock`?**
A: Both provide mutual exclusion and the same happens-before visibility
guarantee. `ReentrantLock` is an explicit object offering `tryLock()`,
interruptible acquisition, configurable fairness, and multiple
`Condition`s per lock — at the cost of requiring manual
`lock()`/`unlock()` (mandatorily in try/finally), where `synchronized`
guarantees release automatically even on exception.

**Q (junior/mid): Why would you ever choose `ReentrantReadWriteLock` over
plain `synchronized`?**
A: When the workload is read-heavy and reads don't mutate shared state —
letting concurrent readers proceed without serializing against each other
increases throughput, while writers remain fully exclusive (against both
other writers and all readers), preserving correctness.

**Q (mid): What is a "biased lock" and do modern JVMs still use it?**
A: An old optimization where a lock stayed "biased" toward the single
thread that first acquired it, letting that thread re-acquire without a
CAS. It was removed in JDK 15 (JEP 374) because CAS-based lightweight
locking became cheap enough on modern hardware that biasing's added
complexity stopped paying for itself. Java 21 doesn't have it.

**Q (mid): Explain what `StampedLock.tryOptimisticRead()` actually does,
and the one rule you must never break when using it.**
A: It returns a stamp representing the lock's current state without
acquiring any real lock — no blocking, no CAS on shared state. The caller
reads the data, then calls `validate(stamp)` to confirm no write occurred
during the read. The rule: never act on the data read during that window
(branch on it, expose it, throw based on it) until `validate()` has
returned true; if it returns false, discard the read and fall back to a
real lock.

**Q (mid/senior): Fair vs non-fair `ReentrantReadWriteLock` — what's the
actual trade-off, and how would you decide?**
A: Fair mode grants the lock in roughly FIFO arrival order, which
prevents starvation (in particular, writer starvation under sustained
read pressure) at a throughput cost, since it disallows "barging" — a
newly-arriving thread jumping the queue when the lock happens to be free
at that instant. Non-fair mode allows barging and is faster in the
common/lightly-contended case, but under sustained heavy read load can
starve writers indefinitely. Decide based on whether starving the write
path is an acceptable failure mode for the specific data — for something
like a real inventory reservation, we chose fair deliberately because a
stalled `reserve()` call under load is a real checkout failure, not just
a latency blip.

**Q (senior/staff): You inherit a service using a single coarse
`synchronized` block on a hot read-heavy path, and profiling under load
shows most CPU time in threads `BLOCKED` on that monitor. Walk through
how you'd decide between `ReentrantReadWriteLock` and `StampedLock` as
the fix, and what you'd verify before shipping either.**
A: Points worth hitting: (1) confirm via thread dumps that the contention
really is `BLOCKED` threads queued on that one monitor, not something
else masquerading as lock contention (e.g., GC pauses, or genuine
downstream I/O latency inflating hold time); (2) `ReentrantReadWriteLock`
is the safer first move — same mental model as `synchronized` (real
locks, blocking, no special validation discipline required), lower risk
of introducing a new bug class; move to `StampedLock` only if profiling
after the RWLock fix still shows meaningful contention specifically from
the shared reader-count bookkeeping at very high reader concurrency,
since StampedLock trades that away at the cost of a stricter, easier-to-
get-wrong optimistic-read discipline across the whole team, not just this
one call site; (3) before shipping either, run a correctness test
equivalent to `LedgerCorrectnessTest` under real concurrent load — a
faster-but-subtly-wrong version is a worse outcome than the slow-but-
correct baseline; (4) decide fairness deliberately per data being
protected, not by copying a default — ask specifically "what does it cost
this system if the write path is occasionally starved under read
pressure," which is a business question as much as a technical one, not a
setting you should leave on its default without thinking about it.

---

## 10. What's next

**Phase 3 — Atomic Variables & Lock-Free Programming.** We go one level
below locks entirely: CAS (compare-and-swap) mechanics, `AtomicInteger`/
`AtomicLong`/`AtomicReference`, the ABA problem and `AtomicStampedReference`,
and why `LongAdder` dramatically outperforms `AtomicLong` under high-
contention increment workloads (directly relevant to Phase 1's `count`
fix — we'll revisit it and make it faster). Component built: a lock-free
`OrderIdGenerator` and a `MetricsCounter` for the engine's throughput
dashboard (used again in Phase 13).

Say **"next"** when ready.
