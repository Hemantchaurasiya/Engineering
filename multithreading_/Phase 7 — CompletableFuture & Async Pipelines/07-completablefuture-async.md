# Phase 7 — CompletableFuture & Async Pipelines

**Project:** Concurrent Order Processing Engine
**Stack:** Java 21
**Components built this phase:** `PaymentGatewayClient`, `FraudCheckService`, `PaymentOrchestrator`
**Files:** `src/main/java/com/orderengine/phase7/`

---

## 1. Where this phase sits

Phase 6's pipeline stages are synchronous within their own worker
threads — a `PipelineStage` worker calls `process()` and blocks for its
full duration, including any simulated I/O wait, occupying that worker
thread the entire time. Phase 4 sized the `payment` pool around exactly
this reality (I/O-bound: a larger pool is needed to keep throughput up
while most threads are simply waiting). This phase changes the model:
instead of a worker thread blocking for the duration of a slow call,
`CompletableFuture` lets that wait become genuinely non-blocking — the
calling thread is freed immediately, and a *continuation* runs later, on
some thread, once the result is ready.

`PaymentOrchestrator` is the real payoff: a full async chain for
charging a payment gateway, with a timeout, an automatic fallback to a
backup gateway, a concurrently-running independent fraud check, and
guaranteed terminal handling of every failure path — composed entirely
out of `CompletableFuture` combinators, no blocking `get()`/`join()`
anywhere in the orchestration logic itself.

---

## 2. `thenApply` vs `thenApplyAsync`: which thread actually runs your code

This is the single most commonly misunderstood `CompletableFuture`
mechanic, and `AsyncPitfallsDemo` proves it directly rather than leaving
it as an abstract claim:

- **`thenApply(fn)`** (no `Async` suffix): if the future is *already
  complete* when `thenApply` is called, the continuation runs
  **synchronously, on the calling thread**, immediately. If the future is
  **not yet complete**, the continuation instead runs on whichever thread
  ends up *completing* the future — typically the executor thread that
  finished the original `supplyAsync` work — **not** the thread that
  called `thenApply`. This is a genuinely timing-dependent dual behavior:
  the exact same line of code can run on two different threads depending
  on whether the future happened to already be done.
- **`thenApplyAsync(fn)`** (no executor argument): always runs on
  `ForkJoinPool.commonPool()`, regardless of completion timing — more
  predictable than plain `thenApply` in *where* it runs, but see §5 for
  why the *default* executor itself is a real danger for the wrong kind
  of work.
- **`thenApplyAsync(fn, executor)`**: the only fully deterministic option
  — always runs on the given executor, never the calling thread, never
  whatever thread happened to complete the future. This is why every
  `supplyAsync` call in `PaymentGatewayClient` and `FraudCheckService`
  passes an explicit executor rather than relying on either default.

The practical rule this phase's code follows: **use an explicit executor
for anything that matters where it runs** — especially real I/O work,
which should never share a thread pool it wasn't deliberately sized for
(exactly Phase 4's sizing discipline, now applied to async code instead
of blocking pipeline workers).

---

## 3. `thenCompose` vs `thenCombine`: dependent vs independent

These solve two different problems and are not interchangeable —
`CombiningFuturesDemo` and `PaymentOrchestrator` both use each for
exactly the case it fits:

- **`thenCompose`** ("flatMap for futures"): use when the *next* async
  call needs the *previous* call's result as input — a genuinely
  sequential dependency. `fetchUserId().thenCompose(id ->
  fetchUserProfile(id))` in `CombiningFuturesDemo` is the canonical shape.
  Using `thenApply` here by mistake (a very common error) produces a
  doubly-nested `CompletableFuture<CompletableFuture<Profile>>` instead of
  a flat `CompletableFuture<Profile>`, because `thenApply` has no way of
  knowing the function you gave it returns a future itself — it just
  wraps whatever comes back as the outer future's result type, nested
  future and all. `thenCompose` specifically flattens this.
