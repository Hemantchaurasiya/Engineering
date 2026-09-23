package com.orderengine.phase8;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.orderengine.phase8.Domain.ReconciliationRecord;

public class ParallelStreamPitfallsDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Pitfall 1: forEach + non-thread-safe accumulation ===");
        demoRacyAccumulation();

        System.out.println("\n=== Pitfall 2: parallel streams need a source that splits well ===");
        demoSplittingCost();

        System.out.println("\n=== Pitfall 3: blocking work inside a parallel stream starves the common pool ===");
        demoBlockingInsideParallelStream();
    }

    private static void demoRacyAccumulation() {
        List<ReconciliationRecord> records = Domain.generateDataset(200_000, 0.1);
        ParallelStreamReconciler reconciler = new ParallelStreamReconciler();

        int correctCount = reconciler.reconcileCorrect(records).size();

        int mismatchesFound = -1;
        boolean sawCorruption = false;
        try {
            mismatchesFound = reconciler.reconcileBroken(records).size();
        } catch (Exception e) {
            sawCorruption = true;
            System.out.println("  broken version THREW during concurrent ArrayList.add(): "
                    + e.getClass().getSimpleName());
        }

        System.out.println("  correct (Collectors.toList()) mismatch count: " + correctCount);
        if (!sawCorruption) {
            System.out.println("  broken (racy ArrayList.add()) mismatch count: " + mismatchesFound
                    + (mismatchesFound != correctCount
                            ? "  -> BUG REPRODUCED: counts differ, meaning updates were silently lost"
                            : "  -> no corruption observed THIS run (still racy — timing dependent, "
                                    + "exactly like Phase 1's demos; rerun to see it fail, or run at "
                                    + "even larger scale)"));
        }
    }

    private static void demoSplittingCost() {
        int size = 500_000;
        List<ReconciliationRecord> arrayBacked = Domain.generateDataset(size, 0.1); // ArrayList — splits cheaply
        List<ReconciliationRecord> linkedBacked = new LinkedList<>(arrayBacked);     // LinkedList — splits poorly

        ParallelStreamReconciler reconciler = new ParallelStreamReconciler();

        long start1 = System.currentTimeMillis();
        int arrayResult = reconciler.reconcileCorrect(arrayBacked).size();
        long arrayElapsed = System.currentTimeMillis() - start1;

        long start2 = System.currentTimeMillis();
        int linkedResult = reconciler.reconcileCorrect(linkedBacked).size();
        long linkedElapsed = System.currentTimeMillis() - start2;

        System.out.println("  ArrayList-backed parallel stream:  " + arrayElapsed + "ms (mismatches=" + arrayResult + ")");
        System.out.println("  LinkedList-backed parallel stream: " + linkedElapsed + "ms (mismatches=" + linkedResult + ")");
        System.out.println("  LinkedList's Spliterator can't cheaply jump to a midpoint (no random access) — "
                + "splitting degrades toward walking the list to find split points, eroding or eliminating "
                + "the benefit .parallel() is supposed to provide. Expect ArrayList meaningfully faster here, "
                + "for the exact same total work and thread count.");
    }

    private static void demoBlockingInsideParallelStream() throws InterruptedException {
        // Mirrors Phase 7 §5's ForkJoinPool.commonPool() starvation demo,
        // but triggered through .parallel() instead of supplyAsync() —
        // same underlying pool, same danger, different entry point.
        // Parallel streams use ForkJoinPool.commonPool() by default,
        // exactly like the no-executor CompletableFuture *Async methods.
        int commonPoolParallelism = java.util.concurrent.ForkJoinPool.commonPool().getParallelism();
        System.out.println("  common pool parallelism: " + commonPoolParallelism);

        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < commonPoolParallelism * 3; i++) items.add(i);

        long start = System.currentTimeMillis();
        items.parallelStream().forEach(i -> {
            // Simulates a blocking call (network, disk, a lock wait) made
            // the wrong way — directly inside a parallel stream, which
            // ties up common-pool worker threads for the FULL duration
            // of each blocking call instead of the brief CPU burst
            // parallel streams are designed around.
            sleep(100);
        });
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("  processed " + items.size() + " items with 100ms blocking work each in "
                + elapsed + "ms — with only " + commonPoolParallelism + " common-pool threads available, "
                + "this necessarily runs in multiple waves rather than all concurrently, exactly like a "
                + "regular blocking thread pool would, EXCEPT this pool is shared JVM-wide with unrelated "
                + "code (parallel streams AND CompletableFuture's default executor both use it — see "
                + "Phase 7 §5). Blocking work belongs in a dedicated executor, never directly inside "
                + "a parallel stream.");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
