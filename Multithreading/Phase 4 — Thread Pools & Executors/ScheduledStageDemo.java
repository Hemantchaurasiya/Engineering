package com.orderengine.phase4;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ScheduledExecutorService covers two genuinely different real needs in
 * this engine, and the API differs deliberately for each:
 *
 * 1. scheduleAtFixedRate — "run every N seconds, regardless of how long
 *    each run takes" (as long as each run finishes before the next is
 *    due; if a run takes LONGER than the period, the next execution
 *    starts immediately after the previous one finishes rather than
 *    overlapping — ScheduledExecutorService never runs the same task
 *    concurrently with itself). Used here for periodic metrics flush —
 *    the dashboard wants a fresh number every 500ms on a steady
 *    cadence, not measured relative to how long the previous flush took.
 *
 * 2. scheduleWithFixedDelay — "run, then wait N seconds AFTER it
 *    finishes, then run again." Used here for payment retry backoff —
 *    after a failed payment attempt, we want a fixed gap AFTER the
 *    failure before retrying, not a fixed gap from when the ORIGINAL
 *    attempt started (which fixed-rate would give you, and which could
 *    mean back-to-back retries with zero gap if an attempt happened to
 *    take exactly as long as the period).
 *
 * THE PITFALL this demo also proves: if a scheduled task throws an
 * uncaught exception, ALL FUTURE EXECUTIONS OF THAT TASK SILENTLY STOP —
 * permanently, with no further log output, no exception surfaced
 * anywhere unless you're specifically checking the Future. This is a
 * dramatically worse version of the plain-thread silent-death pitfall
 * from NamedThreadFactory's javadoc: here, the task doesn't even get a
 * fresh thread next time — the recurring schedule itself is simply
 * cancelled. Production code scheduling recurring work must catch and
 * handle exceptions INSIDE the task body, never let them propagate out.
 */
public class ScheduledStageDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== scheduleAtFixedRate: metrics flush every 500ms ===");
        demoFixedRate();

        System.out.println("\n=== scheduleWithFixedDelay: payment retry backoff ===");
        demoFixedDelay();

        System.out.println("\n=== Pitfall: an uncaught exception silently kills future runs ===");
        demoSilentDeathPitfall();
    }

    private static void demoFixedRate() throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory("metrics-flush", true));
        AtomicInteger tick = new AtomicInteger(0);

        ScheduledFuture<?> handle = scheduler.scheduleAtFixedRate(() -> {
            int n = tick.incrementAndGet();
            System.out.println("  flush #" + n + " at " + System.currentTimeMillis() % 100000);
        }, 0, 500, TimeUnit.MILLISECONDS);

        Thread.sleep(2200);
        handle.cancel(false);
        scheduler.shutdown();
        System.out.println("  cancelled after ~2.2s, observed " + tick.get() + " flushes (expect ~5)");
    }

    private static void demoFixedDelay() throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory("payment-retry", true));
        AtomicInteger attempt = new AtomicInteger(0);

        ScheduledFuture<?> handle = scheduler.scheduleWithFixedDelay(() -> {
            int n = attempt.incrementAndGet();
            long simulatedCallDurationMillis = 300; // pretend this attempt itself takes 300ms
            System.out.println("  retry attempt #" + n + " starting");
            sleep(simulatedCallDurationMillis);
            System.out.println("  retry attempt #" + n + " finished — next starts 500ms after THIS point");
        }, 0, 500, TimeUnit.MILLISECONDS);

        Thread.sleep(2500);
        handle.cancel(false);
        scheduler.shutdown();
        System.out.println("  cancelled after ~2.5s, observed " + attempt.get()
                + " attempts (each ~800ms apart: 300ms work + 500ms delay)");
    }

    private static void demoSilentDeathPitfall() throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory("broken-scheduled-task", true));
        AtomicInteger runs = new AtomicInteger(0);

        scheduler.scheduleAtFixedRate(() -> {
            int n = runs.incrementAndGet();
            System.out.println("  run #" + n);
            if (n == 2) {
                throw new RuntimeException("simulated bug on run #2");
            }
        }, 0, 200, TimeUnit.MILLISECONDS);

        Thread.sleep(1200);
        scheduler.shutdown();
        System.out.println("  total runs observed: " + runs.get()
                + " (expect exactly 2 — the exception on run #2 silently cancelled all future runs, "
                + "no exception was ever printed anywhere because nothing checked the Future)");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
