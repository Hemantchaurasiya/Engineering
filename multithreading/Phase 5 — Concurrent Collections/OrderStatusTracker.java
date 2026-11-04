package com.orderengine.phase5;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live order-status tracker: order-service updates this on every pipeline
 * stage transition (VALIDATED -> INVENTORY_RESERVED -> PAYMENT_CHARGED ->
 * NOTIFIED), and any stage can be racing another stage's update or a
 * status-read from a monitoring endpoint at the same instant. This makes
 * ConcurrentHashMap the right structure, but ONLY if you use its atomic
 * compound-operation methods correctly — see the deliberately-wrong
 * method below.
 *
 * ConcurrentHashMap does NOT make every multi-step operation you build on
 * top of it atomic just because the map itself is thread-safe. "Read,
 * decide, write" built out of two separate map calls is exactly as racy
 * on a ConcurrentHashMap as it would be on a plain HashMap — the map
 * guarantees each individual call is safe, not that your sequence of
 * calls is. compute()/computeIfAbsent()/computeIfPresent()/merge() exist
 * specifically to make the WHOLE read-modify-write atomic as one call.
 */
public class OrderStatusTracker {

    public enum Status { VALIDATED, INVENTORY_RESERVED, PAYMENT_CHARGED, NOTIFIED, FAILED }

    private final ConcurrentHashMap<Long, Status> statusByOrderId = new ConcurrentHashMap<>();

    /**
     * BROKEN (kept here deliberately for the phase notes / demo to point
     * at): "only advance to INVENTORY_RESERVED if currently VALIDATED"
     * implemented as separate get() + put() calls. Two threads racing to
     * advance the SAME order's status (e.g. a duplicate delivery of the
     * same event from an upstream system, a common real occurrence) can
     * both read VALIDATED, both decide the transition is legal, and both
     * write — the second write silently overwrites the first with an
     * identical value, which looks harmless here but is the same race
     * shape that, on a method that also has a SIDE EFFECT per transition
     * (e.g. "reserve inventory exactly once"), would double-execute that
     * side effect. ConcurrentHashMap being thread-safe per-call does not
     * protect this sequence of two calls.
     */
    public boolean advanceToReservedBroken(long orderId) {
        Status current = statusByOrderId.get(orderId);        // call #1
        if (current == Status.VALIDATED) {
            statusByOrderId.put(orderId, Status.INVENTORY_RESERVED); // call #2 — race window between #1 and #2
            return true;
        }
        return false;
    }

    /**
     * FIXED: the entire read-modify-write happens inside a single call to
     * compute(), which ConcurrentHashMap guarantees executes atomically
     * per key — internally by holding the lock on that key's bin for the
     * duration of the remapping function (see phase notes §2 on exactly
     * what's locked and what isn't). Two threads racing this call for the
     * same orderId are fully serialized against EACH OTHER for that key
     * only — a completely different key proceeds concurrently, unaffected.
     */
    public boolean advanceToReserved(long orderId) {
        boolean[] transitionedByThisCall = new boolean[1];
        statusByOrderId.compute(orderId, (id, current) -> {
            if (current == Status.VALIDATED) {
                transitionedByThisCall[0] = true;
                return Status.INVENTORY_RESERVED;
            }
            return current; // already transitioned (by someone else) or in some other state — leave unchanged
        });
        return transitionedByThisCall[0];
    }

    /** Atomic "insert only if this is the first time we've seen this order". */
    public boolean recordValidated(long orderId) {
        return statusByOrderId.putIfAbsent(orderId, Status.VALIDATED) == null;
    }

    /**
     * Test-only hook variant of advanceToReservedBroken(): identical
     * logic, but runs the given action in the exact race window between
     * the get() and the put(). This lets CheckThenActRaceDemo force the
     * losing interleaving deterministically (guaranteed to reproduce on
     * any hardware, including a single-core machine where the natural,
     * unforced version of this race may not trigger via scheduling luck
     * alone) — the same deterministic-reproduction approach Phase 3's
     * AbaProblemDemo uses, rather than relying on the JIT-hoisting-style
     * timing luck from Phase 1's demo.
     */
    public boolean advanceToReservedBrokenWithHook(long orderId, Runnable betweenGetAndPut) {
        Status current = statusByOrderId.get(orderId);
        if (current == Status.VALIDATED) {
            if (betweenGetAndPut != null) {
                betweenGetAndPut.run();
            }
            statusByOrderId.put(orderId, Status.INVENTORY_RESERVED);
            return true;
        }
        return false;
    }

    /**
     * Test-only hook variant of advanceToReserved(): runs the given
     * action INSIDE the compute() remapping function, before the
     * transition check. Because compute() holds the per-key bin lock for
     * the entire duration of the remapping function (Phase 5 notes §2),
     * any other thread's own compute()/advanceToReserved() call for the
     * SAME key must fully block until this call (hook included) returns
     * — CheckThenActRaceDemo measures this directly by timing how long a
     * second thread's call takes, proving real mutual exclusion rather
     * than just asserting it.
     */
    public boolean advanceToReservedWithHook(long orderId, Runnable insideComputeBlock) {
        boolean[] transitionedByThisCall = new boolean[1];
        statusByOrderId.compute(orderId, (id, current) -> {
            if (insideComputeBlock != null) {
                insideComputeBlock.run();
            }
            if (current == Status.VALIDATED) {
                transitionedByThisCall[0] = true;
                return Status.INVENTORY_RESERVED;
            }
            return current;
        });
        return transitionedByThisCall[0];
    }

    public Status statusOf(long orderId) {
        return statusByOrderId.get(orderId);
    }

    public Map<Long, Status> snapshot() {
        // Iterating a ConcurrentHashMap directly (via keySet()/entrySet())
        // is safe and never throws ConcurrentModificationException, but
        // is "weakly consistent": it may or may not reflect updates made
        // by other threads DURING this iteration, without throwing or
        // corrupting anything either way. new HashMap<>(this) below takes
        // a point-in-time-ish copy for callers that want a stable view.
        return new java.util.HashMap<>(statusByOrderId);
    }

    public int trackedOrderCount() {
        return statusByOrderId.size();
    }
}
