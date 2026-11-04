package com.orderengine.phase4;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default Executors.defaultThreadFactory() names threads "pool-N-thread-M"
 * — useless in a thread dump or a log line when you're trying to figure
 * out which PIPELINE STAGE a stuck or throwing thread belongs to. Every
 * pool this engine creates uses this factory instead, so
 * "inventory-reservation-worker-3" tells you immediately, in a thread
 * dump or a stack trace, which subsystem you're looking at.
 *
 * Two other things a custom ThreadFactory should almost always set,
 * both of which the JDK default gets "wrong" for a production service:
 *
 *  - daemon status: worker threads in a long-running service should
 *    normally be non-daemon while the pool is meant to keep the JVM
 *    alive (the JVM won't exit while non-daemon threads are running),
 *    but pools doing background/best-effort work (e.g. periodic metrics
 *    flush) may want daemon=true so they never block shutdown. This
 *    factory takes it as a constructor parameter per-pool rather than
 *    guessing.
 *
 *  - an uncaught exception handler. If a task submitted via execute()
 *    (not submit()) throws an unchecked exception, and nothing catches
 *    it, the THREAD DIES SILENTLY by default — no log line, nothing —
 *    and the pool quietly replaces it with a fresh thread on the next
 *    submission. Without a handler wired here, this class of bug can run
 *    in production for months leaving only a mysteriously-declining
 *    throughput as evidence. (Contrast with submit(), which is covered
 *    in the theory notes §6 — it captures exceptions into the Future
 *    instead, which has its own, different footgun.)
 */
public class NamedThreadFactory implements ThreadFactory {

    private final String poolName;
    private final boolean daemon;
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    public NamedThreadFactory(String poolName, boolean daemon) {
        this.poolName = poolName;
        this.daemon = daemon;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r, poolName + "-worker-" + threadNumber.getAndIncrement());
        thread.setDaemon(daemon);
        thread.setUncaughtExceptionHandler((t, e) ->
                System.err.println("[UNCAUGHT] thread=" + t.getName()
                        + " pool=" + poolName + " exception=" + e));
        return thread;
    }
}
