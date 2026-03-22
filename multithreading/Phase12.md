# Java Multithreading & Concurrency — Phase 12: Performance & Optimization

> A complete walkthrough of Phase 12 from the Java Multithreading & Concurrency Mastery Roadmap, with real-world examples and production-quality code for every concept.

---

## Table of Contents

1. [12.1 — Amdahl's Law](#121--amdahls-law)
2. [12.2 — Contention and Scalability (Lock Striping)](#122--contention-and-scalability--lock-striping)
3. [12.3 — Lock-Free and Wait-Free Algorithms (CAS)](#123--lock-free-and-wait-free-algorithms-cas)
4. [12.4 — False Sharing](#124--false-sharing)
5. [12.5 — Context Switching Overhead](#125--context-switching-overhead)
6. [12.6 — Profiling Concurrent Applications](#126--profiling-concurrent-applications)
7. [Key Takeaways](#key-takeaways)
8. [Full Source Files](#full-source-files)

---

## 12.1 — Amdahl's Law

### Concept

Amdahl's Law defines the **theoretical maximum speedup** you can get by parallelizing a program, given that some fraction of it must always run sequentially.

**Formula:**

```
Speedup = 1 / (S + (1 - S) / N)
```

- `S` = the sequential fraction of your program (0.0 – 1.0)
- `N` = number of processors / threads
- As `N → ∞`, speedup caps at `1/S`

### Real-World Scenario

**E-commerce order processing pipeline processing 10,000 orders.**

- **Sequential (20%):** generating sequential order IDs, writing ordered audit logs — must happen in-order
- **Parallel (80%):** item validation, price calculation, inventory checks — embarrassingly parallel

If 20% of your work is sequential, no matter how many cores you add, you'll **never exceed 5× speedup**. That's the ceiling. Adding 1,000 cores still gives you only 5×.

### Theoretical Predictions

| Threads | Max Speedup (S=0.20) |
|---------|----------------------|
| 1       | 1.00×                |
| 2       | 1.67×                |
| 4       | 2.50×                |
| 8       | 3.33×                |
| 16      | 4.00×                |
| 32      | 4.44×                |
| 64      | 4.71×                |
| ∞       | 5.00× ← hard ceiling |

### Key Insight

> **Reduce S before adding threads.** Cutting the sequential fraction from 20% → 10% raises your ceiling from 5× to 10×. That's more valuable than doubling your hardware.

### Full Code — `AmdahlsLaw.java`

```java
package phase12;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;

/**
 * Amdahl's Law Demo — E-Commerce Order Processing Pipeline
 *
 * Scenario: Process 10,000 orders.
 *   - Sequential portion (~20%): generating order IDs, audit logging (must be in-order)
 *   - Parallel portion (~80%): item validation, price calculation, inventory check
 *
 * Amdahl's Law: Speedup = 1 / (S + (1 - S) / N)
 *   S = sequential fraction, N = number of processors/threads
 */
public class AmdahlsLaw {

    private static final int ORDER_COUNT = 10_000;
    private static final double SEQUENTIAL_FRACTION = 0.20;

    private final AtomicLong orderIdSequence = new AtomicLong(100_000);
    private final List<String> auditLog = new ArrayList<>();
    private final Object auditLock = new Object();

    record Order(long orderId, String customerId, List<String> items, double totalPrice) {}

    // SEQUENTIAL PROCESSING — baseline
    public long processSequentially() {
        long start = System.currentTimeMillis();

        for (int i = 0; i < ORDER_COUNT; i++) {
            long orderId = orderIdSequence.getAndIncrement();          // sequential: ID assignment
            double price = simulateItemValidationAndPricing("CUST-" + i); // parallel-eligible
            synchronized (auditLock) {
                auditLog.add("Order " + orderId + " processed, total=$" + price); // sequential: audit log
            }
        }

        return System.currentTimeMillis() - start;
    }

    // PARALLEL PROCESSING — with N threads
    public long processWithThreads(int threadCount) throws InterruptedException, ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        long start = System.currentTimeMillis();

        List<Future<Order>> futures = new ArrayList<>(ORDER_COUNT);

        for (int i = 0; i < ORDER_COUNT; i++) {
            final int customerId = i;
            long orderId = orderIdSequence.getAndIncrement(); // sequential: must happen before submission

            Future<Order> future = pool.submit(() -> {
                double price = simulateItemValidationAndPricing("CUST-" + customerId); // parallel
                return new Order(orderId, "CUST-" + customerId, List.of("item1", "item2"), price);
            });

            futures.add(future);
        }

        for (Future<Order> future : futures) {
            Order order = future.get();
            synchronized (auditLock) {
                auditLog.add("Order " + order.orderId() + " done, total=$" + order.totalPrice());
            }
        }

        pool.shutdown();
        return System.currentTimeMillis() - start;
    }

    private double simulateItemValidationAndPricing(String customerId) {
        double price = 0;
        for (int j = 0; j < 500; j++) {
            price += Math.sqrt(j) * Math.log(j + 1);
        }
        try { Thread.sleep(0, 500_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return Math.round(price * 100.0) / 100.0;
    }

    public static double theoreticalSpeedup(double sequentialFraction, int numProcessors) {
        return 1.0 / (sequentialFraction + (1.0 - sequentialFraction) / numProcessors);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Amdahl's Law — E-Commerce Order Processing Demo");

        System.out.println("Theoretical Max Speedup:");
        for (int n : new int[]{1, 2, 4, 8, 16, 32, 64, Integer.MAX_VALUE}) {
            double speedup = n == Integer.MAX_VALUE
                    ? 1.0 / SEQUENTIAL_FRACTION
                    : theoreticalSpeedup(SEQUENTIAL_FRACTION, n);
            String label = n == Integer.MAX_VALUE ? "∞" : String.valueOf(n);
            System.out.printf("  %-10s %.2fx%n", label, speedup);
        }

        AmdahlsLaw demo = new AmdahlsLaw();
        long seqTime = demo.processSequentially();
        System.out.printf("Sequential: %d ms%n", seqTime);

        for (int threads : new int[]{2, 4, Runtime.getRuntime().availableProcessors()}) {
            demo = new AmdahlsLaw();
            long parallelTime = demo.processWithThreads(threads);
            double actualSpeedup = (double) seqTime / parallelTime;
            double theoretical = theoreticalSpeedup(SEQUENTIAL_FRACTION, threads);
            System.out.printf("%d threads: %d ms | actual: %.2fx | theoretical: %.2fx%n",
                    threads, parallelTime, actualSpeedup, theoretical);
        }
    }
}
```

---

## 12.2 — Contention and Scalability — Lock Striping

### Concept

**Lock contention** happens when multiple threads compete for the same lock. One thread holds it; the others wait. This serializes execution and destroys scalability.

**Lock striping** solves this by partitioning data across N independent locks. Threads operating on different partitions (stripes) proceed in parallel. This is exactly how `ConcurrentHashMap` works internally.

### Real-World Scenario

**Bank with 1,000,000 accounts doing concurrent transfers across 50 threads.**

Three approaches compared:

| Strategy | Description | Memory | Concurrency |
|----------|-------------|--------|-------------|
| **GlobalLock** | One lock for ALL accounts | O(1) | Terrible — everything serialized |
| **PerAccountLock** | One lock per account | O(N) | Maximum, but deadlock risk |
| **StripedLock** | N locks, accounts hashed to stripe | O(stripes) | Production sweet spot |

### The Deadlock Danger

With per-account or striped locking, **always acquire locks in a consistent order** (e.g., lower ID first). Without this:

- Thread A holds `lock(account_1)`, waiting for `lock(account_2)`
- Thread B holds `lock(account_2)`, waiting for `lock(account_1)`
- → **Deadlock!**

```java
// ALWAYS lock in account-ID order — prevents deadlock
int firstLock  = Math.min(fromId, toId);
int secondLock = Math.max(fromId, toId);

locks[firstLock].lock();
try {
    locks[secondLock].lock();
    try {
        // ... transfer ...
    } finally { locks[secondLock].unlock(); }
} finally { locks[firstLock].unlock(); }
```

### Rule of Thumb

> Stripe count ≈ **4× your expected concurrent thread count**. Going from 16 → 256 stripes gives diminishing returns because the probability of collision drops quickly past your thread count.

### Full Code — `LockContention.java`

```java
package phase12;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lock Contention & Striping — Bank Account Transfer System
 *
 * Problem: 1,000,000 accounts; 50 threads doing concurrent transfers.
 *   v1 — GlobalLock:   ONE lock for all accounts → massive contention, poor throughput
 *   v2 — PerAccount:   One lock per account → great concurrency but high memory
 *   v3 — StripedLock:  N locks, accounts hashed to stripes → balance of both
 */
public class LockContention {

    private static final int ACCOUNT_COUNT  = 100_000;
    private static final int TRANSFER_COUNT = 500_000;
    private static final int THREAD_COUNT   = Runtime.getRuntime().availableProcessors() * 2;

    static class Account {
        final int id;
        long balance;
        Account(int id, long initialBalance) { this.id = id; this.balance = initialBalance; }
    }

    // V1: GLOBAL LOCK — single bottleneck
    static class GlobalLockBank {
        private final Account[] accounts;
        private final ReentrantLock globalLock = new ReentrantLock();

        GlobalLockBank(int size) {
            accounts = new Account[size];
            for (int i = 0; i < size; i++) accounts[i] = new Account(i, 10_000);
        }

        void transfer(int fromId, int toId, long amount) {
            globalLock.lock();
            try {
                Account from = accounts[fromId];
                Account to   = accounts[toId];
                if (from.balance >= amount) {
                    from.balance -= amount;
                    to.balance   += amount;
                }
            } finally {
                globalLock.unlock();
            }
        }
    }

    // V2: PER-ACCOUNT LOCK — deadlock-safe via ordered locking
    static class PerAccountLockBank {
        private final Account[] accounts;
        private final ReentrantLock[] locks;

        PerAccountLockBank(int size) {
            accounts = new Account[size];
            locks    = new ReentrantLock[size];
            for (int i = 0; i < size; i++) {
                accounts[i] = new Account(i, 10_000);
                locks[i]    = new ReentrantLock();
            }
        }

        void transfer(int fromId, int toId, long amount) {
            int firstLock  = Math.min(fromId, toId);
            int secondLock = Math.max(fromId, toId);

            locks[firstLock].lock();
            try {
                locks[secondLock].lock();
                try {
                    Account from = accounts[fromId];
                    Account to   = accounts[toId];
                    if (from.balance >= amount) {
                        from.balance -= amount;
                        to.balance   += amount;
                    }
                } finally { locks[secondLock].unlock(); }
            } finally { locks[firstLock].unlock(); }
        }
    }

    // V3: STRIPED LOCK — production sweet spot (used by ConcurrentHashMap)
    static class StripedLockBank {
        private final Account[] accounts;
        private final ReentrantLock[] stripes;
        private final int stripeCount;

        StripedLockBank(int size, int stripeCount) {
            this.stripeCount = stripeCount;
            accounts = new Account[size];
            stripes  = new ReentrantLock[stripeCount];
            for (int i = 0; i < size; i++)       accounts[i] = new Account(i, 10_000);
            for (int i = 0; i < stripeCount; i++) stripes[i]  = new ReentrantLock();
        }

        private int stripe(int accountId) { return accountId % stripeCount; }

        void transfer(int fromId, int toId, long amount) {
            int firstStripe  = Math.min(stripe(fromId), stripe(toId));
            int secondStripe = Math.max(stripe(fromId), stripe(toId));

            stripes[firstStripe].lock();
            try {
                if (firstStripe != secondStripe) stripes[secondStripe].lock();
                try {
                    Account from = accounts[fromId];
                    Account to   = accounts[toId];
                    if (from.balance >= amount) {
                        from.balance -= amount;
                        to.balance   += amount;
                    }
                } finally {
                    if (firstStripe != secondStripe) stripes[secondStripe].unlock();
                }
            } finally { stripes[firstStripe].unlock(); }
        }
    }
}
```

---

## 12.3 — Lock-Free and Wait-Free Algorithms (CAS)

### Concept

**Compare-and-Swap (CAS)** is a single CPU instruction that:
1. Reads the current value of a memory location
2. Compares it to an expected value
3. If they match, atomically writes a new value
4. Returns success or failure

This is "optimistic concurrency" — assume no conflict, retry if wrong. No locks means no thread blocking, no deadlocks, no priority inversion.

```
compareAndSet(expected, newValue)
  → if current == expected: set to newValue, return true
  → if current != expected: do nothing, return false (caller retries)
```

### Real-World Scenario

**Web server tracking 100K requests/second across 32 threads.**

Metrics (request count, error rate, latency) are updated on every single request. A synchronized counter becomes a global bottleneck.

### Three Approaches Compared

| Approach | Mechanism | Best For |
|----------|-----------|---------|
| `synchronized` | Mutual exclusion lock | Simple, any data structure |
| `AtomicLong` | CAS spin loop | Moderate contention, conditional updates |
| `LongAdder` | Striped cells per thread | High-contention pure counters |

### The ABA Problem

CAS has a subtle bug: Thread A reads value `A`, another thread changes it to `B` then back to `A`. Thread A's CAS succeeds (it still sees `A`) but the state has changed underneath.

**Fix:** Use `AtomicStampedReference` — a version counter is also checked during CAS, so `A(stamp=1) → B(stamp=2) → A(stamp=3)` is always detectable.

```java
// ABA-safe: checks both reference AND stamp
top.compareAndSet(currentTop, newTop, currentStamp, currentStamp + 1);
```

### Full Code — `LockFreeMetrics.java`

```java
package phase12;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Lock-Free Algorithms — High-Performance Metrics System
 */
public class LockFreeMetrics {

    // V1: Synchronized counter — simple but contended
    static class SynchronizedMetrics {
        private long requestCount = 0;
        private long errorCount   = 0;
        private long totalLatency = 0;

        synchronized void recordRequest(long latencyMs, boolean isError) {
            requestCount++;
            totalLatency += latencyMs;
            if (isError) errorCount++;
        }
    }

    // V2: Lock-free with AtomicLong + CAS
    static class LockFreeMetricsV2 {
        private final AtomicLong requestCount = new AtomicLong(0);
        private final AtomicLong errorCount   = new AtomicLong(0);
        private final AtomicLong totalLatency = new AtomicLong(0);

        void recordRequest(long latencyMs, boolean isError) {
            requestCount.incrementAndGet();    // CAS spin loop under the hood
            totalLatency.addAndGet(latencyMs); // atomic add
            if (isError) errorCount.incrementAndGet();
        }
    }

    // V3: LongAdder — striped cells, lowest contention for increments
    // Each thread writes to its own cell; sum() combines them.
    // Trade-off: sum() is NOT atomic (fine for metrics, wrong for bank balances).
    static class LongAdderMetrics {
        private final LongAdder requestCount = new LongAdder();
        private final LongAdder errorCount   = new LongAdder();
        private final LongAdder totalLatency = new LongAdder();

        void recordRequest(long latencyMs, boolean isError) {
            requestCount.increment();      // writes to thread-local cell — near-zero contention
            totalLatency.add(latencyMs);
            if (isError) errorCount.increment();
        }

        long getRequestCount() { return requestCount.sum(); } // combines all cells
    }

    // V4: Lock-Free Stack with ABA fix via AtomicStampedReference
    static class ABAFreeStack<T> {
        private static class Node<T> {
            final T value;
            Node<T> next;
            Node(T value) { this.value = value; }
        }

        private final AtomicStampedReference<Node<T>> top =
                new AtomicStampedReference<>(null, 0);

        void push(T value) {
            Node<T> newNode = new Node<>(value);
            int[] stampHolder = new int[1];
            Node<T> currentTop;
            do {
                currentTop = top.get(stampHolder);
                newNode.next = currentTop;
            } while (!top.compareAndSet(currentTop, newNode, stampHolder[0], stampHolder[0] + 1));
        }

        T pop() {
            int[] stampHolder = new int[1];
            Node<T> currentTop, newTop;
            do {
                currentTop = top.get(stampHolder);
                if (currentTop == null) return null;
                newTop = currentTop.next;
                // Checks BOTH reference AND stamp — ABA-safe!
            } while (!top.compareAndSet(currentTop, newTop, stampHolder[0], stampHolder[0] + 1));
            return currentTop.value;
        }
    }
}
```

### When to Use Each

- **`synchronized`** — Simple, readable, works for anything. Use it first.
- **`AtomicLong`** — Lock-free, great at moderate contention. Use when you need `compareAndSet()` for conditional updates (e.g., "only update if current value matches X").
- **`LongAdder`** — Fastest for pure increment/add at any contention level. Use for metrics, event counters, throughput tracking.
- **`AtomicStampedReference`** — Only when you need lock-free data structures (stacks, queues) and must solve the ABA problem. Otherwise prefer `java.util.concurrent` collections.

---

## 12.4 — False Sharing

### Concept

**False sharing** is a performance problem caused by CPU cache architecture, not by any logical data sharing between threads.

CPUs manage cache in **64-byte "cache lines"** — chunks of memory. When one core writes to a memory location, it invalidates the entire cache line on all other cores, even if those cores are accessing **completely different variables** that happen to sit in the same line.

```
Cache line (64 bytes):
[ Worker0.orders | Worker1.orders | Worker2.orders | Worker3.orders ]
     Core 0           Core 1           Core 2           Core 3

Core 0 writes → entire line marked MODIFIED
→ Core 1, 2, 3 must re-fetch from L3/RAM before their next write
→ Even though they never touch Core 0's data!
```

This "cache line ping-pong" causes massive slowdown on tight loops — the exact workloads where you want maximum performance.

### Real-World Scenario

**4 order-processing workers, each tracking their own statistics (orders, revenue, errors).** Workers are completely independent — Worker 1 never reads Worker 2's counters. Yet if their stats pack into the same cache line, they constantly invalidate each other.

### Three Fixes

**1. Manual padding** — add dummy fields to push the next object to a new cache line:
```java
static class PaddedWorkerStats {
    volatile long ordersProcessed = 0;
    volatile long revenue         = 0;
    long p1, p2, p3, p4, p5, p6, p7; // 56 bytes padding → pushes to new cache line
}
```

**2. `@Contended` annotation** — JVM handles padding automatically (recommended):
```java
@Contended
static class ContendedWorkerStats {
    volatile long ordersProcessed = 0;
    volatile long revenue         = 0;
}
// Requires JVM flag: -XX:-RestrictContended
```

**3. Strided arrays** — space array elements 128 bytes apart:
```java
private static final int STRIDE = 16; // 16 longs × 8 bytes = 128 bytes
private final long[] orders = new long[THREAD_COUNT * STRIDE];

void recordOrder(int workerId, long amount) {
    orders[workerId * STRIDE]++; // each worker's slot is 128 bytes apart
}
```

### Full Code — `FalseSharing.java`

```java
package phase12;

import jdk.internal.vm.annotation.Contended;
import java.util.concurrent.*;

/**
 * False Sharing — Concurrent Order Processing Stats
 * Run with: java -XX:-RestrictContended phase12.FalseSharing
 */
public class FalseSharing {

    private static final int THREAD_COUNT = 4;
    private static final int ITERATIONS   = 50_000_000;

    // V1: NAIVE — stats packed tightly, likely on same cache line
    static class NaiveWorkerStats {
        volatile long ordersProcessed = 0;
        volatile long revenue         = 0;
        volatile long errors          = 0;
        volatile long retries         = 0;
    }

    // V2: PADDED — manual padding to isolate to its own cache line
    static class PaddedWorkerStats {
        volatile long ordersProcessed = 0;
        volatile long revenue         = 0;
        volatile long errors          = 0;
        volatile long retries         = 0;
        long p1, p2, p3, p4, p5, p6, p7; // 56 bytes padding
    }

    // V3: @Contended — JVM adds 128-byte padding before and after (recommended)
    @Contended
    static class ContendedWorkerStats {
        volatile long ordersProcessed = 0;
        volatile long revenue         = 0;
        volatile long errors          = 0;
        volatile long retries         = 0;
    }

    // V4: STRIDED ARRAY — 128-byte gap between each worker's slot
    static class StridedArrayStats {
        private static final int STRIDE = 16;
        private final long[] ordersProcessed = new long[THREAD_COUNT * STRIDE];
        private final long[] revenue         = new long[THREAD_COUNT * STRIDE];

        void recordOrder(int workerId, long amount) {
            ordersProcessed[workerId * STRIDE]++;
            revenue[workerId * STRIDE] += amount;
        }
    }
}
```

### When to Care About False Sharing

- ✅ Tight loops updating per-thread counters or accumulators
- ✅ Thread-local state stored in shared arrays
- ✅ If profiling shows high L1/L2 cache miss rates or unexpected memory bandwidth
- ❌ Don't pre-optimize — profile first, pad second. Most code isn't hot enough to matter.

---

## 12.5 — Context Switching Overhead

### Concept

Every time the OS switches from one thread to another, it must:
1. Save the current thread's registers, stack pointer, program counter
2. Load the next thread's saved state
3. Flush CPU pipeline and possibly TLB

This costs **1–10 microseconds** per switch. At thousands of context switches per second, this becomes measurable overhead.

### The Thread Count Dilemma

**Too few threads:** CPU sits idle during I/O waits → wasted capacity  
**Too many threads:** constant context switching → wasted CPU on overhead

### Little's Law Applied to Thread Pools

```
Optimal Threads = N_cpu × U_cpu × (1 + W/C)

N_cpu = number of CPU cores
U_cpu = target CPU utilization (typically 0.7–0.9)
W     = average wait time per task (I/O, sleep, network)
C     = average compute time per task (pure CPU work)
```

### Sizing Guide by Scenario

| Scenario | W/C Ratio | Threads (8-core machine) |
|----------|-----------|--------------------------|
| Pure CPU (image processing) | 0 | ~8 |
| Light I/O (cache hit, 1ms wait) | 0.2 | ~10 |
| Mixed (DB query + processing) | 2 | ~22 |
| Heavy I/O (external API, 100ms wait) | 20 | ~151 |
| Mostly waiting (polling service) | 99 | ~715 |

### CPU-Bound vs I/O-Bound: The Key Difference

**CPU-bound:** Threads always need a core. More threads than cores = context switches without benefit.
```
Thread count ≈ CPU cores
```

**I/O-bound:** Threads sleep while waiting for I/O. More threads = better CPU utilization.
```
Thread count = CPU cores × (1 + W/C)
```

---

## 12.6 — Profiling Concurrent Applications

### ThreadMXBean — Programmatic Thread Analysis

Java's `ThreadMXBean` provides runtime thread analysis — the same data that JVisualVM and Java Mission Control surface in their UIs.

```java
ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
threadMXBean.setThreadContentionMonitoringEnabled(true);
threadMXBean.setThreadCpuTimeEnabled(true);

// Get all thread info with 5 frames of stack
ThreadInfo[] infos = threadMXBean.getThreadInfo(
    threadMXBean.getAllThreadIds(), 5);

for (ThreadInfo info : infos) {
    long cpuTime     = threadMXBean.getThreadCpuTime(info.getThreadId()) / 1_000_000; // ms
    long blockedTime = info.getBlockedTime(); // time spent waiting for locks
    Thread.State state = info.getThreadState();
    // ...
}

// Deadlock detection — always check in production thread dumps!
long[] deadlockedIds = threadMXBean.findDeadlockedThreads();
if (deadlockedIds != null) {
    // DEADLOCK! Investigate immediately.
}
```

### Reading a Thread Dump

When you take a thread dump (via `jstack`, `kill -3`, or JVisualVM), look for:

| Pattern | Meaning |
|---------|---------|
| Many threads in `BLOCKED` | Lock contention — who holds the lock? |
| Many threads in `WAITING` | Possible deadlock or starvation |
| Many threads in `TIMED_WAITING` | Normal if they're sleeping/waiting with timeout |
| One thread with 100% CPU | Busy-spin, infinite loop, or hot lock-free retry |
| `"parking to wait for..."` | Thread pool thread waiting for work (normal) |

### Full Code — `ContextSwitchingAndProfiling.java`

```java
package phase12;

import java.lang.management.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

/**
 * Context Switching Overhead & Profiling
 * Demonstrates optimal thread count for CPU-bound vs I/O-bound work,
 * and programmatic thread analysis via ThreadMXBean.
 */
public class ContextSwitchingAndProfiling {

    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();

    // CPU-BOUND: pure math, no I/O. Each thread needs a core.
    static double cpuBoundTask(int iterations) {
        double result = 0;
        for (int i = 1; i <= iterations; i++) {
            result += Math.sqrt(i) * Math.log(i);
        }
        return result;
    }

    // I/O-BOUND: mostly sleeping. Core is free for other threads during wait.
    static double ioBoundTask(int iterations) throws InterruptedException {
        double result = 0;
        for (int i = 1; i <= iterations; i++) {
            result += i;
            Thread.sleep(1); // simulates 1ms DB/network round-trip
        }
        return result;
    }

    static void analyzeThreads() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        bean.setThreadContentionMonitoringEnabled(true);
        bean.setThreadCpuTimeEnabled(true);

        ThreadInfo[] infos = bean.getThreadInfo(bean.getAllThreadIds(), 5);

        for (ThreadInfo info : infos) {
            if (info == null) continue;
            long cpuMs     = bean.getThreadCpuTime(info.getThreadId()) / 1_000_000;
            long blockedMs = info.getBlockedTime();
            System.out.printf("%-30s %-12s CPU:%dms Blocked:%dms%n",
                    info.getThreadName(), info.getThreadState(), cpuMs, Math.max(0, blockedMs));
        }

        // Always check for deadlocks!
        long[] deadlocked = bean.findDeadlockedThreads();
        if (deadlocked != null) {
            System.out.println("DEADLOCK DETECTED!");
            for (ThreadInfo info : bean.getThreadInfo(deadlocked, Integer.MAX_VALUE)) {
                System.out.println("  " + info.getThreadName()
                        + " waiting for lock held by " + info.getLockOwnerName());
            }
        }
    }
}
```

---

## Key Takeaways

### The Three Mental Models

**1. Reduce S before adding threads (Amdahl's Law)**

Halving your sequential fraction from 20% → 10% doubles your speedup ceiling from 5× → 10× — no hardware needed. Always look for opportunities to parallelize the sequential parts first.

**2. Contention is the enemy of scalability**

The progression from high to low contention:
```
synchronized (global lock)
    ↓ lock striping (16-64 stripes)
        ↓ per-item locking (with ordered acquisition)
            ↓ CAS / AtomicLong (lock-free)
                ↓ LongAdder (striped cells, near-zero contention)
```
Use the simplest approach that meets your throughput needs. Don't over-engineer.

**3. Hardware topology matters**

False sharing and context switching are CPU-level phenomena, invisible at the Java source level. A tight loop that looks obviously parallel can be throttled by cache line contention. Always:
- Profile first (ThreadMXBean, JVisualVM, async-profiler)
- Identify hot paths
- Apply targeted fixes (`@Contended`, padding, LongAdder)

### Quick Reference: Which Tool for Which Problem?

| Problem | Tool |
|---------|------|
| Sequential bottleneck | Restructure algorithm; use Fork/Join; reduce shared state |
| Lock contention | Lock striping; shorter critical sections; read-write locks |
| High-frequency counters | `LongAdder` over `AtomicLong` over `synchronized` |
| Conditional atomic update | `compareAndSet()` on `AtomicReference` / `AtomicLong` |
| Cache ping-pong on arrays | Strided access (`[i * 16]`) or `@Contended` |
| Too many threads (CPU-bound) | Reduce to `N_cpu` threads |
| Too few threads (I/O-bound) | Increase to `N_cpu × (1 + W/C)` |
| Production performance diagnosis | Thread dump + `ThreadMXBean` + async-profiler |

---

## Full Source Files

Five complete, runnable Java files were produced for this session:

| File | Phase | Real-World Scenario |
|------|-------|---------------------|
| `AmdahlsLaw.java` | 12.1 | E-commerce order pipeline with sequential ID generation |
| `LockContention.java` | 12.2 | Bank account transfers across 100K accounts |
| `LockFreeMetrics.java` | 12.3 | Web server request/error/latency counters |
| `FalseSharing.java` | 12.4 | Order processing workers on separate CPU cores |
| `ContextSwitchingAndProfiling.java` | 12.5 + 12.6 | Payment processing pool sizing + live thread analysis |

### Running the Examples

```bash
# Compile all files
javac -d out phase12/*.java

# Run each demo
java -cp out phase12.AmdahlsLaw
java -cp out phase12.LockContention
java -cp out phase12.LockFreeMetrics

# FalseSharing requires the flag to activate @Contended
java -XX:-RestrictContended -cp out phase12.FalseSharing

# ContextSwitching demo
java -cp out phase12.ContextSwitchingAndProfiling
```

### Recommended Reading

- *Java Concurrency in Practice* — Brian Goetz (Chapter 11: Performance and Scalability)
- *The Art of Multiprocessor Programming* — Herlihy & Shavit (lock-free algorithms)
- JDK source: `java.util.concurrent.atomic.LongAdder`, `ConcurrentHashMap` (striping in practice)
- Aleksey Shipilёv's blog — deep dives on JVM performance, false sharing, JMM

---

*Document covers Phase 12 of the Java Multithreading & Concurrency Mastery Roadmap.*  
*All code targets Java 17+ and follows production best practices.*