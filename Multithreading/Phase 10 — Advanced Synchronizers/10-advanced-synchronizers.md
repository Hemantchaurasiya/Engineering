# Phase 10 — Advanced Synchronizers

**Project:** Concurrent Order Processing Engine
**Stack:** Java 21
**Components built this phase:** `StartupCoordinator`, `PaymentRateLimiter`, plus standalone `CyclicBarrier`/`Exchanger`/`Phaser` demos
**Files:** `src/main/java/com/orderengine/phase10/`

---

## 1. Where this phase sits

Every synchronizer in this phase solves a specific, narrow coordination
shape that a general-purpose lock (Phase 2) or a full async framework
(Phase 7) would either not express cleanly or would require rebuilding
from more primitive pieces. The five covered here — `CountDownLatch`,
`Semaphore`, `CyclicBarrier`, `Phaser`, `Exchanger` — are all built in
`java.util.concurrent` on the same underlying `AbstractQueuedSynchronizer`
machinery that also backs `ReentrantLock` (Phase 2), so none of them pin
virtual threads (Phase 9 §4) — the same reason `ReentrantLock` doesn't.

---

## 2. `CountDownLatch`: one-shot "wait for N events"

Already used informally in earlier phases' demos (Phase 1's
`CountDownLatch` for thread coordination, `AbaProblemDemo`'s deterministic
interleaving). `StartupCoordinator` formalizes the **dual-latch pattern**,
worth knowing by name because it recurs constantly in real startup and
test-harness code:

- `readySignal` (count = N): each of N stages counts it down once *it*
  becomes ready; the coordinator awaits it reaching zero.
- `startSignal` (count = 1): the coordinator counts this down exactly
  once, *after* all stages are ready; every stage was meanwhile blocked
  awaiting this second latch, so they all unblock at effectively the same
  instant.

`StartupCoordinatorDemo` proves the payoff directly: 5 stages
initializing at wildly different, randomized speeds (50-400ms) still all
begin actual work within a tiny spread of each other, because release
happens via one shared `countDown()` call rather than each stage
proceeding independently the moment *it* finishes initializing.

**The defining trait, and why it's the wrong tool for repeated
coordination**: a `CountDownLatch` is intentionally **one-shot**. Once
its count reaches zero, it's permanently open — `await()` returns
immediately for any future caller, `countDown()` past zero is a harmless
no-op, and there is no way to reset it. Exactly right for "wait for N
one-time events." Wrong for anything needing to repeat across multiple
rounds — that's `CyclicBarrier`'s job.

---

## 3. `Semaphore`: capping concurrency, not mutual exclusion

