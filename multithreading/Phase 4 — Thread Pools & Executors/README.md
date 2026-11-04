# Java Multithreading & Concurrency Mastery — Concurrent Order Processing Engine

Project-based deep-dive into Java multithreading and concurrency. Every
phase adds a real subsystem to one cohesive `OrderProcessingEngine`
(`edge-gateway → order-service → inventory-service / payment-service /
notification-service`), using pure Java concurrency primitives — no
framework hiding what's happening underneath.

**Stack:** Java 21 (enables Phase 9 virtual threads / structured concurrency)

**Workflow:** say "next" to generate the next phase in full (theory +
production code + bugs/pitfalls + benchmarks + interview Q&A).

---

## Progress

| Phase | Topic | Status |
|---|---|---|
| 1 | JMM & Thread Fundamentals | ✅ Done |
| 2 | Synchronization & Locks | ✅ Done |
| 3 | Atomic Variables & Lock-Free Programming | ✅ Done |
| 4 | Thread Pools & Executors | ✅ Done |
| 5 | Concurrent Collections | ✅ Done |
| 6 | Producer-Consumer & Worker Pool Patterns | ✅ Done |
| 7 | CompletableFuture & Async Pipelines | ✅ Done |
| 8 | Fork/Join & Parallel Streams | ✅ Done |
| 9 | Virtual Threads & Structured Concurrency | ✅ Done |
| 10 | Advanced Synchronizers (Latch/Barrier/Semaphore/Phaser) | ✅ Done |
| 11 | Deadlock, Livelock, Starvation | ✅ Done |
| 12 | Production Resilience Patterns | ✅ Done |
| 13 | Observability & Testing Concurrent Code | ✅ Done |
| 14 | Capstone: Full Engine Integration | ✅ Done |

---

## Phase 1 — JMM & Thread Fundamentals ✅

**Component built:** `OrderIngestionBuffer` (edge-gateway intake buffer)

**Files:**
- `phase1-jmm-thread-fundamentals/01-jmm-and-thread-fundamentals.md` — theory, happens-before, visibility vs atomicity, safe publication, common bugs, interview Q&A
- `phase1-jmm-thread-fundamentals/src/main/java/com/orderengine/phase1/Order.java`
- `phase1-jmm-thread-fundamentals/src/main/java/com/orderengine/phase1/BrokenOrderIngestionBuffer.java` — intentionally buggy (visibility + lost-update)
- `phase1-jmm-thread-fundamentals/src/main/java/com/orderengine/phase1/VisibilityBugDemo.java` — reproduces both bugs
- `phase1-jmm-thread-fundamentals/src/main/java/com/orderengine/phase1/OrderIngestionBuffer.java` — fixed, production version
- `phase1-jmm-thread-fundamentals/src/main/java/com/orderengine/phase1/FixedBufferDemo.java` — proves the fix

**Compile & run locally (JDK 21):**
```bash
cd phase1-jmm-thread-fundamentals/src/main/java
javac com/orderengine/phase1/*.java -d out
java -cp out com.orderengine.phase1.VisibilityBugDemo
java -cp out com.orderengine.phase1.FixedBufferDemo
```

**Key concepts covered:** happens-before, visibility vs. atomicity, volatile
semantics, monitor lock happens-before rule, safe publication / final field
guarantees, thread lifecycle, Runnable vs Callable vs extending Thread.

---

## Phase 2 — Synchronization & Locks ✅

**Component built:** `InventoryLedger` (inventory-service stock tracking) — three implementations

**Files:**
- `phase2-synchronization-and-locks/02-synchronization-and-locks.md` — synchronized internals (mark word, lightweight/heavyweight locking, removed biased locking), ReentrantLock, ReentrantReadWriteLock (fairness, downgrading), StampedLock (optimistic reads), common bugs, interview Q&A
- `phase2-synchronization-and-locks/src/main/java/com/orderengine/phase2/InventoryLedger.java` — shared contract
- `.../InventoryLedgerSynchronized.java` — baseline, correct but serializes reads
- `.../InventoryLedgerReadWriteLock.java` — concurrent readers, fair write lock
- `.../InventoryLedgerStamped.java` — optimistic reads, no lock in common case
- `.../LedgerCorrectnessTest.java` — proves all three are equally correct
- `.../LedgerBenchmark.java` — throughput comparison under 95% read / 5% write load

