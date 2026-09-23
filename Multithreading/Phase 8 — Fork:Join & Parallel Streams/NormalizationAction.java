package com.orderengine.phase8;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * RecursiveAction is RecursiveTask's sibling for subtasks that mutate
 * data IN PLACE and return nothing, rather than producing a value that
 * needs merging. Used here for a simple normalization pass — rounding
 * every amount in a mutable array to 2 decimal places before
 * reconciliation — where each element's update is fully independent and
 * there's no result to combine, only a side effect to apply.
 *
 * Same fork-one-compute-other-directly idiom as ForkJoinReconciler,
 * just without a join()-and-merge step at the end, since there's nothing
 * to merge — join() here is only for WAITING until the forked half is
 * done, not for retrieving a value from it (RecursiveAction.join()
 * returns void).
 */
public class NormalizationAction {

    private static final int THRESHOLD = 1000;

    public static void normalize(double[] amounts) {
        try (ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors())) {
            pool.invoke(new NormalizeTask(amounts, 0, amounts.length));
        }
    }

    private static final class NormalizeTask extends RecursiveAction {
        private final double[] amounts;
        private final int start;
        private final int end;

        NormalizeTask(double[] amounts, int start, int end) {
            this.amounts = amounts;
            this.start = start;
            this.end = end;
        }

        @Override
        protected void compute() {
            int size = end - start;
            if (size <= THRESHOLD) {
                for (int i = start; i < end; i++) {
                    amounts[i] = Math.round(amounts[i] * 100.0) / 100.0;
                }
                return;
            }

            int mid = start + size / 2;
            NormalizeTask left = new NormalizeTask(amounts, start, mid);
            NormalizeTask right = new NormalizeTask(amounts, mid, end);

            left.fork();
            right.compute(); // right half runs on this thread
            left.join();     // just waits — RecursiveAction.join() returns void, nothing to merge
        }
    }
}
