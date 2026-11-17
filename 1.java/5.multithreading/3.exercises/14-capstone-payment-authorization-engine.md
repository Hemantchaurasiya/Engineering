# Group 14 — Capstone: Real-Time Payment Authorization Engine

**Level:** Expert | **Duration:** 1–2 weeks | **Prerequisites:** Groups 1–13

## Goal
Build a single-JVM engine that authorizes card payments under heavy concurrency, combining nearly every concept from this course. It must be correct under stress, observable, resilient, and shut down gracefully.

## Functional Requirements
1. **Ingest** authorization requests (simulated at up to 20,000 req/s).
2. **Per-card ordering:** requests for the same card are processed strictly in order; different cards in parallel. *(12.5)*
3. **Pipeline:** validate → fraud-score → balance-hold → decision → notify. *(12.1)*
4. **Fraud scoring** calls 4 external scoring services in parallel with a hard 150 ms budget; partial results allowed; one required service. *(6.2, 6.4, 10.5)*
5. **Balance holds** are atomic per account and never exceed available funds. *(2.2, 3.6, 4.3)*
6. **Idempotency:** the same request id delivered concurrently must be applied once. *(12.8)*
7. **Rate limiting** per merchant and global. *(12.2)*
8. **Resilience:** circuit breaker and bulkhead around each external dependency; retry only where safe. *(12.3, 6.5)*
9. **Single-flight cache** for merchant risk profiles with refresh-ahead. *(12.4, 12.6)*
10. **Notifications** (email/SMS/webhook) sent through a retry scheduler with dead-lettering. *(12.7)*
11. **Graceful shutdown:** in-flight authorizations finish or are recorded for recovery. *(5.6)*

## Non-Functional Requirements
- p99 authorization latency ≤ 250 ms at 10,000 req/s.
- No unbounded queues or thread pools.
- Metrics: per-stage queue depth, latency histograms, rejected count, breaker states, pool utilisation. *(13.4)*
- Trace + correlation ids across all thread hops. *(13.5)*
- Deadlock-free by design (documented lock hierarchy) with a runtime `DeadlockDetector`. *(11.1)*

## Build It Twice
- **Version A:** platform threads + `ThreadPoolExecutor` + `CompletableFuture`.
- **Version B:** Java 21 virtual threads + `Semaphore` bulkheads + structured concurrency.
- Compare throughput, p99 latency, memory, code complexity, and failure behaviour. Write your findings in `COMPARISON.md`.

## Verification Plan
| Test | Pass criteria |
|---|---|
| Invariant stress test | Sum of balances + holds is conserved over 10M random operations |
| Ordering test | Per-card sequence numbers strictly increasing |
| Duplicate-delivery test | 30% duplicate requests, zero double holds |
| Chaos test | Inject 2 s stalls / 50% errors in each dependency; p99 stays within budget (degraded mode) |
| Overload test | 5× expected load: system sheds load gracefully, memory flat |
| Shutdown test | `kill -TERM` under load: no lost requests (each either completed or recorded) |
| Deadlock test | Detector reports zero deadlocks over a 30-minute soak |
| jcstress / harness | Core data structures pass formal and stress tests |

## Deliverables
- Source code with tests
- `ARCHITECTURE.md` (thread model diagram: which pool/threads own which stage, lock hierarchy, backpressure points)
- `COMPARISON.md` (Version A vs B)
- `RUNBOOK.md` (how to diagnose saturation, deadlock, and retry storms)
- Benchmark results and dashboards

## Self-Assessment Rubric
| Skill | Beginner | Proficient | Expert |
|---|---|---|---|
| Correctness | Passes basic tests | Passes stress tests | Formal reasoning + jcstress |
| Resilience | Timeouts only | Breaker + bulkhead | Chaos-tested, degraded modes |
| Performance | Works | Tuned pools with data | Profiled, contention-free hot paths |
| Observability | Logs | Metrics | Traces + metrics + runbook |
| Shutdown | Abrupt | Graceful | Recoverable and tested |