**Compile & run locally (JDK 21):**
```bash
cd phase2-synchronization-and-locks/src/main/java
javac com/orderengine/phase2/*.java -d out
java -cp out com.orderengine.phase2.LedgerCorrectnessTest
java -cp out com.orderengine.phase2.LedgerBenchmark
```

**Key concepts covered:** synchronized internals (mark word, lightweight
vs heavyweight locking, removed biased locking), ReentrantLock vs
synchronized, ReentrantReadWriteLock (fairness trade-offs, downgrading vs
upgrading), StampedLock optimistic reads, benchmark methodology honesty
(why this isn't JMH).

---

## Phase 3 — Atomic Variables & Lock-Free Programming ✅

**Components built:** `OrderIdGenerator`, `MetricsCounter`, `TreiberStack` (lock-free buffer pool)

**Files:**
- `phase3-atomics-and-lock-free/03-atomics-and-lock-free.md` — CAS mechanics, lock-free/wait-free/obstruction-free definitions, ABA problem, AtomicStampedReference fix, LongAdder internals, common bugs, interview Q&A
- `.../OrderIdGenerator.java` — idiomatic AtomicLong + hand-rolled manual-CAS version for teaching mechanics
- `.../MetricsCounter.java` — AtomicLong vs LongAdder side by side
- `.../TreiberStack.java` — lock-free stack via AtomicReference CAS (ABA-vulnerable under pooling, by design/discussion)
- `.../AbaProblemDemo.java` — deterministic (latch-coordinated, not timing-luck) reproduction of ABA corruption + AtomicStampedReference fix
- `.../CounterBenchmark.java` — AtomicLong vs LongAdder under sweeping thread counts
- `.../Phase3ConcurrencyTest.java` — correctness proofs for OrderIdGenerator uniqueness and TreiberStack push/pop

**Compile & run locally (JDK 21):**
```bash
cd phase3-atomics-and-lock-free/src/main/java
javac com/orderengine/phase3/*.java -d out
java -cp out com.orderengine.phase3.Phase3ConcurrencyTest
java -cp out com.orderengine.phase3.AbaProblemDemo
java -cp out com.orderengine.phase3.CounterBenchmark
```

**Key concepts covered:** CAS mechanics and the retry-loop pattern,
lock-free vs wait-free vs obstruction-free vs blocking, the ABA problem
and AtomicStampedReference/AtomicMarkableReference, LongAdder internals
(striped cells) and when its non-atomic sum() is/isn't acceptable.

---

## Phase 4 — Thread Pools & Executors ✅

**Components built:** `NamedThreadFactory`, `PoolSizingCalculator`, `StageExecutors` (tuned per-stage pools)

**Files:**
- `phase4-thread-pools-and-executors/04-thread-pools-and-executors.md` — ThreadPoolExecutor's exact task-submission algorithm, CPU-bound/I/O-bound sizing formulas, rejection policies, execute() vs submit(), shutdown() vs shutdownNow(), ScheduledExecutorService semantics + silent-death pitfall, common bugs, interview Q&A
- `.../NamedThreadFactory.java` — meaningful thread names, daemon status, uncaught exception handler
- `.../PoolSizingCalculator.java` — Ncpu+1 and Ncpu×U×(1+W/C) formulas
- `.../StageExecutors.java` — one tuned ThreadPoolExecutor per pipeline stage with a deliberately different rejection policy each
- `.../ThreadPoolExecutorInternalsDemo.java` — proves the core→queue→max→reject order live
- `.../RejectionPolicyDemo.java` — all 4 built-in handlers + a custom one under identical saturation
- `.../ScheduledStageDemo.java` — fixed-rate vs fixed-delay + the silent-schedule-death pitfall
- `.../GracefulShutdownDemo.java` — shutdown() vs shutdownNow(), execute() vs submit() exception handling

**Compile & run locally (JDK 21):**
```bash
cd phase4-thread-pools-and-executors/src/main/java
javac com/orderengine/phase4/*.java -d out
java -cp out com.orderengine.phase4.ThreadPoolExecutorInternalsDemo
java -cp out com.orderengine.phase4.RejectionPolicyDemo
java -cp out com.orderengine.phase4.ScheduledStageDemo
java -cp out com.orderengine.phase4.GracefulShutdownDemo
```

**Key concepts covered:** ThreadPoolExecutor's real task-submission order
(core threads are the last thing NOT the first thing added under load —
queue absorbs first), CPU-bound vs I/O-bound pool sizing math, custom
ThreadFactory, all rejection policies and their real behavioral
differences, execute()/submit() exception contracts, shutdown()/
shutdownNow()/awaitTermination semantics, ScheduledExecutorService
fixed-rate vs fixed-delay and its silent-failure trap.

---

## Phase 5 — Concurrent Collections ✅

**Components built:** `OrderStatusTracker` (ConcurrentHashMap), `VipAwareOrderQueue` (PriorityBlockingQueue), `RetryScheduler` (DelayQueue), `NotificationChannelRegistry` (CopyOnWriteArrayList)

**Files:**
- `phase5-concurrent-collections/05-concurrent-collections.md` — ConcurrentHashMap internals (per-bin locking, treeification, striped size()), why thread-safe-per-call ≠ safe-sequence, BlockingQueue family internals (single-lock vs two-lock, unbounded PriorityBlockingQueue danger, DelayQueue leader-follower), CopyOnWriteArrayList snapshot iteration, weakly consistent iteration, common bugs, interview Q&A
- `.../OrderStatusTracker.java` — broken get-then-put vs correct compute() atomic transition
- `.../VipAwareOrderQueue.java` — PriorityBlockingQueue + Semaphore backpressure wrapper (the class is unbounded by default!)
- `.../RetryScheduler.java` — DelayQueue-based exponential backoff for payment retries
- `.../NotificationChannelRegistry.java` — CopyOnWriteArrayList for read-dominated listener registry
- `.../CheckThenActRaceDemo.java` — proves the broken map sequence duplicates a transition; proves compute() fixes it
- `.../BlockingQueueComparisonDemo.java` — ArrayBlockingQueue (1 lock) vs LinkedBlockingQueue (2 locks) under mixed load
- `.../Phase5ConcurrencyTest.java` — VIP ordering, backpressure, retry-delay ordering, concurrent iteration+mutation safety

**Compile & run locally (JDK 21):**
```bash
cd phase5-concurrent-collections/src/main/java
javac com/orderengine/phase5/*.java -d out
java -cp out com.orderengine.phase5.Phase5ConcurrencyTest
java -cp out com.orderengine.phase5.CheckThenActRaceDemo
java -cp out com.orderengine.phase5.BlockingQueueComparisonDemo
```

**Key concepts covered:** ConcurrentHashMap per-bin locking/treeification/
striped size(), compute()/computeIfAbsent()/merge() atomicity vs racy
check-then-act, ArrayBlockingQueue vs LinkedBlockingQueue lock strategy,
PriorityBlockingQueue's unbounded-queue danger, DelayQueue mechanics,
CopyOnWriteArrayList snapshot/weakly-consistent iteration and its O(n)
write cost.

---

## Phase 6 — Producer-Consumer & Worker Pool Patterns ✅

**Components built:** `WorkItem`, `PipelineStage` (reusable base), `OrderValidationStage`, `InventoryReservationStage`, `SinkStage`

**Files:**
- `phase6-producer-consumer/06-producer-consumer.md` — producer-consumer generalized to a dual consumer/producer pipeline stage, bounded-buffer backpressure, why direct synchronous forwarding makes shutdown ordering structurally safe, the poison-pill pattern proven superior to flag-based shutdown, common bugs, interview Q&A
- `.../WorkItem.java` — sealed Data/PoisonPill type
- `.../PipelineStage.java` — reusable bounded-queue, N-worker, poison-pill-shutdown base every stage extends
- `.../Domain.java` — phase-local Order/ValidatedOrder/ReservedOrder records
- `.../OrderValidationStage.java`, `.../InventoryReservationStage.java`, `.../SinkStage.java` — three real chained stages
- `.../PipelineDemo.java` — full end-to-end run: 6 producers -> validation -> reservation -> sink, graceful front-to-back shutdown, self-checking invariants
- `.../BoundedBufferBackpressureDemo.java` — makes backpressure (queue-depth plateau) directly observable
- `.../PoisonPillVsFlagDemo.java` — deterministic proof a flag-based shutdown strands queued work while a poison pill never does

**Compile & run locally (JDK 21):**
```bash
cd phase6-producer-consumer/src/main/java
javac com/orderengine/phase6/*.java -d out
java -cp out com.orderengine.phase6.PoisonPillVsFlagDemo
java -cp out com.orderengine.phase6.BoundedBufferBackpressureDemo
java -cp out com.orderengine.phase6.PipelineDemo
```

**Key concepts covered:** producer-consumer generalized to multi-stage
pipelines, bounded buffers as the actual backpressure mechanism, why
direct synchronous downstream forwarding gives a structural (not just
conventional) shutdown-ordering guarantee, the poison-pill pattern vs
external shutdown flags and the real race the latter has.

---

## Phase 7 — CompletableFuture & Async Pipelines ✅

**Components built:** `PaymentGatewayClient`, `FraudCheckService`, `PaymentOrchestrator`

**Files:**
- `phase7-completablefuture-async/07-completablefuture-async.md` — thenApply vs thenApplyAsync thread-identity semantics, thenCompose vs thenCombine, exceptionally/handle/exceptionallyCompose, CompletionException unwrapping, the ForkJoinPool.commonPool() starvation danger, allOf/anyOf, common bugs, interview Q&A
- `.../PaymentGatewayClient.java` — primary/backup gateway clients, always using an explicit dedicated executor
- `.../FraudCheckService.java` — independent async call combined via thenCombine
- `.../PaymentOrchestrator.java` — orTimeout + exceptionallyCompose fallback + thenCombine + handle, fully composed async chain
- `.../PaymentOrchestratorDemo.java` — 200 concurrent orders, allOf, outcome tally across all 4 terminal states
- `.../AsyncPitfallsDemo.java` — proves thread-identity behavior, proves exceptions vanish unobserved, proves common-pool starvation of unrelated parallel-stream code
- `.../CombiningFuturesDemo.java` — thenCompose, thenCombine (proven genuinely concurrent by timing), allOf, anyOf

**Compile & run locally (JDK 21):**
```bash
cd phase7-completablefuture-async/src/main/java
javac com/orderengine/phase7/*.java -d out
java -cp out com.orderengine.phase7.CombiningFuturesDemo
java -cp out com.orderengine.phase7.AsyncPitfallsDemo
java -cp out com.orderengine.phase7.PaymentOrchestratorDemo
```

**Key concepts covered:** thenApply/thenApplyAsync thread-identity
semantics, thenCompose (dependent) vs thenCombine (independent-parallel),
exceptionally vs handle vs exceptionallyCompose, CompletionException
unwrapping, orTimeout, the ForkJoinPool.commonPool() shared-pool
starvation danger for blocking work, allOf/anyOf.

---

## Phase 8 — Fork/Join & Parallel Streams ✅

**Components built:** `SequentialReconciler`, `ForkJoinReconciler`, `NormalizationAction`, `ParallelStreamReconciler`

**Files:**
- `phase8-forkjoin-parallel-streams/08-forkjoin-parallel-streams.md` — ForkJoinPool work-stealing internals (per-worker deques, LIFO own-work/FIFO steal), the fork-one-compute-other idiom, threshold tuning as a real trade-off, why heavy CPU-bound fork/join work also avoids the shared commonPool (mirrors Phase 7's I/O-bound warning), parallel stream Spliterator splitting quality, the classic forEach+non-thread-safe-collection footgun, when parallel streams help vs hurt, common bugs, interview Q&A
- `.../Domain.java` — reconciliation record types + synthetic dataset generator with real per-record CPU cost
- `.../SequentialReconciler.java` — correctness/performance baseline
- `.../ForkJoinReconciler.java` — RecursiveTask, dedicated pool, threshold-based splitting, fully explained work-stealing
- `.../NormalizationAction.java` — RecursiveAction (in-place mutation, no merge)
- `.../ParallelStreamReconciler.java` — correct (Collectors.toList()) vs deliberately broken (forEach + ArrayList.add()) versions
- `.../ReconciliationBenchmark.java` — sequential vs fork/join vs parallel-stream across dataset sizes, proving the small-N crossover
- `.../ParallelStreamPitfallsDemo.java` — proves racy accumulation, ArrayList vs LinkedList splitting cost, and common-pool starvation from blocking work

**Compile & run locally (JDK 21):**
```bash
cd phase8-forkjoin-parallel-streams/src/main/java
javac com/orderengine/phase8/*.java -d out
java -cp out com.orderengine.phase8.ReconciliationBenchmark
java -cp out com.orderengine.phase8.ParallelStreamPitfallsDemo
```

**Key concepts covered:** ForkJoinPool work-stealing (per-thread deques,
LIFO local work / FIFO stealing), RecursiveTask vs RecursiveAction, the
fork-one-compute-other-directly idiom, threshold tuning, dedicated vs
shared commonPool for CPU-bound work, Spliterator-driven parallel stream
splitting and why ArrayList beats LinkedList, safe collection (Collectors)
vs unsafe manual accumulation (forEach+add) under .parallel().

---

## Phase 9 — Virtual Threads & Structured Concurrency ✅

**Components built:** `PlatformThreadNotificationStage`, `VirtualThreadNotificationStage`, `PinningDemo`, `StructuredConcurrencyDemo`

**Files:**
- `phase9-virtual-threads/09-virtual-threads.md` — platform vs virtual thread mechanics (mounting/unmounting/carriers), why Phase 4's pool-sizing formulas become unnecessary for I/O-bound virtual-thread code, what virtual threads don't help (CPU-bound work), the synchronized-blocking pinning pitfall proven directly, ThreadLocal-at-scale anti-pattern, StructuredTaskScope vs raw CompletableFuture composition, common bugs, interview Q&A
- `.../NotificationSender.java` — simulated blocking I/O call
- `.../PlatformThreadNotificationStage.java` — Phase-6-style fixed pool baseline
- `.../VirtualThreadNotificationStage.java` — one virtual thread per task, no sizing formula
- `.../NotificationBenchmark.java` — 10,000 concurrent I/O-bound sends, platform pool vs virtual threads
- `.../PinningDemo.java` — proves synchronized blocks pin virtual threads to their carrier while ReentrantLock doesn't
- `.../StructuredConcurrencyDemo.java` — ShutdownOnFailure / ShutdownOnSuccess (preview API), contrasted with Phase 7's CompletableFuture composition

**Compile & run locally (JDK 21):**
```bash
cd phase9-virtual-threads/src/main/java
javac com/orderengine/phase9/NotificationSender.java com/orderengine/phase9/PlatformThreadNotificationStage.java com/orderengine/phase9/VirtualThreadNotificationStage.java com/orderengine/phase9/NotificationBenchmark.java com/orderengine/phase9/PinningDemo.java -d out
java -cp out com.orderengine.phase9.NotificationBenchmark
java -Djdk.virtualThreadScheduler.parallelism=2 -cp out com.orderengine.phase9.PinningDemo

# StructuredConcurrencyDemo needs --enable-preview (Java 21 preview API):
javac --release 21 --enable-preview com/orderengine/phase9/StructuredConcurrencyDemo.java -d out
java --enable-preview -cp out com.orderengine.phase9.StructuredConcurrencyDemo
```

**Key concepts covered:** platform vs virtual thread mechanics (carriers,
mounting/unmounting), why virtual threads help I/O-bound but not
CPU-bound work, carrier-thread pinning under synchronized (the most
important virtual-thread migration pitfall) and why ReentrantLock avoids
it, ThreadLocal misuse at virtual-thread scale, StructuredTaskScope's
structural parent-child task lifetime vs CompletableFuture's detached
futures, ShutdownOnFailure/ShutdownOnSuccess vs anyOf's lack of
cancellation.

---

## Phase 10 — Advanced Synchronizers ✅

**Components built:** `StartupCoordinator`, `PaymentRateLimiter`, plus standalone CyclicBarrier/Exchanger/Phaser demos

**Files:**
- `phase10-advanced-synchronizers/10-advanced-synchronizers.md` — CountDownLatch's one-shot nature and the dual-latch startup pattern, Semaphore as a concurrency cap distinct from pool sizing (and why virtual threads make this more important, not less), CyclicBarrier's per-round barrier action, Phaser's dynamic party registration vs CyclicBarrier's fixed count, Exchanger's two-party rendezvous vs BlockingQueue decoupling, a tool-selection table, common bugs, interview Q&A
- `.../StartupCoordinator.java` + `StartupCoordinatorDemo.java` — dual-CountDownLatch pattern, proven to release all stages within a tight time spread regardless of differing init times
- `.../PaymentRateLimiter.java` + `PaymentRateLimiterDemo.java` — Semaphore-based concurrency cap, proven to hold firm against 100 virtual-thread-fired calls
- `.../BatchPhaseCyclicBarrierDemo.java` — multi-round batch processing with a per-round barrier action
- `.../DoubleBufferExchangerDemo.java` — producer/consumer double-buffering via Exchanger
- `.../DynamicPhaserDemo.java` — workers dynamically deregistering mid-job, phaser correctly advancing on the shrinking party count

**Compile & run locally (JDK 21):**
```bash
cd phase10-advanced-synchronizers/src/main/java
javac com/orderengine/phase10/*.java -d out
java -cp out com.orderengine.phase10.StartupCoordinatorDemo
java -cp out com.orderengine.phase10.PaymentRateLimiterDemo
java -cp out com.orderengine.phase10.BatchPhaseCyclicBarrierDemo
java -cp out com.orderengine.phase10.DoubleBufferExchangerDemo
java -cp out com.orderengine.phase10.DynamicPhaserDemo
```

**Key concepts covered:** CountDownLatch one-shot semantics and the
dual-latch startup pattern, Semaphore concurrency capping (distinct from
pool sizing) with fairness trade-offs, CyclicBarrier's reusable
multi-round rendezvous and elected barrier action, Phaser's dynamic
party registration, Exchanger's two-party lockstep handoff vs queue-based
decoupling.

---

## Phase 11 — Deadlock, Livelock, Starvation ✅

**Components built:** `Account`, `DeadlockDemo`, `FixedTransfer`, `LivelockDemo`, `StarvationDemo`

**Files:**
- `phase11-deadlock-livelock-starvation/11-deadlock-livelock-starvation.md` — the four Coffman conditions, two independent deadlock fixes (consistent ordering vs tryLock+backoff) each breaking a different condition, ThreadMXBean/jstack/jcmd deadlock diagnosis, livelock vs deadlock's different thread-dump signature, starvation as a third distinct failure mode invisible to aggregate throughput monitoring, common bugs, interview Q&A
- `.../Account.java` — locked account used by the transfer scenario
- `.../DeadlockDemo.java` — deterministic (latch-forced) real deadlock + programmatic ThreadMXBean detection/diagnostic dump
- `.../FixedTransfer.java` — both fixes (consistent ordering; tryLock+timeout+backoff) proven against the identical adversarial interleaving
- `.../LivelockDemo.java` — threads colliding in perfect lockstep with zero randomized backoff, fixed by adding it
- `.../StarvationDemo.java` — fair vs non-fair lock under sustained adversarial contention, measuring one victim thread's actual share

**Compile & run locally (JDK 21):**
```bash
cd phase11-deadlock-livelock-starvation/src/main/java
javac com/orderengine/phase11/*.java -d out
java -cp out com.orderengine.phase11.DeadlockDemo
java -cp out com.orderengine.phase11.FixedTransfer
java -cp out com.orderengine.phase11.LivelockDemo
java -cp out com.orderengine.phase11.StarvationDemo
```

**Key concepts covered:** the four Coffman conditions and which fix
breaks which one, ThreadMXBean.findDeadlockedThreads() as the same
mechanism behind jstack/jcmd, livelock's actively-running-but-no-progress
signature distinct from deadlock's genuinely-blocked signature,
starvation as healthy-in-aggregate-but-unfair-to-one-thread, randomized
backoff as the fix connecting tryLock-deadlock-avoidance to
livelock-avoidance.

---

## Phase 12 — Production Resilience Patterns ✅

**Components built:** `TokenBucketRateLimiter`, `CircuitBreaker`, `Bulkhead`, `ResilientPaymentGateway`

**Files:**
- `phase12-resilience-patterns/12-resilience-patterns.md` — token bucket vs Phase 10's semaphore (rate vs concurrency), circuit breaker CLOSED/OPEN/HALF_OPEN state machine and the single-probe rule, bulkhead as failure isolation distinct from load management, why composition order (rate limit -> circuit breaker -> bulkhead) matters, common bugs, interview Q&A
- `.../TokenBucketRateLimiter.java` — lazy-refill token bucket, allows bounded bursts
- `.../CircuitBreaker.java` — full 3-state machine, sliding-window failure rate, CAS-guarded single HALF_OPEN probe
- `.../Bulkhead.java` — per-dependency isolated executor pool
- `.../FlakyPaymentGateway.java` — runtime-adjustable health profile (healthy/slow/failing) for chaos testing
- `.../ResilientPaymentGateway.java` — composes all three in the correct order with a unified fallback
- `.../ChaosTestHarness.java` — full lifecycle test (healthy->failing->OPEN->recovery->CLOSED) + bulkhead isolation proof (hung dependency can't starve a healthy one)

**Compile & run locally (JDK 21):**
```bash
cd phase12-resilience-patterns/src/main/java
javac com/orderengine/phase12/*.java -d out
java -cp out com.orderengine.phase12.ChaosTestHarness
```

**Key concepts covered:** token bucket rate limiting (throughput cap,
bursting, lazy refill) vs semaphore-based concurrency capping, circuit
breaker state machine and single-probe HALF_OPEN recovery testing,
bulkhead as structural failure isolation between dependencies, and the
deliberate cheapest-check-first composition order for wrapping a real
call in all three.

---

## Phase 13 — Observability & Testing Concurrent Code ✅

**Components built:** `MetricsRegistry`, `LatencyHistogram`, `HealthDashboardServer`, `ThreadDumpAnalyzer`, `StressTestHarness`

**Files:**
- `phase13-observability-and-testing/13-observability-and-testing.md` — LongAdder-backed live metrics, StampedLock-guarded percentile histogram (with an honesty note on real streaming-histogram algorithms), a real live HTTP dashboard via the JDK's built-in HttpServer, ThreadDumpAnalyzer generalizing Phase 11's deadlock detection to full triage with a diagnostic playbook, StressTestHarness built on Phase 9 virtual threads + Phase 10 semaphore, why ordinary tests can't catch these bug classes and what jcstress does differently, common bugs, interview Q&A
- `.../MetricsRegistry.java` + `LatencyHistogram.java` — hand-built metrics primitives with the reasoning made explicit
- `.../HealthDashboardServer.java` — live `/metrics` and `/health` HTTP endpoints, no external dependencies
- `.../ThreadDumpAnalyzer.java` — full thread-state breakdown + deadlock detection, jstack-equivalent via ThreadMXBean
- `.../StressTestHarness.java` — virtual-thread load generator feeding the live registry, with an honesty note on JMH/Gatling being the real production tools
- `.../JcstressRaceExample.java` — illustrative (non-runnable) jcstress test shape + explanation of forced-interleaving exploration
- `.../EngineObservabilityDemo.java` — ties it all together: live dashboard reachable *during* a running stress test, plus a mid-run thread dump snapshot

**Compile & run locally (JDK 21):**
```bash
cd phase13-observability-and-testing/src/main/java
javac com/orderengine/phase13/MetricsRegistry.java com/orderengine/phase13/LatencyHistogram.java com/orderengine/phase13/HealthDashboardServer.java com/orderengine/phase13/ThreadDumpAnalyzer.java com/orderengine/phase13/StressTestHarness.java com/orderengine/phase13/EngineObservabilityDemo.java -d out
java -cp out com.orderengine.phase13.EngineObservabilityDemo
# while running, in another terminal: curl http://localhost:8089/metrics (repeat during the run to see it update live)
```

**Key concepts covered:** LongAdder for hot metrics counters, a real live
HTTP metrics endpoint via the JDK's built-in HttpServer, ThreadMXBean-based
full thread-dump triage (BLOCKED/WAITING spikes/thread-count growth as
distinct diagnostic signals), virtual-thread+semaphore load generation,
and why only jcstress-style forced interleaving exploration (not
scale-based or ordinary unit testing) can systematically catch the race
classes covered throughout this project.

---

## Phase 14 — Capstone: Full Engine Integration ✅

**Components built:** `OrderProcessingEngine` wiring every prior phase's surviving design decisions together

**Files:**
- `phase14-capstone/14-capstone.md` — full architecture table mapping every component to its originating phase, why two concurrency models (platform-thread pools for CPU-bound stages, virtual threads for I/O-bound ones) coexist deliberately in one pipeline, why AsyncIoStage needed its own shutdown-drain logic beyond Phase 6's synchronous guarantee, an honest scope note on what's still missing for genuine production-readiness, interview Q&A, closing note on why the 14-phase ordering was deliberate
- `.../OrderProcessingEngine.java` — the orchestrator: startup coordination, full pipeline wiring, load drive, front-to-back shutdown
- `.../EngineConfig.java` — pool sizes derived from Phase 4's formulas, not hardcoded
- `.../PipelineStage.java` + `WorkItem.java` — Phase 6's poison-pill base for CPU-bound stages
- `.../AsyncIoStage.java` — the capstone's new synthesis: virtual-thread dispatch (Phase 9) + semaphore concurrency cap (Phase 10/12) + correct in-flight shutdown drain
- `.../ValidationStage.java`, `ReservationStage.java`, `InventoryLedger.java` — CPU-bound stages + Phase 2/5's atomic ledger
- `.../PaymentStage.java`, `CircuitBreaker.java`, `NotificationStage.java` — I/O-bound stages + Phase 12's resilience
- `.../Metrics.java` — Phase 3 LongAdder counters + Phase 13's live HTTP dashboard, condensed
- `.../StartupCoordinator.java` — Phase 10's dual-latch pattern
- `.../EngineDemo.java` — entry point

**Compile & run locally (JDK 21):**
```bash
cd phase14-capstone/src/main/java
javac com/orderengine/capstone/*.java -d out
java -cp out com.orderengine.capstone.EngineDemo
# while running, in another terminal: curl http://localhost:8090/metrics (repeat to watch it update live)
```

**Key concepts covered:** composing platform-thread and virtual-thread
concurrency models within one pipeline based on each stage's actual
workload shape, extending Phase 6's poison-pill drain guarantee to an
asynchronous virtual-thread dispatch shape, config-driven pool sizing
derived from formulas rather than hardcoded constants, and an honest
accounting of the gap between "proof of composition" and genuine
production-readiness.

---

## Domain model (consistent across all phases)

```
edge-gateway → order-service → inventory-service
                              → payment-service
                              → notification-service
```

`Order` (immutable record) is the shared unit of work flowing through every
stage built in this project.
