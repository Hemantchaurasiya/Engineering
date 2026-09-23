# Phase 8 — Fork/Join & Parallel Streams

**Project:** Concurrent Order Processing Engine
**Stack:** Java 21
**Components built this phase:** `SequentialReconciler`, `ForkJoinReconciler`, `NormalizationAction`, `ParallelStreamReconciler`
**Files:** `src/main/java/com/orderengine/phase8/`

---

## 1. Where this phase sits — a different flavor of parallelism

Phase 7 was about **I/O-bound concurrency**: many independent, mostly-
waiting operations (network calls) composed asynchronously so a thread
never sits blocked for no reason. This phase is about **CPU-bound
parallelism**: one large in-memory computation, split into pieces small
enough to run genuinely simultaneously across cores, then merged back
together. Different problem shape, different tool: `ForkJoinPool` and
parallel streams, applied to a real batch job — end-of-day reconciliation
of every order against its ledger entry, embarrassingly parallel (every
record's comparison is fully independent of every other's).

---

## 2. `ForkJoinPool` internals: work-stealing

Every worker thread in a `ForkJoinPool` owns its own double-ended queue
(deque) of tasks, rather than every thread pulling from one shared
central queue (contrast with Phase 4's `ThreadPoolExecutor`, where every
worker contends on the same queue). This difference is the whole point:

- A worker pushes and pops its **own** newly-created subtasks from the
  **head** of its own deque — LIFO. This is deliberate: the most recently
  created task is the smallest, most fine-grained piece of work, likely
  still hot in that core's cache, so working on it next is cheap.
- When a worker's own deque runs empty, it **steals** from the **tail**
  of another, busier worker's deque — FIFO from the stealer's
  perspective. Stealing the *oldest* remaining task from a busy worker is
  deliberate too: an old, not-yet-split task tends to be the *largest*
  remaining chunk, meaning it likely still has plenty of further
  recursive splitting left in it — good, substantial work for the idle
  thief to sink its teeth into, rather than stealing something almost
  finished.

Because a stealing thread only ever touches the *opposite* end of another
thread's deque from where the owner is actively working, contention
between the owner and any thief is minimal — a structurally different
contention profile from a single shared queue, and the reason
`ForkJoinPool` scales well specifically for the fine-grained,
recursively-splitting workload shape this phase's `ReconcileTask`
represents.

---

## 3. `RecursiveTask` and the fork-one-compute-other idiom

`ForkJoinReconciler.ReconcileTask` (a `RecursiveTask<List<Mismatch>>`,
since each subtask *produces a result* that needs merging) follows the
standard fork/join shape exactly, worth internalizing as a pattern:

```
if (size <= THRESHOLD) {
    solve directly, sequentially      // base case
} else {
    split into left half, right half
    leftTask.fork();                   // schedule left asynchronously
    rightResult = rightTask.compute(); // compute right DIRECTLY, same thread
    leftResult = leftTask.join();      // wait for the forked left half
    merge leftResult and rightResult
}
```

**Forking only one half, and computing the other directly**, is
deliberate — not laziness. Forking *both* halves would create an extra
task object and an extra scheduling handoff for work the current thread
could simply execute itself immediately, with zero overhead. Forking one
and computing the other inline gets the identical recursive parallelism
with roughly half the task-creation overhead at every level of the
recursion — a real, measurable difference at scale, since this pattern
recurses down to potentially thousands of leaf tasks.

**The threshold matters, and picking it is a genuine judgment call**,
structurally similar to Phase 4's pool-sizing decisions but at a
different layer (work granularity, not thread count): too small a
threshold means task-creation and scheduling overhead dominates actual
work, making the parallel version *slower* than sequential; too large a
threshold under-splits the problem, leaving cores idle that could have
been doing useful stolen work. `ReconciliationBenchmark` sweeps dataset
sizes specifically to make this crossover point observable rather than
asserted — expect the fork/join and parallel-stream versions to actually
*lose* to plain sequential code at the smallest tested size (1,000
records), and pull ahead as size grows.

`RecursiveAction` (`NormalizationAction`) is the sibling for subtasks
that mutate data in place and produce no result to merge — same idiom,
`join()` just returns `void` (used purely to wait for the forked half,
not to retrieve a value).

---

## 4. Why `ForkJoinReconciler` never uses `ForkJoinPool.commonPool()`

`ForkJoinReconciler` constructs its **own dedicated** `ForkJoinPool`
rather than relying on the default `commonPool()` that `pool.invoke()`
would otherwise implicitly reach for. This is a direct continuation of
Phase 7 §5's warning, from the opposite direction: Phase 7 warned about
submitting *blocking I/O work* to the shared common pool and starving
unrelated code; this phase's danger is the mirror image — a *heavy,
sustained CPU-bound* fork/join computation submitted to the shared common
pool can just as easily starve `CompletableFuture`'s default-executor
async chains (which also default to the very same common pool) elsewhere
in the same JVM. Both directions of this interaction point at the same
underlying lesson: **the JVM-wide shared common pool is not free capacity
for you to use casually** — any code with real, sustained resource needs
(whether blocking I/O or heavy CPU work) should get its own deliberately
sized, dedicated pool, exactly as `StageExecutors` (Phase 4),
`PaymentGatewayClient` (Phase 7), and `ForkJoinReconciler` (this phase)
all do.

