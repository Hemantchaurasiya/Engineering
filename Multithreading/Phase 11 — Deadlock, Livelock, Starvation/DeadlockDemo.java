package com.orderengine.phase11;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * INTENTIONALLY BROKEN — reproduces a textbook deadlock deterministically
 * (via CountDownLatch coordination, not timing luck — same discipline as
 * every prior phase's forced-interleaving demos).
 *
 * THE BUG: transferring between two accounts requires locking BOTH of
 * them (a transfer must be atomic across both balances, or a concurrent
 * reader could observe money "in transit" — debited from one side,
 * not yet credited to the other). This method locks source-then-
 * destination, in whatever order the CALLER happens to pass them:
 *
 *   transfer(accountA, accountB, amount)  locks A, then B
 *   transfer(accountB, accountA, amount)  locks B, then A
 *
 * If thread 1 calls the first and thread 2 calls the second AT THE SAME
 * TIME, thread 1 can grab A's lock while thread 2 grabs B's lock, and
 * then BOTH threads block forever trying to acquire the OTHER account's
 * lock, which the other thread is holding and will never release (it's
 * waiting on thread 1, which is waiting on it) — the classic circular
 * wait. This is DEADLOCK, one of the four Coffman conditions all being
 * satisfied simultaneously:
 *   1. Mutual exclusion    — each lock can only be held by one thread
 *   2. Hold and wait       — each thread holds one lock while waiting for another
 *   3. No preemption       — a held lock can't be forcibly taken away
 *   4. Circular wait       — thread 1 waits on thread 2's lock, thread 2 waits on thread 1's
 * Breaking ANY ONE of these four conditions prevents deadlock — the
 * fixes in FixedTransfer.java each break a different one.
 */
public class DeadlockDemo {

    public static void main(String[] args) throws InterruptedException {
        Account accountA = new Account(1, 1000);
        Account accountB = new Account(2, 1000);

        CountDownLatch bothLockedFirstAccount = new CountDownLatch(2);

        Thread thread1 = new Thread(() -> {
            brokenTransfer(accountA, accountB, 100, bothLockedFirstAccount);
        }, "transfer-A-to-B");

        Thread thread2 = new Thread(() -> {
            brokenTransfer(accountB, accountA, 50, bothLockedFirstAccount);
        }, "transfer-B-to-A");

        thread1.start();
        thread2.start();

        System.out.println("Both transfers started — waiting up to 5s to see if they complete...\n");

        thread1.join(5000);
        thread2.join(5000);

        if (thread1.isAlive() || thread2.isAlive()) {
            System.out.println("DEADLOCK CONFIRMED — at least one transfer thread is still blocked after 5s.\n");
            detectAndPrintDeadlock();
        } else {
            System.out.println("Both transfers completed (didn't deadlock this run — see phase notes on why "
                    + "deadlock reproduction can still be timing-sensitive despite the coordinated setup; "
                    + "rerun, or note the FixedTransfer demo proves the actual fix regardless).");
        }

        System.exit(0); // the JVM won't exit on its own with deadlocked non-daemon threads still parked
    }

    private static void brokenTransfer(Account from, Account to, double amount, CountDownLatch syncPoint) {
        System.out.println("  " + Thread.currentThread().getName() + " attempting to lock account "
                + from.id() + " first...");
        from.lock().lock();
        try {
            System.out.println("  " + Thread.currentThread().getName() + " locked account " + from.id()
                    + ", now attempting account " + to.id() + "...");

            syncPoint.countDown();
            try {
                // Forces both threads to have their FIRST lock held before
                // either attempts the second — guarantees the deadlock
                // interleaving is reached deterministically rather than by
                // timing luck.
                syncPoint.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            to.lock().lock(); // <-- both threads block here forever, each holding what the other wants
            try {
                from.adjust(-amount);
                to.adjust(amount);
                System.out.println("  " + Thread.currentThread().getName() + " completed transfer.");
            } finally {
                to.lock().unlock();
            }
        } finally {
            from.lock().unlock();
        }
    }

    /**
     * Uses ThreadMXBean.findDeadlockedThreads() — the exact same
     * underlying mechanism `jstack`/`jcmd Thread.print` use to report
     * "Found one Java-level deadlock" — to detect and report the
     * deadlock programmatically, entirely from within the JVM, no
     * external tooling required for this demo. In a real production
     * incident you'd normally reach for `jstack <pid>` or
     * `jcmd <pid> Thread.print` from the command line against a running
     * process instead, but the underlying detection algorithm (walking
     * the "who owns this lock, who's waiting for it, is there a cycle"
     * graph) is identical either way.
     */
    private static void detectAndPrintDeadlock() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedIds = threadBean.findDeadlockedThreads();

        if (deadlockedIds == null) {
            System.out.println("(ThreadMXBean did not report a monitor/lock-based deadlock — "
                    + "the threads may just be slow this run)");
            return;
        }

        System.out.println("=== Deadlock diagnostic (equivalent to jstack/jcmd Thread.print output) ===\n");
        ThreadInfo[] infos = threadBean.getThreadInfo(deadlockedIds, true, true);
        for (ThreadInfo info : infos) {
            System.out.println("Thread \"" + info.getThreadName() + "\" is " + info.getThreadState());
            System.out.println("  waiting to lock: " + info.getLockName());
            System.out.println("  which is held by: \"" + info.getLockOwnerName() + "\"");
            System.out.println();
        }
        System.out.println("This is exactly the pattern to look for in a real jstack dump: threads in "
                + "BLOCKED state, each one's \"waiting to lock\" pointing at a monitor owned by ANOTHER "
                + "thread that is itself blocked in the same dump — a cycle, not just contention.");
    }
}
