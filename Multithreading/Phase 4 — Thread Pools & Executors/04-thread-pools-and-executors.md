# Phase 4 — Thread Pools & Executors

**Project:** Concurrent Order Processing Engine
**Stack:** Java 21
**Components built this phase:** `NamedThreadFactory`, `PoolSizingCalculator`, `StageExecutors` (tuned per-stage pools)
**Files:** `src/main/java/com/orderengine/phase4/`

---

## 1. Why raw `Thread` doesn't scale to a real system

Phases 1-3 created `Thread` objects directly because the point was to see
JMM/lock/CAS mechanics with nothing hiding them. Production systems don't
do this, for reasons that become obvious once you try: creating an OS
thread is expensive (allocation, kernel bookkeeping, a full stack — MB-
scale by default), an unbounded number of them will exhaust memory and
crash the scheduler under load, and nothing manages their lifecycle,
naming, or failure handling for you. `ExecutorService` (specifically
`ThreadPoolExecutor` underneath, for almost everything we do) solves all
of this: threads are created once and reused across many tasks, sized
deliberately, and given a real lifecycle (submit → run → shutdown).

This phase builds `StageExecutors` — one tuned pool per pipeline stage —
and this is the point where `OrderIdGenerator`, `MetricsCounter`, and
`TreiberStack` from Phase 3 stop being standalone demos and become
components real worker threads inside these pools would use.

---

## 2. `ThreadPoolExecutor`'s actual task-submission algorithm

This is the single most commonly misunderstood mechanic in this whole
phase, and it's worth internalizing precisely rather than approximately.
On every `execute(task)` call, in this exact order:

1. **If fewer than `corePoolSize` threads currently exist, start a new
   one for this task** — even if other core threads are idle right now.
   Core threads are created *lazily*, on demand, as work arrives (not
   eagerly at construction — `prestartAllCoreThreads()` exists
   specifically for cases where you want to pay that cost up front
   instead).
2. **Otherwise, try to enqueue the task** in the work queue, without
   creating any new thread.
3. **If the queue rejects the task** (a bounded queue that's full),
   *then* try to create a new thread, up to `maximumPoolSize`.
4. **If the pool is already at `maximumPoolSize` too**, the task goes to
   the `RejectedExecutionHandler`.

The counterintuitive consequence, proven directly by
`ThreadPoolExecutorInternalsDemo`: **with a bounded queue that has real
capacity, the pool will never grow past `corePoolSize` until the queue is
completely full.** Extra threads beyond core size are the *last* resort,
not the first response to rising load — a very common wrong mental model
is "the pool grows toward max size as load increases," when the real
behavior is "the queue absorbs load first, and the pool only grows once
the queue can absorb no more." This has direct sizing consequences: a
generously-sized queue with a small core pool can leave `maximumPoolSize`
essentially unreachable in practice, silently defeating the point of
having configured a larger max at all.

`keepAliveTime` only matters for threads *above* `corePoolSize` — a
non-core thread that sits idle longer than `keepAliveTime` is
terminated and removed. It has no effect on core threads unless
`allowCoreThreadTimeOut(true)` is explicitly set, which lets even core
threads time out and exit when the pool is fully idle — useful for pools
that see bursty, infrequent traffic and shouldn't hold threads (and their
memory) alive indefinitely between bursts.

---

## 3. Sizing pools: the actual math, not guesses