---

## 5. Parallel streams: how splitting actually works, and where it breaks

`.parallelStream()` (or `.stream().parallel()`) internally uses a
**`Spliterator`** to divide the source across the common pool (by
default) via the exact same fork/join machinery underneath — parallel
streams are not a separate parallelism mechanism, they're a convenience
layer built directly on `ForkJoinPool`.

**Splitting quality depends entirely on the source's data structure.**
An `ArrayList` (or a plain array) supports cheap, O(1) random access —
its `Spliterator` can instantly compute a midpoint index and split in
two, recursively, at essentially no cost. A `LinkedList` has no random
access at all — finding a midpoint requires *walking* the list, which
gets progressively more expensive and directly erodes (and at small
enough element counts, can eliminate) the benefit `.parallel()` is
supposed to provide, for the exact same total element count and thread
count. `ParallelStreamPitfallsDemo`'s splitting-cost demo measures this
difference directly on identical data, backed by an `ArrayList` versus a
`LinkedList`, doing the exact same total work.

**The classic accumulation footgun**: `Collectors.toList()` (and
`collect()` generally) is safe under `.parallel()` because the
`Collector` interface has a well-defined strategy for combining
independently-accumulated partial results from different threads — no
thread ever observes another thread's partially-updated container.
Calling `forEach()` and manually `add()`-ing into a plain, non-thread-safe
`ArrayList` from inside the lambda is a completely different, unsafe
thing: `forEach()` on a parallel stream runs that lambda from *multiple
threads concurrently*, by design — that's the entire point of
`.parallel()`. `ArrayList.add()` has no internal synchronization at all;
concurrent calls can corrupt its internal size bookkeeping, silently drop
elements, or throw, depending on the exact interleaving.
`ParallelStreamPitfallsDemo`'s accumulation demo runs both the correct
`Collectors.toList()` version and the broken `forEach()`-plus-`add()`
version against the identical dataset and compares counts — this is
exactly Phase 1's uncoordinated-shared-mutable-state problem, reachable
from ordinary-looking stream syntax with no lock or atomic anywhere
nearby to raise suspicion in a casual code review, which is precisely
what makes it a dangerous, easy-to-introduce bug in real codebases.

**The blocking-work danger, generalized from Phase 7**: parallel streams
use the shared common pool by default, exactly like `CompletableFuture`'s
no-executor `*Async` methods. Putting a blocking call (network, disk, a
lock wait) directly inside a parallel stream's lambda ties up common-pool
worker threads for the call's *full* blocking duration instead of the
brief CPU burst parallel streams are designed around — the same
JVM-wide-shared-pool danger as Phase 7 §5, just reached via `.parallel()`
instead of `supplyAsync()`. `ParallelStreamPitfallsDemo`'s third demo
proves this directly.

---

## 6. When parallel streams genuinely help vs. quietly hurt

