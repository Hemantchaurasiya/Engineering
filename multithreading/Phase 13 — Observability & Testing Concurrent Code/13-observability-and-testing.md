# Phase 13 — Observability & Testing Concurrent Code

**Project:** Concurrent Order Processing Engine
**Stack:** Java 21
**Components built this phase:** `MetricsRegistry`, `LatencyHistogram`, `HealthDashboardServer`, `ThreadDumpAnalyzer`, `StressTestHarness`
**Files:** `src/main/java/com/orderengine/phase13/`

---

## 1. Where this phase sits

Every phase so far has proven its claims with a demo you run once and
read the printed output from. Real production systems need to be
observable **continuously, while running** — not just correct when you
happen to test them. This phase builds the piece explicitly deferred
since Phase 3: `MetricsCounter`'s `LongAdder`-backed counters, now wired
to an actual live HTTP endpoint (`MetricsRegistry` + `HealthDashboardServer`),
plus the tooling to capture and interpret a thread dump under real load
(`ThreadDumpAnalyzer`, generalizing Phase 11's `ThreadMXBean` deadlock
detection to full-dump triage) and to generate that load in the first
place (`StressTestHarness`).

---

## 2. `MetricsRegistry`: the primitive choices made explicit

`MetricsRegistry` is deliberately hand-built rather than pulling in a
real library like Micrometer — every production service should use a
real one; this class exists specifically to keep the underlying
concurrency reasoning visible. Every counter is a `LongAdder`, for
exactly Phase 3 §5's reason: these are hot, high-write, approximate-read
counters (incremented on potentially every single order across every
pipeline stage), precisely `LongAdder`'s sweet spot, and precisely the
wrong place to reach for `AtomicLong`'s stronger-but-slower guarantees
or, far worse, a lock around a shared counter.

`LatencyHistogram` uses a `StampedLock`-guarded ring buffer, computing
percentiles on demand from a sorted copy. Worth being honest about here,
in the same spirit as every prior phase's benchmark disclaimers: this is
**not** how a real metrics library computes percentiles at scale — proper
implementations use streaming histogram algorithms (e.g. HDRHistogram)
that avoid storing and sorting raw samples, which gets expensive at real
volume. This implementation is intentionally simple to keep the
concurrency mechanics visible; production code should use a real,
well-tested percentile-estimation library instead.

---

## 3. `HealthDashboardServer`: real, continuous exposure

Phase 12's `ChaosTestHarness` only ever printed final counts once, at the
end of a run. `HealthDashboardServer` uses `com.sun.net.httpserver.HttpServer`
(built into the JDK — no external dependency needed to prove this
concept) to expose `MetricsRegistry`'s data over real HTTP, live, while
the engine is running — the same shape a Prometheus scraper or an
internal ops dashboard would consume in production, just without a real
metrics library's export-format sophistication. `EngineObservabilityDemo`
proves this is genuinely live, not just structurally present: it starts
the dashboard, waits, then runs a stress test while the dashboard stays
reachable, so counters visibly update mid-run rather than only appearing
after everything finishes.

---

## 4. `ThreadDumpAnalyzer`: generalizing Phase 11's deadlock detection

Phase 11's `DeadlockDemo` used `ThreadMXBean.findDeadlockedThreads()` for
one specific purpose. `ThreadDumpAnalyzer` generalizes the same
underlying API — `ThreadMXBean.dumpAllThreads()` — to full-dump triage:
a breakdown of every thread by state, plus deadlock detection, plus a
list of currently `BLOCKED` threads and what they're waiting on. This is
the same data `jstack <pid>` / `jcmd <pid> Thread.print` show against a
real running process; the programmatic version here exists so this
project's own demos can inspect themselves without needing external
tooling, but the diagnostic reasoning transfers directly to reading a
real dump:

- **Many `BLOCKED` threads on the same lock** → a contention hotspot;
  revisit Phase 2's lock-choice guidance (would `ReadWriteLock`/
  `StampedLock` help if traffic is read-heavy?) or check for a Phase 11
  deadlock cycle if the blocked threads form one.
- **A spike in `WAITING`/`TIMED_WAITING` correlated with a latency
  incident** → threads piling up waiting on a slow downstream call —
  Phase 12 territory: is a circuit breaker or bulkhead missing here?
- **Thread count climbing across dumps taken minutes apart** → a thread
  leak — revisit Phase 4's pool-sizing discipline (something creating
  unbounded/unpooled threads instead of using a bounded pool?), or Phase
  9 (should this be virtual threads, where raw thread *count* stops
  being the metric that matters and carrier-level metrics take over?).

---

## 5. `StressTestHarness`: generating the load worth observing

Uses Phase 9's virtual-thread-per-task model specifically so the load
generator's *own* mechanics don't become an accidental bottleneck in
what's being measured — launching thousands of concurrent callers needs
no pool-sizing calculation of its own. Concurrency is capped via a Phase
10 `Semaphore` to model a sustained target load rather than one
instantaneous burst, and every call's outcome and latency feed directly
into `MetricsRegistry`, visible on the live dashboard the whole time.

