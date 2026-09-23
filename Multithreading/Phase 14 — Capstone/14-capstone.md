# Phase 14 — Capstone: Full Engine Integration

**Project:** Concurrent Order Processing Engine
**Stack:** Java 21
**Components built this phase:** `OrderProcessingEngine` and every supporting class wiring Phases 1-13 together
**Files:** `src/main/java/com/orderengine/capstone/`

---

## 1. What this phase is, and isn't

Every prior phase built and independently tested one component, in
isolation, against a purpose-built demo. This phase wires the surviving
design decisions from all thirteen into **one running system** —
`OrderProcessingEngine` — and runs it end to end: real startup
coordination, a real multi-stage pipeline mixing two genuinely different
concurrency models (platform-thread pools for CPU-bound stages, virtual
threads for I/O-bound ones), a live metrics dashboard reachable the
entire time it runs, and a real front-to-back graceful shutdown.

This is **not** a rewrite of every phase's full teaching content —
each component here is a condensed, working version of what its
originating phase built and explained in depth; the comments point back
to exactly which phase to reread for the full reasoning behind each
choice. The value of this phase is in the **composition** — seeing how
components designed independently, in isolation, correctly interlock
into a coherent whole without their design decisions conflicting.

---

## 2. The architecture, phase by phase

| Component | Built in | What it contributes here |
|---|---|---|
| `Order`, domain records | Phase 1 | Immutable units of work — safe publication across every stage boundary |
| `StartupCoordinator` | Phase 10 | Dual-`CountDownLatch`: every stage signals ready, then all producers release simultaneously |
| `ValidationStage`, `ReservationStage` | Phase 6 | Poison-pill `PipelineStage`, platform-thread workers |
| — sizing | Phase 4 | `EngineConfig` derives worker counts from `Ncpu+1`, not hardcoded numbers |
| `InventoryLedger` | Phase 2 / 5 | `ConcurrentHashMap.compute()` atomic reservation across concurrent workers |
| `PaymentStage`, `NotificationStage` | Phase 9 | Virtual-thread-per-task dispatch — no platform pool sizing formula needed for I/O-bound work |
| — concurrency cap | Phase 10 / 12 | `Semaphore`-bounded concurrent virtual threads per stage — a bulkhead-shaped limit |
| `CircuitBreaker` | Phase 12 | Guards the simulated payment gateway call inside `PaymentStage` |
| `Metrics`, live dashboard | Phase 3 / 13 | `LongAdder` counters, real HTTP `/metrics` endpoint, reachable throughout the run |
| Shutdown ordering | Phase 6 / 4 | Front-to-back: validation → reservation → payment → notification, each fully drained before the next begins |

What's **not** individually re-demonstrated here (because it would be
redundant with its own phase's dedicated proof): Phase 1's visibility
bug, Phase 3's ABA problem, Phase 7's `CompletableFuture` combinators,
Phase 8's fork/join reconciliation, Phase 11's deadlock scenarios. Those
remain correctly proven in their own phases; this capstone assumes their
lessons and builds on top of them rather than re-litigating them.

---

## 3. Where two concurrency models meet in one pipeline

The most concrete synthesis in this phase: `ValidationStage`/
`ReservationStage` (CPU-bound, `PipelineStage`, fixed platform-thread
pools sized `Ncpu+1`) hand off directly to `PaymentStage`/
`NotificationStage` (I/O-bound, `AsyncIoStage`, virtual-thread-per-task,
no pool-sizing formula at all). This isn't an oversight or
inconsistency — it's Phase 9's central argument applied deliberately:
**use the concurrency model that fits the workload's actual shape**,
not one generic abstraction stretched to cover both. A pipeline with
every stage forced through the same platform-thread `PipelineStage`
shape would need Phase 4's I/O-bound wait/compute formula for the
payment stage; using `AsyncIoStage` there instead sidesteps that
calculation entirely, exactly as Phase 9 §1 argued.

`AsyncIoStage` still needed one genuinely new piece of reasoning beyond
directly reusing Phase 9's `VirtualThreadNotificationStage`: **correct
shutdown drainage**. Phase 6's `PipelineStage.shutdown()` gets its
structural drain guarantee from workers processing synchronously — a
worker's loop iteration doesn't finish (and the shutdown latch doesn't
count down) until its downstream forward call has returned. `AsyncIoStage`
fires each item onto its own virtual thread *asynchronously* from the
dispatcher's perspective, so the dispatcher thread finishing (after
consuming its poison pill) does **not** by itself mean all fired work has
completed. `AsyncIoStage.shutdown()` therefore tracks in-flight virtual
thread tasks explicitly (an `AtomicInteger` plus a wait/notify drain) and
only returns once that count reaches zero — restoring the same
completeness guarantee Phase 6 established for the synchronous case, for
this structurally different asynchronous shape.

---

## 4. Running it yourself

Same caveat as every prior phase — no `javac` in this sandbox:

```bash
cd phase14-capstone/src/main/java
javac com/orderengine/capstone/*.java -d out
java -cp out com.orderengine.capstone.EngineDemo
```

While it's running (a few seconds, processing 5,000 orders), open
another terminal and run `curl http://localhost:8090/metrics` a few
times to watch `validation.processed`, `reservation.processed`,
`payment.processed`/`payment.rejected`, and `notification.processed`
climb in real time. The final printed summary should show every
`.processed` count roughly matching what flowed through, with a small
number of `payment.rejected` from the simulated 3% gateway failure rate
(and possibly a brief circuit-breaker trip if failures cluster densely
enough within its 20-call window — printed circuit behavior would be
visible if `PaymentStage` logged its breaker state, left as a natural
extension pointing back to Phase 12's `ChaosTestHarness` for the fuller
demonstration of that specific behavior).

