# Phase 1 — Java Memory Model & Thread Fundamentals

**Project:** Concurrent Order Processing Engine
**Stack:** Java 21
**Component built this phase:** `OrderIngestionBuffer` (edge-gateway intake buffer)
**Files:** `src/main/java/com/orderengine/phase1/`

---

## 1. Why start here instead of with `ExecutorService`

Every later phase — thread pools, concurrent collections, `CompletableFuture`,
virtual threads — is built *on top of* the Java Memory Model (JMM). If you
learn those tools without the JMM underneath, you'll be able to write code
that works on your laptop and fails intermittently in production under
different core counts, JIT tiers, and CPU cache topologies. The bugs in this
phase are that category of bug: legal-by-spec, timing-dependent, and
notoriously hard to reproduce in a debugger (because attaching a debugger or
adding a `println` often introduces exactly the memory barrier that hides
the bug).

So Phase 1's job is narrow and foundational: understand *why* multithreaded
code needs synchronization at all, beyond "avoiding race conditions on
shared variables" as a vague slogan.

---

## 2. The core problem: memory is not what you think it is

In a single-threaded program, you can reason about code top-to-bottom: each
statement finishes before the next begins, and every read sees the latest
write. That's **program order**, and it's the illusion the JVM works hard to
preserve for a *single* thread.

It makes **no such promise across threads**, for two independent reasons:

### 2.1 Compiler and CPU reordering
The JIT compiler and the CPU are both allowed to reorder, cache, and
eliminate reads/writes, as long as the *single-threaded* observable
behavior doesn't change. If thread A writes `x = 1; y = 2;`, thread B might
observe `y = 2` before `x = 1` — there was never a promise about the order
another thread sees these in, only that A itself sees its own writes in
order.

### 2.2 CPU cache / core-local visibility
Each core can cache a variable's value. A write on core 1 doesn't
automatically flush to main memory or invalidate core 2's cache line. Core 2
can spin on a stale cached value indefinitely — not because of a scheduling
quirk, but because nothing ever told it to look again.

The JMM (JLS Chapter 17) is the specification that tells you exactly which
actions are *guaranteed* to be visible across threads, and in what order.
Everything else is legal to reorder or cache. This is why "it worked in my
testing" is close to meaningless for concurrent code — you tested one
particular reordering the JIT/CPU happened to choose that run.

---

## 3. Two separate problems people conflate: visibility vs. atomicity

This is the single most common source of confusion in Java concurrency, and
it's baked directly into the bugs you're about to build and fix.

| | **Visibility** | **Atomicity** |
|---|---|---|
| Question it answers | "Will thread B ever see thread A's write?" | "Can this operation be interrupted halfway by another thread?" |
| Example failure | B spins forever on a stale `false` | Two threads both do `count++`, one increment is lost |
| Fixed by `volatile`? | Yes | **No** |
| Fixed by `synchronized`/locks? | Yes | Yes |
| Fixed by `Atomic*` classes? | Yes (they're also volatile-backed) | Yes, for single operations on that variable |

`volatile` gives you visibility and ordering guarantees (see §4) for reads
and writes of *that single field*. It does **not** make compound actions
(`count++`, `if (x == null) x = new Foo()`, "check-then-act" of any kind)
atomic. `count++` on a `volatile int` is still three separate operations
(read, add, write) and two threads can interleave them and lose an update —
this is exactly Bug #2 in the code below.

---

## 4. `happens-before`: the actual contract

The JMM defines correctness via a partial order called **happens-before**.
If action X happens-before action Y, then X's effects (all of them, not
just the specific variable) are guaranteed visible to Y. If there is no
happens-before edge between two actions on different threads, the JVM is
free to let them appear in any order to each other, or to hide one entirely.

The happens-before rules that matter most in day-to-day code:

