package com.orderengine.phase11;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Two independent, complete fixes for DeadlockDemo's bug, each breaking
 * a DIFFERENT one of the four Coffman conditions (see that class's
 * javadoc). Both are run here against the SAME adversarial interleaving
 * that reliably deadlocked the broken version, to prove each fix
 * actually holds under exactly the conditions that broke the original.
 *
 * FIX 1 — Consistent lock ordering (breaks "circular wait"):
 * Every transfer, regardless of logical source/destination, acquires
 * locks in a GLOBAL, CONSISTENT order — here, always the lower account
 * ID first. Thread 1 (A->B) and Thread 2 (B->A) now BOTH attempt to lock
 * account 1 first, no matter which direction the transfer is logically
 * going. One of them wins that race and proceeds to lock account 2
 * next, uncontested (since the other thread is now waiting on account 1,
 * not off holding account 2 and waiting on account 1 — nobody now HOLDS
 * a second lock while waiting on a first one from someone who holds
 * IT). This is the standard, simplest, most broadly-applicable deadlock
 * fix: give every lock a stable total order and always acquire in that
 * order, everywhere, no exceptions. The discipline has to be applied
 * EVERYWHERE locks A and B might be acquired together — a single call
 * site anywhere in the codebase that still locks them in the "wrong"
 * order reintroduces the exact same deadlock.
 *
 * FIX 2 — tryLock with timeout (breaks "hold and wait" — gives up the
 * lock it already holds rather than waiting indefinitely for the second):
 * Instead of blocking forever on the second lock, use
 * tryLock(timeout, unit). If it fails to acquire within the timeout,
 * RELEASE the first lock already held and retry the whole operation
 * from scratch (typically after a randomized backoff, to reduce the
 * chance of the same two threads immediately colliding again in
 * lockstep). This doesn't require a global lock ordering convention —
 * useful when that discipline is hard to guarantee across a large or
 * unfamiliar codebase — at the cost of needing retry logic and doing
 * strictly more work under contention (repeated failed attempts) than
 * fix 1's approach, which never has to back off or retry at all once
 * ordering is correctly applied everywhere.
 */
public class FixedTransfer {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Fix 1: consistent lock ordering ===");
        runOrderedFix();

        System.out.println("\n=== Fix 2: tryLock with timeout + backoff ===");
        runTryLockFix();
    }

    // --- Fix 1: consistent ordering ---

    private static void runOrderedFix() throws InterruptedException {
        Account accountA = new Account(1, 1000);
        Account accountB = new Account(2, 1000);
        CountDownLatch syncPoint = new CountDownLatch(2);

        Thread t1 = new Thread(() -> orderedTransfer(accountA, accountB, 100, syncPoint), "transfer-A-to-B");
        Thread t2 = new Thread(() -> orderedTransfer(accountB, accountA, 50, syncPoint), "transfer-B-to-A");

        t1.start();
        t2.start();
        t1.join(5000);
        t2.join(5000);

        System.out.println("  both transfers completed: " + (!t1.isAlive() && !t2.isAlive()));
        System.out.println("  final balances: account " + accountA.id() + "=" + accountA.balance()
                + ", account " + accountB.id() + "=" + accountB.balance()
                + " (expected 1000-100+50=950 and 1000-50+100=1050)");
    }

    /** Always locks the LOWER account id first, regardless of logical from/to direction. */
    private static void orderedTransfer(Account from, Account to, double amount, CountDownLatch syncPoint) {
        Account first = from.id() < to.id() ? from : to;
        Account second = from.id() < to.id() ? to : from;

        first.lock().lock();
        try {
            syncPoint.countDown();
            try {
                syncPoint.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            second.lock().lock(); // no deadlock possible: every thread attempts the SAME first lock
            try {
                from.adjust(-amount);
                to.adjust(amount);
                System.out.println("  " + Thread.currentThread().getName() + " completed transfer.");
            } finally {
                second.lock().unlock();
            }
        } finally {
            first.lock().unlock();
        }
    }

    // --- Fix 2: tryLock with timeout + backoff ---

    private static void runTryLockFix() throws InterruptedException {
        Account accountA = new Account(1, 1000);
        Account accountB = new Account(2, 1000);

        Thread t1 = new Thread(() -> tryLockTransfer(accountA, accountB, 100), "transfer-A-to-B");
        Thread t2 = new Thread(() -> tryLockTransfer(accountB, accountA, 50), "transfer-B-to-A");

        t1.start();
        t2.start();
        t1.join(5000);
        t2.join(5000);

        System.out.println("  both transfers completed: " + (!t1.isAlive() && !t2.isAlive()));
        System.out.println("  final balances: account " + accountA.id() + "=" + accountA.balance()
                + ", account " + accountB.id() + "=" + accountB.balance()
                + " (expected 950 and 1050)");
    }

    private static void tryLockTransfer(Account from, Account to, double amount) {
        int attempts = 0;
        while (true) {
            attempts++;
            boolean gotFrom = false;
            boolean gotTo = false;
            try {
                gotFrom = from.lock().tryLock(100, TimeUnit.MILLISECONDS);
                if (gotFrom) {
                    gotTo = to.lock().tryLock(100, TimeUnit.MILLISECONDS);
                    if (gotTo) {
                        from.adjust(-amount);
                        to.adjust(amount);
                        System.out.println("  " + Thread.currentThread().getName()
                                + " completed transfer on attempt " + attempts);
                        return;
                    }
                }
                // Failed to get both — give up whatever we DID get and retry.
                // This is the key move: releasing the first lock rather than
                // holding it while waiting indefinitely breaks "hold and wait".
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                if (gotTo) to.lock().unlock();
                if (gotFrom) from.lock().unlock();
            }
            // Randomized backoff — without this, two threads that keep
            // colliding at the same moment can retry in lockstep forever,
            // which is LIVELOCK, not deadlock (see LivelockDemo in this
            // phase) — always-retry-immediately can trade one failure mode
            // for another if you're not careful.
            sleep(ThreadLocalRandom.current().nextInt(5, 30));
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