---

## 5. What a genuinely production-grade version would still need

In the spirit of every prior phase's honesty notes: this capstone proves
the composition is *sound*, not that it's *complete*. A real production
version of this engine would still need, beyond what's built here:

- **Phase 7's full async orchestration** (fallback to a backup gateway,
  not just a circuit-breaker rejection) rather than this capstone's
  simplified single-gateway `PaymentStage`.
- **Phase 8's fork/join reconciliation** as a genuinely separate batch
  job, not wired into the live pipeline at all.
- **Phase 11's deadlock-safe patterns** applied anywhere the engine
  might need to lock two resources together (this capstone's
  `InventoryLedger` never needs two locks at once, so the risk doesn't
  arise here, but a real engine handling refunds/reversals between two
  ledger entries would need exactly Phase 11's consistent-ordering
  discipline).
- **Phase 13's `jcstress` coverage** for the genuinely concurrency-
  critical primitives (`InventoryLedger.reserve()`, `AsyncIoStage`'s
  in-flight tracking) rather than relying on this capstone's own
  scale-based "run it and see the counts balance" proof, which — as
  Phase 13 §6 explained — is a real limitation of this kind of testing,
  not a guarantee.
- **A real metrics library** (Micrometer or equivalent) instead of the
  hand-built `Metrics`/dashboard, and a real load-testing tool (JMH,
  Gatling) instead of this capstone's simple producer-thread loop, for
  anything resembling real capacity planning.

None of these omissions are accidental — they're the same honest
scoping boundary every phase drew around its own demos, now drawn
around the capstone as a whole.

---

## 6. Interview Q&A (junior → staff)

**Q (junior): Why does this engine use platform threads for
validation/reservation but virtual threads for payment/notification,
instead of one consistent model throughout?**
A: The two pairs of stages have genuinely different workload shapes —
validation and reservation are CPU-bound (in-memory checks, no waiting),
where Phase 4's `Ncpu+1` platform-thread sizing is appropriate; payment
and notification are I/O-bound (dominated by waiting on external calls),
where Phase 9's virtual threads let concurrency scale far beyond a small
platform-thread pool without any sizing formula. Using the same model
for both would mean either wasting virtual threads' benefit on CPU-bound
work (Phase 9 §2 — virtual threads don't help when nothing's blocked)
or under-provisioning I/O-bound work behind a needlessly small platform
pool.

**Q (mid): Why did `AsyncIoStage` need its own shutdown-drain logic
instead of reusing `PipelineStage`'s poison-pill mechanism unchanged?**
A: `PipelineStage`'s drain guarantee relies on each worker processing
synchronously — the worker's loop doesn't advance (and the shutdown
latch doesn't count down) until downstream forwarding has completed.
`AsyncIoStage` fires work onto virtual threads asynchronously from its
single dispatcher thread's perspective, so the dispatcher consuming its
poison pill and exiting does not by itself mean all fired work has
finished. It needed explicit in-flight tracking (a counter plus a
wait/notify drain) to restore the same "genuinely done before shutdown
returns" guarantee for this different execution shape.

**Q (mid/senior): The dual-latch `StartupCoordinator` here has every
stage call `signalStageReady()` immediately after `start()`, with no
real initialization delay to wait out. Is the pattern still doing
useful work in this capstone, or is it vestigial?**
A: It's still doing real work: it guarantees every producer thread waits
until *all four* stages have been constructed and started before any
order is submitted, rather than the first producer racing ahead the
instant the first stage happens to be ready — avoiding any stage-specific
"early" traffic pattern that could look different from steady-state
behavior. It would become load-bearing rather than just tidy the moment
any stage gained real startup cost (a connection pool warming up, a
cache preloading), which is a realistic direction for this capstone to
be extended in without changing the coordination pattern itself.

**Q (senior/staff): If you were asked to take this capstone from
"proof of composition" to genuinely production-ready, what's the first
thing you'd add, and why that first?**
A: Points worth hitting: (1) real observability depth before anything
else — Phase 13's honest note that this project's own load-testing and
metrics tooling isn't production-grade is the most consequential gap,
since without trustworthy signal, every other improvement is flying
blind; concretely, that means a real metrics library (proper histogram
algorithms, not this capstone's sort-on-snapshot approach) and
`jcstress` coverage specifically for `InventoryLedger.reserve()` and
`AsyncIoStage`'s in-flight tracking, the two most genuinely
concurrency-critical primitives introduced fresh in this integration
rather than copied unchanged from an already-proven phase; (2) after
that, Phase 12's fuller resilience treatment for `PaymentStage` — a real
fallback path (Phase 7's backup-gateway pattern) rather than this
capstone's bare circuit-breaker rejection, since a production payment
path failing closed with no fallback is a real business problem; (3)
finally, Phase 11's lock-ordering discipline audited and documented for
every place the engine might eventually need to hold two locks
together, even though the current `InventoryLedger` doesn't need it yet
— establishing the discipline before a future feature (refunds,
reversals) introduces the need under time pressure is cheaper than
retrofitting it after an incident.

---

## 7. Closing note

Fourteen phases, one running system. The order this project followed —
memory model and visibility, then locks, then lock-free primitives, then
pools, then concurrent collections, then producer-consumer composition,
then async, then data-parallelism, then virtual threads, then
higher-level synchronizers, then the failure modes all of the above can
produce, then resilience against external failure, then observability,
then integration — mirrors the order these concerns actually surface
when building a real concurrent system: you need to understand what's
happening at the memory level before locks make sense, locks before
pools, pools before the patterns built on top of them, and the full
toolkit before you can reason clearly about how a real system fails and
how to watch it while it's running. That ordering was deliberate from
Phase 1's first line — this is where it was always heading.