`PaymentRateLimiter` uses a counting `Semaphore` to cap **concurrent
in-flight payment gateway calls** — a genuinely different concern from
Phase 4's pool sizing. Pool sizing controls how much *parallel work
capacity* a service has overall; a semaphore here controls how much of
that capacity is allowed to hit *one specific downstream dependency* at
once (a real constraint many payment gateways impose per merchant
account, independent of the calling service's own thread count). This
distinction matters more, not less, once Phase 9's virtual threads are in
play: `PaymentRateLimiterDemo` fires 100 calls essentially
simultaneously via cheap virtual threads — with no natural thread-count
ceiling to slow things down — and the semaphore is the *only* thing
holding peak concurrency at the configured cap of 5, proven by directly
tracking and printing the observed peak.

**Semaphores have no ownership concept.** Unlike every lock covered so
far, the thread that calls `release()` doesn't have to be the same thread
that called `acquire()` — unusual among this project's synchronizers, and
occasionally useful (a producer acquiring a permit that a different
consumer thread later releases), though `PaymentRateLimiter` uses the
more common same-thread acquire/release-in-`finally` pattern. That
`finally` block is exactly as mandatory here as it was for every explicit
lock since Phase 2 — a missed `release()` permanently shrinks the
effective limit by one, a slow, easy-to-miss resource leak that would
manifest as gradually declining throughput with no obvious cause.

**Fairness** works identically to Phase 2 §4's `ReentrantReadWriteLock`
discussion: `new Semaphore(n, true)` gives FIFO permit acquisition,
preventing a newly-arriving call from repeatedly barging ahead of one
that's been waiting longer, at some throughput cost — `PaymentRateLimiter`
chooses fairness deliberately for the same reason
`InventoryLedgerReadWriteLock` did: starving a specific caller
indefinitely under sustained load is a worse outcome than the modest
cost of fairness.

---

## 4. `CyclicBarrier`: repeated "wait for N, together, then reset"

`BatchPhaseCyclicBarrierDemo` shows the shape `CountDownLatch` cannot
express: a **fixed** group of worker threads that must repeatedly reach a
common checkpoint together, across multiple rounds, with the barrier
automatically resetting itself for reuse each time every party arrives.
Used here for a multi-round batch job where round K+1 genuinely depends
on round K being fully complete across every worker (e.g. aggregated
results only exist once every worker's chunk of that round is done).

The **barrier action** — the `Runnable` passed to the constructor — runs
exactly **once per round**, on whichever thread happens to be the *last*
to arrive for that round, not on every thread and not on some separate
dedicated thread. `BatchPhaseCyclicBarrierDemo` uses this to print
round-progress and reset per-round counters without needing any separate
"who reports this round's summary" coordination — the barrier itself
elects the reporter, for free.

---

## 5. `Phaser`: `CyclicBarrier` with a dynamic party count

`DynamicPhaserDemo` demonstrates the one thing `CyclicBarrier`
structurally cannot do: change how many parties are being waited on,
*while phases are in progress*. `CyclicBarrier`'s party count is fixed at
construction, permanently. `Phaser` lets parties `register()` and
`arriveAndDeregister()` dynamically — a phase only advances once every
*currently registered* party has arrived, so parties that already left
via `arriveAndDeregister()` are correctly no longer waited on.

The demo's motivating scenario: 5 workers, where even-numbered workers
finish their assigned work after phase 2 and permanently stop
participating (`arriveAndDeregister()`), while odd-numbered workers and
the observing main thread continue through all 4 phases. Later phases
correctly advance based only on the shrinking set of still-registered
parties — printed `getRegisteredParties()` counts drop from 6 to 3 to 1
to 0 across the run, visible proof the mechanism works as described
rather than silently hanging waiting for parties that already departed.

`Phaser` is more powerful than `CyclicBarrier` but also meaningfully more
complex to reason about correctly — reach for `CyclicBarrier` whenever
the party count really is fixed (the common case), and only reach for
`Phaser` when dynamic registration is a genuine requirement, not a
default upgrade.

---

## 6. `Exchanger`: two-party rendezvous handoff

`DoubleBufferExchangerDemo` is the narrowest-scope synchronizer in this
phase — exactly right for **two** threads that repeatedly need to swap a
whole object with each other at a rendezvous point, and not really
applicable beyond that specific shape. The classic case, demonstrated
directly: **double buffering** — a producer fills buffer A while a
consumer drains buffer B (the *previous* batch); when both finish their
current buffer, `exchange()` swaps them atomically, and each side starts
working on what the other just finished with, with neither thread ever
touching a buffer the other is actively using.

This is meaningfully different from a `BlockingQueue`-based handoff
(Phase 5/6): a queue lets the producer get arbitrarily far ahead of the
consumer (up to its bound) without the two sides ever synchronizing at a
shared point in time. `Exchanger` forces a **hard, mutual rendezvous**
every round — useful specifically when both sides genuinely need to
alternate in lockstep rather than being decoupled by a buffer's slack.

---

## 7. Running the demos yourself

Same caveat as every prior phase — no `javac` in this sandbox:

```bash
cd phase10-advanced-synchronizers/src/main/java
javac com/orderengine/phase10/*.java -d out
java -cp out com.orderengine.phase10.StartupCoordinatorDemo
java -cp out com.orderengine.phase10.PaymentRateLimiterDemo
java -cp out com.orderengine.phase10.BatchPhaseCyclicBarrierDemo
java -cp out com.orderengine.phase10.DoubleBufferExchangerDemo
java -cp out com.orderengine.phase10.DynamicPhaserDemo
```

---

## 8. Choosing among these five (and Phase 2's locks)

| Need | Tool |
|---|---|
| Wait for N one-time events, then proceed forever after | `CountDownLatch` |
| Cap concurrent access to a resource at N | `Semaphore` |
| Fixed group of threads repeatedly syncing at a checkpoint | `CyclicBarrier` |
| Same, but the group's membership changes over time | `Phaser` |
| Exactly two threads repeatedly swapping a whole object | `Exchanger` |
| Mutual exclusion for a critical section | `ReentrantLock`/`synchronized` (Phase 2) |
| Many concurrent readers, exclusive writer | `ReentrantReadWriteLock`/`StampedLock` (Phase 2) |

---

## 9. Common production bugs from this phase's concepts

1. **Using `CountDownLatch` for repeated coordination** — it can't reset;
   code that tries to reuse one across multiple rounds either needs a
   fresh instance each round (awkward, easy to get the handoff timing
   wrong) or silently breaks after the first round. `CyclicBarrier`/
   `Phaser` are the correct tools for repetition.

2. **Missing `release()` on a `Semaphore`** — exactly like a missed lock
   `unlock()`, this permanently shrinks the effective concurrency limit
   by one per occurrence, manifesting as a slow, hard-to-diagnose
   throughput decline rather than an obvious crash.

3. **`CyclicBarrier` deadlock from a mismatched arrival count** — if any
   registered party never calls `await()` (crashes, takes an early
   `return`, throws before reaching the barrier), every other party
   waiting at that barrier blocks forever (or until the configured
   timeout, if one was used) — unlike `Phaser`, `CyclicBarrier` has no
   way to "excuse" a party that isn't coming.

4. **Forgetting a `Phaser` party never deregisters** — the mirror image
   of bug 3: a party that stops actually doing work but never calls
   `arriveAndDeregister()` (or an equivalent explicit removal) leaves the
   phaser permanently waiting on it every future phase, blocking
   everyone else indefinitely.

5. **Using `Exchanger` where a `BlockingQueue` was actually the right
   tool** — reaching for `Exchanger` when the two sides don't actually
   need lockstep rendezvous (e.g. the consumer could reasonably run
   somewhat ahead or behind) needlessly couples their pacing together,
   when a bounded queue would decouple them with the same safety and
   better throughput under uneven relative speeds.

---

## 10. Interview Q&A (junior → staff)

**Q (junior): What's the key difference between `CountDownLatch` and
`CyclicBarrier`?**
A: `CountDownLatch` is one-shot — once its count reaches zero it stays
permanently open and cannot be reset. `CyclicBarrier` automatically
resets after every party arrives, so it can coordinate the same fixed
group of threads across multiple repeated rounds.

**Q (junior/mid): Why would you use a `Semaphore` instead of just sizing
your thread pool to the concurrency limit you want?**
A: Pool size controls how much work a service can do in parallel overall;
a semaphore caps how much of that capacity is allowed to hit one specific
resource or dependency at a time, which is a different, complementary
constraint — especially important with virtual threads (Phase 9), where
thread count no longer naturally limits concurrency at all.

**Q (mid): What can `Phaser` do that `CyclicBarrier` fundamentally
cannot?**
A: Change the number of parties being coordinated at runtime — parties
can register and deregister dynamically between or during phases.
`CyclicBarrier`'s party count is fixed permanently at construction.

**Q (mid): When is `Exchanger` the right tool instead of a
`BlockingQueue`?**
A: When exactly two threads need to repeatedly hand off whole objects to
each other at a hard, mutual rendezvous point — like double buffering,
where each side must wait for the other to be ready before proceeding.
A `BlockingQueue` decouples producer and consumer pacing (up to its
bound); `Exchanger` deliberately does not — it forces lockstep
synchronization every round, which is the point when that's genuinely
what's needed.

**Q (mid/senior): In `PaymentRateLimiterDemo`, why does firing 100 calls
via virtual threads specifically make the semaphore's role clearer than
firing them via a small fixed platform-thread pool would?**
A: With a small platform-thread pool, the pool size itself already
happens to cap concurrency near some number, potentially masking whether
the semaphore is actually doing meaningful work — you might see
"correct" behavior even with a broken semaphore, purely because the pool
was the real limiting factor. Virtual threads remove that accidental
ceiling entirely (Phase 9) — with cheap, effectively unlimited
concurrent virtual threads all attempting the call simultaneously, the
semaphore becomes the *only* thing enforcing the limit, making its
correctness (or a bug in it) directly and unambiguously observable.

**Q (senior/staff): A junior engineer wants to replace a `CyclicBarrier`-
based multi-worker batch job with a `Phaser` "to be more flexible, in
case we need dynamic worker counts later." What trade-offs would you
raise before agreeing?**
A: Points worth hitting: (1) `Phaser` is meaningfully more complex to
reason about correctly than `CyclicBarrier` — dynamic registration means
the set of parties being waited on can change between any two phases,
which is a real source of bugs if any code path fails to deregister a
party that's actually done (bug 4 above), silently hanging every future
phase; (2) "we might need this flexibility later" is weaker justification
than a concrete, current requirement — if the worker count genuinely is
fixed today, `CyclicBarrier`'s simpler, harder-to-misuse fixed-party
model is the better default, and migrating to `Phaser` later if dynamic
registration becomes an actual requirement is a small, well-contained
change; (3) if there IS a concrete near-term need for dynamic worker
counts, ask specifically how deregistration will be guaranteed to happen
on every exit path (including exceptions) for every worker — likely via
a try/finally wrapping each worker's participation, mirroring the
lock/permit release discipline from Phase 2 and this phase's `Semaphore`
usage, since an un-deregistered party is exactly as dangerous here as a
never-released lock or permit is elsewhere.

---

## 11. What's next

**Phase 11 — Deadlock, Livelock, Starvation.** Every synchronization
primitive covered since Phase 2 can be misused into these three failure
modes. This phase deliberately induces a real deadlock in a
lock-ordering scenario across two of this project's own components,
diagnoses it using thread-dump analysis (`jstack`/`jcmd`), and fixes it
with consistent lock ordering and `tryLock`-with-timeout as an escape
hatch. Also covers livelock (threads actively responding to each other
but making no real progress) and starvation (a thread perpetually losing
out to others under a scheduling or fairness policy that technically
isn't "stuck" but never actually proceeds) as distinct, related failure
modes with different diagnostic signatures.

Say **"next"** when ready.
