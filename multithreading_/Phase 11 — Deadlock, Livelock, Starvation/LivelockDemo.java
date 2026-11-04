package com.orderengine.phase11;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Livelock is DIFFERENT from deadlock, and the difference matters
 * diagnostically: in a deadlock, threads are genuinely BLOCKED — a
 * thread dump shows them stuck, not executing any code, each waiting on
 * a lock. In LIVELOCK, threads are actively, continuously RUNNING and
 * RESPONDING to each other — CPU usage can look completely normal, a
 * thread dump shows threads busy in real code, not blocked on anything —
 * yet the system makes NO real forward progress, because every thread
 * keeps "politely" backing off in a way that perfectly cancels out the
 * other's attempt, forever, in lockstep.
 *
 * THE SCENARIO: two "reconciliation workers" each need BOTH accounts
 * locked briefly to double-check a mismatch, using tryLock so as not to
 * deadlock — the "polite" instinct: "if I can't get both locks right
 * now, let go of what I have and let the other one through." Without
 * ANY randomization in the retry timing, and with both threads following
 * the EXACT same "detect contention, back off immediately, retry
 * immediately" logic, they can fall into PERFECT lockstep: both grab
 * their first lock at the same moment, both fail to get their second at
 * the same moment, both release and retry at the same moment, forever —
 * a real, reproducible failure mode, not just a theoretical curiosity.
 *
 * THE FIX is the same principle FixedTransfer's tryLock fix already
 * applied: RANDOMIZED backoff. Breaking the symmetry between the two
 * threads' retry timing means they stop colliding in lockstep — sooner
 * or later, by chance, one of them ends up retrying at a moment when the
 * other genuinely isn't contending, and it gets through.
 */
public class LivelockDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Livelock: no randomized backoff, threads collide in lockstep ===");
        runScenario(false);

        System.out.println("\n=== Fixed: randomized backoff breaks the symmetry ===");
        runScenario(true);
    }

    private static void runScenario(boolean useRandomizedBackoff) throws InterruptedException {
        ReentrantLock lockA = new ReentrantLock();
        ReentrantLock lockB = new ReentrantLock();
        AtomicInteger successfulReconciliations = new AtomicInteger(0);
        AtomicInteger totalAttempts = new AtomicInteger(0);

        Runnable politeWorker = () -> {
            while (successfulReconciliations.get() < 1) {
                totalAttempts.incrementAndGet();
                boolean gotA = lockA.tryLock();
                if (gotA) {
                    // Simulate a moment of real work between the two
                    // acquisitions — this is what creates the contention
                    // window the other thread can collide with.
                    sleepMicros();
                    boolean gotB = lockB.tryLock();
                    if (gotB) {
                        successfulReconciliations.incrementAndGet();
                        lockB.unlock();
                        lockA.unlock();
                        return;
                    }
                    lockA.unlock(); // "polite" — give up A since we couldn't also get B
                }
                if (useRandomizedBackoff) {
                    sleepMillis(ThreadLocalRandom.current().nextInt(1, 10));
                }
                // without backoff: loop immediately, no delay at all —
                // maximizes the chance of perfectly re-colliding
            }
        };

        Thread t1 = new Thread(politeWorker, "reconciler-1");
        Thread t2 = new Thread(politeWorker, "reconciler-2");

        long start = System.currentTimeMillis();
        t1.start();
        t2.start();

        long timeoutMillis = 2000;
        t1.join(timeoutMillis);
        t2.join(timeoutMillis);
        long elapsed = System.currentTimeMillis() - start;

        boolean stillRunning = t1.isAlive() || t2.isAlive();
        System.out.println("  after " + timeoutMillis + "ms: successful reconciliations = "
                + successfulReconciliations.get() + ", total attempts = " + totalAttempts.get()
                + ", threads still running = " + stillRunning);

        if (stillRunning) {
            t1.interrupt();
            t2.interrupt();
            System.out.println("  RESULT: LIVELOCK — both threads were still actively spinning after "
                    + timeoutMillis + "ms with " + totalAttempts.get() + " wasted attempts and ZERO successes; "
                    + "note neither thread was ever BLOCKED — a thread dump during this window would show "
                    + "both RUNNABLE, actively executing, which is exactly what makes livelock harder to spot "
                    + "than deadlock in production monitoring that only alerts on blocked/stuck threads.");
        } else {
            System.out.println("  RESULT: completed in " + elapsed + "ms after " + totalAttempts.get()
                    + " attempts (randomized backoff broke the symmetry and let one thread through)");
        }
    }

    private static void sleepMicros() {
        try {
            TimeUnit.MICROSECONDS.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
