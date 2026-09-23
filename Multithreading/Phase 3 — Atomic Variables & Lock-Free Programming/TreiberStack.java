package com.orderengine.phase3;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A lock-free stack (the "Treiber stack", published by R. Kent Treiber in
 * 1986 — one of the foundational lock-free algorithms) used here as a
 * pool of reusable, pre-allocated "OrderContext" buffer objects for the
 * ingestion path: instead of allocating a fresh buffer object per order
 * (GC pressure at high throughput) and instead of a locked pool
 * (contention on the hot allocate/release path), workers push/pop
 * buffers from this lock-free free-list.
 *
 * push() and pop() both follow the exact same CAS-retry shape as
 * OrderIdGenerator.nextIdManualCas(), just operating on a reference to a
 * linked node instead of a long:
 *
 *   1. Read the current top (a plain read).
 *   2. Build the new node/next-pointer relationship based on it.
 *   3. compareAndSet(oldTop, newTop) — succeeds only if nothing else
 *      changed `top` since step 1.
 *   4. Retry on failure.
 *
 * This class is deliberately vulnerable to the ABA problem when nodes are
 * pooled/reused rather than always freshly allocated — see AbaDemo.java
 * and the phase notes (§4) for the concrete failure scenario and why it
 * matters specifically for free-lists like this one.
 */
public class TreiberStack<T> {

    private static final class Node<T> {
        final T value;
        Node<T> next;

        Node(T value) {
            this.value = value;
        }
    }

    private final AtomicReference<Node<T>> top = new AtomicReference<>();

    public void push(T value) {
        Node<T> newNode = new Node<>(value);
        Node<T> currentTop;
        do {
            currentTop = top.get();
            newNode.next = currentTop;
        } while (!top.compareAndSet(currentTop, newNode));
    }

    /** Returns null if the stack is empty. */
    public T pop() {
        Node<T> currentTop;
        Node<T> newTop;
        do {
            currentTop = top.get();
            if (currentTop == null) {
                return null;
            }
            newTop = currentTop.next;
        } while (!top.compareAndSet(currentTop, newTop));
        return currentTop.value;
    }

    public boolean isEmpty() {
        return top.get() == null;
    }
}