- **`thenCombine`**: use when two calls have **no dependency on each
  other's result** and should run **concurrently**, merged once both
  finish. `PaymentOrchestrator` starts the fraud check and the payment
  charge at the same moment (fraud check doesn't need the payment
  result, and the payment charge doesn't need the fraud check result) and
  combines them with `thenCombine` once both resolve.
  `CombiningFuturesDemo`'s `demoThenCombine()` proves this concurrency is
  real, not accidental: two 150ms calls combined finish in ~150ms total,
  not ~300ms — if they'd actually run sequentially (e.g. by mistakenly
  starting the second call only after the first completed), total
  elapsed time would be close to the sum, not the max.

---

## 4. Exception handling: `exceptionally`, `handle`, `exceptionallyCompose`

- **`exceptionally(fn)`**: recovers from a failure with a **fallback
  value**, computed synchronously from the exception. It cannot itself
  return a new async operation — only a plain value.
- **`handle((result, error) -> ...)`**: called on **every** completion,
  success or failure, with exactly one of `result`/`error` non-null. Used
  as `PaymentOrchestrator`'s terminal step specifically because it
  guarantees exactly one outcome is always produced — there's no
  completion path that can fall through unhandled.
- **`exceptionallyCompose(fn)`** (Java 12+): the async-aware version of
  `exceptionally` — the recovery function returns a **new
  `CompletableFuture`**, not just a value. `PaymentOrchestrator` uses this
  specifically for the primary-to-backup gateway fallback: recovering
  from a failed primary charge isn't a static fallback value, it's *an
  entirely new async gateway call*, which only `exceptionallyCompose`
  (not `exceptionally`) can express directly.

**`CompletionException` wrapping**: when an exception crosses an async
boundary (propagates out of a `supplyAsync`/continuation running on a
different thread than the one that eventually observes it), it arrives
wrapped in a `CompletionException`, with the real cause available via
`getCause()`. `PaymentOrchestrator.unwrap()` exists specifically to strip
this wrapper before logging, so the logged message is the actual gateway
failure reason rather than a generic `CompletionException` message with
the useful detail one level down.

`orTimeout(duration, unit)` (Java 9+) is what bounds how long
`PaymentOrchestrator` waits on the primary gateway — if it doesn't
complete in time, the future completes exceptionally with a
`TimeoutException`, which lands in the exact same
`exceptionallyCompose` fallback path as a genuine `GatewayException`
would. Both failure modes — "the primary gateway said no" and "the
primary gateway took too long to answer at all" — get identical
treatment: fall back to the backup.

---

## 5. The `ForkJoinPool.commonPool()` danger

Every `*Async` method **without an explicit `Executor` argument**
defaults to `ForkJoinPool.commonPool()` — a single pool, shared
JVM-wide, also used internally by parallel streams and various other
library code. This is fine for genuinely short, CPU-bound continuations.
It is a real, production-proven danger for **blocking work** — exactly
what a real payment gateway HTTP call is.

`AsyncPitfallsDemo`'s third demo proves the failure mode concretely:
saturating every common-pool worker thread with blocking calls (as would
happen if `PaymentGatewayClient` used the no-executor `supplyAsync`
overload for its real network call) leaves **zero** threads available for
completely unrelated code elsewhere in the same JVM that also wants the
common pool — in the demo, a parallel stream. The observed behavior isn't
even a clean failure: a parallel stream with no available common-pool
worker degrades to running on the calling thread instead of deadlocking,
which *masks* the problem rather than surfacing it — the stream still
"works," just with none of the parallelism it was written assuming it
would get, silently, with no error or warning anywhere. This is precisely
why `PaymentGatewayClient` and `FraudCheckService` both construct and
pass their own dedicated executors to every `supplyAsync` call rather
than ever using the single-argument overload.

---

## 6. `allOf` and `anyOf`