**Help:** large element counts, genuinely CPU-bound per-element work
(enough to amortize splitting/merging overhead), a source with cheap
random-access splitting (arrays, `ArrayList`), stateless and
non-interfering operations (no shared mutable state touched from inside
the stream operations), and a cheap-to-merge result shape (like
`Collectors.toList()`'s combiner).

**Hurt:** small element counts (fixed overhead dominates — proven
directly by `ReconciliationBenchmark`'s smallest size), I/O-bound or
blocking work inside the stream (§5, and Phase 7 §5), sources that don't
split cheaply (`LinkedList`, general `Iterator`-based sources), any
operation with a real dependency on encounter order that forces extra
internal synchronization to preserve it, and anything touching shared
mutable state directly from inside a lambda instead of going through the
stream's own safe collection/reduction machinery.

---

## 7. Running the demos yourself

Same caveat as every prior phase — no `javac` in this sandbox:

```bash
cd phase8-forkjoin-parallel-streams/src/main/java
javac com/orderengine/phase8/*.java -d out
java -cp out com.orderengine.phase8.ReconciliationBenchmark
java -cp out com.orderengine.phase8.ParallelStreamPitfallsDemo
```

`ReconciliationBenchmark` checks correctness (all three implementations
must agree on the exact mismatch count) before printing any timing
comparison — same discipline as every prior phase's benchmarks. Expect
sequential to win or tie at the smallest size (1,000 records) and
fork-join/parallel-stream to pull progressively further ahead as size
grows toward 1,000,000.

---

## 8. Common production bugs from this phase's concepts

1. **`forEach()` + a non-thread-safe collection on a parallel stream** —
   proven directly above; the single most common real-world parallel
   stream bug, precisely because it looks completely ordinary.

2. **Reaching for `.parallel()` on a small or already-fast collection**
   out of habit — pure overhead with no benefit, sometimes a net
   *slowdown*, as `ReconciliationBenchmark` shows concretely at its
   smallest tested size.

3. **`.parallel()` on a `LinkedList` (or other non-random-access
   source)** expecting the same speedup an `ArrayList` would give —
   splitting cost can erode most or all of the theoretical benefit.

4. **Blocking I/O called directly inside a parallel stream's lambda** —
   starves the shared common pool for everything else in the JVM relying
   on it, exactly Phase 7 §5's danger from a different entry point.

5. **Submitting heavy, sustained CPU-bound fork/join work to
   `ForkJoinPool.commonPool()`** (by using `ForkJoinTask.fork()`/`invoke()`
   without an explicit dedicated pool, or by nesting a heavy parallel
   stream inside otherwise-async code) — can starve `CompletableFuture`
   async chains elsewhere in the same JVM, the mirror image of pitfall 4.

---

## 9. Interview Q&A (junior → staff)

**Q (junior): What is work-stealing, in one sentence?**
A: Each `ForkJoinPool` worker thread has its own task deque and works
from its own head (LIFO); when a worker runs out of work, it steals from
the tail of another, busier worker's deque instead of every thread
contending on one shared queue.

**Q (junior/mid): Why does `ForkJoinReconciler.ReconcileTask.compute()`
fork only one half and compute the other directly, instead of forking
both?**
A: Forking both halves creates an extra task object and scheduling
round-trip for work the current thread could just execute immediately at
zero cost. Forking one and computing the other inline achieves the same
recursive parallelism with roughly half the task-creation overhead at
every level of the recursion.

**Q (mid): Why can a parallel version of a computation sometimes be
SLOWER than the sequential version?**
A: Parallelism has real fixed costs — task creation, scheduling, and
merging results back together. For small inputs or cheap per-element
work, that overhead can exceed the total sequential work itself, making
the "parallel" version net slower. Parallelism is a trade of fixed
overhead for potential throughput, and only pays off once there's enough
real work to amortize against.

**Q (mid): Why is `forEach()` with `list.add()` on a parallel stream
unsafe, while `.collect(Collectors.toList())` is safe?**
A: `forEach()` runs its lambda from multiple threads concurrently by
design, and a plain `ArrayList` has no internal synchronization —
concurrent `add()` calls race and can corrupt it. `Collectors.toList()`
(and `collect()` generally) uses the `Collector` interface's defined
strategy for merging independently-accumulated partial results across
threads, so no thread ever observes another's partial, in-progress state.

**Q (mid/senior): Why does a `LinkedList` parallelize worse than an
`ArrayList` for the same data and thread count?**
A: A `Spliterator` needs to find split points to divide work across
threads. `ArrayList`'s random access lets it compute a midpoint index in
O(1) and split instantly, recursively, at essentially no cost.
`LinkedList` has no random access — finding a midpoint requires walking
the list — so splitting cost grows and can erode or eliminate the
parallelism benefit for the same underlying data.

**Q (senior/staff): A colleague wants to use `ForkJoinPool.commonPool()`
directly for a new heavy batch computation because "it's already there
and free." What's your concern, and how does it connect to something you
might have flagged in a completely unrelated part of the same codebase
that uses `CompletableFuture`?**
A: Points worth hitting: (1) the common pool is a single JVM-wide shared
resource — a sustained heavy CPU-bound computation occupying it can
starve anything else in the JVM that also relies on it by default,
including `CompletableFuture`'s no-executor `*Async` continuations and
any other parallel streams running concurrently elsewhere in the service;
(2) this is the exact mirror image of the blocking-I/O-on-common-pool
danger from Phase 7 — same shared resource, same "looks unrelated in the
code, isn't unrelated at runtime" failure shape, just approached from the
CPU-bound side instead of the I/O-bound side; (3) recommend a dedicated,
explicitly-sized `ForkJoinPool` for the new batch work instead — exactly
what `ForkJoinReconciler` does — so this computation's resource needs are
isolated and don't create an invisible dependency between two
code-reviewed-in-isolation, seemingly-unrelated parts of the same
service; (4) if there's already a known async-heavy path elsewhere in the
service relying on the common pool's default availability, this is
worth flagging explicitly as a review comment connecting the two, since
the interaction would otherwise likely only surface as a confusing,
hard-to-correlate production incident under real concurrent load.

---

## 10. What's next

**Phase 9 — Virtual Threads & Structured Concurrency (Java 21+).**
Everything through Phase 8 has used platform (OS) threads — expensive to
create, which is exactly why Phases 4-7 spent so much effort on pooling,
sizing, and reuse rather than just spinning up a thread per unit of work.
Virtual threads change that calculus for I/O-bound work specifically:
extremely cheap, JVM-managed threads where blocking is nearly free
(they simply "unmount" from their carrier platform thread while
waiting, freeing it for other virtual threads, rather than tying up a
scarce OS thread). Covers virtual vs platform threads, the specific
pinning pitfalls that can silently defeat virtual threads' whole benefit,
and `StructuredTaskScope` for structuring concurrent subtasks with a
single, clear parent-child lifetime. Component built: a rewrite of
Phase 6's I/O-heavy notification stage on virtual threads, benchmarked
directly against its original platform-thread-pool version to show
exactly where the difference matters and where it doesn't.

Say **"next"** when ready.
