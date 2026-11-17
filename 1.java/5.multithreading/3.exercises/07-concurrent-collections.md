# Group 7 — Concurrent Collections & Blocking Queues

**Level:** Intermediate | **Exercises:** 6 | **Prerequisites:** Groups 2–5

## What you will master
- `ConcurrentHashMap`: atomic compound ops (`compute`, `merge`, `computeIfAbsent`)
- Why `Collections.synchronizedMap` is not enough
- `CopyOnWriteArrayList`/`Set`
- `BlockingQueue` family and when to use each
- `ConcurrentLinkedQueue/Deque`
- `ConcurrentSkipListMap/Set` (sorted concurrent structures)
- Weakly consistent iterators

---

## Exercise 7.1 — Endpoint Hit Counter (`ConcurrentHashMap` atomics) ★

**Scenario:** Count hits per endpoint from 50 threads.

**Task**
1. Write the buggy version: `map.put(k, map.getOrDefault(k, 0) + 1)`; prove lost updates.
2. Fix with `merge(k, 1, Integer::sum)`; then with `compute`; then with `LongAdder` values: `map.computeIfAbsent(k, x -> new LongAdder()).increment()`.
3. Benchmark all three.
4. Demonstrate `Collections.synchronizedMap` iteration hazard (`ConcurrentModificationException` unless externally locked) vs `ConcurrentHashMap`'s weakly consistent iterator.

**Prove it:** Exact totals under load; you can explain which atomic guarantee each method gives.

---

## Exercise 7.2 — Exchange-Rate Cache & the Thundering Herd ★★★

**Scenario:** 500 threads request the rate for `USD/INR` at the same moment after cache expiry; the upstream API call costs money.

**Task**
1. Naive cache: `if (!cache.containsKey(k)) cache.put(k, fetch(k))` → count fetch calls (many).
2. `computeIfAbsent(k, this::fetch)` → exactly one fetch per key. Verify.
3. Show the dangers: (a) long-running `fetch` blocks other keys in the same bin, (b) recursive `computeIfAbsent` can deadlock / throw `IllegalStateException`.
4. Better design: cache `CompletableFuture<Rate>` so the map operation is instant and the fetch runs outside the map lock. Add TTL and removal of failed futures.

**Prove it:** Exactly one upstream call per key per TTL window, even with 500 concurrent callers; failures do not poison the cache.

---

## Exercise 7.3 — Webhook Subscriber Registry (`CopyOnWriteArrayList`) ★★

**Scenario:** Subscribers register/unregister rarely but events are dispatched thousands of times per second.

**Task**
1. Implement `EventBus` with `CopyOnWriteArrayList<Listener>`.
2. Show that a listener can unsubscribe *during* dispatch without `ConcurrentModificationException`.
3. Benchmark against `Collections.synchronizedList` + manual locking for read-heavy vs write-heavy mixes.
4. Identify the break-even point where copy-on-write becomes a liability.

**Prove it:** Table of throughput at 1%, 10%, 50% writes with a recommendation.

---

## Exercise 7.4 — Choosing a `BlockingQueue` ★★★

**Scenario:** Four different subsystems each need a different queue.

| Subsystem | Requirement | Candidate |
|---|---|---|
| Image resize jobs | Bounded, backpressure | `ArrayBlockingQueue` |
| Audit log writer | High throughput, optional bound | `LinkedBlockingQueue` |
| Support tickets | Priority ordering | `PriorityBlockingQueue` |
| Retry scheduler | Items become visible after a delay | `DelayQueue` |
| Hand-off between two threads | No buffering | `SynchronousQueue` |

**Task**
1. Build a mini working demo of each.
2. For `DelayQueue`, implement `RetryJob implements Delayed` with exponential backoff.
3. For `PriorityBlockingQueue`, show that it is unbounded and that iteration order ≠ priority order; only `take()` honours priority.
4. Compare `put/offer/add` and `take/poll/remove` semantics (blocking, timed, throwing) in a table.
5. Try `LinkedTransferQueue` and `transfer()` for guaranteed hand-off.

**Prove it:** Each demo has a test proving its defining property (bound, order, delay, hand-off).

---

## Exercise 7.5 — Work-Stealing Task Deque ★★★★

**Scenario:** Multiple workers each own a `ConcurrentLinkedDeque` of tasks; idle workers steal from the tail of others.

**Task**
1. Implement `Worker` with its own deque: push/pop from the head, thieves `pollLast()`.
2. Generate imbalanced workloads (one worker gets 90% of the tasks) and measure total time with and without stealing.
3. Handle the termination problem: how do you know that *all* queues are empty and *all* workers idle? (Use an `AtomicInteger` of outstanding tasks.)
4. Compare with `ForkJoinPool` for the same workload (preview of Group 9).

**Prove it:** With stealing, the total time approaches total_work / N.

---

## Exercise 7.6 — Order Book / Leaderboard (`ConcurrentSkipListMap`) ★★★

**Scenario:** A trading engine keeps bids sorted by price; a game keeps a real-time leaderboard.

**Task**
1. Implement the bid side of an order book using `ConcurrentSkipListMap<BigDecimal, Queue<Order>>` (reverse order).
2. Operations: add order, cancel order, get best bid, get top-10 levels, range query `subMap`.
3. Run 8 writer threads and 8 reader threads and validate the sorted invariant by snapshot checks.
4. Compare to `TreeMap` + lock (`ReadWriteLock`).

**Prove it:** No lost orders; `firstKey()` always returns the true best price at some point in time; document its weakly-consistent semantics.

---

## Group 7 Completion Checklist
- [ ] I never write `get` then `put` on a shared map
- [ ] I can explain `computeIfAbsent` pitfalls
- [ ] I can choose the right `BlockingQueue` for any scenario
- [ ] I understand weakly-consistent vs fail-fast iterators
