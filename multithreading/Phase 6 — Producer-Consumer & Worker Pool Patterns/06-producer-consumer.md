# Phase 6 — Producer-Consumer & Worker Pool Patterns

**Project:** Concurrent Order Processing Engine
**Stack:** Java 21
**Components built this phase:** `WorkItem`, `PipelineStage` (reusable base), `OrderValidationStage`, `InventoryReservationStage`, `SinkStage`
**Files:** `src/main/java/com/orderengine/phase6/`

---

## 1. Where this phase sits

Every prior phase built one isolated, independently-tested component:
Phase 2's ledger, Phase 3's ID generator and stack, Phase 4's tuned pools,
Phase 5's specialized queues. This phase is the first time components get
**wired together into an actual running multi-stage pipeline** — real
producer threads submitting real orders, flowing through real concurrent
worker stages, ending at a real sink, with a real, race-free shutdown
sequence. `PipelineStage` is the reusable base every stage (this phase's
two, and every later phase's payment/notification stages) extends.

---

## 2. The classic producer-consumer pattern, generalized

At its simplest: one or more **producer** threads put work into a shared
buffer; one or more **consumer** threads take work out and process it.
The buffer decouples producer and consumer rates — producers don't need
to know or care how fast consumers are, and vice versa, as long as the
buffer has *some* capacity to absorb short-term rate mismatches.

`PipelineStage<IN, OUT>` generalizes this into something a real pipeline
needs: each stage is simultaneously a **consumer** of its own input queue
*and* — via its `Downstream<OUT>` forwarder — a **producer** into the
next stage. This dual role is the entire point of chaining stages:
`OrderValidationStage`'s workers consume `Order`s and produce
`ValidatedOrder`s directly into `InventoryReservationStage`'s queue,
whose workers in turn consume those and produce `ReservedOrder`s into
`SinkStage`. `PipelineDemo` wires exactly this three-stage chain with six
concurrent producer threads and four workers per stage — genuinely
multiple producers *and* multiple consumers at every hop, not the
simplified single-producer/single-consumer toy version of this pattern.

**Design choice worth calling out explicitly:** `PipelineStage` forwards
to the next stage via a **direct, synchronous method call**
(`downstream.submit(result)`, effectively `nextStage::submit`) from
inside the current stage's own worker thread, rather than through a
second intermediate queue this class would separately own and relay. See
§4 for why this specific choice is what makes shutdown ordering
*structurally* safe rather than merely "safe if you get the timing
right."

---

## 3. Bounded buffers and backpressure

Every `PipelineStage`'s input queue is a bounded `ArrayBlockingQueue`.
`submit()` calls `put()`, which **blocks** once the queue is full. This
is deliberate and is the entire backpressure mechanism: a slow stage
naturally throttles everything upstream of it, because upstream
producers (including an upstream *stage's own worker threads*, inside
their `downstream.submit()` call) simply can't push more work in until
there's room. `BoundedBufferBackpressureDemo` makes this directly
observable — a deliberately slow consumer against several fast producers
on a small bounded queue produces a printed queue-depth plateau that
holds steady at capacity, which is producers being blocked inside
`put()`, not a stall or a bug.

Contrast this with what an *unbounded* queue would do under the identical
rate mismatch: depth would climb without limit, producers would never
block at all, and the problem would only surface later as memory
pressure — precisely Phase 5 §3's `PriorityBlockingQueue` danger,
generalized to any unbounded buffer choice anywhere in a pipeline. Every
bound chosen in `PipelineStage` (`inputQueueCapacity`, passed explicitly
at construction for every stage in `PipelineDemo`) is a deliberate
backpressure decision, not a default to leave unexamined.

---

## 4. Why forwarding structurally guarantees safe shutdown ordering

Because `downstream.submit(result)` is a **direct, synchronous** call
made from inside the current stage's own worker thread — not queued for
some separate relay thread to pick up later — a strong guarantee falls
out for free: by the time a worker thread has fully exited (which is what
`shutdown()`'s `CountDownLatch` waits for), **every item that worker ever
produced has already been fully submitted into the downstream stage's own
queue.** There's no separate bridge/relay component whose own drain state
would need independent tracking or its own correctness argument.

This is what makes `PipelineDemo`'s shutdown sequence —
`validationStage.shutdown()`, *then* `reservationStage.shutdown()`, *then*
`sinkStage.shutdown()` — safe as a hard structural fact, not just "safe if
nothing races badly": once `validationStage.shutdown()` returns, there is
categorically no `ValidatedOrder` still "in flight" anywhere that hasn't
already landed in `reservationStage`'s queue, because the only way a
validation worker could have produced one was by directly, synchronously
handing it to `reservationStage.submit()` before that worker was allowed
to exit and count down the latch. This mirrors — and makes rigorous — the
same front-to-back ordering intuition Phase 4's
`StageExecutors.shutdownGracefully()` relied on informally.

---

## 5. The poison-pill pattern

`WorkItem<T>` is a **sealed interface** with exactly two implementations:
`Data<T>` (real work) and `PoisonPill<T>` (a shutdown signal), forcing
every consumer to handle both cases explicitly rather than relying on a
magic null or a separately-tracked flag. `shutdown()` enqueues exactly
`workerCount` pills — one per worker, since each worker consumes exactly
one item per loop iteration and needs exactly one pill to receive and
stop; fewer would strand some workers blocked on `take()` forever, more
would be harmless but unnecessary.

**Why this beats an external flag**, proven concretely (not just
asserted) by `PoisonPillVsFlagDemo`: a worker loop shaped like `while
(running.get()) { poll(); process(); }` has a genuine race — `shutdown()`
can flip `running` to `false` at *any* point, including the instant right
after a producer enqueues several more items but *before* the worker's
next flag check. The worker's next iteration sees `running == false` and
exits immediately — with those items still sitting in the queue,
unprocessed, forever. `PoisonPillVsFlagDemo`'s flag-based scenario forces
exactly this interleaving deterministically (via `CountDownLatch`
coordination, not timing luck) and shows 0 of 5 legitimately-queued items
get processed. The poison-pill version, run identically, always processes
all 5 first — because the shutdown signal travels through the *same*
FIFO queue as the work, there's no separate "check a flag" step with its
own independent race window at all; the queue's own ordering guarantee
*is* the shutdown guarantee.

---

## 6. Running the demos yourself

Same caveat as every prior phase — no `javac` in this sandbox:

```bash
cd phase6-producer-consumer/src/main/java
javac com/orderengine/phase6/*.java -d out
java -cp out com.orderengine.phase6.PoisonPillVsFlagDemo
java -cp out com.orderengine.phase6.BoundedBufferBackpressureDemo
java -cp out com.orderengine.phase6.PipelineDemo
```

`PipelineDemo` is the phase's real correctness check, via the invariants
it prints at the end: `validation.processed + validation.rejected` should
exactly equal the total orders submitted (every order was accounted for
somewhere); `reservation.processed + reservation.rejected` should exactly
equal `validation.processed` (everything validation forwarded was
accounted for by reservation); and `sink.collected().size()` should
exactly equal `reservation.processed` (everything reservation forwarded
made it all the way to the end). The 20% "gadget" traffic deliberately
exceeds that product's 300-unit stock partway through the run, so
`reservation.rejected > 0` is expected and correct — a genuine business
rejection (insufficient stock), not a concurrency bug — while every
number should still balance exactly.

---

## 7. Common production bugs from this phase's concepts

1. **Flag-based shutdown instead of poison pills** — proven directly
   above (§5) to be able to silently strand already-queued work. Any
   worker loop that checks an external "keep running" condition
   *separately* from checking the work queue has this exact race,
   regardless of whether the flag itself is `volatile`/`AtomicBoolean`
   (correct visibility doesn't fix the *ordering* gap between the flag
   check and the queue check).

2. **Unbounded inter-stage queues** — the same danger as Phase 5's
   `PriorityBlockingQueue`, here at the pipeline-architecture level: any
   stage's input queue constructed without an explicit, deliberately
   chosen capacity removes backpressure from that hop entirely, letting
   a slow downstream stage cause unbounded memory growth upstream instead
   of a visible, immediate slowdown.

3. **Shutting every stage down simultaneously** instead of front-to-back
   — can drop work that's genuinely in flight between stages at the
   moment of shutdown, if the ordering guarantee from §4 isn't
   structurally present (e.g., if forwarding were done via a separate
   relay thread with its own untracked drain state, rather than direct
   synchronous calls).

4. **Producer threads not handling `InterruptedException` from
   `submit()`/`put()` correctly** — swallowing the interrupt instead of
   either restoring the interrupt status (`Thread.currentThread().interrupt()`)
   or propagating it can leave a thread that *should* be shutting down
   silently continuing to run, or leave the interrupt signal lost for
   any other code further up the call stack that might have wanted to
   observe it.

5. **A worker's `process()` throwing an exception type not narrow enough
   to distinguish "this specific item was bad" from "something is
   structurally broken"** — `PipelineStage` counts every non-
   `InterruptedException` exception as a per-item rejection and keeps
   the worker looping. A bug that throws on every single item (rather
   than genuinely bad individual items) would silently reject the entire
   remaining stream one item at a time instead of surfacing as an
   obvious, loud failure — worth having a real system distinguish
   "expected per-item validation failure" from "the stage itself is
   broken" as different exception types with different handling, which
   this simplified version doesn't yet do (a good candidate to revisit
   once Phase 12's resilience patterns are in place).

---

## 8. Interview Q&A (junior → staff)

**Q (junior): What is the producer-consumer pattern, in one sentence?**
A: One or more producer threads add work to a shared buffer while one or
more consumer threads remove and process it, with the buffer decoupling
producer and consumer rates from each other.

**Q (junior/mid): Why does a bounded queue provide backpressure and an
unbounded one doesn't?**
A: A bounded queue's `put()` blocks once full, which directly and
immediately slows producers down to match the consumer's actual rate. An
unbounded queue's `put()` never blocks — producers keep adding
unchecked, so a rate mismatch shows up as unlimited memory growth instead
of an immediate, visible slowdown at the point of production.

**Q (mid): Why is a poison pill safer than an external "should I stop"
flag for shutting down a producer-consumer worker?**
A: The flag check and the queue check are two independent operations
with a race window between them — shutdown can flip the flag at the
exact moment work is queued but not yet seen, stranding it. A poison
pill travels through the *same* FIFO queue as real work, so the queue's
own ordering guarantee ensures every item enqueued before the pill is
processed before the pill is ever observed — there's no separate race
window to have.

**Q (mid): In a multi-stage pipeline, why does forwarding to the next
stage via a direct synchronous call (rather than a separate relay
thread/queue) make shutdown ordering easier to reason about?**
A: It guarantees that by the time a stage's worker has fully exited
(and its shutdown latch reflects that), everything that worker ever
produced has already been handed off into the downstream stage's own
queue — there's no separate bridge component with its own independent
drain state that could still be catching up. This makes "shut the
upstream stage down fully, then shut the next one down" a structural
guarantee rather than something that depends on getting timing right.

**Q (mid/senior): A junior engineer proposes replacing a pipeline's
poison-pill shutdown with `ExecutorService.shutdownNow()` for
simplicity, arguing it accomplishes the same thing faster. What's the
actual difference, and when would each be the right call?**
A: `shutdownNow()` *interrupts* running workers and returns whatever
tasks were still queued and never started — it does not guarantee queued
work gets processed, only that you get the list back to potentially deal
with elsewhere, and an interrupt is a request a worker can ignore, not a
guarantee it stops immediately. A poison pill guarantees full, in-order
drain of everything queued before shutdown was requested, at the cost of
worker threads needing to actually reach and consume the pill (i.e. not
being stuck blocked on something else indefinitely). Poison pills are
right when queued work MUST complete (a real, in-flight customer order);
`shutdownNow()` is right for a genuine emergency stop where completing
already-queued work is explicitly not required — different correctness
requirements, not a speed-vs-simplicity trade-off.

**Q (senior/staff): You inherit a 4-stage pipeline where stage 3
occasionally seems to "lose" a handful of items during deploys — not
crashes, just items that entered stage 2 successfully but never appear
in stage 3's output, and it correlates with deploy timing, not load.
Walk through your diagnostic approach.**
A: Points worth hitting: (1) first check exactly how stage 2 signals
shutdown and forwards to stage 3 — if it's a flag-based shutdown rather
than a poison pill, or if forwarding goes through a separate relay
component rather than a direct synchronous call, this is likely exactly
the stranding bug demonstrated in `PoisonPillVsFlagDemo`, and the
deploy-timing correlation is a strong signal since deploys are precisely
when shutdown sequences run; (2) verify by instrumenting queue depth
immediately before and after the shutdown call during a deploy — a
non-zero queue size at the moment a stage reports itself "shut down" is
direct proof of stranded work, exactly as `PoisonPillVsFlagDemo` prints
explicitly; (3) if forwarding is via direct synchronous calls (this
phase's design) rather than a separate relay, verify the shutdown call
ordering itself is front-to-back and that nothing shuts multiple stages
down concurrently; (4) fix by converting any flag-based shutdown to a
poison-pill shutdown (§5) and any relay-based forwarding to direct
synchronous forwarding (§4), then add an assertion/test structurally
like the invariant checks `PipelineDemo` prints (input count equals
processed+rejected at every stage, exactly) as a standing regression
test run after every future shutdown-logic change, since this exact bug
class is easy to silently reintroduce later without such a check.

---

## 9. What's next

**Phase 7 — CompletableFuture & Async Pipelines.** So far every stage
processes synchronously within its own worker thread — a worker blocks
for the full duration of `process()`, including this phase's simulated
I/O waits. This phase introduces genuinely asynchronous composition:
`thenApply`/`thenCompose`/`thenCombine`, exception handling via
`handle`/`exceptionally`, combining multiple in-flight futures, and
running different stages of a single async chain on different executors.
Component built: the payment-service call orchestration — a real
async chain with timeout and fallback behavior, standing in for an actual
external payment gateway call, connecting directly to Phase 4's
`payment` pool sizing rationale (I/O-bound, high wait-to-compute ratio)
by finally making the *waiting* itself non-blocking for the calling
thread rather than just sized-around.

Say **"next"** when ready.
