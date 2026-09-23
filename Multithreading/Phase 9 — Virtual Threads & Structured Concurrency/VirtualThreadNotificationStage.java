package com.orderengine.phase9;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The virtual-thread rewrite: ONE virtual thread PER notification, no
 * pool size to calculate, no Phase 4 wait/compute formula needed at all.
 * Executors.newVirtualThreadPerTaskExecutor() is deliberately NOT a pool
 * in the Phase 4 sense — it does not reuse threads; it creates a brand
 * new virtual thread for every submitted task, every time, by design.
 * That's fine specifically BECAUSE virtual threads are cheap enough
 * (roughly hundreds of bytes of initial stack footprint, growable, vs a
 * platform thread's ~1MB reserved OS stack) that "one per task, always
 * fresh" is a completely reasonable default rather than something that
 * would need pooling to be affordable.
 *
 * The actual concurrency-limiting resource — real OS threads needed to
 * ACTUALLY EXECUTE code on a CPU core — still exists: virtual threads
 * are scheduled onto a small, fixed pool of "carrier" platform threads
 * (by default sized to available processors) by the JDK's own virtual
 * thread scheduler. The difference from a platform-thread pool is WHEN a
 * virtual thread needs a carrier: only while it's actually RUNNING code.
 * The moment a virtual thread calls a blocking operation the JDK
 * recognizes (Thread.sleep, blocking I/O, java.util.concurrent locks,
 * etc.), it UNMOUNTS from its carrier — the carrier is freed to run a
 * different virtual thread — and the blocked virtual thread's state is
 * parked as a lightweight continuation, resuming (remounting onto some
 * available carrier, not necessarily the same one) once the blocking
 * operation completes. Thousands of virtual threads can be "blocked" on
 * I/O simultaneously while consuming only a small, fixed number of real
 * OS threads to do it — see phase notes §2 for the full mechanics, and
 * §4 for the pinning cases where this unmounting DOESN'T happen.
 */
public class VirtualThreadNotificationStage {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final NotificationSender sender;

    public VirtualThreadNotificationStage(NotificationSender sender) {
        this.sender = sender;
    }

    public void sendAll(List<Long> orderIds) {
        List<Future<?>> futures = new ArrayList<>();
        for (long orderId : orderIds) {
            futures.add(executor.submit(() -> sender.send(orderId)));
        }
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
