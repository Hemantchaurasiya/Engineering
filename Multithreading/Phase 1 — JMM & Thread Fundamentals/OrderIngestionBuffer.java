package com.orderengine.phase1;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production-ready version of the intake buffer. This is the real
 * component we carry forward into later phases (Phase 5 replaces the
 * ArrayList+lock with a BlockingQueue; Phase 4 wires this into a pool).
 * For Phase 1 the goal is narrowly: fix the two JMM bugs correctly and
 * be able to explain *why* each fix works at the memory-model level.
 *
 * Fix #1 (visibility): `running` is now `volatile`. A volatile write
 * establishes a happens-before edge with every subsequent volatile read
 * of the same field (JLS 17.4.5). That guarantees the drain thread's
 * next read of `running` sees the control thread's write — not just
 * "probably sees it sooner", but is GUARANTEED to see it, and also
 * guaranteed to see everything the writing thread did before the write
 * (here, nothing else, but this generalizes — see notes section 4).
 *
 * Fix #2 (atomicity): `count` becomes an AtomicInteger and uses
 * incrementAndGet(), which performs the whole read-modify-write as one
 * atomic CAS-based operation. volatile alone would NOT have fixed this
 * bug — volatile gives visibility, not atomicity of compound actions.
 * That distinction is the most commonly confused point in the JMM and
 * is worth sitting with (see notes section 3).
 */
public class OrderIngestionBuffer {

    private volatile boolean running = true;          // fix #1
    private final AtomicInteger count = new AtomicInteger(0); // fix #2

    private final List<Order> intake = new ArrayList<>();

    public void offer(Order order) {
        synchronized (intake) {
            intake.add(order);
        }
        count.incrementAndGet();
    }

    public void shutdown() {
        running = false;
    }

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
        return count.get();
    }

    public List<Order> snapshotAndClear() {
        synchronized (intake) {
            List<Order> copy = new ArrayList<>(intake);
            intake.clear();
            return copy;
        }
    }
}
