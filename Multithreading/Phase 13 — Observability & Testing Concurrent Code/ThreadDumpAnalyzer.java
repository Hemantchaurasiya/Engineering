package com.orderengine.phase13;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.EnumMap;
import java.util.Map;

/**
 * A programmatic thread-dump capture and summary tool — the same
 * underlying data `jstack <pid>` / `jcmd <pid> Thread.print` show you,
 * accessed here via ThreadMXBean directly (as Phase 11's DeadlockDemo
 * first used for deadlock detection specifically; this class
 * generalizes the same API to full-dump triage).
 *
 * WHAT TO LOOK FOR IN A REAL PRODUCTION THREAD DUMP, using the state
 * breakdown this class prints:
 *
 *   Large numbers of BLOCKED threads, especially many blocked on the
 *   SAME lock — a contention hotspot; cross-reference with Phase 2's
 *   lock-choice guidance (would a ReadWriteLock/StampedLock relieve
 *   this if the traffic is read-heavy?) or Phase 11's deadlock
 *   detection if the blocked threads form a cycle.
 *
 *   Large numbers of WAITING/TIMED_WAITING threads — usually normal
 *   (a healthy pool with idle worker threads waiting on an empty queue
 *   looks exactly like this), but a sudden SPIKE in this count
 *   correlated with a latency incident can indicate threads piling up
 *   waiting on a slow downstream call (Phase 12 territory — is a
 *   circuit breaker/bulkhead needed here that isn't in place yet?).
 *
 *   Thread count climbing over time across repeated dumps taken minutes
 *   apart — a thread leak; cross-reference with Phase 4's pool-sizing
 *   discipline (is something creating unbounded/unpooled threads
 *   instead of using a properly bounded pool?) or Phase 9 (should this
 *   workload be on virtual threads instead, where thread COUNT itself
 *   stops being the right thing to watch at all, and carrier-level
 *   metrics matter more?).
 */
public class ThreadDumpAnalyzer {

    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

    public void printSummary() {
        ThreadInfo[] allThreads = threadBean.dumpAllThreads(true, true);

        Map<Thread.State, Integer> stateCounts = new EnumMap<>(Thread.State.class);
        for (Thread.State state : Thread.State.values()) stateCounts.put(state, 0);
        for (ThreadInfo info : allThreads) {
            stateCounts.merge(info.getThreadState(), 1, Integer::sum);
        }

        System.out.println("=== Thread dump summary (" + allThreads.length + " total threads) ===");
        stateCounts.forEach((state, count) -> {
            if (count > 0) {
                System.out.printf("  %-16s %d%n", state, count);
            }
        });

        long[] deadlocked = threadBean.findDeadlockedThreads();
        if (deadlocked != null) {
            System.out.println("\n  *** " + deadlocked.length + " DEADLOCKED THREADS DETECTED ***");
            for (ThreadInfo info : threadBean.getThreadInfo(deadlocked, true, true)) {
                System.out.println("    " + info.getThreadName() + " waiting on " + info.getLockName()
                        + " held by " + info.getLockOwnerName());
            }
        } else {
            System.out.println("\n  no deadlock detected");
        }

        System.out.println("\n=== Threads currently BLOCKED (contention hotspots) ===");
        boolean anyBlocked = false;
        for (ThreadInfo info : allThreads) {
            if (info.getThreadState() == Thread.State.BLOCKED) {
                anyBlocked = true;
                System.out.println("  " + info.getThreadName() + " blocked on " + info.getLockName()
                        + " (owned by " + info.getLockOwnerName() + ")");
            }
        }
        if (!anyBlocked) {
            System.out.println("  (none)");
        }
    }
}
