package com.orderengine.phase3;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * Demonstrates the ABA problem concretely, using a deterministic two-
 * thread interleaving (coordinated with CountDownLatch, not timing luck —
 * this reproduces identically every single run, unlike the JIT-hoisting
 * demo in Phase 1 which is inherently timing-dependent) against a
 * pooled-node stack scenario, then shows AtomicStampedReference fixing it.
 *
 * THE SCENARIO (mirrors why TreiberStack.java calls out that it's
 * vulnerable when nodes are pooled/reused rather than always freshly
 * allocated — this is exactly why):
 *
 * Stack starts as top -> A -> B -> C.
 *
 *   Thread 1 (the "attacker" thread): begins a pop(). It reads
 *   currentTop = A and computes newTop = A.next = B — but is paused by
 *   the OS/scheduler BEFORE it executes the compareAndSet.
 *
 *   Thread 2 (runs to completion while T1 is paused):
 *     - pops A (stack is now top -> B -> C)
 *     - pops B (stack is now top -> C)
 *     - a pooled-object allocator reuses the SAME node object that held
 *       "A" (identical memory identity — this is the entire point of
 *       object pooling: avoid allocating a fresh node) and pushes it
 *       back (stack is now top -> A(same object) -> C)
 *
 *   Thread 1 resumes and executes compareAndSet(currentTop=A, newTop=B).
 *   A plain AtomicReference CAS only compares REFERENCE IDENTITY. The
 *   top is once again literally the same A object T1 originally read —
 *   so the CAS SUCCEEDS, even though the entire structure of the stack
 *   changed underneath T1 while it was paused. Thread 1 has no way to
 *   know "this is A again, but a DIFFERENT A situation" — that's the
 *   ABA problem by definition: value observed as A, changed away from A,
 *   changed back to (an indistinguishable) A, and a naive CAS can't tell
 *   the difference.
 *
 * RESULT: top becomes B. But B was already removed by Thread 2 and the
 * node Thread 2 legitimately just re-pushed (A) is silently dropped from
 * the stack — no exception, no CAS failure, just quiet structural
 * corruption. This is what makes ABA bugs so dangerous: the code runs,
 * returns normally, and produces a wrong answer with no error signal.
 */
public class AbaProblemDemo {

    static final class Node<T> {
        final T value;
        Node<T> next;
        Node(T value) { this.value = value; }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Vulnerable: plain AtomicReference CAS ===");
        runVulnerableScenario();

        System.out.println();
        System.out.println("=== Fixed: AtomicStampedReference CAS ===");
        runFixedScenario();
    }

