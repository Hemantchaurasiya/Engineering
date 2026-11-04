package com.orderengine.phase1;

import java.util.ArrayList;
import java.util.List;

/**
 * INTENTIONALLY BROKEN — do not copy into production.
 *
 * This is the first version of the order-intake buffer for edge-gateway.
 * A background "gateway thread" pushes incoming orders into an internal
 * list. A separate "drain thread" is supposed to notice when the gateway
 * signals shutdown and stop pulling.
 *
 * Two independent JMM bugs are baked in on purpose:
 *
 *  1. `running` is a plain boolean, not volatile, and not guarded by a
 *     lock. There is no happens-before edge between the write in
 *     shutdown() and the read in the drain loop. The JIT is legally
 *     allowed to hoist the read of `running` out of the while-loop
 *     entirely (it sees no possible write from another thread, from its
 *     single-threaded-optimizer point of view), which produces an
 *     infinite loop that never observes the shutdown signal.
 *
 *  2. `count` is a plain int incremented from multiple gateway threads
 *     with `count++`. That is read-modify-write — three separate
 *     operations — with no atomicity guarantee, so increments are lost
 *     under concurrent writers.
 */
public class BrokenOrderIngestionBuffer {

    // BUG #1: no volatile, no synchronized, no happens-before edge at all.
    private boolean running = true;

    // BUG #2: not atomic. count++ is read -> add 1 -> write. Two threads
    // can both read the same value before either writes back, so one
    // increment is silently lost.
    private int count = 0;

    private final List<Order> intake = new ArrayList<>();

    /** Called by ingestion (gateway) threads for every incoming order. */
    public void offer(Order order) {
        synchronized (intake) {
            intake.add(order);
        }
        count++; // BUG #2 site
    }

    /** Called once, from a control thread, to stop the drain loop. */
    public void shutdown() {
        running = false; // BUG #1 site: this write may never become visible
    }

    /**
     * Called by the drain thread. Spins until shutdown() is observed.
     * With the bug present, this can spin forever even though shutdown()
     * has definitely returned on the other thread.
     */
    public void drainUntilShutdown(Runnable onEachSpin) {
        long spins = 0;
        while (running) {
            spins++;
            if (onEachSpin != null && spins % 50_000_000L == 0) {
                onEachSpin.run();
            }
        }
    }

    public int getCount() {
        return count;
    }
}
