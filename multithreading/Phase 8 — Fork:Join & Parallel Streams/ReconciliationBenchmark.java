package com.orderengine.phase8;

import java.util.List;

import com.orderengine.phase8.Domain.Mismatch;
import com.orderengine.phase8.Domain.ReconciliationRecord;

/**
 * Runs all three implementations against the SAME datasets at several
 * sizes, checking correctness first (every implementation must find the
 * exact same number of mismatches) and only then comparing timing.
 *
 * The key thing to observe across the size sweep: parallelism has real,
 * fixed overhead (task creation, scheduling, joining/merging results).
 * At SMALL sizes, that overhead can exceed the total sequential work
 * itself, making the "parallel" versions slower than plain sequential
 * code — parallelism is not a free win, it's a trade of fixed overhead
 * for potential throughput, and the trade only pays off once there's
 * enough real work to amortize the overhead against. Watch for the
 * crossover point in the printed results where fork/join and parallel
 * streams start winning.
 */
public class ReconciliationBenchmark {

    public static void main(String[] args) {
        int[] sizes = {1_000, 10_000, 100_000, 1_000_000};

        for (int size : sizes) {
            System.out.println("=== dataset size = " + size + " ===");
            List<ReconciliationRecord> records = Domain.generateDataset(size, 0.1);

            SequentialReconciler sequential = new SequentialReconciler();
            ForkJoinReconciler forkJoin = new ForkJoinReconciler(Runtime.getRuntime().availableProcessors());
            ParallelStreamReconciler parallelStream = new ParallelStreamReconciler();

            long t0 = System.nanoTime();
            List<Mismatch> sequentialResult = sequential.reconcile(records);
            long sequentialMs = (System.nanoTime() - t0) / 1_000_000;

            long t1 = System.nanoTime();
            List<Mismatch> forkJoinResult = forkJoin.reconcile(records);
            long forkJoinMs = (System.nanoTime() - t1) / 1_000_000;

            long t2 = System.nanoTime();
            List<Mismatch> parallelStreamResult = parallelStream.reconcileCorrect(records);
            long parallelStreamMs = (System.nanoTime() - t2) / 1_000_000;

            forkJoin.shutdown();

            boolean correct = sequentialResult.size() == forkJoinResult.size()
                    && sequentialResult.size() == parallelStreamResult.size();

            System.out.printf("  sequential:      %,6dms  (mismatches=%d)%n", sequentialMs, sequentialResult.size());
            System.out.printf("  fork/join:       %,6dms  (%.2fx)%n", forkJoinMs,
                    ratio(sequentialMs, forkJoinMs));
            System.out.printf("  parallel stream:  %,6dms  (%.2fx)%n", parallelStreamMs,
                    ratio(sequentialMs, parallelStreamMs));
            System.out.println("  correctness: " + (correct ? "OK, all three agree" : "MISMATCH — investigate!"));
            System.out.println();
        }
    }

    private static double ratio(long sequentialMs, long otherMs) {
        return otherMs == 0 ? Double.POSITIVE_INFINITY : (double) sequentialMs / otherMs;
    }
}