- **`CompletableFuture.allOf(futures...)`**: returns a
  `CompletableFuture<Void>` that completes once **every** given future
  has completed (successfully or exceptionally). `PaymentOrchestratorDemo`
  uses this to wait for a whole batch of 200 concurrently-processed
  orders before tallying results — the standard pattern for "wait for a
  batch of independent async operations, then proceed," and calling
  `.join()` on individual futures *after* `allOf` has already completed
  is safe and non-blocking, since they're all guaranteed done by then.
  Note `allOf` itself discards individual results (it's `Void`) — you
  still collect each future's own result yourself afterward.
- **`CompletableFuture.anyOf(futures...)`**: completes as soon as **any
  one** of the given futures completes — useful for redundant calls to
  multiple equivalent sources (e.g. read replicas) where you want the
  fastest answer and don't care about the rest. `CombiningFuturesDemo`
  proves this picks the genuinely fastest of three configured delays, not
  submission order.

---

## 7. Running the demos yourself

Same caveat as every prior phase — no `javac` in this sandbox:

```bash
cd phase7-completablefuture-async/src/main/java
javac com/orderengine/phase7/*.java -d out
java -cp out com.orderengine.phase7.CombiningFuturesDemo
java -cp out com.orderengine.phase7.AsyncPitfallsDemo
java -cp out com.orderengine.phase7.PaymentOrchestratorDemo
```

`PaymentOrchestratorDemo` runs 200 orders concurrently through the full
orchestration and tallies outcomes — expect to see `COMPLETED` orders
handled by both `primary-gateway` and `backup-gateway` (proving the
fallback path genuinely triggers under both timeout and failure
conditions), some `HELD_FOR_REVIEW` (the fraud check's simulated 10%
flag rate), and — rarely, since it needs the primary AND backup to both
fail for the *same* order — the occasional `FAILED`.

---

## 8. Common production bugs from this phase's concepts

1. **Using the no-executor `*Async` overload for real I/O work** — §5's
   common-pool starvation danger, a genuinely surprising cross-feature
   interaction that can silently degrade unrelated code elsewhere in the
   same JVM with no error message pointing at the actual cause.

2. **`thenApply` where `thenCompose` was needed** — produces a nested
   `CompletableFuture<CompletableFuture<T>>` that compiles (sometimes with
   an unchecked-adjacent structural surprise rather than a compile error,
   depending on how the result is subsequently used) and is awkward and
   easy to misuse downstream — a strong signal to look for whenever a
   `thenApply`'s lambda itself returns something that looks like a future.

3. **Fire-and-forget async chains with no terminal `get()`/`join()`/
   `whenComplete()`** — exceptions vanish completely and silently, proven
   directly in `AsyncPitfallsDemo`. This is Phase 4 §6's `submit()`-
   without-`get()` pitfall, generalized and made easier to lose track of
   across a longer async chain with several intermediate steps.

4. **Relying on `exceptionally` where an async fallback was actually
   needed** — `exceptionally` can only return a plain value, not trigger
   another async call; reaching for it when the real fallback is another
   network call (as `PaymentOrchestrator`'s backup gateway is) either
   doesn't compile as intended or forces an awkward, incorrect
   `.join()`-inside-the-handler workaround that reintroduces blocking
   exactly where the whole point was to avoid it. `exceptionallyCompose`
   is the correct tool.

5. **Forgetting that `CompletionException` wraps the real cause** — code
   that catches/logs the raw exception from a `handle`/`whenComplete`
   callback without unwrapping it produces unhelpful, generic log
   messages ("CompletionException: java.util.concurrent.CompletionException")
   instead of the actual underlying failure reason, making production
   incident diagnosis meaningfully harder for no good reason.

---

## 9. Interview Q&A (junior → staff)

**Q (junior): What's the difference between `thenApply` and
`thenApplyAsync`?**
A: `thenApply` runs its continuation either synchronously on the calling
thread (if the future was already complete) or on whatever thread
completed the future (if it wasn't yet) — which thread runs it depends on
timing. `thenApplyAsync` (with no executor) always runs on
`ForkJoinPool.commonPool()` regardless of timing; with an explicit
executor argument, it always runs there, deterministically.

**Q (junior/mid): When would you use `thenCompose` instead of
`thenApply`?**
A: When the function you're chaining itself returns a
`CompletableFuture` — typically because the next async call depends on
the previous call's result. `thenCompose` flattens the result into a
single future; `thenApply` would produce an awkward nested
`CompletableFuture<CompletableFuture<T>>` instead.

**Q (mid): Why is submitting a blocking network call to
`CompletableFuture.supplyAsync(supplier)` (no executor argument)
dangerous?**
A: It defaults to `ForkJoinPool.commonPool()`, a single pool shared
JVM-wide including by parallel streams and other library internals.
Blocking calls occupy common-pool threads for their full duration; enough
concurrent blocking calls can starve completely unrelated code elsewhere
in the JVM that also needs the common pool, and the failure mode can be
subtle (e.g. a parallel stream silently degrading to sequential execution
on the calling thread rather than throwing or deadlocking) rather than an
obvious, loud error.

**Q (mid): What's the practical difference between `exceptionally` and
`exceptionallyCompose`?**
A: `exceptionally` recovers with a plain fallback value computed
synchronously from the exception. `exceptionallyCompose` recovers by
returning an entirely new `CompletableFuture` — necessary when the
fallback itself is another asynchronous operation (like calling a backup
service), which `exceptionally` has no way to express.

**Q (mid/senior): Explain the difference between `thenCombine` and
`allOf`/`anyOf`, and when you'd reach for each.**
A: `thenCombine` merges exactly two independent futures' results into
one value via a combining function, keeping a single typed
`CompletableFuture<R>` result. `allOf` waits on an arbitrary number of
futures but discards their individual results (returns `Void`) — you
collect each one's result separately once you know all are done; it's
for "wait for a batch, then proceed," not for merging values inline.
`anyOf` completes as soon as any one of several futures completes,
useful for redundant/racing calls where you only want the fastest
result and don't care about the others.

**Q (senior/staff): You're reviewing a PR that adds a new async call to
an existing `CompletableFuture` chain using `.thenApplyAsync(fn)` with no
explicit executor, inside a service that also makes heavy use of
parallel streams elsewhere. What do you flag, and what would you ask the
author to change?**
A: Points worth hitting: (1) the no-executor `thenApplyAsync` defaults to
`ForkJoinPool.commonPool()`, the same pool the service's parallel streams
rely on — if the new async work involves any blocking (I/O, a network
call, a lock wait), it can starve the common pool and degrade or stall
unrelated parallel-stream code elsewhere in the service, in a way that
would be very difficult to correlate back to this specific change during
an incident, since the two features look completely unrelated in the
code; (2) ask for an explicit, appropriately-sized dedicated `Executor`
to be passed instead — sized per Phase 4's CPU-bound/I/O-bound formulas
depending on what the new work actually does; (3) if the work is
genuinely short and CPU-bound with no blocking at all, the common pool
might be acceptable, but that should be a deliberate, documented decision
in the PR rather than an accidental default, precisely because the
failure mode when it's wrong is silent degradation, not a loud error that
would get caught in testing.

---

## 10. What's next

**Phase 8 — Fork/Join & Parallel Streams.** A different flavor of
parallelism: not "wait on independent async I/O calls" but "split a
single large in-memory computation into smaller pieces, work on them in
parallel, and merge the results" — CPU-bound divide-and-conquer, this
time deliberately using `ForkJoinPool` on purpose (with full
understanding, after this phase's §5, of exactly what it is and isn't
safe to run there) rather than avoiding it. Covers work-stealing
internals, `RecursiveTask` vs `RecursiveAction`, and when parallel
streams genuinely help versus when they quietly hurt. Component built: a
parallel batch order-reconciliation job — processing a large end-of-day
batch of completed orders against ledger records, split and merged via
fork/join, contrasted directly against a naive sequential version and a
naive (wrong) parallel-stream version to show exactly where each
approach's assumptions break down.

Say **"next"** when ready.
