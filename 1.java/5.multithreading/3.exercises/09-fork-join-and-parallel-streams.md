# Group 9 — Fork/Join Framework & Parallel Streams

**Level:** Intermediate → Advanced | **Exercises:** 5 | **Prerequisites:** Groups 5, 7

## What you will master
- Divide-and-conquer with `RecursiveTask` / `RecursiveAction`
- Work-stealing and `ForkJoinPool` parallelism
- Threshold tuning
- Parallel stream correctness and performance pitfalls
- Running blocking work safely

---

## Exercise 9.1 — Parallel Transaction Aggregation (`RecursiveTask`) ★★★

**Scenario:** Sum and group 50 million transactions by merchant category.

**Starter**
```java
class SumTask extends RecursiveTask<Long> {
    private static final int THRESHOLD = 10_000;
    protected Long compute() {
        if (hi - lo <= THRESHOLD) return sequentialSum();
        int mid = (lo + hi) >>> 1;
        SumTask left = new SumTask(data, lo, mid);
        SumTask right = new SumTask(data, mid, hi);
        left.fork();
        return right.compute() + left.join();
    }
}
```

**Task**
1. Implement the sum; verify equal to the sequential result.
2. Extend to a `RecursiveTask<Map<Category, Long>>` with merge.
3. Explain why you `fork()` one subtask and `compute()` the other directly.
4. Benchmark thresholds 100, 1k, 10k, 100k, 1M; find the sweet spot.

**Prove it:** A speed-up graph vs threshold and vs parallelism (1…cores).

---

## Exercise 9.2 — Parallel Merge Sort of Trades ★★★

**Task**
1. Implement a parallel merge sort on `Trade[]` (sort by timestamp) using `RecursiveAction`.
2. Switch to `Arrays.sort` below a threshold.
3. Compare with `Arrays.parallelSort`.
4. Verify stability (equal timestamps keep input order).

**Prove it:** Result identical to `Arrays.sort`; scaling plot with 1, 2, 4, 8 threads.

---

## Exercise 9.3 — Parallel Stream Pitfalls ★★★

**Scenario:** A developer "optimised" a report job with `.parallelStream()` and now results are wrong and the app is slower.

**Task**
1. Reproduce each bug:
   - Mutating a shared `ArrayList` in `forEach` → lost elements / exceptions.
   - Using `forEachOrdered` unnecessarily → no speedup.
   - Small data → overhead dominates.
   - Non-associative reduction (`(a, b) -> a - b`) → wrong answers.
   - Blocking IO in the stream → common pool starvation.
2. Fix each (collectors, associative reductions, bigger chunks, a dedicated pool).
3. Run a parallel stream inside a custom `ForkJoinPool.submit(...)` and show which pool executed it.
4. Write a one-page "when to use parallel streams" checklist.

**Prove it:** Each pitfall has a failing test and a fixed version.

---

## Exercise 9.4 — Parallel Directory Scan (`RecursiveTask` with I/O) ★★★★

**Scenario:** Compute the total size and the top 10 largest files in a large directory tree.

**Task**
1. One `RecursiveTask` per directory; fork children, join results.
2. Observe the trade-off of blocking I/O inside ForkJoin; try `ForkJoinPool.managedBlock` with a `ManagedBlocker`.
3. Compare with a virtual-thread-per-directory version (Group 10).

**Prove it:** Equal results across implementations and a benchmark on a tree of 100k+ files.

---

## Exercise 9.5 — Observe Work-Stealing ★★

**Task**
1. Run an uneven-workload `ForkJoinTask` (some leaves 100× heavier).
2. Print `pool.getStealCount()`, `getActiveThreadCount()`, `getQueuedTaskCount()` periodically.
3. Compare with a fixed thread pool using fixed chunks.

**Prove it:** Show steal counts rising and a faster finish than static partitioning.

---

## Group 9 Completion Checklist
- [ ] I know when fork/join helps and when it doesn't
- [ ] I can implement and tune a `RecursiveTask`
- [ ] I can spot every classic parallel-stream bug