`PoolSizingCalculator` implements the two standard formulas (from Brian
Goetz's *Java Concurrency in Practice*) `StageExecutors` derives every
pool size from:

**CPU-bound work** (validation, business-rule checks — dominated by
actual computation): `Nthreads = Ncpu + 1`. The `+1` accounts for a
thread occasionally taking a page fault or being briefly descheduled,
so a spare thread can keep a core busy; going meaningfully above this
wastes cycles on context-switching between threads all fighting over the
same physical cores with nothing to overlap.

**I/O-bound work** (payment gateway calls, notification delivery —
dominated by waiting, not computing): `Nthreads = Ncpu × Utarget × (1 +
W/C)`, where `W` is average wait time per task and `C` is average
compute time per task. Intuition: if a task waits 9× as long as it
computes, one core can usefully support roughly 10 such tasks
concurrently before the CPU itself becomes the bottleneck, because 9 of
every 10 "in-flight" tasks at any instant are just waiting, not competing
for a core at all.

This is exactly why `StageExecutors` sizes the payment and notification
pools far larger than validation/reservation despite running on identical
hardware — `payment`'s ~120ms average network wait against ~4ms of actual
compute work (`W/C = 30`) drives a pool size an order of magnitude larger
than validation's `Ncpu + 1`. The number comes from the work's actual
profile, not a round number picked by feel — and if that profile is
wrong (e.g., the payment gateway's real latency is 400ms, not 120ms), the
formula is wrong in exactly the same proportion, which is why these
numbers need to come from real measurement in production, not permanent
constants.

---

## 4. `ThreadFactory`: naming, daemon status, uncaught exceptions

`NamedThreadFactory` fixes three things the JDK default gets wrong for
production use, all directly visible in a thread dump or a log line:

- **Names.** `Executors.defaultThreadFactory()` produces `pool-1-thread-3`
  — useless when you're staring at a thread dump trying to figure out
  which subsystem a stuck thread belongs to. Every pool here names its
  threads after the stage: `payment-worker-7`.
- **Daemon status**, set deliberately per pool rather than left to
  default — a background best-effort pool (metrics flush) can be daemon
  so it never blocks JVM shutdown; a pool whose work must complete before
  the process exits should not be.
- **An uncaught exception handler.** Without one, a task submitted via
  `execute()` that throws an unchecked exception kills that worker thread
  *silently* — no log, nothing — and the pool quietly starts a
  replacement on the next submission. This can run in production for
  months as a slow, unexplained throughput decline with zero direct
  evidence in the logs. `GracefulShutdownDemo` reproduces this directly.

---

## 5. Rejection policies: real, different production behavior

A rejection only happens once core threads, the queue, *and* max threads
are all simultaneously saturated. `RejectionPolicyDemo` runs an identical
saturating burst against all five options so the difference is directly
observable rather than theoretical:

- **`AbortPolicy`** (JDK default if unspecified): throws
  `RejectedExecutionException` back to the caller. `StageExecutors` uses
  this for `payment` — a rejected payment task must be a loud, synchronous
  failure the caller can act on (retry, alert, fail the order visibly),
  never silent.
- **`CallerRunsPolicy`**: the calling thread executes the task itself,
  synchronously. This is genuine **backpressure**, not just "don't lose
  work" — the producer is now busy running the task instead of submitting
  more, which naturally throttles the rate of new submissions to match
  real processing capacity. Used for `validation`/`inventoryReservation`.
- **`DiscardPolicy`**: silently drops the task — no exception, no log.
  Rarely correct alone; only for genuinely disposable work the caller has
  no way to react to regardless.
- **`DiscardOldestPolicy`**: drops the *oldest* queued task, then retries
  the new submission. Used for `notification` — a fresher notification is
  more valuable under overload than an old queued one.
- **Custom handler**: `RejectedExecutionHandler` is a single method —
  writing your own (log with context, emit a metric, then decide) is
  common where none of the four built-ins matches your exact
  observability needs.

---

## 6. `execute()` vs `submit()`: where exceptions go to die

Two different, easily-confused exception contracts, both proven in
`GracefulShutdownDemo`:

- **`execute(Runnable)`**: an uncaught exception propagates to the
  thread's `UncaughtExceptionHandler` — silent unless you wired one up
  via a custom `ThreadFactory` (§4).
- **`submit(...)`**: returns a `Future`. An exception thrown inside the
  task is *caught by the executor* and stored inside the `Future` — it's
  only re-thrown, wrapped in `ExecutionException`, when you call
  `future.get()`. If nothing ever calls `get()` on that `Future`, the
  exception is silently swallowed **forever**. This is arguably the worse
  footgun of the two, precisely because a `Future` *feels* like it should
  be "handling errors for you" — it isn't; it's relocating *where* you're
  responsible for checking, and it's extremely easy to submit
  fire-and-forget work via `submit()`, never touch the returned `Future`,
  and have real failures vanish with no trace at all.

---

## 7. `shutdown()` vs `shutdownNow()`, and `awaitTermination`

Not interchangeable, and `StageExecutors.shutdownGracefully()` uses
`shutdown()` deliberately, stage by stage in pipeline order:

- **`shutdown()`**: stop accepting *new* tasks; everything already
  submitted (running or queued) runs to completion. This is the correct
  default for a pipeline where dropping an order mid-flight on deploy is
  a real business problem, not just a graceful-degradation nicety.
- **`shutdownNow()`**: attempts to stop everything immediately —
  *interrupts* every actively running thread (an interrupt is a request,
  not a guarantee: a thread that ignores `InterruptedException` or never
  checks `Thread.interrupted()` simply keeps running regardless) and
  returns the `List<Runnable>` of tasks that were still queued and never
  even started, so the caller can inspect or requeue them elsewhere.
- **`awaitTermination(timeout, unit)`** blocks until either all tasks
  finish following a `shutdown()`/`shutdownNow()` call, or the timeout
  elapses. It does **not** itself trigger shutdown — calling it without
  first calling `shutdown()`/`shutdownNow()` just blocks for the full
  timeout, since the pool never stops accepting new work in the meantime.

`StageExecutors.shutdownGracefully()` shuts each stage down in pipeline
order (validation first, notification last) and falls back to
`shutdownNow()` per-stage only if that stage doesn't drain within its
timeout — worth reading directly in the source, since the ordering
choice (front-of-pipeline first) is itself a deliberate decision to avoid
downstream stages losing in-flight work from a stage that's still
feeding them.

---

## 8. `ScheduledExecutorService`: two different recurrence semantics

`ScheduledStageDemo` covers both, because this engine genuinely needs
both, for different reasons:

- **`scheduleAtFixedRate`**: run every fixed period *from the start of
  each execution*, not overlapping (if a run takes longer than the
  period, the next run starts immediately after the previous one finishes
  rather than running concurrently with it — a `ScheduledExecutorService`
  never runs the same recurring task concurrently with itself). Used for
  the metrics dashboard flush — the dashboard wants a value on a steady
  wall-clock cadence.
- **`scheduleWithFixedDelay`**: run, then wait the fixed period *after
  that run finishes*, then run again. Used for payment retry backoff —
  the gap you actually want is "N seconds after the failed attempt
  ended," not "N seconds after the attempt started" (which, with
  fixed-rate, could produce back-to-back retries with zero real gap if an
  attempt happens to take about as long as the configured period).

**The pitfall, proven directly:** if a scheduled task throws an uncaught
exception, **every future execution of that recurring task silently
stops — permanently** — with no exception surfaced anywhere unless code
specifically checks the returned `ScheduledFuture`. This is a strictly
worse version of the plain-`execute()` silent-death pitfall from §4/§6:
the task doesn't even get a fresh attempt next cycle — the whole schedule
is simply cancelled. Any production code scheduling recurring work must
catch and handle exceptions *inside* the task body and never let them
propagate out, full stop.

### Running the demos yourself

Same caveat as every prior phase — this sandbox has a JRE only, no
`javac`:

```bash
cd phase4-thread-pools-and-executors/src/main/java
javac com/orderengine/phase4/*.java -d out
java -cp out com.orderengine.phase4.ThreadPoolExecutorInternalsDemo
java -cp out com.orderengine.phase4.RejectionPolicyDemo
java -cp out com.orderengine.phase4.ScheduledStageDemo
java -cp out com.orderengine.phase4.GracefulShutdownDemo
```

---

## 9. Common production bugs from this phase's concepts

1. **Assuming pools grow toward `maximumPoolSize` as load rises**, when
   with a real-capacity bounded queue the pool stays at `corePoolSize`
   until the queue is completely full first (§2) — leads to surprise
   under-provisioning that only manifests once traffic spikes hard enough
   to actually fill the queue, which can be well past the point someone
   assumed extra capacity had already kicked in.

2. **One generic shared pool for everything**, sized once by guesswork —
   the anti-pattern this entire phase's `StageExecutors` design pushes
   back against. A slow I/O-bound stage sharing a pool with a fast
   CPU-bound one means the CPU-bound stage's threads can all be tied up
   waiting on the I/O-bound stage's tasks, starving work that should have
   been fast.

3. **`Executors.newFixedThreadPool`/`newCachedThreadPool` used
   uncritically in production.** `newFixedThreadPool` uses an *unbounded*
   `LinkedBlockingQueue` — under sustained overload this means unbounded
   memory growth instead of backpressure or rejection, a real and common
   cause of OOM incidents. `newCachedThreadPool` has no cap on thread
   count at all (`Integer.MAX_VALUE` max) — under a burst it can create
   thousands of threads. Constructing `ThreadPoolExecutor` directly, as
   every pool in `StageExecutors` does, forces an explicit, deliberate
   choice for queue bound and max size instead of inheriting a
   convenience factory's defaults.

4. **Fire-and-forget `submit()` with the `Future` never checked** —
   silently loses real failures, as demonstrated in §6/`GracefulShutdownDemo`.
   If you don't need the result and don't intend to call `get()`, use
   `execute()` instead (and make sure a real `UncaughtExceptionHandler` is
   wired via the `ThreadFactory`) so failures are at least visible
   somewhere, rather than using `submit()` and getting neither result
   handling nor visible failures.

5. **Recurring scheduled tasks with no internal exception handling** —
   silently and permanently stop after the first uncaught exception, per
   §8. Any `Runnable` passed to `scheduleAtFixedRate`/`scheduleWithFixedDelay`
   in real code should wrap its body in a try/catch that logs and
   swallows, specifically so one bad iteration doesn't kill the entire
   recurring job forever.

---

## 10. Interview Q&A (junior → staff)

**Q (junior): In what order does `ThreadPoolExecutor` grow threads,
queue tasks, and reject work?**
A: Core threads first (created lazily as work arrives, up to
`corePoolSize`), then the queue, then additional threads up to
`maximumPoolSize` only once the queue rejects a task, then the
`RejectedExecutionHandler` only once the pool is also at max size. The
pool does not grow toward max size just because load is increasing while
the queue still has room.

**Q (junior/mid): What's the difference between `execute()` and
`submit()` regarding exceptions?**
A: `execute()` sends an uncaught exception to the thread's
`UncaughtExceptionHandler` (silent by default unless one is configured).
`submit()` catches the exception into the returned `Future` and only
re-throws it (wrapped in `ExecutionException`) when `get()` is called —
if `get()` is never called, the exception is silently lost.

**Q (mid): Why would a service use `newFixedThreadPool` in a demo but not
in production?**
A: It's backed by an unbounded `LinkedBlockingQueue` — under sustained
overload, instead of applying backpressure or rejecting work, it queues
unboundedly, which under real load can grow memory without limit and
cause an OOM rather than a controlled degradation. Production code should
construct `ThreadPoolExecutor` directly with an explicitly bounded queue
and a chosen rejection policy.

**Q (mid): Explain the difference between `scheduleAtFixedRate` and
`scheduleWithFixedDelay`, and give a case where using the wrong one would
cause a real bug.**
A: Fixed-rate schedules the next run relative to when the *previous run
started* (non-overlapping, but back-to-back if a run takes about as long
as the period); fixed-delay schedules the next run relative to when the
*previous run finished*. Using fixed-rate for something like a retry
backoff, where you specifically want a minimum gap after a failure before
retrying, could produce retries with effectively no real gap if an
attempt's duration happens to approach the configured period — defeating
the point of backoff.

**Q (mid/senior): Why does `CallerRunsPolicy` count as a backpressure
mechanism rather than just "a way to avoid dropping work"?**
A: Because it makes the *producer's own thread* do the rejected work
synchronously, which means the producer can't submit more work until
that task finishes — it directly slows the rate of new submissions to
match the pool's real processing capacity, rather than absorbing
overload into an ever-growing queue or silently discarding it. It's a
self-regulating feedback loop, not merely a fallback destination for
overflow tasks.

**Q (senior/staff): You're handed a service with one shared
`newCachedThreadPool()` used for everything — fast CPU-bound validation
and slow outbound HTTP calls both submit to it — and it's occasionally
spiking to thousands of live threads under load, correlating with GC
pressure and latency spikes elsewhere in the same JVM. Walk through your
fix.**
A: Points worth hitting: (1) `newCachedThreadPool` has no real cap
(`Integer.MAX_VALUE` max threads) — under a burst of slow I/O-bound calls
each holding a thread for the call's duration, thread count can grow
essentially unbounded, and each OS thread carries real memory (default
stack size) and GC-visible overhead (thread-local allocation buffers,
GC root scanning per thread), which explains the correlated GC pressure;
(2) split into separate, purpose-sized pools per work profile — exactly
this phase's `StageExecutors` pattern — using the CPU-bound/I/O-bound
sizing formulas from §3 rather than an unbounded cached pool for
everything; (3) give each new pool an explicitly bounded queue and a
deliberately chosen rejection policy suited to that work's criticality
(§5) — fast, cheap-to-retry work can use `CallerRunsPolicy` for
backpressure, business-critical work should fail loudly via
`AbortPolicy` rather than silently queue without bound; (4) validate the
fix isn't just theoretically correct but measured — instrument
`getPoolSize()`/`getActiveCount()`/`getQueue().size()` (as
`ThreadPoolExecutorInternalsDemo` does) under real production-shaped load
before and after, since pool-sizing formulas are a starting estimate from
measured wait/compute ratios, not a guarantee, and the actual fix needs
verifying against real traffic, not just the math.

---

## 11. What's next

**Phase 5 — Concurrent Collections.** `StageExecutors` gives us the
worker pools; this phase gives us the thread-safe data structures those
workers actually pass work through. `ConcurrentHashMap` internals (how
Java 8+ moved away from segment locking to a finer-grained CAS+bin-lock
scheme), the full `BlockingQueue` family (`ArrayBlockingQueue`,
`LinkedBlockingQueue`, `PriorityBlockingQueue` for VIP order handling,
`DelayQueue` for scheduled retries), and `CopyOnWriteArrayList` for
rarely-mutated, frequently-iterated state (e.g. a live list of registered
notification channels). Component built: the engine's internal
inter-stage queues, replacing the placeholder direct-call wiring assumed
so far with real bounded producer→consumer channels between stages —
setting up directly for Phase 6's producer-consumer patterns.

Say **"next"** when ready.