    private static void runVulnerableScenario() throws InterruptedException {
        AtomicReference<Node<String>> top = new AtomicReference<>();
        Node<String> nodeA = new Node<>("A");
        Node<String> nodeB = new Node<>("B");
        Node<String> nodeC = new Node<>("C");
        nodeA.next = nodeB;
        nodeB.next = nodeC;
        top.set(nodeA); // top -> A -> B -> C

        CountDownLatch t1Ready = new CountDownLatch(1);
        CountDownLatch t2Done = new CountDownLatch(1);

        Thread t1 = new Thread(() -> {
            // T1 begins a pop(): read currentTop and compute newTop, then
            // PAUSE before the CAS — simulating a preemption at exactly
            // the vulnerable moment.
            Node<String> currentTop = top.get();      // A
            Node<String> newTop = currentTop.next;     // B
            t1Ready.countDown();
            await(t2Done);

            boolean success = top.compareAndSet(currentTop, newTop);
            System.out.println("  T1 stale CAS(expected=A, new=B) succeeded = " + success);
        });

        Thread t2 = new Thread(() -> {
            await(t1Ready);
            Node<String> poppedA = pop(top); // pop A -> top now B
            Node<String> poppedB = pop(top); // pop B -> top now C
            System.out.println("  T2 popped: " + poppedA.value + ", " + poppedB.value);
            // Pooled allocator reuses the SAME nodeA object identity for
            // a fresh push — this is the crux of the bug.
            push(top, poppedA); // top -> A(same object) -> C
            System.out.println("  T2 pushed pooled node 'A' back onto the stack");
            t2Done.countDown();
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.print("  Final stack contents (should have been [A, C] after T2's work): [");
        printStack(top);
        System.out.println("]");
        System.out.println("  RESULT: BUG REPRODUCED — the 'A' node T2 legitimately re-pushed "
                + "was silently overwritten by T1's stale CAS. It vanished with no error.");
    }

    private static void runFixedScenario() throws InterruptedException {
        AtomicStampedReference<Node<String>> top = new AtomicStampedReference<>(null, 0);
        Node<String> nodeA = new Node<>("A");
        Node<String> nodeB = new Node<>("B");
        Node<String> nodeC = new Node<>("C");
        nodeA.next = nodeB;
        nodeB.next = nodeC;
        top.set(nodeA, 0); // top -> A -> B -> C, stamp 0

        CountDownLatch t1Ready = new CountDownLatch(1);
        CountDownLatch t2Done = new CountDownLatch(1);

        Thread t1 = new Thread(() -> {
            int[] stampHolder = new int[1];
            Node<String> currentTop = top.get(stampHolder); // A, stamp captured too
            int observedStamp = stampHolder[0];
            Node<String> newTop = currentTop.next;           // B
            t1Ready.countDown();
            await(t2Done);

            // Same reference (A) will be true again, but the STAMP will
            // have moved on (T2's pop/pop/push each bump the stamp) —
            // so this CAS correctly fails even though the reference
            // identity alone would have matched.
            boolean success = top.compareAndSet(currentTop, newTop, observedStamp, observedStamp + 1);
            System.out.println("  T1 stale CAS(expected=A@stamp" + observedStamp
                    + ", new=B) succeeded = " + success
                    + "  (correctly rejected — real code would now retry with fresh state)");
        });

        Thread t2 = new Thread(() -> {
            await(t1Ready);
            Node<String> poppedA = popStamped(top); // stamp bumps
            Node<String> poppedB = popStamped(top); // stamp bumps
            System.out.println("  T2 popped: " + poppedA.value + ", " + poppedB.value);
            pushStamped(top, poppedA); // stamp bumps again
            System.out.println("  T2 pushed pooled node 'A' back onto the stack");
            t2Done.countDown();
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.print("  Final stack contents: [");
        printStamped(top);
        System.out.println("]");
        System.out.println("  RESULT: no corruption — the stamp mismatch made ABA detectable, "
                + "T1's stale CAS was rejected instead of silently succeeding.");
    }

    // --- plain-reference stack helpers (vulnerable version) ---

    private static <T> Node<T> pop(AtomicReference<Node<T>> top) {
        Node<T> cur, next;
        do {
            cur = top.get();
            next = cur.next;
        } while (!top.compareAndSet(cur, next));
        return cur;
    }

    private static <T> void push(AtomicReference<Node<T>> top, Node<T> node) {
        Node<T> cur;
        do {
            cur = top.get();
            node.next = cur;
        } while (!top.compareAndSet(cur, node));
    }

    private static void printStack(AtomicReference<Node<String>> top) {
        Node<String> n = top.get();
        boolean first = true;
        while (n != null) {
            if (!first) System.out.print(", ");
            System.out.print(n.value);
            first = false;
            n = n.next;
        }
    }

    // --- stamped-reference stack helpers (fixed version) ---

    private static <T> Node<T> popStamped(AtomicStampedReference<Node<T>> top) {
        int[] stampHolder = new int[1];
        Node<T> cur, next;
        int stamp;
        do {
            cur = top.get(stampHolder);
            stamp = stampHolder[0];
            next = cur.next;
        } while (!top.compareAndSet(cur, next, stamp, stamp + 1));
        return cur;
    }

    private static <T> void pushStamped(AtomicStampedReference<Node<T>> top, Node<T> node) {
        int[] stampHolder = new int[1];
        Node<T> cur;
        int stamp;
        do {
            cur = top.get(stampHolder);
            stamp = stampHolder[0];
            node.next = cur;
        } while (!top.compareAndSet(cur, node, stamp, stamp + 1));
    }

    private static void printStamped(AtomicStampedReference<Node<String>> top) {
        Node<String> n = top.getReference();
        boolean first = true;
        while (n != null) {
            if (!first) System.out.print(", ");
            System.out.print(n.value);
            first = false;
            n = n.next;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
