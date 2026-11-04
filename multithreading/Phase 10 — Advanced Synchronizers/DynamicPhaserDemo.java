package com.orderengine.phase10;

import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Phaser is CyclicBarrier's more flexible, more complex sibling. The key
 * difference: a CyclicBarrier's party count is FIXED at construction and
 * cannot change. A Phaser's registered party count can change DYNAMICALLY
 * — parties can register() and arriveAndDeregister() at runtime, across
 * phases, which a CyclicBarrier simply cannot express at all.
 *
 * Realistic motivating scenario for this engine: a multi-phase
 * reconciliation job (Phase 8 territory) where the number of active
 * worker threads is itself elastic — perhaps workers are spun up or torn
 * down between phases based on remaining batch size, or some workers
 * finish their assigned portion early and should stop participating in
 * future phases entirely rather than being forced to keep showing up to
 * a barrier they have no more work for.
 *
 * Core Phaser operations used below:
 *   register()               — add a new party the phaser will wait for
 *   arriveAndAwaitAdvance()  — this party has finished its part of the
 *                              CURRENT phase; block until every
 *                              currently-registered party has also
 *                              arrived, then advance to the next phase
 *                              together (like CyclicBarrier.await())
 *   arriveAndDeregister()    — this party has finished ALL the phases it
 *                              will participate in; arrive at the
 *                              current phase AND permanently stop being
 *                              counted for future phases
 *
 * A phase only advances once every CURRENTLY REGISTERED party has
 * arrived — deregistered parties are no longer counted, so the
 * remaining, still-registered parties aren't stuck waiting forever for
 * a party that already left.
 */
public class DynamicPhaserDemo {

    public static void main(String[] args) throws InterruptedException {
        Phaser phaser = new Phaser(1); // 1 = this main thread registers itself first, deregisters at the end

        int workerCount = 5;
        Thread[] workers = new Thread[workerCount];

        for (int w = 0; w < workerCount; w++) {
            final int workerId = w;
            // Workers with an even id finish early (after phase 2) and
            // deregister — workers with an odd id stay for all 4 phases.
            final int phasesThisWorkerParticipatesIn = (workerId % 2 == 0) ? 2 : 4;

            phaser.register(); // each worker registers itself as a party BEFORE starting

            workers[w] = new Thread(() -> {
                for (int phase = 1; phase <= phasesThisWorkerParticipatesIn; phase++) {
                    sleep(ThreadLocalRandom.current().nextLong(20, 100)); // simulate this phase's work
                    System.out.println("  worker-" + workerId + " finished phase " + phase);

                    if (phase == phasesThisWorkerParticipatesIn) {
                        System.out.println("  worker-" + workerId + " deregistering after phase " + phase
                                + " (done for good — future phases won't wait for it)");
                        phaser.arriveAndDeregister();
                    } else {
                        phaser.arriveAndAwaitAdvance(); // waits for all CURRENTLY registered parties
                    }
                }
            }, "worker-" + workerId);
        }

        for (Thread t : workers) t.start();

        // Main thread also participates in each phase transition, purely
        // to observe and print phase-advance progress.
        int totalPhases = 4;
        for (int phase = 1; phase <= totalPhases; phase++) {
            int phaseReached = phaser.arriveAndAwaitAdvance();
            System.out.println("=== phase " + phase + " complete for all currently-registered parties "
                    + "(phaser now at phase " + phaseReached + ", "
                    + phaser.getRegisteredParties() + " parties still registered) ===");
        }
        phaser.arriveAndDeregister(); // main thread done observing

        for (Thread t : workers) t.join();
        System.out.println("\nAll workers finished. Final registered party count: " + phaser.getRegisteredParties()
                + " (should be 0 — everyone deregistered)");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
