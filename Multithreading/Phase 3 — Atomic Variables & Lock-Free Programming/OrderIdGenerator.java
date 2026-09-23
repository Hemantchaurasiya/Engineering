package com.orderengine.phase3;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Lock-free, globally unique, monotonically increasing order ID generator
 * for edge-gateway. Every incoming order gets its ID from here before
 * entering the pipeline. This needs to be correct under extremely high
 * concurrency (every ingestion thread calls it) and needs to never block —
 * ID generation sitting behind a lock would serialize the very first step
 * of every request, becoming an artificial bottleneck for the whole
 * system before any real work even starts.
 *
 * Two implementations on purpose:
 *
 *  - nextId() uses AtomicLong.incrementAndGet(), the idiomatic way to do
 *    this in production code. Use this one.
 *
 *  - nextIdManualCas() reimplements the exact same operation by hand with
 *    an explicit compareAndSet retry loop, to make CAS mechanics visible
 *    instead of hidden inside a JDK method. Never write this pattern by
 *    hand in production for something the JDK already provides — it
 *    exists here purely so you can see the shape of every lock-free
 *    algorithm in this phase (Treiber stack included) before it gets
 *    more complex with pointers/references instead of a single long.
 */
public class OrderIdGenerator {

    private final AtomicLong sequence = new AtomicLong(0);

    /** Production path: let the JDK's intrinsic-backed CAS loop do it. */
    public long nextId() {
        return sequence.incrementAndGet();
    }

    /**
     * Educational path: same result, hand-rolled. This is the canonical
     * shape of every CAS-based algorithm:
     *
     *   1. Read the current value (a plain, unsynchronized read).
     *   2. Compute the desired next value from it.
     *   3. Attempt to atomically swap old -> new with compareAndSet,
     *      which succeeds ONLY IF no other thread has changed the value
     *      since step 1.
     *   4. If it failed, another thread won the race — retry from step 1
     *      with the now-current value. This is "optimistic concurrency":
     *      assume no interference, verify atomically, retry on conflict,
     *      instead of taking a lock that blocks other threads from even
     *      trying concurrently.
     *
     * Under contention this can retry multiple times per call, but each
     * individual attempt is a single hardware CAS instruction (cmpxchg
     * on x86) — no thread ever blocks/parks waiting for another thread to
     * finish, unlike a lock. That's the core lock-free property: system-
     * wide progress is guaranteed (SOME thread's CAS succeeds every
     * attempt cycle), even though any individual thread could in theory
     * keep losing the race under pathological scheduling.
     */
    public long nextIdManualCas() {
        while (true) {
            long current = sequence.get();
            long next = current + 1;
            if (sequence.compareAndSet(current, next)) {
                return next;
            }
            // CAS failed: another thread updated `sequence` between our
            // get() and our compareAndSet(). Loop and retry with the
            // fresh value — no lock was ever held, no thread blocked.
        }
    }

    public long currentValue() {
        return sequence.get();
    }
}