1. **Program order rule**: within one thread, each action happens-before
   every subsequent action in that thread (this is the single-threaded
   illusion, and it's real *within* a thread).
2. **Monitor lock rule**: an unlock on a monitor happens-before every
   subsequent lock on that *same* monitor. This is why `synchronized`
   blocks give both mutual exclusion *and* visibility — people often only
   remember the mutual exclusion half.
3. **Volatile variable rule**: a write to a `volatile` field happens-before
   every subsequent read of that *same* field.
4. **Thread start rule**: `Thread.start()` happens-before any action in the
   started thread.
5. **Thread termination rule**: every action in a thread happens-before
   another thread successfully returns from `Thread.join()` on it.
6. **Transitivity**: if X happens-before Y and Y happens-before Z, then X
   happens-before Z. This is what lets you publish a whole graph of objects
   safely through a single volatile write or lock — not just the one field
   you touched last.

Notice what's *not* on this list: plain field reads/writes have no
happens-before relationship with each other across threads. That absence is
the bug.

---

## 5. Safe publication and final fields

A related, often-missed guarantee: if an object's fields are all `final`
and the constructor doesn't leak `this` before it completes, then *any*
thread that gets a reference to that object after construction is
guaranteed to see the fully-initialized final fields — no synchronization
required. This is why `Order` in this project is a `record` with all-final
components: once you hand an `Order` to another thread through any safe
mechanism (a `volatile` field, a `synchronized` block, a `BlockingQueue`,
thread creation), the receiving thread is guaranteed to see fully
constructed field values, never a half-built object. This guarantee is
narrower than people assume — it covers the final fields themselves, not
arbitrary mutable objects those fields might point to.

---

## 6. Thread lifecycle and creation APIs

Six states (`Thread.State`): `NEW → RUNNABLE → {BLOCKED, WAITING,
TIMED_WAITING} → TERMINATED`. `RUNNABLE` covers both "actually running on a
core" and "ready and waiting for the scheduler" — Java doesn't distinguish
them at the API level, which matters when you're reading a thread dump and
see 40 threads as `RUNNABLE` on an 8-core box.

Creation APIs, and when each is the right call:

- **`Runnable`** — `void run()`, no checked exceptions, no return value.
  Use for fire-and-forget work.
- **`Callable<V>`** — `V call() throws Exception`. Use when the task
  produces a result or can throw a checked exception — this is what you
  submit to an `ExecutorService` to get back a `Future<V>` (Phase 4).
- **Extending `Thread` directly** — almost never the right call in
  production code. It ties task logic to thread-management logic and
  prevents pooling. We won't use this pattern anywhere in this project past
  Phase 1's raw demos.
- **Virtual threads (`Thread.ofVirtual()`)** — Phase 9. Different scheduler,
  same JMM rules — happens-before is unchanged.

---

## 7. The bugs, built and fixed

### 7.1 `BrokenOrderIngestionBuffer` — two independent bugs

```java
private boolean running = true;   // BUG 1: no happens-before edge
private int count = 0;            // BUG 2: non-atomic compound update
```

**Bug 1 — visibility.** The drain thread spins on `while (running) {}`. The
control thread calls `shutdown()` which does `running = false`. There is no
happens-before edge between that write and the drain thread's reads (no
volatile, no lock, no other synchronizing action). The JIT is legally
permitted to hoist `running` into a register once it proves — from its
single-threaded vantage point — that nothing in the loop body can change
it, and never re-read main memory again. Result: the loop can spin forever,
even though `shutdown()` returned successfully long ago on the other
thread.

**Bug 2 — atomicity.** `count++` inside `offer()` is read-modify-write.
With 8 threads each doing 100,000 increments, you'd expect a final count of
800,000. Under real contention you'll observe fewer — some increments are
silently overwritten because two threads read the same value before either
writes back.

### 7.2 The fix: `OrderIngestionBuffer`

```java
private volatile boolean running = true;              // fix 1
private final AtomicInteger count = new AtomicInteger(0); // fix 2
```

- `volatile` fixes bug 1 by the volatile-variable happens-before rule (§4.3):
  every read of `running` after the write is guaranteed to observe it.
- `AtomicInteger.incrementAndGet()` fixes bug 2 because it performs the
  read-modify-write as a single atomic hardware-level compare-and-swap
  (mechanics covered in depth in Phase 3) — no other thread can observe or
  interleave a partial state.
- Note what would **not** have worked: making `count` merely `volatile`
  (`private volatile int count`). That fixes visibility of each read/write
  but not the atomicity of the three-step increment — the lost-update bug
  would still occur. This is the single most common wrong "fix" people
  reach for.

### 7.3 Running the demos yourself

This sandbox only has a JRE, not a JDK, so I verified the logic by hand
rather than by executing it. Compile and run these locally with JDK 21:

```bash
cd phase1-jmm-thread-fundamentals/src/main/java
javac com/orderengine/phase1/*.java -d out
java -cp out com.orderengine.phase1.VisibilityBugDemo
java -cp out com.orderengine.phase1.FixedBufferDemo
```

Notes on reproducing bug 1 reliably:
- Run it as a normal `java` invocation (not stepped in a debugger) — you
  need the C2 JIT to actually kick in, which takes a short warm-up period;
  the demo sleeps 300ms before calling `shutdown()` for this reason.
- Don't add a `println` inside the spin loop itself — I/O calls and many
  other JDK methods contain internal synchronization that would
  incidentally introduce a happens-before edge and mask the bug. The demo
  only prints from a counter checkpoint every 50M iterations, which is far
  cheap enough not to interfere, and is there only so you can see the
  thread is alive and spinning, not deadlocked.
- Reproduction is timing/hardware-dependent by nature — that's the whole
  point (§2). On some machines/JVM builds you may need to run it a few
  times, or it may reproduce every time. Either outcome is teaching you
  something real: "usually works" is not a correctness argument for
  concurrent code.

---

## 8. Common production bugs from this phase's concepts

