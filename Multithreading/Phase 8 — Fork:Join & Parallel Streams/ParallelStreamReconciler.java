package com.orderengine.phase8;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.orderengine.phase8.Domain.Mismatch;
import com.orderengine.phase8.Domain.ReconciliationRecord;

public class ParallelStreamReconciler {

    /**
     * CORRECT usage. Collectors.toList() (and stream collection in
     * general via collect()) is safe under .parallel() because the
     * Collector interface has a defined, safe strategy for combining
     * partial results from different threads (a "combiner" function that
     * merges independently-accumulated partial containers) — you never
     * see a partially-updated shared container from two threads at once.
     * The parallelism here is entirely internal to the stream machinery;
     * nothing in this method touches shared mutable state directly.
     */
    public List<Mismatch> reconcileCorrect(List<ReconciliationRecord> records) {
        return records.parallelStream()
                .filter(Domain::isMismatch)
                .map(r -> new Mismatch(r.orderId(), r.orderAmount(), r.ledgerAmount(),
                        r.orderAmount() - r.ledgerAmount()))
                .collect(Collectors.toList());
    }

    /**
     * BROKEN — kept here deliberately for ParallelStreamPitfallsDemo to
     * exercise. forEach() on a parallel stream runs the lambda from
     * MULTIPLE THREADS CONCURRENTLY, by design — that's the entire point
     * of using .parallel(). Calling a non-thread-safe ArrayList.add()
     * from inside that lambda is exactly Phase 1's uncoordinated shared
     * mutable state problem, dressed up in stream syntax that makes it
     * easy to forget you're now in a multithreaded context. ArrayList's
     * internal array-resizing logic has no synchronization at all; two
     * threads calling add() concurrently can corrupt its internal size
     * bookkeeping, silently drop elements, or throw
     * ArrayIndexOutOfBoundsException/ConcurrentModificationException
     * depending on the exact interleaving — a nondeterministic bug by
     * nature, exactly like Phase 5's CheckThenActRaceDemo but reachable
     * from ordinary-looking stream code with no lock or atomic anywhere
     * in sight to raise suspicion during a code review.
     */
    public List<Mismatch> reconcileBroken(List<ReconciliationRecord> records) {
        List<Mismatch> mismatches = new ArrayList<>(); // NOT thread-safe
        records.parallelStream().forEach(r -> {
            if (Domain.isMismatch(r)) {
                mismatches.add(new Mismatch(r.orderId(), r.orderAmount(), r.ledgerAmount(),
                        r.orderAmount() - r.ledgerAmount())); // racy — see javadoc above
            }
        });
        return mismatches;
    }
}
