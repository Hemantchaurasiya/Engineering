package com.orderengine.phase8;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

import com.orderengine.phase8.Domain.Mismatch;
import com.orderengine.phase8.Domain.ReconciliationRecord;

/**
 * Fork/Join divide-and-conquer: recursively split the record list into
 * halves until a chunk is small enough to solve directly (sequentially),
 * then merge results back up. RecursiveTask<V> is the right base class
 * here specifically because each subtask PRODUCES A RESULT (the list of
 * mismatches found in its chunk) that needs merging — contrast with
 * RecursiveAction (NormalizationAction.java in this phase), used when a
 * subtask mutates data in place and returns nothing.
 *
 * THE STANDARD FORK/JOIN IDIOM, used exactly as written below, is worth
 * memorizing as a pattern, not just reading once:
 *
 *   if (problem size <= THRESHOLD) {
 *       solve directly, sequentially — this is the base case
 *   } else {
 *       split the problem into two halves
 *       leftTask.fork();            // schedule the LEFT half to run
 *                                    // asynchronously (possibly on
 *                                    // another worker thread, possibly
 *                                    // stolen — see phase notes §2)
 *       V rightResult = rightTask.compute(); // compute the RIGHT half
 *                                    // DIRECTLY on the CURRENT thread —
 *                                    // deliberately NOT forked
 *       V leftResult = leftTask.join(); // wait for the forked left half
 *       merge leftResult and rightResult
 *   }
 *
 * Forking only ONE of the two halves (not both) and computing the other
 * directly is the standard, deliberate idiom — not laziness. Forking
 * BOTH halves would create an extra task object and an extra scheduling
 * round-trip for work the current thread could simply do itself
 * immediately with zero overhead; forking one and computing the other
 * inline gets the same recursive parallelism with half the task-creation
 * overhead at every level of the recursion.
 *
 * WORK-STEALING (why this scales well): every worker thread in a
 * ForkJoinPool has its OWN double-ended queue (deque) of tasks. A worker
 * pushes/pops its OWN new work from the HEAD of its own deque (LIFO —
 * good cache locality, since the most recently created task is the
 * "smallest"/most fine-grained one and likely still hot in cache). When
 * a worker runs out of its own work, it STEALS from the TAIL of another,
 * busier worker's deque (FIFO from the stealer's perspective) — stealing
 * the OLDEST, typically LARGEST remaining tasks from a busy thread, which
 * is efficient because a large stolen task usually has plenty of further
 * splitting left in it to keep the stealing thread busy for a while,
 * rather than stealing something almost done. This design means idle
 * threads actively find work from busy ones with minimal contention
 * (workers only ever touch the opposite end of each other's deques from
 * where the owner is working), rather than every thread contending on
 * one shared central queue — a completely different contention profile
 * from Phase 4's shared-queue ThreadPoolExecutor.
 */
public class ForkJoinReconciler {

    private static final int THRESHOLD = 500; // tuned in ReconciliationBenchmark; see phase notes §3

    private final ForkJoinPool pool;

    /** Uses a DEDICATED pool, never ForkJoinPool.commonPool() — see phase notes §4
     *  for why sharing the common pool with unrelated work (including Phase 7's
     *  async chains, which also default to it) is exactly the danger flagged there. */
    public ForkJoinReconciler(int parallelism) {
        this.pool = new ForkJoinPool(parallelism);
    }

    public List<Mismatch> reconcile(List<ReconciliationRecord> records) {
        return pool.invoke(new ReconcileTask(records, 0, records.size()));
    }

    public void shutdown() {
        pool.shutdown();
    }

    private static final class ReconcileTask extends RecursiveTask<List<Mismatch>> {
        private final List<ReconciliationRecord> records;
        private final int start; // inclusive
        private final int end;   // exclusive

        ReconcileTask(List<ReconciliationRecord> records, int start, int end) {
            this.records = records;
            this.start = start;
            this.end = end;
        }

        @Override
        protected List<Mismatch> compute() {
            int size = end - start;
            if (size <= THRESHOLD) {
                return solveDirectly();
            }

            int mid = start + size / 2;
            ReconcileTask leftTask = new ReconcileTask(records, start, mid);
            ReconcileTask rightTask = new ReconcileTask(records, mid, end);

            leftTask.fork();                      // schedule left half asynchronously
            List<Mismatch> rightResult = rightTask.compute(); // compute right half on THIS thread
            List<Mismatch> leftResult = leftTask.join();      // wait for the forked left half

            List<Mismatch> merged = new ArrayList<>(leftResult.size() + rightResult.size());
            merged.addAll(leftResult);
            merged.addAll(rightResult);
            return merged;
        }

        private List<Mismatch> solveDirectly() {
            List<Mismatch> mismatches = new ArrayList<>();
            for (int i = start; i < end; i++) {
                ReconciliationRecord record = records.get(i);
                if (Domain.isMismatch(record)) {
                    mismatches.add(new Mismatch(record.orderId(), record.orderAmount(),
                            record.ledgerAmount(), record.orderAmount() - record.ledgerAmount()));
                }
            }
            return mismatches;
        }
    }
}
