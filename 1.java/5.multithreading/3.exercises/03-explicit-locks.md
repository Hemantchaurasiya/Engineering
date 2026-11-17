# Group 3 — Explicit Locks (`java.util.concurrent.locks`)

**Level:** Intermediate | **Exercises:** 6 | **Prerequisites:** Groups 1–2

## What you will master
- `ReentrantLock`: `lock`, `tryLock`, timed and interruptible acquisition
- `Condition` objects (multiple wait-sets per lock)
- `ReentrantReadWriteLock` and lock downgrading
- `StampedLock` optimistic reads
- Fair vs. non-fair locks
- Lock striping and fine-grained locking

---

## Exercise 3.1 — Seat Booking with Timeouts (`tryLock`) ★★

**Scenario:** Booking a group of adjacent seats requires locking each seat. Customers should not wait forever.

**Task**
1. Model `Seat` with its own `ReentrantLock`.
2. `bookSeats(List<Seat>, Duration timeout)` acquires all locks with `tryLock(timeout)`. On failure, release everything acquired and return a failure result.
3. Always use `try { ... } finally { unlock(); }`. Deliberately forget `unlock()` in one branch and observe the effect on other threads.
4. Run 200 customers contending for overlapping seat ranges in opposite orders. Show no deadlock, even without global ordering, thanks to timeouts.

**Prove it:** No seat is double-booked; failed bookings leave no seat locked.

---

## Exercise 3.2 — Bounded Blocking Queue (`Condition`) ★★★

**Scenario:** Re-implement your Exercise 2.5 buffer using `ReentrantLock` with two conditions.

**Starter**
```java
class BoundedQueue<T> {
    private final Lock lock = new ReentrantLock();
    private final Condition notFull  = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();
    private final Object[] items; private int head, tail, count;
    // put(): while (count == items.length) notFull.await(); ... notEmpty.signal();
    // take(): while (count == 0) notEmpty.await(); ... notFull.signal();
}
```

**Task**
1. Implement `put`, `take`, and timed `offer(item, timeout)` / `poll(timeout)`.
2. Explain why `signal()` (not `signalAll()`) is now safe.
3. Benchmark against `ArrayBlockingQueue` with 4P/4C and 1M items.

**Prove it:** No lost/duplicate items; throughput within a reasonable factor of the JDK version.

---

## Exercise 3.3 — Feature-Flag Cache (`ReadWriteLock`) ★★

**Scenario:** Feature flags are read 100,000× per second and updated once a minute.

**Task**
1. Implement `FeatureFlagStore` with `get(flag)` (read lock) and `replaceAll(Map)` (write lock).
2. Benchmark: `synchronized` vs `ReentrantReadWriteLock` vs `volatile` immutable map snapshot, with 16 readers and 1 writer. Use JMH or a careful manual harness.
3. Demonstrate lock **downgrading** (write → read) and show that upgrading (read → write) deadlocks.
4. Show a writer-starvation scenario under non-fair mode and discuss.

**Prove it:** A results table with the three approaches and a one-paragraph recommendation (hint: the snapshot approach often wins here).

---

## Exercise 3.4 — Live Price Ticker (`StampedLock`) ★★★

**Scenario:** A market-data feed updates `bid`/`ask` thousands of times per second; hundreds of threads read the latest quote.

**Task**
1. Implement `Quote` with `StampedLock`: `tryOptimisticRead()` → read fields → `validate(stamp)` → fall back to `readLock()` if invalid.
2. Write a stress test that detects torn reads (bid > ask should never be observed if the writer always keeps `bid <= ask`).
3. Compare with `ReentrantReadWriteLock`.
4. Note the gotchas: `StampedLock` is not reentrant and has no `Condition`.

**Prove it:** Zero torn reads in a 60-second stress run.

---

## Exercise 3.5 — Support Ticket Queue (fair vs. non-fair) ★★

**Scenario:** Agents compete for a shared "assignment" lock. Some agents never seem to get tickets.

**Task**
1. Run 10 threads repeatedly acquiring a non-fair lock for 5 seconds; count acquisitions per thread.
2. Repeat with `new ReentrantLock(true)`.
3. Present the distribution (min / max / stddev) for both and total throughput.
4. When would you accept a fair lock's throughput cost?

**Prove it:** Data table showing the fairness/throughput trade-off on *your* machine.

---

## Exercise 3.6 — Wallet Service with Lock Striping ★★★

**Scenario:** 10 million user wallets can't each have a lock object, but one global lock is a bottleneck.

**Task**
1. Implement `StripedLock` with N (e.g. 64) `ReentrantLock`s; `lockFor(key)` = `locks[(h ^ (h >>> 16)) & (N-1)]`.
2. `transfer(a, b)` must lock two stripes: acquire in stripe-index order; handle both keys mapping to the same stripe (reentrancy).
3. Compare 1 / 16 / 64 / 1024 stripes under 32 threads.
4. Compare with `ConcurrentHashMap.compute()` for single-key updates.

**Prove it:** Invariant "sum of balances is constant" holds; chart throughput vs stripe count.

---

## Group 3 Completion Checklist
- [ ] I always unlock in `finally`
- [ ] I can pick between `synchronized`, `ReentrantLock`, `ReadWriteLock`, `StampedLock` and justify it
- [ ] I can use multiple `Condition`s on one lock
- [ ] I can build deadlock-free multi-lock code with `tryLock` or ordering
