package com.orderengine.phase9;

import java.util.ArrayList;
import java.util.List;

/**
 * 10,000 notifications, each simulating a 100ms blocking network call —
 * a genuinely I/O-heavy workload, exactly the shape the notification
 * pool was originally sized for in Phase 4. A SMALL fixed platform pool
 * (deliberately undersized here — 50 threads, chosen to make the
 * bottleneck obvious) can only have 50 notifications truly in flight at
 * once; the other 9,950 queue behind them in waves. Virtual threads
 * launch all 10,000 essentially at once — each blocks on its own
 * Thread.sleep, unmounts from its carrier immediately (freeing that
 * carrier for another virtual thread), and total wall-clock time should
 * be close to the time for ONE 100ms send, not 10,000/50 = 200 sequential
 * waves of 100ms each.
 */
public class NotificationBenchmark {

    private static final int NOTIFICATION_COUNT = 10_000;
    private static final long SIMULATED_LATENCY_MILLIS = 100;
    private static final int SMALL_PLATFORM_POOL_SIZE = 50;

    public static void main(String[] args) throws InterruptedException {
        List<Long> orderIds = new ArrayList<>();
        for (long i = 0; i < NOTIFICATION_COUNT; i++) orderIds.add(i);

        System.out.println(NOTIFICATION_COUNT + " notifications, "
                + SIMULATED_LATENCY_MILLIS + "ms simulated latency each\n");

        NotificationSender sender = new NotificationSender(SIMULATED_LATENCY_MILLIS);

        PlatformThreadNotificationStage platformStage =
                new PlatformThreadNotificationStage(SMALL_PLATFORM_POOL_SIZE, sender);
        long t0 = System.currentTimeMillis();
        platformStage.sendAll(orderIds);
        long platformElapsed = System.currentTimeMillis() - t0;
        platformStage.shutdown();

        System.out.printf("platform pool (size=%d): %,dms total  "
                        + "(theoretical minimum waves: %d, so ~%,dms expected)%n",
                SMALL_PLATFORM_POOL_SIZE, platformElapsed,
                (NOTIFICATION_COUNT + SMALL_PLATFORM_POOL_SIZE - 1) / SMALL_PLATFORM_POOL_SIZE,
                ((NOTIFICATION_COUNT + SMALL_PLATFORM_POOL_SIZE - 1) / SMALL_PLATFORM_POOL_SIZE) * SIMULATED_LATENCY_MILLIS);

        VirtualThreadNotificationStage virtualStage = new VirtualThreadNotificationStage(sender);
        long t1 = System.currentTimeMillis();
        virtualStage.sendAll(orderIds);
        long virtualElapsed = System.currentTimeMillis() - t1;
        virtualStage.shutdown();

        System.out.printf("virtual thread-per-task: %,dms total  "
                        + "(theoretical minimum: ~%dms, one wave for ALL %,d notifications)%n",
                virtualElapsed, SIMULATED_LATENCY_MILLIS, NOTIFICATION_COUNT);

        System.out.printf("%nspeedup: %.1fx%n", (double) platformElapsed / virtualElapsed);
        System.out.println("(no pool-sizing formula was needed for the virtual-thread version — "
                + "compare this class's simplicity to Phase 4's PoolSizingCalculator-driven StageExecutors)");
    }
}