1. **Double-checked locking without `volatile`** — the classic broken
   singleton:
   ```java
   if (instance == null) {
       synchronized (lock) {
           if (instance == null) {
               instance = new Foo(); // NOT safe without volatile
           }
       }
   }
   ```
   Without `volatile Foo instance`, another thread can see a non-null
   reference to a *partially constructed* `Foo` due to reordering of the
   constructor's writes relative to the reference assignment. Fix: `private
   static volatile Foo instance;`

2. **Flags used for cross-thread shutdown signaling without `volatile` or
   `AtomicBoolean`** — exactly bug 1 above, extremely common in hand-rolled
   worker loops.

3. **Assuming `volatile` makes multi-step operations safe** — exactly bug 2
   above. If you see `volatile` on a counter that's incremented from
   multiple threads, that's a code-review red flag, not a green one.

4. **Leaking `this` from a constructor** (e.g., starting a thread or
   registering a listener from inside the constructor before it finishes)
   — breaks the safe-publication guarantee from §5, because the object can
   be observed by another thread before construction completes.

---

## 9. Interview Q&A (junior → staff)

**Q (junior): What does `volatile` actually do?**
A: It guarantees visibility and ordering for reads/writes of that field
across threads (establishes a happens-before edge per JLS 17.4.5), and
prevents the compiler/CPU from reordering other memory operations around
accesses to it. It does **not** make compound operations on that field
atomic.

**Q (junior/mid): Why might `while (!done) {}` never terminate even though
another thread definitely set `done = true`?**
A: Without a happens-before edge (no `volatile`, no lock, no other
synchronizing action), the JIT can legally cache the read and never revisit
main memory, because from a single-threaded viewpoint nothing in the loop
could change `done`. Fix: make `done` volatile, or guard both the read and
write with the same lock, or use `AtomicBoolean`.

**Q (mid): Does `synchronized` give you visibility, or just mutual
exclusion?**
A: Both. The monitor lock happens-before rule means an unlock happens-before
every subsequent lock on that same monitor — so it provides the same
visibility guarantee as volatile, for everything touched inside the
critical section, not just one field. This is frequently forgotten; people
reach for `volatile` "on top of" `synchronized` unnecessarily.

**Q (mid/senior): Why isn't `count++` on a volatile int thread-safe?**
A: `++` is not a single JVM-level atomic operation — it's a read, then an
add, then a write. `volatile` guarantees each of those three sub-operations
individually sees/publishes correctly, but between the read and the write
another thread can interleave its own read-modify-write on the same stale
value, and one increment is lost. Atomicity of the *whole* operation, not
visibility of its parts, is what's missing. Fix with `AtomicInteger`/CAS or
a lock around the whole increment.

**Q (senior/staff): You inherit a service where a shutdown flag bug like
this reproduces in production only under high load, never in staging. How
do you approach diagnosing it without being able to reliably repro
locally?**
A: Points worth hitting: (1) don't try to repro by adding logging/debugger
breakpoints first — that can mask exactly this class of bug by introducing
synchronizing side effects; (2) take thread dumps (`jstack` / `jcmd
Thread.print`) from the stuck production instance and look for threads
`RUNNABLE` in a tight loop with no progress rather than `BLOCKED`/`WAITING`
— that pattern points at a visibility bug rather than a deadlock; (3) audit
every field read inside worker loops for volatile/lock coverage rather than
guessing; (4) staging not reproducing it is expected — lower core counts,
different JIT tiering thresholds, and different cache topology change
whether hoisting is observed, which is exactly why "can't repro in staging"
is not evidence of correctness for this bug class; (5) tools like `jcstress`
(Phase 13) exist specifically because unit tests essentially never catch
this class of bug.

**Q (staff): What does the JMM guarantee about final fields, and why does
that matter for immutable domain objects passed between threads?**
A: If every field of an object is `final` and the constructor doesn't allow
`this` to escape before completing, then any thread obtaining a reference
to that object after construction is guaranteed to see fully-initialized
final field values — no external synchronization needed for that
guarantee. This is why immutable value objects (like `Order` here) are
"free" to hand across threads through any publication mechanism, and it's
part of why favoring immutability is a first-class concurrency strategy,
not just a style preference — Phase 2 onward, we'll keep leaning on this
rather than reaching for locks by default.

---

## 10. What's next

**Phase 2 — Synchronization & Locks.** We'll build `InventoryLedger`, a
component that needs both exclusive writes (reserve/release stock) and
frequent concurrent reads (check availability). You'll see why
`synchronized` is sometimes the wrong tool even once correctness is
established, and implement the fix with `ReentrantReadWriteLock` and then
`StampedLock`'s optimistic-read mode — plus the internals of how
`synchronized` itself is implemented (biased → lightweight → heavyweight
locking) so "just use synchronized" stops being a black box.

Say **"next"** when ready.