**Honesty note, consistent with every prior phase's benchmark
disclaimers**: this is a convenient tool for exploring this project's own
components interactively, not a substitute for real tools. Production
capacity planning or performance regression testing needs **JMH** for
precise microbenchmarks (proper warmup handling, dead-code-elimination
protection, statistical rigor this hand-rolled loop doesn't attempt) or
**Gatling/k6/Locust** for full-scale realistic load testing against a
running service over the network with proper ramp profiles and
distributed load generation.

---

## 6. `jcstress`: why ordinary tests can't catch these bugs, and what can

`JcstressRaceExample` is **not runnable** with this project's plain
`javac`/`java` commands — `jcstress` is a separate OpenJDK Code Tools
artifact requiring its own Maven/Gradle project setup. It's included as
an illustration of the real API shape and, more importantly, the
reasoning for why it exists.

Every "broken, then fixed" demo throughout this entire project has
reproduced its race one of two ways: **sheer scale** (running enough
iterations that a bad interleaving shows up often enough to observe —
Phase 1's `VisibilityBugDemo`, Phase 8's racy accumulation demo), which
is exactly what makes these bug classes dangerous in production, since
they can pass thousands of test runs and still occur rarely at real
scale; or **deterministic forced coordination** via `CountDownLatch`
(Phase 1's `AbaProblemDemo`, Phase 6's `PoisonPillVsFlagDemo`), which
only works when you already know *exactly* which interleaving to force —
useless for discovering a race you don't already suspect.

`jcstress` solves this differently: it runs a test method many millions
of times, deliberately varying real OS-level scheduling and JIT
compilation state across runs to explore as many actual interleavings as
possible — far more thoroughly than relying on natural scheduling
variance. It categorizes every observed outcome against a declared set
of `@Outcome(expect = ACCEPTABLE)` / `@Outcome(expect = FORBIDDEN)`
results and fails hard the moment a forbidden outcome is ever observed,
turning "this might not show up in your test run" into "this framework
actively hunts for the interleaving that reveals it" — and reports how
*often* each outcome occurred, which is itself valuable: a bug that
occurs in 1-in-10-million interleavings versus 1-in-10 represents very
different real-world risk, information ordinary pass/fail testing
doesn't surface at all.

---

## 7. Running the demos yourself

Same caveat as every prior phase — no `javac` in this sandbox:

```bash
cd phase13-observability-and-testing/src/main/java
javac com/orderengine/phase13/MetricsRegistry.java \
      com/orderengine/phase13/LatencyHistogram.java \
      com/orderengine/phase13/HealthDashboardServer.java \
      com/orderengine/phase13/ThreadDumpAnalyzer.java \
      com/orderengine/phase13/StressTestHarness.java \
      com/orderengine/phase13/EngineObservabilityDemo.java \
      -d out
java -cp out com.orderengine.phase13.EngineObservabilityDemo
```

While it's running, open another terminal and run
`curl http://localhost:8089/metrics` a few times during the ~few-second
stress test window to watch the counters and latency percentiles change
between requests — the actual proof this is live, not just structurally
present. `JcstressRaceExample.java` is excluded from this compile
command deliberately (it's illustrative only, as explained in §6) and
would fail to compile if included, since its example test body is inside
a comment, not real annotated code — running a genuine jcstress test
requires the separate project setup described in that file.

---

## 8. Common production bugs from this phase's concepts

1. **No live metrics at all — only logs** — makes it impossible to see a
   developing problem (rising latency, climbing error rate) until
   someone happens to go looking through logs after the fact, or an
   alert fires on a threshold that's already been breached for a while.

2. **A metrics registry itself becoming a bottleneck** — using the wrong
   primitive (a `synchronized` counter, or `AtomicLong` under very high
   contention) for hot counters can make observability code itself a
   measurable drag on the system it's trying to observe; exactly why
   Phase 3's `LongAdder` reasoning matters here, not just as a Phase 3
   exercise.

3. **Treating a hand-rolled load-test loop's numbers as production-grade
   capacity planning data** — a quick load-test tool like
   `StressTestHarness` is genuinely useful for interactive exploration
   but doesn't have JMH's statistical rigor or a real load-testing
   tool's realistic traffic shaping; presenting its numbers as an
   authoritative capacity plan risks a wrong sizing decision.

4. **Only checking for deadlock when a system reports "stuck"** — Phase
   11's lesson recurring here: `ThreadDumpAnalyzer`'s full state
   breakdown (not just the deadlock check) is needed to distinguish
   deadlock from livelock from starvation from ordinary heavy load,
   since a deadlock check alone returns a clean "no deadlock" for all
   three of the other explanations.

5. **Assuming ordinary unit/integration tests would have caught a
   concurrency bug that shipped to production** — the entire motivation
   for `jcstress`: standard tests run a fixed, small number of times
   under whatever scheduling the test machine happens to produce, which
   is a poor substitute for deliberately exploring the interleaving
   space the way `jcstress` does.

---

## 9. Interview Q&A (junior → staff)

**Q (junior): Why use `LongAdder` instead of `AtomicLong` for a metrics
counter incremented on every request?**
A: Metrics counters are hot (incremented extremely frequently) and
read comparatively rarely, and don't need a perfectly precise
instantaneous value — exactly `LongAdder`'s sweet spot (Phase 3 §5): it
trades linearizable reads for dramatically better write throughput under
contention by striping the counter across internal cells.

**Q (junior/mid): What's the practical difference between a thread dump
showing many `BLOCKED` threads versus many `WAITING`/`TIMED_WAITING`
threads?**
A: `BLOCKED` means threads are actively contending for a lock someone
else holds — a real contention hotspot worth investigating. `WAITING`/
`TIMED_WAITING` is often completely normal (idle pool threads waiting on
an empty queue look exactly like this) — what matters is whether the
COUNT of such threads is spiking abnormally, which can indicate threads
piling up waiting on a slow downstream call rather than genuine
lock contention.

**Q (mid): Why can't ordinary unit tests reliably catch the kind of
race conditions covered throughout this project (Phase 1's visibility
bug, Phase 5's check-then-act race, Phase 8's racy accumulation)?**
A: Ordinary tests run a fixed, small number of times under whatever
thread scheduling the test machine happens to produce — they can pass
consistently even when a real race exists, if the specific bad
interleaving just doesn't happen to occur during those particular runs.
This is exactly why these bugs are dangerous in production: they can
pass extensive testing and still occur, rarely, at real scale and under
different hardware/JVM conditions.

**Q (mid): What does `jcstress` do differently from just running a test
in a loop many times?**
A: It deliberately varies real OS-level scheduling and JIT compilation
states across its many runs, specifically trying to explore as many
different actual thread interleavings as possible, rather than passively
hoping natural scheduling variance happens to surface a bad one. It also
categorizes every observed outcome against declared acceptable/forbidden
results and reports exactly how often each occurred, providing risk
information ("occurs 1-in-10 vs 1-in-10-million interleavings") that a
simple pass/fail loop doesn't.

**Q (senior/staff): Your team ships a service with solid unit test
coverage, including some concurrency-focused tests using scale-based
reproduction (many threads, many iterations) similar to this project's
early-phase demos. A rare, hard-to-reproduce data corruption bug still
reaches production months later. How would you explain this to
leadership, and what would you recommend changing about the testing
strategy?**
A: Points worth hitting: (1) scale-based reproduction (run many threads,
many times) increases the CHANCE of surfacing a bad interleaving but
provides no guarantee — it's fundamentally sampling the interleaving
space via whatever the test machine's actual scheduler happens to
produce, not systematically exploring it, so a bug requiring a rare,
specific interleaving can genuinely evade thousands of test runs and
still occur under different production hardware, JVM version, or load
shape; (2) this isn't a failure of the team's diligence — it's an
inherent limitation of scale-based testing for this specific bug class,
worth stating plainly rather than treating as a process failure to
assign blame for; (3) recommend introducing `jcstress` for the specific,
identified-as-risky shared-mutable-state code paths (not everywhere —
it's a heavier, more specialized tool, appropriate for genuinely
concurrency-critical code like Phase 1-3's foundational primitives, not
every line that happens to touch a thread) as a systematic
interleaving-exploration layer complementing, not replacing, existing
scale-based tests; (4) also recommend auditing whether the corrupted
code path had the kind of "thread-safe per-call but not as a sequence"
shape covered in Phase 5 §2, or an ABA-vulnerable pattern from Phase 3
§4, since those specific bug shapes are exactly the ones most likely to
evade casual scale-based testing while still being real, and knowing
which shape actually caused the incident should directly inform where
`jcstress` coverage gets added first.

---

## 10. What's next

**Phase 14 — Capstone: Full Engine Integration.** Every phase built and
independently tested one piece: Phase 1's `OrderIngestionBuffer`, Phase
2's `InventoryLedger`, Phase 3's ID generator and counters, Phase 4's
`StageExecutors`, Phase 5's specialized queues, Phase 6's `PipelineStage`
chain, Phase 7's async payment orchestration, Phase 8's reconciliation
job, Phase 9's virtual-thread rewrite, Phase 10's synchronizers, Phase
11's deadlock-safe transfer logic, Phase 12's resilience wrapper, and
this phase's observability layer. The capstone wires all of it into one
cohesive, runnable `OrderProcessingEngine`: config-driven tuning,
graceful startup (Phase 10's `StartupCoordinator`) and shutdown (Phase
6's poison-pill discipline, Phase 4's front-to-back ordering), the live
dashboard running the whole time, and a final load test proving the
fully assembled system's throughput and latency hold up under the same
kind of sustained, adversarial conditions each individual phase tested
its own piece against in isolation.

Say **"next"** when ready.
